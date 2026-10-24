package app.slatehome.dizien.terminal

import android.content.Context
import android.net.ConnectivityManager
import android.os.StatFs
import android.system.Os
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

enum class Phase { IDLE, RUNNING, PAUSED, WAITING_WIFI, FAILED, DONE }

val STEP_TITLES = listOf("Check storage & network", "Download Debian", "Verify SHA-256", "Extract & configure", "First launch")
val STEP_SUBTITLES = listOf("", "Official Debian image, pinned", "", "Unpacking into the app's private folder", "Test start with PRoot")

/** First-run installer: runs in a worker thread, state is observed by the Compose UI. */
object Installer {
    var phase by mutableStateOf(Phase.IDLE)
    var step by mutableStateOf(0)                 // index into STEP_TITLES
    var stepProgress by mutableStateOf(-1f)       // 0..1 for the current step, -1 = unknown
    var bytesDone by mutableLongStateOf(0L)
    var bytesTotal by mutableLongStateOf(0L)
    var speedBps by mutableLongStateOf(0L)
    var message by mutableStateOf("")             // short status line
    var error by mutableStateOf<String?>(null)
    var detail by mutableStateOf<String?>(null)   // technical details for a failed step

    @Volatile private var pauseRequested = false
    @Volatile private var cancelRequested = false
    @Volatile private var worker: Thread? = null

    private class Stop(val kind: String) : Exception()

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        synchronized(this) {
            if (worker?.isAlive == true) return
            pauseRequested = false; cancelRequested = false
            error = null; detail = null
            phase = Phase.RUNNING
            TerminalService.start(app)
            worker = Thread({ run(app) }, "dizien-installer").also { it.start() }
        }
    }

    fun pause() { pauseRequested = true }
    fun resume(ctx: Context) { if (phase == Phase.PAUSED || phase == Phase.FAILED || phase == Phase.WAITING_WIFI) start(ctx) }

    /** Stop and delete everything that was downloaded or half-unpacked. */
    fun cancelAndClean(ctx: Context) {
        val app = ctx.applicationContext
        cancelRequested = true
        Thread {
            worker?.join(8000)
            Rootfs.download(app).delete()
            Rootfs.deleteRecursively(Rootfs.staging(app))
            phase = Phase.IDLE; step = 0; stepProgress = -1f; bytesDone = 0; speedBps = 0; error = null; detail = null; message = ""
            TerminalService.refresh(app)
        }.start()
    }

    /** Delete the whole Debian system (used by Settings > Remove Debian). */
    fun uninstall(ctx: Context, done: () -> Unit) {
        val app = ctx.applicationContext
        Thread {
            Sessions.closeAll()
            Thread.sleep(600)
            Rootfs.deleteRecursively(Rootfs.base(app))
            Rootfs.download(app).delete()
            phase = Phase.IDLE; step = 0; stepProgress = -1f; bytesDone = 0; speedBps = 0; error = null; detail = null; message = ""
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(); TerminalService.refresh(app) }
        }.start()
    }

    private fun isMetered(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
    } catch (_: Exception) { false }

    private fun hasNetwork(ctx: Context): Boolean = try {
        @Suppress("DEPRECATION")
        (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo?.isConnected == true
    } catch (_: Exception) { true }

    private fun run(ctx: Context) {
        val prefs = Prefs(ctx)
        try {
            val src = Rootfs.source() ?: throw IOException("This phone's CPU is not supported. DiZien Terminal needs a 64-bit ARM phone (arm64-v8a).")
            // 1. check
            step = 0; stepProgress = -1f; message = "Checking storage and network"
            if (!Proot.binary(ctx).canExecute()) throw IOException("The PRoot program bundled with the app is missing or not executable.")
            Rootfs.base(ctx).mkdirs()
            val free = StatFs(ctx.filesDir.absolutePath).availableBytes
            if (free < Rootfs.MIN_FREE_BYTES) throw IOException("Not enough free storage: ${free / 1048576} MB free, about ${Rootfs.MIN_FREE_BYTES / 1048576} MB needed.")
            val part = Rootfs.download(ctx)
            part.parentFile!!.mkdirs()
            val haveFull = part.isFile && part.length() == src.bytes
            if (!haveFull && !hasNetwork(ctx)) throw IOException("No network connection. Connect to Wi-Fi and try again.")
            // 2. download
            if (!haveFull) { step = 1; download(ctx, src, part, prefs) }
            // 3. verify
            step = 2; stepProgress = 0f; bytesDone = 0; bytesTotal = src.bytes; speedBps = 0; message = "Verifying checksum"
            val md = MessageDigest.getInstance("SHA-256")
            part.inputStream().use { i ->
                val buf = ByteArray(65536); var n: Int; var tot = 0L
                while (i.read(buf).also { n = it } > 0) { md.update(buf, 0, n); tot += n; bytesDone = tot; stepProgress = tot.toFloat() / src.bytes; checkStop() }
            }
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (got != src.sha256) {
                part.delete()
                throw IOException("The downloaded file did not match the expected SHA-256 checksum, so it was deleted and not used. Try again.")
            }
            // 4. extract
            step = 3; stepProgress = 0f; message = "Unpacking Debian"
            val staging = Rootfs.staging(ctx)
            Rootfs.deleteRecursively(staging); File(Rootfs.base(ctx), "installed.ok").delete()
            extract(part, staging, src.bytes)
            val root = Rootfs.root(ctx)
            Rootfs.deleteRecursively(root)
            check(staging.renameTo(root)) { "Could not move the unpacked system into place." }
            message = "Configuring"; stepProgress = -1f
            Configure.guest(ctx)
            // 5. first launch
            step = 4; message = "Starting Debian for the first time"
            firstLaunch(ctx, prefs)
            File(Rootfs.base(ctx), "installed.ok").writeText("${src.debArch} ${System.currentTimeMillis()}\n")
            prefs.installedAt = System.currentTimeMillis()
            part.delete()
            phase = Phase.DONE; message = "Done"; stepProgress = 1f
        } catch (s: Stop) {
            phase = if (s.kind == "pause") Phase.PAUSED else Phase.IDLE
            message = if (s.kind == "pause") "Paused" else ""
        } catch (e: Exception) {
            android.util.Log.w("DiZienInstaller", "install failed at step $step", e)
            error = e.message ?: e.javaClass.simpleName
            phase = Phase.FAILED
        } finally {
            TerminalService.refresh(ctx)
        }
    }

    private fun checkStop() {
        if (cancelRequested) throw Stop("cancel")
        if (pauseRequested) throw Stop("pause")
    }

    private fun download(ctx: Context, src: RootfsSource, part: File, prefs: Prefs) {
        bytesTotal = src.bytes
        var attempt = 0
        while (true) {
            checkStop()
            if (prefs.wifiOnly && isMetered(ctx)) {
                phase = Phase.WAITING_WIFI; message = "Waiting for Wi-Fi (Wi-Fi only is on)"; speedBps = 0
                while (prefs.wifiOnly && isMetered(ctx)) { checkStop(); Thread.sleep(1000) }
                phase = Phase.RUNNING
            }
            val have = if (part.isFile) part.length() else 0L
            if (have > src.bytes) part.delete()
            val start = if (part.isFile) part.length() else 0L
            bytesDone = start
            var conn: HttpURLConnection? = null
            try {
                message = "Downloading from Debian's official image repository"
                conn = (URL(src.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000; readTimeout = 20000
                    setRequestProperty("User-Agent", "DiZienTerminal/${BuildConfig.VERSION_NAME}")
                    if (start > 0) setRequestProperty("Range", "bytes=$start-")
                }
                val code = conn.responseCode
                val append = code == 206
                if (code != 200 && code != 206) throw IOException("The server answered HTTP $code.")
                if (!append && start > 0) { part.delete(); bytesDone = 0 }
                var done = if (append) start else 0L
                var windowStart = System.nanoTime(); var windowBytes = 0L
                conn.inputStream.use { inp ->
                    FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(65536); var n: Int
                        while (inp.read(buf).also { n = it } > 0) {
                            out.write(buf, 0, n); done += n; windowBytes += n
                            bytesDone = done; stepProgress = (done.toFloat() / src.bytes).coerceIn(0f, 1f)
                            val now = System.nanoTime()
                            if (now - windowStart > 1_000_000_000L) {
                                val bps = windowBytes * 1_000_000_000L / (now - windowStart)
                                speedBps = if (speedBps == 0L) bps else (speedBps * 6 + bps * 4) / 10
                                windowStart = now; windowBytes = 0
                                if (prefs.wifiOnly && isMetered(ctx)) throw IOException("metered")
                            }
                            checkStop()
                        }
                    }
                }
                if (part.length() >= src.bytes) { speedBps = 0; return }
                throw IOException("The download ended early.")
            } catch (s: Stop) {
                throw s
            } catch (e: IOException) {
                if (e.message == "metered") continue
                attempt++
                if (attempt >= 4 || !hasNetwork(ctx)) throw IOException("Download interrupted: ${e.message ?: e.javaClass.simpleName}. Your progress is kept; tap Resume to continue.")
                message = "Connection problem, retrying ($attempt/3)"; Thread.sleep(2000L * attempt)
            } finally { conn?.disconnect() }
        }
    }

    private fun extract(archive: File, staging: File, compressedTotal: Long) {
        staging.mkdirs()
        val base = staging.canonicalFile
        val counting = CountingInputStream(archive.inputStream().buffered(1 shl 16))
        var entries = 0
        counting.use {
            val tar = TarReader(GZIPInputStream(counting, 1 shl 16))
            while (true) {
                checkStop()
                val e = tar.next() ?: break
                val name = e.name.removePrefix("./").trimEnd('/')
                if (name.isEmpty() || name == ".") continue
                require(!name.startsWith("/") && name.split('/').none { it == ".." }) { "Unsafe path in the archive" }
                val dest = File(base, name)
                // Never write through a symlink that the archive itself created.
                var p = dest.parentFile
                while (p != null && p != base) {
                    require(!java.nio.file.Files.isSymbolicLink(p.toPath())) { "Unsafe symlink in archive path" }
                    p = p.parentFile
                }
                dest.parentFile!!.mkdirs()
                when {
                    e.isDir -> { dest.mkdirs(); Os.chmod(dest.absolutePath, (e.mode and 511) or 448) }
                    e.isSymlink -> { if (dest.exists()) dest.delete(); Os.symlink(e.link, dest.absolutePath) }
                    e.isHardlink -> {
                        val target = File(base, e.link.removePrefix("./"))
                        require(target.canonicalPath.startsWith(base.path + "/")) { "Unsafe hard link in archive" }
                        if (dest.exists()) dest.delete()
                        try { Os.link(target.absolutePath, dest.absolutePath) } catch (_: Exception) {
                            target.copyTo(dest, overwrite = true)
                            Os.chmod(dest.absolutePath, (e.mode and 511) or 384)
                        }
                    }
                    e.isFile -> {
                        if (dest.exists()) dest.delete()
                        FileOutputStream(dest).use { o -> tar.data.copyTo(o, 65536) }
                        Os.chmod(dest.absolutePath, (e.mode and 511) or 384)
                    }
                    else -> { /* devices, fifos: Android provides /dev itself */ }
                }
                if (++entries % 50 == 0) { bytesDone = counting.count; stepProgress = (counting.count.toFloat() / compressedTotal).coerceIn(0f, 1f) }
            }
        }
        stepProgress = 1f
    }

    private fun firstLaunch(ctx: Context, prefs: Prefs) {
        val cmd = listOf(Proot.binary(ctx).absolutePath) + Proot.args(ctx, prefs,
            listOf("/usr/bin/env", "-i", "PATH=${Proot.PATH}", "/bin/bash", "-c", "echo DIZIEN_OK; . /etc/os-release; echo \"\$PRETTY_NAME\"; uname -m"))
            .drop(1)
        val pb = ProcessBuilder(cmd).redirectErrorStream(true).directory(ctx.filesDir)
        pb.environment().putAll(Proot.env(ctx, prefs))
        val proc = pb.start()
        val out = StringBuilder()
        val reader = Thread { try { proc.inputStream.bufferedReader().forEachLine { out.appendLine(it) } } catch (_: Exception) {} }
        reader.start()
        if (!proc.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) { proc.destroyForcibly(); throw IOException("Debian did not start within 90 seconds.") }
        reader.join(2000)
        if (!out.contains("DIZIEN_OK")) {
            detail = out.toString().take(1500)
            throw IOException("Debian unpacked but did not start (PRoot exit ${proc.exitValue()}). Details are shown below.")
        }
    }
}
