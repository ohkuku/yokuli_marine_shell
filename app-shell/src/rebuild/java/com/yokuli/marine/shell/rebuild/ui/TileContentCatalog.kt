package com.yokuli.marine.shell.rebuild.ui

import com.yokuli.marine.shell.rebuild.*
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.layout.TileDocumentEntry
import com.yokuli.anchorwatch.domain.vessel.MetricLabelRegistry

/** Stable content declarations, not another instrument subscription or task owner. */
enum class TileContentGroup { WATCH, READINGS, SAVED, APPS }
data class TileContentStyle(val key:String,val title:AppPreferenceLabel)
data class TileContentChoice(
    val binding:TileBinding,
    val title:AppPreferenceLabel,
    val subtitle:AppPreferenceLabel,
    val owner:AppId,
    val group:TileContentGroup,
    val sizes:List<MarineTileSize>,
    val styles:List<TileContentStyle>,
    val defaultSize:MarineTileSize,
    val launchToken:String,
    val destinationLabel:AppPreferenceLabel,
    val supported:Boolean=true,
) {
    val description get()=subtitle
    val ownerTitle get()=AppPreferenceLabel(owner.zh,owner.en)
    val defaultPresentation get()=TilePresentation(style=styles.firstOrNull()?.key ?: "simple")
}
private val contentSizes=listOf(MarineTileSize.STANDARD_2X2,MarineTileSize.WIDE_4X2)
private fun label(zh:String,en:String)=AppPreferenceLabel(zh,en)
private fun contentStyles(detailZh:String="详细",detailEn:String="Detail")=listOf(
    TileContentStyle("simple",label("简洁","Simple")),TileContentStyle("detail",label(detailZh,detailEn)))
fun tileReadingBinding(metric:String)=TileBinding("yokuli",TileBindingKind.READING,if(metric=="HEADING")"HEADING_TRUE" else metric)
fun tileBinding(placement:TileDocumentEntry):TileBinding=placement.binding
    ?: TileBinding("yokuli",TileBindingKind.APP,placement.entryId.value)

private fun staticTileContentChoices():List<TileContentChoice> {
    fun reading(id:String):TileContentChoice {
        val tile=tileInstrumentId(id) ?: error("Unsupported reading $id")
        val name=MetricLabelRegistry.get(tile)
        val historyTitle=when(instrumentHistoryKind(instrumentTrendKey(tile).orEmpty())) {
            InstrumentHistoryKind.BEARING->label("方位变化带","Bearing history")
            InstrumentHistoryKind.DIRECTION->label("方位分布","Direction distribution")
            InstrumentHistoryKind.DEPTH->label("水深剖面","Depth profile")
            InstrumentHistoryKind.DEVIATION->label("偏差范围","Deviation history")
            InstrumentHistoryKind.WEATHER->label("观测趋势","Observed trend")
            InstrumentHistoryKind.COUNT->label("次数分布","Count history")
            InstrumentHistoryKind.COUNTER->label("实际增量","Measured increments")
            else->label("变化范围","Range history")
        }
        // Capability policy and picker agree; every listed style has a production renderer.
        val styles=TileReadingPresentationPolicy.styles(id).map {style->TileContentStyle(style,when(style) {
            "simple"->if(id=="POSITION")label("坐标","Coordinates")else label("读数","Reading")
            "compass"->when {
                id in TileReadingPresentationPolicy.relativeDirectionIds||id=="APPARENT_WIND_SPEED"->label("船艏相对方位","Bow-relative dial")
                id=="TRUE_WIND_SPEED"->label("风速与真风向","Speed and true direction")
                else->label("真北方位盘","True-north compass")
            }
            "attitude"->label(if(id=="HEEL")"横倾示意"else"纵倾示意",if(id=="HEEL")"Heel view"else"Pitch view")
            "gauge"->label("刻度量表","Gauge")
            "trend"->historyTitle
            else->when {
                id=="POSITION"->label("坐标与精度","Position and accuracy")
                id in TileReadingPresentationPolicy.windIds->label("风速、方向与来源","Wind details")
                else->label("读数与历史","Reading and history")
            }
        })}
        return TileContentChoice(tileReadingBinding(id),label(name.simplifiedChinese,name.english),
            label("系统选用的${name.simplifiedChinese} · 保留来源与观测时间","Selected ${name.english.lowercase()} · Actual source and observation time"),
            AppId.INSTRUMENTS,TileContentGroup.READINGS,contentSizes,styles,MarineTileSize.STANDARD_2X2,
            "instruments:metric:${tile.name}",label("驾驶台 · ${name.simplifiedChinese}","Helm · ${name.english}"))
    }
    fun task(id:String,owner:AppId,zh:String,en:String,detailZh:String,detailEn:String)=TileContentChoice(
        TileBinding("yokuli",TileBindingKind.CURRENT_TASK,id),label(zh,en),label(detailZh,detailEn),owner,TileContentGroup.WATCH,
        contentSizes,contentStyles(),MarineTileSize.WIDE_4X2,"task:$id",label("${owner.zh} · 当前任务","${owner.en} · Current task"))
    return listOf(
        task("navigation",AppId.CHART,"当前导航","Current navigation","查看此刻的导航与目标，不自动开始","View the current navigation and target; never starts navigation"),
        task("anchorWatch",AppId.ANCHOR,"当前守锚","Current anchor watch","值守、警报和船位时效常驻","Watch state, alarms and position age stay visible"),
        task("recording",AppId.VOYAGES,"当前记录","Current recording","查看本次记录的进度和暂停状态","View the active recording and its paused state"),
        TileContentChoice(TileBinding("yokuli",TileBindingKind.OVERVIEW,"aisTraffic"),label("周围交通","Nearby traffic"),
            label("收到的 AIS 目标与交通警戒","Received AIS traffic and watch status"),AppId.AIS,TileContentGroup.WATCH,
            contentSizes,contentStyles(),MarineTileSize.WIDE_4X2,ShellApp(AppId.AIS).rootToken.value,label("AIS · 观察","AIS · Observe")),
    )+TileReadingPresentationPolicy.supportedIds.map(::reading)+AppId.entries.map {id->
        val app=ShellApp(id)
        val useful=id!=AppId.TILES
        TileContentChoice(TileBinding("yokuli",TileBindingKind.APP,app.entry.value),label(id.zh,id.en),
            label("打开${id.zh}首页","Open ${id.en}"),id,TileContentGroup.APPS,app.sizes,
            listOf(TileContentStyle("static",label("应用图标","App icon")))+
                if(useful)listOf(TileContentStyle("summary",label("应用摘要","App summary")))else emptyList(),
            app.defaultSize,app.rootToken.value,label("${id.zh} · 首页","${id.en} · Home"))
    }
}
private val declaredTileChoices by lazy(::staticTileContentChoices)
fun tileContentChoices(os:OsStore):List<TileContentChoice> = declaredTileChoices.filter {it.group!=TileContentGroup.APPS}+
    os.allPlaces.sortedWith(compareBy<Place> {it.name.lowercase()}.thenBy {it.id}).map {savedPlaceChoice(it)}+
    os.routes.sortedWith(compareBy<Route> {it.name.lowercase()}.thenBy {it.id}).map {savedRouteChoice(it)}+
    declaredTileChoices.filter {it.group==TileContentGroup.APPS}
private fun savedPlaceChoice(place:Place)=TileContentChoice(TileBinding("yokuli",TileBindingKind.SAVED_PLACE,place.id),
    label(place.name,place.name),label("收藏地点 · 查看位置和笔记","Saved place · Position and notes"),AppId.PLACES,TileContentGroup.SAVED,
    contentSizes,listOf(TileContentStyle("simple",label("地点摘要","Place summary"))),MarineTileSize.STANDARD_2X2,
    "tileplace:${place.id}",label("我的航行 · 地点详情","My Sailing · Place details"))
private fun savedRouteChoice(route:Route)=TileContentChoice(TileBinding("yokuli",TileBindingKind.SAVED_ROUTE,route.id),
    label(route.name,route.name),label("收藏航线 · 查看路线，不自动导航","Saved route · View without starting navigation"),AppId.PLACES,TileContentGroup.SAVED,
    contentSizes,listOf(TileContentStyle("simple",label("航线摘要","Route summary"))),MarineTileSize.WIDE_4X2,
    "tileroute:${route.id}",label("我的航行 · 航线详情","My Sailing · Route details"))

/** Missing objects retain their tile identity; navigation snapshots do not revive saved routes. */
fun tileContentDescriptor(os:OsStore,binding:TileBinding):TileContentChoice {
    declaredTileChoices.firstOrNull {it.binding.contentKey==binding.contentKey}?.let {return it}
    if(binding.providerId=="yokuli" && binding.kind==TileBindingKind.APP) {
        tilePresets().firstOrNull {it.entryId.value==binding.contentId}?.let {preset->
            val app=ShellApp(preset.app)
            return TileContentChoice(binding,preset.title,preset.description,preset.app,TileContentGroup.APPS,preset.sizes,
                listOf(TileContentStyle("summary",label("原有应用摘要","Existing app summary")),TileContentStyle("static",label("应用图标","App icon"))),
                preset.defaultSize,app.rootToken.value,label("${preset.app.zh} · 首页（旧磁贴）","${preset.app.en} · Home (legacy tile)"))
        }
    }
    if(binding.providerId=="yokuli")when(binding.kind) {
        TileBindingKind.SAVED_PLACE->os.allPlaces.firstOrNull {it.id==binding.contentId}?.let {return savedPlaceChoice(it)}
        TileBindingKind.SAVED_ROUTE->os.routes.firstOrNull {it.id==binding.contentId}?.let {return savedRouteChoice(it)}
        else->Unit
    }
    val saved=binding.kind in setOf(TileBindingKind.SAVED_PLACE,TileBindingKind.SAVED_ROUTE)
    return TileContentChoice(binding,label(if(saved)"收藏内容"else"旧内容",if(saved)"Saved content"else"Unavailable content"),
        label("保留这块磁贴，可在工坊更换内容或移除","This tile is preserved. Change its content or remove it in Tile Studio"),
        if(saved)AppId.PLACES else AppId.TILES,if(saved)TileContentGroup.SAVED else TileContentGroup.APPS,
        contentSizes,listOf(TileContentStyle("simple",label("摘要","Summary"))),MarineTileSize.STANDARD_2X2,
        when(binding.kind) {TileBindingKind.SAVED_PLACE->"tileplace:${binding.contentId}";TileBindingKind.SAVED_ROUTE->"tileroute:${binding.contentId}";else->ShellApp(AppId.TILES).rootToken.value},
        label(if(saved)"我的航行 · 内容状态"else"磁贴工坊 · 编辑内容",if(saved)"My Sailing · Content status"else"Tile Studio · Edit content"),supported=saved&&binding.providerId=="yokuli")
}
