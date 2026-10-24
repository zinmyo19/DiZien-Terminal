package app.slatehome.dizien.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(AS.Bg)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Txt("‹", 28f, AS.Acc, bold = true) }
            Txt(title, 18f, bold = true)
        }
        Divider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            content()
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun SettingsScreen(
    prefs: Prefs, onBack: () -> Unit, onAbout: () -> Unit, onExtraKeys: (Boolean) -> Unit,
    onSharedStorage: (Boolean, (Boolean) -> Unit) -> Unit, onReset: () -> Unit,
) {
    val ctx = LocalContext.current
    var extra by remember { mutableStateOf(prefs.extraKeys) }
    var awake by remember { mutableStateOf(prefs.keepAwake) }
    var compat by remember { mutableStateOf(prefs.compatMode) }
    var shared by remember { mutableStateOf(prefs.sharedStorage) }
    var used by remember { mutableStateOf<Long?>(null) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { used = withContext(Dispatchers.IO) { Rootfs.dirSize(Rootfs.root(ctx)) } }
    Page("Settings", onBack) {
        SectionLabel("TERMINAL")
        Card {
            Row2("Text size", "${KeyState.fontSize} sp · pinch the screen to zoom", "A") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepBtn("–") { KeyState.fontSize = (KeyState.fontSize - 1).coerceAtLeast(8); prefs.fontSizeSp = KeyState.fontSize }
                    Spacer(Modifier.width(8.dp))
                    StepBtn("+") { KeyState.fontSize = (KeyState.fontSize + 1).coerceAtMost(28); prefs.fontSizeSp = KeyState.fontSize }
                }
            }
            Divider()
            Row2("Extra keys row", "ESC, TAB, CTRL, ALT and arrows above the keyboard", "⌨") {
                SwitchBox(extra) { extra = it; prefs.extraKeys = it; onExtraKeys(it) }
            }
            Divider()
            Row2("Keep screen on", "While a terminal is open", "☀") {
                SwitchBox(awake) { awake = it; prefs.keepAwake = it }
            }
        }
        SectionLabel("DEBIAN")
        Card {
            Row2("Compatibility mode", "Runs PRoot without the seccomp speed-up. Slower, but works on every phone. Applies to new sessions.", "⚙") {
                SwitchBox(compat) { compat = it; prefs.compatMode = it }
            }
            Divider()
            Row2("Phone storage in /sdcard", "Lets Debian read and write your shared phone storage as /sdcard. Android asks for storage access (\"All files access\" on Android 11+). Inside Debian you can also type debian-setup-storage.", "▤") {
                SwitchBox(shared) { want -> onSharedStorage(want) { ok -> shared = ok } }
            }
            Divider()
            Row2("Installed system", "Debian ${Rootfs.RELEASE_NUMBER} (${Rootfs.RELEASE_NAME}) · ${used?.let { "${it / 1048576} MB used" } ?: "calculating…"}", "≡")
            Divider()
            Row2("Remove Debian & start over", "Deletes the Debian system and everything you saved inside it.", "!", onClick = { confirm = true }) {
                Txt(">", 16f, AS.Sub)
            }
        }
        SectionLabel("WORKS WITH")
        Card {
            Row2("DiZien Launcher", "Its Terminal shortcut opens this app automatically. Nothing to set up.", "▦")
        }
        SectionLabel("ABOUT")
        Card { Row2("About & licenses", "Version ${BuildConfig.VERSION_NAME} · not affiliated with Debian", "i", onClick = onAbout) { Txt(">", 16f, AS.Sub) } }
    }
    if (confirm) Dialog(onDismissRequest = { confirm = false }) {
        Column(Modifier.clip(RoundedCornerShape(12.dp)).background(AS.Surf).border(BorderStroke(1.dp, AS.Line), RoundedCornerShape(12.dp)).padding(18.dp)) {
            Txt("Remove Debian?", 18f, bold = true)
            Spacer(Modifier.height(8.dp))
            Txt("All files inside Debian (including anything you installed) will be deleted. You can install Debian again afterwards.", 12.5f, AS.Sub, lineHeight = 17f)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Keep it", false, Modifier.weight(1f)) { confirm = false }
                Btn("Remove", true, Modifier.weight(1f)) { confirm = false; onReset() }
            }
        }
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(AS.Surf2).border(BorderStroke(1.dp, AS.Line), RoundedCornerShape(6.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Txt(label, 18f, AS.Acc, bold = true)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
    Page("About", onBack) {
        Spacer(Modifier.height(16.dp))
        Card {
            Txt("DiZien Terminal", 20f, bold = true)
            Txt("Version ${BuildConfig.VERSION_NAME} · ${BuildConfig.APPLICATION_ID}", 11.5f, AS.Sub)
            Spacer(Modifier.height(10.dp))
            Txt(
                "A rootless Debian shell for your phone. It runs a private copy of Debian inside this app using PRoot, so you need neither root access nor another terminal app.",
                12.5f, AS.Text, lineHeight = 17f,
            )
        }
        SectionLabel("NOT AFFILIATED WITH DEBIAN")
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color.White).padding(8.dp), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.debian_open_use_logo), "Debian Open Use Logo", Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Txt(
                    "DiZien Terminal is an independent project. It is not affiliated with, endorsed by or sponsored by Debian or Software in the Public Interest, Inc. " +
                        "Debian is a registered trademark of Software in the Public Interest, Inc.",
                    11.5f, AS.Text, lineHeight = 16f, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Txt(
                "The logo is the Debian Open Use Logo, © 1999 Software in the Public Interest, Inc., used only to mark the operating system that runs inside the app " +
                    "(not as this app's name or icon). Licensed under LGPL-3.0-or-later or CC BY-SA 3.0. Source: debian.org/logos",
                10.5f, AS.Sub, lineHeight = 14.5f,
            )
        }
        SectionLabel("OPEN SOURCE")
        Card { Row2("Licenses & source code", "PRoot, talloc, Termux terminal, fonts, Debian", "§", onClick = onLicenses) { Txt(">", 16f, AS.Sub) } }
    }
}

private class Lic(val name: String, val what: String, val license: String, val asset: String?)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<Lic?>(null) }
    var body by remember { mutableStateOf("") }
    val items = remember {
        listOf(
            Lic("PRoot", "Runs Debian without root (bundled program, unmodified build)", "GPL-2.0-or-later", "PRoot-COPYING.txt"),
            Lic("talloc", "Memory library linked into PRoot", "LGPL-3.0-or-later", "talloc-LGPL-3.txt"),
            Lic("Termux terminal-emulator & terminal-view", "Terminal screen and key handling (v0.118.0)", "Apache-2.0", "Apache-2.0.txt"),
            Lic("DejaVu Sans Mono", "Terminal font", "Bitstream Vera / DejaVu licence", "DejaVu-Fonts-License.txt"),
            Lic("Debian Open Use Logo", "Shown on the About page only", "LGPL-3.0-or-later or CC BY-SA 3.0", "NOTICE-debian-logo.txt"),
            Lic("AndroidX & Jetpack Compose", "App framework", "Apache-2.0", "Apache-2.0.txt"),
            Lic("GNU GPL v3 text", "Reference copy", "GPL-3.0", "GPL-3.0.txt"),
            Lic("Where the bundled programs come from", "Build and source notes for PRoot/talloc", "Notes", "PROVENANCE.md"),
        )
    }
    LaunchedEffect(open) {
        val o = open
        body = if (o?.asset == null) "" else withContext(Dispatchers.IO) {
            try { ctx.assets.open("licenses/${o.asset}").bufferedReader().readText() } catch (e: Exception) { "Could not read the licence text." }
        }
    }
    val cur = open
    Page(cur?.name ?: "Licenses & source", { if (open != null) open = null else onBack() }) {
        if (cur == null) {
            Spacer(Modifier.height(12.dp))
            Txt(
                "The Debian system downloaded into the app is made of thousands of packages, each with its own licence. Inside Debian, look in /usr/share/doc/<package>/copyright. " +
                    "The exact source of the bundled PRoot and talloc programs is included in the app's source repository (folder corresponding-source/).",
                11.5f, AS.Sub, lineHeight = 16f,
            )
            Spacer(Modifier.height(10.dp))
            Card {
                items.forEachIndexed { i, l ->
                    if (i > 0) Divider()
                    Row2(l.name, "${l.what}\n${l.license}", onClick = { open = l }) { Txt(">", 16f, AS.Sub) }
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Txt(body, 10.5f, AS.Text, lineHeight = 14f)
        }
    }
}
