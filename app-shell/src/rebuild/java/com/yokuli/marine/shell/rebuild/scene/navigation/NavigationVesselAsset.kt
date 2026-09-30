package com.yokuli.marine.shell.rebuild.scene.navigation

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原船体资产的语义坐标为 X 右舷、Y 上、Z 船艏；海图米制世界的前方为 -Z。
 * 加载一次时反射 Z，并同步反射法线/包围盒、反转三角形绕序。不能仅负缩放后
 * 留下反面剔除或把右舷绿灯/横倾放到左舷。原资产及仪表中的姿态约定不变。
 */
internal fun navigationVesselAsset(source:ByteArray):ByteArray {
    val bytes=source.copyOf();val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    require(buffer.getInt(0)==0x46546c67&&buffer.getInt(4)==2){"NAVIGATION_VESSEL_FORMAT"}
    val jsonLength=buffer.getInt(12)
    require(buffer.getInt(16)==0x4e4f534a){"NAVIGATION_VESSEL_FORMAT"}
    val document=JSONObject(String(bytes,20,jsonLength,Charsets.UTF_8))
    val binaryHeader=20+jsonLength
    require(buffer.getInt(binaryHeader+4)==0x004e4942){"NAVIGATION_VESSEL_FORMAT"}
    val binaryLength=buffer.getInt(binaryHeader)
    val binaryOffset=binaryHeader+8
    val accessors=document.getJSONArray("accessors");val views=document.getJSONArray("bufferViews")
    fun accessor(index:Int)=accessors.getJSONObject(index)
    fun offset(index:Int):Pair<Int,Int> {
        val a=accessor(index);val v=views.getJSONObject(a.getInt("bufferView"))
        return binaryOffset+v.optInt("byteOffset",0)+a.optInt("byteOffset",0) to v.optInt("byteStride",0)
    }
    val reflected=mutableSetOf<Int>();val reversed=mutableSetOf<Int>()
    fun reflect(index:Int){
        if(!reflected.add(index))return
        val a=accessor(index)
        require(a.getInt("componentType")==5126&&a.getString("type")=="VEC3"&&!a.has("sparse")){"NAVIGATION_VESSEL_FORMAT"}
        val (base,strideValue)=offset(index);val stride=if(strideValue>0)strideValue else 12
        repeat(a.getInt("count")){i->val at=base+i*stride+8;buffer.putFloat(at,-buffer.getFloat(at))}
        val low=a.optJSONArray("min");val high=a.optJSONArray("max")
        if(low!=null&&high!=null){val lower=low.getDouble(2);low.put(2,-high.getDouble(2));high.put(2,-lower)}
    }
    fun reverse(index:Int){
        if(!reversed.add(index))return
        val a=accessor(index);val component=a.getInt("componentType")
        require(component==5123||component==5125){"NAVIGATION_VESSEL_FORMAT"}
        val width=if(component==5123)2 else 4;val(base,strideValue)=offset(index)
        val stride=if(strideValue>0)strideValue else width
        require(a.getInt("count")%3==0){"NAVIGATION_VESSEL_FORMAT"}
        for(i in 0 until a.getInt("count") step 3){
            val b=base+(i+1)*stride;val c=base+(i+2)*stride
            if(width==2){val value=buffer.getShort(b);buffer.putShort(b,buffer.getShort(c));buffer.putShort(c,value)}
            else{val value=buffer.getInt(b);buffer.putInt(b,buffer.getInt(c));buffer.putInt(c,value)}
        }
    }
    val meshes=document.getJSONArray("meshes")
    repeat(meshes.length()){m->val primitives=meshes.getJSONObject(m).getJSONArray("primitives")
        repeat(primitives.length()){p->val primitive=primitives.getJSONObject(p)
            require(primitive.optInt("mode",4)==4){"NAVIGATION_VESSEL_FORMAT"}
            val attributes=primitive.getJSONObject("attributes")
            reflect(attributes.getInt("POSITION"));if(attributes.has("NORMAL"))reflect(attributes.getInt("NORMAL"))
            require(!attributes.has("TANGENT")){"NAVIGATION_VESSEL_FORMAT"}
            reverse(primitive.getInt("indices"))
        }
    }
    val nodes=document.getJSONArray("nodes")
    repeat(nodes.length()){i->val node=nodes.getJSONObject(i)
        require(!node.has("rotation")&&!node.has("matrix")){"NAVIGATION_VESSEL_FORMAT"}
        node.optJSONArray("translation")?.let{it.put(2,-it.getDouble(2))}
    }
    val json=document.toString().toByteArray(Charsets.UTF_8);val padded=(json.size+3)/4*4
    val result=ByteBuffer.allocate(12+8+padded+8+binaryLength).order(ByteOrder.LITTLE_ENDIAN)
    result.putInt(0x46546c67).putInt(2).putInt(result.capacity()).putInt(padded).putInt(0x4e4f534a).put(json)
    repeat(padded-json.size){result.put(0x20.toByte())}
    result.putInt(binaryLength).putInt(0x004e4942).put(bytes,binaryOffset,binaryLength)
    return result.array()
}
