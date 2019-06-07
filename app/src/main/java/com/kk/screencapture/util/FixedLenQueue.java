package com.kk.screencapture.util;

import android.provider.Telephony;

import com.kk.screencapture.MainActivity;

import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 定长队列
 **/
public class FixedLenQueue<T> {

    private int limit; // 队列长度

    public ArrayBlockingQueue<T> queue;

    public FixedLenQueue(int limit) {
        this.limit = limit;

        queue = new ArrayBlockingQueue<>(limit);
    }

    /**
     * 入列：当队列大小已满时，把队头的元素poll掉
     */
    public T offer(T t) {
        if (queue.size() == limit) {
            T oldestItem = queue.poll();
            return oldestItem;
        }

        queue.offer(t);
        return null;
    }

    public T take() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    public int size() {
        return queue.size();
    }
}