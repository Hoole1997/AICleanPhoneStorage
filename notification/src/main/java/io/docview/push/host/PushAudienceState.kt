package io.docview.push.host

/** 配置默认值与已确认归因分开：即使默认配置为 paid，首次确认前也不能启用买量分池。 */
internal class PushAudienceState(initialChannel: PushUserChannel.UserChannelType) {
    private data class Snapshot(val channel: PushUserChannel.UserChannelType, val confirmed: Boolean)
    @Volatile private var snapshot = Snapshot(initialChannel, false)

    val channel get() = snapshot.channel
    val confirmedPaid get() = snapshot.let { it.confirmed && it.channel == PushUserChannel.UserChannelType.PAID }

    /** 用一个快照发布渠道与确认状态，跨线程读者不会看到一半更新的数据。 */
    @Synchronized fun confirm(channel: PushUserChannel.UserChannelType): PushUserChannel.UserChannelType {
        val previous = snapshot.channel
        snapshot = Snapshot(channel, true)
        return previous
    }
}
