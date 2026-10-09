package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelIdxInFileOrder
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelListInFileOrder

/**
 * m3u 直播源解析：多线路（多行地址）与 http-user-agent
 */
class M3uIptvParserTest {

    private val parser = M3uIptvParser()

    private fun parse(data: String): ChannelGroupList = runBlocking { parser.parse(data) }

    private fun channelsOf(data: String): List<Channel> = parse(data).flatMap { it.channelList }

    private fun channelOf(data: String, name: String): Channel =
        channelsOf(data).first { it.name == name }

    @Test
    fun multiLineUrlsAreAllKeptInOrder() {
        val channel = channelOf(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="浙江卫视" group-title="卫视",浙江卫视
            http://a.com/1
            http://a.com/2
            http://a.com/3
            """.trimIndent(),
            "浙江卫视",
        )

        assertEquals(
            listOf("http://a.com/1", "http://a.com/2", "http://a.com/3"),
            channel.urlList,
        )
    }

    /** 向后兼容：一个频道标签跟一个地址，行为与改动前一致 */
    @Test
    fun singleUrlPerChannelIsUnchanged() {
        val channels = channelsOf(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="CCTV1" group-title="央视",CCTV-1 综合
            http://a.com/1.m3u8
            #EXTINF:-1 tvg-name="CCTV2" group-title="央视",CCTV-2 财经
            http://a.com/2.m3u8
            """.trimIndent(),
        )

        assertEquals(2, channels.size)
        assertEquals(listOf("http://a.com/1.m3u8"), channels[0].urlList)
        assertEquals(listOf("http://a.com/2.m3u8"), channels[1].urlList)
    }

    @Test
    fun httpUserAgentAttributeIsParsed() {
        val channel = channelOf(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="浙江卫视" group-title="卫视" http-user-agent="AptvPlayer-UA",浙江卫视
            http://a.com/1
            http://a.com/2
            """.trimIndent(),
            "浙江卫视",
        )

        assertEquals("AptvPlayer-UA", channel.userAgent)
        assertEquals(2, channel.urlList.size)
    }

    /** VLC 写法：#EXTVLCOPT 独立成行，且不应被当成播放地址 */
    @Test
    fun vlcStyleExTvlcOptUserAgentIsParsed() {
        val channel = channelOf(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="湖南卫视" group-title="卫视",湖南卫视
            #EXTVLCOPT:http-user-agent=VLC-UA/1.0
            http://a.com/hunan
            """.trimIndent(),
            "湖南卫视",
        )

        assertEquals("VLC-UA/1.0", channel.userAgent)
        assertEquals(listOf("http://a.com/hunan"), channel.urlList)
    }

    @Test
    fun userAgentIsNullWhenAbsent() {
        val channel = channelOf(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="深圳卫视" group-title="卫视",深圳卫视
            http://a.com/sz
            """.trimIndent(),
            "深圳卫视",
        )

        assertNull(channel.userAgent)
    }

    /**
     * 少数直播源把属性拆到单独一行。这类行没有 `://` 却带 `=`，
     * 不能混进 urlList，否则会多出一条永远播不通的线路。
     */
    @Test
    fun standaloneAttributeLineIsNotTreatedAsUrl() {
        val channel = channelOf(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="广东卫视" group-title="卫视",广东卫视
            http://a.com/1
            http://a.com/2
            http-user-agent="UA-X"
            """.trimIndent(),
            "广东卫视",
        )

        assertEquals(listOf("http://a.com/1", "http://a.com/2"), channel.urlList)
        assertEquals("UA-X", channel.userAgent)
    }

    /** 同名频道的多个标签应合并为一组线路，并去重 */
    @Test
    fun duplicateChannelEntriesAreMergedAndDeduped() {
        val channels = channelsOf(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="A" group-title="G",A
            http://a.com/1
            http://a.com/2
            #EXTINF:-1 tvg-name="A" group-title="G",A
            http://a.com/2
            http://a.com/3
            """.trimIndent(),
        )

        assertEquals(1, channels.size)
        assertEquals(listOf("http://a.com/1", "http://a.com/2", "http://a.com/3"), channels[0].urlList)
    }

    @Test
    fun channelWithoutUrlIsSkipped() {
        val channels = channelsOf(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="X",X
            #EXTINF:-1 tvg-name="Y",Y
            http://a.com/y
            """.trimIndent(),
        )

        assertEquals(listOf("Y"), channels.map { it.name })
    }

    /** 空行与其它标签行穿插在地址之间时应被忽略，不影响地址收集 */
    @Test
    fun blankAndDirectiveLinesBetweenUrlsAreSkipped() {
        val channel = channelOf(
            """
            #EXTM3U

            #EXTINF:-1 tvg-name="Z" group-title="G",Z

            http://a.com/1

            #EXTVLCOPT:network-caching=1000
            rtmp://b.com/2

            """.trimIndent(),
            "Z",
        )

        assertEquals(listOf("http://a.com/1", "rtmp://b.com/2"), channel.urlList)
    }

    @Test
    fun epgUrlIsParsedFromHeader() {
        val groupList = parse(
            """
            #EXTM3U x-tvg-url="https://epg.example/e.xml.gz"
            #EXTINF:-1 tvg-name="Z",Z
            http://a.com/1
            """.trimIndent(),
        )

        assertEquals("https://epg.example/e.xml.gz", groupList.epgUrl)
    }

    /**
     * 频道 order 记录文件出现次序，与分组无关。
     * 分组会把列表重排（同组的聚到一起），所以 order 与「扁平化后的下标」并不相同。
     */
    @Test
    fun channelOrderRecordsFilePositionAcrossGroups() {
        val groupList = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="A" group-title="G1",A
            http://a.com/a
            #EXTINF:-1 tvg-name="B" group-title="G2",B
            http://a.com/b
            #EXTINF:-1 tvg-name="C" group-title="G1",C
            http://a.com/c
            """.trimIndent(),
        )

        // 扁平化后按分组排列：G1(A,C) 在前，G2(B) 在后
        assertEquals(listOf("A", "C", "B"), groupList.channelList.map { it.name })
        // order 仍是文件中的出现次序
        assertEquals(listOf(0, 2, 1), groupList.channelList.map { it.order })
    }

    /** 换台与数字选台取用的就是这份「文件顺序」投影 */
    @Test
    fun channelListInFileOrderRestoresFileSequence() {
        val groupList = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="A" group-title="G1",A
            http://a.com/a
            #EXTINF:-1 tvg-name="B" group-title="G2",B
            http://a.com/b
            #EXTINF:-1 tvg-name="C" group-title="G1",C
            http://a.com/c
            """.trimIndent(),
        )

        val inFileOrder = groupList.channelListInFileOrder

        assertEquals(listOf("A", "B", "C"), inFileOrder.map { it.name })
        // B 在文件顺序里排第 2（下标 1），但在分组后的列表里排第 3（下标 2）
        assertEquals(1, groupList.channelIdxInFileOrder(inFileOrder[1]))
        assertEquals(2, groupList.channelIdx(inFileOrder[1]))
    }

    /** 多线路频道只占一个文件序号，不会因为有多行地址而把后续频道推后 */
    @Test
    fun multiLineChannelTakesSingleFileOrderSlot() {
        val groupList = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="A" group-title="G",A
            http://a.com/1
            http://a.com/2
            http://a.com/3
            #EXTINF:-1 tvg-name="B" group-title="G",B
            http://a.com/b
            """.trimIndent(),
        )

        assertEquals(listOf("A", "B"), groupList.channelListInFileOrder.map { it.name })
        assertEquals(listOf(0, 1), groupList.channelListInFileOrder.map { it.order })
    }
}
