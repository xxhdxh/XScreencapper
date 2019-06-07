package com.kk.screencapture.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArkResponse {
    public int status = 200;
    public String message = "ok";

    public Object data;

    public ArkResponse(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public static ArkResponse ok() {
        return new ArkResponse();
    }

    public static ArkResponse bad(String msg) {
        return new ArkResponse(400, msg);
    }
}
