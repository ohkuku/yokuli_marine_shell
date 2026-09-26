package com.yokuli.marine.shell.rebuild.data

import android.content.Context
import android.provider.Settings
import android.util.AtomicFile
import com.yokuli.anchorwatch.domain.vessel.VesselDataFreshness
import com.yokuli.anchorwatch.domain.vessel.VesselDataQuality
import java.io.*
import java.security.MessageDigest

/** 有界的显示历史缓存，不是当前观测、来源设置或航行记录的持久化入口。 */
internal class ReadingHistoryCache(context:Context) {
    private val file=AtomicFile(File(context.filesDir,"instrument-history-v1.bin"))
    private val boot=runCatching {Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1)}.getOrDefault(-1)
    data class Loaded(val readings:Map<String,List<Reading>>,val savedAtUtc:Long?)

    fun read(now:Long):Loaded {
        if(!file.baseFile.exists()&&!File(file.baseFile.path+".bak").exists())return Loaded(emptyMap(),null)
        file.openRead().use {raw->
            require(raw.channel.size()<=MAX_BYTES) {"History cache exceeds its size limit"}
            DataInputStream(BufferedInputStream(raw)).use {input->
                require(input.readInt()==MAGIC&&input.readInt()==1) {"Unsupported history cache"}
                val savedBoot=input.readInt()
                val savedAt=input.readLong()
                require(savedAt>0) {"Invalid history save time"}
                // 旧单调时间只对本次开机有效；无法确认 boot 的设备同样不恢复旧 elapsed。
                // 仍完整解析旧boot文件，损坏文件不能因“无需恢复”被误判成功后覆盖。
                val sameBoot=boot>=0&&savedBoot==boot
                val strings=List(input.readInt().also {require(it in 0..MAX_STRINGS)}) {input.readUTF()}
                fun string():String {val index=input.readInt();require(index in strings.indices);return strings[index]}
                val metrics=input.readInt().also {require(it in 0..MAX_METRICS)}
                val readings=linkedMapOf<String,List<Reading>>()
                val seen=hashSetOf<String>()
                repeat(metrics) {
                    val key=string();require(key.isNotBlank()&&seen.add(key))
                    val count=input.readInt().also {require(it in 0..MAX_POINTS)}
                    val points=ArrayList<Reading>(count)
                    repeat(count) {
                        val value=input.readDouble();val elapsed=input.readLong();val utc=input.readLong();val valid=input.readLong()
                        val unit=string();val source=string();val sourceKey=string();val continuity=string();val session=string()
                        val freshness=VesselDataFreshness.entries.getOrNull(input.readUnsignedByte()) ?: error("Invalid historical freshness")
                        val quality=VesselDataQuality.entries.getOrNull(input.readUnsignedByte()) ?: error("Invalid historical quality")
                        require(value.isFinite()&&elapsed>=0&&utc>0&&valid>0&&session.isNotBlank()) {"Invalid history sample"}
                        if(sameBoot&&elapsed in (now-WINDOW_MILLIS).coerceAtLeast(0)..now)points+=Reading(value,unit,source,elapsed,freshness,quality,sourceKey,valid,continuity,utc,session)
                    }
                    if(points.isNotEmpty())readings[key]=points.sortedBy {it.elapsed}
                }
                require(input.read()==-1) {"Unexpected trailing history data"}
                return Loaded(readings,savedAt)
            }
        }
    }

    fun write(snapshot:Map<String,List<Reading>>,savedAt:Long) {
        require(snapshot.size<=MAX_METRICS)
        val dictionary=linkedMapOf<String,Int>()
        fun declare(value:String) {if(value !in dictionary) {require(dictionary.size<MAX_STRINGS&&value.length<=16_384);dictionary[value]=dictionary.size}}
        snapshot.forEach {(key,values)->
            require(values.size<=MAX_POINTS);declare(key)
            values.forEach {point->
                require(point.observedUtcMillis!=null&&!point.historySessionKey.isNullOrBlank())
                listOf(point.unit,point.source,point.sourceKey,point.continuityKey,point.historySessionKey!!).forEach(::declare)
            }
        }
        val stream=file.startWrite()
        val expected=MessageDigest.getInstance("SHA-256")
        var writtenBytes=0L
        try {
            val output=DataOutputStream(BufferedOutputStream(object:FilterOutputStream(stream) {
                override fun write(value:Int) {require(++writtenBytes<=MAX_BYTES);out.write(value);expected.update(value.toByte())}
                override fun write(data:ByteArray,offset:Int,length:Int) {writtenBytes+=length;require(writtenBytes<=MAX_BYTES);out.write(data,offset,length);expected.update(data,offset,length)}
            }))
            output.writeInt(MAGIC);output.writeInt(1);output.writeInt(boot);output.writeLong(savedAt)
            output.writeInt(dictionary.size);dictionary.keys.forEach(output::writeUTF)
            fun string(value:String)=output.writeInt(dictionary.getValue(value))
            output.writeInt(snapshot.size)
            snapshot.forEach {(key,values)->
                string(key);output.writeInt(values.size)
                values.forEach {point->
                    output.writeDouble(point.value);output.writeLong(point.elapsed);output.writeLong(point.observedUtcMillis!!);output.writeLong(point.validForMillis)
                    string(point.unit);string(point.source);string(point.sourceKey);string(point.continuityKey);string(point.historySessionKey!!)
                    output.writeByte(point.freshness.ordinal);output.writeByte(point.quality.ordinal)
                }
            }
            output.flush()
            stream.fd.sync()
            file.finishWrite(stream)
            // AtomicFile 的部分失败只记录日志；退出回执必须证明正式文件确实是刚写出的完整内容。
            check(file.baseFile.length()==writtenBytes) {"History commit is incomplete"}
            val actual=MessageDigest.getInstance("SHA-256")
            file.openRead().use {input->
                val buffer=ByteArray(8192)
                var confirmedBytes=0L
                while(true) {
                    val count=input.read(buffer)
                    if(count<0)break
                    confirmedBytes+=count
                    check(confirmedBytes<=writtenBytes) {"History commit is incomplete"}
                    actual.update(buffer,0,count)
                }
                check(confirmedBytes==writtenBytes) {"History commit is incomplete"}
            }
            check(actual.digest().contentEquals(expected.digest())) {"History commit differs from the saved snapshot"}
        } catch(error:Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    companion object {
        const val WINDOW_MILLIS=15*60_000L
        const val MAX_POINTS=1800
        const val MAX_METRICS=64
        private const val MAX_STRINGS=8192
        private const val MAX_BYTES=32L*1024*1024
        private const val MAGIC=0x59484953
    }
}

/** 读写故障保留旧文件；界面明确重试，不把未落盘的历史说成已保存。 */
data class HistoryStorageState(
    val loading:Boolean=false,
    val pending:Boolean=false,
    val lastSavedUtcMillis:Long?=null,
    val readIssue:String?=null,
    val writeIssue:String?=null,
)
