package com.yokuli.marine.shell.rebuild.scene.navigation

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.yokuli.runtime.contract.chart.ChartDataService
import com.yokuli.runtime.contract.chart.ChartTerrainRequest
import java.security.MessageDigest

/** Shell 只缓存解码后的只读块；持久产品和后台准备归 Core，海图与 AIS 复用同一份解码结果。 */
internal class NavigationTerrainProducts {
    private data class Entry(val scene:NavigationChartScene,val datasetId:String,val revision:Long)
    private val memory=LinkedHashMap<String,Entry>(16,.75f,true)
    private var memoryBytes=0L
    @Synchronized fun trim(){memory.clear();memoryBytes=0}
    @Synchronized fun remove(datasetId:String){
        val keys=memory.filterValues{it.datasetId==datasetId}.keys.toList()
        keys.forEach{key->memory.remove(key)?.let{memoryBytes-=bytes(it.scene)}}
    }
    private fun bytes(scene:NavigationChartScene)=(scene.surfaceGlb?.size?:0).toLong()+(scene.seabedGlb?.size?:0)+scene.markers.size*256L+scene.sources.size*256L+1024
    @Synchronized fun read(key:String,sourceKey:String):NavigationChartScene?=memory[key]?.scene?.takeIf{it.sourceKey==sourceKey}
    @Synchronized fun nearby(datasetId:String,revision:Long,requests:List<ChartTerrainRequest>):List<NavigationChartScene> {
        val wanted=requests.mapTo(hashSetOf()){it.bounds}
        val found=memory.values.filter{entry->entry.datasetId==datasetId&&entry.revision==revision&&
            entry.scene.bounds?.let{tile->wanted.any{it.west<tile.east&&it.east>tile.west&&it.south<tile.north&&it.north>tile.south}}==true}
            .groupBy{it.scene.bounds}.values.map{entries->entries.maxBy{it.scene.lod}.scene}
        found.forEach{memory[it.sceneKey]} // Access-order LRU: a warm viewport remains resident.
        return found
    }
    @Synchronized fun write(scene:NavigationChartScene,datasetId:String,revision:Long) {
        require(scene.patches.isEmpty())
        memory.remove(scene.sceneKey)?.let{memoryBytes-=bytes(it.scene)}
        memory[scene.sceneKey]=Entry(scene,datasetId,revision);memoryBytes+=bytes(scene)
        while((memoryBytes>48L*1024*1024||memory.size>96)&&memory.isNotEmpty()) {
            val key=memory.keys.first();memoryBytes-=bytes(requireNotNull(memory.remove(key)).scene)
        }
    }
}

/** 一份只读展示运行时跨应用复用；不持有第二份定位、AIS 或导航业务状态。 */
internal object NavigationTerrainRuntime {
    private var service:ChartDataService?=null
    private var loader:NavigationTerrainLoader?=null
    private var registered=false
    @Synchronized fun shared(context:Context,charts:ChartDataService):NavigationTerrainLoader {
        if(service!==charts){service=charts;loader=NavigationTerrainLoader(charts)}
        if(!registered) {
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
