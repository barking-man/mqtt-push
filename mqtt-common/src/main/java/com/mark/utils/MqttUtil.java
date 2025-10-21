package com.mark.utils;

/**
 * @author mk
 * @data 2025/10/20 23:40
 * @description
 */
public class MqttUtil {
    public static int getRemainingLength(int length) {
        if (length <= 0) {
            return 0;
        }
        int count = 0;
        do {
            count++;
            length = length >>> 7;
        } while (length > 0);
        return count;
    }
}
