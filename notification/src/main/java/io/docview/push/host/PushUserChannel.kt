package io.docview.push.host

import io.docview.push.BuildConfig
import java.util.concurrent.CopyOnWriteArraySet

/** 宿主同步 SDK 真实归因；渠道文件默认值仅供配置回退，不能作为启用买量分池的依据。 */
internal object PushUserChannel {
    enum class UserChannelType(val value: String) { PAID("paid"), NATURAL("natural") }
    interface ChannelChangeListener { fun onChannelChanged(oldChannel: UserChannelType, newChannel: UserChannelType) }
    private val listeners = CopyOnWriteArraySet<ChannelChangeListener>()
    private val audience = PushAudienceState(if (BuildConfig.USER_CHANNEL == "paid") UserChannelType.PAID else UserChannelType.NATURAL)
    fun isConfirmedPaidUser() = audience.confirmedPaid
    fun getCurrentChannel() = audience.channel
    fun addChannelChangeListener(listener: ChannelChangeListener) { listeners.add(listener) }
    fun setChannel(value: UserChannelType) {
        val old = audience.confirm(value)
        if (old == value) return
        listeners.forEach { it.onChannelChanged(old, value) }
    }
}
