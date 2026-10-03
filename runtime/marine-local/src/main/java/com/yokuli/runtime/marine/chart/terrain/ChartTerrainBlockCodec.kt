package com.yokuli.runtime.marine.chart.terrain

import com.google.gson.Gson
import org.json.JSONObject
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** 同一二进制用于 Core 持久库、原生资料包和只读 FD；不经 JSON 传输网格字节。 */
object ChartTerrainBlockCodec {
    const val SCHEMA=1
    const val MAX_BYTES=12*1024*1024
    private const val MAGIC=0x59544d31
    private val gson=Gson()
    fun encode(tile:ChartTerrainTile):ByteArray {
        val metadata=gson.toJson(tile.copy(surfaceGlb=null,seabedGlb=null)).toByteArray(Charsets.UTF_8)
        val surface=tile.surfaceGlb?:ByteArray(0);val seabed=tile.seabedGlb?:ByteArray(0)
        require(metadata.size in 1..2*1024*1024&&surface.size<=8*1024*1024&&seabed.size<=8*1024*1024&&metadata.size.toLong()+surface.size+seabed.size+52<=MAX_BYTES){"CHART_TERRAIN_MODEL_LIMIT"}
        val checksum=MessageDigest.getInstance("SHA-256").apply{update(metadata);update(surface);update(seabed)}.digest()
        val bytes=ByteArrayOutputStream(metadata.size+surface.size+seabed.size+52)
        DataOutputStream(bytes).use {out->out.writeInt(MAGIC);out.writeInt(SCHEMA);out.writeInt(metadata.size);out.writeInt(surface.size);out.writeInt(seabed.size)
            out.write(checksum);out.write(metadata);out.write(surface);out.write(seabed)}
        return bytes.toByteArray()
    }
    fun decode(bytes:ByteArray,expectedKey:String?=null):ChartTerrainTile {
        require(bytes.size in 53..MAX_BYTES){"CHART_TERRAIN_PRODUCT_INVALID"}
        DataInputStream(ByteArrayInputStream(bytes)).use {input->
            require(input.readInt()==MAGIC&&input.readInt()==SCHEMA){"CHART_TERRAIN_PRODUCT_SCHEMA"}
            val m=input.readInt();val s=input.readInt();val b=input.readInt()
            require(m in 1..2*1024*1024&&s in 0..8*1024*1024&&b in 0..8*1024*1024&&m.toLong()+s+b+52==bytes.size.toLong()){"CHART_TERRAIN_PRODUCT_INVALID"}
            val checksum=ByteArray(32).also(input::readFully)
            val metadata=ByteArray(m).also(input::readFully);val surface=ByteArray(s).also(input::readFully);val seabed=ByteArray(b).also(input::readFully)
            val actual=MessageDigest.getInstance("SHA-256").apply{update(metadata);update(surface);update(seabed)}.digest()
            require(MessageDigest.isEqual(checksum,actual)){"CHART_TERRAIN_PRODUCT_CORRUPT"}
            val tile=requireNotNull(gson.fromJson(String(metadata,Charsets.UTF_8),ChartTerrainTile::class.java))
            require((expectedKey==null||tile.key==expectedKey)&&tile.key.matches(Regex("[0-9a-f]{64}"))&&tile.sourceKey.matches(Regex("[0-9a-f]{64}"))&&
                tile.origin.latitude in -90.0..90.0&&tile.origin.longitude in -180.0..180.0&&tile.radiusMeters.isFinite()&&tile.radiusMeters>0&&
                tile.bounds?.valid==true&&tile.lod in 0..1&&tile.markers.size<=256&&tile.sources.size<=8192&&tile.triangleCount in 0..100_000){"CHART_TERRAIN_PRODUCT_INVALID"}
            require(tile.key==hash("${tile.sourceKey}:${tile.bounds}:${tile.lod}".toByteArray(Charsets.UTF_8))){"CHART_TERRAIN_PRODUCT_IDENTITY"}
            require(tile.surfaceGlb==null&&tile.seabedGlb==null&&tile.minElevationMeters.isFinite()&&tile.maxElevationMeters.isFinite()&&
                tile.minElevationMeters<=tile.maxElevationMeters&&tile.coverage.rasterSampleCount>=0&&tile.coverage.missingRasterSamples>=0&&
                tile.coverage.displayedSoundings in 0..256&&tile.coverage.displayedFacilities in 0..256){"CHART_TERRAIN_PRODUCT_INVALID"}
            val sourceIds=tile.sources.mapTo(hashSetOf()){it.id}
            require(tile.sources.all{it.id.isNotBlank()&&it.id.length<=4096&&it.cellId.isNotBlank()&&it.name.length<=16384&&
                (it.resolutionMeters==null||it.resolutionMeters.isFinite()&&it.resolutionMeters>0)}&&
                tile.markers.all{it.sourceId in sourceIds&&it.id.isNotBlank()&&it.id.length<=4096&&it.title.length<=16384&&
                    it.point.latitude in -90.0..90.0&&it.point.longitude in -180.0..180.0&&
                    it.eastMeters.isFinite()&&it.elevationMeters.isFinite()&&it.southMeters.isFinite()&&
                    (it.depthLowerMeters==null||it.depthLowerMeters.isFinite())&&(it.depthUpperMeters==null||it.depthUpperMeters.isFinite())}){"CHART_TERRAIN_PRODUCT_INVALID"}
            for(glb in listOf(surface,seabed))if(glb.isNotEmpty())validateGlb(glb)
            return tile.copy(surfaceGlb=surface.takeIf{it.isNotEmpty()},seabedGlb=seabed.takeIf{it.isNotEmpty()})
        }
    }
    /** 只接受本产品 profile 的自包含网格，不把外部包的任意 glTF 资源交给渲染线程。 */
    private fun validateGlb(glb:ByteArray) {
        require(glb.size>=28){"CHART_TERRAIN_MODEL_INVALID"}
        val bytes=ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.int==0x46546c67&&bytes.int==2&&bytes.int==glb.size){"CHART_TERRAIN_MODEL_INVALID"}
        val jsonSize=bytes.int
        require(jsonSize in 2..512*1024&&jsonSize%4==0&&bytes.int==0x4e4f534a&&20L+jsonSize+8<=glb.size){"CHART_TERRAIN_MODEL_INVALID"}
        val json=ByteArray(jsonSize).also(bytes::get)
        val binSize=bytes.int
        require(binSize>=0&&bytes.int==0x004e4942&&bytes.remaining()==binSize){"CHART_TERRAIN_MODEL_INVALID"}
        val binStart=bytes.position()
        val document=JSONObject(String(json,Charsets.UTF_8))
        require(document.getJSONObject("asset").getString("version")=="2.0"&&
            listOf("images","textures","extensions","extensionsRequired","animations","skins","cameras").none(document::has)&&
            document.getJSONArray("nodes").length()==1&&document.getJSONArray("meshes").length()==1&&
            document.getJSONArray("materials").length() in 1..64){"CHART_TERRAIN_MODEL_INVALID"}
        val buffers=document.getJSONArray("buffers")
        require(buffers.length()==1&&!buffers.getJSONObject(0).has("uri")&&buffers.getJSONObject(0).getInt("byteLength")==binSize){"CHART_TERRAIN_MODEL_INVALID"}
        val views=document.getJSONArray("bufferViews");val accessors=document.getJSONArray("accessors")
        require(views.length() in 1..192&&accessors.length() in 1..192){"CHART_TERRAIN_MODEL_INVALID"}
        for(i in 0 until views.length()) {
            val view=views.getJSONObject(i);val start=view.optInt("byteOffset",0);val length=view.getInt("byteLength")
            require(view.getInt("buffer")==0&&start>=0&&length>=0&&start.toLong()+length<=binSize&&!view.has("extensions")){"CHART_TERRAIN_MODEL_INVALID"}
        }
        for(i in 0 until accessors.length()) {
            val accessor=accessors.getJSONObject(i);val viewId=accessor.getInt("bufferView")
            require(viewId in 0 until views.length()&&!accessor.has("sparse")){"CHART_TERRAIN_MODEL_INVALID"}
            val view=views.getJSONObject(viewId);val count=accessor.getInt("count");val component=accessor.getInt("componentType")
            val width=when(accessor.getString("type")){"SCALAR"->4;"VEC3"->12;else->error("CHART_TERRAIN_MODEL_INVALID")}
            val stride=view.optInt("byteStride",width);val offset=accessor.optInt("byteOffset",0)
            require(count in 1..300_000&&component in setOf(5125,5126)&&stride in width..252&&offset>=0&&
                offset.toLong()+(count-1L)*stride+width<=view.getInt("byteLength")){"CHART_TERRAIN_MODEL_INVALID"}
            if(component==5126) {
                val start=binStart+view.optInt("byteOffset",0)+offset
                for(n in 0 until count)for(c in 0 until width/4){
                    val value=bytes.getFloat(start+n*stride+c*4)
                    require(value.isFinite()&&kotlin.math.abs(value)<=2_000_000f){"CHART_TERRAIN_MODEL_INVALID"}
                }
            }
        }
        val primitives=document.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives")
        require(primitives.length() in 1..64){"CHART_TERRAIN_MODEL_INVALID"}
        for(i in 0 until primitives.length()) {
            val primitive=primitives.getJSONObject(i)
            require(primitive.optInt("mode",4)==4&&!primitive.has("extensions")){"CHART_TERRAIN_MODEL_INVALID"}
            val position=primitive.getJSONObject("attributes").getInt("POSITION");val indices=primitive.getInt("indices")
            require(position in 0 until accessors.length()&&indices in 0 until accessors.length()){ "CHART_TERRAIN_MODEL_INVALID" }
            val index=accessors.getJSONObject(indices);val view=views.getJSONObject(index.getInt("bufferView"))
            require(index.getInt("componentType")==5125&&index.getString("type")=="SCALAR"&&index.getInt("count")%3==0){"CHART_TERRAIN_MODEL_INVALID"}
            val offset=binStart+view.optInt("byteOffset",0)+index.optInt("byteOffset",0)
            val stride=view.optInt("byteStride",4);val vertexCount=accessors.getJSONObject(position).getInt("count")
            for(n in 0 until index.getInt("count"))require(bytes.getInt(offset+n*stride) in 0 until vertexCount){"CHART_TERRAIN_MODEL_INVALID"}
        }
    }
    fun hash(bytes:ByteArray):String=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
}
