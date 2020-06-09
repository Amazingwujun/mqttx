package com.jun.mqttx.utils;

import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * mqtt topic工具类
 *
 * @author Jun
 * @date 2020-04-23 09:55
 */
public class TopicUtils {

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

        //"/" 通配符判断
        if (subTopic.startsWith("/") || subTopic.endsWith("/")) {
            return false;
        }

        //"#" 通配符判断
        int idx = subTopic.indexOf("#");
        if (idx > -1 && idx != subTopic.length() - 1) {
            return false;
        }


        String[] split = subTopic.split("/");
        for (String fragment : split) {
            //不允许 a//b
            if (StringUtils.isEmpty(fragment)) {
                return false;
            }

            //不允许 a/b+/c，a/b# 等非法 topicFilter
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
