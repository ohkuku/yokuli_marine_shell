package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.contract.*

/** 系统单位各有明确使用范围，显示、刻度与编辑共享同一个偏好值。 */
@Composable internal fun ColumnScope.UnitSettings(os:OsStore) {
    var choosing by rememberSaveable {mutableStateOf<String?>(null)}
    val c=LocalMetro.current
    AppSection(os.t("航程与速度","distance & speed"))
    MeasurementUnitSystem.entries.forEach {units->
        ChoiceRow(if(units==MeasurementUnitSystem.NAUTICAL)os.t("海里 · 节","nautical miles · knots")else os.t("公里 · 公里/小时","kilometres · km/h"),os.measurementUnits==units) {
            os.shell.updateSystemPreferences {it.copy(measurementUnitSystemName=units.name)}
        }
    }
    Label(os.formatDistance(1852.0)+" · "+os.formatSpeed(6.0),16,c.muted)
    MenuRow(os.t("短距离与船体尺寸","short distances & boat dimensions"),
        os.lengthUnitLabel+" · "+os.t("半径、锚链、船长和近距比例尺","radius, rode, length and close-up scale")) {choosing="length"}
    MenuRow(os.t("水深与吃水","depth & draft"),os.depthUnitLabel+" · "+os.formatDepth(5.0)) {choosing="depth"}
    MenuRow(os.t("温度","temperature"),os.temperatureUnitLabel+" · "+os.formatTemperature(20.0)) {choosing="temperature"}
    MenuRow(os.t("气压","pressure"),os.pressureUnitLabel+" · "+os.formatPressure(1013.25)) {choosing="pressure"}
    AppSection(os.t("坐标格式","coordinate format"))
    listOf("DMM" to os.t("度与分","degrees & minutes"),"DD" to os.t("十进制度","decimal degrees"),"DMS" to os.t("度、分与秒","degrees, minutes & seconds")).forEach {(format,label)->
        ChoiceRow(label,os.coordinateFormat==format) {
            os.shell.updateSystemPreferences {it.copy(appPreferenceValues=it.appPreferenceValues+("preferences.coordinate.format" to "c:$format"))}
        }
    }
    Label(os.formatCoordinates(GeoPoint(-36.84123,174.76543)),15)
    Label(os.t("海图、锚警、日志、仪表、数据预览和磁贴共用这些单位；输入框也同步换算。原始记录及 NMEA 报文保留规范单位。",
        "Charts, Anchor Watch, logs, instruments, data previews and tiles share these units, including input fields. Stored records and NMEA sentences keep their standard units."),15,c.muted)
    choosing?.let {kind->
        val title=when(kind){"length"->os.t("短距离与尺寸","short distances & dimensions");"depth"->os.t("水深与吃水","depth & draft");"temperature"->os.t("温度","temperature");else->os.t("气压","pressure")}
        val selected=when(kind){"length"->os.lengthUnit.name;"depth"->os.depthUnit.name;"temperature"->os.temperatureUnit.name;else->os.pressureUnit.name}
        val options=when(kind){
            "length"->listOf("METERS" to os.t("米 · m","metres · m"),"FEET" to os.t("英尺 · ft","feet · ft"))
            "depth"->listOf("METERS" to os.t("米 · m","metres · m"),"FEET" to os.t("英尺 · ft","feet · ft"),"FATHOMS" to os.t("英寻 · fathom","fathoms"))
            "temperature"->listOf("CELSIUS" to os.t("摄氏度 · °C","Celsius · °C"),"FAHRENHEIT" to os.t("华氏度 · °F","Fahrenheit · °F"))
            else->listOf("HECTOPASCALS" to os.t("百帕 · hPa","hectopascals · hPa"),"KILOPASCALS" to os.t("千帕 · kPa","kilopascals · kPa"),"INCHES_OF_MERCURY" to os.t("英寸汞柱 · inHg","inches of mercury · inHg"))
        }
        AppDialog(onDismissRequest={choosing=null}) {
            AppBackHandler {choosing=null}
            AppDialogSurface() {
                AppDialogTitle(title)
                options.forEach {(value,label)->ChoiceRow(label,selected==value) {
                    os.shell.updateSystemPreferences {it.copy(appPreferenceValues=it.appPreferenceValues+("preferences.units.$kind" to "c:$value"))}
                    choosing=null
                }}
                MetroButton(os.t("关闭","close"),{choosing=null})
            }
        }
    }
}
