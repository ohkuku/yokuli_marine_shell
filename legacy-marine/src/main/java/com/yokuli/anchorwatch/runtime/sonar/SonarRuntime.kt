package com.yokuli.anchorwatch.runtime.sonar

import com.yokuli.anchorwatch.data.sonar.SonarSurveyRecorder
import com.yokuli.anchorwatch.domain.sonar.TideMode
import com.yokuli.anchorwatch.runtime.RuntimeOwner
import com.yokuli.anchorwatch.runtime.RuntimeResourceManager
import javax.inject.Inject
import javax.inject.Singleton

data class SonarRuntimeResult(val started:Boolean,val title:String?=null,val message:String?=null)

/** 仅保留历史存储兼容边界。当前产品不启用声纳采集，NMEA 水深读数继续独立工作。 */
@Singleton class SonarRuntime @Inject constructor(
    private val recorder:SonarSurveyRecorder,
    private val resources:RuntimeResourceManager,
) {
    val status get()=recorder.status
    suspend fun restore():Boolean { stop();return false }
    suspend fun start(name:String,tideMode:TideMode,manualTideOffsetMeters:Double,tideStationId:String?=null,demoWatchRunning:Boolean=false)=SonarRuntimeResult(false,"Sonar survey unavailable","Sonar surveying is not part of this version.")
    suspend fun stop(){recorder.stop("Archived after sonar survey removal");resources.release(RuntimeOwner.SONAR_MAPPING)}
    suspend fun watchdog():SonarRuntimeResult?=null
    fun shutdown(){resources.release(RuntimeOwner.SONAR_MAPPING)}
}
