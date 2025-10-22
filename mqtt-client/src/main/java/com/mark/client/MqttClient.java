package com.mark.client;

import com.mark.handler.MqttClientHandler;
import com.mark.utils.MqttUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MQTT客户端（支持连接、订阅、发布、心跳核心功能）
 */
@Slf4j
public class MqttClient {
    // 服务端地址和端口（需与你的服务端一致）
    private final String serverHost;
    private final int serverPort;
    // 客户端唯一标识（MQTT协议要求）
    private final String clientId;

    // Netty核心组件
    private EventLoopGroup group;
    private Channel channel;

    // 构造方法：初始化服务端地址、端口、客户端ID
    public MqttClient(String serverHost, int serverPort, String clientId) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.clientId = clientId;
    }

    /**
     * 1. 连接MQTT服务端
     */
    public void connect() {
        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    // 使用NIO Socket通道
                    .channel(NioSocketChannel.class)
                    // 连接超时时间（3秒）
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                    // 绑定处理器（编解码、心跳、业务逻辑）
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            // 心跳检测：5秒没发消息则发送PINGREQ（与服务端30秒读超时匹配）
                            pipeline.addLast(new IdleStateHandler(0, 5, 0, TimeUnit.SECONDS));
                            // MQTT协议编解码（与服务端一致）
                            pipeline.addLast(new MqttDecoder());
                            pipeline.addLast(MqttEncoder.INSTANCE);
                            // 客户端业务处理器（处理服务端响应、消息接收）
                            pipeline.addLast(new MqttClientHandler(MqttClient.this));
                        }
                    });

            // 发起连接并同步等待结果
            ChannelFuture future = bootstrap.connect(serverHost, serverPort).sync();
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    if (future.isSuccess()) {
                        log.info("Connected to {}:{} success", serverHost, serverPort);
                    } else {
                        log.info("Connected to {}:{} fail", serverHost, serverPort);
                    }
                }
            });
            if (future.isSuccess()) {
                channel = future.channel();
                log.info("MQTT客户端连接成功！clientId={}, 服务端={}:{}", clientId, serverHost, serverPort);
                // 连接成功后，自动发送MQTT CONNECT请求（核心：向服务端发起连接认证）
                sendConnectMessage();
            }

            // 阻塞直到通道关闭（保持客户端运行）
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("MQTT客户端连接异常", e);
            Thread.currentThread().interrupt();
        } finally {
            // 释放资源
            group.shutdownGracefully();
            log.info("MQTT客户端资源已释放");
        }
    }

    /**
     * 2. 发送MQTT CONNECT消息（连接请求）
     * 核心：告知服务端客户端ID、协议版本，请求建立连接
     */
    private void sendConnectMessage() {
        if (channel == null || !channel.isActive()) {
            log.error("通道未激活，无法发送CONNECT消息");
            return;
        }

        // 1. 构建CONNECT消息的可变头（协议版本、心跳间隔等）
        MqttConnectVariableHeader varHeader = new MqttConnectVariableHeader(
                MqttVersion.MQTT_3_1_1.protocolName(), // 协议名（MQTT）
                MqttVersion.MQTT_3_1_1.protocolLevel(), // 协议版本（3.1.1对应4）
                false, // 是否清除会话（false=保留会话，true=断开后清除）
                false, // 是否有遗嘱消息
                false, // 是否遗嘱消息保留
                MqttQoS.AT_MOST_ONCE.value(), // 遗嘱消息QoS
                false, // 是否需要用户名
                false, // 是否需要密码
                60 // 心跳间隔（秒）：客户端与服务端约定的心跳周期
        );

        // 2. 构建CONNECT消息的有效载荷（客户端ID、用户名、密码，此处简化无密码）
        MqttConnectPayload payload = new MqttConnectPayload(
                clientId, // 客户端唯一ID（必须）
                null, // 遗嘱主题（无遗嘱则为null）
                new byte[0], // 遗嘱消息内容（无遗嘱则为null）
                null, // 用户名（无则为null）
                new byte[0]  // 密码（无则为null）
        );

        // 3. 构建完整CONNECT消息
        MqttConnectMessage connectMsg = new MqttConnectMessage(
                new MqttFixedHeader(
                        MqttMessageType.CONNECT, // 消息类型：CONNECT
                        false, // 是否重发（首次发送为false）
                        MqttQoS.AT_MOST_ONCE, // QoS等级（CONNECT固定为0）
                        false, // 是否保留（CONNECT固定为false）
                        0
                ),
                varHeader,
                payload
        );

        // 4. 发送消息到服务端
        channel.writeAndFlush(connectMsg);
        log.info("已向服务端发送CONNECT请求，clientId={}", clientId);
    }

    /**
     * 3. 订阅主题（核心：告诉服务端“我要接收某个主题的消息”）
     * @param topic 要订阅的主题（如“device/temp”）
     * @param qoS 订阅的QoS等级（0/1/2，此处用0简化）
     */
    public void subscribe(String topic, MqttQoS qoS) {
        if (channel == null || !channel.isActive()) {
            log.error("通道未激活，无法订阅主题");
            return;
        }

        // 生成唯一的包ID（MQTT要求：除CONNECT等少数消息外，需包ID标识）
        int packetId = (int) (System.currentTimeMillis() % 65535); // 包ID范围0-65535

        // 1. 构建订阅条目（主题+QoS）
        MqttTopicSubscription subscription = new MqttTopicSubscription(topic, qoS);
        List<MqttTopicSubscription> subscriptionList = new ArrayList<>();
        subscriptionList.add(subscription);
        MqttSubscribePayload payload = new MqttSubscribePayload(subscriptionList);

        // 2. 构建完整SUBSCRIBE消息
        MqttSubscribeMessage subscribeMsg = new MqttSubscribeMessage(
                new MqttFixedHeader(
                        MqttMessageType.SUBSCRIBE,
                        false,
                        MqttQoS.AT_LEAST_ONCE, // SUBSCRIBE固定QoS=1
                        false,
                        //TODO 手动计算长度
                        0
                ),
                MqttMessageIdVariableHeader.from(packetId), // 包ID
                payload
        );

        // 3. 发送订阅请求
        channel.writeAndFlush(subscribeMsg);
        log.info("已向服务端发送订阅请求，topic={}, qoS={}, packetId={}", topic, qoS, packetId);
    }

    /**
     * 4. 发布消息（核心：向服务端发送某个主题的消息，服务端会转发给订阅者）
     * @param topic 消息主题（如“device/temp”）
     * @param content 消息内容（字符串）
     * @param qoS 消息QoS等级（此处用0简化，无需确认）
     */
    public void publish(String topic, String content, MqttQoS qoS) {
        if (channel == null || !channel.isActive()) {
            log.error("通道未激活，无法发布消息");
            return;
        }

        // 1. 构建PUBLISH消息的可变头（主题+包ID，QoS=0时包ID为0）
        MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(
                topic,
                qoS == MqttQoS.AT_MOST_ONCE ? 0 : (int) (System.currentTimeMillis() % 65535)
        );

        // 2. 构建消息体（字符串转字节数组）
        ByteBuf payload = Unpooled.wrappedBuffer(content.getBytes());

        // 3. 构建完整PUBLISH消息
        MqttPublishMessage publishMsg = new MqttPublishMessage(
                new MqttFixedHeader(
                        MqttMessageType.PUBLISH,
                        false, // 是否重发
                        qoS,
                        false, // 是否保留消息（false=不保留）
                        //TODO 手动计算长度
                        0
//                        MqttEncoder.calculateRemainingLength(varHeader.encodedLength() + payload.readableBytes())
                ),
                varHeader,
                payload
        );

        // 4. 发送消息
        channel.writeAndFlush(publishMsg);
        log.info("已发布消息，topic={}, content={}, qoS={}", topic, content, qoS);
    }

    /**
     * 5. 断开连接（核心：主动告知服务端“我要断开了”）
     */
    public void disconnect() {
        if (channel == null || !channel.isActive()) {
            log.error("通道未激活，无需断开");
            return;
        }

        // 构建DISCONNECT消息（无可变头和 payload）
        MqttMessage disconnectMsg = new MqttMessage(
                new MqttFixedHeader(
                        MqttMessageType.DISCONNECT,
                        false,
                        MqttQoS.AT_MOST_ONCE,
                        false,
                        0 // 无剩余长度
                )
        );

        // 发送断开请求后关闭通道
        channel.writeAndFlush(disconnectMsg).addListener(future -> {
            if (future.isSuccess()) {
                log.info("已向服务端发送DISCONNECT请求，clientId={}", clientId);
                channel.close(); // 关闭通道
            }
        });
    }

    // 获取通道（供处理器使用）
    public Channel getChannel() {
        return channel;
    }

    // 客户端入口：测试连接、订阅、发布
    public static void main(String[] args) throws InterruptedException {
        // 1. 创建客户端实例（服务端地址=localhost，端口=9000，客户端ID=test-client-001）
        MqttClient client = new MqttClient("localhost", 9000, "test-client-001");

        // 2. 启动客户端（连接服务端，阻塞运行）
        new Thread(client::connect).start();

        // 3. 等待1秒（确保连接成功），再执行订阅和发布
        TimeUnit.SECONDS.sleep(1);

        // 4. 订阅主题“device/temp”（QoS=0）
        client.subscribe("device/temp", MqttQoS.AT_MOST_ONCE);

        // 5. 循环发布3条消息到“device/temp”
        for (int i = 0; i < 3; i++) {
            client.publish("device/temp", "当前温度：" + (25 + i) + "℃", MqttQoS.AT_MOST_ONCE);
            TimeUnit.SECONDS.sleep(2); // 间隔2秒发一条
        }

        // 6. 发布完成后断开连接
        TimeUnit.SECONDS.sleep(1);
        client.disconnect();
    }
}