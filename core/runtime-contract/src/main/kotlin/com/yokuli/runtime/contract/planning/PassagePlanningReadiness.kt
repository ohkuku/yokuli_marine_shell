package com.yokuli.runtime.contract.planning

import com.yokuli.runtime.contract.chart.*

/** 目录允许提出请求不等于区域已可搜索；只有冻结资料中的实际覆盖和深度均得到确认才是 READY。 */
enum class PassageReadinessStatus { BLOCKED, CHECK_REQUIRED, READY }
enum class PassageReadinessReason {
    NO_DATA_SELECTED, DATA_MISSING, DATA_UNREADABLE, ANALYSIS_NOT_ALLOWED, NO_ACTIVE_CELLS,
    NO_STRUCTURED_COVERAGE, UNSUPPORTED_DATA, REGION_UNCHECKED, REGION_NOT_COVERED,
    DEPTH_NOT_SUPPORTED, READY,
}
/** 区域证据由规划运行时查询实际对象产生。深度支持指有基准的可信深度面，孤立测深点不代替深度面。 */
data class PassagePlanningEvidence(val coverageConfirmed:Boolean,val depthAreasConfirmed:Boolean,val semanticsComplete:Boolean=true)
data class PassagePlanningReadiness(val status:PassageReadinessStatus,val reason:PassageReadinessReason,val affectedDatasetIds:List<String> = emptyList()) {
    val canRequestPlanning:Boolean get()=status!=PassageReadinessStatus.BLOCKED
    val canSearch:Boolean get()=status==PassageReadinessStatus.READY
    val messageZh:String get()=when(reason) {
        PassageReadinessReason.NO_DATA_SELECTED->"先在图库选择航行数据；没有数据时可手动绘制航线"
        PassageReadinessReason.DATA_MISSING->"所选航行数据尚未安装或已移除"
        PassageReadinessReason.DATA_UNREADABLE->"所选航行数据目前无法读取，请在图库恢复"
        PassageReadinessReason.ANALYSIS_NOT_ALLOWED->"所选资料未获准用于航线分析，请在图库核对用途"
        PassageReadinessReason.NO_ACTIVE_CELLS->"所选资料没有有效的结构化海图单元"
        PassageReadinessReason.NO_STRUCTURED_COVERAGE->"所选资料没有有效覆盖范围，暂不能自动规划"
        PassageReadinessReason.UNSUPPORTED_DATA->"所选区域有未支持或不完整的数据，暂不能自动规划"
        PassageReadinessReason.REGION_UNCHECKED->"已选择航行数据；规划前将检查本区域覆盖与深度"
        PassageReadinessReason.REGION_NOT_COVERED->"规划起终点缺少选用资料的有效覆盖，可改用手动绘线"
        PassageReadinessReason.DEPTH_NOT_SUPPORTED->"规划起终点缺少可信深度面，可改用手动绘线"
        PassageReadinessReason.READY->"本区域有可用于搜索的覆盖与深度资料"
    }
    val messageEn:String get()=when(reason) {
        PassageReadinessReason.NO_DATA_SELECTED->"Choose navigation data in Library first. Without data, draw a route manually."
        PassageReadinessReason.DATA_MISSING->"Selected navigation data is not installed or has been removed."
        PassageReadinessReason.DATA_UNREADABLE->"Selected navigation data cannot be read. Restore it in Library."
        PassageReadinessReason.ANALYSIS_NOT_ALLOWED->"Selected data is not approved for route analysis. Review its use in Library."
        PassageReadinessReason.NO_ACTIVE_CELLS->"Selected data contains no active structured chart cells."
        PassageReadinessReason.NO_STRUCTURED_COVERAGE->"Selected data has no valid coverage. Automatic planning is unavailable."
        PassageReadinessReason.UNSUPPORTED_DATA->"The selected area has unsupported or incomplete data. Automatic planning is unavailable."
        PassageReadinessReason.REGION_UNCHECKED->"Navigation data selected. Area coverage and depth will be checked before planning."
        PassageReadinessReason.REGION_NOT_COVERED->"The planning endpoints lack coverage in selected data. You can draw a route manually."
        PassageReadinessReason.DEPTH_NOT_SUPPORTED->"The planning endpoints lack reliable depth areas. You can draw a route manually."
        PassageReadinessReason.READY->"Area coverage and depth evidence are available for route search."
    }
    val message:String get()="$messageZh / $messageEn"
}

/** UI 与运行时共用入口门槛；不从底图图片、在线地图或文件名猜测可规划性。 */
object PassagePlanningEligibility {
    fun evaluate(selectedDatasetIds:List<String>,datasets:List<ChartDataset>,nowUtcMillis:Long,
        missingDatasetIds:List<String> = emptyList(),evidence:PassagePlanningEvidence?=null):PassagePlanningReadiness {
        fun blocked(reason:PassageReadinessReason,ids:List<String> = emptyList())=PassagePlanningReadiness(PassageReadinessStatus.BLOCKED,reason,ids)
        val selected=selectedDatasetIds.distinct()
        if(selected.isEmpty())return blocked(PassageReadinessReason.NO_DATA_SELECTED)
        val missing=selected.filter{it in missingDatasetIds||datasets.none{data->data.id==it}}
        if(missing.isNotEmpty())return blocked(PassageReadinessReason.DATA_MISSING,missing)
        val sources=selected.map{id->datasets.first{it.id==id}}
        val unreadable=sources.filter{!it.offlineReadable||it.issue!=null}.map{it.id}
        if(unreadable.isNotEmpty())return blocked(PassageReadinessReason.DATA_UNREADABLE,unreadable)
        val forbidden=sources.filterNot{it.eligibility.allowsAnalysis(nowUtcMillis)}.map{it.id}
        if(forbidden.isNotEmpty())return blocked(PassageReadinessReason.ANALYSIS_NOT_ALLOWED,forbidden)
        // 更新版取消的单元不能借旧版复活。目录次序和实际搜索的覆盖优先级保持独立。
        val cells=sources.flatMap{data->data.cells.groupBy{it.cellId}.values.map{versions->versions.maxWith(compareBy<ChartCellRevision>{it.edition}.thenBy{it.update})}.filterNot{it.cancelled}}
        if(cells.isEmpty()||cells.none{it.featureCount>0})return blocked(PassageReadinessReason.NO_ACTIVE_CELLS,selected)
        val hasCoverage=cells.any{cell->cell.coverage.any{it.covered&&it.geometry.kind==ChartGeometryKind.POLYGON&&
            it.geometry.parts.any{part->!part.hole&&part.points.size>=3&&part.points.all{p->p.latitude.isFinite()&&p.longitude.isFinite()&&p.latitude in -90.0..90.0&&p.longitude in -180.0..180.0}}}}
        if(!hasCoverage)return blocked(PassageReadinessReason.NO_STRUCTURED_COVERAGE,selected)
        if(evidence==null)return PassagePlanningReadiness(PassageReadinessStatus.CHECK_REQUIRED,PassageReadinessReason.REGION_UNCHECKED)
        if(!evidence.semanticsComplete)return blocked(PassageReadinessReason.UNSUPPORTED_DATA,selected)
        if(!evidence.coverageConfirmed)return blocked(PassageReadinessReason.REGION_NOT_COVERED,selected)
        if(!evidence.depthAreasConfirmed)return blocked(PassageReadinessReason.DEPTH_NOT_SUPPORTED,selected)
        return PassagePlanningReadiness(PassageReadinessStatus.READY,PassageReadinessReason.READY)
    }
}
