package com.mark.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mark.constants.MessageConstant;
import com.mark.exception.ServiceException;
import com.mark.message.Request;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

/**
 * @author mk
 * @data 2025/9/17 0:47
 * @description
 */
public class JsonDecoder extends MessageToMessageDecoder<ByteBuf> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out) throws Exception {
        // 1 将数据读取到字节数组
        byte[] tmp = new byte[buf.readableBytes()];
        buf.readBytes(tmp);
        String jsonStr = new String(tmp);
        JSONObject json = JSON.parseObject(jsonStr);
        String type = json.getString("type");

        // 2 处理不同类型的业务请求
        if (MessageConstant.REQUEST.equals(type)) {
            Request request = new Request();
            request.setSequence(json.getIntValue(MessageConstant.SEQUENCE));
            request.setMessage(json.getString(MessageConstant.MESSAGE));
            // 将解码后的请求对象加入输出列表，传递给下一处理器
            out.add(request);
        } else {
            throw new ServiceException("解码失败，非法消息类型：" + type);
        }

    }
}
