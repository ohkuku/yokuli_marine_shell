package com.yokuli.runtime.marine.hardware

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.yokuli.runtime.contract.device.DeviceBackend
import com.yokuli.runtime.contract.hardware.*
import com.yokuli.runtime.contract.time.ClockSnapshot
import com.yokuli.runtime.contract.time.MarineTime
import java.io.File

/** 存在真实宿主文件夹，不能被实验室的存储故障封锁，否则用户无法退出故障环境。 */
data class LabBootState(
    val version: Int = 1,
    val mode: HardwareMode = HardwareMode.REAL,
    val worldId: String = "",
    val scenario: HardwareScenario = HardwareScenario(),
    val activeScenario: HardwareScenario? = null,
    val replayId: String? = null,
    val replayPositionMillis: Long = 0,
    val scenarioCheckpoint: ScenarioCheckpoint? = null,
    val clock: ClockSnapshot? = null,
    val faults: List<HardwareFault> = emptyList(),
    val detached: List<String> = emptyList(),
    val power: HardwarePower = HardwarePower(),
    val storageFault: StorageFault = StorageFault.NONE,
)

object HardwareLabBoot {
    @Volatile var current = LabBootState(); private set
    @Volatile var initialized = false; private set
    @Volatile var problem: String? = null; private set
    private val gson = Gson()
    fun hostFiles(context: Context) = File(context.applicationInfo.dataDir, "files")
    fun directory(context: Context) = File(hostFiles(context), "virtual-os").apply { mkdirs() }
    private fun file(context: Context) = AtomicFile(File(directory(context), "boot.json"))

    /** 在构造 Room/DataStore/驱动之前执行；恢复虚拟世界时一律暂停，等待用户继续。 */
    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        val disk = file(context)
        current = try {
            if (!disk.baseFile.exists() && !File(disk.baseFile.path + ".bak").exists()) LabBootState()
            else {
                require(disk.baseFile.length() <= 4_194_304)
                disk.openRead().bufferedReader().use { gson.fromJson(it, LabBootState::class.java) }.also {
                    validate(it)
                }
            }
        } catch (_: Exception) {
            // 恢复记录损坏时不能退回真实输出环境。保留原件并进入独立、暂停的恢复空间。
            problem = "VIRTUAL_BOOT_UNREADABLE"
            LabBootState(mode = HardwareMode.SIMULATION, worldId = "00000000-0000-0000-0000-000000000000")
        }
        initialized = true
        val virtual = current.mode != HardwareMode.REAL
        if (virtual) {
            current.clock?.let { saved ->
                MarineTime.applyRemote(saved.copy(virtual = true, paused = true, hostElapsedMillis = MarineTime.hostElapsedMillis(),
                    timeEpoch = MarineTime.hostUtcMillis(), revision = saved.revision + 1))
            } ?: MarineTime.enterVirtual(paused = true).let {
                MarineTime.applyRemote(it.copy(timeEpoch = MarineTime.hostUtcMillis()))
            }
            MarineDeviceBus.enterBackend(if (current.mode == HardwareMode.REPLAY) DeviceBackend.REPLAY else DeviceBackend.SIMULATED)
        }
        VirtualHostServices.configure(virtual, current.power, StorageFault.NONE)
        // 故障注入不阻止重启后打开恢复控制；用户可在演练室再次施加故障。
    }

    @Synchronized fun save(context: Context, state: LabBootState) {
        val bytes = gson.toJson(state).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 4_194_304)
        fun write(disk: AtomicFile) {
            val stream = disk.startWrite()
            try { stream.write(bytes); stream.fd.sync(); disk.finishWrite(stream) }
            catch (failure: Throwable) { disk.failWrite(stream); throw failure }
        }
        // 引导指针是最后提交点。归档失败不能提前切换下一次启动的环境。
        if (state.mode == HardwareMode.SIMULATION) write(AtomicFile(File(directory(context), "last-simulation.json")))
        write(file(context))
        // 不改变当前进程的 namespace；下一次 Core 启动才切换所有存储所有者。
    }

    fun lastSimulation(context: Context): LabBootState? {
        val archive = AtomicFile(File(directory(context), "last-simulation.json"))
        if (!archive.baseFile.exists() && !File(archive.baseFile.path + ".bak").exists()) return null
        require(archive.baseFile.length() <= 4_194_304) { "SIMULATION_ARCHIVE_TOO_LARGE" }
        return archive.openRead().bufferedReader().use { gson.fromJson(it, LabBootState::class.java) }.also {
            validate(it); require(it.mode == HardwareMode.SIMULATION)
        }
    }

    private fun validate(value: LabBootState) {
        require(value.version == 1 && value.mode in HardwareMode.entries)
        require(value.mode == HardwareMode.REAL || value.worldId.matches(Regex("[a-f0-9-]{36}")))
        require(value.mode != HardwareMode.REPLAY || value.replayId?.matches(Regex("[a-f0-9-]{36}")) == true)
        require(value.replayPositionMillis in 0..ScenarioValidation.MAX_DURATION_MILLIS)
        require(value.storageFault in StorageFault.entries && value.faults.size <= 4 && value.detached.size <= 4)
        value.faults.forEach(ScenarioValidation::requireValidFault); value.detached.forEach(ScenarioValidation::requireDevice)
        require(value.mode == HardwareMode.REAL || value.power.percent in 0..100 && value.power.thermal in 0..6)
        ScenarioValidation.requireValid(value.scenario); value.activeScenario?.let(ScenarioValidation::requireValid)
        value.clock?.let {
            require(it.utcMillis in 1..253_402_300_799_999L && it.elapsedMillis in 0..Long.MAX_VALUE / 4 &&
                it.hostElapsedMillis >= 0 && it.timeEpoch >= 0 && it.revision >= 0 && it.rate.isFinite() && it.rate in 0.01..600.0)
        }
    }

    fun files(real: File): File = if (initialized && current.mode != HardwareMode.REAL)
        File(real, "virtual-os/worlds/${current.worldId}/files").apply { mkdirs() } else real
    fun database(real: File): File = if (initialized && current.mode != HardwareMode.REAL) {
        // Room 的绝对路径会再次经过 openOrCreateDatabase；命名空间必须幂等。
        if (real.parentFile?.name == "virtual-${current.worldId}") real
        else File(real.parentFile, "virtual-${current.worldId}/${real.name}").also { it.parentFile?.mkdirs() }
    } else real
    fun preferenceName(name: String): String = if (initialized && current.mode != HardwareMode.REAL) "virtual.${current.worldId}.$name" else name

    /** 新世界复制偏好和海图册索引作为起点，不复制数据库/记录/命令账本/未完成安全会话。 */
    fun seedWorld(context: Context, id: String) {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        val source = File(hostFiles(context), "datastore")
        val destination = File(hostFiles(context), "virtual-os/worlds/$id/files/datastore")
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles()?.filter { it.isFile && it.name in setOf("settings.preferences_pb", "vessel_data_settings.preferences_pb") && it.length() <= 4_194_304 }?.forEach { val target = File(destination, it.name); if (!target.exists()) it.copyTo(target, overwrite = false) }
        }
    }
}
