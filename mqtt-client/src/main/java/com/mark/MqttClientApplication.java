package com.mark;

import com.mark.client.MqttClient;

/**
 * @author mk
 * @data 2025/10/22 23:29
 * @description
 */
public class MqttClientApplication {
    public static void main(String[] args) {
        MqttClient mqttClient = new MqttClient("127.0.0.1", 9000, "client-01");
        mqttClient.connect();
    }
}
