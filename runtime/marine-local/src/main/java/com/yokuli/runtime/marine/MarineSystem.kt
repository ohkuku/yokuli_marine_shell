package com.yokuli.runtime.marine

import com.yokuli.anchorwatch.api.MarineServices
import com.yokuli.anchorwatch.api.LocalMarineServices
import com.yokuli.runtime.contract.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** 系统组合接口：应用通过窄领域端口工作，不取得本地控制器。 */
interface MarineSystem : RuntimeEndpoint {
    val hardwareLab: com.yokuli.runtime.contract.hardware.HardwareLabService
    val devices: com.yokuli.runtime.contract.device.DeviceRuntimeService
    val residency: RuntimeResidencyService
    val presentation: RuntimePresentationService
    val readingHistory: com.yokuli.anchorwatch.api.ReadingHistoryService
    val charts: com.yokuli.runtime.contract.chart.ChartDataService
    val navigation: com.yokuli.runtime.contract.navigation.NavigationSessionService
    val analysis: com.yokuli.runtime.contract.planning.RouteAnalysisService
    val planning: com.yokuli.runtime.contract.planning.RoutePlanningService
    val services: MarineServices
    val voyage: VoyageSessionService
    val anchorCommands: AnchorCommandMonitor
    val ais: com.yokuli.runtime.contract.ais.AisTrafficService
}

/** 默认进程唯一 Core 组合根；Shell 进程只能取得 BinderMarineSystem。 */
@Singleton
class InProcessMarineSystem @Inject constructor(
    override val hardwareLab: com.yokuli.runtime.marine.hardware.LocalHardwareLabService,
    override val devices: com.yokuli.runtime.marine.device.LocalDeviceRuntimeService,
    override val residency: LocalRuntimeResidencyService,
    override val presentation: LocalRuntimePresentationService,
    override val readingHistory: com.yokuli.runtime.marine.history.LocalReadingHistoryService,
    override val services: LocalMarineServices,
    override val charts: com.yokuli.runtime.marine.chart.LocalChartDataService,
    override val navigation: com.yokuli.runtime.marine.navigation.LocalNavigationSessionService,
    private val passages: com.yokuli.runtime.marine.planning.LocalPassagePlanningService,
    override val anchorCommands: com.yokuli.anchorwatch.runtime.AnchorCommandRegistry,
    override val ais: com.yokuli.runtime.marine.ais.LocalAisTrafficService,
) : MarineSystem {
    override val analysis: com.yokuli.runtime.contract.planning.RouteAnalysisService get() = passages
    override val planning: com.yokuli.runtime.contract.planning.RoutePlanningService get() = passages
    private val systemScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val identity = RuntimeConnection("yokuli.marine", RuntimeTransport.IN_PROCESS, RuntimeReadiness.INITIALIZING)
    override val connection: StateFlow<RuntimeConnection> = combine(services.state, residency.state) { state, recovery ->
        identity.copy(readiness = when {
            recovery.recoveryProblem != null -> RuntimeReadiness.UNAVAILABLE
            state.settingsReady && recovery.recoveryReady -> RuntimeReadiness.READY
            else -> RuntimeReadiness.INITIALIZING
        }, reason = recovery.recoveryProblem)
    }.stateIn(systemScope, SharingStarted.Eagerly, identity)
    override val voyage: VoyageSessionService = VoyageSessionCoordinator(services, systemScope)
}

@Module
@InstallIn(SingletonComponent::class)
object MarineSystemBindings {
    @Provides @Singleton
    fun system(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        local: dagger.Lazy<InProcessMarineSystem>,
    ): MarineSystem = when {
        com.yokuli.runtime.marine.ipc.MarineCoreProcess.isShell() -> com.yokuli.runtime.marine.ipc.BinderMarineSystem.shared(context)
        com.yokuli.runtime.marine.ipc.MarineCoreProcess.isCore(context) -> local.get()
        else -> error("This process cannot own or instantiate Marine Core")
    }
    @Provides @Singleton
    fun content(system: MarineSystem): com.yokuli.anchorwatch.api.MarineContentService = system.services.content
}
