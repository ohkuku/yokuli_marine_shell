package com.yokuli.compiler

import com.yokuli.runtime.contract.chart.*
import com.yokuli.runtime.contract.planning.*
import com.yokuli.runtime.marine.chart.*
import com.yokuli.runtime.marine.chart.terrain.*
import com.yokuli.runtime.marine.planning.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.*

/** 制包时完成静态工作。中断后原子 .nav/SQLite READY 产物可复用，半成品不发布。 */
internal suspend fun prepareNativeProducts(stage:File,dataset:ChartDataset,selectedBounds:ChartBounds?,workers:Int,check:()->Unit) {
    val snapshot=ChartDataSnapshot("desktop-compiler",dataset.revision,listOf(dataset))
    val bounds=selectedBounds?.split()?:dataset.cells.flatMap{cell->
        cell.coverage.filter{it.covered}.flatMap{geometryBounds(it.geometry)}.ifEmpty{cell.bounds}
    }
    require(bounds.isNotEmpty()){"CHART_COVERAGE_MISSING"}
    NativeFactReader(stage,snapshot).use {reader->
        val candidates=linkedSetOf<PassageRegionId>()
        for(box in bounds)candidates+=PassageRegionId.intersecting(box)
        // Source coverage may be a long EEZ polygon with ocean holes, never prepare its whole bbox.
        val projection=DrawingProjection(180.0,check)
        val coverage=org.locationtech.jts.index.strtree.STRtree()
        for(cell in dataset.cells)for(item in cell.coverage.filter{it.covered}) {
            val shape=projection.geometry(item.geometry)
            if(!shape.isEmpty)coverage.insert(shape.envelopeInternal,org.locationtech.jts.geom.prep.PreparedGeometryFactory.prepare(shape))
        }
        coverage.build()
        val regions=if(dataset.cells.all{it.coverage.isEmpty()})candidates else candidates.filterTo(linkedSetOf()) {id->
            check();val box=projection.viewport(id.bounds)
            coverage.query(box.envelopeInternal).any{(it as org.locationtech.jts.geom.prep.PreparedGeometry).intersects(box)}
        }
        require(regions.size<=32_768){"Coverage exceeds one distribution volume; use --bounds west,south,east,north to create regional volumes"}
        val products=PassageRegionProducts(File(stage,"runtime/navigation"))
        val neutral=PassageVessel(null,null,null,null,null,null,null,null)
        val policy="base-water-semantics-v2"
        println("Preparing ${regions.size} navigation regions")
        val ordered=regions.sortedWith(compareBy<PassageRegionId>{it.y}.thenBy{it.x})
        val next=java.util.concurrent.atomic.AtomicInteger();val completed=java.util.concurrent.atomic.AtomicInteger()
        // 每个工作者有独立只读连接/有界缓存，相邻小批次提高命中；失败取消同一制包作业。
        coroutineScope {
            repeat(workers) {launch(Dispatchers.Default) {
                NativeFactReader(stage,snapshot).use {local->
                    val geometry=PassageGeometry(local)
                    while(true) {
                        val start=next.getAndAdd(16);if(start>=ordered.size)break
                        for(index in start until minOf(start+16,ordered.size)) {
                            ensureActive();check();val id=ordered[index]
                            val key=products.key(local.sourceIdentity,policy,id)
                            if(products.read(key,local.sourceIdentity,policy,id)==null) {
                                try {
                                    val request=PassageRequest("compile-$key",PassageRoute("compile","","",listOf(id.center,id.center)),listOf(dataset.id),neutral)
                                    val padding=listOf(ChartPoint(id.south,id.west),ChartPoint(id.south+PassageRegionId.STEP,id.west+PassageRegionId.STEP)).maxOf{distance(id.center,it)}+500.0
                                    val world=geometry.world(snapshot,request,listOf(id.center,id.center),padding,PassageWorldPurpose.NAVIGATION_TOPOLOGY)
                                    val product=compilePassageRegion(world,local.sourceIdentity,id,policy,check=check)
                                    products.write(key,product)
                                }catch(cancel:CancellationException){throw cancel}
                                catch(failure:Exception){throw IllegalStateException("Navigation region ${id.x}/${id.y} ${id.bounds}: ${failure.message}",failure)}
                            }
                            val count=completed.incrementAndGet()
                            if(count%25==0||count==1||count==ordered.size)println("  navigation $count/${ordered.size}")
                        }
                    }
                }
            }}
        }
        val archive=File(stage,"navigation.bin")
        require(PassagePreparedArchive.write(stage,archive,check,reader.sourceIdentity)){"No navigation regions prepared"}
        val terrainSource=ChartTerrainBlockCodec.hash("${CHART_TERRAIN_PRODUCT_RULES}:${reader.sourceIdentity}".toByteArray())
        val requests=linkedSetOf<ChartTerrainRequest>()
        // Level 10 is the distribution overview, loaded immediately at every closer camera scale.
        // Fine blocks remain independently resumable, never make the overview wait for a camera.
        val step=360.0/1024
        for(box in regions.map{it.bounds}) {
            val south=floor((box.south+90)/step).toInt();val north=floor((box.north+90-1e-10)/step).toInt()
            val longitudeFactor=2.0.pow(ceil(log2(1.0/cos(Math.toRadians(max(abs(box.south),abs(box.north)))).coerceAtLeast(.003))))
            val dx=min(360.0,step*longitudeFactor)
            for(y in south..north)for(x in floor((box.west+180)/dx).toInt()..floor((box.east+180-1e-10)/dx).toInt()) {
                requests+=ChartTerrainRequest(dataset.id,dataset.revision,ChartBounds(-180+x*dx,max(-90.0,-90+y*step),min(180.0,-180+(x+1)*dx),min(90.0,-90+(y+1)*step)),0)
            }
        }
        JdbcChartSql.create(File(stage,"terrain-products.sqlite")).use {db->
            db.execSQL("CREATE TABLE IF NOT EXISTS products(key TEXT PRIMARY KEY,source_key TEXT NOT NULL,schema INTEGER NOT NULL,sha256 TEXT NOT NULL,payload BLOB NOT NULL,created_at INTEGER NOT NULL)")
            db.execSQL("PRAGMA user_version=1")
            db.execSQL("DELETE FROM products WHERE source_key!=?",arrayOf(terrainSource))
            println("Preparing ${requests.size} overview terrain blocks")
            val writeLock=Mutex()
            val terrainQueue=requests.toList();val nextTerrain=java.util.concurrent.atomic.AtomicInteger()
            val finishedTerrain=java.util.concurrent.atomic.AtomicInteger()
            val blockCount=java.util.concurrent.atomic.AtomicInteger(db.rawQuery("SELECT count(*) FROM products WHERE source_key=?",arrayOf(terrainSource)).use{it.moveToFirst();it.getInt(0)})
            coroutineScope {
                repeat(workers){launch(Dispatchers.Default) {
                    NativeFactReader(stage,snapshot).use {local->
                        val compiler=ChartTerrainCompiler(local.displayReader())
                        suspend fun prepare(request:ChartTerrainRequest,depth:Int) {
                            ensureActive();check()
                            val key=ChartTerrainBlockCodec.hash("$terrainSource:${request.bounds}:${request.lod}".toByteArray())
                            val existing=writeLock.withLock {db.rawQuery("SELECT payload,sha256 FROM products WHERE key=?",arrayOf(key)).use{
                                if(it.moveToFirst())it.getBlob(0) to it.getString(1)else null
                            }}
                            if(existing!=null&&ChartTerrainBlockCodec.hash(existing.first)==existing.second) {
                                try {ChartTerrainBlockCodec.decode(existing.first,key);return}
                                catch(cancel:CancellationException){throw cancel}
                                catch(_:IllegalArgumentException){/* 仅重建本工作区的损坏完成块。 */}
                            }
                            val bytes=try {
                                ChartTerrainBlockCodec.encode(compiler.compile(snapshot,key,terrainSource,request.bounds,request.lod))
                            }catch(cancel:CancellationException){throw cancel}
                            catch(failure:Exception) {
                                val size=minOf(request.bounds.north-request.bounds.south,request.bounds.east-request.bounds.west)*111_320
                                if(failure.message in setOf("CHART_TERRAIN_SUBDIVIDE_REQUIRED","CHART_TERRAIN_MODEL_LIMIT")&&size>400&&depth<7) {
                                    // 不发布缺半片区域的假父块；既有读方可从 READY 祖先或细分子块显示基础层。
                                    for(child in request.splitTerrainRequest())prepare(child,depth+1)
                                    return
                                }
                                throw IllegalStateException("Terrain ${request.bounds}: ${failure.message}",failure)
                            }
                            writeLock.withLock {
                                if(existing==null)require(blockCount.incrementAndGet()<=65_536){"Terrain distribution volume exceeded"}
                                db.execSQL("INSERT OR REPLACE INTO products VALUES (?,?,?,?,?,?)",arrayOf(key,terrainSource,ChartTerrainBlockCodec.SCHEMA,ChartTerrainBlockCodec.hash(bytes),bytes,System.currentTimeMillis()))
                            }
                        }
                        while(true) {
                            val index=nextTerrain.getAndIncrement();if(index>=terrainQueue.size)break
                            prepare(terrainQueue[index],0)
                            val count=finishedTerrain.incrementAndGet()
                            if(count%25==0||count==1||count==terrainQueue.size)println("  terrain $count/${terrainQueue.size} (${blockCount.get()} blocks)")
                        }
                    }
                }}
            }
        }
    }
}
