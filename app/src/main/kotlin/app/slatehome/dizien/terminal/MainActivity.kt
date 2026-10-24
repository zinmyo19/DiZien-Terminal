package app.slatehome.dizien.terminal

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var permCallback: ((Boolean) -> Unit)? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        permCallback?.invoke(r.values.isNotEmpty() && r.values.all { it }); permCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sessions.init(this)
        TermTheme.apply()
        val prefs = Prefs(this)
        KeyState.fontSize = prefs.fontSizeSp
        window.statusBarColor = 0xFF1C1814.toInt()
        window.navigationBarColor = 0xFF1C1814.toInt()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        Sessions.onEmpty = { if (!isFinishing) finishAndRemoveTask() }
        Sessions.onStorageRequest = { what -> runOnUiThread { if (what == "newtab") Sessions.respond("newtab " + Sessions.create(this).mSessionName) else handleDebianStorageRequest(prefs) } }
        setContent { App(prefs) }
    }

    override fun onDestroy() {
        if (isFinishing) { Sessions.view = null; Sessions.onStorageRequest = null }
        super.onDestroy()
    }

    private fun storageGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else Build.VERSION.SDK_INT < 23 || arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    /** Android 11+: the "All files access" screen result is checked when we come back. */
    private var allFilesCallback: ((Boolean) -> Unit)? = null
    override fun onResume() {
        super.onResume()
        allFilesCallback?.let { cb -> allFilesCallback = null; cb(storageGranted()) }
    }
    private fun requestStorage(want: Boolean, result: (Boolean) -> Unit, prefs: Prefs) {
        if (!want) { prefs.sharedStorage = false; result(false); return }
        if (storageGranted()) { prefs.sharedStorage = true; result(true); return }
        if (Build.VERSION.SDK_INT >= 30) {
            // Switch it on right away: Android may restart the app when "All files access" is granted.
            prefs.sharedStorage = true
            allFilesCallback = { ok -> prefs.sharedStorage = ok; result(ok) }
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                catch (_: Exception) { allFilesCallback = null; prefs.sharedStorage = false; result(false) }
            }
            return
        }
        val perms = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        permCallback = { ok -> prefs.sharedStorage = ok; result(ok) }
        permLauncher.launch(perms)
    }
    /** "debian-setup-storage" typed inside Debian asked for access to the phone's files. */
    private fun handleDebianStorageRequest(prefs: Prefs) {
        Sessions.respond("working: asking Android for permission")
        val done = { ok: Boolean ->
            if (ok) {
                Sessions.respond("granted")
            } else if (Build.VERSION.SDK_INT >= 30) {
                Sessions.respond("denied: \"All files access\" was not turned on for DiZien Terminal")
            } else {
                Sessions.respond("denied: permission was not allowed (if Android no longer asks, open Android Settings > Apps > DiZien Terminal > Permissions > Storage)")
            }
        }
        if (storageGranted()) { prefs.sharedStorage = true; done(true); return }
        requestStorage(true, done, prefs)
    }
    @Composable
    private fun App(prefs: Prefs) {
        var installed by remember { mutableStateOf(Rootfs.isInstalled(this)) }
        var screen by rememberSaveable { mutableStateOf("main") }
        var extra by remember { mutableStateOf(prefs.extraKeys) }

        LaunchedEffect(Installer.phase) { if (Installer.phase == Phase.DONE) installed = true }
        LaunchedEffect(installed) {
            if (installed && Sessions.list.isEmpty()) Sessions.create(this@MainActivity)
        }
        BackHandler {
            when (screen) {
                "licenses" -> screen = "about"
                "about" -> screen = if (installed) "settings" else "main"
                "settings" -> screen = "main"
                else -> moveTaskToBack(true)
            }
        }
        when {
            screen == "licenses" -> LicensesScreen { screen = "about" }
            screen == "about" -> AboutScreen({ screen = if (installed) "settings" else "main" }, { screen = "licenses" })
            !installed -> SetupScreen(prefs) { screen = "about" }
            screen == "settings" -> SettingsScreen(
                prefs, onBack = { screen = "main" }, onAbout = { screen = "about" }, onExtraKeys = { extra = it },
                onSharedStorage = { want, cb -> requestStorage(want, cb, prefs) },
                onReset = {
                    Sessions.exitOnEmpty = false
                    Installer.uninstall(this@MainActivity) { installed = false; screen = "main"; Sessions.exitOnEmpty = true }
                },
            )
            else -> TerminalScreen(prefs, extra, onSettings = { screen = "settings" }, onNew = { Sessions.create(this@MainActivity) })
        }
    }
}
