package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.network.OkHttp
import top.yogiczy.mytv.core.data.network.await
import top.yogiczy.mytv.core.data.repositories.FileCacheRepository
import top.yogiczy.mytv.core.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.data.utils.SP
import top.yogiczy.mytv.core.util.utils.removeBom


class IdGenerator {
    private var currentId = 0

    fun nextId(): Int {
        return ++currentId
    }

    fun reset() {
        currentId = 0
    }
}

/**
 * 直播源数据获取
 *
 * @param disableCache 长辈模式：始终联网拉取最新数据，既不读也不写缓存文件。
 *   两点例外：
 *    - 本地源（[IptvSource.isLocal]）本身就是磁盘文件，仍按原逻辑直接读取；
 *    - 拉取失败时仍会回退读一次旧缓存，见 [fetchLatestOrFallbackToCache]。
 */
class IptvRepository(
    private val source: IptvSource,
    private val disableCache: Boolean = false,
) : FileCacheRepository(
    if (source.isLocal) source.url
    else "iptv-${source.url.hashCode().toUInt().toString(16)}.txt",
    source.isLocal,
) {
    private val log = Logger.create(IptvRepository::class.java.simpleName)

    /**
     * 最近一次 [getChannelGroupList] 是否因联网失败而回退到了本地缓存。
     *
     * 长辈模式下这是「保住能看电视」的兜底，但拿到的可能是过期内容，上层需据此提示用户。
     * 每次 [getChannelGroupList] 都会重置。Repository 由调用方按次构造，本属性不跨实例共享。
     */
    var lastLoadUsedCacheFallback: Boolean = false
        private set

    private val idGenerator = IdGenerator()

    private val etagKey = "iptv_etag_${source.url.hashCode().toUInt().toString(16)}"
    private var etag: String
        get() = SP.getString(etagKey, "")
        set(value) = SP.putString(etagKey, value)

    /**
     * 获取直播源数据
     */
    private suspend fun fetchSource(sourceUrl: String): String? {
        log.d("获取远程直播源: $source")

        val client = OkHttp.client
        val request = Request.Builder().url(sourceUrl).apply {
            // 长辈模式不读缓存，也就没有可协商的基准版本，直接取全量内容，
            // 否则服务器回 304 时拿不到数据，只能又退回缓存，与长辈模式的初衷相悖
            if (!disableCache && etag.isNotEmpty() && getCacheFile().exists()) {
                header("If-None-Match", etag)
            }
        }.build()

        try {
            val response = client.newCall(request).await()

            if (response.code == 304) {
                log.i("直播源内容未变动 (304 Not Modified): $source")
                return null
            }

            if (!response.isSuccessful) throw Exception("${response.code}: ${response.message}")

            // 长辈模式既不读也不写缓存，因此也不记录 ETag：否则 ETag 会指向一个
            // 从未落盘的版本，等用户关掉长辈模式后，缓存文件里的旧内容会与这个
            // 更新的 ETag 对不上——协商到 304 时会把旧内容当成最新数据使用
            if (!disableCache) {
                etag = response.header("ETag") ?: ""
            }
            return withContext(Dispatchers.IO) {
                response.body?.string() ?: ""
            }
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception("获取直播源失败，请检查网络连接", ex)
        }
    }

    /**
     * 获取直播源分组列表
     */
    suspend fun getChannelGroupList(cacheTime: Long): ChannelGroupList {
        lastLoadUsedCacheFallback = false
        try {
            val rawData = when {
                // 本地源的 url 就是磁盘上的文件路径，没有「联网获取」一说，直接读文件
                source.isLocal -> getOrRefresh(Long.MAX_VALUE) { fetchSource(source.url) }
                disableCache -> fetchLatestOrFallbackToCache()
                else -> getOrRefresh(cacheTime) { fetchSource(source.url) }
            }
            return parseData(rawData)
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception(ex)
        }
    }

    /**
     * 长辈模式的数据获取：只走网络，不读也不写缓存。
     *
     * 失败（请求异常，或服务器返回 304 导致拿不到内容）时，回退读一次磁盘上的旧缓存——
     * 那可能是开启长辈模式之前留下的，也可能压根不存在。两样都没有才抛异常让上层进错误页，
     * 而不是把空字符串丢给解析器（那会得到「不支持当前直播源格式」的占位频道列表）。
     */
    private suspend fun fetchLatestOrFallbackToCache(): String {
        val outcome = runCatching { fetchSource(source.url) }
        outcome.exceptionOrNull()?.let {
            log.e("长辈模式获取最新直播源失败，尝试回退本地缓存", it)
        }

        outcome.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }

        val cached = getCacheData()
        if (!cached.isNullOrBlank()) {
            log.w("长辈模式未取到最新直播源，已回退本地缓存")
            lastLoadUsedCacheFallback = true
            return cached
        }

        // 既没拿到新数据、也没有可回退的缓存：交给上层进错误页
        throw outcome.exceptionOrNull() ?: Exception("获取直播源失败，且无本地缓存可回退")
    }

    /**
     * 获取缓存的直播源分组列表
     */
    suspend fun getCachedChannelGroupList(): ChannelGroupList? {
        return try {
            withContext(Dispatchers.IO) {
                getCacheData()?.let { parseData(it) }
            }
        } catch (ex: Exception) {
            null
        }
    }

    private suspend fun parseData(rawData: String): ChannelGroupList {
        return withContext(Dispatchers.IO) {
            val sourceData = rawData.removeBom()

            val parser = IptvParser.instances.first { it.isSupport(source.url, sourceData) }
            val startTime = System.currentTimeMillis()
            val groupList = parser.parse(sourceData)

            // 在获取到频道列表后，统一生成数字序号 ID
            idGenerator.reset()
            val groupListWithIds = ChannelGroupList(
                value = groupList.map { group ->
                    group.copy(channelList = ChannelList(group.channelList.map { channel ->
                        channel.copy(id = idGenerator.nextId().toString())
                    }))
                },
                epgUrl = groupList.epgUrl,
            )

            log.i(
                listOf(
                    "解析直播源（${source.name}）完成：${groupList.size}个分组",
                    "${groupList.sumOf { it.channelList.size }}个频道",
                    "${groupList.sumOf { it.channelList.sumOf { channel -> channel.urlList.size } }}条线路",
                    "耗时：${System.currentTimeMillis() - startTime}ms",
                ).joinToString()
            )

            groupListWithIds
        }
    }

    override suspend fun clearCache() {
        if (source.isLocal) return
        super.clearCache()
    }
}