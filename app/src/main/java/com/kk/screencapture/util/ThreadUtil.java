package com.kk.screencapture.util;

public class ThreadUtil {
    /**
     * 安静的睡
     **/
    public static void sleepQuitelySecs(long secs) {
        sleepQuitelyMills(secs * 1000);
    }

    public static void sleepQuitelyMills(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 运行
     **/
    public static void doRun(Runnable runnable) {
        new Thread(runnable).start();
    }

    /**
     * 延迟执行
     **/
    public static void doRunDelayed(Runnable runnable, long secs) {
        sleepQuitelyMills(secs);

        doRun(runnable);
    }
}
