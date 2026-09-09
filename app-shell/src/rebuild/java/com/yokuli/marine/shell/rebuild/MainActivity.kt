package com.yokuli.marine.shell.rebuild

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yokuli.anchorwatch.MainViewModel
import com.yokuli.marine.shell.rebuild.ui.MetroTheme
import com.yokuli.shell.android.AndroidShellKeyAdapter
import com.yokuli.shell.contract.ShellInput
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val marineVm: MainViewModel by viewModels()
    private var longBackConsumed = false
    private val os get()=(application as YokuliApplication).os
    private val gpsPermission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(result[Manifest.permission.ACCESS_FINE_LOCATION]==true) service("gpsOn")
        else os.notify("手机 GPS 需要精确位置权限，可在船舶数据中重试","Phone GPS needs precise location permission. Retry in boat data.")
    }
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun service(action:String,extra:String?=null) {
        if(action=="gpsOn" && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            gpsPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)); return
        }
        if(Build.VERSION.SDK_INT>=33 && action in listOf("connect","gpsOn","shareOn") && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        runCatching { os.marine?.action(action, extra) }
            .onFailure { os.notify("无法启动数据服务，请重试","Could not start the data service. Please retry.") }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        os.attachMarine(marineVm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window,false)
        immersive()
        setContent {
            SideEffect { if(os.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            MetroTheme(os) { OsExperience(os,::service) }
        }
    }
    override fun onWindowFocusChanged(hasFocus:Boolean) { super.onWindowFocusChanged(hasFocus); if(hasFocus) immersive() }
    override fun onResume() { super.onResume(); marineVm.onPermissionsChanged() }
    private fun immersive() { WindowInsetsControllerCompat(window,window.decorView).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val input = AndroidShellKeyAdapter.mapKeyCode(event.keyCode) ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (input == ShellInput.BACK && event.repeatCount > 0 && !longBackConsumed) {
                longBackConsumed = true
                os.shell.input(ShellInput.RECENTS)
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (input == ShellInput.BACK && longBackConsumed) longBackConsumed = false
            else os.shell.input(input)
        }
        return true
    }
    override fun onPause() { os.save(); super.onPause() }
}
