package com.yokuli.marine.shell.rebuild.chart

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.runtime.*
import com.yokuli.runtime.contract.chart.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/** Core 完成文件生成后，由原海图目录所有者登记；离开海图册页面不丢失完成回执。 */
internal class GeneratedChartLinker(context:Context,private val scope:CoroutineScope,private val library:ChartLibrary,private val bundles:ChartBundleStore) {
    private val receipt=AtomicFile(File(context.filesDir,"generated-chart-receipt.txt"))
    private var worker:Job?=null
    private val retry=MutableStateFlow(0)
    var issue by mutableStateOf<String?>(null)
        private set
    fun retry(){retry.value++}
    fun connect(charts:ChartDataService) {
        worker?.cancel()
        worker=scope.launch {
            var acknowledged=withContext(Dispatchers.IO) {runCatching {receipt.openRead().bufferedReader().use {it.readText()}}.getOrDefault("")}
            combine(charts.state.map {it.exportJob}.distinctUntilChanged(),retry) {job,_->job}.collectLatest {job->
                if(job==null||!job.chart||job.phase!=ChartExportPhase.COMPLETE||job.requestId==acknowledged)return@collectLatest
                val folder=job.outputFolderUri?:return@collectLatest
                try {
                    snapshotFlow {library.busy}.first {!it}
                    issue=null
                    val owner=if(job.collectionId!=null)bundles.bundles.firstOrNull {it.id==job.collectionId&&it.datasetId==job.datasetId}
                        else bundles.bundles.filter {it.datasetId==job.datasetId}.singleOrNull()
                    requireNotNull(owner){"CHART_GENERATED_PACKAGE_MISSING"}
                    // 输出目录可能还有别人的海图，仅登记这次实际生成的文件。
                    val identity="atlas-"+java.util.UUID.nameUUIDFromBytes("generated:${job.requestId}".toByteArray(Charsets.UTF_8))
                    val linked=library.importBundleSource(Uri.parse(requireNotNull(job.targetUri)),false,identity,Uri.parse(folder))
                    bundles.attachChartFolder(owner.id,linked.id,owned=true)
                    withContext(Dispatchers.IO) {
                        val stream=receipt.startWrite()
                        try {stream.write(job.requestId.toByteArray());receipt.finishWrite(stream)}
                        catch(error:Exception){receipt.failWrite(stream);throw error}
                    }
                    acknowledged=job.requestId
                }catch(cancel:CancellationException){throw cancel}
                catch(error:Exception){issue=error.message?:"CHART_GENERATED_LINK_FAILED"}
            }
        }
    }
}
