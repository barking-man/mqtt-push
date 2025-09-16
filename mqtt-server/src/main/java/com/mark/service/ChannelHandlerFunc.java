package com.mark.service;

import io.netty.channel.ChannelHandler;

/**
 * @author mk
 * @data 2025/9/17 0:41
 * @description
 */
@FunctionalInterface
public interface ChannelHandlerFunc {

    /**
     * 新建handler
     * @return
     */
    ChannelHandler newInstance();
}
