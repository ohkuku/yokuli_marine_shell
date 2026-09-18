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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** 系统组合接口：应用通过窄领域端口工作，不取得本地控制器。 */
interface MarineSystem : RuntimeEndpoint {
    val services: MarineServices
    val voyage: VoyageSessionService
}

/** ROM 与普通 APK 共用此实现；可替换传输，但当前仍是同进程、同 UID。 */
@Singleton
class InProcessMarineSystem @Inject constructor(override val services: LocalMarineServices) : MarineSystem {
    private val systemScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val identity = RuntimeConnection("yokuli.marine", RuntimeTransport.IN_PROCESS, RuntimeReadiness.INITIALIZING)
    override val connection: StateFlow<RuntimeConnection> = services.state.map {
        identity.copy(readiness = if (it.settingsReady) RuntimeReadiness.READY else RuntimeReadiness.INITIALIZING)
    }.stateIn(systemScope, SharingStarted.Eagerly, identity)
    override val voyage: VoyageSessionService = VoyageSessionCoordinator(services, systemScope)
}

@Module
@InstallIn(SingletonComponent::class)
object MarineSystemBindings {
    @Provides @Singleton
    fun system(local: InProcessMarineSystem): MarineSystem = local
    @Provides @Singleton
    fun content(local: com.yokuli.anchorwatch.api.LocalMarineContentService): com.yokuli.anchorwatch.api.MarineContentService = local
}
