package com.yokuli.runtime.marine.ipc

import android.content.ComponentName
import android.net.Uri
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.yokuli.anchorwatch.domain.anchorage.AnchorageGeometry
import com.yokuli.anchorwatch.domain.sonar.SonarGrid
import com.yokuli.anchorwatch.domain.sonar.SonarCell
import com.yokuli.anchorwatch.data.database.SonarGridCellEntity
import com.yokuli.anchorwatch.domain.vessel.*
import com.yokuli.runtime.contract.ais.AisCommand
import com.yokuli.runtime.contract.chart.ChartCommandResult
import java.io.File

/**
 * 私有兼容传输编码：只能由本 APK 已编译端口声明决定类型，不接受远端 Java 类名。
 * MainUiState 尚属同版本兼容投影；这里的显式 sealed 标签不会变成公开 ROM schema。
 */
internal object MarineCoreCodec {
    val gson: Gson = GsonBuilder()
        .serializeNulls()
        .enableComplexMapKeySerialization()
        .serializeSpecialFloatingPointValues()
        .registerTypeHierarchyAdapter(Uri::class.java, StringCodec<Uri>({ it.toString() }, Uri::parse))
        .registerTypeAdapter(File::class.java, StringCodec<File>({ it.absolutePath }, ::File))
        .registerTypeAdapter(ComponentName::class.java, StringCodec<ComponentName>({ it.flattenToString() }, { requireNotNull(ComponentName.unflattenFromString(it)) }))
        .registerTypeAdapter(Unit::class.java, object : TypeAdapter<Unit>() {
            override fun write(out: JsonWriter, value: Unit?) { out.nullValue() }
            override fun read(input: JsonReader) { input.skipValue() }
        })
        .registerTypeAdapterFactory(TaggedFamily(AisCommand::class.java, mapOf(
            "preferences" to AisCommand.UpdatePreferences::class.java,
            "watch" to AisCommand.Watch::class.java, "alias" to AisCommand.Alias::class.java,
            "acknowledge" to AisCommand.Acknowledge::class.java, "snooze" to AisCommand.Snooze::class.java,
            "retain" to AisCommand.RetainTarget::class.java,
        )))
        .registerTypeAdapterFactory(TaggedFamily(ChartCommandResult::class.java, mapOf(
            "accepted" to ChartCommandResult.Accepted::class.java, "saved" to ChartCommandResult.Saved::class.java,
            "failed" to ChartCommandResult.Failed::class.java, "busy" to ChartCommandResult.Busy::class.java,
        ), mapOf("busy" to ChartCommandResult.Busy)))
        .registerTypeAdapterFactory(TaggedFamily(VesselReference::class.java, mapOf(
            "true" to VesselReference.TrueNorth::class.java, "magnetic" to VesselReference.MagneticNorth::class.java,
            "water" to VesselReference.WaterReferenced::class.java, "ground" to VesselReference.GroundReferenced::class.java,
            "vessel" to VesselReference.VesselRelative::class.java, "depth" to VesselReference.Depth::class.java,
        ), mapOf("true" to VesselReference.TrueNorth, "magnetic" to VesselReference.MagneticNorth,
            "water" to VesselReference.WaterReferenced, "ground" to VesselReference.GroundReferenced,
            "vessel" to VesselReference.VesselRelative)))
        .registerTypeAdapterFactory(TaggedFamily(VesselProvenance::class.java, mapOf(
            "nmea" to VesselProvenance.Nmea::class.java, "phone" to VesselProvenance.PhoneSensor::class.java,
            "derived" to VesselProvenance.Derived::class.java,
        )))
        .registerTypeAdapterFactory(TaggedFamily(AnchorageGeometry::class.java, mapOf(
            "point" to AnchorageGeometry.Point::class.java, "circle" to AnchorageGeometry.Circle::class.java,
            "polygon" to AnchorageGeometry.Polygon::class.java, "multipolygon" to AnchorageGeometry.MultiPolygon::class.java,
        )))
        .registerTypeAdapterFactory(CandidateCodec())
        .registerTypeAdapter(SonarGrid::class.java, object : JsonSerializer<SonarGrid>, JsonDeserializer<SonarGrid> {
            override fun serialize(value: SonarGrid, type: java.lang.reflect.Type, context: JsonSerializationContext) = JsonObject().apply {
                addProperty("cellSizeMeters", value.cellSizeMeters)
                add("cells", context.serialize(value.cells.values.toList()))
            }
            override fun deserialize(json: JsonElement, type: java.lang.reflect.Type, context: JsonDeserializationContext): SonarGrid {
                val obj = json.asJsonObject
                val size = obj["cellSizeMeters"].asDouble
                val cells = context.deserialize<List<SonarCell>>(obj["cells"], object : TypeToken<List<SonarCell>>() {}.type)
                return SonarGrid.fromPersisted(cells.map { SonarGridCellEntity("IPC", 0, it.xIndex, it.yIndex, size, it.depthMeters, it.uncertaintyMeters, it.sampleCount, 0) }, size)
            }
        }).create()

    private class StringCodec<T>(private val encode: (T) -> String, private val decode: (String) -> T) : JsonSerializer<T>, JsonDeserializer<T> {
        override fun serialize(value: T, type: java.lang.reflect.Type, context: JsonSerializationContext) = JsonPrimitive(encode(value))
        override fun deserialize(value: JsonElement, type: java.lang.reflect.Type, context: JsonDeserializationContext): T = decode(value.asString)
    }

    private class TaggedFamily<T : Any>(
        private val base: Class<T>, private val kinds: Map<String, Class<out T>>,
        private val singletons: Map<String, T> = emptyMap(),
    ) : TypeAdapterFactory {
        override fun <R> create(gson: Gson, type: TypeToken<R>): TypeAdapter<R>? {
            if (type.rawType != base && kinds.values.none { it == type.rawType }) return null
            val adapters = kinds.mapValues { gson.getDelegateAdapter(this, TypeToken.get(it.value)) }
            return object : TypeAdapter<R>() {
                override fun write(out: JsonWriter, value: R?) {
                    if (value == null) { out.nullValue(); return }
                    val tag = kinds.entries.firstOrNull { it.value == value.javaClass }?.key ?: error("Unregistered marine variant")
                    @Suppress("UNCHECKED_CAST") val adapter = adapters.getValue(tag) as TypeAdapter<R>
                    val obj = adapter.toJsonTree(value).asJsonObject
                    obj.addProperty("wireKind", tag)
                    gson.getAdapter(JsonElement::class.java).write(out, obj)
                }
                override fun read(input: JsonReader): R? {
                    val value = gson.getAdapter(JsonElement::class.java).read(input)
                    if (value.isJsonNull) return null
                    val tag = value.asJsonObject.remove("wireKind")?.asString ?: error("Missing marine variant")
                    require(tag in kinds) { "Unknown marine variant" }
                    require(type.rawType == base || type.rawType == kinds[tag]) { "Unexpected marine variant" }
                    @Suppress("UNCHECKED_CAST") return (singletons[tag] ?: adapters.getValue(tag).fromJsonTree(value)) as R
                }
            }
        }
    }

    /** 星投影候选也按 metric 恢复真实 Position / String / Double，绝不落成 LinkedTreeMap。 */
    private class CandidateCodec : TypeAdapterFactory {
        override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            if (type.rawType != VesselSourceCandidate::class.java) return null
            fun adapter(metric: VesselMetricId): TypeAdapter<Any> {
                val value = when (metric) {
                    VesselMetricId.POSITION -> VesselPosition::class.java
                    VesselMetricId.DESTINATION_WAYPOINT -> String::class.java
                    else -> Double::class.javaObjectType
                }
                @Suppress("UNCHECKED_CAST") return gson.getDelegateAdapter(this, TypeToken.getParameterized(VesselSourceCandidate::class.java, value)) as TypeAdapter<Any>
            }
            return object : TypeAdapter<T>() {
                override fun write(out: JsonWriter, value: T?) {
                    if (value == null) out.nullValue() else adapter((value as VesselSourceCandidate<*>).metric).write(out, value)
                }
                override fun read(input: JsonReader): T? {
                    val json = gson.getAdapter(JsonElement::class.java).read(input)
                    if (json.isJsonNull) return null
                    val metric = VesselMetricId.valueOf(json.asJsonObject["metric"].asString)
                    @Suppress("UNCHECKED_CAST") return adapter(metric).fromJsonTree(json) as T
                }
            }
        }
    }
}
