package com.audioStreaming;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

class SignalReceiver extends BroadcastReceiver {
    private final static String TAG = SignalReceiver.class.getSimpleName();
    private SignalService signalService;

    public SignalReceiver(SignalService signalService) {
        super();
        this.signalService = signalService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(SignalService.BROADCAST_PLAYBACK_PLAY)) {
            if (!this.signalService.isPlaying) {
                this.signalService.play();
            } else {
                Log.e(TAG, "stop() BROADCAST_PLAYBACK_PLAY");
                this.signalService.stop();
            }
        } else if (action.equals(SignalService.BROADCAST_EXIT)) {
            Log.e(TAG, "stop() BROADCAST_EXIT");
            this.signalService.stop();
        }
    }
}
