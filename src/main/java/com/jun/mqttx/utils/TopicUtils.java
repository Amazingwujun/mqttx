package com.jun.mqttx.utils;

import com.jun.mqttx.broker.handler.AbstractMqttSessionHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * mqtt topic工具类
 *
 * @author Jun
 * @date 2020-04-23 09:55
 */
public class TopicUtils {


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
     * 用于判定客户端订阅的 topic 是否合法，参考 mqtt-v3.1.1 4.7章进行逻辑实现，以下为定制化通配符处理策略：
     * <ul>
     *     <li>第一个字符及最后一个字符不可为'/'</li>
     *     <li># 通配符如果存在，必须是最后一个字符</li>
     * </ul>
     * 这里的判断做的还不是很严谨，所以客户端对 topic 的使用还是得遵循规范，不要乱来
     *
     * @param subTopic 订阅主题
     * @return true if topic valid
     */
    public static boolean isValid(String subTopic) {
        if (StringUtils.isEmpty(subTopic)) {
            return false;
        }

        //1 不允许 "/" 连续出现, 如 "//"
        //2 不允许 " " 空字符串出现
        //3 "#" 只能出现在末位
        //4 '/' 不允许出现在末位及首位
        char[] allChar = subTopic.toCharArray();
        int len = allChar.length;
        for (int i = 0, j = -1; i < len; i++) {
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
            if ('/' == c && (i == len - 1 || i == 0)) {
                return false;
            }
        }

        //5 不允许 a/b+/c，a/b# 等非法 topicFilter
        String[] split = subTopic.split("/");
        for (String fragment : split) {
            if (fragment.contains("+") || fragment.contains("#")) {
                if (fragment.length() > 1) {
                    return false;
                }
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
            return Objects.equals(pub, sub);
        }

        boolean result = true;
        String[] pubs = pub.split("/");
        String[] subs = sub.split("/");
        int pubsLen = pubs.length;
        int subsLen = subs.length;
        if (pubsLen >= subsLen) {
            //发布主题层级高于订阅层级
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

                //有效长度匹配完成，后续无法匹配
                if (i == subsLen - 1) {
                    if (pubsLen != subsLen) {
                        result = false;
                    }
                    break;
                }
            }
        } else {
            if (sub.endsWith("#") && subsLen == pubsLen + 1) {
                //这里只需要比较 pubsLen 的字符即可
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
