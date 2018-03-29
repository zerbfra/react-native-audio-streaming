package com.audioStreaming;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;

import android.os.IBinder;
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

public class ReactNativeAudioStreamingModule extends ReactContextBaseJavaModule implements ServiceConnection {

  private boolean initialStage = true;
  private boolean playPause;
  private MediaPlayer mediaPlayer;

  public static final String SHOULD_SHOW_NOTIFICATION = "showInAndroidNotifications";
  private ReactApplicationContext context;

  private Class<?> clsActivity;

  private Intent bindIntent;
  private String streamingURL;
  private boolean shouldShowNotification;

  @Override public String getName() {
    return "ReactNativeAudioStreaming";
  }

  public ReactNativeAudioStreamingModule(ReactApplicationContext reactContext) {
    
    super(reactContext);
    this.context = reactContext;
    this.shouldShowNotification = false;


    this.mediaPlayer = new MediaPlayer();
    this.mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
  }

  public ReactApplicationContext getReactApplicationContextModule() {
    return this.context;
  }

  public Class<?> getClassActivity() {
    if (this.clsActivity == null) {
      this.clsActivity = getCurrentActivity().getClass();
    }
    return this.clsActivity;
  }

  public void stopOncall() {
    // this.signal.stop();
  }


  public void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
    this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
    .emit(eventName, params);
  }


  @Override public void initialize() {
    super.initialize();

    try {
      //bindIntent = new Intent(this.context, Signal.class);
      //this.context.bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
    } catch (Exception e) {
      Log.e("ERROR", e.getMessage());
    }
  }


  private AudioManager.OnAudioFocusChangeListener focusChangeListener =
  new AudioManager.OnAudioFocusChangeListener() {
    public void onAudioFocusChange(int focusChange) {
      AudioManager am =(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
      switch (focusChange) {

        case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) :
                  // stopOncall();
        //signal.aTrack.setVolume(0.2f);
        break;
        case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) :
                  //stopOncall();
        //signal.aTrack.setVolume(0.0f);
        break;
        case (AudioManager.AUDIOFOCUS_LOSS) :
                  // stopOncall();
        //signal.aTrack.setVolume(0.0f);
        break;
        case (AudioManager.AUDIOFOCUS_GAIN) :
        //signal.aTrack.setVolume(1.0f);
        break;
        default: break;
      }
    }
  };

  @ReactMethod 
  public void play(String streamingURL, ReadableMap options) {

    AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
    int amResult = am.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

    if (amResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      this.streamingURL = streamingURL;
      this.shouldShowNotification = options.hasKey(SHOULD_SHOW_NOTIFICATION) && options.getBoolean(SHOULD_SHOW_NOTIFICATION);

      if (initialStage) {
        new Player().execute(this.streamingURL);
      } else {
        if (!mediaPlayer.isPlaying())
          mediaPlayer.start();
      }

    }
  }
/*
  private void playInternal() {
    signal.play();
    if (shouldShowNotification) {
      signal.showNotification();
    }
  }*/


  @ReactMethod 
  public void pause() {
    this.mediaPlayer.pause();
    if (this.mediaPlayer != null) {
      this.mediaPlayer.reset();
      this.mediaPlayer.release();
      this.mediaPlayer = null;
    }
  }


  @ReactMethod 
  public void destroyNotification() {
    //signal.exitNotification();
  }

  @ReactMethod 
  public void getStatus(Callback callback) {
    //WritableMap state = Arguments.createMap();
    //state.putString("status", signal != null && signal.isPlaying ? Mode.PLAYING : Mode.STOPPED);
    //callback.invoke(null, state);
  }



  /**** PLAYER CLASS *****/

  class Player extends AsyncTask<String, Void, Boolean> {
    @Override
    protected Boolean doInBackground(String... strings) {
      Boolean prepared = false;

      try {
        mediaPlayer.setDataSource(strings[0]);
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
          @Override
          public void onCompletion(MediaPlayer mediaPlayer) {
            initialStage = true;
            playPause = false;
            
            mediaPlayer.stop();
            mediaPlayer.reset();
          }
        });

        mediaPlayer.prepare();
        prepared = true;

      } catch (Exception e) {
        Log.e("MyAudioStreamingApp", e.getMessage());
        prepared = false;
      }

      return prepared;
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
      super.onPostExecute(aBoolean);

      mediaPlayer.start();
      initialStage = false;
    }

    @Override
    protected void onPreExecute() {
      super.onPreExecute();

      // buffering
    }
  }

}