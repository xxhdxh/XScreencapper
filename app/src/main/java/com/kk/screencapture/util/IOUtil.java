package com.kk.screencapture.util;

import java.io.Closeable;

public class IOUtil {
    public static void closeQuitely(Object closeable) {
        if (null != closeable) {
            try {
                if (closeable instanceof Closeable)
                    ((Closeable) closeable).close();

                if (closeable instanceof AutoCloseable)
                    ((AutoCloseable) closeable).close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
