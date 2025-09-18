package com.mark.server;
import com.mark.handler.JsonDecoder;
import com.mark.handler.JsonEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import lombok.Data;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author mk
 * @data 2025/9/10 0:16
 * @description
 */
@Data
public class Server {

    /**
     * 服务地址
     */
    private String host;

    /**
     * 绑定端口，默认9000
     */
    private int port = 9000;

    /**
     * 消息事件核心线程数
     */
    protected int corePoolSize = 10;

    /**
     * 消息事件最大线程数
     */
    protected int maxPoolSize = 150;

    /**
     * 消息事件队列最大值
     */
    protected int queueCapacity = 1000_000;

    /**
     * 是否开启keepAlive
     */
    protected boolean keepAlive = true;

    /**
     * 是否启用tcpNoDelay
     */
    protected boolean tcpNoDelay = true;

    /**
     * 工作线程数
     */
    protected int workCount;

    /**
     * 主线程组
     */
    protected EventLoopGroup bossGroup;

    /**
     * 工作线程组
     */
    protected EventLoopGroup workGroup;

    /**
     * 服务启动类
     */
    protected ServerBootstrap bootstrap;

    /**
     * 消息事件业务处理线程池
     */
    protected ExecutorService messageExecutor;

    /**
     * 通道事件业务处理线程池
     */
    protected ExecutorService channelExecutor;

    /**
     * 异常事件业务处理线程池
     */
    protected ExecutorService exceptionExecutor;

    /**
     * 初始化方法
     */
    protected void init() {
        // 1 非业务线程池初始化
        messageExecutor = new ThreadPoolExecutor(this.corePoolSize, this.maxPoolSize, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(this.queueCapacity),
                new BasicThreadFactory.Builder().namingPattern("MessageProcessor-%d").daemon(true).build(), new ThreadPoolExecutor.AbortPolicy());
        exceptionExecutor = Executors.newCachedThreadPool(new BasicThreadFactory.Builder().namingPattern("exceptionProcessor-%d").daemon(true).build());
        channelExecutor = Executors.newCachedThreadPool(new BasicThreadFactory.Builder().namingPattern("channelProcessor-%d").daemon(true).build());

        // 工作线程数置为CPU核心线程数+1
        this.workCount = Runtime.getRuntime().availableProcessors() + 1;

        // 2 业务线程池初始化
        bossGroup = new NioEventLoopGroup(this.workCount, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "BOSS_" + this.index.incrementAndGet());
            }
        });
        workGroup = new NioEventLoopGroup(this.workCount, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "WORKER_" + this.index.incrementAndGet());
            }
        });
    }

    /**
     * 服务端参数方法
     */
    public ChannelFuture bind() {
        this.init();

        bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workGroup);
        /**
         * 开启keepAlive，则tcp层会定期（通常默认是2小时）向对方发送“保活探测报文“，检测连接是否有效，
         * 如果多次尝试后对方均无响应，则会断开连接，避免僵尸连接
         */
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, keepAlive);
        /**
         * 启动Nagle算法，将多个数据包合成一个大数据包后发送，减少数据包数量，降低网络开销，
         * 适用于带宽有限、即时性要求高的场景
         */
        bootstrap.childOption(ChannelOption.TCP_NODELAY, tcpNoDelay);
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new JsonDecoder());
                pipeline.addLast(new JsonEncoder());
                pipeline.addLast(new MqttDecoder());
                pipeline.addLast(MqttEncoder.INSTANCE);
            }
        });
        return null;
    }
}
