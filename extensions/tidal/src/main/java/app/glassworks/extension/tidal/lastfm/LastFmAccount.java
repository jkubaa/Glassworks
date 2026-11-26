package app.glassworks.extension.tidal.lastfm;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Account actions for the settings page. Callbacks run on the main thread. */
public final class LastFmAccount {

    public interface LoginCallback {
        /** @param error null on success, otherwise a message to show. */
        void onResult(String error);
    }

    /** How often a browser login checks whether the user allowed access. */
    private static final long BROWSER_POLL_MS = 4_000;
    /** How long a browser login waits for the user (Last.fm tokens last 60 minutes). */
    private static final long BROWSER_TIMEOUT_MS = 10 * 60_000;

    /** Changes whenever a browser login starts or is cancelled, so an old one stops quietly. */
    private static volatile int browserLoginId;
    private static volatile boolean browserLoginPending;

    private LastFmAccount() {
    }

    /**
     * Logs in through Last.fm's website: opens the page where the user allows Glassworks to use
     * their account, then waits until they did. Glassworks never sees the password.
     */
    public static void logInWithBrowser(Activity activity, LoginCallback callback) {
        int id = ++browserLoginId;
        browserLoginPending = true;
        LastFmClient client = new LastFmClient(LastFmSettings.getApiKey(), LastFmSettings.getApiSecret());
        Handler main = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            String error;
            try {
                String token = client.getToken();
                String url = client.authUrl(token);
                main.post(() -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception e) {
                        cancelBrowserLogin();
                        callback.onResult("No browser found to open Last.fm.");
                    }
                });

                long deadline = SystemClock.elapsedRealtime() + BROWSER_TIMEOUT_MS;
                while (true) {
                    Thread.sleep(BROWSER_POLL_MS);
                    if (id != browserLoginId) {
                        return;
                    }
                    if (SystemClock.elapsedRealtime() > deadline) {
                        throw new LastFmClient.LastFmException(LastFmClient.ERROR_TOKEN_EXPIRED, "Timed out");
                    }
                    try {
                        String[] session = client.getSession(token);
                        LastFmSettings.setSession(session[0], session[1]);
                        break;
                    } catch (LastFmClient.LastFmException e) {
                        if (e.code != LastFmClient.ERROR_TOKEN_NOT_AUTHORIZED && !e.isRetryable()) {
                            throw e;
                        }
                        // Not allowed yet, or a network hiccup: keep waiting.
                    }
                }
                Scrobbler.get().flushQueueAsync();
                error = null;
            } catch (LastFmClient.LastFmException e) {
                error = e.code == LastFmClient.ERROR_TOKEN_EXPIRED
                        ? "The login took too long. Try again."
                        : errorMessage(e);
            } catch (InterruptedException e) {
                return;
            }
            if (id != browserLoginId) {
                return;
            }
            browserLoginPending = false;
            String result = error;
            main.post(() -> callback.onResult(result));
        }, "Glassworks-LastFm-Login").start();
    }

    /** Whether a browser login is waiting for the user to allow access. */
    public static boolean isBrowserLoginPending() {
        return browserLoginPending;
    }

    /** Stops waiting for a browser login. Its callback is not called. */
    public static void cancelBrowserLogin() {
        browserLoginId++;
        browserLoginPending = false;
    }

    /**
     * Logs in with a username and password. The password is only sent to Last.fm, never stored.
     */
    public static void logIn(String username, String password, LoginCallback callback) {
        LastFmClient client = new LastFmClient(LastFmSettings.getApiKey(), LastFmSettings.getApiSecret());
        Handler main = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            String error;
            try {
                String[] session = client.getMobileSession(username.trim(), password);
                LastFmSettings.setSession(session[0], session[1]);
                Scrobbler.get().flushQueueAsync();
                error = null;
            } catch (LastFmClient.LastFmException e) {
                error = e.code == LastFmClient.ERROR_AUTHENTICATION
                        ? "Wrong username or password."
                        : errorMessage(e);
            }
            String result = error;
            main.post(() -> callback.onResult(result));
        }, "Glassworks-LastFm-Login").start();
    }

    private static String errorMessage(LastFmClient.LastFmException e) {
        if (e.code == LastFmClient.ERROR_INVALID_API_KEY || e.code == LastFmClient.ERROR_SUSPENDED_API_KEY) {
            return LastFmSettings.isUsingOwnApiKey()
                    ? "Last.fm rejected the API key. Check the key and secret."
                    : "Last.fm rejected the built-in API key. Use your own key under Advanced.";
        }
        if (e.code == 0) {
            return "Could not reach Last.fm. Check your connection.";
        }
        return e.getMessage();
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
