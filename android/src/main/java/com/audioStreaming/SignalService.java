package com.audioStreaming;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;

import android.support.v4.app.NotificationCompat;

import com.spoledge.aacdecoder.MultiPlayer;
import com.spoledge.aacdecoder.PlayerCallback;

public class SignalService extends Service implements OnErrorListener,
        OnCompletionListener,
        OnPreparedListener,
        OnInfoListener,
        PlayerCallback {

    private final static String TAG = SignalService.class.getSimpleName();

    // Notification
    private Class<?> clsActivity;
    private static final int NOTIFY_ME_ID = 696969;
    private Notification notification;
    private NotificationCompat.Builder notifyBuilder;
    private NotificationManager notifyManager = null;
    private MultiPlayer aacPlayer;

    private static final int AAC_BUFFER_CAPACITY_MS = 2500;
    private static final int AAC_DECODER_CAPACITY_MS = 700;

    public static final String BROADCAST_PLAYBACK_STOP = "stop",
            BROADCAST_PLAYBACK_PLAY = "pause",
            BROADCAST_EXIT = "stop";

    private final IBinder binder = new SignalBinder();
    private final SignalReceiver receiver = new SignalReceiver(this);
    private Context context;
    private String streamingURL;
    public boolean isPlaying = false;
    private boolean isPreparingStarted = false;
    private EventsReceiver eventsReceiver;

    private TelephonyManager phoneManager;
    private PhoneListener phoneStateListener;

    public void setData(Context context, ReactNativeAudioStreamingModule module) {
        this.context = context;
        this.clsActivity = module.getClassActivity();

        eventsReceiver = new EventsReceiver(module);


        registerReceiver(eventsReceiver, new IntentFilter(Mode.CREATED));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.DESTROYED));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.STARTED));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.CONNECTING));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.START_PREPARING));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.PREPARED));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.PLAYING));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.STOPPED));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.COMPLETED));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.ERROR));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.BUFFERING_START));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.BUFFERING_END));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.METADATA_UPDATED));
        registerReceiver(eventsReceiver, new IntentFilter(Mode.ALBUM_UPDATED));


        phoneStateListener = new PhoneListener(module);
        phoneManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (phoneManager != null) {
            phoneManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }

        runAsForeground();
    }

    @Override
    public void onCreate() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BROADCAST_PLAYBACK_STOP);
        intentFilter.addAction(BROADCAST_PLAYBACK_PLAY);
        intentFilter.addAction(BROADCAST_EXIT);
        registerReceiver(this.receiver, intentFilter);


        try {
            this.aacPlayer = new MultiPlayer(this, AAC_BUFFER_CAPACITY_MS, AAC_DECODER_CAPACITY_MS);
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            java.net.URL.setURLStreamHandlerFactory(new java.net.URLStreamHandlerFactory() {
                public java.net.URLStreamHandler createURLStreamHandler(String protocol) {
                    if ("icy".equals(protocol)) {
                        return new com.spoledge.aacdecoder.IcyURLStreamHandler();
                    }
                    return null;
                }
            });
        } catch (Throwable t) {

        }

        sendBroadcast(new Intent(Mode.CREATED));
    }

    public void setURLStreaming(String streamingURL) {
        this.streamingURL = streamingURL;
    }

    public String getStreamingURL() {
        return streamingURL;
    }

    public void play() {
        if (isConnected()) {
            //Log.e(TAG, "play: isConnected");
            startForeground(NOTIFY_ME_ID, notification);
            this.prepare();
        } else {
            sendBroadcast(new Intent(Mode.STOPPED));
        }

        this.isPlaying = true;
    }

    public void stop() {
        stopForeground(true);
        this.isPreparingStarted = false;
        Log.d("VEDO","Notify App STOP");

        Log.e(TAG, "stop");
        if (this.isPlaying) {
            this.isPlaying = false;
            this.aacPlayer.stop();
        }

        sendBroadcast(new Intent(Mode.STOPPED));
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

        Intent notificationIntent = new Intent(this, clsActivity);
        PendingIntent pendingIntent=PendingIntent.getActivity(this, 0,
                notificationIntent, Intent.FLAG_ACTIVITY_NEW_TASK);

        notifyBuilder.setContentIntent(pendingIntent);

        notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel("com.audioStreaming", "Audio Streaming",
                            NotificationManager.IMPORTANCE_HIGH);
            if (notifyManager != null) {
                notifyManager.createNotificationChannel(channel);
            }

            //notifyBuilder.setChannelId("com.audioStreaming");
            //notifyBuilder.setOnlyAlertOnce(true);

        }
        notification = notifyBuilder.build();
        //notification.bigContentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.streaming_notification_player);
        // notification.bigContentView.setOnClickPendingIntent(R.id.btn_streaming_notification_play, makePendingIntent(Mode.));
        //notification.bigContentView.setOnClickPendingIntent(R.id.btn_streaming_notification_stop, makePendingIntent(Mode.STOPPED));
    }

    private PendingIntent makePendingIntent(String broadcast) {
        Intent intent = new Intent(broadcast);
        return PendingIntent.getBroadcast(this.context, 0, intent, 0);
    }

    public void clearNotification() {
        if (notifyManager != null)
            notifyManager.cancel(NOTIFY_ME_ID);
    }

    public void exitNotification() {
        notifyManager.cancelAll();
        clearNotification();
        notifyBuilder = null;
        notifyManager = null;
    }

    public boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }
        return false;
    }

    public void prepare() {
        /* ------Station- buffering-------- */
        this.isPreparingStarted = true;
        sendBroadcast(new Intent(Mode.START_PREPARING));
        try {
            aacPlayer.stop();
        } catch (Exception e) {
            Log.e(TAG, "Player was already stopped", e);
        }
        try {
            aacPlayer.playAsync(streamingURL);
        } catch (Exception e) {
            Log.e(TAG, "Player error", e);
            //stop();
            e.printStackTrace();
            playerException(e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (this.isPlaying) {
            sendBroadcast(new Intent(Mode.PLAYING));
        } else if (this.isPreparingStarted) {
            sendBroadcast(new Intent(Mode.START_PREPARING));
        } else {
            sendBroadcast(new Intent(Mode.STARTED));
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onPrepared(MediaPlayer _mediaPlayer) {
        this.isPreparingStarted = false;
        sendBroadcast(new Intent(Mode.PREPARED));
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        this.isPlaying = false;
        this.aacPlayer.stop();
        sendBroadcast(new Intent(Mode.COMPLETED));
        Log.e(TAG, "onCompletion: ");
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (what == 701) {
            this.isPlaying = false;
            sendBroadcast(new Intent(Mode.BUFFERING_START));
        } else if (what == 702) {
            this.isPlaying = true;
            sendBroadcast(new Intent(Mode.BUFFERING_END));
        }
        return false;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                //Log.v("ERROR", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK "	+ extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                //Log.v("ERROR", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                //Log.v("ERROR", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        sendBroadcast(new Intent(Mode.ERROR));
        return false;
    }

    @Override
    public void playerStarted() {
        //  TODO
    }

    @Override
    public void playerPCMFeedBuffer(boolean isPlaying, int bufSizeMs, int bufCapacityMs) {
        if (isPlaying) {
            this.isPreparingStarted = false;
            if (bufSizeMs < 500) {
                this.isPlaying = false;
                sendBroadcast(new Intent(Mode.BUFFERING_START));
                //buffering
            } else {
                this.isPlaying = true;
                sendBroadcast(new Intent(Mode.PLAYING));
                //playing
            }
        } else {
            //buffering
            this.isPlaying = false;
            sendBroadcast(new Intent(Mode.BUFFERING_START));
        }
    }

    @Override
    public void playerException(final Throwable t) {
        this.isPlaying = false;
        this.isPreparingStarted = false;
        sendBroadcast(new Intent(Mode.ERROR));
        //  TODO
    }

    @Override
    public void playerMetadata(final String key, final String value) {
        Intent metaIntent = new Intent(Mode.METADATA_UPDATED);
        metaIntent.putExtra("key", key);
        metaIntent.putExtra("value", value);
        sendBroadcast(metaIntent);
        
        if (key != null && key.equals("StreamTitle") && notification != null && value != null) {
            String cleanedMeta = value.replaceAll("@.*@", "");
            String[] separatedMeta = cleanedMeta.split(" - ");
            notifyBuilder.setContentTitle(separatedMeta[0]);
            notifyBuilder.setContentText(separatedMeta[1]);
            
            notifyManager.notify(NOTIFY_ME_ID, notifyBuilder.build());
        }
    }

    @Override
    public void playerAudioTrackCreated(AudioTrack atrack) {
        //  TODO
    }

    @Override
    public void playerStopped(int perf) {
        this.isPlaying = false;
        this.isPreparingStarted = false;
        sendBroadcast(new Intent(Mode.STOPPED));
        //  TODO
    }


}
