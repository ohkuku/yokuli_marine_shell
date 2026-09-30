package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.*
import com.yokuli.marine.shell.rebuild.OsStore

/** 数据查询开关只控制准星信息，不能改变已选的海图图片。 */
@Composable internal fun ChartPortrayalSetting(os: OsStore) {
    val p = os.maps.portrayalPreferences
    Toggle(os.t("准星信息", "Cursor information"), p.showCursorInformation,
        os.t("在准星下查看水深、航标与附近设施。", "Read depth, navigation marks and nearby facilities below the cursor.")) {
        os.maps.updatePortrayal(p.copy(showCursorInformation = it, showDataOverlay = false))
    }
    ChartSourceSaveStatus(os)
}
