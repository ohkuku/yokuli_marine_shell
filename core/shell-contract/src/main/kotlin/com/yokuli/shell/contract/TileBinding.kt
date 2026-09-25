package com.yokuli.shell.contract

/** 中文：内容身份不包含表现、语言、单位、来源或临时任务会话。 */
enum class TileBindingKind { APP, READING, CURRENT_TASK, OVERVIEW, SAVED_PLACE, SAVED_ROUTE, UNKNOWN }

data class TileBinding(
    val providerId: String,
    val kind: TileBindingKind,
    val contentId: String,
    /** 未来种类原文；当前版本保留而不猜测其业务。 */
    val unknownKind: String? = null,
) {
    val contentKey: String = listOf(providerId, unknownKind ?: kind.name, contentId)
        .joinToString(":") { "${it.length}:$it" }
    val startEntryId: LauncherEntryId = if (kind == TileBindingKind.APP) LauncherEntryId(contentId)
        else LauncherEntryId("content." + contentKey.toByteArray(Charsets.UTF_8).joinToString("") { byte -> val value = byte.toInt() and 255; "${HEX[value ushr 4]}${HEX[value and 15]}" })
    private companion object { const val HEX = "0123456789abcdef" }
    val isStructurallyValid: Boolean get() = providerId.isNotBlank() && providerId.length <= 96 && contentId.isNotBlank() && contentId.length <= 512
}

/** 中文：实例的持久表现；旧版应用轮换偏好仅迁移一次。 */
data class TilePresentation(
    val style: String = "default",
    val legacyMode: String? = null,
    val rotate: Boolean? = null,
    val intervalSeconds: Int? = null,
)
