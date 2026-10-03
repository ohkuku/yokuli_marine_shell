package com.yokuli.runtime.marine.chart.terrain

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class ChartTerrainVertex(
    val x:Float,val y:Float,val z:Float,
    val normalX:Float=Float.NaN,val normalY:Float=Float.NaN,val normalZ:Float=Float.NaN,
)

data class ChartTerrainMaterial(
    val name:String,val red:Float,val green:Float,val blue:Float,val alpha:Float=1f,
    val roughness:Float=.9f,val metallic:Float=0f,val doubleSided:Boolean=false,
)

/** 自包含 glTF 2.0 / GLB，只含实际网格与 PBR 材质，不创建相机、灯光或外部资源。 */
class ChartTerrainMeshBuilder(private val maxTriangles:Int=32_000) {
    private class Part {
        var values=FloatArray(1_536);private set
        var size=0;private set
        var count=0
        fun add(value:Float) {
            if(size==values.size)values=values.copyOf(values.size*2)
            values[size++]=value
        }
    }
    /** 只合并六个 float 原始位完全相同的顶点；硬边法线、正负零和材质边界各自保留。 */
    private data class VertexBits(val x:Int,val y:Int,val z:Int,val nx:Int,val ny:Int,val nz:Int)
    private data class IndexedPart(val material:ChartTerrainMaterial,val vertices:FloatArray,val indices:IntArray)

    private fun indexed(material:ChartTerrainMaterial,part:Part,check:()->Unit):IndexedPart {
        val cornerCount=part.count*3
        require(part.size==cornerCount*6)
        val vertices=FloatArray(part.size)
        val indices=IntArray(cornerCount)
        val lookup=HashMap<VertexBits,Int>()
        var vertexCount=0
        for(corner in 0 until cornerCount) {
            if(corner%1_024==0)check()
            val offset=corner*6
            val key=VertexBits(part.values[offset].toRawBits(),part.values[offset+1].toRawBits(),part.values[offset+2].toRawBits(),
                part.values[offset+3].toRawBits(),part.values[offset+4].toRawBits(),part.values[offset+5].toRawBits())
            val existing=lookup[key]
            indices[corner]=if(existing!=null)existing else {
                val index=vertexCount++
                lookup[key]=index
                part.values.copyInto(vertices,index*6,offset,offset+6)
                index
            }
        }
        check()
        return IndexedPart(material,vertices.copyOf(vertexCount*6),indices)
    }
    private val parts=linkedMapOf<ChartTerrainMaterial,Part>()
    var triangleCount:Int=0;private set
    var minY:Float=Float.POSITIVE_INFINITY;private set
    var maxY:Float=Float.NEGATIVE_INFINITY;private set
    val full:Boolean get()=triangleCount>=maxTriangles
    val remainingTriangles:Int get()=maxTriangles-triangleCount

    fun triangle(material:ChartTerrainMaterial,a:ChartTerrainVertex,b:ChartTerrainVertex,c:ChartTerrainVertex):Boolean {
        if(full)return false
        if(listOf(a,b,c).any {!it.x.isFinite()||!it.y.isFinite()||!it.z.isFinite()})return false
        val ux=b.x-a.x;val uy=b.y-a.y;val uz=b.z-a.z
        val vx=c.x-a.x;val vy=c.y-a.y;val vz=c.z-a.z
        val nx=uy*vz-uz*vy;val ny=uz*vx-ux*vz;val nz=ux*vy-uy*vx
        val length=sqrt(nx*nx+ny*ny+nz*nz)
        if(!length.isFinite()||length<.00001f)return false
        val part=parts.getOrPut(material){Part()}
        for(vertex in listOf(a,b,c)) {
            part.add(vertex.x);part.add(vertex.y);part.add(vertex.z)
            val supplied=sqrt(vertex.normalX*vertex.normalX+vertex.normalY*vertex.normalY+vertex.normalZ*vertex.normalZ)
            if(supplied.isFinite()&&supplied>.00001f) {
                part.add(vertex.normalX/supplied);part.add(vertex.normalY/supplied);part.add(vertex.normalZ/supplied)
            }else {part.add(nx/length);part.add(ny/length);part.add(nz/length)}
            minY=min(minY,vertex.y);maxY=max(maxY,vertex.y)
        }
        part.count++;triangleCount++
        return true
    }

    /** 设施符号/导航框的几何，尺寸由调用者标记为示意，不冒充资料里的实物尺寸。 */
    fun box(material:ChartTerrainMaterial,centerX:Float,baseY:Float,centerZ:Float,width:Float,height:Float,depth:Float):Boolean {
        if(triangleCount+12>maxTriangles||listOf(centerX,baseY,centerZ,width,height,depth).any {!it.isFinite()}||width<=0||height<=0||depth<=0)return false
        val x=centerX-width/2;val z=centerZ-depth/2
        val p=arrayOf(
            ChartTerrainVertex(x,baseY,z),ChartTerrainVertex(x+width,baseY,z),
            ChartTerrainVertex(x+width,baseY,z+depth),ChartTerrainVertex(x,baseY,z+depth),
            ChartTerrainVertex(x,baseY+height,z),ChartTerrainVertex(x+width,baseY+height,z),
            ChartTerrainVertex(x+width,baseY+height,z+depth),ChartTerrainVertex(x,baseY+height,z+depth),
        )
        val faces=arrayOf(intArrayOf(4,7,6,5),intArrayOf(0,1,2,3),intArrayOf(0,4,5,1),intArrayOf(3,2,6,7),intArrayOf(0,3,7,4),intArrayOf(1,5,6,2))
        for(face in faces){triangle(material,p[face[0]],p[face[1]],p[face[2]]);triangle(material,p[face[0]],p[face[2]],p[face[3]])}
        return true
    }

    fun glb(check:()->Unit={}):ByteArray? {
        if(triangleCount==0)return null
        val views=JSONArray();val accessors=JSONArray();val materials=JSONArray();val primitives=JSONArray()
        // 精确索引化只减少重复存储和上传，不改变坐标、法线、三角形顺序或原始资料精度。
        val indexedParts=parts.map {(material,part)->check();indexed(material,part,check)}
        val bytes=indexedParts.sumOf{it.vertices.size.toLong()*4+it.indices.size.toLong()*4}
        require(bytes<=Int.MAX_VALUE){"CHART_TERRAIN_MODEL_LIMIT"}
        val byteCount=bytes.toInt()
        val bin=ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        fun accessor(view:Int,offset:Int,count:Int,type:String,component:Int,min:FloatArray?=null,max:FloatArray?=null):Int {
            val id=accessors.length()
            val item=JSONObject().put("bufferView",view).put("byteOffset",offset).put("componentType",component).put("count",count).put("type",type)
            min?.let {item.put("min",JSONArray(it.map(Float::toDouble)))};max?.let {item.put("max",JSONArray(it.map(Float::toDouble)))}
            accessors.put(item);return id
        }
        for(part in indexedParts) {
            check()
            val material=part.material
            val materialIndex=materials.length()
            val color=JSONArray(listOf(material.red,material.green,material.blue,material.alpha).map {it.coerceIn(0f,1f).toDouble()})
            val spec=JSONObject().put("name",material.name).put("doubleSided",material.doubleSided)
                .put("pbrMetallicRoughness",JSONObject().put("baseColorFactor",color).put("metallicFactor",material.metallic.coerceIn(0f,1f).toDouble()).put("roughnessFactor",material.roughness.coerceIn(.05f,1f).toDouble()))
            if(material.alpha<1f)spec.put("alphaMode","BLEND")
            materials.put(spec)
            val vertexCount=part.vertices.size/6;val start=bin.position()
            val low=floatArrayOf(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY)
            val high=floatArrayOf(Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY)
            for(index in part.vertices.indices) {
                if(index%6_144==0)check()
                val value=part.vertices[index]
                bin.putFloat(value)
                val component=index%6
                if(component<3){low[component]=min(low[component],value);high[component]=max(high[component],value)}
            }
            val vertexView=views.length();views.put(JSONObject().put("buffer",0).put("byteOffset",start).put("byteLength",vertexCount*24).put("byteStride",24).put("target",34962))
            val positions=accessor(vertexView,0,vertexCount,"VEC3",5126,low,high)
            val normals=accessor(vertexView,12,vertexCount,"VEC3",5126)
            val indexStart=bin.position()
            for(index in part.indices.indices){if(index%6_144==0)check();bin.putInt(part.indices[index])}
            val indexView=views.length();views.put(JSONObject().put("buffer",0).put("byteOffset",indexStart).put("byteLength",part.indices.size*4).put("target",34963))
            val indices=accessor(indexView,0,part.indices.size,"SCALAR",5125)
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
