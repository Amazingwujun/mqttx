package com.jun.mqttx.utils;

import com.jun.mqttx.broker.handler.AbstractMqttSessionHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * mqtt topic工具类
 *
 * @author Jun
 * @since 1.0.4
 */
public class TopicUtils {
    //@formatter:off

    private static final String SHARE_TOPIC = "$share";

    private static final String SYS_TOPIC = "$SYS";
    /** "$SYS/broker/" */
    private static final String SYS_TOPIC_BROKER = SYS_TOPIC + "/broker/";

    /** broker 全部状态值 */
    public static final String BROKER_STATUS = SYS_TOPIC_BROKER + "status";
    /** 当前连接的客户端数 */
    public static final String BROKER_CLIENTS_ACTIVE_CONNECTED_COUNT = SYS_TOPIC_BROKER + "activeConnectCount";
    /** 服务器时间 */
    public static final String BROKER_TIME = SYS_TOPIC_BROKER + "time";
    /** mqttx 版本 */
    public static final String BROKER_VERSION = SYS_TOPIC_BROKER + "version";
    /** 最大客户端连接数 */
    public static final String BROKER_MAX_CLIENTS_ACTIVE = SYS_TOPIC_BROKER + "maxActiveConnectCount";
    /** @see com.jun.mqttx.broker.handler.ProbeHandler#IN_MSG_SIZE */
    public static final String BROKER_RECEIVED_MSG = SYS_TOPIC_BROKER + "receivedMsg";
    /** @see com.jun.mqttx.broker.handler.ProbeHandler#OUT_MSG_SIZE */
    public static final String BROKER_SEND_MSG = SYS_TOPIC_BROKER + "sendMsg";
    /** 代理上线时长 */
    public static final String BROKER_UPTIME = SYS_TOPIC_BROKER + "uptime";

    private static final Set<String> sysTopicSets;

    //@formatter:on

    static {
        sysTopicSets = new HashSet<>(6);
        sysTopicSets.add(BROKER_STATUS);
        sysTopicSets.add(BROKER_CLIENTS_ACTIVE_CONNECTED_COUNT);
        sysTopicSets.add(BROKER_TIME);
        sysTopicSets.add(BROKER_VERSION);
        sysTopicSets.add(BROKER_MAX_CLIENTS_ACTIVE);
        sysTopicSets.add(BROKER_RECEIVED_MSG);
        sysTopicSets.add(BROKER_SEND_MSG);
        sysTopicSets.add(BROKER_UPTIME);
    }

    /**
     * client 是否被允许订阅 topic
     *
     * @param ctx   {@link ChannelHandlerContext}
     * @param topic 订阅 topic
     * @return true 如果被授权
     */
    public static boolean hasAuthToSubTopic(ChannelHandlerContext ctx, String topic) {
        return hasAuth(ctx, topic, AbstractMqttSessionHandler.AUTHORIZED_SUB_TOPICS);
    }

    /**
     * client 是否允许发布消息到 topic
     *
     * @param ctx   {@link ChannelHandlerContext}
     * @param topic 订阅 topic
     * @return true 如果被授权
     */
    public static boolean hasAuthToPubTopic(ChannelHandlerContext ctx, String topic) {
        return hasAuth(ctx, topic, AbstractMqttSessionHandler.AUTHORIZED_PUB_TOPICS);
    }

    /**
     * client 是否被允许订阅&发布 topic
     *
     * @param ctx   {@link ChannelHandlerContext}
     * @param topic 订阅 topic
     * @param type  授权类别 {@link AbstractMqttSessionHandler#AUTHORIZED_PUB_TOPICS},{@link AbstractMqttSessionHandler#AUTHORIZED_SUB_TOPICS}
     * @return true 如果被授权
     */
    @SuppressWarnings("unchecked")
    private static boolean hasAuth(ChannelHandlerContext ctx, String topic, String type) {
        Channel channel = ctx.channel();
        Object topics = channel.attr(AttributeKey.valueOf(type)).get();
        if (topics == null) {
            return false;
        }
        List<String> authorizedTopics = (List<String>) topics;
        for (String authorizedTopic : authorizedTopics) {
            if (TopicUtils.match(topic, authorizedTopic)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 系统 topic
     *
     * @param topic 主题
     * @return true, if topic is sys
     */
    public static boolean isSys(String topic) {
        return sysTopicSets.contains(topic);
    }

    /**
     * 共享主题格式：<code>$share/{ShareName}/{filter}</code>;
     * <ul>
     *     <li>$share 前缀表示这是一个共享订阅</li>
     *     <li>ShareName 客户端通过 ShareName 共享同一个订阅</li>
     *     <li>filter 与非共享订阅含义相同</li>
     * </ul>
     *
     * @param topic 主题
     * @return true if topic is sharable
     */
    public static boolean isShare(String topic) {
        String[] split = topic.split("/");
        int len = split.length;
        if (len < 3) {
            return false;
        }

        for (int i = 0; i < split.length; i++) {
            String s = split[i];
            if (i == 0 && !SHARE_TOPIC.equals(s)) {
                return false;
            }
            if (i == 1 && (s.contains("+") || s.contains("#"))) {
                return false;
            }
        }

        // 共享主题
        return true;
    }

    /**
     * 用于判定客户端订阅的 topic 是否合法，参考 mqtt-v3.1.1 4.7章进行逻辑实现，以下为定制化通配符处理策略：
     * <ul>
     *     <li>最后一个字符不可为'/'</li>
     *     <li># 通配符如果存在，必须是最后一个字符</li>
     *     <li>共享订阅主题的 ShareName 不允许含有 "+", "#" 符号</li>
     * </ul>
     *
     * @param subTopic 订阅主题
     * @return true if topic valid
     */
    public static boolean isValid(String subTopic) {
        if (StringUtils.isEmpty(subTopic)) {
            return false;
        }

        // 1 不允许 "/" 连续出现, 如 "//";
        // 2 不允许 " " 空字符出现
        // 3 "#" 只能出现在末位
        // 4 "/" 不允许出现在末位
        char[] allChar = subTopic.toCharArray();
        int len = allChar.length;
        for (int i = 0, j = Integer.MIN_VALUE; i < len; i++) {
            char c = allChar[i];
            if (c == '/') {
                if (i == j + 1) {
                    return false;
                }
                j = i;
            }
            if (' ' == c) {
                return false;
            }
            if (c == '#' && i != len - 1) {
                return false;
            }
            if ('/' == c && i == len - 1) {
                return false;
            }
        }

        // 5 不允许 a/b+/c，a/b# 等非法 topicFilter
        String[] split = subTopic.split("/");
        boolean isStartWithShare = false;
        for (int i = 0; i < split.length; i++) {
            String fragment = split[i];
            if (fragment.contains("+") || fragment.contains("#")) {
                if (fragment.length() > 1) {
                    return false;
                }
            }

            // 增加共享订阅主题合法性判断
            if (i == 0 && SHARE_TOPIC.equalsIgnoreCase(fragment)) {
                isStartWithShare = true;
            }
            if (isStartWithShare && i == 1 && (fragment.contains("+") || fragment.contains("#"))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 用于判定客户订阅的主题是否匹配发布主题
     *
     * @param pub 发布主题
     * @param sub 订阅主题 - topicFilter
     * @return true if pub match sub
     */
    public static boolean match(String pub, String sub) {
        if (Objects.equals(pub, sub)) {
            return true;
        }
        if (!sub.contains("#") && !sub.contains("+")) {
            return false;
        }

        boolean result = true;
        String[] pubs = pub.split("/");
        String[] subs = sub.split("/");
        int pubsLen = pubs.length;
        int subsLen = subs.length;
        if (pubsLen >= subsLen) {
            // 发布主题层级高于订阅层级
            for (int i = 0; i < pubsLen; i++) {
                String pubStr = pubs[i];
                String subStr = subs[i];
                if ("#".equals(subStr)) {
                    break;
                }
                if ("+".equals(subStr)) {
                    if (i == subsLen - 1) {
                        if (pubsLen != subsLen) {
                            result = false;
                        }
                        break;
                    }

                    continue;
                }
                if (!Objects.equals(pubStr, subStr)) {
                    result = false;
                    break;
                }

                // 有效长度匹配完成，后续无法匹配
                if (i == subsLen - 1) {
                    if (pubsLen != subsLen) {
                        result = false;
                    }
                    break;
                }
            }
        } else {
            if (sub.endsWith("#") && subsLen == pubsLen + 1) {
                // 这里只需要比较 pubsLen 的字符即可
                for (int i = 0; i < pubsLen; i++) {
                    String pubStr = pubs[i];
                    String subStr = subs[i];
                    if ("+".equals(subStr)) {
                        continue;
                    }
                    if (!Objects.equals(pubStr, subStr)) {
                        result = false;
                        break;
                    }
                }
            } else {
                return false;
            }
        }

        return result;
    }
}