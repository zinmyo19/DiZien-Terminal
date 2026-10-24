package app.slatehome.dizien.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView

/** Holds every open Debian shell. Lives as long as the app process (kept alive by [TerminalService]). */
object Sessions : TerminalSessionClient {
    val list = mutableStateListOf<TerminalSession>()
    var current by mutableStateOf<TerminalSession?>(null)
    /** Bumped whenever a title changes, so tabs can refresh. */
    var tick by mutableStateOf(0)
    var view: TerminalView? = null
    var onEmpty: (() -> Unit)? = null
    @Volatile var exitOnEmpty = true
    /** Lowest number not used by a tab that is still open, so the first tab is always "Session 1". */
    private fun lowestFreeNumber(): Int {
        val used = list.mapNotNull { it.mSessionName?.removePrefix("Session ")?.trim()?.toIntOrNull() }.toSet()
        var n = 1
        while (n in used) n++
        return n
    }
    private lateinit var app: Context
    /** Set by the activity: handles "debian-setup-storage" requests from inside Debian. */
    @Volatile var onStorageRequest: ((String) -> Unit)? = null
    private var ipcThread: Thread? = null
    private fun ipcDir() = java.io.File(Rootfs.root(app), "run/dizien")
    /** Tell the waiting Debian command what happened (text file in /run/dizien). */
    fun respond(text: String) {
        try {
            val d = ipcDir(); d.mkdirs()
            val tmp = java.io.File(d, "response.tmp"); tmp.writeText(text + "\n")
            tmp.renameTo(java.io.File(d, "response"))
        } catch (_: Exception) {}
    }
    private fun startIpc() {
        if (ipcThread?.isAlive == true) return
        ipcThread = Thread {
            while (true) {
                try {
                    val req = java.io.File(ipcDir(), "request")
                    if (req.exists()) {
                        val what = req.readText().trim(); req.delete()
                        val h = onStorageRequest
                        if (what == "storage" || what == "newtab") { if (h != null) h(what) else respond("failed: the DiZien Terminal app screen is not open") }
                    }
                    Thread.sleep(400)
                } catch (_: InterruptedException) { return@Thread } catch (_: Exception) { try { Thread.sleep(1000) } catch (_: Exception) {} }
            }
        }.also { it.isDaemon = true; it.name = "dizien-ipc"; it.start() }
    }

    fun init(ctx: Context) { app = ctx.applicationContext }

    fun create(ctx: Context): TerminalSession {
        init(ctx)
        val prefs = Prefs(ctx)
        Configure.refreshDns(ctx)
        Configure.ensureLayout(ctx)
        startIpc()
        val envMap = Proot.env(ctx, prefs)
        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray() + arrayOf("TERM=xterm-256color")
        val argv = Proot.args(ctx, prefs, Proot.guestShell()).toTypedArray()
        val s = TerminalSession(Proot.binary(ctx).absolutePath, ctx.filesDir.absolutePath, argv, env, 4000, this)
        s.mSessionName = "Session ${lowestFreeNumber()}"
        list.add(s); current = s
        TerminalService.start(ctx)
        return s
    }

    private val closing = HashSet<TerminalSession>()

    /** Close a tab: stops the shell if it is still running. */
    fun close(s: TerminalSession) {
        if (s.isRunning) { closing += s; s.finishIfRunning() } else remove(s)
    }

    fun closeAll() { list.toList().forEach { closing += it; it.finishIfRunning() } }

    private fun remove(s: TerminalSession) {
        closing -= s
        val idx = list.indexOf(s)
        list.remove(s)
        if (current === s) current = list.getOrNull(minOf(idx, list.size - 1))
        if (list.isEmpty() && exitOnEmpty) onEmpty?.invoke()
        TerminalService.refresh(app)
    }

    // ---- TerminalSessionClient ----
    override fun onTextChanged(changedSession: TerminalSession) {
        if (changedSession === current) view?.onScreenUpdated()
    }
    override fun onTitleChanged(changedSession: TerminalSession) { tick++ }
    override fun onSessionFinished(finishedSession: TerminalSession) {
        // A normal `exit` closes the tab; a crash/error keeps it open so its message can be read.
        if (finishedSession in closing || finishedSession.exitStatus == 0) remove(finishedSession) else tick++
    }
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("DiZien Terminal", text))
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) { paste(session) }
    fun paste(session: TerminalSession? = current) {
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(app)?.toString()
        if (!t.isNullOrEmpty()) session?.emulator?.paste(t)
    }
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) { view?.onScreenUpdated() }
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int? = null
    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}

/** Foreground service: keeps shells and the installer alive while the app is in the background. */
class TerminalService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Android requires startForeground() soon after startForegroundService(); do it right away.
        startForeground(NOTIF_ID, notification(this))
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            Sessions.closeAll(); Installer.pause()
            stopForeground(true); stopSelf()
            return START_NOT_STICKY
        }
        if (!needed()) { stopForeground(true); stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        @Volatile private var instance: TerminalService? = null
        private val main = android.os.Handler(android.os.Looper.getMainLooper())
        const val CHANNEL = "sessions"
        const val NOTIF_ID = 1
        const val ACTION_EXIT = "app.slatehome.dizien.terminal.EXIT"

        fun needed() = Sessions.list.isNotEmpty() || Installer.phase == Phase.RUNNING || Installer.phase == Phase.WAITING_WIFI

        fun start(ctx: Context) {
            val i = Intent(ctx, TerminalService::class.java)
            // The app is visible when this is called, so a plain startService() is allowed; the service
            // promotes itself with startForeground() in onCreate(). This avoids the strict 10-second
            // startForegroundService() deadline on very slow or heavily loaded devices.
            try { ctx.startService(i) } catch (e: IllegalStateException) {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            }
        }

        /** Re-evaluate: update the notification text or stop the service when nothing is running. */
        fun refresh(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (needed()) { if (instance != null) nm.notify(NOTIF_ID, notification(ctx)) }
            else main.post { instance?.let { it.stopForeground(true); it.stopSelf() } }
        }

        private fun notification(ctx: Context): Notification {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Terminal sessions", NotificationManager.IMPORTANCE_LOW))
            }
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT)
            val exit = PendingIntent.getService(ctx, 1, Intent(ctx, TerminalService::class.java).setAction(ACTION_EXIT), PendingIntent.FLAG_UPDATE_CURRENT)
            val text = when {
                Installer.phase == Phase.RUNNING || Installer.phase == Phase.WAITING_WIFI -> "Setting up Debian..."
                Sessions.list.size == 1 -> "1 session running"
                else -> "${Sessions.list.size} sessions running"
            }
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(ctx)
            b.setSmallIcon(R.drawable.ic_notification).setContentTitle("DiZien Terminal").setContentText(text)
                .setContentIntent(open).setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Exit", exit).build())
            return b.build()
        }
    }
}
