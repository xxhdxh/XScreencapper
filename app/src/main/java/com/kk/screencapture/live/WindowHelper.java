package com.kk.screencapture.live;

import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

public class WindowHelper {
    public static void set1x1Window(Window window) {
        //放在左上角
        window.setGravity(Gravity.START | Gravity.TOP);
        WindowManager.LayoutParams attributes = window.getAttributes();
        //宽高设计为1个像素
        attributes.width = 1;
        attributes.height = 1;
        //起始坐标
        attributes.x = 0;
        attributes.y = 0;
        window.setAttributes(attributes);
    }
}
