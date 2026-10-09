package top.yogiczy.mytv.core.data.entities.channel

import androidx.compose.runtime.Immutable
import java.util.concurrent.atomic.AtomicInteger

/**
 * 频道
 */
@Immutable
data class Channel(
    /**
     * 频道ID，展示为频道号
     */
    val id: String = "0", // 按直播源文件顺序生成

    /**
     * 频道在直播源文件中的出现次序（0 起）。
     *
     * 换台顺序与数字选台编号都以此为准，**与分组无关**：
     * 选台界面仍按分组展示，但上下键是跨分组按文件顺序走的。
     * 解析器未写入时全部为 0，排序稳定，会退回列表原有顺序。
     */
    val order: Int = 0,

    /**
     * 频道名称
     */
    val name: String = "",

    /**
     * 节目单名称，用于查询节目单
     */
    val epgName: String = "",

    /**
     * 播放地址，多条即为多线路
     */
    val urlList: List<String> = listOf("http://1.2.3.4"),

    /**
     * 播放请求的 User-Agent，来自直播源的 http-user-agent 等属性；
     * 为空时由播放器回退到全局配置
     */
    val userAgent: String? = null,

    /**
     * 台标
     */
    val logo: String? = null,
) {
    companion object {
        val EXAMPLE = Channel(
            id = "1",
            name = "CCTV-1 综合",
            epgName = "cctv1",
            urlList = listOf(
                "http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226231/index.m3u8",
                "http://[2409:8087:5e01:34::20]:6610/ZTE_CMS/00000001000000060000000000000131/index.m3u8?IAS",
            ),
            logo = "https://live.fanmingming.com/tv/CCTV1.png"
        )
    }
}