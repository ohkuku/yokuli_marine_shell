package com.yokuli.marine.shell.rebuild.scene.navigation

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.ChartDataService
import java.io.*
import java.security.MessageDigest

/**
 * 海图/AIS 共用的展示编译产物。事实仍由 Core 快照拥有；这里仅缓存可重建的 GLB。
 * 内存按字节淘汰，磁盘按来源修订/编译版本命名，完整校验后原子发布。
 */
internal class NavigationTerrainProducts(private val directory:File?) {
    private val gson=Gson()
    private val memory=LinkedHashMap<String,NavigationChartScene>(16,.75f,true)
    private var memoryBytes=0L
    private val memoryLimit=32L*1024*1024
    private val diskLimit=192L*1024*1024
    @Synchronized fun trim(){memory.clear();memoryBytes=0}
    private fun bytes(scene:NavigationChartScene)=(scene.surfaceGlb?.size?:0).toLong()+(scene.seabedGlb?.size?:0)+scene.markers.size*256L+scene.sources.size*256L+1024
    @Synchronized private fun retain(scene:NavigationChartScene) {
        memory.remove(scene.sceneKey)?.let{memoryBytes-=bytes(it)}
        memory[scene.sceneKey]=scene;memoryBytes+=bytes(scene)
        while((memoryBytes>memoryLimit||memory.size>96)&&memory.isNotEmpty()){val key=memory.keys.first();memoryBytes-=bytes(requireNotNull(memory.remove(key)))}
    }
    fun read(key:String,sourceKey:String):NavigationChartScene? {
        synchronized(this){memory[key]}?.takeIf{it.sourceKey==sourceKey}?.let{return it}
        val root=directory?:return null
        val file=File(root,"$key.ykm")
        if(!file.isFile)return null
        return try {
            require(file.length() in 48..10L*1024*1024)
            val result=DataInputStream(BufferedInputStream(FileInputStream(file))).use{input->
                require(input.readInt()==0x594b4d34)
                val m=input.readInt();val s=input.readInt();val b=input.readInt()
                require(m in 2..2*1024*1024&&s in 0..8*1024*1024&&b in 0..8*1024*1024&&m.toLong()+s+b+48==file.length())
                val hash=ByteArray(32).also(input::readFully)
                val metadata=ByteArray(m).also(input::readFully)
                val surface=ByteArray(s).also(input::readFully);val seabed=ByteArray(b).also(input::readFully)
                val digest=MessageDigest.getInstance("SHA-256").apply{update(metadata);update(surface);update(seabed)}.digest()
                require(MessageDigest.isEqual(hash,digest))
                val info=gson.fromJson(metadata.toString(Charsets.UTF_8),NavigationChartScene::class.java)
                require(info.sceneKey==key&&info.sourceKey==sourceKey&&info.origin.valid()&&info.radiusMeters.isFinite()&&info.patches.isEmpty())
                info.copy(surfaceGlb=surface.takeIf{it.isNotEmpty()},seabedGlb=seabed.takeIf{it.isNotEmpty()})
            }
            file.setLastModified(System.currentTimeMillis());retain(result);result
        }catch(_:Exception){file.delete();null}
    }
    fun write(scene:NavigationChartScene) {
        require(scene.patches.isEmpty())
        retain(scene)
        val root=directory?:return
        var staging:File?=null
        try {
            if(!root.isDirectory&&!root.mkdirs())return
            val target=File(root,"${scene.sceneKey}.ykm")
            if(target.isFile)return
            val metadata=gson.toJson(scene.copy(surfaceGlb=null,seabedGlb=null)).toByteArray(Charsets.UTF_8)
            val surface=scene.surfaceGlb?:ByteArray(0);val seabed=scene.seabedGlb?:ByteArray(0)
            val digest=MessageDigest.getInstance("SHA-256").apply{update(metadata);update(surface);update(seabed)}.digest()
            staging=File.createTempFile("terrain-",".pending",root)
            FileOutputStream(staging).use{file->
                val out=DataOutputStream(BufferedOutputStream(file))
                out.writeInt(0x594b4d34);out.writeInt(metadata.size);out.writeInt(surface.size);out.writeInt(seabed.size)
                out.write(digest);out.write(metadata);out.write(surface);out.write(seabed);out.flush();file.fd.sync()
            }
            require(staging.renameTo(target)){"TERRAIN_CACHE_PUBLISH_FAILED"}
            // 异常退出的临时产物不会被读取；此目录只含本编译器可重建缓存。
            val files=root.listFiles().orEmpty()
            files.filter{it.name.endsWith(".pending")&&System.currentTimeMillis()-it.lastModified()>60_000}.forEach{it.delete()}
            val published=files.filter{it.extension=="ykm"}.sortedBy{it.lastModified()}
            var total=published.sumOf{it.length()}
            var count=published.size
            for(file in published){if(total<=diskLimit&&count<=1024)break;val size=file.length();if(file.delete()){total-=size;count--}}
        }catch(_:IOException){/* 缓存写入失败不破坏已完成的来源读取和可显示网格。 */}
        catch(_:IllegalArgumentException){/* 元数据拒绝缓存时继续使用内存中的不可变产物。 */}
        catch(_:IllegalStateException){/* 原子发布失败，保留原缓存。 */}
        finally {staging?.delete()}
    }
}

/** 一份 UI 展示运行时跨应用复用；不建立定位/导航/AIS 的第二个业务所有者。 */
internal object NavigationTerrainRuntime {
    private var service:ChartDataService?=null
    private var loader:NavigationTerrainLoader?=null
    private var registered=false
    @Synchronized fun shared(context:Context,charts:ChartDataService):NavigationTerrainLoader {
        if(service!==charts){service=charts;loader=NavigationTerrainLoader(charts,File(context.applicationContext.cacheDir,"maritime-terrain-v4"))}
        if(!registered){
            registered=true
            context.applicationContext.registerComponentCallbacks(object:ComponentCallbacks2 {
                override fun onConfigurationChanged(value:Configuration){}
                override fun onLowMemory(){synchronized(this@NavigationTerrainRuntime){loader?.trimMemory()}}
                override fun onTrimMemory(level:Int){if(level>=ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)synchronized(this@NavigationTerrainRuntime){loader?.trimMemory()}}
            })
        }
        return requireNotNull(loader)
    }
}

internal fun terrainHash(value:String):String=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
