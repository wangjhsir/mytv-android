package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants
import java.net.URLEncoder

/**
 * txt直播源解析
 */
class TxtIptvParser : IptvParser {

    override fun isSupport(url: String, data: String): Boolean {
        return data.contains("#genre#")
    }

    override suspend fun parse(data: String): ChannelGroupList = withContext(Dispatchers.Default) {
        val lines = data.split("\r\n", "\n")
        val iptvList = mutableListOf<IptvResponseItem>()

        var groupName: String? = null
        // 频道在文件中的出现次序：换台与数字选台以此为序，与分组无关
        var channelOrder = 0
        lines.forEach { line ->
            if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@forEach

            if (line.contains("#genre#")) {
                groupName = line.split(",", "，").firstOrNull()?.trim()
            } else {
                val res = line.split(",", "，")
                if (res.size < 2) return@forEach

                val order = channelOrder++
                val channelName = ChannelUtil.standardChannelName(res[0].trim())

                iptvList.addAll(res[1].split("#").map { url ->
                    IptvResponseItem(
                        order = order,
                        name = res[0].trim(),
                        channelName = channelName,
                        groupName = groupName ?: "其他",
                        url = url.trim(),
                    )
                })
            }
        }

        return@withContext ChannelGroupList(iptvList.groupBy { it.groupName }.map { groupEntry ->
            ChannelGroup(
                name = groupEntry.key,
                channelList = ChannelList(groupEntry.value.groupBy { it.name }.map { nameEntry ->
                    val channelName = nameEntry.value.first().channelName
                    
                    Channel(
                        name = nameEntry.key,
                        order = nameEntry.value.minOf { it.order },
                        epgName = channelName,
                        urlList = nameEntry.value.map { it.url }.distinct(),
                        logo = "${Constants.CHANNEL_LOGO_SOURCE}${channelName}.png"
                    )
                }),
            )
        })
    }

    private data class IptvResponseItem(
        val order: Int,
        val name: String,
        val channelName: String,
        val groupName: String,
        val url: String,
    )
}