package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.shell.rebuild.OsStore
import com.yokuli.runtime.contract.chart.ChartDataset
import com.yokuli.runtime.contract.chart.ChartStorageUsage
import kotlinx.coroutines.CancellationException
import java.util.Locale

/** 展开才触发一次可取消的 Core 文件分账；页面不持文件路径，也不轮询磁盘。 */
@Composable internal fun ChartPreparedStorageSummary(os:OsStore,dataset:ChartDataset) {
    var expanded by remember(dataset.id){mutableStateOf(false)}
    var refresh by remember(dataset.id){mutableIntStateOf(0)}
    var usage by remember(dataset.id,dataset.revision){mutableStateOf<ChartStorageUsage?>(null)}
    var loading by remember(dataset.id){mutableStateOf(false)}
    var failed by remember(dataset.id){mutableStateOf(false)}
    LaunchedEffect(expanded,dataset.id,dataset.revision,refresh) {
        if(!expanded)return@LaunchedEffect
        loading=true;failed=false
        try {usage=os.maps.charts.readStorageUsage(dataset.id)}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){failed=true;usage=null}
        finally{loading=false}
    }
    MenuRow(os.t("储存占用","Storage usage"),icon="folder"){expanded=!expanded}
    AnimatedVisibility(expanded) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(loading)MetroProgress(os.t("正在读取文件大小","Reading file sizes"))
            if(failed) {
                Label(os.t("暂时无法读取占用，请稍后重试","Storage usage is unavailable. Try again shortly."),13,LocalMetro.current.muted)
                MetroButton(os.t("重试","Retry"),{refresh++})
            }
            usage?.let{value->
                val local=value.localOriginalBytes+value.factsAndIndexBytes+value.terrainBytes+value.navigationBytes+value.otherLocalBytes
                StorageSizeRow(os.t("本机文件","Local files"),local)
                StorageSizeRow(os.t("原始资料","Original data"),value.localOriginalBytes)
                StorageSizeRow(os.t("查询数据与索引","Query data and indexes"),value.factsAndIndexBytes)
                StorageSizeRow(os.t("三维区域","Prepared 3D areas"),value.terrainBytes)
                StorageSizeRow(os.t("规划区域","Prepared routing areas"),value.navigationBytes)
                if(value.otherLocalBytes>0)StorageSizeRow(os.t("其他资料文件","Other data files"),value.otherLocalBytes)
                if(value.linkedOriginalBytes>0) {
                    StorageSizeRow(os.t("关联的外部原件","Linked external originals"),value.linkedOriginalBytes)
                    Label(os.t("关联原件不计入上方本机文件；这里显示来源版本记录的大小。","Linked originals are excluded from local files. Their recorded source size is shown."),12,LocalMetro.current.muted)
                }
                if(value.sourceIssue!=null)Label(os.t("部分外部原件暂时无法读取，本机占用仍可查看。","Some external originals cannot be read. Local file sizes are still available."),12,LocalMetro.current.muted)
                Label(os.t("按文件大小统计；准备进行中时可能继续增长。","File sizes at the time of this reading; preparation may add more."),12,LocalMetro.current.muted)
                MetroButton(os.t("刷新","Refresh"),{refresh++},enabled=!loading)
            }
        }
    }
}

@Composable private fun StorageSizeRow(title:String,bytes:Long) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Label(title,13,modifier=Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Label(storageBytes(bytes),13,LocalMetro.current.muted)
    }
}
private fun storageBytes(bytes:Long):String {
    if(bytes<1024)return "$bytes B"
    val units=listOf("KiB","MiB","GiB","TiB")
    var value=bytes/1024.0;var index=0
    while(value>=1024&&index<units.lastIndex){value/=1024;index++}
    return String.format(Locale.ROOT,if(value<10)"%.2f %s"else "%.1f %s",value,units[index])
}
