package com.kk.screencapture.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

public class BitmapUtil {
    public static int quality = 90;

    /**
     * 将bitmap转为Base64字符串
     *
     * @param bitmap
     * @return base64字符串
     */
    public static String bitmapToString(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        bitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream);
        byte[] bytes = outputStream.toByteArray();

        IOUtil.closeQuitely(outputStream);

        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    /**
     * 将base64字符串转为bitmap
     *
     * @param base64String
     * @return bitmap
     */
    public static Bitmap base64ToBitmap(String base64String) {
        byte[] bytes = Base64.decode(base64String, Base64.NO_WRAP);

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        return bitmap;
    }
}
