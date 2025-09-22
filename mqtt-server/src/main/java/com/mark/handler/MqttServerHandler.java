package com.mark.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * @author mk
 * @data 2025/9/23 0:07
 * @description
 */
@Slf4j
public class MqttServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    private void handleMqttMessage(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttFixedHeader mqttFixedHeader = msg.fixedHeader();
        switch (mqttFixedHeader.messageType()) {
            case CONNECT:
                handleConnectMsg(ctx, (MqttConnectMessage) msg);
        }
    }

    /**
     * 连接消息处理器
     * @param ctx
     * @param msg
     */
    private void handleConnectMsg(ChannelHandlerContext ctx, MqttConnectMessage msg) {
        MqttConnectVariableHeader variableHeader = msg.variableHeader();
        log.info("客户端连接: clientId=" + msg.payload().clientIdentifier());
        // 构造连接响应
        MqttConnAckMessage connAck = MqttMessageBuilders.connAck().returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(false)
                .build();
        ctx.writeAndFlush(connAck);
    }

    /**
     * 心跳消息处理器
     * @param ctx
     */
    private void handlePingMsg(ChannelHandlerContext ctx) {
        log.info("收到心跳消息，回复PONG");
        // 构造心跳响应
        MqttMessage pingResp = MqttMessageFactory.newMessage(new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0), null, null);
        ctx.writeAndFlush(pingResp);
    }

    /**
     * 处理发布消息
     * @param ctx
     * @param msg
     */
    private void handlePublishMsg(ChannelHandlerContext ctx, MqttPublishMessage msg) {
        String topic = msg.variableHeader().topicName();
        byte[] payloadByteArr = new byte[msg.payload().readableBytes()];
        msg.payload().readBytes(payloadByteArr);

        log.info("收到发布消息，topic=" + topic + "，内容=" + new String(payloadByteArr));
        if (Objects.equals(msg.fixedHeader().qosLevel(), MqttQoS.AT_LEAST_ONCE)) {
            MqttPubAckMessage pubAck = (MqttPubAckMessage) MqttMessageBuilders.pubAck().packetId(msg.variableHeader().packetId()).build();
            ctx.writeAndFlush(pubAck);
        }
        //TODO 其余几个类型的QOS待补充
    }

    /**
     * 处理订阅消息
     * @param ctx
     * @param msg
     */
    private void handleSubscribeMsg(ChannelHandlerContext ctx, MqttSubscribeMessage msg) {
        int packetId = msg.variableHeader().messageId();
        log.info("客户端订阅主题，packetId=" + packetId);
        // 构造订阅ack
        MqttSubAckMessage subAck = MqttMessageBuilders.subAck()
                .packetId(packetId)
                .addGrantedQos(MqttQoS.AT_MOST_ONCE)
                .build();
        ctx.writeAndFlush(subAck);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (Objects.equals(event.state(), IdleState.READER_IDLE)) {
                log.info("心跳超时，关闭连接");
                ctx.close();
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("服务端异常: ", cause);
        ctx.close();
    }
}
