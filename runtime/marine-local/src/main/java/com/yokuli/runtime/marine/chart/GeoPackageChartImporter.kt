package com.yokuli.runtime.marine.chart

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.DataInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * 离线 GeoPackage 的有界导入适配器。只写尚未发布的 stage，目录发布、许可、回执仍由 ChartDataService 拥有。
 * 同包的覆盖/水深/障碍图层属于同一个逻辑图幅，避免按表拆图幅后被覆盖优先级互相屏蔽。
 */
internal object GeoPackageChartImporter {
    private const val CELL="GPKG"
    private const val MAX_ROWS=2_000_000L
    private const val MAX_TABLES=256
    private const val MAX_TEXT=8_192
    private const val MAX_ATTRIBUTE_TEXT=64_000
    private const val MAX_TOTAL_VERTICES=20_000_000L
    private const val MAX_COVERAGE_VERTICES=100_000L
    private val geometryTypes=setOf("GEOMETRY","POINT","LINESTRING","POLYGON","MULTIPOINT","MULTILINESTRING","MULTIPOLYGON")
    private data class Column(val name:String,val type:String,val primary:Int)
    private data class Table(val name:String,val geometry:String,val type:String,val srs:Int,val epsg:Int,val z:Int,val m:Int,val primary:String,val attributes:List<String>,val changed:String?)

    /** progress 是已处理对象数/总数；源文件只读，失败由调用方丢弃整个 stage，不能发布半个索引。 */
    suspend fun prepare(file:File,stage:File,datasetId:String,check:()->Unit,
        objectClasses:Map<Int,String> = emptyMap(),
        progress:suspend (done:Int,total:Int,detail:String)->Unit={_,_,_->},
    ):List<ChartCellRevision> = withContext(Dispatchers.IO) {
        check();require(file.isFile&&file.length() in 100..512_000_000L) {"GPKG_FILE_SIZE_LIMIT"}
        require(datasetId.isNotBlank()&&datasetId.length<=256) {"GPKG_DATASET_ID_INVALID"}
        val magic=ByteArray(16).also {bytes->DataInputStream(file.inputStream()).use{it.readFully(bytes)}}
        require(String(magic,StandardCharsets.US_ASCII)=="SQLite format 3\u0000") {"GPKG_NOT_SQLITE"}
        require(stage.isDirectory||stage.mkdirs()) {"CHART_STORAGE_FULL"}
        val indexFile=File(stage,"features.sqlite")
        require(!indexFile.exists()) {"GPKG_STAGE_ALREADY_INDEXED"}
        val gson=Gson();val geometryReader=GeoPackageGeometryReader(check)
        SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use {source->
            source.execSQL("PRAGMA query_only=ON")
            require(source.longValue("PRAGMA application_id")==0x47504B47L) {"GPKG_APPLICATION_ID_INVALID"}
            require(source.longValue("PRAGMA user_version") in 10000..19999) {"GPKG_VERSION_UNSUPPORTED"}
            val tables=readTables(source,check)
            val counts=tables.associateWith{table->check();source.longValue("SELECT COUNT(*) FROM ${quote(table.name)}").also{require(it in 0..MAX_ROWS) {"CHART_FEATURE_LIMIT"}}}
            val total=counts.values.sum();require(total in 1..MAX_ROWS) {if(total==0L)"GPKG_NO_FEATURES"else"CHART_FEATURE_LIMIT"}
            val coverage=ArrayList<CoverageEvidence>();val quality=linkedSetOf<String>();val issues=linkedSetOf<String>();val datums=linkedSetOf<String>()
            var bounds=emptyList<ChartBounds>();var coverageBounds=emptyList<ChartBounds>()
            var vertices=0L;var coverageVertices=0L;var row=0L
            var uniformScale:Int?=null;var scaleInitialized=false;var scalesDiffer=false
            val classCodes=objectClasses.entries.associate{it.value.uppercase(Locale.ROOT) to it.key}
            SQLiteDatabase.openOrCreateDatabase(indexFile,null).use {target->
                target.execSQL("PRAGMA journal_mode=DELETE")
                ChartFeatureIndex.create(target)
                target.beginTransaction()
                try {
                    for(table in tables){
                        check();progress(row.toInt(),total.toInt(),table.name)
                        val stableTable=UUID.nameUUIDFromBytes(table.name.toByteArray(StandardCharsets.UTF_8)).toString()
                        source.rawQuery("SELECT ${quote(table.primary)},length(${quote(table.geometry)}),typeof(${quote(table.geometry)}) FROM ${quote(table.name)} ORDER BY ${quote(table.primary)}",null).use {rows->
                            while(rows.moveToNext()){
                                check();require(rows.getType(0)==Cursor.FIELD_TYPE_INTEGER) {"GPKG_FEATURE_ID_INVALID:${table.name}"}
                                val fid=rows.getLong(0)
                                val attributes=readAttributes(source,table,fid,check).toMutableMap()
                                attributes["GPKG_TABLE"]=table.name
                                attributes["GPKG_FEATURE_ID"]=fid.toString()
                                attributes["GPKG_SRS"]= "EPSG:${table.epsg}"
                                val read=if(rows.isNull(1)||rows.getString(2)=="null")null else {
                                    require(rows.getString(2)=="blob") {"GPKG_GEOMETRY_NOT_BINARY"}
                                    val size=rows.getLong(1);require(size in 13..GeoPackageGeometryReader.MAX_BLOB.toLong()) {"GPKG_GEOMETRY_SIZE_LIMIT"}
                                    geometryReader.read(readBlob(source,table,fid,size.toInt(),check),table.srs,table.epsg,table.type,table.z,table.m)
                                }
                                vertices+=read?.vertices?:0;require(vertices<=MAX_TOTAL_VERTICES) {"GPKG_TOTAL_VERTEX_LIMIT"}
                                if(read?.hasZ==true)attributes["GPKG_HAS_Z"]="true"
                                if(read?.hasM==true)attributes["GPKG_HAS_M"]="true"
                                val feature=feature(datasetId,"$datasetId/$CELL/$stableTable/$fid",attributes,read?.geometry,table.changed,classCodes)
                                row++;val featureBounds=ChartFeatureIndex.insert(target,row,feature,gson)
                                bounds=mergeBounds(bounds+featureBounds)
                                if(!scaleInitialized){uniformScale=feature.source.compilationScale;scaleInitialized=true}else if(uniformScale!=feature.source.compilationScale)scalesDiffer=true
                                if(feature.kind==NauticalFeatureKind.COVERAGE&&feature.geometry.kind==ChartGeometryKind.POLYGON&&feature.attributes["CATCOV"] in setOf("1","2")){
                                    coverageVertices+=feature.geometry.parts.sumOf{it.points.size}.toLong()
                                    require(coverage.size<2_000&&coverageVertices<=MAX_COVERAGE_VERTICES) {"GPKG_COVERAGE_SIZE_LIMIT"}
                                    coverage+=CoverageEvidence(feature.id,CELL,feature.geometry,feature.attributes["CATCOV"]=="1",feature.source.compilationScale)
                                    coverageBounds=mergeBounds(coverageBounds+featureBounds)
                                }
                                feature.depth?.datum?.takeIf{it.isNotBlank()}?.let{datums+=it.uppercase(Locale.ROOT)}
                                feature.attributes.filterKeys{it in setOf("quality","QUASOU","CATZOC","POSACC","SOUACC","TECSOU","SURSTA","SUREND")}.forEach{(k,v)->if(v.isNotBlank()&&quality.size<128)quality+="$k=${v.take(256)}"}
                                feature.issues.forEach{if(issues.size<128)issues+=it else issues+="GPKG_MORE_OBJECT_ISSUES"}
                                if(row%256L==0L)progress(row.toInt(),total.toInt(),table.name)
                            }
                        }
                    }
                    require(row==total) {"GPKG_SOURCE_CHANGED_DURING_READ"}
                    if(coverage.none{it.covered})issues+="NO_EXPLICIT_ENC_COVERAGE"
                    if(quality.isEmpty())issues+="SURVEY_QUALITY_UNSPECIFIED"
                    if(datums.size>1)issues+="UNSUPPORTED_MIXED_VERTICAL_DATUM"
                    check();target.setTransactionSuccessful()
                }finally{target.endTransaction()}
            }
            progress(row.toInt(),total.toInt(),file.name)
            listOf(ChartCellRevision(CELL,edition=1,update=0,intendedUsage=0,compilationScale=uniformScale.takeUnless{scalesDiffer},
                issueDate=tables.mapNotNull{it.changed?.take(10)}.maxOrNull(),featureCount=row.toInt(),bounds=coverageBounds.ifEmpty{bounds},
                coverage=coverage,quality=quality.toList(),hasUnsupportedSemantic=issues.any{it!="NO_EXPLICIT_ENC_COVERAGE"&&it!="SURVEY_QUALITY_UNSPECIFIED"},issues=issues.toList()))
        }
    }

    private fun readTables(db:SQLiteDatabase,check:()->Unit):List<Table> {
        requireColumns(db,"gpkg_contents",setOf("table_name","data_type","identifier","description","last_change","min_x","min_y","max_x","max_y","srs_id"))
        requireColumns(db,"gpkg_geometry_columns",setOf("table_name","column_name","geometry_type_name","srs_id","z","m"))
        requireColumns(db,"gpkg_spatial_ref_sys",setOf("srs_name","srs_id","organization","organization_coordsys_id","definition","description"))
        val tables=ArrayList<Table>()
        db.rawQuery("SELECT table_name,srs_id,last_change,min_x,min_y,max_x,max_y FROM gpkg_contents WHERE data_type='features' ORDER BY table_name LIMIT ${MAX_TABLES+1}",null).use {contents->
            while(contents.moveToNext()){
                check();require(tables.size<MAX_TABLES) {"GPKG_TABLE_LIMIT"}
                val name=contents.getString(0);require(name.isNotBlank()&&name.length<=255&&!name.startsWith("sqlite_",true)&&!name.startsWith("gpkg_",true)) {"GPKG_TABLE_NAME_INVALID"}
                require(contents.getType(1)==Cursor.FIELD_TYPE_INTEGER) {"GPKG_SRS_MISSING:$name"};val srs=contents.getInt(1)
                val changed=contents.getString(2)?.also{require(runCatching{Instant.parse(it)}.isSuccess) {"GPKG_LAST_CHANGE_INVALID:$name"}}
                require(changed!=null) {"GPKG_LAST_CHANGE_INVALID:$name"}
                val extent=(3..6).map{index->if(contents.isNull(index))null else contents.getString(index).toDoubleOrNull()?.takeIf{it.isFinite()}?:error("GPKG_CONTENTS_BOUNDS_INVALID:$name")}
                require((extent[0]==null||extent[2]==null||extent[0]!!<=extent[2]!!)&&(extent[1]==null||extent[3]==null||extent[1]!!<=extent[3]!!)) {"GPKG_CONTENTS_BOUNDS_INVALID:$name"}
                val columns=columns(db,name);require(columns.size<=128) {"GPKG_COLUMN_LIMIT:$name"}
                val primary=columns.filter{it.primary>0};require(primary.size==1&&primary[0].type.equals("INTEGER",true)) {"GPKG_INTEGER_PRIMARY_KEY_REQUIRED:$name"}
                db.rawQuery("SELECT column_name,geometry_type_name,srs_id,z,m FROM gpkg_geometry_columns WHERE table_name=?",arrayOf(name)).use{geometry->
                    require(geometry.moveToFirst()) {"GPKG_GEOMETRY_COLUMN_MISSING:$name"}
                    val column=geometry.getString(0);val type=geometry.getString(1).uppercase(Locale.ROOT)
                    require(type in geometryTypes) {"GPKG_GEOMETRY_TYPE_UNSUPPORTED:$type"}
                    require(columns.any{it.name==column}&&column!=primary[0].name) {"GPKG_GEOMETRY_COLUMN_INVALID:$name"}
                    require((2..4).all{geometry.getType(it)==Cursor.FIELD_TYPE_INTEGER}) {"GPKG_GEOMETRY_METADATA_INVALID:$name"}
                    require(geometry.getInt(2)==srs) {"GPKG_CONTENTS_SRS_MISMATCH:$name"}
                    val z=geometry.getInt(3);val m=geometry.getInt(4);require(z in 0..2&&m in 0..2) {"GPKG_GEOMETRY_DIMENSION_INVALID"}
                    val epsg=db.rawQuery("SELECT organization,organization_coordsys_id,definition FROM gpkg_spatial_ref_sys WHERE srs_id=?",arrayOf(srs.toString())).use{system->
                        require(system.moveToFirst()) {"GPKG_SRS_MISSING:$srs"}
                        require(system.getType(1)==Cursor.FIELD_TYPE_INTEGER) {"GPKG_SRS_INVALID:$srs"}
                        val organization=system.getString(0);val code=system.getInt(1)
                        require(organization.equals("EPSG",true)&&code in setOf(4326,3857)) {"GPKG_SRS_UNSUPPORTED:$organization:$code"}
                        require(!system.getString(2).isNullOrBlank()) {"GPKG_SRS_DEFINITION_MISSING"}
                        require(!system.moveToNext()) {"GPKG_SRS_DUPLICATE"};code
                    }
                    val attrs=columns.filter{it.name!=column&&it.name!=primary[0].name}.map{it.name}
                    tables+=Table(name,column,type,srs,epsg,z,m,primary[0].name,attrs,changed)
                    require(!geometry.moveToNext()) {"GPKG_MULTIPLE_GEOMETRY_COLUMNS:$name"}
                }
            }
        }
        require(tables.isNotEmpty()) {"GPKG_NO_FEATURE_TABLES"}
        if(hasTable(db,"gpkg_extensions")){
            requireColumns(db,"gpkg_extensions",setOf("table_name","column_name","extension_name","definition","scope"))
            db.rawQuery("SELECT table_name,column_name,extension_name FROM gpkg_extensions LIMIT 4097",null).use{rows->
                var count=0
                while(rows.moveToNext()){
                    check();require(++count<=4096) {"GPKG_EXTENSION_LIMIT"}
                    val table=rows.getString(0);val extension=rows.getString(2)
                    if(table!=null&&tables.any{it.name==table})require(extension in setOf("gpkg_rtree_index","gpkg_schema","gpkg_metadata","gpkg_crs_wkt","gpkg_crs_wkt_1_1")) {"GPKG_EXTENSION_UNSUPPORTED:$extension"}
                }
            }
        }
        return tables
    }

    private fun columns(db:SQLiteDatabase,table:String):List<Column> {
        require(hasTable(db,table)) {"GPKG_TABLE_MISSING_OR_VIEW_UNSUPPORTED:$table"}
        return db.rawQuery("PRAGMA table_info(${quote(table)})",null).use{rows->buildList {
            while(rows.moveToNext()){
                require(size<256) {"GPKG_COLUMN_LIMIT:$table"}
                val name=rows.getString(rows.getColumnIndexOrThrow("name"));require(name.length in 1..255&&'\u0000' !in name) {"GPKG_COLUMN_NAME_INVALID"}
                add(Column(name,rows.getString(rows.getColumnIndexOrThrow("type")),rows.getInt(rows.getColumnIndexOrThrow("pk"))))
            }
        }}
    }
    private fun requireColumns(db:SQLiteDatabase,table:String,names:Set<String>){require(columns(db,table).map{it.name}.toSet().containsAll(names)) {"GPKG_METADATA_COLUMNS_MISSING:$table"}}
    private fun hasTable(db:SQLiteDatabase,name:String)=db.rawQuery("SELECT type FROM sqlite_master WHERE name=?",arrayOf(name)).use{it.moveToFirst()&&it.getString(0)=="table"}
    private fun quote(value:String):String {require('\u0000' !in value&&value.length in 1..255) {"GPKG_IDENTIFIER_INVALID"};return "\""+value.replace("\"","\"\"")+"\""}
    private fun SQLiteDatabase.longValue(sql:String)=rawQuery(sql,null).use{require(it.moveToFirst()) {"GPKG_METADATA_INVALID"};it.getLong(0)}

    private fun readBlob(db:SQLiteDatabase,table:Table,id:Long,size:Int,check:()->Unit):ByteArray {
        val output=ByteArray(size);var offset=0
        while(offset<size){
            check();val length=minOf(128_000,size-offset)
            db.rawQuery("SELECT substr(${quote(table.geometry)},?,?) FROM ${quote(table.name)} WHERE ${quote(table.primary)}=?",arrayOf((offset+1).toString(),length.toString(),id.toString())).use{
                require(it.moveToFirst()) {"GPKG_FEATURE_DISAPPEARED"};val part=it.getBlob(0)
                require(part.size==length) {"GPKG_GEOMETRY_TRUNCATED"};part.copyInto(output,offset)
            };offset+=length
        }
        return output
    }
    private fun readAttributes(db:SQLiteDatabase,table:Table,id:Long,check:()->Unit):Map<String,String> {
        if(table.attributes.isEmpty())return emptyMap()
        val expressions=table.attributes.joinToString(","){column->val c=quote(column);"typeof($c),length(CAST($c AS TEXT)),substr(CAST($c AS TEXT),1,${MAX_TEXT+1})"}
        return db.rawQuery("SELECT $expressions FROM ${quote(table.name)} WHERE ${quote(table.primary)}=?",arrayOf(id.toString())).use{row->
            require(row.moveToFirst()) {"GPKG_FEATURE_DISAPPEARED"};var total=0
            buildMap {
                for((index,column) in table.attributes.withIndex()){
                    check();val offset=index*3
                    if(row.getString(offset)=="null")continue
                    require(row.getString(offset)!="blob") {"GPKG_ATTRIBUTE_BLOB_UNSUPPORTED:$column"}
                    require(row.getLong(offset+1)<=MAX_TEXT) {"GPKG_ATTRIBUTE_SIZE_LIMIT:$column"}
                    val value=row.getString(offset+2);total+=value.length;require(total<=MAX_ATTRIBUTE_TEXT) {"GPKG_ATTRIBUTE_SIZE_LIMIT"}
                    put(column,value)
                }
            }
        }
    }

    private fun feature(datasetId:String,id:String,raw:MutableMap<String,String>,geometry:ChartGeometry?,changed:String?,classes:Map<String,Int>):NauticalFeature {
        val fields=raw.mapKeys{it.key.lowercase(Locale.ROOT)}
        fun value(vararg names:String)=names.firstNotNullOfOrNull{fields[it.lowercase(Locale.ROOT)]?.trim()?.takeIf(String::isNotEmpty)}
        val issues=mutableListOf<String>()
        val attrs=raw.toMutableMap()
        // S-57 属性名规范化后供现有绘制/分析共用；原始自定义字段同样保留。
        raw.filterKeys{it.matches(Regex("[A-Za-z][A-Za-z0-9_]{4,8}"))}.forEach{(key,v)->attrs.putIfAbsent(key.uppercase(Locale.ROOT),v)}
        fun alias(target:String,vararg names:String){value(*names)?.let{attrs[target]=it}}
        alias("OBJNAM","name","OBJNAM");alias("SORIND","source","SORIND");alias("QUASOU","quality","QUASOU")
        alias("POSACC","accuracy","POSACC");alias("INFORM","description","INFORM")
        val className=value("object_class")?.uppercase(Locale.ROOT)
        val kindName=value("kind")?.uppercase(Locale.ROOT)
        val byClass=className?.let{if(it.toIntOrNull()!=null)classes.entries.firstOrNull{entry->entry.value==it.toIntOrNull()}?.key else it}
        val byKind=kindName?.let{runCatching{NauticalFeatureKind.valueOf(it)}.getOrNull()?:S57Reader.kind(it)}
        var kind=byKind ?: byClass?.let{S57Reader.kind(it)} ?: NauticalFeatureKind.OTHER
        if(byKind!=null&&byClass!=null&&S57Reader.kind(byClass)!=byKind){issues+="UNINTERPRETED_GPKG_KIND_CONFLICT";kind=NauticalFeatureKind.OTHER}
        if(kind==NauticalFeatureKind.OTHER)issues+="UNINTERPRETED_GPKG_OBJECT:${(className?:kindName?:"unspecified").take(64)}"
        var shape=geometry ?: ChartGeometry(ChartGeometryKind.NONE,emptyList())
        if(shape.kind==ChartGeometryKind.NONE)issues+="GEOMETRY_MISSING"
        val expected=when(kind){
            NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.COVERAGE->setOf(ChartGeometryKind.POLYGON)
            NauticalFeatureKind.SOUNDING->setOf(ChartGeometryKind.POINT,ChartGeometryKind.MULTIPOINT)
            NauticalFeatureKind.DEPTH_CONTOUR->setOf(ChartGeometryKind.LINE)
            else->null
        }
        if(expected!=null&&shape.kind !in expected){issues+="UNINTERPRETED_GPKG_GEOMETRY_KIND";kind=NauticalFeatureKind.OTHER}
        fun number(vararg names:String,minimum:Double= -20_000.0,maximum:Double=20_000.0):Double? {
            val values=names.mapNotNull{name->fields[name.lowercase(Locale.ROOT)]?.trim()?.takeIf{it.isNotEmpty()}}
            if(values.isEmpty())return null
            val numbers=values.map{it.toDoubleOrNull()}
            if(numbers.any{it==null||!it.isFinite()||it !in minimum..maximum}){issues+="UNINTERPRETED_GPKG_NUMBER:${names.first()}";return null}
            val first=numbers.first()!!
            if(numbers.any{abs(it!!-first)>1e-8}){issues+="UNINTERPRETED_GPKG_FIELD_CONFLICT:${names.first()}";return null}
            return first
        }
        // 所有明确的单位声明都必须一致，不能让较先出现的 m 遮住另一个字段中的 feet/fathoms。
        val units=listOfNotNull(value("depth_unit"),value("S57_DEPTH_UNIT"),value("DEPUNI")).map{it.lowercase(Locale.ROOT)}
        val metric=units.all{it in setOf("1","m","metre","metres","meter","meters")}
        if(!metric)issues+="UNSUPPORTED_GPKG_DEPTH_UNIT"
        val minimum=if(metric)number("depth_min_m","DRVAL1")else null
        val maximum=if(metric)number("depth_max_m","DRVAL2")else null
        val point=if(metric)number("depth_m","VALSOU","VALDCO")else null
        if(minimum!=null&&maximum!=null&&minimum>maximum)issues+="UNINTERPRETED_GPKG_DEPTH_INTERVAL"
        val datumValues=listOfNotNull(value("vertical_datum"),value("VERDAT")).map{it.uppercase(Locale.ROOT)}
        val datum=datumValues.firstOrNull()?.takeUnless{it in setOf("0","-1","UNKNOWN","UNDEFINED","UNSPECIFIED")}
        if(datumValues.distinct().size>1)issues+="UNINTERPRETED_GPKG_DATUM_CONFLICT"
        if(kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,NauticalFeatureKind.DRYING_AREA)&&minimum==null)issues+="GPKG_DEPTH_MINIMUM_MISSING"
        if(kind==NauticalFeatureKind.SOUNDING&&point==null)issues+="GPKG_SOUNDING_DEPTH_MISSING"
        if(kind==NauticalFeatureKind.DEPTH_AREA&&maximum!=null&&maximum<=0.0)kind=NauticalFeatureKind.DRYING_AREA
        val quality=value("quality","QUASOU","CATZOC")
        val depth=when(kind){
            NauticalFeatureKind.SOUNDING->DepthEvidence(DepthEvidenceKind.POINT,pointMeters=point,datum=datum,quality=quality)
            NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,NauticalFeatureKind.DRYING_AREA->DepthEvidence(DepthEvidenceKind.INTERVAL,minimum,maximum,datum=datum,quality=quality)
            NauticalFeatureKind.DEPTH_CONTOUR->DepthEvidence(DepthEvidenceKind.CONTOUR,pointMeters=point,datum=datum,quality=quality)
            NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK,NauticalFeatureKind.OBSTRUCTION->DepthEvidence(if(point!=null)DepthEvidenceKind.POINT else DepthEvidenceKind.UNKNOWN,pointMeters=point,datum=datum,quality=quality)
            else->null
        }
        if(depth!=null&&datum.isNullOrBlank())issues+="GPKG_VERTICAL_DATUM_MISSING"
        if(kind==NauticalFeatureKind.SOUNDING&&point!=null)shape=shape.copy(parts=shape.parts.map{part->part.copy(points=part.points.map{it.copy(depthMeters=point)})})
        if(kind==NauticalFeatureKind.COVERAGE){
            val covered=value("covered")?.lowercase(Locale.ROOT)?.let{when(it){"true","1","yes"->true;"false","0","no"->false;else->null}}
            val category=value("CATCOV")?.let{when(it){"1"->true;"2"->false;else->null}}
            if(value("covered")!=null&&covered==null||value("CATCOV")!=null&&category==null||covered!=null&&category!=null&&covered!=category)issues+="UNSUPPORTED_GPKG_COVERAGE_CATEGORY"
            else (covered?:category)?.let{attrs["CATCOV"]=if(it)"1"else"2"} ?: run{issues+="UNSUPPORTED_GPKG_COVERAGE_CATEGORY"}
            if("UNSUPPORTED_GPKG_COVERAGE_CATEGORY" in issues)attrs.remove("CATCOV")
        }
        number("accuracy","POSACC",minimum=0.0,maximum=1_000_000.0)
        val scale=number("compilation_scale","CSCALE",minimum=1.0,maximum=100_000_000.0)?.let{v->if(v%1.0==0.0)v.toInt()else{issues+="UNINTERPRETED_GPKG_COMPILATION_SCALE";null}}
        val acronym=byClass?:kindName?.takeIf{S57Reader.kind(it)!=NauticalFeatureKind.OTHER}?:when(kind){
            NauticalFeatureKind.LAND->"LNDARE";NauticalFeatureKind.SOUNDING->"SOUNDG";NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DRYING_AREA->"DEPARE"
            NauticalFeatureKind.DEPTH_CONTOUR->"DEPCNT";NauticalFeatureKind.DREDGED_AREA->"DRGARE";NauticalFeatureKind.OBSTRUCTION->"OBSTRN"
            NauticalFeatureKind.WRECK->"WRECKS";NauticalFeatureKind.ROCK->"UWTROC";NauticalFeatureKind.LIGHT->"LIGHTS";NauticalFeatureKind.BRIDGE->"BRIDGE"
            NauticalFeatureKind.OVERHEAD->"CBLOHD";NauticalFeatureKind.RESTRICTED->"RESARE";NauticalFeatureKind.COVERAGE->"M_COVR";NauticalFeatureKind.QUALITY->"M_QUAL"
            else->"GPKG_${kind.name}"
        }
        val source=ChartFeatureSource(datasetId,CELL,1,0,0,scale,0,null,datum?.toIntOrNull()?.takeIf{it>0},datum?.toIntOrNull()?.takeIf{it>0},changed?.take(10),
            sourceDate=value("source_date","SORDAT"),sourceIndication=value("source","SORIND"))
        return NauticalFeature(id,datasetId,CELL,classes[acronym]?:0,acronym,kind,shape,attrs,depth,source,issues.distinct())
    }

    private fun mergeBounds(bounds:List<ChartBounds>):List<ChartBounds> {
        if(bounds.isEmpty())return emptyList()
        val groups=if(bounds.any{it.west<= -179.999}&&bounds.any{it.east>=179.999})listOf(bounds.filter{it.west<0},bounds.filter{it.west>=0})else listOf(bounds)
        return groups.filter{it.isNotEmpty()}.map{group->ChartBounds(group.minOf{it.west},group.minOf{it.south},group.maxOf{it.east},group.maxOf{it.north})}
    }
}
