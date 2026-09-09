package com.yokuli.marine.shell.rebuild

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yokuli.marine.shell.rebuild.data.MarineService
import com.yokuli.marine.shell.rebuild.ui.*

class MainActivity : ComponentActivity() {
    private val os get()=(application as YokuliApplication).os
    private val gpsPermission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(result[Manifest.permission.ACCESS_FINE_LOCATION]==true || result[Manifest.permission.ACCESS_COARSE_LOCATION]==true) service("gpsOn")
        else os.notify("未获得定位权限，可在船舶数据中重试","Location permission denied. Retry in boat data.")
    }
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun service(action:String,extra:String?=null) {
        if(action=="gpsOn" && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            gpsPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)); return
        }
        if(Build.VERSION.SDK_INT>=33 && action in listOf("connect","gpsOn","shareOn") && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        runCatching { ContextCompat.startForegroundService(this,Intent(this,MarineService::class.java).setAction(action).putExtra("sentence",extra)) }
            .onFailure { os.notify("无法启动数据服务，请重试","Could not start the data service. Please retry.") }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        immersive()
        setContent {
            SideEffect { if(os.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            MetroTheme(os) { OsExperience(os,::service) }
        }
    }
    override fun onWindowFocusChanged(hasFocus:Boolean) { super.onWindowFocusChanged(hasFocus); if(hasFocus) immersive() }
    private fun immersive() { WindowInsetsControllerCompat(window,window.decorView).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } }
    override fun onPause() { os.save(); super.onPause() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun OsExperience(os:OsStore,service:(String,String?)->Unit) {
    BackHandler { os.back() }
    val c=LocalMetro.current
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(os.page,transitionSpec={
                val duration=if(os.reduceMotion) 0 else 200
                (slideInHorizontally(tween(duration)) { it/5 }+fadeIn(tween(duration))) togetherWith
                    (slideOutHorizontally(tween(duration)) { -it/9 }+fadeOut(tween(duration)))
            },label="page") { page ->
                when {
                    page=="start" -> Desktop(os)
                    page=="chart" -> ChartScreen(os)
                    page=="library" -> LibraryScreen(os)
                    page=="places" -> PlacesScreen(os)
                    page.startsWith("place:") -> PlaceScreen(os,page.substringAfter(':'))
                    page.startsWith("route:") -> RouteScreen(os,page.substringAfter(':'))
                    page=="data" -> DataScreen(os,service)
                    page=="nmea" -> NmeaScreen(os,service)
                    page=="settings" -> SettingsScreen(os)
                    page=="search" -> SearchScreen(os)
                    page=="sessions" -> SessionsScreen(os)
                    else -> Desktop(os)
                }
            }
            os.toast?.let { message ->
                Box(Modifier.align(Alignment.BottomCenter).padding(14.dp).fillMaxWidth().background(c.accent).clickable { os.toast=null }.padding(16.dp)) { Label(message,17,Color.White) }
            }
        }
        if(os.storageError) Label(os.t("存储失败，改动尚未保存","Storage error. Changes have not been saved."),13,Color(0xFFE67D47),Modifier.padding(8.dp))
        Row(Modifier.fillMaxWidth().height(56.dp).background(Color.Black),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            for((icon,action) in listOf("back" to { os.back() },"start" to { os.home() },"search" to { os.open("search") })) {
                Box(Modifier.weight(1f).fillMaxHeight().combinedClickable(onClick=action,onLongClick=if(icon=="back") ({os.open("sessions")}) else null),contentAlignment=Alignment.Center) {
                    Glyph(icon,Modifier.size(25.dp),Color.White)
                }
            }
        }
    }
}
