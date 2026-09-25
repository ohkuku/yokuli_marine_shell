package com.yokuli.marine.shell.rebuild

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader

object Gpx {
    private const val YOKULI_NAMESPACE="https://yokuli.app/gpx/1"
    private val GPX_NAMESPACES=setOf("", "http://www.topografix.com/GPX/1/0", "http://www.topografix.com/GPX/1/1")

    /**
     * 当前可一次性导入的收藏资料：wpt 是坐标，rte 是用户规划的航线。
     * routes 绝不接收实际航迹 trk；未来支持航迹时必须独立保存 trkseg 段及其时间，
     * 并增加对应的导入结果与用户入口，不能把段首尾补线后塞进 Route。
     */
    data class Contents(val places:List<Place>,val routes:List<Route>)

    /** 包含实际航迹时整份文件拒绝，调用方须解释原因；混合文件也不部分导入。 */
    class UnsupportedTracks : IllegalArgumentException("GPX tracks are not supported by the places and planned routes importer")

    fun read(input:InputStream):Contents {
        val bytes=input.readNBytesCompat(8*1024*1024+1)
        require(bytes.size<=8*1024*1024) {"large"}
        val text=bytes.toString(Charsets.UTF_8)
        require(!text.contains("<!DOCTYPE",true) && !text.contains("<!ENTITY",true)) {"xml"}
        val parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);parser.setInput(StringReader(text))
        val places=mutableListOf<Place>();val routes=mutableListOf<Route>();var points=mutableListOf<GeoPoint>()
        var routeName="";var markName="";var note="";var mark:GeoPoint?=null;var count=0
        var rootNamespace:String?=null;var rootDepth=0;var routeDepth=0;var markDepth=0
        var kind=PlaceKind.MARK;var collection=""
        var navigationTargets:List<Int>?=null
        fun readPoint():GeoPoint {
            require(++count<=15000) {"large"}
            return GeoPoint(parser.getAttributeValue(null,"lat").toDouble(),parser.getAttributeValue(null,"lon").toDouble())
                .also {require(it.valid()) {"coordinate"}}
        }
        while(parser.eventType!=XmlPullParser.END_DOCUMENT) {
            if(parser.eventType==XmlPullParser.START_TAG) {
                if(rootNamespace==null) {
                    require(parser.name=="gpx" && parser.namespace.orEmpty() in GPX_NAMESPACES) {"xml"}
                    rootNamespace=parser.namespace.orEmpty();rootDepth=parser.depth
                }
                // 先完整解析才返回 Contents；即使 trk 位于合法坐标/航线之后，也不会提交前半份资料。
                if(parser.name=="trk" && parser.namespace.orEmpty() in GPX_NAMESPACES) throw UnsupportedTracks()
                if(parser.namespace.orEmpty()==rootNamespace) when(parser.name) {
                    "rte"->if(parser.depth==rootDepth+1) {routeDepth=parser.depth;points=mutableListOf();routeName="";navigationTargets=null}
                    "wpt"->if(parser.depth==rootDepth+1) {
                        mark=readPoint();markDepth=parser.depth;markName="";note="";kind=PlaceKind.MARK;collection=""
                    }
                    "rtept"->if(routeDepth>0 && parser.depth==routeDepth+1) {points.add(readPoint())}
                    "name"->when {
                        mark!=null && parser.depth==markDepth+1 -> markName=parser.nextText().take(100)
                        routeDepth>0 && parser.depth==routeDepth+1 -> routeName=parser.nextText().take(100)
                    }
                    "desc"->if(mark!=null && parser.depth==markDepth+1) note=parser.nextText().take(20000)
                    "type"->if(mark!=null && parser.depth==markDepth+1) {
                        val value=parser.nextText().uppercase(java.util.Locale.ROOT)
                        kind=runCatching {PlaceKind.valueOf(value)}.getOrDefault(PlaceKind.MARK)
                    }
                }
                if(routeDepth>0 && parser.name=="navigationTargets" && parser.namespace==YOKULI_NAMESPACE && parser.depth==routeDepth+2) {
                    require(navigationTargets==null) {"duplicate-navigation-targets"}
                    navigationTargets=parser.nextText().split(',').map {it.trim().toInt()}
                }
                if(mark!=null && parser.name=="collection" && parser.namespace==YOKULI_NAMESPACE && parser.depth==markDepth+2) {
                    collection=parser.nextText().take(80)
                }
            }
            if(parser.eventType==XmlPullParser.END_TAG && parser.namespace.orEmpty()==rootNamespace) when(parser.name) {
                "wpt"->if(parser.depth==markDepth) {
                    mark?.let { places+=Place(name=markName.ifBlank {"GPX ${places.size+1}"},point=it,note=note,kind=kind,collection=collection) }
                    mark=null;markDepth=0
                }
                "rte"->if(parser.depth==routeDepth) {
                    navigationTargets?.let {targets->require(targets.isNotEmpty()&&targets==targets.distinct().sorted()&&targets.all {it in points.indices}&&targets.last()==points.lastIndex) {"invalid-navigation-targets"}}
                    if(points.size>=2) routes+=Route(name=routeName.ifBlank {"GPX ${routes.size+1}"},points=points.toList(),navigationTargetIndices=navigationTargets)
                    routeDepth=0
                }
            }
            parser.next()
        }
        require(rootNamespace!=null && (places.isNotEmpty() || routes.isNotEmpty())) {"empty"}
        return Contents(places,routes)
    }
    private fun InputStream.readNBytesCompat(max:Int):ByteArray {
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(out.size()<max) {val n=read(buffer,0,minOf(buffer.size,max-out.size()));if(n<0) break;out.write(buffer,0,n)}
        return out.toByteArray()
    }
    fun write(output:OutputStream,places:List<Place>,routes:List<Route>) {
        val x=Xml.newSerializer();x.setOutput(output,"UTF-8");x.startDocument("UTF-8",true)
        x.startTag(null,"gpx").attribute(null,"version","1.1").attribute(null,"creator","Yokuli OS").attribute(null,"xmlns","http://www.topografix.com/GPX/1/1")
        fun element(name:String,text:String) {x.startTag(null,name);x.text(text);x.endTag(null,name)}
        fun point(tag:String,p:GeoPoint,name:String?=null,note:String?=null,place:Place?=null) {
            x.startTag(null,tag).attribute(null,"lat",p.lat.toString()).attribute(null,"lon",p.lon.toString())
            name?.let {element("name",it)};note?.takeIf {it.isNotBlank()}?.let {element("desc",it)}
            place?.let {
                element("type",it.kind.name)
                if(it.collection.isNotBlank()) {
                    x.startTag(null,"extensions");x.setPrefix("yokuli",YOKULI_NAMESPACE)
                    x.startTag(YOKULI_NAMESPACE,"collection").text(it.collection).endTag(YOKULI_NAMESPACE,"collection")
                    x.endTag(null,"extensions")
                }
            }
            x.endTag(null,tag)
        }
        places.forEach {point("wpt",it.point,it.name,it.note,it)}
        routes.forEach {route ->
            x.startTag(null,"rte");element("name",route.name)
            route.navigationTargetIndices?.let {targets->
                x.startTag(null,"extensions");x.setPrefix("yokuli",YOKULI_NAMESPACE)
                x.startTag(YOKULI_NAMESPACE,"navigationTargets").text(targets.joinToString(",")).endTag(YOKULI_NAMESPACE,"navigationTargets")
                x.endTag(null,"extensions")
            }
            route.points.forEach {point("rtept",it)};x.endTag(null,"rte")
        }
        x.endTag(null,"gpx");x.endDocument();x.flush()
    }
}
