from pathlib import Path
import hashlib
repo=Path.cwd()
main=repo/'tools/maritime-compiler/src/main/kotlin/com/yokuli/compiler'
build=repo/'tools/maritime-compiler/build.gradle.kts'
s=build.read_text()
s=s.replace('"ChartSql", "ChartFeatureEncoder"','"ChartPositionQuery", "ChartPositionSessions", "ChartFeaturePayload", "ChartGeometrySpanIndex", "ChartSql", "ChartFeatureEncoder"')
s=s.replace('"PassageGeometry", "PassageGeometryOperations"','"PassageRegionRouter", "PassageTopologyStore", "PassageLazySearch", "PassageGeometry", "PassageGeometryOperations"')
s+='''\ntasks.register<JavaExec>("actualRouteProbe") {\n    classpath = sourceSets.main.get().runtimeClasspath\n    mainClass.set("com.yokuli.compiler.ActualRouteProbeKt")\n    args("/tmp/yokuli-route-probe")\n    maxHeapSize = "4g"\n}\n'''
build.write_text(s)
service=(repo/'runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt').read_text()
a=service.index('    private suspend fun inspectPreparedPosition(')
b=service.index('    private fun acquirePositionSession(',a)
method=service[a:b]
print('Unmodified production inspectPreparedPosition body SHA256',hashlib.sha256(method.encode()).hexdigest())
header='''package com.yokuli.runtime.marine.chart
import com.yokuli.compiler.NativeFactReader
import com.yokuli.compiler.JdbcChartSql
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.hardware.VirtualHostServices
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.google.gson.Gson
import java.io.File
import java.util.UUID
import kotlin.math.*

// Diagnostic ownership/IO adapter only. No Android Core or mutable catalogue is instantiated.
internal class LocalChartDataService(private val dir:File,private val reader:NativeFactReader) {
    private data class Stored(val dataset:ChartDataset,val directory:String)
    private data class Catalogue(val datasets:List<Stored>)
    private data class IndexedFeature(val stored:Stored,val id:String,val rowId:Long,val length:Int)
    private val root=dir.parentFile
    private val stored=Stored(reader.snapshot.datasets.single(),dir.name)
    private val catalogue=Catalogue(listOf(stored))
    private val leases=mutableMapOf(reader.snapshot.id to listOf(stored))
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(ChartDataState(loading=false))
    private val positionSessions=ChartPositionSessions()
    private val geometryQueries=ChartGeometryQueryIndex()
    private val gson=Gson()
    suspend fun preparedSourceIdentity(snapshotId:String):String {require(snapshotId==reader.snapshot.id);return reader.sourceIdentity}
    suspend fun preparedNavigationDirectory(snapshotId:String):File {require(snapshotId==reader.snapshot.id);return File(dir,"runtime/navigation")}
    suspend fun inspect(snapshotId:String,point:ChartPoint)=inspectPreparedPosition(snapshotId,null,point,5.0,true)
    private fun checkLinkedSources(selected:List<Stored>){require(selected.single()==stored);require(File(dir,"features.sqlite").isFile)}
    private fun acquirePositionSession(stored:Stored)=positionSessions.acquire(ChartPositionSessions.Key(stored.directory,stored.dataset.revision,1,1)) {
        ChartPositionSessions.Session(SQLiteDatabase(JdbcChartSql.openReadOnly(File(dir,"features.sqlite"))),null)
    }
    private suspend fun releasePositionLease(id:String)=withContext(NonCancellable){mutex.withLock {leases.remove(id);Unit}}
    private fun spatialQueryFrom(buckets:List<Int>?):String =
        (if(buckets==null)"spatial s" else "spatial_bucket sb INDEXED BY spatial_bucket_key CROSS JOIN spatial s ON s.id=sb.spatial_id")+
            " CROSS JOIN spatial_feature sf ON sf.id=s.id CROSS JOIN features f ON f.rowid=sf.feature_row"
    private fun localReadBytes(feature:NauticalFeature):Int =
        (feature.geometry.parts.sumOf{it.points.size.toLong()*64+64}+
            feature.attributes.entries.sumOf{(it.key.length+it.value.length).toLong()*2}+4096)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    private fun readWindowFeature(db:SQLiteDatabase,row:IndexedFeature,signal:CancellationSignal,bounds:ChartBounds):NauticalFeature = error("Diagnostic only accepts verified native schema 8")
'''
(main/'DiagnosticCore.kt').write_text(header+method+'\n}\n')
(main/'DiagnosticSQLite.kt').write_text('''package android.database.sqlite
import com.yokuli.runtime.marine.chart.ChartSql
import com.yokuli.runtime.marine.chart.ChartSqlRows
import android.os.CancellationSignal
import java.io.Closeable
internal class SQLiteDatabase(val sql:ChartSql):Closeable {
    val version:Int get()=rawQuery("PRAGMA user_version",null).use{check(it.moveToFirst());it.getInt(0)}
    fun rawQuery(query:String,args:Array<out String>?,signal:CancellationSignal?=null):ChartSqlRows {
        signal?.throwIfCanceled();return sql.rawQuery(query,args)
    }
    fun execSQL(query:String,args:Array<out Any?> = emptyArray())=sql.execSQL(query,args)
    override fun close()=sql.close()
}
''')
(main/'DiagnosticAndroidSql.kt').write_text('''package com.yokuli.runtime.marine.chart
import android.database.sqlite.SQLiteDatabase
internal class AndroidChartSql(db:SQLiteDatabase):ChartSql by db.sql
internal object ChartFeatureIndex {fun queryBuckets(bounds:com.yokuli.runtime.contract.chart.ChartBounds)=ChartNativeIndexWriter.queryBuckets(bounds)}
internal class RasterBathymetryStore:java.io.Closeable {
    fun readWindow(id:String,x:Int,y:Int,w:Int,h:Int,check:()->Unit):com.yokuli.runtime.contract.chart.RasterBathymetryWindow=error("Fixture contains no rasters")
    override fun close(){}
}
''')
(main/'DiagnosticAndroidOs.kt').write_text('''package android.os
class CancellationSignal {
    @Volatile private var cancelled=false
    fun cancel(){cancelled=true}
    fun throwIfCanceled(){if(cancelled)throw java.util.concurrent.CancellationException()}
}
object SystemClock {fun elapsedRealtime()=System.nanoTime()/1_000_000}
''')
(main/'DiagnosticContent.kt').write_text('''package android.content
class ContentValues
''')
(main/'DiagnosticLog.kt').write_text('''package android.util
object Log {
    @JvmStatic fun i(tag:String,message:String):Int {println("$tag: $message");return 0}
}
''')
(main/'ActualRouteProbe.kt').write_text('''package com.yokuli.compiler
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.*
import com.yokuli.runtime.marine.chart.*
import com.yokuli.runtime.marine.planning.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.system.measureNanoTime

fun main(args:Array<String>)=runBlocking {
    val dir=File(args.single());val gson=Gson()
    val json=JsonParser.parseString(File(dir,"catalog.json").readText()).asJsonObject
    val dataset=gson.fromJson(json.get("dataset"),ChartDataset::class.java)
    val snapshot=ChartDataSnapshot("actual-package-probe",dataset.revision,listOf(dataset))
    NativeFactReader(dir,snapshot).use { reader ->
        println("JVM_SOURCE_IDENTITY "+reader.sourceIdentity)
        require(reader.sourceIdentity=="6ba67ff543c33e5e33741f2fccfe1cf5e63840d262f1a331c11d89402e3a5e27")
        val core=LocalChartDataService(dir,reader)
        val start=ChartPoint(-36.786067,174.672049);val end=ChartPoint(-36.744436,174.807857)
        for((name,point) in listOf("A" to start,"B" to end)) {
            try {
                val info=core.inspect(snapshot.id,point)
                println("JVM_ORIGINAL_POINT_QUERY "+gson.toJson(mapOf("point" to name,"result" to info)))
            }catch(e:Exception){println("JVM_POINT_QUERY_FAILURE "+name+" "+e.javaClass.name+" "+e.message);e.printStackTrace()}
        }
        PassagePreparedArchive.install(File(dir,"navigation.bin"),dir,{})
        println("JVM_NAVIGATION_INSTALLED "+File(dir,"runtime/navigation").listFiles()!!.size)
        val router=PassageRegionRouter(core,PassageGeometry(reader))
        for(draft in listOf<Double?>(null,2.1)) {
            repeat(2) { attempt ->
                val vessel=PassageVessel(draft,null,null,if(draft==null)null else 0.0,null,null,null,null)
                val request=PassageRequest("actual-$draft-$attempt",PassageRoute("probe","probe","A to B",listOf(start,end)),listOf(dataset.id),vessel)
                val t=System.nanoTime()
                try {
                    val result=withTimeout(180_000){router.route(snapshot,request,start,end,onPreparing={println("JVM_PREPARING "+it)}){println("JVM_SEARCH "+it)}}
                    val output=mapOf("draftMeters" to draft,"attempt" to attempt,"hostElapsedMillis" to (System.nanoTime()-t)/1_000_000,
                        "returned" to (result!=null),"distanceMeters" to result?.points?.zipWithNext()?.sumOf{distance(it.first,it.second)},
                        "points" to result?.points,"issueCount" to result?.issues?.size)
                    println("JVM_PRODUCTION_ROUTER "+gson.toJson(output))
                    File(dir,"route-$draft-$attempt.json").writeText(gson.toJson(output))
                }catch(e:Exception) {
                    println("JVM_ROUTER_FAILURE "+gson.toJson(mapOf("draft" to draft,"attempt" to attempt,"type" to e.javaClass.name,"message" to e.message,"hostElapsedMillis" to (System.nanoTime()-t)/1_000_000)))
                    e.printStackTrace()
                }
            }
        }
    }
    Unit
}
''')
print('Prepared isolated JVM adapter. Production query body and routing source are not modified.')
