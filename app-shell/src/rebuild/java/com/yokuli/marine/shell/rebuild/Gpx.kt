package com.yokuli.marine.shell.rebuild

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader

object Gpx {
    private const val YOKULI_NAMESPACE="https://yokuli.app/gpx/1"
    data class Contents(val places:List<Place>,val routes:List<Route>)
    fun read(input:InputStream):Contents {
        val bytes=input.readNBytesCompat(8*1024*1024+1)
        require(bytes.size<=8*1024*1024) {"large"}
        val text=bytes.toString(Charsets.UTF_8)
        require(!text.contains("<!DOCTYPE",true) && !text.contains("<!ENTITY",true)) {"xml"}
        val parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);parser.setInput(StringReader(text))
        val places=mutableListOf<Place>();val routes=mutableListOf<Route>();var points=mutableListOf<GeoPoint>()
        var routeName="";var markName="";var note="";var mark:GeoPoint?=null;var inRoute=false;var root=false;var count=0
        var kind=PlaceKind.MARK;var collection=""
        while(parser.eventType!=XmlPullParser.END_DOCUMENT) {
            if(parser.eventType==XmlPullParser.START_TAG) when(parser.name) {
                "gpx"->root=true
                "rte","trk"->{inRoute=true;points=mutableListOf();routeName=""}
                "wpt","rtept","trkpt"->{
                    require(++count<=15000) {"large"}
                    val p=GeoPoint(parser.getAttributeValue(null,"lat").toDouble(),parser.getAttributeValue(null,"lon").toDouble());require(p.valid()) {"coordinate"}
                    if(parser.name=="wpt") {mark=p;markName="";note="";kind=PlaceKind.MARK;collection=""} else points.add(p)
                }
                "name"->{val value=parser.nextText().take(100);if(mark!=null) markName=value else if(inRoute && points.isEmpty()) routeName=value}
                "desc"->{val value=parser.nextText().take(20000);if(mark!=null) note=value}
                "type"->if(mark!=null) { kind=runCatching { PlaceKind.valueOf(parser.nextText().uppercase(java.util.Locale.ROOT)) }.getOrDefault(PlaceKind.MARK) }
                "collection"->if(mark!=null && parser.namespace==YOKULI_NAMESPACE) collection=parser.nextText().take(80)
            }
            if(parser.eventType==XmlPullParser.END_TAG) when(parser.name) {
                "wpt"->{mark?.let { places+=Place(name=markName.ifBlank {"GPX ${places.size+1}"},point=it,note=note,kind=kind,collection=collection) };mark=null}
                "rte","trk"->{if(points.size>=2) routes+=Route(name=routeName.ifBlank {"GPX ${routes.size+1}"},points=points.toList());inRoute=false}
            }
            parser.next()
        }
        require(root && (places.isNotEmpty() || routes.isNotEmpty())) {"empty"}
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
        routes.forEach {route ->x.startTag(null,"rte");element("name",route.name);route.points.forEach {point("rtept",it)};x.endTag(null,"rte")}
        x.endTag(null,"gpx");x.endDocument();x.flush()
    }
}
