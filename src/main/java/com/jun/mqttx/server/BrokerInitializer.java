package com.jun.mqttx.server;

import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.utils.SslUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.net.ssl.SSLException;
import java.time.Duration;

/**
 * broker 启动器
 *
 * @author Jun
 * @date 2020-03-03 20:55
 */
@Component
public class BrokerInitializer {
    //@formatter:off

    /** ip */
    private String host;

    /** 端口 */
    private Integer port;

    /** 心跳 */
    private Duration heartbeat;

    /** 握手队列 */
    private Integer soBacklog;

    /** ssl开关 */
    private Boolean sslEnable;

    /** 证书工具 */
    private SslUtils sslUtils;

    /** broker handler */
    private BrokerHandler brokerHandler;

    //@formatter:on

    public BrokerInitializer(BizConfig bizConfig, BrokerHandler brokerHandler, SslUtils sslUtils) {
        Assert.notNull(bizConfig, "bizConfig can't be null");
        Assert.notNull(sslUtils, "sslUtils can't be null");
        Assert.notNull(brokerHandler, "brokerHandler can't be null");

        this.sslUtils = sslUtils;
        this.brokerHandler = brokerHandler;
        this.host = bizConfig.getHost();
        this.port = bizConfig.getPort();
        this.heartbeat = bizConfig.getHeartbeat();
        this.soBacklog = bizConfig.getSoBacklog();
        this.sslEnable = bizConfig.getSslEnable();

        //参数校验
        Assert.hasText(host, "host can't be null");
        Assert.notNull(port, "port can't be null");
        Assert.notNull(heartbeat, "heartbeat can't be null");
        Assert.notNull(soBacklog, "soBacklog can't be null");
    }


    /**
     * 启动服务
     */
    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();

            b
                    .group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .option(ChannelOption.SO_BACKLOG, soBacklog)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws SSLException {
                            ChannelPipeline pipeline = socketChannel.pipeline();

                            //心跳检测
                            pipeline.addLast(new IdleStateHandler(0, 0,
                                    (int) heartbeat.getSeconds()));
                            if (Boolean.TRUE.equals(sslEnable)) {
                                //SslContext
                                SslContext sslContext = SslContextBuilder
                                        .forServer(sslUtils.getKeyManagerFactory())
                                        .clientAuth(ClientAuth.NONE) //不校验客户端
                                        .build();
                                pipeline.addLast(sslContext.newHandler(socketChannel.alloc()));
                            }
                            pipeline.addLast(MqttEncoder.INSTANCE);
                            pipeline.addLast(new MqttDecoder());
                            pipeline.addLast(brokerHandler);
                        }
                    });

            Channel channel = b.bind(host, port).sync().channel();
            channel.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }
}
