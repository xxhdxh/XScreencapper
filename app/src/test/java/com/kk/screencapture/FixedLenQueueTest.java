package com.kk.screencapture;

import com.kk.screencapture.util.FixedLenQueue;
import com.kk.screencapture.util.ThreadUtil;

import org.junit.Test;

public class FixedLenQueueTest {

    @Test
    public void basicTest() {
        System.out.println("test--->"+Thread.currentThread().getName());
        final FixedLenQueue<Integer> numsQueue = new FixedLenQueue<>(10);

        numsQueue.offer(1);
        numsQueue.offer(2);
        numsQueue.offer(3);

        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("chidl--->"+Thread.currentThread().getName());
                ThreadUtil.sleepQuitelyMills(5 * 1000);
                numsQueue.offer(4);
            }
        }).start();

        System.out.print("--->" + numsQueue.take());
        System.out.print("--->" + numsQueue.take());
        System.out.print("--->" + numsQueue.take());

        System.out.print("--->" + numsQueue.take());
    }
}
