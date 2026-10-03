package com.yokuli.marine.shell.rebuild.scene.navigation

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.yokuli.runtime.contract.chart.ChartDataService
import java.security.MessageDigest

/** Shell 只缓存解码后的只读块；持久产品和后台准备归 Core，海图与 AIS 复用同一份解码结果。 */
internal class NavigationTerrainProducts {
    private val memory=LinkedHashMap<String,NavigationChartScene>(16,.75f,true)
    private var memoryBytes=0L
    @Synchronized fun trim(){memory.clear();memoryBytes=0}
    private fun bytes(scene:NavigationChartScene)=(scene.surfaceGlb?.size?:0).toLong()+(scene.seabedGlb?.size?:0)+scene.markers.size*256L+scene.sources.size*256L+1024
    @Synchronized fun read(key:String,sourceKey:String):NavigationChartScene?=memory[key]?.takeIf{it.sourceKey==sourceKey}
    @Synchronized fun write(scene:NavigationChartScene) {
        require(scene.patches.isEmpty())
        memory.remove(scene.sceneKey)?.let{memoryBytes-=bytes(it)}
        memory[scene.sceneKey]=scene;memoryBytes+=bytes(scene)
        while((memoryBytes>48L*1024*1024||memory.size>96)&&memory.isNotEmpty()) {
            val key=memory.keys.first();memoryBytes-=bytes(requireNotNull(memory.remove(key)))
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
