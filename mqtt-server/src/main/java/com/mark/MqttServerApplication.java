package com.mark;

import com.mark.server.Server;

/**
 * @author mk
 * @data 2025/9/10 0:16
 * @description
 */
public class MqttServerApplication {
    public static void main(String[] args) throws InterruptedException {
        Server server = new Server();
        server.setHost("127.0.0.1");
        server.bind();
    }
}
