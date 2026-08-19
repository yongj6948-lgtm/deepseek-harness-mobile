package cool.rin.deepseekremote

import android.content.Context
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 首次启动在应用私有目录生成 RSA 密钥，之后复用。
 * 机制与 dsh-mobile-app 的 SshKeys.java 一致：公钥经 SFTP 写入电脑
 * ~/.ssh/authorized_keys 后，后续连接只走公钥，不再需要密码。
 */
internal object SshKeys {

    private const val PRIV = "id_dsh"
    private const val PUB = "id_dsh.pub"
    private const val COMMENT = "deepseek-harness-mobile@android"

    fun privateKeyFile(ctx: Context): File = File(ctx.filesDir, PRIV)

    fun publicKeyFile(ctx: Context): File = File(ctx.filesDir, PUB)

    /** 保证密钥存在，返回 OpenSSH 公钥行。 */
    fun ensurePublicKey(ctx: Context): String {
        val pub = publicKeyFile(ctx)
        val priv = privateKeyFile(ctx)
        if (pub.isFile && priv.isFile && pub.length() > 0 && priv.length() > 0) {
            return read(pub)
        }
        generate(ctx)
        return read(pub)
    }

    fun publicKeyLine(ctx: Context): String = ensurePublicKey(ctx).trim()

    /** OpenSSH 公钥里的 key body，用来判断 authorized_keys 是否已写入。 */
    fun publicKeyBody(ctx: Context): String {
        val line = publicKeyLine(ctx)
        val parts = line.split(Regex("\\s+"))
        return if (parts.size >= 2) parts[1] else line
    }

    private fun generate(ctx: Context) {
        try {
            val kp = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 3072)
            try {
                kp.writePrivateKey(privateKeyFile(ctx).absolutePath)
                kp.writePublicKey(publicKeyFile(ctx).absolutePath, COMMENT)
            } finally {
                kp.dispose()
            }
        } catch (e: Exception) {
            throw IllegalStateException("生成 SSH 密钥失败: ${e.message}", e)
        }
    }

    private fun read(f: File): String {
        try {
            f.inputStream().use { input ->
                val bos = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var n: Int
                while (input.read(buf).also { n = it } != -1) bos.write(buf, 0, n)
                return bos.toString(StandardCharsets.UTF_8.name()).trim()
            }
        } catch (e: Exception) {
            return ""
        }
    }
}
