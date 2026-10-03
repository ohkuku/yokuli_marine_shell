package com.yokuli.chartpackage

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** 一份组合包的独立真实子包。资料类别保持独立，组合关系不授予新的安全资格。 */
data class AtlasPackageManifest(val manifest:ChartPackageManifest) {
    val id get()=manifest.id
    val name get()=manifest.name
    val metadata get()=manifest.metadata
    val charts get()=manifest.files.singleOrNull {it.format=="charts"}
    val geodata get()=manifest.files.singleOrNull {it.format=="geodata"}
}

/**
 * .yklpkg 将一份 .yklcharts、.yklgeodata 或两者原子绑定；不接受任意 ZIP 或应用包。
 * 共用图包严格 UTF-8/JSON、声明白名单、路径、大小、SHA-256 和同步写盘规则。
 */
object YokuliAtlasPackage {
    const val FORMAT="yokuli.atlas-package"
    const val VERSION=1

    fun readManifest(input:InputStream,check:()->Unit={}):AtlasPackageManifest =
        AtlasPackageManifest(YokuliChartPackage.readAtlasManifest(input,check))

    fun extract(input:InputStream,directory:File,check:()->Unit={}):AtlasPackageManifest {
        return extractWithProgress(input,directory,{},check)
    }

    fun extractWithProgress(input:InputStream,directory:File,progress:(ChartPackageReadProgress)->Unit,
        check:()->Unit={}):AtlasPackageManifest {
        val atlas=AtlasPackageManifest(YokuliChartPackage.extractAtlasWithProgress(input,directory,check,progress))
        // 外层摘要通过之后，再核对子包声明；其完整有效载荷由各自图册所有者正式导入时校验。
        listOfNotNull(atlas.charts?.let {it to "charts"},atlas.geodata?.let {it to "data"}).forEach {(entry,kind)->
            check()
            File(directory,entry.path).inputStream().use {YokuliChartPackage.readManifest(it,kind,check)}
        }
        return atlas
    }

    /** 写入前检查两份实际子包声明；共用引擎会二次核对源流未被替换。 */
    fun write(output:OutputStream,id:String,name:String,charts:ChartPackageSource?=null,geodata:ChartPackageSource?=null,
        metadata:Map<String,String> = emptyMap(),provider:String="",license:String="",attribution:String="",
        check:()->Unit={},progress:(Long,Long)->Unit={_,_->}):AtlasPackageManifest {
        require(charts!=null||geodata!=null) {"ATLAS_EMPTY"}
        require((charts==null||charts.format=="charts")&&(geodata==null||geodata.format=="geodata")) {"YKLCHART_KIND_MISMATCH"}
        charts?.open()?.use {YokuliChartPackage.readManifest(it,"charts",check)}
        geodata?.open()?.use {YokuliChartPackage.readManifest(it,"data",check)}
        return AtlasPackageManifest(YokuliChartPackage.writeAtlas(output,id,name,
            listOfNotNull(charts?.copy(priority=0),geodata?.copy(priority=if(charts==null)0 else 1)),metadata,provider,license,attribution,check,progress))
    }
}
