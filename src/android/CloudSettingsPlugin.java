package cordova.plugin.cloudsettings;

import android.app.Activity;
import android.app.backup.BackupManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;


public class CloudSettingsPlugin extends CordovaPlugin {

    static final String LOG_TAG = "CloudSettingsPlugin";
    static final String LOG_TAG_JS = "CloudSettingsPlugin[native]";
    static final Object sDataLock = new Object();
    static String javascriptNamespace = "cordova.plugin.cloudsettings";

    protected boolean debugEnabled = false;

    public static CloudSettingsPlugin instance = null;
    static CordovaWebView webView;
    static BackupManager bm;

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        bm = new BackupManager(cordova.getActivity().getApplicationContext());
        instance = this;
        this.webView = webView;
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        boolean success = true;
        try {
            if (action.equals("enableDebug")) {
                setDebug(true, callbackContext);
            }else if (action.equals("saveBackup")) {
                saveBackup(args, callbackContext);
            }else if (action.equals("saveBackup")) {
                saveBackup(args, callbackContext);
            }else {
                handleError("Invalid action: " + action);
                success = false;
            }
        } catch (Exception e) {
            handleException(e);
            success = false;
        }
        return success;
    }

    protected void setDebug(boolean enabled, CallbackContext callbackContext) {
        debugEnabled = enabled;
        d("debug: " + String.valueOf(enabled));
        sendPluginResultOk(callbackContext);
    }

    protected void saveBackup(JSONArray args, CallbackContext callbackContext) throws JSONException {
        d("Requesting Backup");
        bm.dataChanged();
        sendPluginResultOk(callbackContext);
    }

    protected static void handleException(Exception e, String description) {
        handleError("EXCEPTION: " + description + ": " + e.getMessage());
    }

    protected static void handleException(Exception e) {
        handleError("EXCEPTION: " + e.getMessage());
    }

    protected static void handleError(String error) {
        e(error);
    }

    protected static void executeGlobalJavascript(final String jsString) {
        instance.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    webView.loadUrl("javascript:" + jsString);
                } catch (Exception e) {
                    instance.handleException(e);
                }
            }
        });
    }

    protected static void jsCallback(String name) {
        String jsStatement = String.format(javascriptNamespace + "[\"%s\"]();", name);
        executeGlobalJavascript(jsStatement);
    }

    protected static String jsQuoteEscape(String js) {
        js = js.replace("\"", "\\\"");
        return "\"" + js + "\"";
    }

    protected Activity getActivity() {
        return this.cordova.getActivity();
    }

    protected void sendPluginResultOk(CallbackContext callbackContext) {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
    }

    protected void sendPluginResultError(String errorMessage, CallbackContext callbackContext) {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, errorMessage));
    }

    protected static void onRestore() {
        if(instance != null){
            jsCallback("_onRestore");
        }
    }

    protected static void d(String message) {
        Log.d(LOG_TAG, message);
        if (instance != null && instance.debugEnabled) {
            message = LOG_TAG_JS + ": " + message;
            message = instance.jsQuoteEscape(message);
            instance.executeGlobalJavascript("console.log("+message+")");
        }
    }

    protected static void i(String message) {
        Log.i(LOG_TAG, message);
        if (instance != null && instance.debugEnabled) {
            message = LOG_TAG_JS + ": " + message;
            message = instance.jsQuoteEscape(message);
            instance.executeGlobalJavascript("console.info("+message+")");
        }
    }

    protected static void w(String message) {
        Log.w(LOG_TAG, message);
        if (instance != null && instance.debugEnabled) {
            message = LOG_TAG_JS + ": " + message;
            message = instance.jsQuoteEscape(message);
            instance.executeGlobalJavascript("console.warn("+message+")");
        }
    }

    protected static void e(String message) {
        Log.e(LOG_TAG, message);
        if (instance != null && instance.debugEnabled) {
            message = LOG_TAG_JS + ": " + message;
            message = instance.jsQuoteEscape(message);
            instance.executeGlobalJavascript("console.error("+message+")");
        }
    }
}
