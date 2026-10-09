package top.yogiczy.mytv.core.data.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 线路服务器标识与「跳过已拉黑线路」的选择逻辑
 */
class ChannelUtilTest {

    @Test
    fun urlHostExtractsAuthority() {
        assertEquals(
            "183.156.225.89:9050",
            ChannelUtil.urlHost("http://183.156.225.89:9050/udp/233.50.201.100:5140"),
        )
        // 路径里带自己的冒号（live.php?id=xxx / udp 组播地址）不影响取 authority
        assertEquals(
            "101.35.240.114:88",
            ChannelUtil.urlHost("http://101.35.240.114:88/live.php?id=浙江卫视"),
        )
        // 没有端口
        assertEquals("a.com", ChannelUtil.urlHost("http://a.com"))
        assertEquals("c.com", ChannelUtil.urlHost("rtsp://c.com/live"))
        // 无 scheme 的串退回原串，避免所有异常串挤到同一个空 key 上
        assertEquals("some/path", ChannelUtil.urlHost("some/path"))
    }

    /** 同一台服务器上不同频道的线路必须归并成同一个 key，拉黑才能跨频道生效 */
    @Test
    fun sameServerAcrossChannelsSharesHostKey() {
        val zhejiang = "http://183.156.225.89:9050/udp/233.50.201.100:5140"
        val qianjiang = "http://183.156.225.89:9050/udp/233.50.201.132:5140"

        assertEquals(ChannelUtil.urlHost(zhejiang), ChannelUtil.urlHost(qianjiang))
    }

    /** 同一 IP 不同端口是不同的服务，要分开统计 */
    @Test
    fun differentPortIsDifferentHost() {
        assertEquals("122.230.90.27:8888", ChannelUtil.urlHost("http://122.230.90.27:8888/udp/1:2"))
        assertEquals("122.230.90.27:8881", ChannelUtil.urlHost("http://122.230.90.27:8881/udp/1:2"))
    }

    @Test
    fun nextPlayableUrlIdxSkipsBlacklisted() {
        val urlList = listOf(
            "http://dead.com/a",
            "http://dead.com/b",
            "http://alive.com/c",
        )

        val idx = ChannelUtil.nextPlayableUrlIdx(urlList, 0, setOf("dead.com"))
        assertEquals(2, idx)
    }

    @Test
    fun nextPlayableUrlIdxWrapsAround() {
        val urlList = listOf(
            "http://alive.com/a",
            "http://dead.com/b",
        )

        // 从末尾（dead）开始找，应绕回第 0 条
        val idx = ChannelUtil.nextPlayableUrlIdx(urlList, 1, setOf("dead.com"))
        assertEquals(0, idx)
    }

    @Test
    fun nextPlayableUrlIdxReturnsCurrentWhenUsable() {
        val urlList = listOf("http://a.com/1", "http://b.com/2")
        assertEquals(1, ChannelUtil.nextPlayableUrlIdx(urlList, 1, setOf("a.com")))
    }

    /** 全被拉黑返回 -1：调用方据此忽略黑名单，否则频道彻底没法播 */
    @Test
    fun nextPlayableUrlIdxReturnsMinusOneWhenAllBlacklisted() {
        val urlList = listOf("http://a.com/1", "http://b.com/2")

        assertEquals(-1, ChannelUtil.nextPlayableUrlIdx(urlList, 0, setOf("a.com", "b.com")))
    }

    @Test
    fun nextPlayableUrlIdxHandlesEmptyList() {
        assertEquals(-1, ChannelUtil.nextPlayableUrlIdx(emptyList(), 0, emptySet()))
    }

    @Test
    fun nextPlayableUrlIdxWithoutBlacklistReturnsFromIdx() {
        val urlList = listOf("http://a.com/1", "http://b.com/2", "http://c.com/3")
        assertEquals(2, ChannelUtil.nextPlayableUrlIdx(urlList, 2, emptySet()))
    }
}
