package com.kk.screencapture.util;

import java.util.HashMap;
import java.util.Map;

public class MapUtil {
    public static long getLong0(Map<String, Object> map, String key) {
        return getLong(map, key, 0);
    }

    public static long getLong(Map<String, Object> map, String key, long defVal) {
        Object val = map.get(key);

        if (null == val) {
            return defVal;
        }

        try {
            return Float.valueOf(String.valueOf(val)).longValue();
        } catch (Exception e) {
            return defVal;
        }
    }

    public static void main(String[] args) {
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 1.0f);
        map.put("c", "1");
        map.put("d", "1.0f");

        System.out.println("a:" + getLong0(map, "a"));
        System.out.println("b:" + getLong0(map, "b"));
        System.out.println("c:" + getLong0(map, "c"));
        System.out.println("d:" + getLong0(map, "d"));
        System.out.println("e:" + getLong0(map, "e"));
    }
}

