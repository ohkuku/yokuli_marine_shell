package com.yokuli.marine.shell.rebuild.extensions

/**
 * .ykl 的公开系统调用表。授权、能力发现和实际分派共用此表，不能用 Core 的反射端口表代替。
 * SDK 版本是应用承诺理解的协议版本；升级宿主不会把旧应用默认为具备新的写权限。
 */
internal object ExtensionSdkContract {
    const val VERSION = 2
    const val PACKAGE_FORMAT = ".ykl"
    data class Method(
        val name: String,
        val since: Int = 1,
        val permission: String? = null,
        val needsCore: Boolean = false,
        val foregroundOnly: Boolean = false,
        val mutation: Boolean = false,
    )
    val methods = listOf(
        Method("system.info"),
        Method("system.services"),
        Method("storage.get"),
        Method("storage.set", mutation = true),
        Method("marine.snapshot", permission = "marine.read"),
        Method("nmea.connections", permission = "nmea.read"),
        Method("navigation.open", permission = "navigation.open", foregroundOnly = true),
        Method("devices.snapshot", 2, "devices.read"),
        Method("sources.snapshot", 2, "sources.read", needsCore = true),
        Method("sources.select", 2, "sources.control", needsCore = true, foregroundOnly = true, mutation = true),
        Method("nmea.setConnectionEnabled", 2, "nmea.control", needsCore = true, foregroundOnly = true, mutation = true),
        Method("sharing.snapshot", 2, "sharing.read", needsCore = true),
        Method("sharing.setEnabled", 2, "sharing.control", needsCore = true, foregroundOnly = true, mutation = true),
        Method("voyage.snapshot", 2, "voyage.read", needsCore = true),
        Method("voyage.receipt", 2, "voyage.read", needsCore = true),
        Method("voyage.command", 2, "voyage.control", needsCore = true, foregroundOnly = true, mutation = true),
    ).associateBy { it.name }
    val permissions: Set<String> = methods.values.mapNotNull { it.permission }.toSet()
    fun permissionsFor(sdk: Int): Set<String> = methods.values.filter { it.since <= sdk }.mapNotNull { it.permission }.toSet()
}
