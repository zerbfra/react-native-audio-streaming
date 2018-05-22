package com.audioStreaming;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import javax.annotation.Nullable;

public class ReactNativeAudioStreamingModule extends ReactContextBaseJavaModule
        implements ServiceConnection {

    private final static String TAG = ReactNativeAudioStreamingModule.class.getSimpleName();

    public static final String SHOULD_SHOW_NOTIFICATION = "showInAndroidNotifications";
    private ReactApplicationContext context;

    private Class<?> clsActivity;
    private static SignalService signalService;
    private Intent bindIntent;
    private String streamingURL;
    private boolean play = false;
    private boolean shouldShowNotification;

    public ReactNativeAudioStreamingModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
    }

    public ReactApplicationContext getReactApplicationContextModule() {
        return this.context;
    }

    public Class<?> getClassActivity() {
        if (this.clsActivity == null && getCurrentActivity() != null) {
            this.clsActivity = getCurrentActivity().getClass();
        }
        return this.clsActivity;
    }

    public void stopOncall() {
        Log.e(TAG, "stop() stopOncall");
        if (signalService != null)
            this.signalService.stop();
    }

    public SignalService getSignal() {
        return signalService;
    }

    public void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    @Override
    public String getName() {
        return "ReactNativeAudioStreaming";
    }

    @Override
    public void initialize() {
        super.initialize();

        try {
            bindIntent = new Intent(this.context, SignalService.class);
            this.context.startService(bindIntent);
            this.context.bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e("ERROR", e.getMessage());
        }
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        signalService = ((SignalService.SignalBinder) service).getService();
        signalService.setData(this.context, this);
        if (play) {
            playInternal();
        }
        WritableMap params = Arguments.createMap();
        sendEvent(this.getReactApplicationContextModule(), "streamingOpen", params);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        signalService = null;
    }

    @ReactMethod
    public void play(String streamingURL, ReadableMap options) {
        this.streamingURL = streamingURL;
        this.shouldShowNotification =
                options.hasKey(SHOULD_SHOW_NOTIFICATION) && options.getBoolean(SHOULD_SHOW_NOTIFICATION);
        playInternal();
    }

    private void playInternal() {
        play = true;
        if (signalService != null) {
            if (isServicePlaying() && TextUtils.equals(signalService.getStreamingURL(), streamingURL)) {
                // do nothing
            } else {
                signalService.setURLStreaming(streamingURL); // URL of MP3 or AAC stream
                signalService.play();
            }
//            if (shouldShowNotification) {
//                signalService.showNotification();
//            }
        }
    }

    @ReactMethod
    public void stop() {
        Log.e(TAG, "stop() stop() from react code");
        play = false;
        if (signalService != null)
            signalService.stop();
    }

    @ReactMethod
    public void pause() {
        // Not implemented on aac
        Log.e(TAG, "stop() from pause()");
        this.stop();
    }

    @ReactMethod
    public void resume() {
        // Not implemented on aac
        playInternal();
    }

    @ReactMethod
    public void destroyNotification() {
        signalService.exitNotification();
    }

    private boolean isServicePlaying() {
        return signalService != null && signalService.isPlaying;
    }

    @ReactMethod
    public void getStatus(Callback callback) {
        WritableMap state = Arguments.createMap();
        state.putString("status", isServicePlaying() ? Mode.PLAYING : Mode.STOPPED);
        if (signalService != null) {
            state.putString("streamUrl", signalService.getStreamingURL());
        }
        callback.invoke(null, state);
    }
}
