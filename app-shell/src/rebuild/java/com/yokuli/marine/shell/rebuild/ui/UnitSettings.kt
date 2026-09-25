package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.shell.contract.*

/** 距离、速度、比例尺独立选择；没有隐藏的“靠近后换成米”规则。 */
@Composable internal fun ColumnScope.UnitSettings(os:OsStore) {
    var choosing by rememberSaveable {mutableStateOf<String?>(null)}
    val c=LocalMetro.current
    val units=os.unitPreferences
    fun distanceName(unit:DistanceUnit):String = when(unit) {
        DistanceUnit.NAUTICAL_MILES->os.t("海里","nautical miles")
        DistanceUnit.KILOMETERS->os.t("公里","kilometres")
        DistanceUnit.MILES->os.t("英里","miles")
        DistanceUnit.METERS->os.t("米","metres")
        DistanceUnit.FEET->os.t("英尺","feet")
        DistanceUnit.YARDS->os.t("码","yards")
    }+" · ${unit.symbol}"
    fun speedName(unit:SpeedUnit):String = when(unit) {
        SpeedUnit.KNOTS->os.t("节","knots")
        SpeedUnit.KILOMETERS_PER_HOUR->os.t("公里/小时","kilometres per hour")
        SpeedUnit.MILES_PER_HOUR->os.t("英里/小时","miles per hour")
        SpeedUnit.METERS_PER_SECOND->os.t("米/秒","metres per second")
    }+" · ${unit.symbol}"
    AppSection(os.t("距离与速度","distance & speed"))
    MenuRow(os.t("距离 · 所有尺度","distance · every scale"),distanceName(units.distance)) {choosing="distance"}
    MenuRow(os.t("速度","speed"),speedName(units.speed)) {choosing="speed"}
    MenuRow(os.t("地图比例尺","map scale"),units.scaleDistance?.let(::distanceName)
        ?: os.t("跟随距离单位 · ","follow distance · ")+units.distance.symbol) {choosing="scale"}
    Label(os.t("距离与比例尺不会随缩放自动换单位。1、2、5 的刻度也适用于小数尺度。",
        "Distance and map scale keep your chosen unit at every zoom. Scale marks use 1, 2 and 5 at fractional sizes too."),14,c.muted)
    AppSection(os.t("当前显示效果","your display"))
    listOf(1.0,100.0,10000.0).forEach {meters->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Label(os.formatDistance(meters),15)
            Label(os.t("比例尺 ","scale ")+(os.unitFormats.scaleBar(meters)?.label ?: "—"),14,c.muted)
        }
    }
    Label(os.t("航速 ","speed ")+os.formatSpeed(6.0),14,c.muted)
    AppSection(os.t("尺寸与环境","dimensions & environment"))
    MenuRow(os.t("船体、锚链与警戒半径","boat, rode & watch radius"),os.lengthUnitLabel) {choosing="length"}
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
    Label(os.t("海图、守锚、日志、驾驶台、磁贴和通知共用同一设置，输入框同步换算。原始记录与 NMEA 报文保持规范单位。",
        "Charts, Anchor Watch, logs, instruments, tiles and notifications share these settings. Inputs convert too; stored records and NMEA retain their standard units."),14,c.muted)
    choosing?.let {kind->
        val title=when(kind){"distance"->os.t("全尺度距离","distance at every scale");"speed"->os.t("速度单位","speed unit");"scale"->os.t("地图比例尺","map scale");"length"->os.t("船体、锚链与半径","boat, rode & radius");"depth"->os.t("水深与吃水","depth & draft");"temperature"->os.t("温度","temperature");else->os.t("气压","pressure")}
        val selected=when(kind){"distance"->units.distance.name;"speed"->units.speed.name;"scale"->units.scaleDistance?.name?:"FOLLOW_DISTANCE";"length"->units.length.name;"depth"->units.depth.name;"temperature"->units.temperature.name;else->units.pressure.name}
        val options=when(kind){
            "distance"->DistanceUnit.entries.map {it.name to distanceName(it)}
            "speed"->SpeedUnit.entries.map {it.name to speedName(it)}
            "scale"->listOf("FOLLOW_DISTANCE" to os.t("跟随距离单位 · ","follow distance · ")+units.distance.symbol)+DistanceUnit.entries.map {it.name to distanceName(it)}
            "length"->listOf("METERS" to os.t("米 · m","metres · m"),"FEET" to os.t("英尺 · ft","feet · ft"))
            "depth"->listOf("METERS" to os.t("米 · m","metres · m"),"FEET" to os.t("英尺 · ft","feet · ft"),"FATHOMS" to os.t("英寻 · fathom","fathoms"))
            "temperature"->listOf("CELSIUS" to os.t("摄氏度 · °C","Celsius · °C"),"FAHRENHEIT" to os.t("华氏度 · °F","Fahrenheit · °F"))
            else->listOf("HECTOPASCALS" to os.t("百帕 · hPa","hectopascals · hPa"),"KILOPASCALS" to os.t("千帕 · kPa","kilopascals · kPa"),"INCHES_OF_MERCURY" to os.t("英寸汞柱 · inHg","inches of mercury · inHg"))
        }
        AppDialog(onDismissRequest={choosing=null}) {
            AppBackHandler {choosing=null}
            AppDialogSurface {
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
