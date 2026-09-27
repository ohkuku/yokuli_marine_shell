package com.yokuli.marine.shell.rebuild

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.yokuli.runtime.contract.RuntimeReadiness

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.hardware.display.DisplayManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yokuli.runtime.contract.PositionSourceRequest
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.R
import com.yokuli.marine.shell.rebuild.ui.MetroTheme
import com.yokuli.shell.android.AndroidShellKeyAdapter
import com.yokuli.shell.contract.ShellInput
import com.yokuli.shell.engine.LauncherAction
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var brandArrivalVisible by mutableStateOf(false)
    private var automaticResidencySuspended = false
    /** 显式退出保存未完成时，权限返回/旋转不能重新打开已关闭的采集。 */
    fun holdAutomaticResidencyForExit(hold: Boolean) { automaticResidencySuspended = hold }
    private var longBackConsumed = false
    private var foregroundFrames = false
    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = requestSmoothFrames()
        override fun onDisplayRemoved(displayId: Int) = requestSmoothFrames()
        override fun onDisplayChanged(displayId: Int) {
            if (window.decorView.display?.displayId == displayId) requestSmoothFrames()
        }
    }
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
        if (action == PositionSourceRequest.ENABLE_PHONE &&
            os.marine?.system?.hardwareLab?.state?.value?.mode.let { it == null || it == com.yokuli.runtime.contract.hardware.HardwareMode.REAL }) {
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
        // 起始窗口使用 Manifest 的品牌主题；实际 Activity 首帧立即切回正常 UI。
        // Android 12+ 的系统 splash 由系统自行移除，不等待标志动画、不拦截首帧。
        setTheme(R.style.Theme_YokuliOS)
        super.onCreate(savedInstanceState)
        brandArrivalVisible = BrandArrivalSession.claim(intent, savedInstanceState != null)
        automaticResidencySuspended = savedInstanceState?.getBoolean("yokuli.explicit_exit_pending") == true
        os.connectSystem((application as YokuliApplication).marineSystem)
        os.systemAction=serviceHandler
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val system = (application as YokuliApplication).marineSystem
                system.connection.collectLatest { connection ->
                    if (connection.readiness != RuntimeReadiness.READY) return@collectLatest
                    system.residency.state.first { it.recoveryReady || it.recoveryProblem != null }
                    if (!system.residency.state.value.recoveryReady) return@collectLatest
                    val returningHomeAfterExit = BuildConfig.ROM_HOME && intent?.hasCategory(Intent.CATEGORY_HOME) == true && system.residency.state.value.explicitlyStopped
                    if (!isFinishing && !isDestroyed && !returningHomeAfterExit && !automaticResidencySuspended)
                        system.residency.startFromForeground()
                    system.services.sources.onPermissionsChanged()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val feedback = (application as YokuliApplication).marineSystem.services.feedback
                feedback.presentationRequests.collect { requests ->
                    for (request in requests) {
                        try {
                            feedback.acknowledgePresentation(request.id)
                            if (System.currentTimeMillis() - request.createdAtUtc in 0..900_000L)
                                presentMarineRequest(request)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { os.notify("无法打开分享或系统设置，请重试", "Could not open sharing or system settings. Please retry.", app=AppId.SETTINGS) }
                    }
                }
            }
        }
        // 配置变化会重用最初的 HOME intent；旋转只恢复当前任务，不再次执行 Home。
        if(savedInstanceState == null) handleSystemIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window,false)
        immersive()
        setContent {
            val keepAwake = os.keepAwake
            LaunchedEffect(keepAwake) {
                if(keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            MetroTheme(os) {
                OsExperience(os)
                if (brandArrivalVisible) BrandArrivalHost(os) { brandArrivalVisible = false }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        brandArrivalVisible = false
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
        intent.getStringExtra("yokuli.notice.id")?.takeIf { it.length in 1..256 }?.let { id ->
            // Android通知仅携带持久消息身份；目的地取自自己的消息服务，不信任外部route字符串。
            intent.removeExtra("yokuli.notice.id")
            os.scope.launch {
                kotlinx.coroutines.withTimeoutOrNull(8000) { os.notifications.awaitLoaded() }
                os.openNotification(id)
            }
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
    override fun onWindowFocusChanged(hasFocus:Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus) { immersive(); requestSmoothFrames() }
    }
    override fun onResume() {
        super.onResume()
        os.scope.launch { kotlinx.coroutines.withTimeoutOrNull(10_000) { os.notifications.onAppForeground() } }
        foregroundFrames = true
        displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        requestSmoothFrames()
    }
    /**
     * 中文：前台窗口请求当前分辨率支持的最高刷新率，至少表达 60Hz 的渲染意图。
     * 不锁显示模式、不改分辨率；Compose/Choreographer 仍用系统 vsync，不用 delay(16) 造帧。
     * 省电、温控及系统调度仍可能限制实际帧率，不能把此请求当成性能测量结果。
     */
    private fun requestSmoothFrames() {
        if (!foregroundFrames) return
        val display = window.decorView.display ?: return
        val mode = display.mode
        val preferred = display.supportedModes.asSequence()
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .map { it.refreshRate }.filter { it.isFinite() && it > 0f }.maxOrNull()?.coerceAtLeast(60f) ?: 60f
        val attributes = window.attributes
        if (attributes.preferredRefreshRate != preferred) {
            attributes.preferredRefreshRate = preferred
            window.attributes = attributes
        }
    }
    private fun immersive() { WindowInsetsControllerCompat(window,window.decorView).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val input = AndroidShellKeyAdapter.mapKeyCode(event.keyCode) ?: return super.dispatchKeyEvent(event)
        if (brandArrivalVisible) brandArrivalVisible = false
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
    override fun onPause() {
        brandArrivalVisible = false
        foregroundFrames = false
        displayManager?.unregisterDisplayListener(displayListener)
        val attributes = window.attributes
        if (attributes.preferredRefreshRate != 0f) {
            attributes.preferredRefreshRate = 0f
            window.attributes = attributes
        }
        os.save()
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("yokuli.explicit_exit_pending", automaticResidencySuspended)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { if(os.systemAction === serviceHandler) os.systemAction=null;super.onDestroy() }
}
