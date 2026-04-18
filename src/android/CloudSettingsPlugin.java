package cordova.plugin.cloudsettings;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.util.Log;

import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class CloudSettingsPlugin extends CordovaPlugin {

    private static final String TAG = "CloudSettingsPlugin";
    private static final String DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata";
    private static final String FILE_NAME = "cloudsettings.json";
    private static final int REQUEST_CODE_DRIVE_AUTH = 50433;

    public static CloudSettingsPlugin instance = null;
    private String cachedAccessToken = null;
    private CallbackContext pendingConnectCallback = null;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        instance = this;

        // Silent auth attempt — no UI, fire-and-forget
        if (!isFireOS()) {
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        AuthorizationResult result = authorize();
                        if (!result.hasResolution()) {
                            cachedAccessToken = result.getAccessToken();
                            d("Silent auth succeeded on init");
                        }
                    } catch (Exception e) {
                        d("Silent auth skipped: " + e.getMessage());
                    }
                }
            });
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        try {
            switch (action) {
                case "checkAuth":      checkAuth(callbackContext); break;
                case "connect":        connect(callbackContext); break;
                case "save":           save(args.getString(0), callbackContext); break;
                case "load":           load(callbackContext); break;
                case "exists":         exists(callbackContext); break;
                case "hasLocalFile":   hasLocalFile(callbackContext); break;
                case "deleteLocalFile": deleteLocalFile(callbackContext); break;
                case "revokeAuth":     revokeAuth(callbackContext); break;
                default: return false;
            }
        } catch (Exception e) {
            callbackContext.error("Exception: " + e.getMessage());
        }
        return true;
    }

    // ---------------------------------------------------------------
    // Auth actions
    // ---------------------------------------------------------------

    private void checkAuth(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    AuthorizationResult result = authorize();
                    if (!result.hasResolution()) {
                        String token = getAccessToken();
                        String email = fetchEmailWithRetry(token);
                        JSONObject json = new JSONObject();
                        json.put("authorized", true);
                        json.put("email", email);
                        callbackContext.success(json.toString());
                    } else {
                        JSONObject json = new JSONObject();
                        json.put("authorized", false);
                        callbackContext.success(json.toString());
                    }
                } catch (Exception e) {
                    try {
                        JSONObject json = new JSONObject();
                        json.put("authorized", false);
                        callbackContext.success(json.toString());
                    } catch (Exception ex) {
                        callbackContext.error("checkAuth failed: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void connect(final CallbackContext callbackContext) {
        final Activity activity = cordova.getActivity();
        AuthorizationRequest request = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(DRIVE_APPDATA_SCOPE)))
                .build();

        d("connect: starting authorize()");
        AuthorizationClient client = Identity.getAuthorizationClient(activity);
        client.authorize(request)
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<AuthorizationResult>() {
                    @Override
                    public void onSuccess(AuthorizationResult result) {
                        d("connect: authorize success, hasResolution=" + result.hasResolution());
                        if (!result.hasResolution()) {
                            // Already authorized
                            d("connect: already authorized");
                            cordova.getThreadPool().execute(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String token = getAccessToken();
                                        String email = fetchEmailWithRetry(token);
                                        d("connect: fetched email=" + email);
                                        JSONObject json = new JSONObject();
                                        json.put("email", email);
                                        callbackContext.success(json.toString());
                                    } catch (Exception e) {
                                        d("connect: failed to fetch email: " + e.getMessage());
                                        callbackContext.error("Failed to fetch email: " + e.getMessage());
                                    }
                                }
                            });
                        } else {
                            // Need user consent — launch the PendingIntent
                            d("connect: needs resolution, launching PendingIntent with requestCode=" + REQUEST_CODE_DRIVE_AUTH);
                            pendingConnectCallback = callbackContext;
                            cordova.setActivityResultCallback(CloudSettingsPlugin.this);
                            try {
                                activity.startIntentSenderForResult(
                                        result.getPendingIntent().getIntentSender(),
                                        REQUEST_CODE_DRIVE_AUTH,
                                        null, 0, 0, 0);
                                d("connect: startIntentSenderForResult called successfully");
                            } catch (IntentSender.SendIntentException e) {
                                d("connect: startIntentSenderForResult failed: " + e.getMessage());
                                pendingConnectCallback = null;
                                callbackContext.error("Failed to launch auth intent: " + e.getMessage());
                            }
                        }
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        d("connect: authorize failed: " + e.getMessage());
                        callbackContext.error("Failed to connect: " + e.getMessage());
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_DRIVE_AUTH) return;

        final CallbackContext cb = pendingConnectCallback;
        pendingConnectCallback = null;

        if (cb == null) {
            w("onActivityResult: no pending callback");
            return;
        }

        if (resultCode == Activity.RESULT_OK) {
            d("onActivityResult: RESULT_OK, getting valid token");
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String token = getAccessToken();
                        String email = fetchEmailWithRetry(token);
                        d("onActivityResult: fetched email=" + email);
                        JSONObject json = new JSONObject();
                        json.put("email", email);
                        cb.success(json.toString());
                    } catch (Exception e) {
                        d("onActivityResult: failed: " + e.getMessage());
                        cb.error("Failed to complete connect: " + e.getMessage());
                    }
                }
            });
        } else {
            d("onActivityResult: result not OK, code=" + resultCode);
            cb.error("User cancelled or authorization failed");
        }
    }

    private void revokeAuth(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Get a fresh token to revoke
                    AuthorizationResult result = authorize();
                    String token = result.hasResolution() ? cachedAccessToken : result.getAccessToken();
                    if (token != null) {
                        // 1. Invalidate the token in Play Services' local cache.
                        //    Without this, authorize() returns the same stale token
                        //    even after server-side revocation.
                        try {
                            com.google.android.gms.auth.GoogleAuthUtil.clearToken(
                                    cordova.getActivity(), token);
                            d("revokeAuth: clearToken succeeded");
                        } catch (Exception clearEx) {
                            d("revokeAuth: clearToken failed: " + clearEx.getMessage());
                        }

                        // 2. Revoke the token server-side via Google OAuth2 endpoint
                        java.net.HttpURLConnection conn = null;
                        try {
                            java.net.URL url = new java.net.URL("https://oauth2.googleapis.com/revoke?token=" + token);
                            conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                            conn.setDoOutput(true);
                            conn.getOutputStream().close();
                            int responseCode = conn.getResponseCode();
                            d("revokeAuth: HTTP revoke response: " + responseCode);
                        } finally {
                            if (conn != null) conn.disconnect();
                        }
                    }
                    cachedAccessToken = null;
                    callbackContext.success();
                } catch (Exception e) {
                    cachedAccessToken = null;
                    callbackContext.error("Revoke failed: " + e.getMessage());
                }
            }
        });
    }

    // ---------------------------------------------------------------
    // Drive CRUD actions
    // ---------------------------------------------------------------

    private void save(final String jsonString, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String token = getAccessToken();

                    // Note: Do NOT write to the local file here. The local
                    // cloudsettings.json is a legacy artifact from the old
                    // BackupManager era, used only for migration detection
                    // (hasLocalFile/deleteLocalFile). Writing here would
                    // re-create the file after migration deletes it, causing
                    // the migration dialog to falsely re-trigger on next launch.

                    // Write to Drive
                    List<String> fileIds = DriveHelper.listFiles(token, FILE_NAME);
                    if (!fileIds.isEmpty()) {
                        DriveHelper.updateFile(token, fileIds.get(0), FILE_NAME, jsonString);
                        d("Updated existing Drive file");
                    } else {
                        DriveHelper.createFile(token, FILE_NAME, jsonString);
                        d("Created new Drive file");
                    }

                    callbackContext.success();
                } catch (IOException e) {
                    callbackContext.error("Network error: " + e.getMessage());
                } catch (Exception e) {
                    callbackContext.error("Save failed: " + e.getMessage());
                }
            }
        });
    }

    private void load(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String token = getAccessToken();

                    List<String> fileIds = DriveHelper.listFiles(token, FILE_NAME);
                    if (!fileIds.isEmpty()) {
                        String content = DriveHelper.downloadFile(token, fileIds.get(0));
                        callbackContext.success(content);
                    } else {
                        callbackContext.error("No backup found");
                    }
                } catch (IOException e) {
                    callbackContext.error("Network error: " + e.getMessage());
                } catch (Exception e) {
                    callbackContext.error("Load failed: " + e.getMessage());
                }
            }
        });
    }

    private void exists(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    AuthorizationResult result = authorize();
                    if (result.hasResolution()) {
                        // Not yet authorized — return false
                        callbackContext.success(0);
                        return;
                    }
                    String token = getAccessToken();

                    List<String> fileIds = DriveHelper.listFiles(token, FILE_NAME);
                    callbackContext.success(fileIds.isEmpty() ? 0 : 1);
                } catch (Exception e) {
                    // On network error, return false
                    callbackContext.success(0);
                }
            }
        });
    }

    // ---------------------------------------------------------------
    // Migration helpers
    // ---------------------------------------------------------------

    private void hasLocalFile(CallbackContext callbackContext) {
        File file = new File(getActivity().getFilesDir(), FILE_NAME);
        callbackContext.success(file.exists() ? 1 : 0);
    }

    private void deleteLocalFile(CallbackContext callbackContext) {
        File file = new File(getActivity().getFilesDir(), FILE_NAME);
        if (file.exists()) file.delete();
        callbackContext.success();
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /**
     * Blocking wrapper around Identity.getAuthorizationClient().authorize().
     * Must be called from a background thread (Cordova thread pool).
     * Returns the AuthorizationResult which callers check via hasResolution().
     */
    private AuthorizationResult authorize() throws ExecutionException, InterruptedException {
        Activity activity = cordova.getActivity();
        AuthorizationRequest request = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(DRIVE_APPDATA_SCOPE)))
                .build();
        return Tasks.await(Identity.getAuthorizationClient(activity).authorize(request));
    }

    /**
     * Get an access token from Play Services. Calls authorize() and returns
     * the token if the user has already granted consent. Throws if consent
     * is required (hasResolution). Must be called from a background thread.
     */
    private String getAccessToken() throws Exception {
        AuthorizationResult result = authorize();
        if (result.hasResolution()) {
            throw new Exception("Not authorized");
        }
        String token = result.getAccessToken();
        cachedAccessToken = token;
        return token;
    }

    /**
     * Fetch the user's email, retrying once on 401 (stale cached token).
     * After revokeAuth(), Play Services may briefly cache the old token.
     * If fetchEmail gets a 401, we clear the cache and re-authorize.
     * Must be called from a background thread.
     */
    private String fetchEmailWithRetry(String token) throws Exception {
        try {
            return DriveHelper.fetchEmail(token);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                d("fetchEmailWithRetry: token got 401, clearing and retrying");
                try {
                    com.google.android.gms.auth.GoogleAuthUtil.clearToken(
                            cordova.getActivity(), token);
                } catch (Exception clearEx) {
                    d("fetchEmailWithRetry: clearToken failed: " + clearEx.getMessage());
                }
                String freshToken = getAccessToken();
                return DriveHelper.fetchEmail(freshToken);
            }
            throw e;
        }
    }

    private boolean isFireOS() {
        return "Amazon".equalsIgnoreCase(android.os.Build.MANUFACTURER);
    }

    private Activity getActivity() {
        return cordova.getActivity();
    }

    // ---------------------------------------------------------------
    // Logging helpers
    // ---------------------------------------------------------------

    static void d(String message) {
        Log.d(TAG, message);
    }

    static void i(String message) {
        Log.i(TAG, message);
    }

    static void w(String message) {
        Log.w(TAG, message);
    }

    static void e(String message) {
        Log.e(TAG, message);
    }
}
