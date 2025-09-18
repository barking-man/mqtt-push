package com.mark.handler;

import com.alibaba.fastjson.JSONObject;
import com.mark.constants.MessageConstant;
import com.mark.exception.ServiceException;
import com.mark.message.BaseMessage;
import com.mark.message.Request;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * @author mk
 * @data 2025/9/18 23:39
 * @description
 */
public class JsonEncoder extends MessageToMessageEncoder<BaseMessage> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, BaseMessage msg, List<Object> out) throws Exception {
        if (msg instanceof Request) {
            Request request = (Request) msg;
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type", MessageConstant.REQUEST);
            jsonObject.put("message", request.getMessage());
            jsonObject.put("sequence", request.getSequence());
            ByteBuf byteBuf = Unpooled.copiedBuffer(jsonObject.toString().getBytes());
            out.add(byteBuf);
        } else {
            throw new ServiceException("编码失败，非法消息类型：" + msg);
        }
    }
}
