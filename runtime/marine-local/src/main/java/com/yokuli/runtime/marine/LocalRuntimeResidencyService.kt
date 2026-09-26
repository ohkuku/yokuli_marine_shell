package com.yokuli.runtime.marine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yokuli.anchorwatch.runtime.RuntimeResidencyRepository
import com.yokuli.anchorwatch.runtime.YokuliRuntimeCoordinator
import com.yokuli.anchorwatch.service.AnchorForegroundService
import com.yokuli.runtime.contract.*
import com.yokuli.runtime.contract.ais.AisCommand
import com.yokuli.runtime.contract.navigation.*
import com.yokuli.runtime.marine.ais.LocalAisTrafficService
import com.yokuli.runtime.marine.navigation.LocalNavigationSessionService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** 系统生命周期开关只编排已有领域，不复制船位、连接、传感器或警报所有者。 */
@Singleton
class LocalRuntimeResidencyService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RuntimeResidencyRepository,
    private val coordinator: YokuliRuntimeCoordinator,
    private val navigation: LocalNavigationSessionService,
    private val ais: LocalAisTrafficService,
) : RuntimeResidencyService {
    override val state = repository.state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val lifecycleGeneration = AtomicLong()
    override fun startFromForeground() {
        if (state.value.requested && state.value.phase == RuntimeResidencyPhase.RUNNING && state.value.problem == null) return
        if (state.value.phase in setOf(RuntimeResidencyPhase.STARTING, RuntimeResidencyPhase.STOPPING)) return
        val generation = lifecycleGeneration.get()
        scope.launch { mutex.withLock {
            // 前台回调可能在退出拿到锁前排队；旧启动不得在退出完成后清除闩锁。
            if (generation != lifecycleGeneration.get() || state.value.phase == RuntimeResidencyPhase.STOPPING) return@withLock
            if (state.value.requested && state.value.phase == RuntimeResidencyPhase.RUNNING && state.value.problem == null) return@withLock
            try {
                repository.startRequest()
                ContextCompat.startForegroundService(context, Intent(context, AnchorForegroundService::class.java).setAction("OS_RUNTIME_START"))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                repository.phase(RuntimeResidencyPhase.BLOCKED, "BACKGROUND_START_NOT_ALLOWED")
            }
        } }
    }
    override suspend fun exit(): RuntimeExitResult {
        lifecycleGeneration.incrementAndGet()
        return withContext(NonCancellable + Dispatchers.Default) { mutex.withLock {
        repository.phase(RuntimeResidencyPhase.STOPPING)
        try {
            val navigationState = withTimeout(15_000) { navigation.state.first { it.ready } }
            navigationState.session?.takeIf { it.phase == NavigationPhase.ACTIVE }?.let { session ->
                val receipt = navigation.execute(NavigationCommand(UUID.randomUUID().toString(), NavigationAction.PAUSE, session.id, session.revision))
                check(receipt.result == NavigationResult.SAVED) { receipt.reason ?: "NAVIGATION_COULD_NOT_PAUSE" }
            }
            // 完全退出是明确关闭监控的用户动作。阈值、关注船及别名仍保留，重开不重新启用保护。
            val traffic = withTimeout(15_000) { ais.snapshot.first { it.runtime.ready } }
            if (traffic.preferences.monitoringEnabled) {
                val result = ais.command(AisCommand.UpdatePreferences(traffic.preferences.copy(
                    cpaEnabled = false, proximityEnabled = false, anchorProximityEnabled = false)))
                check(result.success) { result.reason ?: "AIS_COULD_NOT_STOP" }
            }
            withTimeout(30_000) { coordinator.stopForExplicitExit() }
            RuntimeExitResult(true)
        } catch (error: Exception) {
            val problem = error.message?.take(200) ?: "EXIT_NOT_COMPLETED"
            repository.phase(RuntimeResidencyPhase.BLOCKED, problem)
            RuntimeExitResult(false, problem)
        }
        } }
    }
}
