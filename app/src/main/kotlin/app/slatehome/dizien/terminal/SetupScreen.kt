package app.slatehome.dizien.terminal

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AppHeader(subtitle: String, badge: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).border(BorderStroke(2.dp, AS.Acc), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Txt(">_", 18f, AS.Acc, bold = true) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt("DIZIEN TERMINAL", 19f, bold = true, letter = 1.5f)
            Txt(subtitle, 12f, AS.Sub)
        }
        if (badge != null) Pill(badge, filled = false)
    }
    Divider()
}

@Composable
fun SetupScreen(prefs: Prefs, onOpenAbout: () -> Unit) {
    val ctx = LocalContext.current
    val src = remember { Rootfs.source() }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnly) }
    val phase = Installer.phase
    Column(Modifier.fillMaxSize().background(AS.Bg)) {
        AppHeader("Debian ${Rootfs.RELEASE_NUMBER} · ${src?.debArch ?: "unsupported CPU"} · rootless", src?.debArch?.uppercase())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel("FIRST-RUN SETUP")
            if (src == null) {
                Card {
                    Txt("This phone is not supported", 17f, bold = true)
                    Spacer(Modifier.height(6.dp))
                    Txt("DiZien Terminal runs Debian for 64-bit ARM (arm64-v8a) phones only.", 12.5f, AS.Sub, lineHeight = 17f)
                }
            } else when (phase) {
                Phase.IDLE -> IdleCard(src) { Installer.start(ctx) }
                else -> RunningCard(src)
            }
            if (src != null && (phase == Phase.RUNNING || phase == Phase.PAUSED || phase == Phase.WAITING_WIFI || phase == Phase.FAILED)) {
                Spacer(Modifier.height(16.dp))
                StepsCard()
            }
            SectionLabel("OPTIONS")
            Card(Modifier) {
                Row2("Release", "Stable · ${Rootfs.RELEASE_NAME} (${Rootfs.RELEASE_NUMBER})", "≡", onClick = {
                    Toast.makeText(ctx, "This version installs Debian stable (trixie) only.", Toast.LENGTH_SHORT).show()
                }) { Txt(">", 16f, AS.Sub) }
                Divider()
                Row2("Wi-Fi only", "Pause the download on mobile data", "≈") {
                    SwitchBox(wifiOnly) { wifiOnly = it; prefs.wifiOnly = it }
                }
            }
            Spacer(Modifier.height(18.dp))
            Txt(
                "Independent project, not affiliated with or endorsed by Debian. Debian is a registered trademark of Software in the Public Interest, Inc.",
                11f, AS.Sub, lineHeight = 15f,
            )
            Spacer(Modifier.height(6.dp))
            Txt("About & licenses", 12f, AS.Acc, bold = true, modifier = Modifier.clickableNoIndication(onOpenAbout).padding(vertical = 8.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IdleCard(src: RootfsSource, onStart: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tile(56) { Txt("↓", 26f, AS.Acc, bold = true) }
            Spacer(Modifier.width(14.dp))
            Column {
                Txt("Install Debian", 18f, bold = true)
                Txt("Debian ${Rootfs.RELEASE_NUMBER} (${Rootfs.RELEASE_NAME}) · ${src.debArch}", 12f, AS.Sub)
            }
        }
        Spacer(Modifier.height(14.dp))
        Txt(
            "DiZien Terminal downloads the official Debian image (about ${fmtMb(src.bytes)} MB), checks its SHA-256 fingerprint and unpacks it " +
                "into the app's private storage (about 100 MB). No root is needed and nothing outside this app is changed.",
            12.5f, AS.Sub, lineHeight = 17f,
        )
        Spacer(Modifier.height(16.dp))
        Btn("Install Debian", primary = true, modifier = Modifier.fillMaxWidth(), onClick = onStart)
    }
}

@Composable
private fun RunningCard(src: RootfsSource) {
    val ctx = LocalContext.current
    val phase = Installer.phase
    val step = Installer.step
    val failed = phase == Phase.FAILED
    val title = when {
        failed -> "Setup stopped"
        phase == Phase.PAUSED -> "Paused"
        step <= 1 -> "Downloading Debian"
        step == 2 -> "Verifying download"
        step == 3 -> "Unpacking Debian"
        else -> "Starting Debian"
    }
    val frac = Installer.stepProgress
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tile(56) { Txt(if (failed) "!" else "↓", 26f, if (failed) AS.Err else AS.Acc, bold = true) }
            Spacer(Modifier.width(14.dp))
            Column {
                Txt(title, 18f, bold = true)
                Txt("debian-${Rootfs.RELEASE_NAME}-${src.debArch}-rootfs.tar.gz", 11f, AS.Sub)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (failed) {
            Txt(Installer.error ?: "Unknown error", 13f, AS.Text, lineHeight = 18f)
            Installer.detail?.let {
                Spacer(Modifier.height(8.dp))
                Txt(it, 10.5f, AS.Sub, lineHeight = 14f, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(AS.Term).padding(8.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Txt(if (frac >= 0f) "${(frac * 100).toInt()}%" else "…", 38f, AS.Acc, bold = true)
                Spacer(Modifier.weight(1f))
                if (step <= 1 && Installer.bytesTotal > 0) {
                    val sp = if (Installer.speedBps > 0) " · ${fmtMb(Installer.speedBps)} MB/s" else ""
                    Txt("${fmtMb(Installer.bytesDone)} / ${fmtMb(Installer.bytesTotal)} MB$sp", 12.5f, AS.Sub)
                }
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(if (frac < 0f) 0f else frac)
            Spacer(Modifier.height(8.dp))
            val eta = if (step <= 1 && Installer.speedBps > 0) {
                val s = ((Installer.bytesTotal - Installer.bytesDone) / Installer.speedBps).toInt()
                if (s >= 90) "About ${(s + 30) / 60} minutes left" else "About $s seconds left"
            } else Installer.message
            Txt(eta, 12.5f, AS.Sub)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                phase == Phase.RUNNING || phase == Phase.WAITING_WIFI ->
                    if (step <= 1) Btn("Pause", false, Modifier.weight(1f)) { Installer.pause() }
                phase == Phase.PAUSED -> Btn("Resume", false, Modifier.weight(1f)) { Installer.resume(ctx) }
                failed -> Btn("Try again", true, Modifier.weight(1f)) { Installer.resume(ctx) }
            }
            Btn("Cancel & clean up", !failed && phase != Phase.PAUSED, Modifier.weight(if (step <= 1 || failed || phase == Phase.PAUSED) 1.4f else 1f)) { Installer.cancelAndClean(ctx) }
        }
    }
}

@Composable
private fun StepsCard() {
    val phase = Installer.phase
    Card {
        STEP_TITLES.forEachIndexed { i, t ->
            val done = i < Installer.step || phase == Phase.DONE
            val running = i == Installer.step && phase != Phase.DONE
            val failed = running && phase == Phase.FAILED
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(7.dp))
                        .background(if (done) AS.Acc else AS.Bg)
                        .border(BorderStroke(2.dp, if (done) AS.Acc else if (failed) AS.Err else if (running) AS.Acc else AS.Line), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        done -> Txt("✓", 15f, AS.AccFg, bold = true)
                        failed -> Txt("!", 15f, AS.Err, bold = true)
                        else -> Txt("${i + 1}", 14f, if (running) AS.Acc else AS.Sub, bold = true)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(t, 14f, if (done || running) AS.Text else AS.Sub, bold = running || done)
                    if (running && STEP_SUBTITLES[i].isNotEmpty()) Txt(STEP_SUBTITLES[i], 11.5f, AS.Sub)
                }
                if (running && !failed) Pill(if (phase == Phase.PAUSED) "Paused" else if (phase == Phase.WAITING_WIFI) "Waiting" else "Running")
            }
        }
    }
}
