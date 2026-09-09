package io.docview.push.host

import io.docview.push.BuildConfig
import java.util.concurrent.CopyOnWriteArraySet

/** 安装分发 flavor 与用户归因渠道是两件事；在归因 SDK 未接入前使用渠道文件声明的默认值。 */
internal object PushUserChannel {
    enum class UserChannelType(val value: String) { PAID("paid"), NATURAL("natural") }
    interface ChannelChangeListener { fun onChannelChanged(oldChannel: UserChannelType, newChannel: UserChannelType) }
    private val listeners = CopyOnWriteArraySet<ChannelChangeListener>()
    @Volatile private var channel = if (BuildConfig.USER_CHANNEL == "paid") UserChannelType.PAID else UserChannelType.NATURAL
    fun getCurrentChannel() = channel
    fun addChannelChangeListener(listener: ChannelChangeListener) { listeners.add(listener) }
    fun setChannel(value: UserChannelType) {
        val old = channel
        if (old == value) return
        channel = value
        listeners.forEach { it.onChannelChanged(old, value) }
    }
}
