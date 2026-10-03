package com.yokuli.marine.shell.rebuild.scene.ais

import com.google.android.filament.Engine
import com.google.android.filament.Scene
import com.google.android.filament.MaterialInstance
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.FilamentInstance
import kotlin.math.*

/** 有界实例池共享网格与材质；每个目标的实体身份在观测更新之间保持不变。 */
internal class TrafficModelPool(val asset: FilamentAsset, instances: List<FilamentInstance>) {
    private val available = ArrayDeque(instances)
    val assigned = mutableMapOf<String, FilamentInstance>()
    private val transforms = mutableMapOf<Int, FloatArray>()
    private val paints = mutableMapOf<Int, String>()
    private val shapes = mutableMapOf<Int,String>()
    private val entityNames = asset.instance.entities.map { asset.getName(it).orEmpty() }
    private val modelEntities = mutableMapOf<Pair<Int,String>,IntArray>()
    private var capacity = instances.size
    private var sourceReleased = false
    /** 每帧只补一小批，避免繁忙水域首次打开时在 UI 线程创建最大实例池。 */
    fun growFor(required: Int, loader: AssetLoader): Boolean {
        val target = required.coerceIn(0, 65)
        repeat(min(4, (target - capacity).coerceAtLeast(0))) {
            val instance = requireNotNull(loader.createInstance(asset)) { "Traffic instance could not be created" }
            available.addLast(instance); capacity++
        }
        if (capacity == 65 && !sourceReleased) { asset.releaseSourceData(); sourceReleased = true }
        return capacity < target
    }
    fun acquire(id: String): FilamentInstance? = assigned[id] ?: available.removeFirstOrNull()?.also { assigned[id] = it }
    fun transformChanged(instance: FilamentInstance, transform: FloatArray): Boolean {
        if (transforms[instance.root]?.contentEquals(transform) == true) return false
        transforms[instance.root] = transform
        return true
    }
    fun paintChanged(instance: FilamentInstance, paint: String): Boolean {
        if (paints[instance.root] == paint) return false
        paints[instance.root] = paint
        return true
    }
    /** 一份共享 fleet 网格只把报告类别对应的实体加入场景，其余轮廓不参与绘制。 */
    fun entities(instance: FilamentInstance, form: AisVesselForm): IntArray =
        modelEntities.getOrPut(instance.root to form.name) {
            val index=entityNames.indexOf("display:${form.name}").takeIf{it>=0}
                ?: entityNames.indexOf("display:GENERIC")
            if(index>=0&&index<instance.entities.size) intArrayOf(instance.entities[index]) else instance.entities
        }
    fun show(instance:FilamentInstance,form:AisVesselForm,scene:Scene) {
        if(shapes[instance.root]==form.name)return
        scene.removeEntities(instance.entities)
        scene.addEntities(entities(instance,form));shapes[instance.root]=form.name
    }
    fun retain(ids: Set<String>, scene: Scene) {
        assigned.keys.filterNot(ids::contains).forEach { id ->
            assigned.remove(id)?.let { scene.removeEntities(it.entities);shapes.remove(it.root); available.addLast(it) }
        }
    }
}

/** 导航与 AIS 共用的实时交通绘制；宿主只提供本次帧相机和唯一 AIS 快照。 */
internal class MaritimeTrafficLayer(
    private val engine:Engine, private val scene:Scene, private val loader:AssetLoader,
    private val vessels:TrafficModelPool, private val neutral:TrafficModelPool,
){
    private val materials=mutableMapOf<String,MaterialInstance>()
    private val originals=mutableMapOf<Pair<Int,Int>,MaterialInstance>()
    private var priorityInput:List<AisSceneTarget>?=null
    private var prioritySelected:String?=null
    private var priorityCenter:AisVector3?=null
    private var ordered=emptyList<AisSceneTarget>()
    var presentedIds:Set<String> = emptySet();private set
    var growing=false;private set
    fun forgetObservations(){priorityInput=null;prioritySelected=null;priorityCenter=null;ordered=emptyList();presentedIds=emptySet()}
    fun draw(frame:AisSceneFrame,selectedId:String?,viewportWidth:Int,density:Float,own:AisSceneTarget?=null){
        fun visible(target:AisSceneTarget):Boolean {
            val p=frame.targetProjections[target.id]?:frame.camera.project(frame.targetPositions[target.id]?:frame.local.position(target.position))
            return p.x in -.25f..1.25f&&p.y in -.25f..1.25f
        }
        val center=frame.camera.target
        if(priorityInput!==frame.targets||prioritySelected!=selectedId||priorityCenter?.let{(it-center).let {p->p.dot(p)}>max(100.0,frame.camera.halfHeight*.25).pow(2)}!=false){
            priorityInput=frame.targets;prioritySelected=selectedId;priorityCenter=center
            ordered=frame.targets.sortedWith(compareByDescending<AisSceneTarget>{it.id==selectedId}.thenByDescending{it.risk}.thenByDescending{it.followed}
                .thenBy{frame.targetPositions[it.id]?.let {point->(point-center).let {p->p.dot(p)}}?:Double.MAX_VALUE}.thenBy{it.id})
        }
        val prioritized=ordered.asSequence().filter(::visible).take(64).toList()
        val candidates=listOfNotNull(own?.takeIf(::visible))+prioritized
        val ships=candidates.filter{it.kind==AisSceneKind.VESSEL&&validAisBearing(it.headingDegrees)!=null}
        val symbols=candidates.filterNot{it.kind==AisSceneKind.VESSEL&&validAisBearing(it.headingDegrees)!=null}
        vessels.retain(ships.mapTo(mutableSetOf()){it.id},scene);neutral.retain(symbols.mapTo(mutableSetOf()){it.id},scene)
        val moreShips=vessels.growFor(ships.size,loader);val moreSymbols=neutral.growFor(symbols.size,loader)
        growing=moreShips||moreSymbols
        val transforms=engine.transformManager
        for(target in ships+symbols){
            val pool=if(target.kind==AisSceneKind.VESSEL&&validAisBearing(target.headingDegrees)!=null)vessels else neutral
            val instance=pool.acquire(target.id)?:continue
            val matrix=aisDisplayGeometry(target,frame,viewportWidth,density).matrix()
            if(pool.transformChanged(instance,matrix))transforms.setTransform(transforms.getInstance(instance.root),matrix)
            val paint=when{target.stale||target.lost->"old";target.id==AisTrafficRenderer3D.OWN_ID->"own";target.risk->"risk";target.id==selectedId->"selected";else->"target"}
            val form=if(pool===vessels)target.form else AisVesselForm.GENERIC
            if(pool.paintChanged(instance,"$paint:${form.name}"))paint(pool.entities(instance,form),paint,if(pool===vessels)"vessel"else"symbol")
            pool.show(instance,form,scene)
        }
        presentedIds=(vessels.assigned.keys+neutral.assigned.keys).filterNot{it==AisTrafficRenderer3D.OWN_ID}.toSet()
    }
    private fun paint(entities:IntArray,key:String,kind:String){
        val manager=engine.renderableManager
        for(entity in entities){
            val renderable=manager.getInstance(entity);if(renderable==0)continue
            for(primitive in 0 until manager.getPrimitiveCount(renderable)){
                originals.getOrPut(entity to primitive){manager.getMaterialInstanceAt(renderable,primitive)}
                val material=materials.getOrPut("$kind:$key:$primitive"){
                    MaterialInstance.duplicate(manager.getMaterialInstanceAt(renderable,primitive),"traffic-$key").apply{
                        if(getMaterial().hasParameter("baseColorFactor")){
                            val color=when(key){"old"->floatArrayOf(.25f,.25f,.25f);"own","selected"->floatArrayOf(.98f,.98f,.98f);"risk"->floatArrayOf(1f,.27f,.10f);else->floatArrayOf(.6f,.6f,.6f)}
                            val shade=when(primitive){1->.86f;2->.10f;3->.52f;else->1f}
                            if(primitive==2)setParameter("baseColorFactor",shade,shade,shade,1f)
                            else setParameter("baseColorFactor",color[0]*shade,color[1]*shade,color[2]*shade,1f)
                        }
                    }
                }
                manager.setMaterialInstanceAt(renderable,primitive,material)
            }
        }
    }
    fun close(){
        vessels.retain(emptySet(),scene);neutral.retain(emptySet(),scene)
        val manager=engine.renderableManager
        originals.forEach{(key,source)->val instance=manager.getInstance(key.first);if(instance!=0)manager.setMaterialInstanceAt(instance,key.second,source)}
        originals.clear()
        materials.values.forEach{runCatching{engine.destroyMaterialInstance(it)}};materials.clear();presentedIds=emptySet()
    }
}
