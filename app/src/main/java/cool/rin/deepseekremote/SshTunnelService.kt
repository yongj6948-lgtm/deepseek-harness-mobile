package cool.rin.deepseekremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * 前台服务：SSH 公钥登录，并把电脑 127.0.0.1:3080 转到手机 127.0.0.1:<port>。
 * 首次若带了密码，会用 SFTP 把本机公钥写入远端 ~/.ssh/authorized_keys（密码不落盘）。
 * 参考 dsh-mobile-app 的 SshTunnelService.java。
 */
internal class SshTunnelService : Service() {

    companion object {
        const val ACTION_STATUS = "cool.rin.deepseekremote.TUNNEL_STATUS"
        const val EXTRA_STATE = "state"
        const val EXTRA_MSG = "msg"
        const val EXTRA_PORT = "port"
        const val EXTRA_PASSWORD = "password"
        const val STATE_CONNECTING = "connecting"
        const val STATE_UP = "up"
        const val STATE_ERR = "err"
        const val STATE_DOWN = "down"

        const val PREFS = "deepseek_ssh_preferences"
        const val KEY_SSH_HOST = "ssh_host"
        const val KEY_SSH_USER = "ssh_user"
        const val KEY_SSH_PORT = "ssh_port"
        const val KEY_REMOTE_PORT = "remote_port"

        private const val CH = "dsh_ssh"
        private const val ACTION_STOP = "cool.rin.deepseekremote.STOP_TUNNEL"

        fun start(ctx: Context, passwordOrNull: String?) {
            val i = Intent(ctx, SshTunnelService::class.java)
            if (!passwordOrNull.isNullOrEmpty()) {
                i.putExtra(EXTRA_PASSWORD, passwordOrNull)
            }
            // 个别 ROM / FGS 策略会拒绝前台服务启动，降级为普通 startService，避免崩溃
            try {
                ctx.startForegroundService(i)
            } catch (e: Exception) {
                ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, SshTunnelService::class.java))
        }
    }

    private var session: Session? = null
    private var worker: Thread? = null
    @Volatile private var running = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "DeepSeek SSH 隧道", NotificationManager.IMPORTANCE_LOW)
            ch.description = "保持手机到 DSH 电脑的 SSH 端口转发"
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && ACTION_STOP == intent.action) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(1, notification("正在连接 SSH…"))
            val password = intent?.getStringExtra(EXTRA_PASSWORD)
            running = true
            worker?.interrupt()
            worker = Thread({ connectLoop(password) }, "dsh-ssh").also { it.start() }
        } catch (e: Exception) {
            // 前台通知/线程启动被 ROM 拦截时不自杀，广播错误并停服，由主界面提示
            running = false
            broadcast(STATE_ERR, e.message ?: e.javaClass.simpleName, 0)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        closeSession()
        worker?.interrupt()
        broadcast(STATE_DOWN, "已断开", 0)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectLoop(firstPassword: String?) {
        var password = firstPassword
        var backoff = 1000L
        while (running) {
            try {
                broadcast(STATE_CONNECTING, "正在连接 SSH…", 0)
                val port = connectOnce(password)
                password = null // 只在首次尝试用密码装公钥
                backoff = 1000L
                startForeground(1, notification("隧道已连接  127.0.0.1:$port"))
                broadcast(STATE_UP, "SSH 已连接", port)
                while (running && session != null && session!!.isConnected) {
                    Thread.sleep(2000)
                }
                if (!running) return
                startForeground(1, notification("隧道断开，重连中…"))
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                if (!running) return
                val msg = e.message ?: e.javaClass.simpleName
                broadcast(STATE_ERR, msg, 0)
                startForeground(1, notification("连接失败：$msg"))
                password = null
            }
            closeSession()
            try {
                Thread.sleep(backoff)
            } catch (e: InterruptedException) {
                return
            }
            backoff = minOf(backoff * 2, 15000L)
        }
    }

    private fun connectOnce(password: String?): Int {
        val p = getSharedPreferences(PREFS, MODE_PRIVATE)
        val host = p.getString(KEY_SSH_HOST, "").orEmpty().trim()
        val user = p.getString(KEY_SSH_USER, "").orEmpty().trim()
        val sshPort = parsePort(p.getString(KEY_SSH_PORT, "22"), 22)
        val remotePort = parsePort(p.getString(KEY_REMOTE_PORT, "3080"), 3080)
        if (host.isEmpty() || user.isEmpty()) {
            throw IllegalStateException("未填写 SSH 主机或用户名")
        }

        SshKeys.ensurePublicKey(this)
        val jsch = JSch()
        jsch.addIdentity(SshKeys.privateKeyFile(this).absolutePath)
        loadKnownHosts(jsch)

        val s = jsch.getSession(user, host, sshPort)
        s.setConfig("StrictHostKeyChecking", "accept-new")
        s.setConfig(
            "PreferredAuthentications",
            if (!password.isNullOrEmpty()) "publickey,password,keyboard-interactive" else "publickey",
        )
        s.serverAliveInterval = 30_000
        s.serverAliveCountMax = 3
        if (!password.isNullOrEmpty()) {
            s.setPassword(password)
        }
        s.connect(15000)

        if (!password.isNullOrEmpty()) {
            try {
                installAuthorizedKey(s)
            } catch (e: Exception) {
                // 装公钥失败不阻断本次隧道，下次仍可用密码或手动粘贴
                LogSafe.w("SshTunnel", "install authorized_keys failed", e)
            }
        }

        val localPort = s.setPortForwardingL("127.0.0.1", 0, "127.0.0.1", remotePort)
        session = s
        return localPort
    }

    private fun loadKnownHosts(jsch: JSch) {
        try {
            val kh = java.io.File(filesDir, "known_hosts")
            if (!kh.exists()) kh.createNewFile()
            jsch.setKnownHosts(kh.absolutePath)
        } catch (ignored: Exception) {
        }
    }

    private fun installAuthorizedKey(s: Session) {
        val pub = SshKeys.publicKeyLine(this)
        val body = SshKeys.publicKeyBody(this)
        val sftp = s.openChannel("sftp") as ChannelSftp
        sftp.connect(10000)
        try {
            try {
                sftp.mkdir(".ssh")
            } catch (ignored: SftpException) {
            }
            var existing = ""
            try {
                sftp.get(".ssh/authorized_keys").use { input -> existing = readAll(input) }
            } catch (ignored: SftpException) {
            }
            if (existing.contains(body)) return
            val next = StringBuilder(existing)
            if (next.isNotEmpty() && next.last() != '\n') next.append('\n')
            next.append(pub.trim()).append('\n')
            val data = next.toString().toByteArray(StandardCharsets.UTF_8)
            sftp.put(ByteArrayInputStream(data), ".ssh/authorized_keys")
        } finally {
            sftp.disconnect()
        }
    }

    private fun readAll(input: InputStream): String {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var n: Int
        while (input.read(buf).also { n = it } != -1) bos.write(buf, 0, n)
        return bos.toString(StandardCharsets.UTF_8.name())
    }

    private fun closeSession() {
        val s = session
        session = null
        if (s != null) {
            try {
                s.disconnect()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun notification(text: String): Notification {
        val open = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = Intent(this, SshTunnelService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else Notification.Builder(this)
        return b
            .setContentTitle("DeepSeek Harness Mobile")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pi)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "断开", stopPi).build())
            .build()
    }

    private fun broadcast(state: String, msg: String, port: Int) {
        val i = Intent(ACTION_STATUS)
        i.setPackage(packageName)
        i.putExtra(EXTRA_STATE, state)
        i.putExtra(EXTRA_MSG, msg)
        i.putExtra(EXTRA_PORT, port)
        sendBroadcast(i)
    }

    private fun parsePort(raw: String?, fallback: Int): Int {
        return raw?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 } ?: fallback
    }
}

/** 轻量日志封装，避免直连 android.util.Log 被 minify 噪音干扰。 */
internal object LogSafe {
    fun w(tag: String, msg: String, t: Throwable) {
        android.util.Log.w(tag, msg, t)
    }
}
