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
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
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

        // 参数校验
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

        if (Epoll.isAvailable()) {
            log.info("Epoll 可用，启用 {}", EpollEventLoopGroup.class.getName());
        } else {
            log.info("Epoll 不可用，启用 {}", NioEventLoopGroup.class.getName());
        }
    }


    /**
     * 启动服务.
     * <p>
     * 为优化性能，当 {@link Epoll#isAvailable()} = true , 启用 Native Epoll.
     * 参考 <a href="https:// netty.io/wiki/native-transports.html">https:// netty.io/wiki/native-transports.html</a>
     * <p>
     * <pre>
     * Netty provides the following platform specific JNI transports:
     *    Linux (since 4.0.16)
     *    MacOS/BSD (since 4.1.11)
     * These JNI transports add features specific to a particular platform, generate less garbage,
     * and generally improve performance when compared to the NIO based transport.
     * </pre>
     * 普遍的服务器都是 x86 架构 64bit 的 linux 系统, 所以 pom 中引入 <classifier>linux-x86_64</classifier> 的依赖
     */
    public void start() throws InterruptedException {
        if (boss == null || work == null) {
            if (Epoll.isAvailable()) {
                boss = new EpollEventLoopGroup(1);
                work = new EpollEventLoopGroup();
            } else {
                boss = new NioEventLoopGroup(1);
                work = new NioEventLoopGroup();
            }
        }
        if (sslEnable) {
            try {
                sslContext = SslContextBuilder
                        .forServer(sslUtils.getKeyManagerFactory())
                        .clientAuth(ClientAuth.NONE) // 不校验客户端
                        .build();
            } catch (SSLException e) {
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

        if (Epoll.isAvailable()) {
            b.channel(EpollServerSocketChannel.class);
        } else {
            b.channel(NioServerSocketChannel.class);
        }

        b
                .group(boss, work)
                .handler(new LoggingHandler(LogLevel.INFO))
                .option(ChannelOption.SO_BACKLOG, soBacklog)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        ChannelPipeline pipeline = socketChannel.pipeline();

                        if (sslEnable) {
                            pipeline.addLast(sslContext.newHandler(socketChannel.alloc()));
                        }
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
     * <pre>
     *    If MQTT is transported over a WebSocket [RFC6455] connection, the following conditions apply:
     * · MQTT Control Packets MUST be sent in WebSocket binary data frames. If any other type of data frame is received the recipient MUST close the Network Connection [MQTT-6.0.0-1].
     * · A single WebSocket data frame can contain multiple or partial MQTT Control Packets. The receiver MUST NOT assume that MQTT Control Packets are aligned on WebSocket frame boundaries [MQTT-6.0.0-2].
     * · The client MUST include “mqtt” in the list of WebSocket Sub Protocols it offers [MQTT-6.0.0-3].
     * · The WebSocket Sub Protocol name selected and returned by the server MUST be “mqtt” [MQTT-6.0.0-4].
     * · The WebSocket URI used to connect the client and server has no impact on the MQTT protocol.
     * </pre>
     * 总结一下就是：
     * <ol>
     *     <li>必须使用字节流（webSocket binary data frames）, 其它一律关闭连接</li>
     *     <li>一个 websocket 可以包含多个 mqtt 控制包, 实现协议时不能假设一个 websocket 包就是一个 mqtt 控制包</li>
     *     <li>客户端要申明自己的自协议是 "mqtt"</li>
     *     <li>服务端当然得支持子协议(subProtocol) "mqtt" 啦</li>
     * </ol>
     */
    private void websocket() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();

        if (Epoll.isAvailable()) {
            b.channel(EpollServerSocketChannel.class);
        } else {
            b.channel(NioServerSocketChannel.class);
        }

        b
                .group(boss, work)
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