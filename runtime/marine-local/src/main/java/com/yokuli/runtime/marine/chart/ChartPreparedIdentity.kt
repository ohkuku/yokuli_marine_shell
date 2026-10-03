package com.yokuli.runtime.marine.chart

import com.google.gson.Gson
import com.yokuli.runtime.contract.chart.ChartDataset

/** 内容和通行语义才改变预编译身份；安装目录/本机数据集 ID/显示名称不参与。 */
internal fun chartPreparedIdentity(content:String,dataset:ChartDataset):String {
    val gson=Gson()
    val policy=dataset.cells.sortedBy{it.cellId}.joinToString("|") {cell->
        // 不含名称、说明等展示元数据；所有会改变语义所有权或资格的目录字段都进入产品键。
        gson.toJson(listOf(cell.cellId,cell.priority,cell.priorityExplicit,cell.cancelled,cell.edition,cell.update,
        cell.compilationScale,cell.intendedUsage,cell.linzScaleBand,cell.referenceOnly,cell.hasUnsupportedSemantic,
        cell.quality,cell.issues,cell.wholeCellIssues,cell.coverage.map{listOf(it.featureId,it.covered,it.compilationScale,it.detailTier)}))
    }
    val eligibility=gson.toJson(dataset.eligibility)
    return java.security.MessageDigest.getInstance("SHA-256").digest((content+"\n"+policy+"\n"+eligibility).toByteArray()).joinToString(""){"%02x".format(it)}
}
