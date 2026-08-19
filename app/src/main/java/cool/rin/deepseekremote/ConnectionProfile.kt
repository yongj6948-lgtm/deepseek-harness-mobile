package cool.rin.deepseekremote

/**
 * 单条连接配置：直连（HTTPS / 内网 HTTP）或 SSH 隧道。
 * 用于把「直连地址」和「SSH 电脑」统一放进同一个连接管理器。
 */
internal data class ConnectionProfile(
    val mode: String,
    val url: String = "",
    val sshHost: String = "",
    val sshUser: String = "",
    val sshPort: String = "22",
    val sshRemotePort: String = "3080",
) {

    companion object {
        const val MODE_DIRECT = "direct"
        const val MODE_SSH = "ssh"

        fun direct(url: String) = ConnectionProfile(MODE_DIRECT, url = url)
    }
}
