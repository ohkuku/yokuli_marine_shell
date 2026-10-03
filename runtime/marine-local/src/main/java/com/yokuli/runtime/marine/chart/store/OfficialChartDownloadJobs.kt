package com.yokuli.runtime.marine.chart.store

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*

/** 接收器只提交系统作业；大文件校验不能占用广播的短生命周期。 */
class OfficialChartDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE && intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) >= 0) {
            OfficialChartDownloadJobs.schedule(context)
        }
    }
}

internal object OfficialChartDownloadJobs {
    private const val JOB_ID = 7652
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        // 重复广播/轮询不能重置正在校验的大文件。
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, OfficialChartVerificationJob::class.java))
            .setPersisted(true).setMinimumLatency(0).setOverrideDeadline(1000)
            .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OfficialChartDownloadEntryPoint { fun officialChartStore(): LocalOfficialChartStore }

/** 在默认 Core 进程恢复账本与核验；系统回收时保留 VERIFYING，下次从文件重新核验。 */
class OfficialChartVerificationJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var task: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        task = scope.launch {
            var retry = false
            try {
                val store = EntryPointAccessors.fromApplication(applicationContext, OfficialChartDownloadEntryPoint::class.java).officialChartStore()
                store.finishSystemDownloads()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { retry = true }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { task?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
