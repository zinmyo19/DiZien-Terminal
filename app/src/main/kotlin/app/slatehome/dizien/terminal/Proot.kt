package app.slatehome.dizien.terminal

import android.content.Context
import android.net.ConnectivityManager
import android.system.Os
import java.io.File

/** Builds the PRoot command that runs the private Debian root filesystem (no root needed). */
object Proot {
    const val PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    fun binary(ctx: Context) = File(Rootfs.nativeDir(ctx), "libproot.so")

    /** Is the shared phone storage reachable from this app right now? */
    fun sharedStoragePath(): File? {
        val f = File("/storage/emulated/0")
        // Bound whenever the user switched "Phone storage" on; Android itself decides if access is allowed.
        return if (f.isDirectory) f else null
    }

    /** argv for PRoot (argv[0] included) that starts [inner] inside Debian. */
    fun args(ctx: Context, prefs: Prefs, inner: List<String>): List<String> {
        val root = Rootfs.root(ctx)
        val fake = Rootfs.fakeProc(ctx)
        val l = ArrayList<String>()
        l += "proot"
        l += listOf("--kill-on-exit", "--link2symlink", "-0", "-r", root.absolutePath)
        for (p in listOf("/dev", "/proc", "/sys")) if (File(p).exists()) l += listOf("-b", p)
        // Android hides parts of /proc from apps; give Debian harmless stand-ins so ps/top/free work.
        for (n in listOf("stat", "loadavg", "uptime", "version", "vmstat")) {
            if (File(fake, n).isFile) l += listOf("-b", "${File(fake, n).absolutePath}:/proc/$n")
        }
        l += listOf("-b", "/proc/self/fd:/dev/fd", "-b", "/proc/self/fd/0:/dev/stdin",
            "-b", "/proc/self/fd/1:/dev/stdout", "-b", "/proc/self/fd/2:/dev/stderr")
        File(root, "tmp").mkdirs()
        l += listOf("-b", "${File(root, "tmp").absolutePath}:/dev/shm")
        if (prefs.sharedStorage) sharedStoragePath()?.let { l += listOf("-b", "${it.absolutePath}:/sdcard") }
        l += listOf("-w", "/root")
        l += inner
        return l
    }

    fun env(ctx: Context, prefs: Prefs): Map<String, String> {
        val nd = Rootfs.nativeDir(ctx)
        val tmp = Rootfs.prootTmp(ctx).also { it.mkdirs() }
        val m = linkedMapOf(
            "PROOT_TMP_DIR" to tmp.absolutePath,
            "PROOT_LOADER" to File(nd, "libproot-loader.so").absolutePath,
            "PROOT_LOADER_32" to File(nd, "libproot-loader32.so").absolutePath,
            // Android's own variables must not leak into Debian programs.
            "PATH" to "/system/bin",
            "HOME" to ctx.filesDir.absolutePath,
        )
        // Compatibility mode (default) skips PRoot's seccomp speed-up, which is the part that
        // differs between phone kernels (Snapdragon, Exynos, MediaTek, Tensor ...).
        if (prefs.compatMode) m["PROOT_NO_SECCOMP"] = "1"
        return m
    }

    /** `env -i ...` line that gives Debian a clean login environment. */
    fun guestShell(extraEnv: List<String> = emptyList()): List<String> = listOf(
        "/usr/bin/env", "-i",
        "HOME=/root", "USER=root", "LOGNAME=root", "SHELL=/bin/bash",
        "PATH=$PATH", "TERM=xterm-256color", "COLORTERM=truecolor", "LANG=C.UTF-8",
        "TMPDIR=/tmp", "HOSTNAME=${Rootfs.HOSTNAME}",
        // No interactive debconf dialogs (there is no dialog/readline frontend in the minimal image).
        "DEBIAN_FRONTEND=noninteractive", "DEBCONF_NONINTERACTIVE_SEEN=true",
    ) + extraEnv + listOf("/bin/bash", "--login")

    fun dnsLines(ctx: Context): String {
        val out = LinkedHashSet<String>()
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val lp = cm.getLinkProperties(cm.activeNetwork)
            lp?.dnsServers?.forEach { a -> a.hostAddress?.substringBefore('%')?.let { out += it } }
        } catch (_: Exception) {}
        out += "1.1.1.1"; out += "8.8.8.8"
        return out.joinToString("\n", postfix = "\n") { "nameserver $it" }
    }
}

/** Writes the small set of files that make a plain Debian image work nicely inside PRoot. */
object Configure {
    fun write(f: File, text: String, mode: Int = 420 /* 0644 */) {
        f.parentFile?.mkdirs()
        if (f.exists() || java.nio.file.Files.isSymbolicLink(f.toPath())) { try { Os.chmod(f.absolutePath, 420) } catch (_: Exception) {}; f.delete() }
        f.writeText(text)
        Os.chmod(f.absolutePath, mode)
    }

    fun fakeProc(ctx: Context) {
        val d = Rootfs.fakeProc(ctx); d.mkdirs()
        write(File(d, "stat"), "cpu  1957 0 2877 93280 262 342 254 87 0 0\ncpu0 1957 0 2877 93280 262 342 254 87 0 0\n" +
            "intr 0\nctxt 140223\nbtime 1700000000\nprocesses 4\nprocs_running 1\nprocs_blocked 0\n")
        write(File(d, "loadavg"), "0.12 0.07 0.02 2/165 765\n")
        write(File(d, "uptime"), "124.08 932.80\n")
        write(File(d, "version"), "Linux version 6.1.0 (dizien@localhost) (gcc) #1 SMP PREEMPT\n")
        write(File(d, "vmstat"), "nr_free_pages 0\n")
    }

    fun guest(ctx: Context) {
        val root = Rootfs.root(ctx)
        fakeProc(ctx)
        write(File(root, "etc/hostname"), Rootfs.HOSTNAME + "\n")
        write(File(root, "etc/hosts"), "127.0.0.1 localhost\n127.0.1.1 ${Rootfs.HOSTNAME}\n::1 localhost ip6-localhost ip6-loopback\n")
        write(File(root, "etc/resolv.conf"), Proot.dnsLines(ctx))
        // Inside PRoot there is no real root user to drop privileges to, so apt must not try.
        write(File(root, "etc/apt/apt.conf.d/99dizien-terminal"),
            "APT::Sandbox::User \"root\";\nDpkg::Use-Pty \"true\";\nAPT::Color \"1\";\n")
        ensureLayout(ctx)
        File(root, "tmp").let { it.mkdirs(); Os.chmod(it.absolutePath, 511 or 512 /* 1777 */) }
        write(File(root, "root/.bashrc"), BASHRC, 420)
        write(File(root, "root/.profile"), "[ -n \"\$BASH_VERSION\" ] && [ -f \"\$HOME/.bashrc\" ] && . \"\$HOME/.bashrc\"\n")
    }

    /** Directories Debian expects under /usr/local; also repaired for systems installed by 0.1.0. */
    fun ensureLayout(ctx: Context) {
        try {
            val root = Rootfs.root(ctx)
            File(root, "sdcard").mkdirs()
            File(root, "run/dizien").mkdirs()
            write(File(root, "usr/local/bin/debian-setup-storage"), SETUP_STORAGE, 493 /* 0755 */)
            for (d in listOf("bin", "sbin", "lib", "etc", "include", "share", "src", "games", "man", "share/man"))
                File(root, "usr/local/$d").mkdirs()
            File(root, "var/local").mkdirs()
        } catch (_: Exception) {}
    }
    /** Called before every new session so DNS follows the current network. */
    fun refreshDns(ctx: Context) {
        try { write(File(Rootfs.root(ctx), "etc/resolv.conf"), Proot.dnsLines(ctx)) } catch (_: Exception) {}
    }

    private val BASHRC = """
# DiZien Terminal - default shell setup for the private Debian system.
# You can edit this file freely.
case ${'$'}- in *i*) ;; *) return ;; esac
HISTCONTROL=ignoreboth
HISTSIZE=2000
shopt -s checkwinsize
export PS1='\[\e[38;5;149m\]\u@${Rootfs.HOSTNAME}\[\e[38;5;245m\]:\w\$ \[\e[0m\]'
alias ls='ls --color=auto'
alias ll='ls -alF'
alias grep='grep --color=auto'
if [ -z "${'$'}DIZIEN_WELCOMED" ] && [ -t 1 ]; then
  export DIZIEN_WELCOMED=1
  . /etc/os-release 2>/dev/null
  printf '\e[38;5;178mDiZien Terminal\e[0m  %s - running rootless with PRoot\n' "${'$'}PRETTY_NAME"
  printf '\e[38;5;245mIndependent app, not affiliated with Debian. Try: apt update && apt install nano\e[0m\n'
fi
""".trimStart()
}

/** Shell command installed inside Debian: asks the app to allow access to the phone's files. */
private val SETUP_STORAGE = """#!/bin/bash
# debian-setup-storage - installed by DiZien Terminal (independent app, not affiliated with Debian).
D=/run/dizien
have_files() { [ -n "${'$'}(ls -A /sdcard 2>/dev/null | head -n 1)" ]; }
echo "debian-setup-storage"
echo "What this does: asks the DiZien Terminal app for permission to use your phone's"
echo "files, then shows them here as /sdcard (for example /sdcard/Download)."
echo "Nothing is changed outside this app. You can switch it off again in Settings."
echo
if have_files; then
  echo "OK: /sdcard is already available. It contains ${'$'}(ls -A /sdcard | wc -l) items."
  exit 0
fi
if [ ! -d ${'$'}D ]; then
  echo "FAILED: the connection to the app is missing. Close DiZien Terminal completely and open it again."
  exit 1
fi
rm -f ${'$'}D/response ${'$'}D/request.tmp
echo storage > ${'$'}D/request.tmp && mv ${'$'}D/request.tmp ${'$'}D/request
echo "Asking the app ... if Android shows a screen, please allow access for DiZien Terminal"
echo "(on Android 11 or newer: turn on \"Allow access to manage all files\", then press Back)."
i=0; ack=0; last=""
while [ ${'$'}i -lt 240 ]; do
  r=${'$'}(cat ${'$'}D/response 2>/dev/null)
  if [ -n "${'$'}r" ]; then
    ack=1
    case "${'$'}r" in
      granted*|denied*|failed*) break ;;
      *) [ "${'$'}r" != "${'$'}last" ] && { echo "... ${'$'}{r#*: }"; last="${'$'}r"; } ;;
    esac
  fi
  if [ ${'$'}ack = 0 ] && [ ${'$'}i -ge 10 ]; then
    echo "FAILED: the app did not answer. Open the DiZien Terminal app screen once and try again."
    rm -f ${'$'}D/request
    exit 1
  fi
  sleep 1; i=${'$'}((i+1))
done
r=${'$'}(cat ${'$'}D/response 2>/dev/null)
case "${'$'}r" in
  granted*)
    if have_files; then
      echo "OK: success! Your phone files are in /sdcard (${'$'}(ls -A /sdcard | wc -l) items). Try: ls /sdcard"
    else
      # This tab was started before the permission existed: ask the app for a fresh tab that has /sdcard.
      rm -f ${'$'}D/response; echo newtab > ${'$'}D/request.tmp && mv ${'$'}D/request.tmp ${'$'}D/request
      j=0; while [ ${'$'}j -lt 15 ] && ! grep -q '^newtab' ${'$'}D/response 2>/dev/null; do sleep 1; j=${'$'}((j+1)); done
      r=${'$'}(cat ${'$'}D/response 2>/dev/null)
      echo "OK: success! Android allowed it. A new tab (${'$'}{r#newtab }) was opened for you -"
      echo "your phone files are in /sdcard there. Try: ls /sdcard"
      echo "(This tab was started before the permission, so it cannot see /sdcard.)"
    fi
    exit 0 ;;
  denied*|failed*) echo "FAILED: ${'$'}{r#*: }" ;;
  *) echo "FAILED: no answer in time (access was not allowed)." ;;
esac
echo "Nothing was changed. You can try again, or use: menu > Settings > Phone storage in /sdcard"
exit 1
"""
