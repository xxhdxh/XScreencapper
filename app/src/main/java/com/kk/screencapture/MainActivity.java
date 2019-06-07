package com.kk.screencapture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.kk.screencapture.live.LiveKeeper;
import com.kk.screencapture.live.WindowHelper;
import com.kk.screencapture.util.ThreadUtil;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    private final int REQUEST_MEDIA_PROJECTION = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        WindowHelper.set1x1Window(getWindow());

//        LiveKeeper.keepLive(this);

        ThreadUtil.doRunDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "request for capture service");
                startActivityForResult(((MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION);
            }
        }, 3 * 1000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode != REQUEST_MEDIA_PROJECTION || resultCode != Activity.RESULT_OK) {
            super.onActivityResult(requestCode, resultCode, data);
            tooltip("截取失败");
        }

        ForegroundScreencapService.captureDataIntent = data;

        startService(new Intent(MainActivity.this, ForegroundScreencapService.class));

        finish();
    }

    /**
     * tooltip
     **/
    public void tooltip(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}


