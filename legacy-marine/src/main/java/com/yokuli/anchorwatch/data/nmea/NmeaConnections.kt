package com.yokuli.anchorwatch.data.nmea

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yokuli.anchorwatch.domain.model.NmeaConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** 用户连接的持久身份不随 IP、端口修改或重连代次改变；每条连接可独立接收、发送或双向。 */
data class NmeaConnectionSpec(
    val id:String=java.util.UUID.randomUUID().toString(),
    val name:String="NMEA",
    val protocol:Protocol=Protocol.TCP,
    val host:String="",
    val port:Int=10110,
    val localPort:Int=10110,
    val receive:Boolean=true,
    val send:Boolean=false,
    val feed:NmeaFeed=NmeaFeed.SYSTEM,
    val forwardFrom:Set<String> = emptySet(),
    val sentenceTypes:Set<String> = emptySet(),
    val autoReconnect:Boolean=true,
    val requireChecksum:Boolean=true,
    val priority:Int=0,
    /** 此目的地允许分享的能力；空集合为全部关闭，null 兼容旧版本。 */
    val capabilities:Set<String>?=null,
) {
    fun profile()=ConnectionProfile(name,protocol,host,port,requireChecksum,autoReconnect,stableId=id,localPort=if(receive)localPort else 0)
}
/** SYSTEM 编码全局选中的数据；PHONE 仅手机能力；RAW 按输入连接转发原始数据。 */
enum class NmeaFeed { SYSTEM, PHONE, RAW }
/** 原始报文携带实际发件人和连接代次，能力筛选与防回送不能丢弃这些来源信息。 */
data class NmeaRawFrame(val connectionId:String,val generation:Long,val peer:String,val sentence:String,val receivedElapsedRealtime:Long)
/** 连接实况：requested 是用户意图，state 是传输事实，writtenSentences 只计真正写出。 */
data class NmeaConnectionSnapshot(
    val spec:NmeaConnectionSpec,
    val requested:Boolean=false,
    val state:NmeaConnectionState=NmeaConnectionState.DISCONNECTED,
    val transport:NmeaTransportDiagnostics=NmeaTransportDiagnostics(),
    val diagnostics:NmeaDiagnostics=NmeaDiagnostics(),
    val writtenSentences:Long=0,
    val droppedSentences:Long=0,
    val lastWrittenElapsed:Long?=null,
    val recentWritten:List<String> = emptyList(),
    val error:String?=null,
)
private val Context.nmeaConnectionsStore by preferencesDataStore("os_nmea_connections")
@Singleton class NmeaConnectionStore @Inject constructor(@ApplicationContext private val context:Context){
    private val key=stringPreferencesKey("connections_v1")
    private val gson=Gson()
    suspend fun read():List<NmeaConnectionSpec>?=context.nmeaConnectionsStore.data.first()[key]?.let{json->runCatching{gson.fromJson<List<NmeaConnectionSpec>>(json,object:TypeToken<List<NmeaConnectionSpec>>(){}.type)}.getOrNull()}
    suspend fun save(values:List<NmeaConnectionSpec>){context.nmeaConnectionsStore.edit{it[key]=gson.toJson(values)}}
}
