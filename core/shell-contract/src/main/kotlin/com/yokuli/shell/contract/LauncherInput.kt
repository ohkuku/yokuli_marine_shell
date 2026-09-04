package com.yokuli.shell.contract

/**
 * 中文：所有壳级输入源使用同一组语义，再由 Engine 串行处理。
 * English: Every shell input source emits the same semantics before serialized Engine dispatch.
 */
enum class LauncherInput {
    BACK,
    START,
    SEARCH,
    RECENTS,
}
