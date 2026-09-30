package com.yokuli.marine.shell.rebuild.scene.navigation

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

internal data class NavigationTerrainVertex(
    val x:Float,val y:Float,val z:Float,
    val normalX:Float=Float.NaN,val normalY:Float=Float.NaN,val normalZ:Float=Float.NaN,
)

internal data class NavigationTerrainMaterial(
    val name:String,val red:Float,val green:Float,val blue:Float,val alpha:Float=1f,
    val roughness:Float=.9f,val metallic:Float=0f,val doubleSided:Boolean=false,
)

/** 自包含 glTF 2.0 / GLB，只含实际网格与 PBR 材质，不创建相机、灯光或外部资源。 */
internal class NavigationTerrainMeshBuilder(private val maxTriangles:Int=32_000) {
    private class Part {val values=ArrayList<Float>();var count=0}
    private val parts=linkedMapOf<NavigationTerrainMaterial,Part>()
    var triangleCount:Int=0;private set
    var minY:Float=Float.POSITIVE_INFINITY;private set
    var maxY:Float=Float.NEGATIVE_INFINITY;private set
    val full:Boolean get()=triangleCount>=maxTriangles

    fun triangle(material:NavigationTerrainMaterial,a:NavigationTerrainVertex,b:NavigationTerrainVertex,c:NavigationTerrainVertex):Boolean {
        if(full)return false
        if(listOf(a,b,c).any {!it.x.isFinite()||!it.y.isFinite()||!it.z.isFinite()})return false
        val ux=b.x-a.x;val uy=b.y-a.y;val uz=b.z-a.z
        val vx=c.x-a.x;val vy=c.y-a.y;val vz=c.z-a.z
        val nx=uy*vz-uz*vy;val ny=uz*vx-ux*vz;val nz=ux*vy-uy*vx
        val length=sqrt(nx*nx+ny*ny+nz*nz)
        if(!length.isFinite()||length<.00001f)return false
        val part=parts.getOrPut(material){Part()}
        for(vertex in listOf(a,b,c)) {
            part.values.add(vertex.x);part.values.add(vertex.y);part.values.add(vertex.z)
            val supplied=sqrt(vertex.normalX*vertex.normalX+vertex.normalY*vertex.normalY+vertex.normalZ*vertex.normalZ)
            if(supplied.isFinite()&&supplied>.00001f) {
                part.values.add(vertex.normalX/supplied);part.values.add(vertex.normalY/supplied);part.values.add(vertex.normalZ/supplied)
            }else {part.values.add(nx/length);part.values.add(ny/length);part.values.add(nz/length)}
            minY=min(minY,vertex.y);maxY=max(maxY,vertex.y)
        }
        part.count++;triangleCount++
        return true
    }

    /** 设施符号/导航框的几何，尺寸由调用者标记为示意，不冒充资料里的实物尺寸。 */
    fun box(material:NavigationTerrainMaterial,centerX:Float,baseY:Float,centerZ:Float,width:Float,height:Float,depth:Float):Boolean {
        if(triangleCount+12>maxTriangles||width<=0||height<=0||depth<=0)return false
        val x=centerX-width/2;val z=centerZ-depth/2
        val p=arrayOf(
            NavigationTerrainVertex(x,baseY,z),NavigationTerrainVertex(x+width,baseY,z),
            NavigationTerrainVertex(x+width,baseY,z+depth),NavigationTerrainVertex(x,baseY,z+depth),
            NavigationTerrainVertex(x,baseY+height,z),NavigationTerrainVertex(x+width,baseY+height,z),
            NavigationTerrainVertex(x+width,baseY+height,z+depth),NavigationTerrainVertex(x,baseY+height,z+depth),
        )
        val faces=arrayOf(intArrayOf(4,7,6,5),intArrayOf(0,1,2,3),intArrayOf(0,4,5,1),intArrayOf(3,2,6,7),intArrayOf(0,3,7,4),intArrayOf(1,5,6,2))
        for(face in faces){triangle(material,p[face[0]],p[face[1]],p[face[2]]);triangle(material,p[face[0]],p[face[2]],p[face[3]])}
        return true
    }

    fun glb():ByteArray? {
        if(triangleCount==0)return null
        val views=JSONArray();val accessors=JSONArray();val materials=JSONArray();val primitives=JSONArray()
        // 每顶点 position+normal 24 字节，三角形独立顶点；避免无效共享法线跨越断层/区间边界。
        val byteCount=triangleCount*3*(24+4)
        val bin=ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        fun accessor(view:Int,offset:Int,count:Int,type:String,component:Int,min:FloatArray?=null,max:FloatArray?=null):Int {
            val id=accessors.length()
            val item=JSONObject().put("bufferView",view).put("byteOffset",offset).put("componentType",component).put("count",count).put("type",type)
            min?.let {item.put("min",JSONArray(it.map(Float::toDouble)))};max?.let {item.put("max",JSONArray(it.map(Float::toDouble)))}
            accessors.put(item);return id
        }
        for((material,part) in parts) {
            val materialIndex=materials.length()
            val color=JSONArray(listOf(material.red,material.green,material.blue,material.alpha).map {it.coerceIn(0f,1f).toDouble()})
            val spec=JSONObject().put("name",material.name).put("doubleSided",material.doubleSided)
                .put("pbrMetallicRoughness",JSONObject().put("baseColorFactor",color).put("metallicFactor",material.metallic.coerceIn(0f,1f).toDouble()).put("roughnessFactor",material.roughness.coerceIn(.05f,1f).toDouble()))
            if(material.alpha<1f)spec.put("alphaMode","BLEND")
            materials.put(spec)
            val vertexCount=part.count*3;val start=bin.position()
            val low=floatArrayOf(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY)
            val high=floatArrayOf(Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY)
            part.values.forEachIndexed {index,value->
                bin.putFloat(value)
                val component=index%6
                if(component<3){low[component]=min(low[component],value);high[component]=max(high[component],value)}
            }
            val vertexView=views.length();views.put(JSONObject().put("buffer",0).put("byteOffset",start).put("byteLength",vertexCount*24).put("byteStride",24).put("target",34962))
            val positions=accessor(vertexView,0,vertexCount,"VEC3",5126,low,high)
            val normals=accessor(vertexView,12,vertexCount,"VEC3",5126)
            val indexStart=bin.position();repeat(vertexCount){bin.putInt(it)}
            val indexView=views.length();views.put(JSONObject().put("buffer",0).put("byteOffset",indexStart).put("byteLength",vertexCount*4).put("target",34963))
            val indices=accessor(indexView,0,vertexCount,"SCALAR",5125)
            primitives.put(JSONObject().put("attributes",JSONObject().put("POSITION",positions).put("NORMAL",normals)).put("indices",indices).put("material",materialIndex).put("mode",4))
        }
        val document=JSONObject().put("asset",JSONObject().put("version","2.0").put("generator","yokuli chart terrain"))
            .put("scene",0).put("scenes",JSONArray().put(JSONObject().put("nodes",JSONArray().put(0))))
            .put("nodes",JSONArray().put(JSONObject().put("mesh",0)))
            .put("meshes",JSONArray().put(JSONObject().put("primitives",primitives)))
            .put("buffers",JSONArray().put(JSONObject().put("byteLength",byteCount)))
            .put("bufferViews",views).put("accessors",accessors).put("materials",materials)
        val json=document.toString().toByteArray(Charsets.UTF_8);val jsonSize=(json.size+3)/4*4
        val result=ByteBuffer.allocate(12+8+jsonSize+8+byteCount).order(ByteOrder.LITTLE_ENDIAN)
        result.putInt(0x46546c67).putInt(2).putInt(result.capacity())
        result.putInt(jsonSize).putInt(0x4e4f534a).put(json);repeat(jsonSize-json.size){result.put(0x20.toByte())}
        result.putInt(byteCount).putInt(0x004e4942).put(bin.array())
        return result.array()
    }
}
