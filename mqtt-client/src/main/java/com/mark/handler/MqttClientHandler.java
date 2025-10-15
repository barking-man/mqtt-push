package com.mark.handler;

import com.mark.client.MqttClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * MQTT客户端处理器：处理服务端回复的消息（如CONNACK、SUBACK、PUBLISH）和心跳
 */
@Slf4j
public class MqttClientHandler extends ChannelInboundHandlerAdapter {
    private final MqttClient client;

    public MqttClientHandler(MqttClient client) {
        this.client = client;
    }

    /**
     * 接收服务端发送的消息
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof MqttMessage)) {
            super.channelRead(ctx, msg);
            return;
        }

        MqttMessage mqttMsg = (MqttMessage) msg;
        MqttMessageType msgType = mqttMsg.fixedHeader().messageType();

        // 根据消息类型处理服务端响应
        switch (msgType) {
            // 1. 处理服务端的连接确认（CONNACK）
            case CONNACK:
                handleConnAck((MqttConnAckMessage) mqttMsg);
                break;
            // 2. 处理服务端的订阅确认（SUBACK）
            case SUBACK:
                handleSubAck((MqttSubAckMessage) mqttMsg);
                break;
            // 3. 处理服务端转发的消息（PUBLISH，如其他客户端发的消息）
            case PUBLISH:
                handlePublish((MqttPublishMessage) mqttMsg);
                break;
            // 4. 处理心跳响应（PINGRESP）
            case PINGRESP:
                log.info("收到服务端心跳响应（PINGRESP）");
                break;
            default:
                log.debug("收到未处理的消息类型：{}", msgType);
        }
    }

    /**
     * 处理连接确认（CONNACK）：判断服务端是否接受连接
     */
    private void handleConnAck(MqttConnAckMessage msg) {
        MqttConnectReturnCode returnCode = msg.variableHeader().connectReturnCode();
        if (returnCode == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
            log.info("服务端接受连接（CONNACK），sessionPresent={}", msg.variableHeader().isSessionPresent());
        } else {
            log.error("服务端拒绝连接（CONNACK），原因：{}", returnCode);
            client.getChannel().close(); // 拒绝则关闭通道
        }
    }

    /**
     * 处理订阅确认（SUBACK）：判断订阅是否成功
     */
    private void handleSubAck(MqttSubAckMessage msg) {
        int packetId = msg.variableHeader().messageId();
        // 订阅结果：每个订阅条目对应一个QoS（0=成功，128=失败）
        for (Integer grantedQoS : msg.payload().grantedQoSLevels()) {
            if (grantedQoS == 128) {
                log.error("订阅失败，packetId={}", packetId);
            } else {
                log.info("订阅成功，packetId={}，服务端授予QoS={}", packetId, grantedQoS);
            }
        }
    }

    /**
     * 处理服务端转发的消息（PUBLISH）：接收并打印消息
     */
    private void handlePublish(MqttPublishMessage msg) {
        String topic = msg.variableHeader().topicName();
        // 读取消息内容（ByteBuf转字符串）
        byte[] contentBytes = new byte[msg.payload().readableBytes()];
        msg.payload().readBytes(contentBytes);
        String content = new String(contentBytes);

        log.info("收到服务端转发的消息，topic={}，content={}，QoS={}",
                topic, content, msg.fixedHeader().qosLevel());
    }

    /**
     * 处理心跳超时：5秒没发消息，发送PINGREQ给服务端
     */
//    @Override
//    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
//        if (evt instanceof IdleStateEvent event) {
//            if (event.state() == IdleState.WRITER_IDLE) {
//                // 发送心跳请求（PINGREQ）
//                MqttMessage pingReq = new MqttMessage(
//                        new MqttFixedHeader(
//                                MqttMessageType.PINGREQ,
//                                false,
//                                MqttQoS.AT_MOST_ONCE,
//                                false,
//                                0
//                        )
//                );
//                ctx.writeAndFlush(pingReq);
//                log.info("发送心跳请求（PINGREQ）");
//            }
//        }
//    }

    /**
     * 处理异常（如断连）
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端异常", cause);
        ctx.close();
    }

    /**
     * 处理通道关闭
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("MQTT客户端通道已关闭");
    }
}