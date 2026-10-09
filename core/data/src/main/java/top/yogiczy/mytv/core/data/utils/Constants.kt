package top.yogiczy.mytv.core.data.utils

import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList

/**
 * 常量
 */
object Constants {
    /**
     * 应用 标题
     */
    const val APP_TITLE = "我的电视"

    /**
     * 应用 代码仓库
     */
    const val APP_REPO = "https://github.com/minyoad/mytv-android"

    /**
     * GitHub加速代理地址
     */
    const val GITHUB_PROXY = "https://gh.monlor.com/"

    const val WEBVIEW_CHANNELS_URL="https://raw.githubusercontent.com/minyoad/my-iptv/refs/heads/master/list/webview_channels.txt"

    /**
     * IPTV直播源
     *
     * 只保留杭州电信多线路源。该源一个频道标签下跟多行地址（多线路），
     * 解析由 M3uIptvParser 处理；部分频道带 http-user-agent 属性，按频道生效。
     */
    val IPTV_SOURCE_LIST = IptvSourceList(
        listOf(
            IptvSource(
                name = "默认直播源 杭州电信（多线路）",
                url = "https://gh-proxy.org/https://raw.githubusercontent.com/wangjhsir/iptv/refs/heads/main/hangzhou-multi-ip.m3u",
            ),
        )
    )

    /**
     * IPTV源缓存时间（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 12L // 12小时

    /**
     * 直播源周期检查间隔（毫秒）
     *
     * 电视端 App 会长时间停留在前台，Activity 不重建、也不会走 onResume，
     * 导致 init() 中的过期检查只有冷启动时才执行一次。此处定时唤起检查，
     * 是否真正发起网络请求仍由 IPTV_SOURCE_CACHE_TIME 窗口节流，
     * 因此定时唤起本身不产生额外请求压力。
     */
    const val IPTV_SOURCE_CHECK_INTERVAL = 1000 * 60 * 30L // 30分钟

    /**
     * 线路服务器（host:port）累计播放失败多少次后拉黑。
     *
     * 拉黑只在选线路时生效（跳过该服务器上的所有线路），
     * 且全部线路都被拉黑时忽略黑名单，避免频道彻底没法播。
     * 计数只在下一次刷新直播源时清空。
     */
    const val IPTV_URL_HOST_BLACKLIST_THRESHOLD = 3

    /**
     * 节目单来源
     */
    val EPG_SOURCE_LIST = EpgSourceList(
        listOf(
            EpgSource(
                name = "默认节目单 my吧",
                url = "https://iptv-cdn.mybacc.com/epg/e.xml.gz",
            ),
            EpgSource(
                name = "默认节目单 老张的EPG",
                url = "http://epg.51zmt.top:8000/e.xml.gz",
            ),
            EpgSource(
                name = "默认节目单 回看七天",
                url = "https://e.erw.cc/all.xml.gz",
            ),
            EpgSource(
                name = "默认节目单 mybacc 备用",
//                url = "https://iptv-cdn.mybacc.com/epg/e.xml.gz",
                url = GITHUB_PROXY+"https://raw.githubusercontent.com/minyoad/my-iptv/refs/heads/master/epg/e.xml.gz",
            ),
        )
    )

    /**
     * 频道logo来源
     */
    const val CHANNEL_LOGO_SOURCE = "https://iptv-cdn.mybacc.com/logo/"

    /**
     * 节目单刷新时间阈值（小时）
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2 // 不到2点不刷新

    /**
     * Git最新版本信息
     */
    val GIT_RELEASE_LATEST_URL = mapOf(
        "stable" to GITHUB_PROXY+"https://raw.githubusercontent.com/minyoad/mytv-android-update/main/tv-stable.json",
        "beta" to GITHUB_PROXY+"https://raw.githubusercontent.com/minyoad/mytv-android-update/main/tv-beta.json",
    )

    /**
     * HTTP请求重试次数
     */
    const val HTTP_RETRY_COUNT = 3L

    /**
     * HTTP请求重试间隔时间（毫秒）
     */
    const val HTTP_RETRY_INTERVAL = 1500L

    /**
     * 播放器 userAgent
     */
    const val VIDEO_PLAYER_USER_AGENT = "ExoPlayer"

    /**
     * 播放器加载超时
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 5 // 5秒

    /**
     * 日志历史最大保留条数
     */
    const val LOG_HISTORY_MAX_SIZE = 50

    /**
     * 界面 临时频道界面显示时间
     */
    const val UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION = 1500L // 1.5秒

    /**
     * 界面 超时未操作自动关闭界面
     */
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15 // 15秒

    /**
     * 界面 时间显示前后范围
     */
    const val UI_TIME_SCREEN_SHOW_DURATION = 1000L * 30 // 前后30秒
}
