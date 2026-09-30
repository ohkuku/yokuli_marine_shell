package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.ChartGeometryKind
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/**
 * LINZ LDS 水文 GIS 导出的显式语义适配，不把普通地形、文件名中的 LINZ 或一个 depth 列当海图。
 * 表名/identifier 必须匹配 Hydro 图层的完整名称和比例尺带，且具备提供方的原始字段。
 * LDS 是由海图派生的参考 GIS 产品；本适配不会把它提升为官方 ENC，也不补造 datum/coverage。
 */
internal object LinzLdsAdapter {
    const val REFERENCE_ISSUE = "REFERENCE_ONLY_LINZ_LDS"
    private const val PROVIDER = "Toitū Te Whenua Land Information New Zealand — LINZ Data Service"
    private const val SOURCE_URL = "https://data.linz.govt.nz/"

    data class Layer(
        val title: String,
        val acronym: String,
        val requiredColumns: Set<String>,
        val geometryTypes: Set<String>,
        val scaleBand: String? = null,
    )

    private val polygons = setOf("POLYGON", "MULTIPOLYGON")
    private val points = setOf("POINT", "MULTIPOINT")
    private val lines = setOf("LINESTRING", "MULTILINESTRING")
    private val commonColumns = setOf("fidn", "sordat", "sorind", "inform")
    private val scaleBands = listOf(
        "1:4k - 1:22k", "1:22k - 1:90k", "1:90k - 1:350k",
        "1:350k - 1:1,500k", "1:1.5mil and smaller",
    )

    // 名称及字段来自 LINZ 官方 layers/{id}/ 元数据，而不是按相似英文猜测类别。
    private val definitions = listOf(
        Layer("Depth area polygon", "DEPARE", setOf("drval1", "drval2", "verdat", "quasou"), polygons),
        Layer("Dredged area polygon", "DRGARE", setOf("drval1", "drval2", "verdat", "quasou"), polygons),
        Layer("Depth contour polyline", "DEPCNT", setOf("valdco", "verdat"), lines),
        Layer("Sounding points", "SOUNDG", setOf("depth", "verdat", "quasou"), points),
        Layer("Land area polygon", "LNDARE", setOf("condtn", "objnam"), polygons),
        Layer("Coverage polygon", "M_COVR", setOf("catcov"), polygons),
        Layer("Quality of data polygon", "M_QUAL", setOf("catzoc", "posacc", "souacc"), polygons),
        Layer("Underwater/awash rock points", "UWTROC", setOf("valsou", "watlev", "verdat"), points),
        Layer("Wreck points", "WRECKS", setOf("catwrk", "valsou", "watlev"), points),
        Layer("Wreck polygon", "WRECKS", setOf("catwrk", "valsou", "watlev"), polygons),
        Layer("Obstruction points", "OBSTRN", setOf("catobs", "valsou", "watlev"), points),
        Layer("Obstruction polygon", "OBSTRN", setOf("catobs", "valsou", "watlev"), polygons),
        Layer("Obstruction polyline", "OBSTRN", setOf("catobs", "valsou", "watlev"), lines),
        Layer("Restricted area polygon", "RESARE", setOf("catrea", "restrn"), polygons),
        Layer("Bridge polygon", "BRIDGE", setOf("catbrg", "verclr"), polygons),
        Layer("Bridge polyline", "BRIDGE", setOf("catbrg", "verclr"), lines),
        Layer("Cable, overhead polyline", "CBLOHD", setOf("verclr"), lines),
        Layer("Light points", "LIGHTS", setOf("catlit", "colour"), points),
        // 未测量区有明确危险语义，但当前公共契约没有该类；保留 UNSARE 由保守分析拒绝放行。
        Layer("Unsurveyed area polygon", "UNSARE", emptySet(), polygons),
        Layer("Unsurveyed area polygons", "UNSARE", emptySet(), polygons),
    )
    private val names = buildMap {
        definitions.forEach { definition ->
            scaleBands.forEach { band ->
                val title = "${definition.title} (Hydro, $band)"
                put(normalize(title), definition.copy(title = title, scaleBand = band))
            }
        }
    }

    fun recognizeTitle(title:String):Layer? = names[normalize(title)]

    /** 仅用于新资料文件的默认顺序；不写入对象的 CSCALE 或 compilationScale。 */
    fun scaleBandSortDenominator(scaleBand:String?):Int? = when(scaleBand) {
        scaleBands[0] -> 4_000
        scaleBands[1] -> 22_000
        scaleBands[2] -> 90_000
        scaleBands[3] -> 350_000
        scaleBands[4] -> 1_500_000
        else -> null
    }

    fun recognize(tableName: String, identifier: String?, columns: List<String>, geometryType: String): Layer? {
        val matches = listOfNotNull(identifier, tableName).mapNotNull { raw ->
            // LDS 导出表名会用短横线/下划线，部分工具还保留 linz-data- 与图层 ID 前缀。
            val name = normalize(raw).removePrefix("linzdata").removePrefix("linz")
                .replace(Regex("^[0-9]+"), "")
            names[name]
        }.distinctBy { Triple(it.acronym,it.geometryTypes,it.scaleBand) }
        require(matches.size <= 1) { "LINZ_LDS_LAYER_IDENTITY_CONFLICT:$tableName" }
        val layer = matches.singleOrNull() ?: return null
        val available = columns.map { it.lowercase(Locale.ROOT) }.toSet()
        require(available.containsAll(commonColumns + layer.requiredColumns)) { "LINZ_LDS_SCHEMA_MISSING:$tableName" }
        require(geometryType in layer.geometryTypes || geometryType == "GEOMETRY") { "LINZ_LDS_GEOMETRY_MISMATCH:$tableName" }
        return layer
    }

    fun acceptsGeometry(layer: Layer, kind: ChartGeometryKind): Boolean = when (kind) {
        ChartGeometryKind.POLYGON -> "POLYGON" in layer.geometryTypes
        ChartGeometryKind.POINT, ChartGeometryKind.MULTIPOINT -> "POINT" in layer.geometryTypes
        ChartGeometryKind.LINE -> "LINESTRING" in layer.geometryTypes
        ChartGeometryKind.NONE -> true // 无几何由共用导入器报告，不生成覆盖。
    }

    /** 只补充确定的对象类/单位与来源；保留原 S-57 属性及 fidn，不从比例尺带猜编制比例尺。 */
    fun adapt(layer: Layer, attributes: MutableMap<String, String>): List<String> {
        val issues = mutableListOf(REFERENCE_ISSUE)
        val fields = attributes.mapKeys { it.key.lowercase(Locale.ROOT) }
        val explicitClass = fields["object_class"]?.trim()?.uppercase(Locale.ROOT)
        if (!explicitClass.isNullOrBlank() && explicitClass != layer.acronym) {
            issues += "UNINTERPRETED_LINZ_OBJECT_CLASS_CONFLICT"
            attributes["LINZ_EXPECTED_OBJECT_CLASS"] = layer.acronym
        } else attributes["object_class"] = layer.acronym
        attributes["LINZ_LDS_LAYER"] = layer.title
        layer.scaleBand?.let {band->
            attributes["LINZ_LDS_SCALE_BAND"]=band
            scaleBandSortDenominator(band)?.let {scale->
                attributes["YOKULI_DETAIL_SCALE"]=scale.toString()
                com.yokuli.runtime.contract.chart.detailTierForScale(scale)?.let {attributes["YOKULI_DETAIL_TIER"]=it.toString()}
            }
        }
        attributes["LINZ_LDS_PROVIDER"] = PROVIDER
        attributes["LINZ_LDS_SOURCE_URL"] = SOURCE_URL
        attributes["LINZ_LDS_USE"] = "Reference GIS data; does not replace nautical charts and must not be used for navigation"
        attributes["LINZ_LDS_FIDN"] = fields["fidn"].orEmpty()
        if (fields["source"].isNullOrBlank() && fields["sorind"].isNullOrBlank()) attributes["source"] = PROVIDER
        if (fields["depth_unit"].isNullOrBlank() && fields["depuni"].isNullOrBlank()) attributes["depth_unit"] = "m"
        // SOUNDG 在 LDS 中已经拆成二维 Point + depth，而非需要把任意 Z 当测深。
        if (layer.acronym == "SOUNDG") {
            val depth = fields["depth"]?.trim()?.takeIf(String::isNotEmpty)
            if (depth != null) {
                val existing = listOfNotNull(fields["depth_m"], fields["valsou"])
                if (existing.any { it.toDoubleOrNull() != depth.toDoubleOrNull() }) {
                    issues += "UNINTERPRETED_LINZ_SOUNDING_DEPTH_CONFLICT"
                } else attributes["depth_m"] = depth
            }
        }
        return issues
    }

    /** 导出 INTEGER fid 会重排；提供方 fidn 才用于 LDS 快照之间的对象身份。 */
    fun featureKey(attributes: Map<String, String>): String {
        val fidn = attributes.entries.firstOrNull { it.key.equals("fidn", true) }?.value?.trim()
        require(!fidn.isNullOrBlank() && fidn.length <= 512) { "LINZ_LDS_FEATURE_ID_MISSING" }
        return UUID.nameUUIDFromBytes(fidn.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private fun normalize(value: String) = value.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }
}
