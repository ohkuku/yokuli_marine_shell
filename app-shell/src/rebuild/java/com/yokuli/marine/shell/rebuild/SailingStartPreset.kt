package com.yokuli.marine.shell.rebuild

import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.AdaptiveTilePacker
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TileDocumentEntry

/** 帆船默认桌面只组合已有内容。传感器、记录、守锚及导航仍需用户自己启用。 */
internal object SailingStartPreset {
    const val VERSION=3
    private data class Item(val key:String,val owner:AppId,val binding:TileBinding,val size:MarineTileSize,
        val presentation:TilePresentation,val target:String)
    private val panels=listOf("navigationReadings","windConditions","depthClearance","vesselAttitude")
    private fun app(key:String,id:AppId,size:MarineTileSize,cover:Boolean=false):Item {
        val app=ShellApp(id)
        return Item(key,id,TileBinding("yokuli",TileBindingKind.APP,app.entry.value),size,
            TilePresentation(style=if(cover)"summary"else"static",legacyMode=if(cover)"MAP"else null,rotate=false),app.rootToken.value)
    }
    private fun task(key:String,id:AppId,content:String)=Item(key,id,TileBinding("yokuli",TileBindingKind.CURRENT_TASK,content),
        MarineTileSize.STANDARD_2X2,TilePresentation(style="detail"),"task:$content")
    private val items=listOf(
        app("chart",AppId.CHART,MarineTileSize.WIDE_4X2,cover=true),
        Item("sailing",AppId.INSTRUMENTS,TileCompositePolicy.canonicalBinding(panels),MarineTileSize.LARGE_4X4,
            TilePresentation(style="detail",compositePanels=panels,tapTarget="instruments:tab:navigation"),"instruments:tab:navigation"),
        Item("pressure",AppId.INSTRUMENTS,TileBinding("yokuli",TileBindingKind.READING,"PRESSURE"),MarineTileSize.WIDE_4X2,
            TilePresentation(style="trend",historyMinutes=15),"instruments:metric:PRESSURE"),
        task("recording",AppId.VOYAGES,"recording"),
        task("anchor",AppId.ANCHOR,"anchorWatch"),
        app("library",AppId.LIBRARY,MarineTileSize.STANDARD_2X2),
        app("sailing-plans",AppId.PLACES,MarineTileSize.STANDARD_2X2),
        app("traffic",AppId.AIS,MarineTileSize.ICON_1X1),
        app("sources",AppId.DATA_CENTER,MarineTileSize.ICON_1X1),
        app("downloads",AppId.CHART_STORE,MarineTileSize.ICON_1X1),
        app("settings",AppId.SETTINGS,MarineTileSize.ICON_1X1),
    )
    /** 固定身份同时供初始目录和文档使用，首帧无需等待动态目录修复。 */
    val catalogue:List<LauncherEntryDescriptor> get()=items.filter {it.binding.kind!=TileBindingKind.APP}.map {item ->
        LauncherEntryDescriptor(item.binding.startEntryId,ShellApp(item.owner).id,LaunchToken(item.target),item.size,
            listOf(MarineTileSize.STANDARD_2X2,MarineTileSize.WIDE_4X2,MarineTileSize.LARGE_4X4),PinPolicy.PINNABLE)
    }
    fun document(columns:Int=4):StartDocument {
        require(columns==4||columns==6)
        val profile=WpReferenceProfiles.withColumns(WpReferenceProfiles.PHONE_PORTRAIT_4COL.id,columns)
        val document=StartDocument(2,profile.id,VERSION,items.mapIndexed {index,item ->
            TileDocumentEntry(TileInstanceId("sailing-start-${item.key}"),item.binding.startEntryId,item.size,index*1024L,
                binding=item.binding,presentation=item.presentation)
        })
        val packed=AdaptiveTilePacker.pack(document,columns)
        val cells=packed.tiles.associate {it.entry.tileId to it.cell}
        return document.copy(placements=document.placements.map {it.copy(preferredCell=cells.getValue(it.tileId))})
    }
}
