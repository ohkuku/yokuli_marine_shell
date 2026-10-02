package com.yokuli.marine.shell.rebuild.scene.ais

import com.google.android.filament.Engine
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.yokuli.marine.shell.rebuild.GeoPoint
import com.yokuli.marine.shell.rebuild.scene.navigation.routeMesh
import com.yokuli.marine.shell.rebuild.scene.navigation.navigationTerrainOrigin
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** AIS 只显示 Core 当前导航路线，网格与导航视图共用，不保存或修改航线。 */
internal class AisRouteLayer(
    private val engine:Engine,private val scene:Scene,private val loader:AssetLoader,
    private val requestFrame:()->Unit,private val onFailure:(Throwable?)->Unit,
){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val resources=ResourceLoader(engine)
    private var job:Job?=null
    private var key=""
    private var routeIdentity=0
    private var closed=false
    private var pending:ByteBuffer?=null
    private var loading:FilamentAsset?=null
    private var current:FilamentAsset?=null
    private var held:ByteBuffer?=null
    private var loadedOrigin:GeoPoint?=null
    private var requestedOrigin:GeoPoint?=null
    private var loadingOrigin:GeoPoint?=null
    private var transformLocal:AisLocalFrame?=null
    val busy get()=job?.isActive==true||pending!=null||loading!=null
    fun update(route:List<GeoPoint>,frame:AisSceneFrame,range:Double,center:AisScenePosition?=null){
        val focus=center?:frame.local.origin
        val origin=navigationTerrainOrigin(GeoPoint(focus.latitude,focus.longitude),range)
        val band=round(ln(range.coerceAtLeast(100.0))/ln(1.5)).toInt()
        val identity=route.hashCode()
        val next="$identity:$origin:$band"
        if(closed||next==key)return
        key=next;job?.cancel();pending=null;cancelUpload()
        // 路线身份变化立即移除旧线；只有同一条线的视域/线宽细化才保留旧资源。
        if(identity!=routeIdentity){clear();routeIdentity=identity}
        requestedOrigin=origin
        if(route.size<2){clear();return}
        onFailure(null)
        job=scope.launch {
            try{
                delay(90)
                val bytes=withContext(Dispatchers.Default){
                    val context=currentCoroutineContext()
                    routeMesh(route,origin,(range*6).coerceIn(1_000.0,120_000.0),(range/750).coerceIn(.25,20.0)){context.ensureActive()}
                        ?.let{ByteBuffer.allocateDirect(it.size).order(ByteOrder.nativeOrder()).apply{put(it);flip()}}
                }
                if(!closed&&key==next){pending=bytes;if(bytes==null)clear();requestFrame()}
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){onFailure(error)}
        }
    }
    fun advance(frame:AisSceneFrame){
        if(closed)return
        try{
            loading?.let{asset->
                resources.asyncUpdateLoad()
                if(resources.asyncGetLoadProgress()>=1f){
                    loading=null;held=null;asset.releaseSourceData();clear();current=asset;loadedOrigin=loadingOrigin
                    val manager=engine.renderableManager
                    asset.entities.forEach{entity->val instance=manager.getInstance(entity)
                        if(instance!=0){manager.setPriority(instance,7);manager.setCastShadows(instance,false)
                            for(i in 0 until manager.getPrimitiveCount(instance))manager.getMaterialInstanceAt(instance,i).apply{setDepthWrite(false);setDepthCulling(false)}}}
                    scene.addEntities(asset.entities);transformLocal=null
                }
            }
            if(loading==null)pending?.let{buffer->
                pending=null;held=buffer;loadingOrigin=requestedOrigin
                val asset=requireNotNull(loader.createAsset(buffer)){"AIS_ROUTE_INVALID"};loading=asset
                require(asset.resourceUris.isEmpty());check(resources.asyncBeginLoad(asset)){"AIS_ROUTE_UPLOAD_FAILED"}
            }
            if(transformLocal!==frame.local){
                val asset=current?:return;val origin=loadedOrigin?:return;transformLocal=frame.local
                val point=frame.local.position(AisScenePosition(origin.lat,origin.lon))
                val east=frame.local.position(AisScenePosition(origin.lat,origin.lon+1/(111_320*cos(Math.toRadians(origin.lat)).coerceAtLeast(.003))))
                val south=frame.local.position(AisScenePosition(origin.lat-1.0/111_320,origin.lon))
                val matrix=floatArrayOf((east.x-point.x).toFloat(),0f,(east.z-point.z).toFloat(),0f,0f,1f,0f,0f,(south.x-point.x).toFloat(),0f,(south.z-point.z).toFloat(),0f,point.x.toFloat(),0f,point.z.toFloat(),1f)
                engine.transformManager.setTransform(engine.transformManager.getInstance(asset.root),matrix)
            }
        }catch(error:Exception){cancelUpload();pending=null;onFailure(error)}
    }
    private fun cancelUpload(){loading?.let{asset->loading=null;held=null;try{resources.asyncCancelLoad();resources.evictResourceData()}finally{loader.destroyAsset(asset)}}}
    private fun clear(){current?.let{scene.removeEntities(it.entities);loader.destroyAsset(it)};current=null;loadedOrigin=null;transformLocal=null}
    fun close(){if(closed)return;closed=true;scope.cancel();pending=null;runCatching{cancelUpload()};runCatching{clear()};runCatching{resources.destroy()}}
}
