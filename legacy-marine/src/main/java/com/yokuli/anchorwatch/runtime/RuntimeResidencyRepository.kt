package com.yokuli.anchorwatch.runtime

import android.content.Context
import com.yokuli.runtime.contract.RuntimeResidencyPhase
import com.yokuli.runtime.contract.RuntimeResidencyState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Android 服务、来源和系统入口共享一份退出闩锁；先落盘再释放，不靠杀进程模拟退出。 */
@Singleton
class RuntimeResidencyRepository @Inject constructor(@ApplicationContext context: Context) {
    private val storage = context.getSharedPreferences("system_runtime_residency", Context.MODE_PRIVATE)
    @Volatile var explicitlyStopped: Boolean = storage.getBoolean("explicit_stop", false)
        private set
    private val mutable = MutableStateFlow(RuntimeResidencyState(requested = storage.getBoolean("run_requested", false) && !explicitlyStopped, explicitlyStopped = explicitlyStopped))
    val state = mutable.asStateFlow()
    suspend fun startRequest() = withContext(Dispatchers.IO) {
        check(storage.edit().putBoolean("run_requested", true).putBoolean("explicit_stop", false).commit()) { "RUNTIME_INTENT_NOT_SAVED" }
        explicitlyStopped = false
        mutable.update { it.copy(requested = true, explicitlyStopped = false, phase = RuntimeResidencyPhase.STARTING, problem = null) }
    }
    suspend fun stopRequest() = withContext(Dispatchers.IO) {
        check(storage.edit().putBoolean("run_requested", false).putBoolean("explicit_stop", true).commit()) { "EXIT_INTENT_NOT_SAVED" }
        explicitlyStopped = true
        mutable.update { it.copy(requested = false, explicitlyStopped = true, phase = RuntimeResidencyPhase.STOPPING, problem = null) }
    }
    fun phase(phase: RuntimeResidencyPhase, problem: String? = null) { mutable.update {
        // 关闭中的异步能力回调不能将状态重新标成运行；失败只由实际退出路径保留为BLOCKED。
        if ((it.phase == RuntimeResidencyPhase.STOPPING || it.explicitlyStopped) && phase in setOf(RuntimeResidencyPhase.STARTING, RuntimeResidencyPhase.RUNNING)) it
        else it.copy(phase = phase, problem = problem)
    } }
    fun capabilitiesReady(problem: String?) { mutable.update {
        if (!it.requested || it.explicitlyStopped || it.phase == RuntimeResidencyPhase.STOPPING) it
        else it.copy(phase = if(problem == null) RuntimeResidencyPhase.RUNNING else RuntimeResidencyPhase.BLOCKED, problem = problem)
    } }
    fun resources(value: RuntimeResourceSnapshot, inputs: Int, outputs: Int, sharing: Boolean) {
        mutable.update { it.copy(phoneLocation = value.needsSystemLocation, phoneMotion = value.phoneMotionActive,
            phoneHeading = value.phoneHeadingActive, phonePressure = value.phonePressureActive,
            inputConnections = inputs, outputConnections = outputs, sharing = sharing) }
    }
}
