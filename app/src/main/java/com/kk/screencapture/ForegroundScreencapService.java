package com.kk.screencapture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SyncContext;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.kk.screencapture.util.FixedLenQueue;
import com.kk.screencapture.util.IOUtil;
import com.kk.screencapture.util.ThreadUtil;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 前台截图进程
 **/
public class ForegroundScreencapService extends Service {
    private static final String TAG = "ScreencapService";

    // 防止多次调用start
    private volatile boolean isStarted = false;
    public volatile boolean isCapturing = false;

    // 启动
    public static final String ACTION_START = "start";
    // 退出
    public static final String ACTION_STOP = "stop";

    private static final String CHANNEL_ID = "screen cap";
    private static final int NOTI_ID = 1;
    private static final String CHANNEL_NAME = "X-Screencap";
    private static final String CHANNEL_DESCRIPTION = "live screen capping...";

    WindowManager mWindowManager;
    public int mScreenWidth = 270;
    public int mScreenHeight = 480;
    private int mScreenDensity;

    MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;

    public static Intent captureDataIntent;

    Timer captureTimer = null;
    TimerTask captureTask = null;

    // 默认截图间隔
    public long captureIntervalMillis = 1;
    private int maxBufferedSize = 60;

    FixedLenQueue<Image> imgBufferedQueue = new FixedLenQueue<>(maxBufferedSize);
    CaptureServer captureServer;

    @Override
    public void onCreate() {
        super.onCreate();

        // 获取屏幕信息
        getScreenInfo();

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        captureServer = new CaptureServer(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return super.onStartCommand(intent, flags, startId);
        }

        String action = intent.getAction();

        if (action == null) {
            tryStartCapture();
            return START_STICKY;
        }

        switch (action) {
            case ACTION_START:
                tryStartCapture();
                break;

            case ACTION_STOP:
                stop();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        releaseCaptureReader();

        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }

        captureServer.release();

        stopCaptureTimer();

        isStarted = false;
    }

    /**
     * 创建通知的渠道
     **/
    @RequiresApi(Build.VERSION_CODES.O)
    private void createChannel() {
        NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
        if (mNotificationManager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(CHANNEL_DESCRIPTION);
        channel.setShowBadge(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        mNotificationManager.createNotificationChannel(channel);
    }

    /**
     * 启动前台服务
     **/
    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel();
        }

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(CHANNEL_NAME);
        bigTextStyle.bigText(CHANNEL_DESCRIPTION);

        // 前台服务的通知
        Notification foreNoti = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(bigTextStyle)
            .setWhen(System.currentTimeMillis())
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
            .build();

        startForeground(NOTI_ID, foreNoti);
    }

    private void stop() {
        stopForeground(true);
        stopSelf();
    }

    /**
     * 获取屏幕信息
     **/
    private void getScreenInfo() {
        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        DisplayMetrics metrics = new DisplayMetrics();

        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
    }

    /**
     * 启动截屏服务
     **/
    private void tryStartCapture() {
        if (isStarted) {
            return;
        }

        if (null == captureDataIntent) {
            Log.e(TAG, "capture intent cannot be empty");
            stop();
            return;
        }

        startForegroundService();

        Log.i(TAG, "starting socketio server...");
        captureServer.startDaemon();
        captureServer.loopBroadcastCaptures();

        mMediaProjection = mMediaProjectionManager.getMediaProjection(Activity.RESULT_OK, captureDataIntent);

        // 启动截屏进程
        startCaptureProcess();

        isStarted = true;
    }

    /**
     * 启动截屏进程
     **/
    public void startCaptureProcess() {
        if (isCapturing) {
            return;
        }

        Log.i(TAG, "starting capture process...");

        // 创建截屏读取器
        createCaptureReader();

        // 启动截图定时器
        startCaptureTimer();

        Log.i(TAG, "success to start capture process");

        isCapturing = true;
    }

    /**
     * 停止截屏
     **/
    public void stopCaptureProcess() {
        if (!isCapturing) {
            return;
        }

        Log.i(TAG, "stoping capture process...");
        stopCaptureTimer();

        releaseCaptureReader();

        imgBufferedQueue.queue.clear();

        Log.i(TAG, "success to stop capture process");

        isCapturing = false;
    }

    public void restartCaptureProcess() {
        stopCaptureProcess();

        ThreadUtil.doRunDelayed(new Runnable() {
            @Override
            public void run() {
                startCaptureProcess();
            }
        }, 3);
    }

    /**
     * 创建截屏读取器
     **/
    private void createCaptureReader() {
        if (null == mImageReader) {
            mImageReader = ImageReader.newInstance(mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, maxBufferedSize);
        }

        if (null == mVirtualDisplay) {
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                mScreenWidth, mScreenHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
        }
    }

    /**
     * 销毁截屏读取器
     **/
    private void releaseCaptureReader() {
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }

        if (null != mVirtualDisplay) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    /**
     * 启动截图定时器
     **/
    public void startCaptureTimer() {
        // 停止旧的任务
        stopCaptureTimer();

        captureTimer = new Timer();
        captureTask = new TimerTask() {
            @Override
            public void run() {
                capture();
            }
        };

        captureTimer.scheduleAtFixedRate(captureTask, 3000, captureIntervalMillis);
    }

    /**
     * 停止旧的定时器
     **/
    public void stopCaptureTimer() {
        if (null != captureTask) {
            captureTask.cancel();
            captureTask = null;
        }

        if (null != captureTimer) {
            captureTimer.cancel();
            captureTimer.purge();
            captureTimer = null;
        }
    }

    AtomicLong captureCount = new AtomicLong();

    private void capture() {
        try {
            int queueSize = imgBufferedQueue.size();

            if (maxBufferedSize - queueSize < 5) {
                ThreadUtil.sleepQuitelySecs(3);
                Log.w(TAG, String.format("capture queue is almost full:%s", queueSize));
                return;
            }

            if (captureServer.sidList.size() == 0) {
                Log.w(TAG, "no client connect!!!");
                ThreadUtil.sleepQuitelySecs(10);
                return;
            }

            Image capturedImg = mImageReader.acquireLatestImage();

            if (capturedImg == null) {
                return;
            }

            long count = captureCount.addAndGet(1);

            Image oldestImg = imgBufferedQueue.offer(capturedImg);

            Log.i(TAG, String.format("capture count:%s,queue:%s", count, imgBufferedQueue.size()));

            IOUtil.closeQuitely(oldestImg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
