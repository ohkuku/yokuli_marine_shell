package com.yokuli.runtime.marine.ipc

import com.yokuli.runtime.contract.notification.NoticeTarget
import java.lang.reflect.Method

/** Only explicit commands become failed-action notices. Lifecycle upkeep has its own read models. */
internal data class CoreCommandNotice(
    val key: String,
    val publisher: String,
    val actionZh: String,
    val actionEn: String,
    val destinationZh: String,
    val destinationEn: String,
    val target: NoticeTarget,
)

internal fun commandNotice(call: CoreCall, method: Method): CoreCommandNotice? {
    val name = method.name
    if (MarineCorePorts.cancellableRead(call.port, method) || call.port in setOf("display", "feedback", "presentation", "readingHistory")) return null
    if (when (call.port) {
        "residency" -> name == "startFromForeground"
        "sources" -> name in setOf("onPermissionsChanged", "clearVesselCalibrationFeedback")
        "charts" -> name in setOf("releaseSnapshot", "prepareTerrain", "prepareTerrainRegion")
        "voyage", "anchorCommands" -> name == "recheck"
        "voyages" -> name == "recheckCommand"
        "anchor" -> name == "loadHistoryEvents"
        "preferences" -> name == "clearBackupResult"
        else -> false
    }) return null
    fun argument(index: Int) = call.arguments.get(index)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    val action = when (name) {
        "switchGpsDataSource" -> when (argument(0)) {
            "SYSTEM" -> "采用手机 GPS" to "Use Phone GPS"
            "NMEA" -> "采用船载 GPS" to "Use boat GPS"
            else -> "关闭船位来源" to "Turn off position input"
        }
        "selectNmeaPositionConnection" -> "采用船载 GPS 连接" to "Select boat GPS connection"
        "setVesselMetricSource" -> "更改仪表数据来源" to "Change instrument source"
        "confirmFixedPhoneMount", "confirmTripAttitudeFrame" -> "确认手机安装方向" to "Confirm phone mounting"
        "setPhoneHeadingAlignment", "alignPhoneHeadingToBow", "alignPhoneHeadingToNmea" -> "校准手机船首向" to "Calibrate phone heading"
        "setPhoneAttitudeAlignment" -> "校准手机倾角" to "Calibrate phone attitude"
        "invalidateFixedPhoneMount" -> "重置手机安装方向" to "Reset phone mounting"
        "startNmeaConnection", "saveAndConnect" -> "连接船载设备" to "Connect boat instrument"
        "stopNmeaConnection", "disconnect" -> "断开船载设备" to "Disconnect boat instrument"
        "removeNmeaConnection" -> "移除船载连接" to "Remove boat connection"
        "saveNmeaConnection" -> "保存船载连接" to "Save boat connection"
        "requestArm", "arm" -> "开始守锚" to "Start anchor watch"
        "requestPauseWatch", "pauseWatch" -> "暂停守锚" to "Pause anchor watch"
        "requestResumeWatch", "resumeWatch" -> "继续守锚" to "Resume anchor watch"
        "requestLiftAnchor", "liftAnchor" -> "结束守锚" to "End anchor watch"
        "acknowledge" -> "确认守锚警报" to "Acknowledge anchor alarm"
        "start", "startTrip" -> "开始航行记录" to "Start voyage recording"
        "pause", "pauseTrip" -> "暂停航行记录" to "Pause voyage recording"
        "resume", "resumeTrip" -> "继续航行记录" to "Resume voyage recording"
        "finish", "endTrip" -> "结束航行记录" to "Finish voyage recording"
        "captureMoment", "markTripWaypoint" -> "记录航行时刻" to "Capture voyage moment"
        "request", "requestCommand" -> when (call.arguments.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.get("action")?.asString) {
            "START" -> "开始航行记录" to "Start voyage recording"
            "PAUSE" -> "暂停航行记录" to "Pause voyage recording"
            "RESUME" -> "继续航行记录" to "Resume voyage recording"
            "FINISH" -> "结束航行记录" to "Finish voyage recording"
            else -> "更改航行记录" to "Update voyage recording"
        }
        else -> when (call.port) {
            "anchor", "anchorCommands" -> "更改守锚任务" to "Update anchor watch"
            "voyage", "voyages" -> "更改航行记录" to "Update voyage recording"
            "network", "sharing" -> "更改船联网设置" to "Update boat network"
            "sources" -> "更改数据来源" to "Update data source"
            "charts" -> "更改海图册资料" to "Update chart catalog"
            "navigation" -> "读取导航记录" to "Read navigation record"
            "preferences" -> "更改船舶设置" to "Update vessel settings"
            else -> "更改系统设置" to "Update system settings"
        }
    }
    val metric = if (name == "setVesselMetricSource") argument(0) else "POSITION"
    val scope = when (name) {
        "switchGpsDataSource" -> "POSITION:${argument(0)}"
        "selectNmeaPositionConnection" -> "POSITION:${argument(0).take(80)}"
        "setVesselMetricSource" -> "$metric:${argument(1).take(80)}"
        "startNmeaConnection", "stopNmeaConnection", "removeNmeaConnection" -> argument(0).take(80)
        else -> ""
    }
    val destination = when (call.port) {
        "anchor", "anchorCommands" -> Triple("ANCHOR", "守锚" to "Anchor Watch", NoticeTarget("anchor"))
        "voyage", "voyages" -> Triple("VOYAGES", "航行日志" to "Logbook", NoticeTarget("voyages"))
        "network", "sharing" -> Triple("NMEA", "船联网" to "Boat Network", NoticeTarget("nmea"))
        "sources" -> Triple("DATA_CENTER", "数据中心" to "Data Center", NoticeTarget("data_center", section = if (scope.isNotEmpty()) "source/$metric" else "mount"))
        "charts" -> Triple("CHART", "海图册" to "Chart Catalog", NoticeTarget("chart", section = "library"))
        "navigation" -> Triple("CHART", "导航" to "Navigation", NoticeTarget("chart"))
        else -> Triple("SETTINGS", "系统设置" to "Settings", NoticeTarget("settings", section = "permissions"))
    }
    return CoreCommandNotice("${call.port}:$name:$scope", destination.first, action.first, action.second,
        destination.second.first, destination.second.second, destination.third)
}
