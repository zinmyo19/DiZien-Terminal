package app.slatehome.dizien.terminal

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.util.Properties

/** Sticky CTRL / ALT keys of the extra-keys row (one-shot: they clear after the next key). */
object KeyState {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
    var fontSize by mutableStateOf(14)
    fun consume() { ctrl = false; alt = false }
}

object TermTheme {
    private var applied = false
    fun apply() {
        if (applied) return
        applied = true
        val p = Properties()
        p["foreground"] = "#F3E9D7"; p["background"] = "#12100D"; p["cursor"] = "#FFB020"
        val pal = listOf(
            "#1C1814", "#E06C5B", "#A9C46B", "#FFB020", "#6F9BD1", "#C08AD6", "#5FB7A6", "#D9CDB8",
            "#6B5F50", "#F08A78", "#C9D96B", "#FFC84D", "#8DB4E6", "#D6A6E8", "#82D0C0", "#F3E9D7",
        )
        pal.forEachIndexed { i, c -> p["color$i"] = c }
        TerminalColors.COLOR_SCHEME.updateWith(p)
    }
}

class TermClient(private val prefs: Prefs) : TerminalViewClient {
    override fun onScale(scale: Float): Float {
        if (scale < 0.92f) { KeyState.fontSize = (KeyState.fontSize - 1).coerceAtLeast(8); prefs.fontSizeSp = KeyState.fontSize; return 1f }
        if (scale > 1.08f) { KeyState.fontSize = (KeyState.fontSize + 1).coerceAtMost(28); prefs.fontSizeSp = KeyState.fontSize; return 1f }
        return scale
    }
    override fun onSingleTapUp(e: MotionEvent?) {
        val v = Sessions.view ?: return
        v.requestFocus()
        (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(v, 0)
    }
    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
        if (session != null && !session.isRunning) {
            if (keyCode == KeyEvent.KEYCODE_ENTER) Sessions.close(session)
            return true
        }
        return false
    }
    override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
    override fun onLongPress(event: MotionEvent?) = false
    override fun readControlKey() = KeyState.ctrl
    override fun readAltKey() = KeyState.alt
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
        if (session != null && !session.isRunning) { if (codePoint == 13 || codePoint == 10) Sessions.close(session); return true }
        KeyState.consume()
        return false
    }
    override fun onEmulatorSet() {}
    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}

@Composable
fun TerminalScreen(prefs: Prefs, extraKeys: Boolean, onSettings: () -> Unit, onNew: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AS.Term)) {
        TabBar(onSettings, onNew)
        Box(Modifier.weight(1f).fillMaxWidth()) { TermViewHost(prefs) }
        if (extraKeys) ExtraKeys()
    }
}

@Composable
private fun TermViewHost(prefs: Prefs) {
    val client = remember { TermClient(prefs) }
    val cur = Sessions.current
    val size = KeyState.fontSize
    AndroidView(
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        factory = { ctx ->
            TermTheme.apply()
            TerminalView(ctx, null).apply {
                setTerminalViewClient(client)
                isFocusable = true; isFocusableInTouchMode = true
                setBackgroundColor(0xFF12100D.toInt())
                // setTextSize() creates the renderer; setTypeface() needs it, so keep this order.
                setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.toFloat(), resources.displayMetrics).toInt())
                setTypeface(ResourcesCompat.getFont(ctx, R.font.dejavu_sans_mono) ?: Typeface.MONOSPACE)
                tag = size
                Sessions.view = this
            }
        },
        update = { v ->
            Sessions.view = v
            v.keepScreenOn = prefs.keepAwake
            if (v.tag != size) {
                v.tag = size
                v.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.toFloat(), v.resources.displayMetrics).toInt())
            }
            if (cur != null && v.currentSession !== cur) v.attachSession(cur)
            v.onScreenUpdated()
            v.requestFocus()
        },
    )
}

@Composable
private fun TabBar(onSettings: () -> Unit, onNew: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val t = Sessions.tick
    var menu by remember { mutableStateOf(false) }
    val src = remember { Rootfs.source() }
    Row(Modifier.fillMaxWidth().height(48.dp).background(AS.Bg), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Sessions.list.forEach { s ->
                val sel = s === Sessions.current
                Box(
                    Modifier.height(48.dp).background(if (sel) AS.Surf else AS.Bg).clickable { Sessions.current = s }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt((s.mSessionName ?: "Session").uppercase(), 13f, if (sel) AS.Acc else AS.Sub, bold = sel, letter = 1f)
                    if (sel) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(AS.Acc))
                }
            }
            Box(Modifier.height(48.dp).clickable(onClick = onNew).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Txt("+", 20f, AS.Sub, bold = true)
            }
        }
        Box(Modifier.height(48.dp).clickable { menu = true }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Txt("PROOT · ${src?.debArch?.uppercase() ?: ""}  ⋮", 11f, AS.Sub, letter = 0.5f)
            if (menu) MenuPopup({ menu = false }, onSettings, onNew)
        }
    }
}

private data class KeyDef(val label: String, val action: (TerminalView) -> Unit, val sticky: Int = 0)

@Composable
private fun ExtraKeys() {
    val rows = remember {
        listOf(
            listOf(
                KeyDef("ESC", { sendKey(it, KeyEvent.KEYCODE_ESCAPE) }), KeyDef("/", { sendChar(it, '/') }),
                KeyDef("-", { sendChar(it, '-') }), KeyDef("HOME", { sendKey(it, KeyEvent.KEYCODE_MOVE_HOME) }),
                KeyDef("↑", { sendKey(it, KeyEvent.KEYCODE_DPAD_UP) }), KeyDef("END", { sendKey(it, KeyEvent.KEYCODE_MOVE_END) }),
                KeyDef("PGUP", { sendKey(it, KeyEvent.KEYCODE_PAGE_UP) }),
            ),
            listOf(
                KeyDef("TAB", { sendKey(it, KeyEvent.KEYCODE_TAB) }), KeyDef("CTRL", { KeyState.ctrl = !KeyState.ctrl }, 1),
                KeyDef("ALT", { KeyState.alt = !KeyState.alt }, 2), KeyDef("←", { sendKey(it, KeyEvent.KEYCODE_DPAD_LEFT) }),
                KeyDef("↓", { sendKey(it, KeyEvent.KEYCODE_DPAD_DOWN) }), KeyDef("→", { sendKey(it, KeyEvent.KEYCODE_DPAD_RIGHT) }),
                KeyDef("PGDN", { sendKey(it, KeyEvent.KEYCODE_PAGE_DOWN) }),
            ),
        )
    }
    Column(Modifier.fillMaxWidth().background(AS.Bg).padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k ->
                    val on = (k.sticky == 1 && KeyState.ctrl) || (k.sticky == 2 && KeyState.alt)
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (on) AS.Acc else AS.Surf)
                            .border(BorderStroke(1.dp, if (on) AS.Acc else AS.Line), RoundedCornerShape(6.dp))
                            .clickable { Sessions.view?.let { v -> k.action(v); v.requestFocus() } },
                        contentAlignment = Alignment.Center,
                    ) { Txt(k.label, 12.5f, if (on) AS.AccFg else AS.Text, bold = true) }
                }
            }
        }
    }
}

private fun mods(): Int {
    var m = 0
    if (KeyState.ctrl) m = m or 0x40000000 // KeyHandler.KEYMOD_CTRL
    if (KeyState.alt) m = m or (0x80000000).toInt() // KeyHandler.KEYMOD_ALT
    return m
}

private fun sendKey(v: TerminalView, code: Int) { v.handleKeyCode(code, mods()); KeyState.consume() }
private fun sendChar(v: TerminalView, c: Char) { v.inputCodePoint(c.code, false, false); KeyState.consume() }

@Composable
private fun MenuPopup(dismiss: () -> Unit, onSettings: () -> Unit, onNew: () -> Unit) {
    val ctx = LocalContext.current
    fun item(label: String, run: () -> Unit) = @Composable {
        Box(Modifier.fillMaxWidth().clickable { dismiss(); run() }.padding(horizontal = 16.dp, vertical = 12.dp)) { Txt(label, 13.5f) }
    }
    Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, 120), onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        Column(Modifier.width(220.dp).clip(RoundedCornerShape(8.dp)).background(AS.Surf2).border(BorderStroke(1.dp, AS.Line), RoundedCornerShape(8.dp))) {
            item("New session") { onNew() }()
            item("Paste") { Sessions.paste() }()
            item("Clear screen") { Sessions.current?.reset() }()
            item("Close this session") { Sessions.current?.let { Sessions.close(it) } }()
            item("Text size +") { KeyState.fontSize = (KeyState.fontSize + 1).coerceAtMost(28); Prefs(ctx).fontSizeSp = KeyState.fontSize }()
            item("Text size –") { KeyState.fontSize = (KeyState.fontSize - 1).coerceAtLeast(8); Prefs(ctx).fontSizeSp = KeyState.fontSize }()
            item("Settings & About") { onSettings() }()
            item("Exit (close all sessions)") { Sessions.closeAll() }()
        }
    }
}
