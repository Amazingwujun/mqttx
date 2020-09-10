package com.jun.mqttx.broker;

import com.jun.mqttx.broker.codec.MqttWebsocketCodec;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.exception.GlobalException;
import com.jun.mqttx.utils.SslUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.Objects;

/**
 * broker 启动器
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
@Component
public class BrokerInitializer {
    //@formatter:off

    /** ip */
    private String host;

    /** 端口 */
    private Integer port;

    /** socket 开关 */
    private Boolean enableSocket;

    /** 心跳 */
    private Duration heartbeat;

    /** websocket 端口 */
    private Integer wsPort;

    /** websocket 地址 */
    private String websocketPath;

    /** 握手队列 */
    private Integer soBacklog;

    /** ssl开关 */
    private Boolean sslEnable;

    /**
     * 证书工具
     */
    private SslUtils sslUtils;

    /**
     * broker handler
     */
    private BrokerHandler brokerHandler;

    /**
     * reactor 线程，提供给 socket, websocket 使用
     */
    private EventLoopGroup boss, work;

    private SslContext sslContext;

    /**
     * websocket 开关
     */
    private Boolean enableWebsocket;
    //@formatter:on

    public BrokerInitializer(MqttxConfig mqttxConfig, BrokerHandler brokerHandler, SslUtils sslUtils) {
        Assert.notNull(mqttxConfig, "mqttxConfig can't be null");
        Assert.notNull(sslUtils, "sslUtils can't be null");
        Assert.notNull(brokerHandler, "brokerHandler can't be null");

        MqttxConfig.Ssl ssl = mqttxConfig.getSsl();
        MqttxConfig.Socket socket = mqttxConfig.getSocket();
        MqttxConfig.WebSocket webSocket = mqttxConfig.getWebSocket();

        this.sslUtils = sslUtils;
        this.brokerHandler = brokerHandler;
        this.host = mqttxConfig.getHost();
        this.port = socket.getPort();
        this.enableSocket = socket.getEnable();
        this.heartbeat = mqttxConfig.getHeartbeat();
        this.soBacklog = mqttxConfig.getSoBacklog();
        this.sslEnable = ssl.getEnable();
        this.websocketPath = webSocket.getPath();
        this.wsPort = webSocket.getPort();
        this.enableWebsocket = webSocket.getEnable();

        //参数校验
        Assert.hasText(host, "host can't be null");
        Assert.notNull(port, "port can't be null");
        Assert.notNull(heartbeat, "heartbeat can't be null");
        Assert.notNull(soBacklog, "soBacklog can't be null");
        Assert.hasText(websocketPath, "websocketPath can't be null");
        Assert.notNull(wsPort, "wsPort can't be null");
        Assert.isTrue(!Objects.equals(wsPort, port), "websocket 与 socket 监听端口不能相同");
        if (!enableSocket && !enableWebsocket) {
            throw new GlobalException("socket 或 websocket 服务最少存在一个");
        }
    }

    public void start() throws InterruptedException {
        if (boss == null || work == null) {
            boss = new NioEventLoopGroup(1);
            work = new NioEventLoopGroup();
        }
        if (sslEnable) {
            try {
                sslContext = SslContextBuilder
                        .forServer(sslUtils.getKeyManagerFactory())
                        .clientAuth(ClientAuth.NONE) //不校验客户端
                        .build();
            } catch (SSLException e) {
                //do nothing
                log.error(e.getMessage(), e);
            }
        }

        if (enableSocket) {
            socket();
        }
        if (enableWebsocket) {
            websocket();
        }
    }


    /**
     * socket 服务
     */
    private void socket() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();

        b
                .group(boss, work)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .option(ChannelOption.SO_BACKLOG, soBacklog)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        ChannelPipeline pipeline = socketChannel.pipeline();

                        if (sslEnable) {
                            pipeline.addLast(sslContext.newHandler(socketChannel.alloc()));
                        }
                        //心跳检测
                        pipeline.addLast(new IdleStateHandler(0, 0,
                                (int) heartbeat.getSeconds()));
                        pipeline.addLast(MqttEncoder.INSTANCE);
                        pipeline.addLast(new MqttDecoder());
                        pipeline.addLast(brokerHandler);
                    }
                });
        b.bind(host, port).sync();
    }

    /**
     * websocket 服务
     */
    private void websocket() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b
                .group(boss, work)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    protected void initChannel(SocketChannel socketChannel) {
                        ChannelPipeline pipeline = socketChannel.pipeline();

                        if (sslEnable) {
                            pipeline.addLast(sslContext.newHandler(socketChannel.alloc()));
                        }
                        pipeline.addLast(new IdleStateHandler(0, 0, (int) heartbeat.getSeconds()));
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(new WebSocketServerCompressionHandler());
                        // 请求头中 Sec-WebSocket-Protocol: mqtt, 故 subprotocols 应该支持 mqtt
                        pipeline.addLast(new WebSocketServerProtocolHandler(websocketPath, "mqtt", true));
                        pipeline.addLast(new MqttWebsocketCodec());
                        pipeline.addLast(MqttEncoder.INSTANCE);
                        pipeline.addLast(new MqttDecoder());
                        pipeline.addLast(brokerHandler);
                    }
                });

        b.bind(host, wsPort).sync();
    }
}