package com.yokuli.runtime.contract.chart

/** 按需读取的当前版本文件字节分账；外部关联原件不计入 Yokuli 本地占用，统计不读取原件内容。 */
data class ChartStorageUsage(
    val datasetId:String,
    val revision:Long,
    val measuredAtUtc:Long,
    val localOriginalBytes:Long,
    /** 当前来源版本记录的外部原件大小；sourceIssue 非空时不能声称外部文件仍可读取。 */
    val linkedOriginalBytes:Long,
    val factsAndIndexBytes:Long,
    val terrainBytes:Long,
    val navigationBytes:Long,
    val otherLocalBytes:Long,
    val sourceIssue:String?=null,
)
