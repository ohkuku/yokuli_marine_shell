package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import java.io.File
import java.io.BufferedInputStream
import java.nio.charset.Charset
import kotlin.math.abs

/** ISO 8211/S-57 二进制 reader；不通过贴图、OCR 或 GDAL 桌面可执行文件制造对象。 */
internal class S57Reader(private val dictionaries:S57Dictionaries,private val cancelled:()->Unit) {
    data class Header(val cell:String,val edition:Int,val update:Int,val purpose:Int,val usage:Int,val agency:Int,val issue:String,val applyDate:String)
    data class Parameters(val horizontal:Int,val vertical:Int,val sounding:Int,val scale:Int,val depthUnit:Int,val heightUnit:Int,val coordinateUnit:Int,val coordinateFactor:Long,val soundingFactor:Long)
    data class Record(val fields:Map<String,ByteArray>) {
        val keyField get()=fields["FRID"] ?: fields["VRID"] ?: error("S57_RECORD_ID_MISSING")
        val feature get()=fields.containsKey("FRID")
        val key get()="${keyField.u8(0)}:${keyField.u32(1)}"
        val version get()=keyField.u16(if(feature)9 else 5)
        val instruction get()=keyField.u8(if(feature)11 else 7)
    }
    data class Cell(val header:Header,val parameters:Parameters,val lexical:Int,val nationalLexical:Int,val records:LinkedHashMap<String,Record>)
    data class Transfer(val header:Header,val parameters:Parameters?,val lexical:Int,val nationalLexical:Int,val records:List<Record>,val unknownTags:Set<String>)

    fun read(file:File,metadataOnly:Boolean=false):Transfer {
        require(file.length() in 24..512_000_000L) {"S57_FILE_SIZE_LIMIT"}
        var header:Header?=null;var parameters:Parameters?=null;var aall=0;var nall=0;var sawDdr=false;var declaredFields=emptySet<String>()
        var retainedBytes=0L
        val memoryBudget=(Runtime.getRuntime().maxMemory()/4).coerceIn(16_000_000L,128_000_000L)
        val records=ArrayList<Record>();val unknown=mutableSetOf<String>()
        BufferedInputStream(file.inputStream(),64*1024).use {input->
            while(true) {
                cancelled()
                val first=input.read();if(first<0)break
                val leader=ByteArray(24);leader[0]=first.toByte();input.readFully(leader,1,23)
                val length=leader.ascii(0,5).toIntOrNull() ?: error("S57_ENCRYPTED_OR_INVALID_ISO8211")
                val base=leader.ascii(12,5).toIntOrNull() ?: error("S57_INVALID_DIRECTORY")
                require((length==0||length in 25..99_999)&&base in 25..99_999&&(length==0||base<=length)) {"S57_UNSUPPORTED_RECORD_LENGTH"}
                val fieldLength=leader.u8(20)-48;val fieldPosition=leader.u8(21)-48;val tagLength=leader.u8(23)-48
                require(fieldLength in 1..9&&fieldPosition in 1..9&&tagLength==4) {"S57_UNSUPPORTED_DIRECTORY_FORMAT"}
                val directorySize=base-24
                val directory=ByteArray(directorySize);input.readFully(directory,0,directory.size)
                require(directory.last().toInt()==30) {"S57_DIRECTORY_TERMINATOR"}
                val width=tagLength+fieldLength+fieldPosition
                require((directorySize-1)%width==0) {"S57_DIRECTORY_SIZE"}
                data class Entry(val tag:String,val size:Int,val offset:Int)
                val entries=(0 until directorySize-1 step width).map {position->
                    val size=directory.ascii(position+tagLength,fieldLength).toIntOrNull() ?: error("S57_FIELD_LENGTH")
                    val offset=directory.ascii(position+tagLength+fieldLength,fieldPosition).toIntOrNull() ?: error("S57_FIELD_POSITION")
                    require(size>0&&offset>=0&&offset.toLong()+size<=32_000_000L) {"S57_FIELD_SIZE_LIMIT"}
                    Entry(directory.ascii(position,tagLength),size,offset)
                }
                // ISO 8211 用 00000 表示超过五位数的记录，真实大小由目录决定。
                val extent=entries.maxOfOrNull {it.offset+it.size} ?: 0
                val payloadSize=if(length==0)extent else length-base
                require(payloadSize in extent..32_000_000) {"S57_TRUNCATED_FIELD_AREA"}
                val data=ByteArray(payloadSize);input.readFully(data,0,data.size)
                val fields=linkedMapOf<String,ByteArray>()
                for((tag,size,start) in entries) {
                    val terminator=if(data[start+size-1].toInt()==30)1 else if(tag=="NATF"&&size>=2&&data[start+size-2].toInt()==30&&data[start+size-1].toInt()==0)2 else error("S57_FIELD_TERMINATOR:$tag")
                    val payload=data.copyOfRange(start,start+size-terminator)
                    fields[tag]=fields[tag]?.plus(payload) ?: payload
                }
                when(leader[6].toInt().toChar()) {
                    'L'->{require(!sawDdr&&fields.containsKey("DSID")) {"S57_DDR_MISSING"};validateDefinitions(fields,leader.ascii(10,2).toIntOrNull() ?: 0);declaredFields=fields.keys;sawDdr=true}
                    'D'->{
                        require(sawDdr) {"S57_DDR_MISSING"}
                        require(fields.keys.all {it in declaredFields}) {"S57_UNDECLARED_FIELD"}
                        fields["DSID"]?.let {require(header==null) {"S57_DUPLICATE_DSID"};header=header(it)}
                        if(metadataOnly&&header!=null)return Transfer(requireNotNull(header),null,0,0,emptyList(),emptySet())
                        fields["DSPM"]?.let {parameters=parameters(it)}
                        fields["DSSI"]?.let {require(it.size>=3) {"S57_DSSI_TRUNCATED"};require(it.u8(0)==2) {"S57_TOPOLOGY_NOT_CHAIN_NODE"};aall=it.u8(1);nall=it.u8(2);require(aall in 0..1&&nall in 0..2) {"S57_LEXICAL_LEVEL_UNSUPPORTED"}}
                        if(fields.containsKey("FRID")||fields.containsKey("VRID")) {
                            retainedBytes+=fields.values.sumOf {it.size.toLong()}+256L+fields.size*80L
                            require(records.size<1_000_000&&retainedBytes<=memoryBudget) {"S57_CELL_MEMORY_LIMIT"}
                            records+=Record(fields.filterKeys {it!="0001"})
                        }
                        fields.keys.filterNot {it in setOf("0001","DSID","DSSI","DSPM","DSAC","CATD","FRID","FOID","ATTF","NATF","FSPC","FSPT","FFPC","FFPT","VRID","ATTV","VRPC","VRPT","SGCC","SG2D","SG3D")}.forEach(unknown::add)
                        if(!fields.containsKey("FRID")&&!fields.containsKey("VRID"))require(unknown.isEmpty()) {"S57_UNSUPPORTED_DATASET_FIELD:${unknown.joinToString()}"}
                    }
                    else->error("S57_UNSUPPORTED_RECORD_LEADER")
                }
            }
        }
        return Transfer(requireNotNull(header) {"S57_DSID_MISSING"},parameters,aall,nall,records,unknown)
    }
    /** 按 DDR 声明核对固定二进制布局；不把 ASCII 或另一种精度的字段套进已知偏移。 */
    private fun validateDefinitions(fields:Map<String,ByteArray>,controlLength:Int) {
        require(controlLength in 6..20) {"S57_DDR_CONTROL_LENGTH"}
        val expected=mapOf(
            "DSID" to "b11,b14,b11,b11,A,A,A,A(8),A(8),R(4),b11,A,A,b11,b12,A",
            "DSSI" to "b11,b11,b11,b14,b14,b14,b14,b14,b14,b14,b14",
            "DSPM" to "b11,b14,b11,b11,b11,b14,b11,b11,b11,b11,b14,b14,A",
            "FRID" to "b11,b14,b11,b11,b12,b12,b11", "FOID" to "b12,b14,b12",
            "VRID" to "b11,b14,b12,b11", "VRPT" to "B(40),b11,b11,b11,b11",
            "FSPT" to "B(40),b11,b11,b11", "FFPT" to "B(64),b11,A",
            "ATTF" to "b12,A", "NATF" to "b12,A", "ATTV" to "b12,A",
            "SG2D" to "b24,b24", "SG3D" to "b24,b24,b24",
            "FSPC" to "b11,b12,b12", "VRPC" to "b11,b12,b12", "FFPC" to "b11,b12,b12", "SGCC" to "b11,b12,b12",
        )
        for((tag,expectedTypes) in expected)fields[tag]?.let {bytes->
            require(bytes.size>controlLength) {"S57_DDR_FIELD_TRUNCATED:$tag"}
            val segments=String(bytes,controlLength,bytes.size-controlLength,Charsets.US_ASCII).split('\u001f')
            val format=segments.getOrNull(2) ?: error("S57_DDR_FORMAT_MISSING:$tag")
            require(expandFormat(format)==expectedTypes.split(',')) {"S57_DDR_ENCODING_UNSUPPORTED:$tag"}
        }
    }
    private fun expandFormat(raw:String):List<String> {
        val text=raw.filterNot(Char::isWhitespace);var at=0
        fun group():List<String> {
            require(at<text.length&&text[at++]=='(') {"S57_DDR_FORMAT_INVALID"}
            val result=mutableListOf<String>()
            while(at<text.length&&text[at]!=')') {
                val begin=at;while(at<text.length&&text[at].isDigit())at++
                val repetitions=if(begin==at)1 else text.substring(begin,at).toIntOrNull() ?: error("S57_DDR_REPEAT_INVALID")
                require(repetitions in 1..1000) {"S57_DDR_REPEAT_LIMIT"}
                val terms=if(at<text.length&&text[at]=='(')group()else {
                    val start=at;require(at<text.length&&text[at].isLetter()) {"S57_DDR_TYPE_INVALID"};at++
                    while(at<text.length&&text[at].isDigit())at++
                    if(at<text.length&&text[at]=='(') {at++;while(at<text.length&&text[at]!=')')at++;require(at<text.length) {"S57_DDR_TYPE_INVALID"};at++}
                    listOf(text.substring(start,at).replace("A()","A"))
                }
                repeat(repetitions) {result+=terms};require(result.size<=1000) {"S57_DDR_FIELD_LIMIT"}
                if(at<text.length&&text[at]==',')at++else require(at<text.length&&text[at]==')') {"S57_DDR_FORMAT_INVALID"}
            }
            require(at<text.length&&text[at++]==')') {"S57_DDR_FORMAT_INVALID"}
            return result
        }
        val result=group();require(at==text.length) {"S57_DDR_FORMAT_TRAILING"};return result
    }
    fun base(transfer:Transfer):Cell {
        require(transfer.header.purpose==1&&transfer.header.update==0&&transfer.header.edition>0) {"S57_BASE_REQUIRED"}
        val map=linkedMapOf<String,Record>()
        transfer.records.forEach {record->cancelled();require(record.instruction==1&&record.version>=1) {"S57_BASE_RECORD_INSTRUCTION"};require(map.put(record.key,record)==null) {"S57_DUPLICATE_RECORD"}}
        return Cell(transfer.header,requireNotNull(transfer.parameters) {"S57_DSPM_MISSING"},transfer.lexical,transfer.nationalLexical,map)
    }
    fun apply(cell:Cell,update:Transfer):Cell {
        require(update.header.cell==cell.header.cell) {"S57_CELL_MISMATCH"}
        require(update.header.update==cell.header.update+1) {"S57_MISSING_UPDATE:${cell.header.update+1}"}
        require(update.header.edition==cell.header.edition||update.header.edition==0) {"S57_EDITION_MISMATCH"}
        require(update.header.purpose==2) {"S57_UPDATE_PURPOSE_INVALID"}
        require(update.parameters==null||update.parameters==cell.parameters) {"S57_UPDATE_PARAMETERS_CHANGED"}
        if(update.header.edition==0)return cell.copy(header=update.header,records=linkedMapOf())
        val records=LinkedHashMap(cell.records)
        for(incoming in update.records) {
            cancelled();val old=records[incoming.key]
            when(incoming.instruction) {
                1->{require(old==null&&incoming.version==1) {"S57_INSERT_EXISTING_RECORD:${incoming.key}"};records[incoming.key]=incoming}
                2->{require(old!=null&&incoming.version==old.version+1) {"S57_DELETE_VERSION:${incoming.key}"};records.remove(incoming.key)}
                3->{require(old!=null&&incoming.version==old.version+1) {"S57_UPDATE_RECORD_VERSION:${incoming.key}"};records[incoming.key]=patch(old,incoming,cell.nationalLexical)}
                else->error("S57_RECORD_UPDATE_INSTRUCTION")
            }
        }
        return cell.copy(header=update.header,parameters=update.parameters ?: cell.parameters,records=records)
    }
    private fun patch(old:Record,update:Record,national:Int):Record {
        val fields=old.fields.toMutableMap()
        fields[if(old.feature)"FRID"else"VRID"]=update.keyField
        update.fields["FOID"]?.let {fields["FOID"]=it}
        for(tag in listOf("ATTF","NATF","ATTV"))update.fields[tag]?.let {bytes->
            val values=attributePairs(fields[tag] ?: byteArrayOf(),if(tag=="NATF")national else 1).toMutableMap()
            attributePairs(bytes,if(tag=="NATF")national else 1).forEach {(key,value)->if(value=="\u007f")values.remove(key)else values[key]=value}
            fields[tag]=encodeAttributes(values,if(tag=="NATF")national else 1)
        }
        for((control,target,width) in listOf(Triple("FSPC","FSPT",8),Triple("VRPC","VRPT",9),Triple("FFPC","FFPT",0),Triple("SGCC",if(old.fields.containsKey("SG3D")||update.fields.containsKey("SG3D"))"SG3D"else"SG2D",if(old.fields.containsKey("SG3D")||update.fields.containsKey("SG3D"))12 else 8))) {
            val controls=update.fields[control]
            if(controls!=null) {
                require(controls.size%5==0) {"S57_UPDATE_CONTROL_TRUNCATED:$control"}
                val existing=tuples(fields[target] ?: byteArrayOf(),width).toMutableList()
                val incoming=tuples(update.fields[target] ?: byteArrayOf(),width);var read=0
                for(offset in controls.indices step 5) {
                    val operation=controls.u8(offset);val index=controls.u16(offset+1)-1;val count=controls.u16(offset+3)
                    require(index in 0..existing.size&&count>0) {"S57_UPDATE_INDEX:$target"}
                    when(operation) {
                        1->{require(read+count<=incoming.size) {"S57_UPDATE_PAYLOAD:$target"};existing.addAll(index,incoming.subList(read,read+count));read+=count}
                        2->{require(index+count<=existing.size) {"S57_UPDATE_DELETE:$target"};repeat(count){existing.removeAt(index)}}
                        3->{require(index+count<=existing.size&&read+count<=incoming.size) {"S57_UPDATE_REPLACE:$target"};repeat(count){existing[index+it]=incoming[read+it]};read+=count}
                        else->error("S57_UPDATE_OPERATION:$target")
                    }
                }
                require(read==incoming.size) {"S57_UPDATE_UNUSED_PAYLOAD:$target"}
                fields[target]=join(existing)
            }else update.fields[target]?.let {fields[target]=it}
        }
        // 曲线/曲面等没有实现的几何保留，normalize 会使该 cell 资格明确不足，而非忽略。
        update.fields.filterKeys {it !in setOf("FRID","VRID","FOID","ATTF","NATF","ATTV","FSPC","FSPT","VRPC","VRPT","FFPC","FFPT","SGCC","SG2D","SG3D")}.forEach {(tag,value)->fields[tag]=value}
        return Record(fields)
    }
    fun features(cell:Cell,datasetId:String):Sequence<NauticalFeature> = sequence {
        val params=cell.parameters
        require(params.horizontal==2&&params.coordinateUnit==1) {"S57_HORIZONTAL_DATUM_UNSUPPORTED:${params.horizontal}/${params.coordinateUnit}"}
        require(params.coordinateFactor>0&&params.soundingFactor>0) {"S57_INVALID_COORDINATE_FACTOR"}
        // 区域级基准/比例尺覆盖必须显式辨认；尚未按几何拆分的异值覆盖不能悄悄继承 DSPM。
        val localMetadataIssues=mutableSetOf<String>()
        for(meta in cell.records.values) {
            cancelled();if(!meta.feature)continue
            val name=dictionaries.objects[meta.keyField.u16(7)]
            if(name !in setOf("M_SDAT","M_VDAT","M_CSCL"))continue
            val values=attributePairs(meta.fields["ATTF"] ?: byteArrayOf(),cell.lexical).mapKeys {(code,_)->dictionaries.attributes[code] ?: "ATT_$code"}
            if(name=="M_SDAT"&&values["VERDAT"]?.toIntOrNull()!=params.sounding)localMetadataIssues+="UNSUPPORTED_LOCAL_SOUNDING_DATUM"
            if(name=="M_VDAT"&&values["VERDAT"]?.toIntOrNull()!=params.vertical)localMetadataIssues+="UNSUPPORTED_LOCAL_VERTICAL_DATUM"
            if(name=="M_CSCL"&&values["CSCALE"]?.toIntOrNull()!=params.scale)localMetadataIssues+="UNSUPPORTED_LOCAL_COMPILATION_SCALE"
        }
        val baseSource=ChartFeatureSource(datasetId,cell.header.cell,cell.header.edition,cell.header.update,cell.header.agency,params.scale.takeIf {it>0},cell.header.usage,params.horizontal,params.vertical,params.sounding,cell.header.issue)
        for(record in cell.records.values) {
            cancelled();if(!record.feature)continue
            val key=record.keyField;val primitive=key.u8(5);val objectClass=key.u16(7);val acronym=dictionaries.objects[objectClass] ?: "OBJ_$objectClass"
            val attrs=attributePairs(record.fields["ATTF"] ?: byteArrayOf(),cell.lexical)+attributePairs(record.fields["NATF"] ?: byteArrayOf(),cell.nationalLexical)
            val attributes=attrs.mapKeys {(code,_)->dictionaries.attributes[code] ?: "ATT_$code"}.toMutableMap()
            val foid=record.fields["FOID"]
            val stable=if(foid!=null&&foid.size>=8)"${foid.u16(0)}:${foid.u32(2)}:${foid.u16(6)}"else "record:${record.key}"
            record.fields["FFPT"]?.let {attributes["FEATURE_REFERENCES"]=tuples(it,0).joinToString(";") {part->"${part.u16(0)}:${part.u32(2)}:${part.u16(6)}:${part.u8(8)}"}}
            val issues=localMetadataIssues.toMutableList()
            if(acronym.startsWith("OBJ_"))issues+="UNSUPPORTED_OBJECT_CLASS:$objectClass"
            if(record.fields.keys.any {it !in setOf("FRID","FOID","ATTF","NATF","FSPT","FFPT")})issues+="UNSUPPORTED_FEATURE_FIELD"
            if(attributes.keys.any {it.startsWith("ATT_")})issues+="UNSUPPORTED_ATTRIBUTE_CODE"
            val geometry=geometry(record,cell,primitive,issues)
            val kind=if(acronym=="DEPARE"&&(attributes["DRVAL2"]?.toDoubleOrNull() ?: 1.0)<=0.0)NauticalFeatureKind.DRYING_AREA else kind(acronym)
            if(primitive==3&&kind==NauticalFeatureKind.OTHER&&!acronym.startsWith("M_")&&acronym !in benignAreas)issues+="UNINTERPRETED_AREA:$acronym"
            if(kind in setOf(NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA)&&attributes["WATLEV"]?.let {it !in setOf("3")}==true)issues+="UNINTERPRETED_WATER_LEVEL:${attributes["WATLEV"]}"
            if(kind==NauticalFeatureKind.COVERAGE&&attributes["CATCOV"] !in setOf("1","2"))issues+="UNSUPPORTED_COVERAGE_CATEGORY"
            val datum=if("UNSUPPORTED_LOCAL_SOUNDING_DATUM" in localMetadataIssues&&!attributes.containsKey("VERDAT"))null else if(attributes.containsKey("VERDAT"))attributes["VERDAT"]?.takeIf {it.toIntOrNull()?.let {code->code>0}==true}else params.sounding.takeIf {it>0}?.toString()
            val factor=when(params.depthUnit) {1->1.0;2->1.8288;3->.3048;else->null}
            if(factor==null&&kind in depthKinds)issues+="UNSUPPORTED_DEPTH_UNIT:${params.depthUnit}"
            fun depth(key:String)=attributes[key]?.toDoubleOrNull()?.takeIf(Double::isFinite)?.let {value->factor?.times(value)}
            val evidence=when(kind) {
                NauticalFeatureKind.SOUNDING->DepthEvidence(DepthEvidenceKind.POINT,datum=datum,quality=attributes["QUASOU"])
                NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,NauticalFeatureKind.DRYING_AREA->DepthEvidence(DepthEvidenceKind.INTERVAL,depth("DRVAL1"),depth("DRVAL2"),datum=datum,quality=attributes["QUASOU"])
                NauticalFeatureKind.DEPTH_CONTOUR->DepthEvidence(DepthEvidenceKind.CONTOUR,pointMeters=depth("VALDCO"),datum=datum,quality=attributes["QUASOU"])
                NauticalFeatureKind.ROCK,NauticalFeatureKind.WRECK,NauticalFeatureKind.OBSTRUCTION->DepthEvidence(if(depth("VALSOU")!=null)DepthEvidenceKind.POINT else DepthEvidenceKind.UNKNOWN,pointMeters=depth("VALSOU"),datum=datum,quality=attributes["QUASOU"])
                else->null
            }
            // 保存原始单位和值；解析方可使用规范深度/显式的净空换算，不覆盖原始属性。
            attributes["S57_DEPTH_UNIT"]=params.depthUnit.toString();attributes["S57_HEIGHT_UNIT"]=params.heightUnit.toString()
            yield(NauticalFeature("$datasetId/${cell.header.cell}/$stable",datasetId,cell.header.cell,objectClass,kind=kind,geometry=geometry,attributes=attributes,depth=evidence,source=baseSource.copy(sourceDate=attributes["SORDAT"],sourceIndication=attributes["SORIND"]),acronym=acronym,issues=issues))
        }
    }
    private fun geometry(feature:Record,cell:Cell,primitive:Int,issues:MutableList<String>):ChartGeometry {
        if(primitive==255||primitive==0)return ChartGeometry(ChartGeometryKind.NONE,emptyList())
        val pointers=tuples(feature.fields["FSPT"] ?: byteArrayOf(),8)
        if(pointers.isEmpty()) {issues+="GEOMETRY_MISSING";return ChartGeometry(ChartGeometryKind.NONE,emptyList())}
        fun coordinates(record:Record):List<ChartPoint> {
            val is3d=record.fields.containsKey("SG3D");val raw=record.fields[if(is3d)"SG3D"else"SG2D"] ?: return emptyList()
            val factor=when(cell.parameters.depthUnit) {1->1.0;2->1.8288;3->.3048;else->null}
            return tuples(raw,if(is3d)12 else 8).map {b->
                ChartPoint(b.i32(0).toDouble()/cell.parameters.coordinateFactor,b.i32(4).toDouble()/cell.parameters.coordinateFactor,
                    if(is3d&&factor!=null)b.i32(8).toDouble()/cell.parameters.soundingFactor*factor else null).also {p->
                    require(p.latitude in -90.0..90.0&&p.longitude in -180.0..180.0) {"S57_COORDINATE_RANGE"}
                }
            }
        }
        fun referenced(pointer:ByteArray)=cell.records["${pointer.u8(0)}:${pointer.u32(1)}"]
        fun edge(record:Record):List<ChartPoint> {
            if(record.keyField.u8(0)!=130)return coordinates(record)
            val links=tuples(record.fields["VRPT"] ?: byteArrayOf(),9)
            val start=links.firstOrNull {it.u8(7)==1}?.let(::referenced)?.let(::coordinates).orEmpty()
            val end=links.firstOrNull {it.u8(7)==2}?.let(::referenced)?.let(::coordinates).orEmpty()
            if(start.size!=1||end.size!=1)issues+="EDGE_ENDPOINT_MISSING:${record.key}"
            return (start+coordinates(record)+end).fold(mutableListOf()) {all,p->if(all.lastOrNull()!=p)all.add(p);all}
        }
        val parts=pointers.mapNotNull {pointer->
            val record=referenced(pointer)
            if(record==null) {issues+="SPATIAL_REFERENCE_MISSING";return@mapNotNull null}
            if(record.fields.keys.any {it !in setOf("VRID","ATTV","VRPT","SG2D","SG3D")})issues+="UNSUPPORTED_SPATIAL_FIELD"
            val points=edge(record).let {if(pointer.u8(5)==2)it.reversed()else it}
            if(points.isEmpty()) {issues+="SPATIAL_COORDINATES_MISSING";return@mapNotNull null}
            ChartGeometryPart(points,pointer.u8(6)==2)
        }
        if(primitive==1) {
            val points=parts.flatMap {it.points};return ChartGeometry(if(points.size>1)ChartGeometryKind.MULTIPOINT else ChartGeometryKind.POINT,listOf(ChartGeometryPart(points)))
        }
        if(primitive==2)return ChartGeometry(ChartGeometryKind.LINE,parts)
        if(primitive!=3) {issues+="UNSUPPORTED_GEOMETRY_PRIMITIVE:$primitive";return ChartGeometry(ChartGeometryKind.NONE,parts)}
        val remaining=parts.toMutableList();val rings=mutableListOf<ChartGeometryPart>()
        while(remaining.isNotEmpty()) {
            cancelled();val first=remaining.removeAt(0);val points=first.points.toMutableList()
            while(points.first()!=points.last()) {
                val next=remaining.indexOfFirst {it.hole==first.hole&&it.points.first()==points.last()}
                if(next<0)break
                points+=remaining.removeAt(next).points.drop(1)
            }
            if(points.size<4||points.first()!=points.last())issues+="POLYGON_RING_NOT_CLOSED"
            rings+=ChartGeometryPart(points,first.hole)
        }
        return ChartGeometry(ChartGeometryKind.POLYGON,rings)
    }
    private fun header(bytes:ByteArray):Header {
        require(bytes.u8(0)==10) {"S57_DSID_RECORD_NAME_INVALID"}
        val c=Cursor(bytes);c.skip(5);val purpose=c.byte();val usage=c.byte();val name=c.text().substringBeforeLast('.').uppercase()
        val edition=c.text().toIntOrNull() ?: error("S57_EDITION_INVALID");val update=c.text().toIntOrNull() ?: error("S57_UPDATE_INVALID")
        val apply=c.fixed(8);val issue=c.fixed(8);val version=c.fixed(4)
        require(version.toDoubleOrNull()?.let {it>=3.0&&it<4.0}==true) {"S57_EDITION_UNSUPPORTED"}
        require(c.byte()==1) {"S57_NOT_ENC_PRODUCT"};c.text();c.text();c.byte();val producer=c.short()
        require(name.matches(Regex("[A-Z0-9_]{1,32}"))&&update in 0..999&&edition>=0) {"S57_CELL_IDENTIFICATION_INVALID"}
        return Header(name,edition,update,purpose,usage,producer,issue,apply)
    }
    private fun parameters(b:ByteArray):Parameters {
        require(b.size>=24&&b.u8(0)==20) {"S57_DSPM_TRUNCATED"}
        return Parameters(b.u8(5),b.u8(6),b.u8(7),b.u32(8).toInt(),b.u8(12),b.u8(13),b.u8(15),b.u32(16),b.u32(20))
    }
    companion object {
        private val benignAreas=setOf("SBDARE","ADMARE","ACHARE","BERTHS","MORFAC","SEAARE","WEDKLP","VEGATN","WATTUR","HRBARE","LAKARE","RIVERS","CANALS","DWRTCL","FAIRWY","DOCARE")
        private val depthKinds=setOf(NauticalFeatureKind.SOUNDING,NauticalFeatureKind.DEPTH_AREA,NauticalFeatureKind.DREDGED_AREA,NauticalFeatureKind.DRYING_AREA,NauticalFeatureKind.DEPTH_CONTOUR)
        fun kind(acronym:String):NauticalFeatureKind=when {
            acronym=="LNDARE"->NauticalFeatureKind.LAND
            acronym=="SOUNDG"->NauticalFeatureKind.SOUNDING
            acronym=="DEPARE"->NauticalFeatureKind.DEPTH_AREA
            acronym=="DEPCNT"->NauticalFeatureKind.DEPTH_CONTOUR
            acronym=="DRGARE"->NauticalFeatureKind.DREDGED_AREA
            acronym=="SBDARE"->NauticalFeatureKind.OTHER
            acronym=="UWTROC"->NauticalFeatureKind.ROCK
            acronym=="WRECKS"->NauticalFeatureKind.WRECK
            acronym in setOf("OBSTRN","MARCUL","FSHFAC","PIPSOL","PIPOHD","CBLSUB","CBLARE","PIPARE","HULKES","DMPGRD") ->NauticalFeatureKind.OBSTRUCTION
            acronym in setOf("RESARE","MIPARE","CTNARE","PRCARE","EXEZNE","OSPARE","ICNARE","SPLARE","SUBTLN")->NauticalFeatureKind.RESTRICTED
            acronym=="BRIDGE"->NauticalFeatureKind.BRIDGE
            acronym=="CBLOHD"->NauticalFeatureKind.OVERHEAD
            acronym.startsWith("BCN")||acronym.startsWith("BOY")||acronym in setOf("LNDMRK","DAYMAR") ->NauticalFeatureKind.BEACON
            acronym in setOf("LIGHTS","LITFLT","LITVES") ->NauticalFeatureKind.LIGHT
            acronym.startsWith("TSS")||acronym.startsWith("DWRT")||acronym in setOf("FAIRWY","NAVLNE","RCTLPT","RCRTCL","ISTZNE","RDOCAL") ->NauticalFeatureKind.TRAFFIC
            acronym=="M_COVR"->NauticalFeatureKind.COVERAGE
            acronym in setOf("M_QUAL","M_ACCY","M_SREL","M_SDAT","M_VDAT") ->NauticalFeatureKind.QUALITY
            else->NauticalFeatureKind.OTHER
        }
        fun attributePairs(b:ByteArray,lexical:Int):Map<Int,String> {
            val values=linkedMapOf<Int,String>();var at=0
            while(at<b.size) {
                require(at+2<=b.size) {"S57_ATTRIBUTE_TRUNCATED"};val code=b.u16(at);at+=2;val start=at
                if(lexical==2) {while(at+1<b.size&&!(b.u8(at)==31&&b.u8(at+1)==0))at+=2;require(at+1<b.size) {"S57_NATIONAL_TEXT_TERMINATOR"};values[code]=String(b,start,at-start,Charsets.UTF_16LE).removePrefix("\uFEFF");at+=2}
                else {while(at<b.size&&b.u8(at)!=31)at++;require(at<b.size) {"S57_ATTRIBUTE_TERMINATOR"};values[code]=String(b,start,at-start,Charsets.ISO_8859_1);at++}
            }
            return values
        }
        private fun encodeAttributes(values:Map<Int,String>,lexical:Int):ByteArray=java.io.ByteArrayOutputStream().use {out->values.forEach {(key,value)->out.write(key and 255);out.write(key ushr 8);out.write(value.toByteArray(if(lexical==2)Charsets.UTF_16LE else Charsets.ISO_8859_1));out.write(31);if(lexical==2)out.write(0)};out.toByteArray()}
        fun tuples(b:ByteArray,width:Int):List<ByteArray> {
            if(width>0) {require(b.size%width==0) {"S57_FIXED_FIELD_TRUNCATED"};return (b.indices step width).map {b.copyOfRange(it,it+width)}}
            val result=mutableListOf<ByteArray>();var at=0
            while(at<b.size) {val start=at;at+=9;require(at<=b.size) {"S57_REFERENCE_TRUNCATED"};while(at<b.size&&b.u8(at)!=31)at++;require(at<b.size) {"S57_REFERENCE_TERMINATOR"};result+=b.copyOfRange(start,++at)}
            return result
        }
        private fun join(parts:List<ByteArray>):ByteArray=java.io.ByteArrayOutputStream().use {out->parts.forEach(out::write);out.toByteArray()}
    }
}
internal class S57Dictionaries(objectsText:String,attributesText:String) {
    val objects=parse(objectsText,2);val attributes=parse(attributesText,2)
    private fun parse(text:String,column:Int)=text.lineSequence().mapNotNull {line->val fields=csv(line);val id=fields.firstOrNull()?.toIntOrNull() ?: return@mapNotNull null;fields.getOrNull(column)?.let {id to it}}.toMap()
    private fun csv(line:String):List<String> {val result=mutableListOf<String>();val item=StringBuilder();var quote=false;var i=0;while(i<line.length) {val c=line[i++];when {c=='"'&&quote&&i<line.length&&line[i]=='"'->{item.append('"');i++};c=='"'->quote=!quote;c==','&&!quote->{result+=item.toString();item.clear()};else->item.append(c)}};result+=item.toString();return result}
}
private class Cursor(val bytes:ByteArray,var at:Int=0) {
    fun skip(n:Int){require(at+n<=bytes.size) {"S57_HEADER_TRUNCATED"};at+=n}
    fun byte()=bytes.u8(at).also {skip(1)}
    fun short()=bytes.u16(at).also {skip(2)}
    fun fixed(n:Int)=bytes.ascii(at,n).also {skip(n)}
    fun text():String {val start=at;while(at<bytes.size&&bytes.u8(at)!=31)at++;require(at<bytes.size) {"S57_TEXT_TERMINATOR"};return String(bytes,start,at++-start,Charsets.ISO_8859_1)}
}
internal fun ByteArray.u8(at:Int):Int {require(at in indices) {"S57_TRUNCATED_BINARY"};return this[at].toInt() and 255}
internal fun ByteArray.u16(at:Int)=u8(at) or (u8(at+1) shl 8)
internal fun ByteArray.u32(at:Int):Long=u8(at).toLong() or (u8(at+1).toLong() shl 8) or (u8(at+2).toLong() shl 16) or (u8(at+3).toLong() shl 24)
internal fun ByteArray.i32(at:Int)=u32(at).toInt()
private fun ByteArray.ascii(at:Int,n:Int):String {require(at>=0&&at+n<=size) {"S57_TRUNCATED_ASCII"};return String(this,at,n,Charsets.US_ASCII)}
private fun java.io.InputStream.readFully(b:ByteArray,at:Int,count:Int) {var position=at;while(position<at+count) {val read=read(b,position,at+count-position);require(read>0) {"S57_TRUNCATED_RECORD"};position+=read}}
