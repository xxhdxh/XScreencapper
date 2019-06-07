package com.kk.screencapture;

import android.support.test.runner.AndroidJUnit4;

import com.google.gson.Gson;
import com.kk.screencapture.model.ActionMessage;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        String actionStr = "{\"action\":\"size\",\"extra\":{\"w\":100}}";

        ActionMessage actionMessage = new Gson().fromJson(actionStr, ActionMessage.class);
        System.out.print(actionStr);
    }
}
