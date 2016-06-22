package com.adobe.phonegap.push;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GcmPubSub;
import com.google.android.gms.iid.InstanceID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

import me.leolin.shortcutbadger.ShortcutBadger;

import com.alibaba.sdk.android.AlibabaSDK;
import com.alibaba.sdk.android.SdkConstants;
import com.alibaba.sdk.android.callback.InitResultCallback;
import com.alibaba.sdk.android.push.CloudPushService;
import com.alibaba.sdk.android.push.CommonCallback;
import com.alibaba.sdk.android.push.noonesdk.PushServiceFactory;

public class PushPlugin extends CordovaPlugin implements PushConstants {

    public static final String LOG_TAG = "PushPlugin";

    private static CallbackContext pushContext;
    private static CordovaWebView gWebView;
    private static Bundle gCachedExtras = null;
    private static boolean gForeground = false;

    /**
     * Gets the application context from cordova's main activity.
     * @return the application context
     */
    private Context getApplicationContext() {
        return this.cordova.getActivity().getApplicationContext();
    }


    private void initOneSDK(final Context applicationContext,final JSONArray data, final CallbackContext callbackContext){
        Log.d(LOG_TAG, "get App Package name : " + applicationContext.getPackageName());

        AlibabaSDK.asyncInit(applicationContext, new InitResultCallback(){
            public void onSuccess(){
                Log.e(LOG_TAG, "init onesdk success");
                initAliyunCloudChannel(applicationContext, data, callbackContext);
            }

            
            public void onFailure(int code, String message){
                Log.e(LOG_TAG, "init onesdk failed : " + message);
            }

        });
    }


    private void initAliyunCloudChannel(Context applicationContext, final JSONArray data, final CallbackContext callbackContext){
        PushServiceFactory.init(applicationContext);
        CloudPushService pushService = PushServiceFactory.getCloudPushService();
        pushService.register(applicationContext, new CommonCallback(){
            @Override
            public void onSuccess(String response){
                Log.d(LOG_TAG, "init AliyunCloudChannel success, device id : " + PushServiceFactory.getCloudPushService().getDeviceId() + ",UtDid: " + PushServiceFactory.getCloudPushService().getUTDeviceId() + ", Appkey: " + AlibabaSDK.getGlobalProperty(SdkConstants.APP_KEY));
                initPush(data, callbackContext);
            }

            @Override
            public void onFailed(String errorCode, String errorMessage){
                Log.d(LOG_TAG, "init AliyunCloudChannel failed --errorCode:" + errorCode + " -- errorMessage:" + errorMessage);
            }
        });
    }


    private void initPush(final JSONArray data, final CallbackContext callbackContext){
        pushContext = callbackContext;
        JSONObject jo = null;

        Log.v(LOG_TAG, "execute: data=" + data.toString());
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(COM_ADOBE_PHONEGAP_PUSH, Context.MODE_PRIVATE);
        String token = null;
        String senderID = null;

        try {
            jo = data.getJSONObject(0).getJSONObject(ANDROID);

            Log.v(LOG_TAG, "execute: jo=" + jo.toString());

            senderID = jo.getString(SENDER_ID);

            Log.v(LOG_TAG, "execute: senderID=" + senderID);

            String savedSenderID = sharedPref.getString(SENDER_ID, "");
            String savedRegID = sharedPref.getString(REGISTRATION_ID, "");

            /*
            // first time run get new token
            if ("".equals(savedRegID)) {
                token = InstanceID.getInstance(getApplicationContext()).getToken(senderID, GCM);
            }
            // new sender ID, re-register
            else if (!savedSenderID.equals(senderID)) {
                token = InstanceID.getInstance(getApplicationContext()).getToken(senderID, GCM);
            }
            // use the saved one
            else {
                token = sharedPref.getString(REGISTRATION_ID, "");
            }
            */

            //使用aliyun push返回的设备号
            token = PushServiceFactory.getCloudPushService().getDeviceId();

            if (!"".equals(token)) {
                JSONObject json = new JSONObject().put(REGISTRATION_ID, token);

                Log.v(LOG_TAG, "onRegistered: " + json.toString());

                JSONArray topics = jo.optJSONArray(TOPICS);
                subscribeToTopics(topics, token);

                PushPlugin.sendEvent( json );
            } else {
                callbackContext.error("Empty registration ID received from GCM");
                return;
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "execute: Got JSON Exception " + e.getMessage());
            callbackContext.error(e.getMessage());
        } catch (Exception e) {
            Log.e(LOG_TAG, "execute: Got JSON Exception " + e.getMessage());
            callbackContext.error(e.getMessage());
        }

        if (jo != null) {
            SharedPreferences.Editor editor = sharedPref.edit();
            try {
                editor.putString(ICON, jo.getString(ICON));
            } catch (JSONException e) {
                Log.d(LOG_TAG, "no icon option");
            }
            try {
                editor.putString(ICON_COLOR, jo.getString(ICON_COLOR));
            } catch (JSONException e) {
                Log.d(LOG_TAG, "no iconColor option");
            }
            editor.putBoolean(SOUND, jo.optBoolean(SOUND, true));
            editor.putBoolean(VIBRATE, jo.optBoolean(VIBRATE, true));
            editor.putBoolean(CLEAR_NOTIFICATIONS, jo.optBoolean(CLEAR_NOTIFICATIONS, true));
            editor.putBoolean(FORCE_SHOW, jo.optBoolean(FORCE_SHOW, false));
            editor.putString(SENDER_ID, senderID);
            editor.putString(REGISTRATION_ID, token);
            editor.commit();
        }

        if (gCachedExtras != null) {
            Log.v(LOG_TAG, "sending cached extras");
            sendExtras(gCachedExtras);
            gCachedExtras = null;
        }
    }


    @Override
    public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
        Log.v(LOG_TAG, "execute: action=" + action);
        gWebView = this.webView;

        if (INITIALIZE.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    //初始化阿里云SDK
                    initOneSDK(getApplicationContext(), data, callbackContext);
                }
            });
        } else if (UNREGISTER.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(COM_ADOBE_PHONEGAP_PUSH, Context.MODE_PRIVATE);
                        String token = sharedPref.getString(REGISTRATION_ID, "");
                        JSONArray topics = data.optJSONArray(0);
                        if (topics != null && !"".equals(token)) {
                            unsubscribeFromTopics(topics, token);
                        } else {
                            InstanceID.getInstance(getApplicationContext()).deleteInstanceID();
                            Log.v(LOG_TAG, "UNREGISTER");

                            // Remove shared prefs
                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.remove(SOUND);
                            editor.remove(VIBRATE);
                            editor.remove(CLEAR_NOTIFICATIONS);
                            editor.remove(FORCE_SHOW);
                            editor.remove(SENDER_ID);
                            editor.remove(REGISTRATION_ID);
                            editor.commit();
                        }

                        callbackContext.success();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "execute: Got JSON Exception " + e.getMessage());
                        callbackContext.error(e.getMessage());
                }
            }
            });
        } else if (FINISH.equals(action)) {
            callbackContext.success();
        } else if (HAS_PERMISSION.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    JSONObject jo = new JSONObject();
                    try {
                        jo.put("isEnabled", PermissionUtils.hasPermission(getApplicationContext(), "OP_POST_NOTIFICATION"));
                        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, jo);
                        pluginResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(pluginResult);
                    } catch (UnknownError e) {
                        callbackContext.error(e.getMessage());
                    } catch (JSONException e) {
                        callbackContext.error(e.getMessage());
                    }
                }
            });
        } else if (SET_APPLICATION_ICON_BADGE_NUMBER.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Log.v(LOG_TAG, "setApplicationIconBadgeNumber: data=" + data.toString());
                    try {
                        setApplicationIconBadgeNumber(getApplicationContext(), data.getJSONObject(0).getInt(BADGE));
                    } catch (JSONException e) {
                        callbackContext.error(e.getMessage());
                    }
                    callbackContext.success();
                }
            });
        } else if (CLEAR_ALL_NOTIFICATIONS.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Log.v(LOG_TAG, "clearAllNotifications");
                    clearAllNotifications();
                    callbackContext.success();
                }
            });
        } else {
            Log.e(LOG_TAG, "Invalid action : " + action);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }

        return true;
    }

    public static void sendEvent(JSONObject _json) {
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, _json);
        pluginResult.setKeepCallback(true);
        if (pushContext != null) {
            pushContext.sendPluginResult(pluginResult);
        }
    }

    public static void sendError(String message) {
        PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, message);
        pluginResult.setKeepCallback(true);
        if (pushContext != null) {
            pushContext.sendPluginResult(pluginResult);
        }
    }

    /*
     * Sends the pushbundle extras to the client application.
     * If the client application isn't currently active, it is cached for later processing.
     */
    public static void sendExtras(Bundle extras) {
        if (extras != null) {
            if (gWebView != null) {
                sendEvent(convertBundleToJson(extras));
            } else {
                Log.v(LOG_TAG, "sendExtras: caching extras to send at a later time.");
                gCachedExtras = extras;
            }
        }
    }

    public static void setApplicationIconBadgeNumber(Context context, int badgeCount) {
        if (badgeCount > 0) {
            ShortcutBadger.applyCount(context, badgeCount);
        } else {
            ShortcutBadger.removeCount(context);
        }
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        gForeground = true;
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        gForeground = false;

        SharedPreferences prefs = getApplicationContext().getSharedPreferences(COM_ADOBE_PHONEGAP_PUSH, Context.MODE_PRIVATE);
        if (prefs.getBoolean(CLEAR_NOTIFICATIONS, true)) {
            clearAllNotifications();
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        gForeground = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        gForeground = false;
        gWebView = null;
    }

    private void clearAllNotifications() {
        final NotificationManager notificationManager = (NotificationManager) cordova.getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    private void subscribeToTopics(JSONArray topics, String registrationToken) {
        if (topics != null) {
            String topic = null;
            for (int i=0; i<topics.length(); i++) {
                try {
                    topic = topics.optString(i, null);
                    if (topic != null) {
                        Log.d(LOG_TAG, "Subscribing to topic: " + topic);
                        GcmPubSub.getInstance(getApplicationContext()).subscribe(registrationToken, "/topics/" + topic, null);
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Failed to subscribe to topic: " + topic, e);
                }
            }
        }
    }

    private void unsubscribeFromTopics(JSONArray topics, String registrationToken) {
        if (topics != null) {
            String topic = null;
            for (int i=0; i<topics.length(); i++) {
                try {
                    topic = topics.optString(i, null);
                    if (topic != null) {
                        Log.d(LOG_TAG, "Unsubscribing to topic: " + topic);
                        GcmPubSub.getInstance(getApplicationContext()).unsubscribe(registrationToken, "/topics/" + topic);
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Failed to unsubscribe to topic: " + topic, e);
                }
            }
        }
    }

    /*
     * serializes a bundle to JSON.
     */
    private static JSONObject convertBundleToJson(Bundle extras) {
        Log.d(LOG_TAG, "convert extras to json");
        try {
            JSONObject json = new JSONObject();
            JSONObject additionalData = new JSONObject();

            // Add any keys that need to be in top level json to this set
            HashSet<String> jsonKeySet = new HashSet();
            Collections.addAll(jsonKeySet, TITLE,MESSAGE,COUNT,SOUND,IMAGE);

            Iterator<String> it = extras.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                Object value = extras.get(key);

                Log.d(LOG_TAG, "key = " + key);

                if (jsonKeySet.contains(key)) {
                    json.put(key, value);
                }
                else if (key.equals(COLDSTART)) {
                    additionalData.put(key, extras.getBoolean(COLDSTART));
                }
                else if (key.equals(FOREGROUND)) {
                    additionalData.put(key, extras.getBoolean(FOREGROUND));
                }
                else if ( value instanceof String ) {
                    String strValue = (String)value;
                    try {
                        // Try to figure out if the value is another JSON object
                        if (strValue.startsWith("{")) {
                            additionalData.put(key, new JSONObject(strValue));
                        }
                        // Try to figure out if the value is another JSON array
                        else if (strValue.startsWith("[")) {
                            additionalData.put(key, new JSONArray(strValue));
                        }
                        else {
                            additionalData.put(key, value);
                        }
                    } catch (Exception e) {
                        additionalData.put(key, value);
                    }
                }
            } // while

            json.put(ADDITIONAL_DATA, additionalData);
            Log.v(LOG_TAG, "extrasToJSON: " + json.toString());

            return json;
        }
        catch( JSONException e) {
            Log.e(LOG_TAG, "extrasToJSON: JSON exception");
        }
        return null;
    }

    public static boolean isInForeground() {
      return gForeground;
    }

    public static boolean isActive() {
        return gWebView != null;
    }
}
