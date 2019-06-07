package com.kk.screencapture.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActionMessage {
    // 动作
    String action;

    int width = 270;
    int height = 480;

    long frequency = 1;

    int quality = 90;
}
