package com.yokuli.runtime.marine.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Process
import android.util.AtomicFile
import com.google.gson.Gson
import com.yokuli.anchorwatch.api.LocalMarineServices
import com.yokuli.runtime.contract.hardware.*
import com.yokuli.runtime.contract.time.MarineTime
import com.yokuli.runtime.marine.navigation.LocalNavigationSessionService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Core 唯一实验环境所有者；模式切换重启存储所有者，绝不在仍打开的数据库下换目录。 */
@Singleton
class LocalHardwareLabService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val marine: Provider<LocalMarineServices>,
    private val navigation: Provider<LocalNavigationSessionService>,
) : HardwareLabService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val gson = Gson()
    private var boot = HardwareLabBoot.current.let { if (it.mode == HardwareMode.SIMULATION && it.activeScenario == null) it.copy(activeScenario = it.scenario) else it }
    private val _state = MutableStateFlow(HardwareLabSnapshot(mode = boot.mode, scenario = boot.scenario))
    override val state = _state.asStateFlow()
    private var issue = HardwareLabBoot.problem
    private var restarting = false
    private val recordings = SystemRecordingStore(context, scope) { issue = it }
    private var replayJob: Job? = null
    private var replayPosition = boot.replayPositionMillis
    private var replayDuration = 0L
    private var engine: ScenarioEngine? = null
    private val receipts = File(HardwareLabBoot.directory(context), "commands").apply { mkdirs() }
    private data class Receipt(val fingerprint: String, val result: HardwareLabResult? = null)

    init {
        when (boot.mode) {
            HardwareMode.SIMULATION -> engine = runCatching { ScenarioEngine(scope, boot.activeScenario ?: boot.scenario,
                onPower = { VirtualHostServices.power(it); recordings.power(it) },
                onStorage = { VirtualHostServices.storage(it); recordings.storage(it) },
                onError = { issue = it }, initialCheckpoint = boot.scenarioCheckpoint).also { engine ->
                    if (boot.scenarioCheckpoint == null) boot.faults.forEach(engine::setFault)
                    boot.detached.forEach(engine::detach)
                } }.onFailure { issue = it.message ?: "SCENARIO_RECOVERY_FAILED" }.getOrNull()
            HardwareMode.REPLAY -> boot.replayId?.let { id ->
                replayJob = scope.launch(Dispatchers.IO) {
                    try {
                        replayDuration = recordings.get(id).durationMillis
                        recordings.replay(id, replayPosition) { replayPosition = it }
                        MarineTime.setPaused(true)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (e: Exception) { issue = e.message ?: "REPLAY_FAILED"; MarineTime.setPaused(true) }
                }
            }
            HardwareMode.REAL -> Unit
        }
        scope.launch {
            var lastSave = MarineTime.hostElapsedMillis()
            while (isActive) {
                try {
                    mutex.withLock {
                        if (!restarting) {
                            publish()
                            if (boot.mode != HardwareMode.REAL && MarineTime.hostElapsedMillis() - lastSave > 2000) {
                                checkpoint(); lastSave = MarineTime.hostElapsedMillis()
                            }
                        }
                    }
                } catch (e: Exception) { issue = e.message ?: "LAB_CHECKPOINT_FAILED" }
                delay(500) // 宿主心跳，暂停虚拟时间之后仍能恢复、退出或清除故障。
            }
        }
    }
    private fun checkpoint() {
        boot = boot.copy(clock = MarineTime.snapshot(), replayPositionMillis = replayPosition,
            scenarioCheckpoint = engine?.checkpoint(), activeScenario = if (boot.mode == HardwareMode.SIMULATION) boot.activeScenario ?: boot.scenario else null, faults = engine?.state?.value?.faults ?: boot.faults,
            power = VirtualHostServices.power.value, storageFault = VirtualHostServices.storage.value)
        HardwareLabBoot.save(context, boot)
    }
    private fun power(): HardwarePower {
        if (boot.mode != HardwareMode.REAL) return VirtualHostServices.power.value
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val manager = context.getSystemService(PowerManager::class.java)
        return HardwarePower(percent = if (level < 0) -1 else level * 100 / scale.coerceAtLeast(1),
            external = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            thermal = if (android.os.Build.VERSION.SDK_INT >= 29) manager.currentThermalStatus else 0,
            screenOn = manager.isInteractive)
    }
    private fun publish() {
        val clock = MarineTime.snapshot(); val bus = MarineDeviceBus.state.value
        val disk = context.filesDir
        val power = power()
        if (state.value.power != power) recordings.power(power)
        _state.value = HardwareLabSnapshot(ready = true, mode = boot.mode, epoch = bus.epoch,
            utcMillis = clock.utcMillis, elapsedMillis = clock.elapsedMillis, capturedHostElapsedMillis = MarineTime.hostElapsedMillis(),
            paused = clock.paused, rate = clock.rate, scenario = boot.scenario,
            devices = bus.devices.map { LabDevice(it.spec.id, it.spec.name, it.spec.kind.name, it.attached, engine?.state?.value?.frameCounts?.get(it.spec.id) ?: 0L) },
            faults = engine?.state?.value?.faults ?: boot.faults, recordings = recordings.list(),
            recordingId = recordings.active?.id, replayId = boot.replayId, replayPositionMillis = replayPosition,
            replayDurationMillis = replayDuration, eventCursor = engine?.state?.value?.eventCursor ?: 0,
            power = power, storage = HardwareStorage(disk.usableSpace, disk.totalSpace, VirtualHostServices.storage.value), error = issue, clock = clock)
    }
    override suspend fun execute(command: HardwareLabCommand): HardwareLabResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (restarting) return@withLock HardwareLabResult(false, "RESTARTING", "Core is changing environments")
            if (command.requestId.length !in 1..200) return@withLock HardwareLabResult(false, "INVALID_REQUEST_ID")
            val digest = { text: String -> MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) } }
            val fingerprint = digest(gson.toJson(command)); val path = File(receipts, digest(command.requestId) + ".json")
            val file = AtomicFile(path)
            try {
                if (path.exists() || File(path.path + ".bak").exists()) {
                    require(path.length() <= 32_000)
                    val prior = file.openRead().bufferedReader().use { gson.fromJson(it, Receipt::class.java) }
                    return@withLock if (prior.fingerprint != fingerprint) HardwareLabResult(false, "REQUEST_ID_CONFLICT")
                    else prior.result ?: HardwareLabResult(false, "OUTCOME_UNKNOWN", "Check the current environment before another action")
                }
                require(receipts.list().orEmpty().size < 20_000) { "LAB_COMMAND_LEDGER_FULL" }
                fun save(value: Receipt) {
                    val output = file.startWrite()
                    try { output.write(gson.toJson(value).toByteArray()); output.fd.sync(); file.finishWrite(output) }
                    catch (e: Throwable) { file.failWrite(output); throw e }
                }
                save(Receipt(fingerprint))
                val result = try { act(command) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) { HardwareLabResult(false, "REJECTED", e.message ?: "Hardware action failed") }
                save(Receipt(fingerprint, result)); recordings.command(command)
                if (!restarting) { checkpoint(); publish() }
                result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { HardwareLabResult(false, "PERSISTENCE_FAILED", e.message) }
        }
    }
    private suspend fun act(command: HardwareLabCommand): HardwareLabResult {
        fun virtual() = check(boot.mode != HardwareMode.REAL) { "VIRTUAL_ENVIRONMENT_REQUIRED" }
        when (command.action) {
            LabAction.ENTER_SIMULATION -> {
                val scenario = command.scenario ?: boot.scenario; ScenarioValidation.requireValid(scenario)
                val previous = HardwareLabBoot.lastSimulation(context)
                val sameScenario = previous != null && gson.toJson(previous.activeScenario ?: previous.scenario) == gson.toJson(scenario)
                val next = if (sameScenario) requireNotNull(previous).copy(scenario = scenario, storageFault = StorageFault.NONE)
                    else LabBootState(mode = HardwareMode.SIMULATION, worldId = previous?.worldId ?: UUID.randomUUID().toString(), scenario = scenario, activeScenario = scenario)
                return switch(next)
            }
            LabAction.ENTER_REPLAY -> {
                val recording = recordings.get(command.recordingId); require(recording.frames > 0) { "RECORDING_HAS_NO_INPUT" }; val header = recordings.header(recording.id)
                return switch(LabBootState(mode = HardwareMode.REPLAY, worldId = UUID.nameUUIDFromBytes(("yokuli-replay:" + recording.id).toByteArray()).toString(),
                    scenario = header.scenario, replayId = recording.id,
                    clock = MarineTime.snapshot().copy(virtual = true, paused = true, utcMillis = header.utc)))
            }
            LabAction.RETURN_REAL -> return switch(LabBootState(scenario = boot.scenario))
            LabAction.PAUSE -> { virtual(); MarineTime.setPaused(true) }
            LabAction.RESUME -> { virtual(); check(boot.mode != HardwareMode.REPLAY || replayJob?.isActive == true) { "REPLAY_FINISHED" }; MarineTime.setPaused(false) }
            LabAction.SET_RATE -> { virtual(); require(command.rate.isFinite() && command.rate in 0.01..600.0); MarineTime.setRate(command.rate) }
            LabAction.STEP -> { virtual(); require(command.stepMillis in 1..60_000); MarineTime.step(command.stepMillis) }
            LabAction.SAVE_SCENARIO, LabAction.IMPORT_SCENARIO -> {
                val scenario = command.scenario ?: command.scenarioJson?.let {
                    require(it.toByteArray().size <= 256_000) { "SCENARIO_TOO_LARGE" }; gson.fromJson(it, HardwareScenario::class.java)
                } ?: error("SCENARIO_REQUIRED")
                ScenarioValidation.requireValid(scenario); boot = boot.copy(scenario = scenario)
            }
            LabAction.ATTACH -> { virtual(); requireNotNull(engine) { "SIMULATION_REQUIRED" }.attach(command.deviceId); boot = boot.copy(detached = boot.detached - command.deviceId) }
            LabAction.DETACH -> { virtual(); requireNotNull(engine) { "SIMULATION_REQUIRED" }.detach(command.deviceId); boot = boot.copy(detached = (boot.detached + command.deviceId).distinct()) }
            LabAction.SET_FAULT -> { virtual(); requireNotNull(engine) { "SIMULATION_REQUIRED" }.setFault(requireNotNull(command.fault)) }
            LabAction.CLEAR_FAULT -> { virtual(); requireNotNull(engine) { "SIMULATION_REQUIRED" }.clearFault(command.deviceId) }
            LabAction.START_RECORDING -> recordings.start(command.name, boot.activeScenario ?: boot.scenario, power())
            LabAction.STOP_RECORDING -> recordings.stop()
            LabAction.DELETE_RECORDING -> { require(command.recordingId != boot.replayId) { "REPLAY_IN_USE" }; recordings.delete(command.recordingId) }
            LabAction.SET_POWER -> { virtual(); val value = requireNotNull(command.power); VirtualHostServices.power(value); recordings.power(value) }
            LabAction.SET_STORAGE -> {
                virtual()
                val previous = VirtualHostServices.storage.value
                if (previous != StorageFault.NONE && command.storageFault == StorageFault.NONE) check(recordings.active == null) { "STOP_RECORDING_BEFORE_STORAGE_RECOVERY" }
                VirtualHostServices.storage(command.storageFault); recordings.storage(command.storageFault)
                if (previous != StorageFault.NONE && command.storageFault == StorageFault.NONE) {
                    check(recordings.active == null) { "STOP_RECORDING_BEFORE_STORAGE_RECOVERY" }
                    checkpoint()
                    return restartOwners(boot.copy(storageFault = StorageFault.NONE))
                }
            }
        }
        return HardwareLabResult(true)
    }
    private suspend fun switch(next: LabBootState): HardwareLabResult {
        check(recordings.active == null) { "STOP_RECORDING_BEFORE_SWITCH" }
        val facts = marine.get().state.value
        check(facts.settingsReady) { "CORE_RECOVERY_PENDING" }
        check(facts.active == null && facts.activeTrip == null && navigation.get().state.value.session?.ongoing != true) { "END_ACTIVE_ANCHOR_VOYAGE_AND_NAVIGATION_FIRST" }
        if (next.mode == boot.mode && next.mode == HardwareMode.REAL) return HardwareLabResult(true)
        if (next.mode != HardwareMode.REAL) HardwareLabBoot.seedWorld(context, next.worldId)
        return restartOwners(next)
    }
    private fun restartOwners(next: LabBootState): HardwareLabResult {
        HardwareLabBoot.save(context, next)
        restarting = true
        _state.value = state.value.copy(ready = false, error = "RESTARTING")
        // 给 Binder 足够时间发送已落盘的接受结果；只终止本 APK 的状态所有者，Shell任务栈保留。
        scope.launch {
            delay(1000)
            context.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty()
                .filter { it.uid == Process.myUid() && it.processName == context.packageName + ":notifications" }
                .forEach { Process.killProcess(it.pid) }
            Process.killProcess(Process.myPid())
        }
        return HardwareLabResult(true, "RESTARTING")
    }
    override suspend fun readRecording(id: String, offset: Long, limit: Int) = withContext(Dispatchers.IO) { recordings.chunk(id, offset, limit) }
}
