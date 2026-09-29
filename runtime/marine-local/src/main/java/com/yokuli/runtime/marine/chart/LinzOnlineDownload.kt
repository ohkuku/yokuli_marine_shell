package com.yokuli.runtime.marine.chart

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yokuli.runtime.contract.chart.ChartBounds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.locationtech.jts.geom.*
import org.locationtech.jts.io.WKBWriter
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * LINZ WFS 提供水文要素，路线仍由本机计算。完整分页区域转为标准 GeoPackage，
 * 再交给既有 LINZ 语义/坐标/空洞/索引导入链；超预算或任何一层失败均不发布半包。
 */
internal object LinzOnlineDownload {
    private const val BAND="1:22k - 1:90k"
    private val classes=setOf("DEPARE","DRGARE","LNDARE","UWTROC","WRECKS","OBSTRN","RESARE","BRIDGE","CBLOHD")
    private data class Layer(val id:String,val definition:LinzLdsAdapter.Layer)
    private val factory=GeometryFactory()

    fun validate(bounds:ChartBounds) {
        require(bounds.valid&&bounds.west<bounds.east&&bounds.north-bounds.south in .0001..1.8&&bounds.east-bounds.west in .0001..2.4){"LINZ_AREA_TOO_LARGE"}
    }
    suspend fun download(key:String,bounds:ChartBounds,file:File,progress:suspend (Int,Int,String)->Unit) {
        validate(bounds)
        require(key.isNotBlank()){ "LINZ_KEY_REQUIRED" }
        val work=currentCoroutineContext()
        fun check(){work.ensureActive()}
        var totalBytes=0L
        fun get(parameters:Map<String,String>):String {
            check()
            val query=parameters.entries.joinToString("&"){URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8")}
            val connection=URL("https://data.linz.govt.nz/services;key=$key/wfs?$query").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout=10_000;connection.readTimeout=15_000;connection.instanceFollowRedirects=false
                connection.setRequestProperty("User-Agent","YokuliOS/1.0")
                val code=connection.responseCode
                require(code in 200..299){if(code==401||code==403)"LINZ_KEY_REJECTED"else "LINZ_HTTP_$code"}
                return connection.inputStream.use { input->
                    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(16_384)
                    while(true) {
                        check();val count=input.read(buffer);if(count<0)break
                        totalBytes+=count
                        require(output.size()+count<=16_000_000&&totalBytes<=128_000_000){"LINZ_AREA_TOO_COMPLEX"}
                        output.write(buffer,0,count)
                    }
                    output.toString("UTF-8")
                }
            } catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(error:Exception) {
                // URL 内含用户密钥，网络库异常文本绝不能直接发布进状态、通知或日志。
                if(error.message?.startsWith("LINZ_")==true)throw error
                throw java.io.IOException("LINZ_NETWORK_UNAVAILABLE")
            } finally {connection.disconnect()}
        }
        progress(0,1,"LINZ · catalogue")
        val xml=get(mapOf("service" to "WFS","version" to "2.0.0","request" to "GetCapabilities"))
        require(!xml.contains("<!DOCTYPE",true)&&!xml.contains("<!ENTITY",true)){"LINZ_INVALID_CATALOGUE"}
        val document=DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware=true;isXIncludeAware=false;isExpandEntityReferences=false
            runCatching{setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)}
            runCatching{setFeature("http://xml.org/sax/features/external-general-entities",false)}
            runCatching{setFeature("http://xml.org/sax/features/external-parameter-entities",false)}
        }.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val types=document.getElementsByTagNameNS("*","FeatureType")
        val layers=buildList {
            for(i in 0 until types.length) {
                check();val item=types.item(i) as? Element?:continue
                val title=item.getElementsByTagNameNS("*","Title").item(0)?.textContent?:continue
                val definition=LinzLdsAdapter.recognizeTitle(title)?:continue
                if(definition.acronym !in classes||!title.contains(BAND))continue
                val id=item.getElementsByTagNameNS("*","Name").item(0)?.textContent?.substringAfterLast(':')?:continue
                require(id.matches(Regex("layer-[0-9]+"))){"LINZ_INVALID_LAYER"}
                add(Layer(id,definition))
            }
        }.distinctBy{it.id}.sortedBy{it.id}
        require(layers.size in 2..32&&layers.any{it.definition.acronym=="DEPARE"}&&layers.any{it.definition.acronym=="LNDARE"}){"LINZ_HYDRO_LAYERS_UNAVAILABLE"}
        SQLiteDatabase.openOrCreateDatabase(file,null).use {db->
            db.execSQL("PRAGMA application_id=1196444487");db.execSQL("PRAGMA user_version=10300")
            db.execSQL("CREATE TABLE gpkg_spatial_ref_sys(srs_name TEXT,srs_id INTEGER PRIMARY KEY,organization TEXT,organization_coordsys_id INTEGER,definition TEXT,description TEXT)")
            db.execSQL("INSERT INTO gpkg_spatial_ref_sys VALUES('WGS 84',4326,'EPSG',4326,'EPSG:4326','Longitude/latitude WGS 84')")
            db.execSQL("CREATE TABLE gpkg_contents(table_name TEXT PRIMARY KEY,data_type TEXT,identifier TEXT,description TEXT,last_change TEXT,min_x REAL,min_y REAL,max_x REAL,max_y REAL,srs_id INTEGER)")
            db.execSQL("CREATE TABLE gpkg_geometry_columns(table_name TEXT,column_name TEXT,geometry_type_name TEXT,srs_id INTEGER,z INTEGER,m INTEGER)")
            val writer=WKBWriter(2,org.locationtech.jts.io.ByteOrderValues.LITTLE_ENDIAN)
            var totalFeatures=0;var depthFeatures=0;var vertices=0L
            val uniqueIds=HashSet<String>()
            db.beginTransaction()
            try {
                for((layerIndex,layer) in layers.withIndex()) {
                    var start=0;var declaredTotal:Long?=null;var columns:Set<String>?=null
                    val table=layer.id.replace('-','_')
                    while(true) {
                        check();progress(layerIndex,layers.size,layer.definition.title)
                        // WFS 2 的 BBOX 按 EPSG:4326 的纬/经顺序；GeoJSON 几何使用 WGS84 经/纬。
                        // 与原 LINZ 客户端一致明确请求 EPSG:4326，逐坐标验证，不猜测交换轴。
                        val bbox=String.format(Locale.ROOT,"%.9f,%.9f,%.9f,%.9f,urn:ogc:def:crs:EPSG::4326",bounds.south,bounds.west,bounds.north,bounds.east)
                        val response=get(mapOf("service" to "WFS","version" to "2.0.0","request" to "GetFeature",
                            "typeNames" to layer.id,"outputFormat" to "json","srsName" to "EPSG:4326",
                            "bbox" to bbox,"count" to "500","startIndex" to start.toString(),"sortBy" to "fidn"))
                        val root=runCatching{JsonParser.parseString(response).asJsonObject}.getOrElse{error("LINZ_INVALID_RESPONSE")}
                        require(root.get("type")?.asString=="FeatureCollection"){"LINZ_INVALID_RESPONSE"}
                        val crs=root.getAsJsonObject("crs")?.getAsJsonObject("properties")?.get("name")?.asString
                        require(crs==null||crs.contains("CRS84",true)||crs.endsWith("4326")){"LINZ_UNSUPPORTED_CRS"}
                        val features=root.getAsJsonArray("features")?:error("LINZ_INVALID_RESPONSE")
                        val matched=root.get("numberMatched")?.asString?.toLongOrNull()?:root.get("totalFeatures")?.asString?.toLongOrNull()
                        if(matched!=null) {
                            require(matched in 0..40_000&&(declaredTotal==null||declaredTotal==matched)){"LINZ_SOURCE_CHANGED"}
                            declaredTotal=matched
                        }
                        for(raw in features) {
                            check();val item=raw.asJsonObject
                            val props=item.getAsJsonObject("properties")?:error("LINZ_SCHEMA_MISSING")
                            val attrs=props.keySet().toSet()
                            require(attrs.size<=100&&attrs.all{it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))&&it !in setOf("fid","geom")}){"LINZ_SCHEMA_INVALID"}
                            if(columns==null) {
                                LinzLdsAdapter.recognize(table,layer.definition.title,attrs.toList(),"GEOMETRY")?:error("LINZ_SCHEMA_MISSING")
                                columns=attrs
                                db.execSQL("CREATE TABLE \"$table\"(fid INTEGER PRIMARY KEY,geom BLOB,"+attrs.joinToString(","){"\"$it\" TEXT"}+")")
                                db.execSQL("INSERT INTO gpkg_contents VALUES(?,?,?,?,?,?,?,?,?,4326)",arrayOf(table,"features",layer.definition.title,"LINZ LDS reference data; not corrected for Notices to Mariners",Instant.now().toString(),bounds.west,bounds.south,bounds.east,bounds.north))
                                db.execSQL("INSERT INTO gpkg_geometry_columns VALUES(?,'geom','GEOMETRY',4326,0,0)",arrayOf(table))
                            }
                            require(attrs==columns){"LINZ_SOURCE_CHANGED"}
                            val identity=item.get("id")?.asString?:props.get("fidn")?.asString?:error("LINZ_FEATURE_ID_MISSING")
                            require(uniqueIds.add(layer.id+":"+identity)){"LINZ_PAGING_REPEATED"}
                            val sourceShape=geometry(item.getAsJsonObject("geometry")?:error("LINZ_GEOMETRY_MISSING"))
                            // WFS 返回与 BBOX 相交的完整对象；覆盖只能取本次所有危险图层均完整下载的区域。
                            val shape=if(layer.definition.acronym in setOf("DEPARE","DRGARE"))sourceShape.intersection(
                                factory.toGeometry(Envelope(bounds.west,bounds.east,bounds.south,bounds.north)))else sourceShape
                            if(shape.isEmpty)continue
                            require(!shape.isEmpty&&shape.isValid){"LINZ_GEOMETRY_INVALID"}
                            vertices+=shape.numPoints;totalFeatures++
                            require(totalFeatures<=80_000&&vertices<=3_000_000){"LINZ_AREA_TOO_COMPLEX"}
                            if(layer.definition.acronym=="DEPARE")depthFeatures++
                            val header=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).put('G'.code.toByte()).put('P'.code.toByte()).put(0).put(1).putInt(4326).array()
                            val values=ContentValues().apply {
                                put("fid",totalFeatures);put("geom",header+writer.write(shape))
                                props.entrySet().forEach{(name,value)->if(value.isJsonNull)putNull(name)else {
                                    val text=if(value.isJsonPrimitive)value.asString else value.toString()
                                    require(text.length<=8192){"LINZ_ATTRIBUTE_TOO_LARGE"};put(name,text)
                                }}
                            }
                            db.insertOrThrow(table,null,values)
                        }
                        start+=features.size()
                        require(declaredTotal==null||start<=declaredTotal!!){"LINZ_SOURCE_CHANGED"}
                        if(declaredTotal!=null&&start.toLong()==declaredTotal)break
                        if(features.size()==0) {require(declaredTotal==null){"LINZ_INCOMPLETE_REGION"};break}
                        if(declaredTotal==null&&features.size()<500)break
                        require(start<=40_000){"LINZ_AREA_TOO_COMPLEX"}
                    }
                }
                require(depthFeatures>0){"LINZ_NO_HYDRO_COVERAGE"}
                check();db.setTransactionSuccessful()
            } finally {db.endTransaction()}
        }
    }

    private fun geometry(json:JsonObject):Geometry {
        val coords=json.getAsJsonArray("coordinates")?:error("LINZ_GEOMETRY_INVALID")
        fun point(a:JsonArray):Coordinate {
            require(a.size()>=2){"LINZ_GEOMETRY_INVALID"}
            val x=a[0].asDouble;val y=a[1].asDouble
            require(x.isFinite()&&y.isFinite()&&x in -180.0..180.0&&y in -90.0..90.0){"LINZ_COORDINATES_INVALID"}
            return Coordinate(x,y)
        }
        fun line(a:JsonArray)=a.map{point(it.asJsonArray)}.toTypedArray().also{require(it.size in 2..300_000){"LINZ_GEOMETRY_INVALID"}}
        fun polygon(a:JsonArray):Polygon {
            require(a.size()>0){"LINZ_GEOMETRY_INVALID"}
            val rings=a.map{factory.createLinearRing(line(it.asJsonArray))}
            return factory.createPolygon(rings.first(),rings.drop(1).toTypedArray())
        }
        return when(json.get("type")?.asString) {
            "Point"->factory.createPoint(point(coords))
            "MultiPoint"->factory.createMultiPointFromCoords(line(coords))
            "LineString"->factory.createLineString(line(coords))
            "MultiLineString"->factory.createMultiLineString(coords.map{factory.createLineString(line(it.asJsonArray))}.toTypedArray())
            "Polygon"->polygon(coords)
            "MultiPolygon"->factory.createMultiPolygon(coords.map{polygon(it.asJsonArray)}.toTypedArray())
            else->error("LINZ_GEOMETRY_UNSUPPORTED")
        }
    }
}
