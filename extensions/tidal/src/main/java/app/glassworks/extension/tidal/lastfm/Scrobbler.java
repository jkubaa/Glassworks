package app.glassworks.extension.tidal.lastfm;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * Decides when a track counts as played and sends it to Last.fm.
 * <p>
 * It follows TIDAL's media session: metadata says which track is loaded, playback state says
 * whether it is playing. Only time spent playing counts, so pausing or seeking does not scrobble
 * early. Following Last.fm's rules, tracks shorter than 30 seconds are never scrobbled, and a
 * track is scrobbled once it played for the threshold percentage of its length or 4 minutes,
 * whichever comes first.
 * <p>
 * All state lives on one background thread. Scrobbles that fail because of the network or a
 * Last.fm outage are queued and sent with the next scrobble or app start, up to 50 per request.
 * After a rate-limit error, nothing is sent for a few minutes.
 */
final class Scrobbler {

    private static final String TAG = "Glassworks";

    private static final long MIN_TRACK_MS = 30_000;
    private static final long MAX_THRESHOLD_MS = 240_000;
    /** Used when TIDAL does not report a duration. */
    private static final long UNKNOWN_DURATION_THRESHOLD_MS = 240_000;
    /** Caps the offline queue. It is sent in batches of {@link LastFmClient#MAX_BATCH}. */
    private static final int MAX_QUEUE = 500;
    /** How long all requests pause after Last.fm reports a rate limit. */
    private static final long RATE_LIMIT_BACKOFF_MS = 5 * 60_000;

    private static Scrobbler instance;

    private final Handler handler;
    private final Runnable thresholdCheck = this::checkThreshold;
    private final Runnable delayedFlush = this::flushQueue;
    /** {@link SystemClock#elapsedRealtime()} until which no requests are sent, after a rate limit. */
    private long backoffUntil;

    /** Latest track of each media session (TIDAL may have more than one, such as for Sonos). */
    private final Map<Object, Track> sessionTracks = new HashMap<>();
    private Object activeSession;

    private Track track;
    private long startedAtSeconds;
    private long playedMs;
    private long playingSince = -1;
    private long lastPositionMs;
    private boolean scrobbled;
    private boolean nowPlayingSent;
    private boolean playing;
    private boolean queueFlushed;

    static synchronized Scrobbler get() {
        if (instance == null) {
            instance = new Scrobbler();
        }
        return instance;
    }

    private Scrobbler() {
        HandlerThread thread = new HandlerThread("Glassworks-LastFm");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    // region Events, called from any thread

    void onMetadata(Object session, Track newTrack) {
        handler.post(() -> {
            sessionTracks.put(session, newTrack);
            if (session == activeSession) {
                changeTrack(newTrack);
            }
        });
    }

    void onPlaybackState(Object session, boolean isPlaying, long positionMs) {
        handler.post(() -> {
            if (isPlaying && session != activeSession) {
                // Another session started playing: follow it.
                pauseCounting();
                activeSession = session;
                track = null;
                changeTrack(sessionTracks.get(session));
            }
            if (session != activeSession) {
                return;
            }

            if (isPlaying && scrobbled && positionMs < 5_000 && lastPositionMs - positionMs > 30_000) {
                // The same track started again, such as with repeat one.
                restartTrack();
            }
            lastPositionMs = positionMs;

            playing = isPlaying;
            if (isPlaying) {
                startCounting();
            } else {
                pauseCounting();
            }
        });
    }

    /** Sends queued scrobbles, such as after logging in. */
    void flushQueueAsync() {
        handler.post(this::flushQueue);
    }

    // endregion

    // region Play tracking

    private void changeTrack(Track newTrack) {
        if (newTrack != null && newTrack.sameAs(track)) {
            return;
        }
        pauseCounting();
        track = newTrack;
        restartTrack();
        if (playing) {
            startCounting();
        }
    }

    private void restartTrack() {
        handler.removeCallbacks(thresholdCheck);
        playedMs = 0;
        playingSince = playing ? SystemClock.elapsedRealtime() : -1;
        startedAtSeconds = 0;
        scrobbled = false;
        nowPlayingSent = false;
    }

    private void startCounting() {
        if (track == null) {
            return;
        }
        if (playingSince < 0) {
            playingSince = SystemClock.elapsedRealtime();
        }
        if (startedAtSeconds == 0) {
            startedAtSeconds = System.currentTimeMillis() / 1000;
        }
        if (!nowPlayingSent) {
            nowPlayingSent = true;
            sendNowPlaying(track);
        }
        scheduleThresholdCheck();
    }

    private void pauseCounting() {
        handler.removeCallbacks(thresholdCheck);
        if (playingSince >= 0) {
            playedMs += SystemClock.elapsedRealtime() - playingSince;
            playingSince = -1;
        }
        checkThreshold();
    }

    private long currentPlayedMs() {
        return playedMs + (playingSince >= 0 ? SystemClock.elapsedRealtime() - playingSince : 0);
    }

    /** Play time after which the current track is scrobbled, or -1 if it never is. */
    private long thresholdMs() {
        if (track == null) {
            return -1;
        }
        if (track.durationMs <= 0) {
            return UNKNOWN_DURATION_THRESHOLD_MS;
        }
        if (track.durationMs < MIN_TRACK_MS) {
            return -1;
        }
        return Math.min(track.durationMs * LastFmSettings.getThresholdPercent() / 100, MAX_THRESHOLD_MS);
    }

    private void scheduleThresholdCheck() {
        handler.removeCallbacks(thresholdCheck);
        long threshold = thresholdMs();
        if (scrobbled || threshold < 0 || playingSince < 0) {
            return;
        }
        handler.postDelayed(thresholdCheck, Math.max(0, threshold - currentPlayedMs()) + 250);
    }

    private void checkThreshold() {
        long threshold = thresholdMs();
        if (scrobbled || threshold < 0 || currentPlayedMs() < threshold) {
            if (!scrobbled && playingSince >= 0) {
                scheduleThresholdCheck();
            }
            return;
        }
        scrobbled = true;
        enqueue(track, startedAtSeconds);
        flushQueue();
    }

    // endregion

    // region Last.fm requests (on the scrobbler thread)

    private LastFmClient client() {
        if (!LastFmSettings.isEnabled() || !LastFmSettings.isLoggedIn()) {
            return null;
        }
        return new LastFmClient(LastFmSettings.getApiKey(), LastFmSettings.getApiSecret());
    }

    private void sendNowPlaying(Track playingTrack) {
        if (!LastFmSettings.isNowPlayingEnabled() || isBackingOff()) {
            return;
        }
        LastFmClient client = client();
        if (client == null) {
            return;
        }
        try {
            client.updateNowPlaying(LastFmSettings.getSessionKey(), playingTrack);
        } catch (LastFmClient.LastFmException e) {
            handleError(e);
        }
        if (!queueFlushed) {
            flushQueue();
        }
    }

    private void enqueue(Track scrobbledTrack, long timestampSeconds) {
        if (!LastFmSettings.isEnabled() || !LastFmSettings.isLoggedIn()) {
            return;
        }
        try {
            JSONArray queue = readQueue();
            queue.put(scrobbledTrack.toJson(timestampSeconds));
            while (queue.length() > MAX_QUEUE) {
                queue.remove(0);
            }
            GlassworksSettings.putString(LastFmSettings.KEY_QUEUE, queue.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to queue a scrobble", e);
        }
    }

    private void flushQueue() {
        LastFmClient client = client();
        if (client == null || isBackingOff()) {
            // While backing off, the delayed flush sends the queue.
            return;
        }
        queueFlushed = true;
        JSONArray queue = readQueue();
        int sent = 0;
        try {
            while (sent < queue.length()) {
                int end = Math.min(sent + LastFmClient.MAX_BATCH, queue.length());
                int done = sendBatch(client, queue, sent, end);
                sent += done;
                if (sent < end) {
                    // Keep the rest of the queue for later.
                    break;
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Corrupt scrobble queue, clearing it", e);
            sent = queue.length();
        }

        JSONArray remaining = new JSONArray();
        for (int i = sent; i < queue.length(); i++) {
            remaining.put(queue.opt(i));
        }
        GlassworksSettings.putString(LastFmSettings.KEY_QUEUE,
                remaining.length() == 0 ? null : remaining.toString());
    }

    /**
     * Sends queue items {@code [from, to)} in one request.
     *
     * @return how many items were dealt with (sent, or dropped because Last.fm rejected them);
     *         fewer than {@code to - from} means the rest must stay queued
     */
    private int sendBatch(LastFmClient client, JSONArray queue, int from, int to) throws JSONException {
        List<Track> tracks = new ArrayList<>(to - from);
        long[] timestamps = new long[to - from];
        for (int i = from; i < to; i++) {
            JSONObject item = queue.getJSONObject(i);
            tracks.add(Track.fromJson(item));
            timestamps[i - from] = item.optLong("timestamp");
        }
        try {
            int accepted = client.scrobble(LastFmSettings.getSessionKey(), tracks, timestamps);
            LastFmSettings.addScrobbleCount(accepted);
            LastFmSettings.setLastError("");
            if (accepted < tracks.size()) {
                Log.i(TAG, "Last.fm ignored " + (tracks.size() - accepted) + " of " + tracks.size() + " scrobbles");
            }
            return to - from;
        } catch (LastFmClient.LastFmException e) {
            handleError(e);
            if (e.isRetryable() || e.code == LastFmClient.ERROR_INVALID_SESSION
                    || e.code == LastFmClient.ERROR_INVALID_API_KEY) {
                return 0;
            }
            if (to - from == 1) {
                // Rejected (such as a missing artist): drop it.
                Log.w(TAG, "Last.fm rejected a scrobble: " + e.getMessage());
                return 1;
            }
            // One bad track can fail the whole request: send them one by one so only it is dropped.
            for (int i = from; i < to; i++) {
                if (sendBatch(client, queue, i, i + 1) == 0) {
                    return i - from;
                }
            }
            return to - from;
        }
    }

    private boolean isBackingOff() {
        return SystemClock.elapsedRealtime() < backoffUntil;
    }

    private void handleError(LastFmClient.LastFmException e) {
        Log.w(TAG, "Last.fm request failed (" + e.code + "): " + e.getMessage());
        if (e.code == LastFmClient.ERROR_RATE_LIMIT) {
            // Stop all requests for a while, then send the queue again.
            backoffUntil = SystemClock.elapsedRealtime() + RATE_LIMIT_BACKOFF_MS;
            handler.removeCallbacks(delayedFlush);
            handler.postDelayed(delayedFlush, RATE_LIMIT_BACKOFF_MS + 1_000);
        }
        if (e.code == LastFmClient.ERROR_INVALID_SESSION) {
            LastFmSettings.setLastError("Last.fm ended the session. Log in again.");
        } else if (e.code == LastFmClient.ERROR_INVALID_API_KEY) {
            LastFmSettings.setLastError(LastFmSettings.isUsingOwnApiKey()
                    ? "Last.fm rejected the API key."
                    : "Last.fm rejected the built-in API key. Use your own key under Advanced.");
        } else if (e.isRetryable()) {
            LastFmSettings.setLastError("Last.fm could not be reached. Scrobbles are kept and sent later.");
        } else {
            LastFmSettings.setLastError(e.getMessage());
        }
    }

    // endregion

    /** Number of scrobbles waiting to be sent. */
    static int getQueueSize() {
        return readQueue().length();
    }

    private static JSONArray readQueue() {
        String json = GlassworksSettings.getString(LastFmSettings.KEY_QUEUE, "");
        if (json.isEmpty()) {
            return new JSONArray();
        }
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
