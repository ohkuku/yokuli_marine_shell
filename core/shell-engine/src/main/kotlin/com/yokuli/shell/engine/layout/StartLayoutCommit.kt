package com.yokuli.shell.engine.layout

import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.geometry.WpReferenceProfiles

/** 整页替换必须来自用户确认的布局版本；普通单块编辑仍使用 TileCommitRequest。 */
data class StartLayoutCommitRequest(val requestId:String,val expectedRevision:Long,val document:StartDocument)
sealed interface StartLayoutCommitResult {
    data class Saved(val documentRevision:Long):StartLayoutCommitResult
    data object Conflict:StartLayoutCommitResult
    data class Failed(val reason:String):StartLayoutCommitResult
}

/** 使用原文档有限回执账本和同一原子写入，不另存一份桌面或创建磁贴业务状态。 */
internal object StartLayoutCommitPolicy {
    fun replace(current:StartDocument,request:StartLayoutCommitRequest,entries:Collection<LauncherEntryDescriptor>):TileCommitPolicy.Decision {
        fun failed(reason:String)=TileCommitPolicy.Decision(current,TileCommitResult.Failed(reason))
        if(request.requestId.isBlank()||request.requestId.length>128)return failed("Invalid layout request")
        val receipt=current.receipts.lastOrNull {it.requestId==request.requestId}
        if(receipt!=null) {
            if(receipt.operation!="replace-start")return failed("Layout request identity conflict")
            return TileCommitPolicy.Decision(current,TileCommitResult.Saved(receipt.tileId,receipt.revision,receipt.documentRevision))
        }
        if(current.revision!=request.expectedRevision)return TileCommitPolicy.Decision(current,TileCommitResult.Conflict(null))
        val candidate=request.document
        val profile=runCatching {WpReferenceProfiles.require(candidate.profileId)}.getOrNull()?:return failed("Unknown Start profile")
        if(candidate.placements.isEmpty()||candidate.placements.size>128||candidate.spacers.isNotEmpty()||
            !StartDocumentValidator.isValid(candidate,entries,profile)||candidate.placements.any {it.binding==null}||
            candidate.placements.map {it.effectiveContentKey}.distinct().size!=candidate.placements.size)return failed("Invalid Start layout")
        // 和工坊保存共用内容、尺寸、样式与组合去向校验；预设不是绕开规则的导入路径。
        var checking=candidate.copy(placements=emptyList(),revision=0,receipts=emptyList(),removedTiles=emptyList())
        candidate.placements.forEachIndexed {index,tile ->
            val decision=TileCommitPolicy.save(checking,TileCommitRequest("layout-check-$index",tile.tileId,
                requireNotNull(tile.binding),tile.presentation,tile.size),entries)
            if(decision.result !is TileCommitResult.Saved)return failed("Unsupported Start content")
            checking=decision.document
        }
        val revision=current.revision+1
        val anchor=TileInstanceId("start-layout")
        val after=candidate.copy(revision=revision,placements=candidate.placements.map {it.copy(revision=revision)},
            receipts=(current.receipts+TileCommitReceipt(request.requestId,anchor,revision,revision,"replace-start")).takeLast(32),
            removedTiles=current.removedTiles,recoveryNotes=emptyList(),preservedProto=current.preservedProto)
        return TileCommitPolicy.Decision(after,TileCommitResult.Saved(anchor,revision,revision))
    }
}
