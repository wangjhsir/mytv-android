package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.util.utils.removeBom

/**
 * m3u直播源解析
 */
class M3uIptvParser : IptvParser {

    override fun isSupport(url: String, data: String): Boolean {
        return data.removeBom().startsWith("#EXTM3U")
    }

    override suspend fun parse(data: String): ChannelGroupList = withContext(Dispatchers.Default) {
        val cleanData = data.removeBom()
        val lines = cleanData.split("\r\n", "\n")
        val iptvList = mutableListOf<IptvResponseItem>()

        var epgUrl: String? = null
        val header = lines.firstOrNull { it.startsWith("#EXTM3U") }
        if (header != null) {
            epgUrl = extractAttribute(header, "x-tvg-url")
                ?: extractAttribute(header, "url-tvg")
                ?: extractAttribute(header, "tvg-url")
        }

        // 频道在文件中的出现次序：换台与数字选台以此为序，与分组无关
        var channelOrder = 0

        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXTINF")) return@forEachIndexed

            val order = channelOrder++
            val name = line.substringAfterLast(",").trim()
            val channelName = ChannelUtil.standardChannelName(extractAttribute(line, "tvg-name") ?: name)
            val groupName = extractAttribute(line, "group-title") ?: "其他"
            val logo = extractAttribute(line, "tvg-logo") ?: extractAttribute(line, "logo")

            // 💡 多线路：允许一个 #EXTINF 后面跟多行播放地址（部分直播源以此承载同一
            // 频道的多条线路）。收集到下一个频道标签为止的所有地址行，任一线路都不可丢。
            var userAgent = extractUserAgent(line)
            val urlList = mutableListOf<String>()
            for (i in (index + 1)..<lines.size) {
                val nextLine = lines[i].trim()
                if (nextLine.startsWith("#EXTINF")) break // 遇到下一个频道标签，说明当前频道结束
                if (nextLine.isBlank()) continue
                // 标签行（如 VLC 风格的 #EXTVLCOPT:http-user-agent=xxx），
                // 以及少数源里单独成行的 `http-user-agent="xxx"`：只取其中的 User-Agent
                if (nextLine.startsWith("#") || isAttributeLine(nextLine)) {
                    extractUserAgent(nextLine)?.let { userAgent = it }
                    continue
                }
                urlList.add(nextLine)
            }

            urlList.forEach { url ->
                iptvList.add(
                    IptvResponseItem(
                        order = order,
                        name = name,
                        channelName = channelName,
                        groupName = groupName,
                        url = url,
                        userAgent = userAgent,
                        logo = logo,
                    )
                )
            }
        }

        return@withContext ChannelGroupList(
            value = iptvList.groupBy { it.groupName }.map { groupEntry ->
                ChannelGroup(
                    name = groupEntry.key,
                    channelList = ChannelList(groupEntry.value.groupBy { it.name }.map { nameEntry ->
                        Channel(
                            name = nameEntry.key,
                            order = nameEntry.value.minOf { it.order },
                            epgName = nameEntry.value.first().channelName,
                            urlList = nameEntry.value.map { it.url }.distinct(),
                            userAgent = nameEntry.value.firstNotNullOfOrNull { it.userAgent },
                            logo = nameEntry.value.first().logo
                        )
                    })
                )
            },
            epgUrl = epgUrl,
        )
    }

    /**
     * 提取请求 User-Agent。
     *
     * 直播源中常见两种写法，后者为 VLC 风格、独立成行：
     *  - `#EXTINF:-1 http-user-agent="AptvPlayer-UA",频道名`
     *  - `#EXTVLCOPT:http-user-agent=AptvPlayer-UA`
     *
     * `user-agent` 兜底用于只写了裸属性名的源；由于 `http-user-agent=` 以 `user-agent=` 结尾，
     * 该兜底命中的是同一个值，不会误取到其它内容。
     */
    private fun extractUserAgent(line: String): String? {
        return extractAttribute(line, "http-user-agent")
            ?: extractAttribute(line, "user-agent")
    }

    /**
     * 判断是否为「属性赋值」行，例如 `http-user-agent="AptvPlayer-UA"`。
     *
     * 少数直播源会把属性拆到单独一行。这类行没有 `://` 却带 `=`，
     * 不能收进 urlList，否则会多出一条永远播不通的线路。真实播放地址都带 scheme，
     * 因此不会误伤。
     */
    private fun isAttributeLine(line: String): Boolean =
        line.contains("=") && !line.contains("://")

    /**
     * 高性能属性提取（避免使用正则）
     */
    private fun extractAttribute(line: String, attributeName: String): String? {
        val key = "$attributeName=\""
        val startIdx = line.indexOf(key)
        if (startIdx != -1) {
            val valueStart = startIdx + key.length
            val endIdx = line.indexOf("\"", valueStart)
            if (endIdx != -1) {
                return line.substring(valueStart, endIdx).trim()
            }
        }
        
        // 尝试不带引号的形式，例如 tvg-logo=http://...
        val keyNoQuote = "$attributeName="
        val startIdxNoQuote = line.indexOf(keyNoQuote)
        if (startIdxNoQuote != -1) {
            val valueStart = startIdxNoQuote + keyNoQuote.length
            var endIdx = line.indexOf(" ", valueStart)
            if (endIdx == -1) endIdx = line.indexOf(",", valueStart)
            if (endIdx == -1) endIdx = line.length
            
            val value = line.substring(valueStart, endIdx).trim()
            return value.ifBlank { null }
        }
        
        return null
    }

    private data class IptvResponseItem(
        val order: Int,
        val name: String,
        val channelName: String,
        val groupName: String,
        val url: String,
        val userAgent: String?,
        val logo: String?,
    )
}