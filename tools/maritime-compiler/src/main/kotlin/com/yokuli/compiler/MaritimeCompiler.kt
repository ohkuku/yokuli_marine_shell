package com.yokuli.compiler

import com.google.gson.Gson
import com.yokuli.chartpackage.*
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.marine.chart.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

/** 工作区检查点也必须原子发布，断电不能把已完成的事实库变成不可恢复状态。 */
private fun writeCheckpoint(file:File,text:String) {
    val pending=File(file.parentFile,".${file.name}.pending")
    try {
        FileOutputStream(pending).use{out->out.write(text.toByteArray(Charsets.UTF_8));out.fd.sync()}
        Files.move(pending.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }finally {pending.delete()}
}

/** 制包入口：真实源资料 → 与手机相同的事实库 → 原生数据子包 → 可导入图册的 .yklpkg。 */
fun main(args:Array<String>)=runBlocking {
    val options=args.toList().chunked(2).associate {require(it.size==2&&it[0].startsWith("--")){"Usage: --input folder --output file.yklpkg --id identifier --name title"};it[0].removePrefix("--") to it[1]}
    require(options.keys.all{it in setOf("input","output","id","name","provider","license","attribution","work-dir","products","bounds","workers")}){"Unknown compiler option"}
    val input=File(requireNotNull(options["input"]){"--input is required"}).canonicalFile
    val output=File(requireNotNull(options["output"]){"--output is required"}).canonicalFile
    require(input.isDirectory&&output.extension=="yklpkg"){"Input must be a directory and output must end in .yklpkg"}
    require(!output.exists()){ "Output already exists; choose a new version: $output" }
    val id=options["id"]?:output.nameWithoutExtension
    val name=options["name"]?:id
    require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))){"Invalid package id"}
    val files=input.listFiles().orEmpty().filter{it.isFile&&it.extension.equals("gpkg",true)}.sortedBy{it.name}
    require(files.isNotEmpty()){ "No supported GeoPackage sources in $input" }
    require(output.parentFile.isDirectory||output.parentFile.mkdirs()){ "Cannot create output directory" }
    val keepWork=options["work-dir"]!=null
    val stage=options["work-dir"]?.let{File(it).canonicalFile}?:Files.createTempDirectory(output.parentFile.toPath(),".maritime-compile-").toFile()
    require(stage.isDirectory||stage.mkdirs())
    val owner=File(stage,"compiler-source.json")
    val sourceIdentity=Gson().toJson(listOf("native-compiler-1",id,files.map{listOf(it.canonicalPath,it.length(),it.lastModified())}))
    if(owner.exists())require(owner.readText()==sourceIdentity){"Compiler workspace belongs to different source revisions"}
    else {require(stage.listFiles().orEmpty().isEmpty()){ "Compiler workspace must be empty" };writeCheckpoint(owner,sourceIdentity)}
    val gson=Gson()
    val nextSpaceCheck=java.util.concurrent.atomic.AtomicLong()
    fun check(){
        if(Thread.currentThread().isInterrupted)throw InterruptedException()
        // 几何内循环会频繁检查取消；不能每个顶点都访问文件系统查询剩余空间。
        val now=System.nanoTime();val next=nextSpaceCheck.get()
        if(now>=next&&nextSpaceCheck.compareAndSet(next,now+1_000_000_000L))
            require(stage.usableSpace>64L*1024*1024){"CHART_STORAGE_FULL"}
    }
    fun fingerprints():String=gson.toJson(files.map{source->
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        source.inputStream().buffered().use{stream->
            val buffer=ByteArray(128*1024)
            while(true){check();val size=stream.read(buffer);if(size<0)break;digest.update(buffer,0,size)}
        }
        listOf(source.name,digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)})
    })
    try {
        val hashFile=File(stage,"source-fingerprints.json")
        val sourceHashes=fingerprints()
        if(hashFile.exists())require(hashFile.readText()==sourceHashes){"Source content changed; use a new compiler workspace"}
        else writeCheckpoint(hashFile,sourceHashes)
        val datasetId=UUID.nameUUIDFromBytes(id.toByteArray()).toString()
        val catalogFile=File(stage,"catalog.json")
        var dataset:ChartDataset?=null
        if(catalogFile.isFile) {
            val parsed=com.google.gson.JsonParser.parseString(catalogFile.readText()).asJsonObject
            dataset=gson.fromJson(parsed.get("dataset"),ChartDataset::class.java)
            println("Reusing completed native facts: ${dataset.cells.sumOf{it.featureCount}} objects")
        }
        if(dataset==null) {
            // Only this marked workspace owns these incomplete files; original sources are read-only.
            listOf("features.sqlite","features.sqlite-journal","features.sqlite-wal","features.sqlite-shm").forEach{File(stage,it).delete()}
            File(stage,"facts-layout-v2.complete").delete()
        }
        val cells=ArrayList<ChartCellRevision>()
        val dictionaries=S57Dictionaries(requireNotNull(object {}.javaClass.getResourceAsStream("/chartdata/s57objectclasses.csv")).bufferedReader().use{it.readText()},
            requireNotNull(object {}.javaClass.getResourceAsStream("/chartdata/s57attributes.csv")).bufferedReader().use{it.readText()})
        if(dataset==null)for((index,file) in files.withIndex()) {
            check()
            println("Compiling ${index+1}/${files.size}: ${file.name}")
            var last=-1
            val cell="GPKG_${UUID.nameUUIDFromBytes(file.name.toByteArray())}"
            cells+=GeoPackageChartImporter.prepare(file,stage,datasetId,::check,database=JdbcChartSql,append=index>0,objectClasses=dictionaries.objects,
                cellIdOverride=cell,progress={done,total,_->
                    val percent=if(total>0)done*100/total else 0
                    if(percent/10!=last){last=percent/10;println("  $done/$total objects")}
                }).map{it.copy(priority=index,priorityExplicit=true,sourceName=file.name)}
        }
        if(dataset==null)require(fingerprints()==sourceHashes){"Source changed while compiling; use a new compiler workspace"}
        check()
        val packingVersion=File(stage,"facts-layout-v2.complete")
        JdbcChartSql.create(File(stage,"features.sqlite")).use {db->
            if(!packingVersion.isFile) {
                println("Packing native facts without redundant LOD indexes")
                for(index in listOf("feature_detail_scale","feature_detail_tier","feature_cell_tier_kind","feature_cell_scale_kind"))
                    db.execSQL("DROP INDEX IF EXISTS $index")
                db.execSQL("PRAGMA page_size=16384")
                db.execSQL("VACUUM")
                db.execSQL("ANALYZE")
            }
            db.rawQuery("PRAGMA quick_check(1)").use{require(it.moveToFirst()&&it.getString(0)=="ok"){"CHART_NATIVE_INDEX_INVALID"}}
        }
        writeCheckpoint(packingVersion,"2")
        if(dataset==null)dataset=ChartDataset(datasetId,name,"GPKG",1,Instant.now().toEpochMilli(),
            DataEligibility(ChartUse.REFERENCE_ONLY,options["provider"].orEmpty(),options["license"].orEmpty(),automatic=true),cells)
        // 修改署名/名称允许复用事实，但内外层必须一致；来源身份会使受影响的产物自然失效。
        dataset=dataset.copy(name=name,eligibility=dataset.eligibility.copy(
            provider=options["provider"].orEmpty(),licenceEvidence=options["license"].orEmpty()))
        val descriptor=mapOf("format" to "yokuli.native-maritime","schema" to 1,"dataset" to dataset,
            "originals" to emptyList<Any>(),"originalsComplete" to false)
        writeCheckpoint(catalogFile,gson.toJson(descriptor))
        val mode=options["products"]?:"full"
        require(mode in setOf("full","facts")){"--products must be full or facts"}
        val requestedBounds=options["bounds"]?.split(',')?.map(String::toDouble)?.let{
            require(it.size==4);ChartBounds(it[0],it[1],it[2],it[3]).also{box->require(box.valid)}
        }
        val workers=options["workers"]?.toInt()?:minOf(2,Runtime.getRuntime().availableProcessors())
        require(workers in 1..8){"--workers must be between 1 and 8"}
        if(mode=="full")prepareNativeProducts(stage,dataset,requestedBounds,workers,::check)
        val sourceFiles=mutableListOf("catalog.json" to "native-catalog","features.sqlite" to "native-facts")
        if(mode=="full")sourceFiles+=listOf("terrain-products.sqlite" to "native-terrain","navigation.bin" to "native-navigation")
        val sources=sourceFiles.mapIndexed{index,(file,format)->
            ChartPackageSource("files/runtime/$file",format,index){File(stage,file).inputStream()}
        }
        val provider=options["provider"].orEmpty();val license=options["license"].orEmpty();val attribution=options["attribution"].orEmpty()
        val geodata=File(stage,"data.yklgeodata")
        FileOutputStream(geodata).use{out->YokuliChartPackage.write(out,id,name,"data",sources,provider=provider,license=license,attribution=attribution,check=::check)}
        // 包 writer 负责关闭 ZIP 及其流；完成后用独立描述符同步，不能 sync 已关闭的 fd。
        FileOutputStream(geodata,true).use{it.fd.sync()}
        val packageFile=File(stage,"bundle.yklpkg")
        FileOutputStream(packageFile).use{out->YokuliAtlasPackage.write(out,id,name,geodata=ChartPackageSource("files/data.yklgeodata","geodata",0){geodata.inputStream()},
            provider=provider,license=license,attribution=attribution,check=::check)}
        FileOutputStream(packageFile,true).use{it.fd.sync()}
        check();Files.move(packageFile.toPath(),output.toPath(),StandardCopyOption.ATOMIC_MOVE)
        println("Published ${output.path} (${output.length()} bytes, ${dataset.cells.sumOf{it.featureCount}} objects)")
    }finally {
        // 完成块/事实检查点才是续作依据；外层封装的临时副本不长期占用数 GB 磁盘。
        File(stage,"data.yklgeodata").delete()
        File(stage,"bundle.yklpkg").delete()
        if(!keepWork)stage.deleteRecursively()
    }
}
