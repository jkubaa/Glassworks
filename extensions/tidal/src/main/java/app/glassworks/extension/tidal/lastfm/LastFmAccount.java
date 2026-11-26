package app.glassworks.extension.tidal.lastfm;

import android.os.Handler;
import android.os.Looper;

/** Account actions for the settings page. Callbacks run on the main thread. */
public final class LastFmAccount {

    public interface LoginCallback {
        /** @param error null on success, otherwise a message to show. */
        void onResult(String error);
    }

    private LastFmAccount() {
    }

    /**
     * Logs in with a username and password, using the API key and secret already saved.
     * The password is only sent to Last.fm, never stored.
     */
    public static void logIn(String username, String password, LoginCallback callback) {
        String apiKey = LastFmSettings.getApiKey();
        String apiSecret = LastFmSettings.getApiSecret();
        Handler main = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            String error;
            try {
                String[] session = new LastFmClient(apiKey, apiSecret).getMobileSession(username.trim(), password);
                LastFmSettings.setSession(session[0], session[1]);
                Scrobbler.get().flushQueueAsync();
                error = null;
            } catch (LastFmClient.LastFmException e) {
                if (e.code == LastFmClient.ERROR_AUTHENTICATION) {
                    error = "Wrong username or password.";
                } else if (e.code == LastFmClient.ERROR_INVALID_API_KEY || e.code == 26) {
                    error = "Last.fm rejected the API key. Check the key and secret.";
                } else if (e.code == 0) {
                    error = "Could not reach Last.fm. Check your connection.";
                } else {
                    error = e.getMessage();
                }
            }
            String result = error;
            main.post(() -> callback.onResult(result));
        }, "Glassworks-LastFm-Login").start();
    }

    /** Number of scrobbles waiting to be sent. */
    public static int getPendingScrobbles() {
        return Scrobbler.getQueueSize();
    }

    /** Tries to send waiting scrobbles now. */
    public static void sendPendingScrobbles() {
        Scrobbler.get().flushQueueAsync();
    }
}
