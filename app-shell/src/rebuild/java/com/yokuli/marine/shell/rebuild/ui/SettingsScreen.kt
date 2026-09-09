package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.BuildConfig
import com.yokuli.marine.shell.rebuild.*

@Composable fun SettingsScreen(os:OsStore) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(os,os.t("设置","settings"))
        Pivot(listOf(os.t("个性化","personal"),os.t("系统","system"))) { page ->
            PageBody {
                if(page==0) {
                    Label(os.t("主题色","accent colour"),25)
                    val colors=listOf(0xFF007F9B,0xFF0050EF,0xFF60A917,0xFFD80073,0xFF6A00FF,0xFFA20025,0xFFF0A30A)
                    for(row in colors.chunked(4)) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        for(color in row) Box(Modifier.weight(1f).aspectRatio(1f).background(Color(color)).then(if(os.accent==color) Modifier.border(3.dp,LocalMetro.current.fg) else Modifier).clickable {os.accent=color;os.save()})
                    }
                    Toggle(os.t("浅色背景","light background"),os.light) {os.light=it;os.save()}
                    Label(os.t("语言","language"),25)
                    MetroButton("简体中文",{os.chinese=true;os.save()},primary=os.chinese)
                    MetroButton("English",{os.chinese=false;os.save()},primary=!os.chinese)
                    Label(os.t("长按磁贴可拖动、改尺寸或取消固定。长按应用列表中的应用，可固定到开始屏幕。","Hold a tile to drag, resize or unpin it. Hold an app in the app list to pin it to Start."),17,LocalMetro.current.muted)
                } else {
                    Toggle(os.t("保持屏幕常亮","keep screen awake"),os.keepAwake) {os.keepAwake=it;os.save()}
                    Toggle(os.t("减少动画","reduce motion"),os.reduceMotion) {os.reduceMotion=it;os.save()}
                    MenuRow(os.t("船舶与值守设置","boat & watch settings"),os.t("船舶尺寸、报警、离线数据、备份与诊断","boat dimensions, alarms, offline data, backup & diagnostics"),"settings") {os.open("marine-settings")}
                    Label("Yokuli OS",38)
                    Label(BuildConfig.VERSION_NAME,16,LocalMetro.current.muted)
                    Label(os.t("海上生活，简单一点。","a little simpler, at sea."),22)
                    Label(os.t("手动体验版 · 基于 marine_shell 重建","Experience build · rebuilt from marine_shell"),15,LocalMetro.current.muted)
                    Label(os.t("Google 在线地图","Google online maps"),25)
                    Label(if(BuildConfig.GOOGLE_MAPS_CONFIGURED) os.t("此 APK 已注入 Key；实际显示取决于授权与网络。","A key is included. Rendering still depends on authorisation and network access.") else os.t("此 APK 未注入 Key。普通地图使用 OpenStreetMap；本地海图无需 Key。","No key in this APK. Standard map uses OpenStreetMap; local charts need no key."),16,LocalMetro.current.muted)
                    Label(os.t("地图：© OpenStreetMap contributors。海图版权归各数据提供者。Google / MapLibre 为地图显示服务。","Maps: © OpenStreetMap contributors. Charts belong to their providers. Google / MapLibre provide map rendering."),14,LocalMetro.current.muted)
                }
            }
        }
    }
}
