package app.slatehome.dizien.terminal

import android.content.Context
import android.os.Build
import java.io.File

/** Small settings store (plain SharedPreferences, nothing leaves the phone). */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("dizien_terminal", Context.MODE_PRIVATE)
    var fontSizeSp: Int
        get() = sp.getInt("font_size", 14)
        set(v) = sp.edit().putInt("font_size", v.coerceIn(8, 28)).apply()
    var keepAwake: Boolean
        get() = sp.getBoolean("keep_awake", false)
        set(v) = sp.edit().putBoolean("keep_awake", v).apply()
    var wifiOnly: Boolean
        get() = sp.getBoolean("wifi_only", true)
        set(v) = sp.edit().putBoolean("wifi_only", v).apply()
    var extraKeys: Boolean
        get() = sp.getBoolean("extra_keys", true)
        set(v) = sp.edit().putBoolean("extra_keys", v).apply()
    /** PRoot without seccomp acceleration: slower but works on every phone/kernel. */
    var compatMode: Boolean
        get() = sp.getBoolean("compat_mode", true)
        set(v) = sp.edit().putBoolean("compat_mode", v).apply()
    var sharedStorage: Boolean
        get() = sp.getBoolean("shared_storage", false)
        set(v) = sp.edit().putBoolean("shared_storage", v).apply()
    var installedAt: Long
        get() = sp.getLong("installed_at", 0L)
        set(v) = sp.edit().putLong("installed_at", v).apply()
}

/** Description of the one official Debian root filesystem image this build installs. */
class RootfsSource(
    val abi: String,           // Android ABI name
    val debArch: String,       // Debian architecture name
    val url: String,
    val sha256: String,
    val bytes: Long,
)

object Rootfs {
    const val RELEASE_NAME = "trixie"
    const val RELEASE_NUMBER = "13"
    const val HOSTNAME = "dz-debian"
    const val ORIGIN = "debuerreotype/docker-debian-artifacts (official Debian Docker image build)"

    // Pinned to an exact commit of Debian's own image-build repository, so the download can never
    // silently change; the SHA-256 below is also checked before anything is extracted.
    private val ARM64 = RootfsSource(
        abi = "arm64-v8a", debArch = "arm64",
        url = "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/" +
            "9c4818e3bfd35139d3798b0420e9c46836658b2e/trixie/slim/oci/blobs/rootfs.tar.gz",
        sha256 = "ddc0c1e36a6793411b6c8d48e1ddad2987405d377da92bcf532ed3181c2e4d62",
        bytes = 30_200_478L,
    )
    // Only used by the emulator test build (x86_64); phones use arm64.
    private val AMD64 = RootfsSource(
        abi = "x86_64", debArch = "amd64",
        url = "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/" +
            "cee78df586385250331cacba8fd1140133c054e5/trixie/slim/oci/blobs/rootfs.tar.gz",
        sha256 = "f2be67225471500c788ce88c597eb5ea8336e912ad1ca4815650e0281afe2034",
        bytes = 29_843_579L,
    )

    /** Null when this phone's CPU is not supported (only 64-bit ARM is). */
    fun source(): RootfsSource? {
        val abis = Build.SUPPORTED_ABIS.toList()
        if ("arm64-v8a" in abis) return ARM64
        if (BuildConfig.X86_TEST && "x86_64" in abis) return AMD64
        return null
    }

    const val MIN_FREE_BYTES = 450L * 1024 * 1024

    fun base(ctx: Context) = File(ctx.filesDir, "debian")
    fun root(ctx: Context) = File(base(ctx), "rootfs")
    fun download(ctx: Context) = File(ctx.filesDir, "downloads/debian-rootfs.tar.gz.part")
    fun staging(ctx: Context) = File(base(ctx), "rootfs.installing")
    fun fakeProc(ctx: Context) = File(base(ctx), "fake-proc")
    fun prootTmp(ctx: Context) = File(ctx.cacheDir, "proot-tmp")
    fun nativeDir(ctx: Context) = File(ctx.applicationInfo.nativeLibraryDir)

    /** Installed means the unpack finished and the marker file written last exists. */
    fun isInstalled(ctx: Context) = File(base(ctx), "installed.ok").isFile && File(root(ctx), "bin").exists()

    fun dirSize(f: File): Long {
        if (!f.exists()) return 0
        var total = 0L
        val stack = ArrayDeque<File>(); stack.add(f)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            val kids = cur.listFiles()
            if (kids == null) { total += cur.length(); continue }
            for (k in kids) {
                if (java.nio.file.Files.isSymbolicLink(k.toPath())) continue
                if (k.isDirectory) stack.add(k) else total += k.length()
            }
        }
        return total
    }

    fun deleteRecursively(f: File) {
        // Never follow symlinks: the Debian tree contains absolute symlinks such as /bin -> usr/bin.
        val kids = if (!java.nio.file.Files.isSymbolicLink(f.toPath())) f.listFiles() else null
        kids?.forEach { deleteRecursively(it) }
        try { android.system.Os.chmod(f.absolutePath, 0x1C0 /* 0700 */) } catch (_: Exception) {}
        f.delete()
    }
}
