package com.yokuli.runtime.contract.hardware

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/** 电源与存储故障属于设备层。真实环境永远不注入故障，不能破坏用户的真实数据库。 */
object VirtualHostServices {
    private val _power = MutableStateFlow(HardwarePower())
    val power = _power.asStateFlow()
    private val _storage = MutableStateFlow(StorageFault.NONE)
    val storage = _storage.asStateFlow()
    @Volatile var virtual: Boolean = false
        private set
    fun configure(virtual: Boolean, power: HardwarePower = HardwarePower(), storage: StorageFault = StorageFault.NONE) {
        this.virtual = virtual
        _power.value = power.copy(simulated = virtual)
        _storage.value = if (virtual) storage else StorageFault.NONE
    }
    fun power(value: HardwarePower) { require(virtual); require(value.percent in 0..100 && value.thermal in 0..6); _power.value = value.copy(simulated = true) }
    fun storage(value: StorageFault) { require(virtual); _storage.value = value }
    fun beforeRead() {
        if (!virtual) return
        when (_storage.value) {
            StorageFault.CORRUPT -> throw IOException("VIRTUAL_STORAGE_CORRUPT")
            StorageFault.PERMISSION_REVOKED -> throw IOException("VIRTUAL_STORAGE_PERMISSION_REVOKED")
            else -> Unit
        }
    }
    fun beforeWrite() {
        if (!virtual) return
        if (_storage.value != StorageFault.NONE) throw IOException("VIRTUAL_STORAGE_${_storage.value.name}")
    }
}
