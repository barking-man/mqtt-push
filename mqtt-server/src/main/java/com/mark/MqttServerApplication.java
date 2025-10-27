package com.mark;

import com.mark.server.Server;
import io.netty.channel.ChannelFuture;

/**
 * @author mk
 * @data 2025/9/10 0:16
 * @description
 */
public class MqttServerApplication {
    public static void main(String[] args) throws InterruptedException {
        try {
            Server server = new Server();
            server.setHost("127.0.0.1");
            ChannelFuture future = server.bind();
            if (future != null) {
                // 阻塞主线程，知道服务端通道关闭
                future.channel().closeFuture().sync();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
