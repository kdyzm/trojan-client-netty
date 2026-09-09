package com.kdyzm.trojan.client.netty.monitor;

import java.util.List;

/**
 * 迷你 JSON 序列化（仅连接快照数组，不引入第三方依赖）。
 * 字符串转义覆盖引号、反斜杠与控制字符。
 *
 * @author kdyzm
 * @date 2026-09-09
 */
public final class MonitorJsonWriter {

    private MonitorJsonWriter() {
    }

    /**
     * 序列化连接快照数组
     */
    public static String writeConnections(List<ConnectionSnapshot> connections) {
        StringBuilder sb = new StringBuilder(connections.size() * 128);
        sb.append('[');
        for (int i = 0; i < connections.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            ConnectionSnapshot s = connections.get(i);
            sb.append('{');
            appendField(sb, "id", s.getId());
            sb.append(',');
            appendField(sb, "src", s.getSrc());
            sb.append(',');
            appendField(sb, "protocol", s.getProtocol());
            sb.append(',');
            appendField(sb, "mode", s.getMode().name());
            sb.append(',');
            appendField(sb, "host", s.getHost());
            sb.append(',');
            appendField(sb, "port", s.getPort());
            sb.append(',');
            appendField(sb, "startTimeMillis", s.getStartTimeMillis());
            sb.append(',');
            appendField(sb, "upBytes", s.getUpBytes());
            sb.append(',');
            appendField(sb, "downBytes", s.getDownBytes());
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String name, String value) {
        sb.append('"').append(name).append("\":\"").append(escape(value)).append('"');
    }

    private static void appendField(StringBuilder sb, String name, long value) {
        sb.append('"').append(name).append("\":").append(value);
    }

    /** 转义引号、反斜杠与控制字符（JSON 规范） */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
