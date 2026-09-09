package com.kdyzm.trojan.client.netty.monitor;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 迷你 JSON writer：字符串转义与结构
 */
public class MonitorJsonWriterTest {

    @Test
    public void testEscapesQuotesAndBackslashAndControlChars() {
        //直接调用私有转义不可行——通过内容触发转义的连接验证
        //host 含引号/反斜杠/换行时输出必须可安全解析
        ConnectionSnapshot snapshot = new ConnectionSnapshot(1L, "s", "p",
                com.kdyzm.trojan.client.netty.router.ProxyDecision.PROXY,
                "evil\"host\\name\n", 80, 0L, 0L, 0L);
        String json = MonitorJsonWriter.writeConnections(Collections.singletonList(snapshot));
        assertTrue(json.contains("\\\""));
        assertTrue(json.contains("\\\\"));
        assertTrue(json.contains("\\n"));
        assertFalse(json.contains("evil\"host")); //裸引号不得出现在输出
    }

    @Test
    public void testArrayStructureWithNumbers() {
        ConnectionSnapshot snapshot = new ConnectionSnapshot(3L, "127.0.0.1:1000", "http",
                com.kdyzm.trojan.client.netty.router.ProxyDecision.DIRECT, "baidu.com", 80, 1000L, 5L, 6L);
        String json = MonitorJsonWriter.writeConnections(Collections.singletonList(snapshot));
        assertTrue(json.startsWith("[{"));
        assertTrue(json.endsWith("}]"));
        assertTrue(json.contains("\"id\":3"));
        assertTrue(json.contains("\"upBytes\":5"));
        assertTrue(json.contains("\"downBytes\":6"));
        assertTrue(json.contains("\"mode\":\"DIRECT\""));
        assertTrue(json.contains("\"host\":\"baidu.com\""));
    }

    @Test
    public void testEmptyList() {
        assertEquals("[]", MonitorJsonWriter.writeConnections(Collections.emptyList()));
    }
}
