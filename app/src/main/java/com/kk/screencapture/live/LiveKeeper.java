package com.kk.screencapture.live;

import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.kk.screencapture.MainActivity;

public class LiveKeeper {
    public static final String TAG = "LiveKeeper";

    public static void keepLive(AppCompatActivity activity) {
        final ScreenManager screenManager = ScreenManager.getInstance(activity);
        screenManager.setActivity(activity);

        ScreenBroadcastListener listener = new ScreenBroadcastListener(activity);

        listener.registerListener(new ScreenBroadcastListener.ScreenStateListener() {
            @Override
            public void onScreenOn() {
//                screenManager.finishActivity();
            }

            @Override
            public void onScreenOff() {
                Log.w(TAG, "try to start placeholder activity");
                screenManager.startActivity(MainActivity.class);
            }
        });
    }
}
