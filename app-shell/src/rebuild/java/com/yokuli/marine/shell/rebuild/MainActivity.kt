package com.yokuli.marine.shell.rebuild

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yokuli.runtime.contract.PositionSourceRequest
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.rebuild.ui.MetroTheme
import com.yokuli.shell.android.AndroidShellKeyAdapter
import com.yokuli.shell.contract.ShellInput
import com.yokuli.shell.engine.LauncherAction
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var longBackConsumed = false
    private val os get()=(application as YokuliApplication).os
    private val serviceHandler: (PositionSourceRequest) -> Unit = ::requestPosition
    private val gpsPermission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(result[Manifest.permission.ACCESS_FINE_LOCATION]==true) requestPosition(PositionSourceRequest.ENABLE_PHONE)
        else os.notify("手机定位需要精确位置权限，可在“数据中心”重试","Phone location needs precise location permission. Retry in Data Center.",app=AppId.DATA_CENTER,destination="data_center:phone")
    }
    private val locationSettings=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(getSystemService(LocationManager::class.java).isLocationEnabled) requestPosition(PositionSourceRequest.ENABLE_PHONE)
        else os.notify("定位服务尚未开启，可在“数据中心”重新开启手机定位","Location services are still off. Enable phone location again in Data Center.",app=AppId.DATA_CENTER,destination="data_center:phone")
    }
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    /** 页面只提出明确的船位请求；Android 权限属于宿主，来源选择属于数据服务。 */
    private fun requestPosition(action: PositionSourceRequest) {
        if (action == PositionSourceRequest.ENABLE_PHONE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                gpsPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return
            }
            if (!getSystemService(LocationManager::class.java).isLocationEnabled) {
                locationSettings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                return
            }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val source = when (action) {
            PositionSourceRequest.ENABLE_PHONE -> GpsDataSource.SYSTEM
            PositionSourceRequest.DISABLE_POSITION -> GpsDataSource.NONE
            PositionSourceRequest.USE_NMEA -> GpsDataSource.NMEA
        }
        runCatching { (application as YokuliApplication).marineSystem.services.sources.switchGpsDataSource(source) }
            .onFailure { os.notify("无法切换船位来源，请重试", "Could not change position source. Please retry.", app = AppId.DATA_CENTER) }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        os.connectSystem((application as YokuliApplication).marineSystem)
        os.systemAction=serviceHandler
        // 配置变化会重用最初的 HOME intent；旋转只恢复当前任务，不再次执行 Home。
        if(savedInstanceState == null) handleSystemIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window,false)
        immersive()
        setContent {
            SideEffect { if(os.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            MetroTheme(os) { OsExperience(os) }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSystemIntent(intent)
    }
    /** 通知交接携带稳定 MMSI。仅接受本应用的目标路由，忽略任意外部页面字符串。 */
    private fun handleSystemIntent(intent:Intent?) {
        if(intent==null)return
        // ROM 系统 Home 有独立优先级；不会因残留通知参数进入业务页面。
        if(BuildConfig.ROM_HOME&&intent.action==Intent.ACTION_MAIN&&intent.hasCategory(Intent.CATEGORY_HOME)) {
            returnHomeFromRomIntent(intent)
            return
        }
        val target=runCatching {intent.getIntExtra("yokuli.ais.target",0)}.getOrDefault(0).takeIf {it in 1..999_999_999}
        val route=runCatching {intent.getStringExtra("yokuli.ais.route")}.getOrNull()
        val routedMmsi=route?.takeIf {it.length<=24&&it.startsWith("ais:target:")}
            ?.substringAfter("ais:target:")?.takeIf {it.length in 1..9&&it.all(Char::isDigit)}?.toIntOrNull()?.takeIf {it in 1..999_999_999}
        val mmsi=target?:routedMmsi
        if(mmsi!=null)os.openSystemDestination("ais:target:$mmsi")
        else if(route=="ais")os.openSystemDestination("ais")
    }
    /** Android 的 Home 是系统级入口：只回桌面，保留内部应用会话和正在运行的航行业务。 */
    private fun returnHomeFromRomIntent(intent: Intent?) {
        if (!BuildConfig.ROM_HOME || intent?.action != Intent.ACTION_MAIN || !intent.hasCategory(Intent.CATEGORY_HOME)) return
        os.notificationShade.close()
        // 不经过应用局部输入处理器，避免某个弹窗把系统 Home 吞掉；不启动或停止任何服务。
        os.shell.dispatch(LauncherAction.ShowDesktop)
    }
    override fun onWindowFocusChanged(hasFocus:Boolean) { super.onWindowFocusChanged(hasFocus); if(hasFocus) immersive() }
    override fun onResume() { super.onResume(); (application as YokuliApplication).marineSystem.services.sources.onPermissionsChanged() }
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
            else if (input == ShellInput.SEARCH) os.notificationShade.toggle()
            else os.shell.input(input)
        }
        return true
    }
    override fun onPause() { os.save(); super.onPause() }
    override fun onDestroy() { if(os.systemAction === serviceHandler) os.systemAction=null;super.onDestroy() }
}
