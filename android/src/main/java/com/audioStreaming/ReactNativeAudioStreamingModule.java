package com.audioStreaming;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
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
import android.app.Activity;

public class ReactNativeAudioStreamingModule extends ReactContextBaseJavaModule
        implements ServiceConnection {

  public static final String SHOULD_SHOW_NOTIFICATION = "showInAndroidNotifications";
  private ReactApplicationContext context;

  private Class<?> clsActivity;
  private static SignalService signal;
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
    Activity activity = getCurrentActivity();
    if (this.clsActivity == null && activity != null) {
      this.clsActivity = activity.getClass();
    }
    return this.clsActivity;
  }

  public void stopOncall() {
    if (this.signal != null) this.signal.stop();
  }

  public SignalService getSignal() {
    return signal;
  }

  public void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
    this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
  }

  @Override public String getName() {
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
    signal = ((SignalService.SignalBinder) service).getService();
    signal.setData(this.context, this);
    if (play) {
      playInternal();
    }
    WritableMap params = Arguments.createMap();
    sendEvent(this.getReactApplicationContextModule(), "streamingOpen", params);
  }

  @Override
  public void onServiceDisconnected(ComponentName className) {
    signal = null;
  }

  @ReactMethod
  public void play(String streamingURL, ReadableMap options) {


    AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
    int amResult = am.requestAudioFocus(focusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN);

    if (amResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      this.streamingURL = streamingURL;
      this.shouldShowNotification = options.hasKey(SHOULD_SHOW_NOTIFICATION) && options.getBoolean(SHOULD_SHOW_NOTIFICATION);
      playInternal();

    }

  }

  @ReactMethod
  public void stop() {
    play = false;
    if (signal != null) signal.stop();
  }

  @ReactMethod
  void updateTrackInfo(ReadableMap info) {
    if(info.hasKey("trackTitle") && info.hasKey("trackArtist")) {
      signal.updateMetadata(info.getString("trackTitle"), info.getString("trackArtist"));
    }
  }

  @ReactMethod
  public void pause() {
    this.stop();
  }

  @ReactMethod
  public void resume() {
    playInternal();
  }

  @ReactMethod
  public void destroyNotification() {
    signal.exitNotification();
  }

  @ReactMethod
  public void getStatus(Callback callback) {
    WritableMap state = Arguments.createMap();
    state.putString("status", isServicePlaying() ? Mode.PLAYING : Mode.STOPPED);
    if (signal != null) {
      state.putString("streamUrl", signal.getStreamingURL());
    }
    callback.invoke(null, state);
  }

  private boolean isServicePlaying() {
    return signal != null && signal.isPlaying();
  }

  private void playInternal() {
    play = true;
    if (!isServicePlaying() && signal != null) {
      signal.setURLStreaming(streamingURL); // URL of MP3 or AAC stream
      signal.play();

    }
  }

  private AudioManager.OnAudioFocusChangeListener focusChangeListener =
          new AudioManager.OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
              AudioManager am =(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
              switch (focusChange) {

                case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) :
                  // Lower the volume while ducking.
                  signal.setVolume(0.2f);
                  break;
                case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) :
                  stop();
                  break;

                case (AudioManager.AUDIOFOCUS_LOSS) :
                  stop();
                  break;

                case (AudioManager.AUDIOFOCUS_GAIN) :
                  signal.setVolume(1.0f);
                  break;
                default: break;
              }
            }
          };


}