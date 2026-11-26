package app.glassworks.extension.tidal.lastfm;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal Last.fm API client (https://www.last.fm/api). Blocking: call it off the main thread.
 * <p>
 * Every call is a signed POST: {@code api_sig} is the MD5 of all parameters sorted by name and
 * concatenated as {@code name + value}, followed by the API secret.
 */
final class LastFmClient {

    private static final String API_URL = "https://ws.audioscrobbler.com/2.0/";
    private static final int TIMEOUT_MS = 15_000;

    /** Most scrobbles Last.fm accepts in one track.scrobble request. */
    static final int MAX_BATCH = 50;

    /** Last.fm error codes that mean the request may succeed later. */
    static final int ERROR_SERVICE_OFFLINE = 11;
    static final int ERROR_TEMPORARY = 16;
    static final int ERROR_RATE_LIMIT = 29;
    /** The session key is no longer valid; the user must log in again. */
    static final int ERROR_INVALID_SESSION = 9;
    static final int ERROR_INVALID_API_KEY = 10;
    static final int ERROR_AUTHENTICATION = 4;

    /** A failed call. {@link #code} is Last.fm's error code, or 0 for network errors. */
    static final class LastFmException extends Exception {
        final int code;

        LastFmException(int code, String message) {
            super(message);
            this.code = code;
        }

        LastFmException(IOException cause) {
            super(cause.getMessage() == null ? "Network error" : cause.getMessage(), cause);
            this.code = 0;
        }

        /** Whether the same request may succeed if retried later. */
        boolean isRetryable() {
            return code == 0 || code == ERROR_SERVICE_OFFLINE || code == ERROR_TEMPORARY
                    || code == ERROR_RATE_LIMIT;
        }
    }

    private final String apiKey;
    private final String apiSecret;

    LastFmClient(String apiKey, String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    /**
     * Logs in with the user's Last.fm username and password.
     *
     * @return {sessionKey, username}
     */
    String[] getMobileSession(String username, String password) throws LastFmException {
        Map<String, String> params = new TreeMap<>();
        params.put("method", "auth.getMobileSession");
        params.put("username", username);
        params.put("password", password);
        JSONObject response = call(params);
        try {
            JSONObject session = response.getJSONObject("session");
            return new String[]{session.getString("key"), session.optString("name", username)};
        } catch (JSONException e) {
            throw new LastFmException(-1, "Unexpected response from Last.fm");
        }
    }

    void updateNowPlaying(String sessionKey, Track track) throws LastFmException {
        Map<String, String> params = new TreeMap<>();
        params.put("method", "track.updateNowPlaying");
        params.put("sk", sessionKey);
        putTrack(params, track);
        call(params);
    }

    /**
     * Scrobbles up to {@link #MAX_BATCH} tracks in one request.
     *
     * @return how many Last.fm accepted; the rest were ignored by its filters (such as a
     *         timestamp that is too old), which is not an error
     */
    int scrobble(String sessionKey, List<Track> tracks, long[] timestampsSeconds) throws LastFmException {
        if (tracks.isEmpty() || tracks.size() > MAX_BATCH) {
            throw new IllegalArgumentException("Batch size " + tracks.size());
        }
        Map<String, String> params = new TreeMap<>();
        params.put("method", "track.scrobble");
        params.put("sk", sessionKey);
        for (int i = 0; i < tracks.size(); i++) {
            String suffix = tracks.size() == 1 ? "" : "[" + i + "]";
            putTrack(params, tracks.get(i), suffix);
            params.put("timestamp" + suffix, Long.toString(timestampsSeconds[i]));
        }
        JSONObject response = call(params);
        JSONObject scrobbles = response.optJSONObject("scrobbles");
        JSONObject attr = scrobbles == null ? null : scrobbles.optJSONObject("@attr");
        return attr == null ? tracks.size() : attr.optInt("accepted", tracks.size());
    }

    private static void putTrack(Map<String, String> params, Track track) {
        putTrack(params, track, "");
    }

    private static void putTrack(Map<String, String> params, Track track, String suffix) {
        params.put("artist" + suffix, track.artist);
        params.put("track" + suffix, track.title);
        if (!track.album.isEmpty()) {
            params.put("album" + suffix, track.album);
        }
        if (track.durationMs > 0) {
            params.put("duration" + suffix, Long.toString(track.durationMs / 1000));
        }
    }

    private JSONObject call(Map<String, String> params) throws LastFmException {
        params.put("api_key", apiKey);

        StringBuilder signature = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            signature.append(entry.getKey()).append(entry.getValue());
        }
        signature.append(apiSecret);
        params.put("api_sig", md5(signature.toString()));
        // Not part of the signature.
        params.put("format", "json");

        StringBuilder body = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (body.length() > 0) {
                    body.append('&');
                }
                body.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
        } catch (IOException e) {
            throw new LastFmException(e);
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("User-Agent", "Glassworks");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }

            int status = connection.getResponseCode();
            InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = in == null ? "" : readAll(in);

            JSONObject json;
            try {
                json = new JSONObject(text);
            } catch (JSONException e) {
                if (status >= 500) {
                    throw new LastFmException(ERROR_TEMPORARY, "Last.fm is unavailable (HTTP " + status + ")");
                }
                throw new LastFmException(-1, "Unexpected response from Last.fm (HTTP " + status + ")");
            }
            if (json.has("error")) {
                throw new LastFmException(json.optInt("error", -1), json.optString("message", "Last.fm error"));
            }
            return json;
        } catch (IOException e) {
            throw new LastFmException(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        }
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
