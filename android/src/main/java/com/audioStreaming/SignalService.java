
package com.audioStreaming;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.os.Build;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.pm.ApplicationInfo;

import com.facebook.infer.annotation.Assertions;
import com.facebook.react.bridge.ReactMethod;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.metadata.id3.ApicFrame;
import com.google.android.exoplayer2.metadata.id3.GeobFrame;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;
import com.google.android.exoplayer2.metadata.id3.PrivFrame;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.metadata.Metadata;

import android.support.v4.app.NotificationCompat;

import java.io.IOException;
import java.util.List;

public class SignalService extends Service implements ExoPlayer.EventListener, MetadataRenderer.Output, ExtractorMediaSource.EventListener {
    private static final String TAG = "ReactNative";

    // Notification
    private Class<?> clsActivity;
    private Notification notification;
    private static final int NOTIFY_ME_ID = 696969;
    private NotificationCompat.Builder notifyBuilder;
    private NotificationManager notifyManager = null;
    public static RemoteViews remoteViews;

    // Player
    private SimpleExoPlayer player = null;

    public static final String BROADCAST_PLAYBACK_STOP = "stop",
            BROADCAST_PLAYBACK_PLAY = "pause",
            BROADCAST_EXIT = "exit";

    private final IBinder binder = new SignalBinder();
    private final SignalReceiver receiver = new SignalReceiver(this);
    private Context context;
    private String streamingURL;
    private EventsReceiver eventsReceiver;
    private ReactNativeAudioStreamingModule module;

    private TelephonyManager phoneManager;
    private PhoneListener phoneStateListener;

    @Override
    public void onCreate() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BROADCAST_PLAYBACK_STOP);
        intentFilter.addAction(BROADCAST_PLAYBACK_PLAY);
        intentFilter.addAction(BROADCAST_EXIT);
        registerReceiver(this.receiver, intentFilter);
    }

    public class SignalBinder extends Binder {
        public SignalService getService() {
            return SignalService.this;
        }
    }

    private void runAsForeground() {
        notifyBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play) // TODO Use app icon instead
                .setContentTitle("TRX Radio")
                .setContentText("Caricamento in corso...");

        notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("com.audioStreaming", "Audio Streaming", NotificationManager.IMPORTANCE_HIGH);
            if (notifyManager != null) {
                notifyManager.createNotificationChannel(channel);
            }


        }
        notification = notifyBuilder.build();
    }


    public void setURLStreaming(String streamingURL) {
        this.streamingURL = streamingURL;
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    public void setData(Context context, ReactNativeAudioStreamingModule module) {
        this.context = context;
        this.clsActivity = module.getClassActivity();
        this.module = module;

        this.eventsReceiver = new EventsReceiver(this.module);

        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.CREATED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.IDLE));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.DESTROYED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.STARTED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.CONNECTING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.PLAYING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.READY));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.STOPPED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.PAUSED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.COMPLETED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.ERROR));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.BUFFERING_START));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.BUFFERING_END));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.METADATA_UPDATED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.ALBUM_UPDATED));

        this.phoneStateListener = new PhoneListener(this.module);
        this.phoneManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (this.phoneManager != null) {
            this.phoneManager.listen(this.phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }

        runAsForeground();
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d("onPlayerStateChanged", "" + playbackState);

        switch (playbackState) {
            case ExoPlayer.STATE_IDLE:
                sendBroadcast(new Intent(Mode.IDLE));
                break;
            case ExoPlayer.STATE_BUFFERING:

                sendBroadcast(new Intent(Mode.BUFFERING_START));
                break;
            case ExoPlayer.STATE_READY:
                if (this.player != null && this.player.getPlayWhenReady()) {
                    sendBroadcast(new Intent(Mode.PLAYING));
                } else {
                    sendBroadcast(new Intent(Mode.READY));
                }
                break;
            case ExoPlayer.STATE_ENDED:
                sendBroadcast(new Intent(Mode.STOPPED));
                break;
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
    }


    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.d(TAG, error.getMessage());
        sendBroadcast(new Intent(Mode.ERROR));
    }

    @Override
    public void onPositionDiscontinuity() {

    }

    private static String getDefaultUserAgent() {
        StringBuilder result = new StringBuilder(64);
        result.append("Dalvik/");
        result.append(System.getProperty("java.vm.version")); // such as 1.1.0
        result.append(" (Linux; U; Android ");

        String version = Build.VERSION.RELEASE; // "1.0" or "3.4b5"
        result.append(version.length() > 0 ? version : "1.0");

        // add the model for the release build
        if ("REL".equals(Build.VERSION.CODENAME)) {
            String model = Build.MODEL;
            if (model.length() > 0) {
                result.append("; ");
                result.append(model);
            }
        }
        String id = Build.ID; // "MASTER" or "M4-rc20"
        if (id.length() > 0) {
            result.append(" Build/");
            result.append(id);
        }
        result.append(")");
        return result.toString();
    }

    /**
     * Player controls
     */

    void updateMetadata(String title, String artist) {
        notifyBuilder.setContentTitle(title);
        notifyBuilder.setContentText(artist);
        notifyManager.notify(NOTIFY_ME_ID, notifyBuilder.build());
    }

    public void play() {
        startForeground(NOTIFY_ME_ID, notification);
        /*
        if (player != null) {
            player.setPlayWhenReady(false);
            player.stop();
            player.seekTo(0);
        }
        */

        // Create player
        Handler mainHandler = new Handler();
        TrackSelector trackSelector = new DefaultTrackSelector();
        LoadControl loadControl = new DefaultLoadControl();
        this.player = ExoPlayerFactory.newSimpleInstance(this.getApplicationContext(), trackSelector, loadControl);

        // Create source
        ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this.getApplication(), getDefaultUserAgent(), bandwidthMeter);
        MediaSource audioSource = new ExtractorMediaSource(Uri.parse(this.streamingURL), dataSourceFactory, extractorsFactory, mainHandler, this);

        // Start preparing audio
        player.prepare(audioSource);
        player.addListener(this);
        player.setPlayWhenReady(true);
    }

    public void start() {
        Assertions.assertNotNull(player);
        player.setPlayWhenReady(true);
        sendBroadcast(new Intent(Mode.PLAYING));
    }

    public void pause() {
        Assertions.assertNotNull(player);
        player.setPlayWhenReady(false);
        sendBroadcast(new Intent(Mode.STOPPED));
    }

    public void resume() {
        Assertions.assertNotNull(player);
        player.setPlayWhenReady(true);
    }

    public void stop() {
        Assertions.assertNotNull(player);
        stopForeground(true);
        player.setPlayWhenReady(false);
        sendBroadcast(new Intent(Mode.STOPPED));
    }

    public boolean isPlaying() {
        return player != null && player.getPlayWhenReady() && player.getPlaybackState() != ExoPlayer.STATE_ENDED;
    }

    /**
     * Meta data information
     */

    @Override
    public void onMetadata(Metadata metadata) {

    }

    /**
     * Notification control
     */

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }


    public void clearNotification() {
        if (notifyManager != null) {
            notifyManager.cancel(NOTIFY_ME_ID);
        }
    }

    public void exitNotification() {
        notifyManager.cancelAll();
        clearNotification();
        notifyBuilder = null;
        notifyManager = null;
    }

    public String getStreamingURL() {
        return this.streamingURL;
    }

    @Override
    public void onLoadError(IOException error) {
        Log.e(TAG, error.getMessage());
    }
}