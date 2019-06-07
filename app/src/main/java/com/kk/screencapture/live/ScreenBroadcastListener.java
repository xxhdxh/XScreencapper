package com.kk.screencapture.live;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class ScreenBroadcastListener {
    public static final String TAG = "ScreenBroadcastListener";

    private Context mContext;

    private ScreenBroadcastReceiver mScreenReceiver;

    private ScreenStateListener mListener;

    public ScreenBroadcastListener(Context context) {
        mContext = context.getApplicationContext();

        mScreenReceiver = new ScreenBroadcastReceiver();
    }

    public void registerListener(ScreenStateListener listener) {
        mListener = listener;
        registerListener();
    }

    private void registerListener() {
        IntentFilter filter = new IntentFilter();

        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        mContext.registerReceiver(mScreenReceiver, filter);
    }

    /**
     * screen状态广播接收者
     */
    private class ScreenBroadcastReceiver extends BroadcastReceiver {
        private String action = null;

        @Override
        public void onReceive(Context context, Intent intent) {
            action = intent.getAction();

            switch (action) {
                case Intent.ACTION_SCREEN_ON:
                    mListener.onScreenOn();
                    break;

                case Intent.ACTION_SCREEN_OFF:
                    mListener.onScreenOff();
                    break;

                default:
                    Log.w(TAG, "unsupport action!");
            }
        }
    }

    public interface ScreenStateListener {
        void onScreenOn();

        void onScreenOff();
    }
}