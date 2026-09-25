package com.yokuli.marine.shell.rebuild

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yokuli.marine.shell.rebuild.ui.tileBinding
import com.yokuli.marine.shell.rebuild.ui.tileContentDescriptor
import com.yokuli.shell.contract.*
import com.yokuli.shell.engine.*
import com.yokuli.shell.engine.geometry.StartViewport
import com.yokuli.shell.engine.layout.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class TileEditorPhase { EDITING, ALREADY_PINNED, SAVING, FAILED, CONFLICT }

/** 中文：来路只引用现有页面；此编辑器不是应用，也没有自己的应用返回栈。 */
data class TileEditorOrigin(val surface:String,val taskId:String?=null,val pageKey:String?=null)
data class TileEditorSession(
    val id:String,
    val tileId:TileInstanceId,
    val binding:TileBinding,
    val presentation:TilePresentation,
    val size:MarineTileSize,
    val originalRevision:Long?,
    val originalBinding:TileBinding,
    val originalPresentation:TilePresentation,
    val originalSize:MarineTileSize,
    val origin:TileEditorOrigin,
    val phase:TileEditorPhase=TileEditorPhase.EDITING,
    val errorText:String?=null,
    val existingTileId:TileInstanceId?=null,
    val showDiscardConfirmation:Boolean=false,
    val visible:Boolean=true,
    val returnAutomatically:Boolean=true,
) {
    val isNew get()=originalRevision==null
    val dirty get()=binding!=originalBinding||presentation!=originalPresentation||size!=originalSize
}
data class TileWorkshopFeedback(val message:String,val tileId:TileInstanceId?=null,
    val removalRequestId:String?=null,val error:Boolean=false)

/**
 * 中文：Shell 临时编辑流程的唯一所有者。草稿只存配置，业务数据由原提供者只读呈现。
 * 配置提交、移除及撤销都交由既有引擎队列，Saved 之后才关闭页面和反馈成功。
 */
class TileWorkshopController(private val os:OsStore,private val shell:WpShellRuntime) {
    private val current=MutableStateFlow<TileEditorSession?>(null)
    val session=current.asStateFlow()
    private val messages=MutableStateFlow<TileWorkshopFeedback?>(null)
    val feedback=messages.asStateFlow()
    var viewport by mutableStateOf<StartViewport?>(null)
    private var loaded=false
    private var pendingExisting:TileInstanceId?=null
    private var pendingBinding:TileBinding?=null
    private var departureObserved=false
    private var saveJob:Job?=null
    private var feedbackJob:Job?=null
    private val pendingRemovals=mutableSetOf<TileInstanceId>()
    private val removalRequests=mutableMapOf<TileInstanceId,Pair<String,Long>>()
    private val undoRequests=mutableMapOf<String,String>()
    private val draftWrites=Channel<String?>(Channel.CONFLATED)
    val blocksInput get()=current.value?.visible==true

    init {
        os.scope.launch {
            shell.persistence.loaded.first {it}
            val payload=shell.persistence.state.value?.workshopDraft
            if(!payload.isNullOrBlank()) {
                val restored=runCatching {decode(payload)}.getOrNull()
                if(restored!=null) {
                    val committed=shell.persistence.document.value?.receipts?.firstOrNull {it.requestId==restored.id&&it.operation=="save"}
                    if(committed==null)current.value=restored.copy(visible=false,returnAutomatically=false,
                        phase=if(restored.phase==TileEditorPhase.SAVING)TileEditorPhase.FAILED else restored.phase,
                        errorText=if(restored.phase==TileEditorPhase.SAVING)os.t("上次保存结果待确认；重试会使用同一请求。","The previous save needs confirmation. Retry uses the same request.")else restored.errorText)
                    else draftWrites.trySend(null)
                } else show(TileWorkshopFeedback(os.t("未完成的磁贴配置无法读取，桌面没有改变。","The unfinished tile draft could not be read. Start is unchanged."),error=true))
            }
            loaded=true
        }
        os.scope.launch {
            shell.persistence.loaded.first {it}
            for(first in draftWrites) {
                delay(120)
                var payload=first
                while(true) {val next=draftWrites.tryReceive();if(next.isFailure)break;payload=next.getOrNull()}
                try {shell.persistence.saveTileDraft(payload)}
                catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception) {
                    current.value?.let {draft->current.value=draft.copy(errorText=os.t("草稿暂未保存；请保留此页面并重试。","The draft was not saved. Keep this page and retry."))}
                }
            }
        }
    }

    private fun origin():TileEditorOrigin {
        val state=shell.engine.state.value
        val task=(state.surface as? ShellVisualSurface.Module)?.let {state.tasks.task(it.taskId)}
        return TileEditorOrigin(surfaceName(state.surface),task?.taskId?.value,task?.currentUiStateKey)
    }
    private fun surfaceName(surface:ShellVisualSurface)=when(surface) {
        ShellVisualSurface.Desktop->"start";ShellVisualSurface.ModuleList->"apps";ShellVisualSurface.Recents->"recents"
        is ShellVisualSurface.Search->"search";is ShellVisualSurface.Module->"app"
    }
    private fun write(value:TileEditorSession?) {current.value=value;draftWrites.trySend(value?.let(::encode))}
    private fun ready():Boolean {
        if(loaded)return true
        show(TileWorkshopFeedback(os.t("正在恢复开始屏幕，请稍候。","Start is restoring. Please wait."),error=true));return false
    }
    fun beginAdd(binding:TileBinding) {
        if(!ready())return
        current.value?.let { draft ->
            if(draft.phase==TileEditorPhase.SAVING) {resume();return}
            if(draft.binding==binding && draft.isNew) {resume();return}
            if(draft.dirty) {
                pendingBinding=binding;pendingExisting=null
                write(draft.copy(visible=true,showDiscardConfirmation=true,origin=origin()))
                return
            }
            write(null)
        }
        val choice=tileContentDescriptor(os,binding)
        if(!choice.supported) {show(TileWorkshopFeedback(os.t("此内容暂不支持新固定。","This content cannot be pinned yet."),error=true));return}
        val existing=find(binding)
        write(TileEditorSession(uid(),TileInstanceId("tile-"+uid()),binding,choice.defaultPresentation,choice.defaultSize,null,
            binding,choice.defaultPresentation,choice.defaultSize,origin(),
            phase=if(existing!=null)TileEditorPhase.ALREADY_PINNED else TileEditorPhase.EDITING,existingTileId=existing?.tileId))
    }
    fun beginEdit(tileId:TileInstanceId) {
        if(!ready())return
        if(current.value?.tileId==tileId) {resume();return}
        current.value?.let {draft ->
            if(draft.phase==TileEditorPhase.SAVING) {resume();return}
            if(draft.dirty) {pendingBinding=null;pendingExisting=tileId;write(draft.copy(visible=true,showDiscardConfirmation=true,origin=origin()));return}
            write(null)
        }
        openExisting(tileId)
    }
    private fun openExisting(tileId:TileInstanceId) {
        val tile=shell.engine.state.value.start.document.placements.firstOrNull {it.tileId==tileId}
        if(tile==null) {show(TileWorkshopFeedback(os.t("这块磁贴已移除。","This tile has been removed."),error=true));return}
        val binding=tileBinding(tile)
        write(TileEditorSession(uid(),tileId,binding,tile.presentation,tile.size,tile.revision,binding,tile.presentation,tile.size,origin()))
    }
    private fun find(binding:TileBinding)=shell.engine.state.value.start.document.placements.firstOrNull {tileBinding(it).contentKey==binding.contentKey}
    fun setBinding(binding:TileBinding) {
        val previous=current.value?.takeUnless {it.phase==TileEditorPhase.SAVING}?:return
        val draft=if(previous.phase==TileEditorPhase.CONFLICT&&shell.engine.state.value.start.document.placements.none {it.tileId==previous.tileId})
            previous.copy(id=uid(),tileId=TileInstanceId("tile-"+uid()),originalRevision=null) else previous
        val choice=tileContentDescriptor(os,binding)
        if(!choice.supported)return
        val existing=find(binding)?.takeUnless {it.tileId==draft.tileId}
        write(draft.copy(id=if(draft.phase==TileEditorPhase.FAILED&&draft.binding!=binding)uid()else draft.id,binding=binding,size=draft.size.takeIf {it in choice.sizes}?:choice.defaultSize,
            presentation=if(binding==draft.binding)draft.presentation else choice.defaultPresentation,
            phase=TileEditorPhase.EDITING,
            existingTileId=existing?.tileId,errorText=null))
    }
    fun setSize(size:MarineTileSize) {modify {it.copy(size=size)}}
    fun setPresentation(presentation:TilePresentation) {modify {it.copy(presentation=presentation)}}
    private fun modify(transform:(TileEditorSession)->TileEditorSession) {
        val draft=current.value?.takeUnless {it.phase==TileEditorPhase.SAVING}?:return
        val next=transform(draft)
        write(next.copy(id=if(draft.phase==TileEditorPhase.FAILED&&next!=draft)uid()else draft.id,errorText=null,phase=TileEditorPhase.EDITING))
    }
    fun requestClose() {
        val draft=current.value?:return
        if(draft.phase==TileEditorPhase.SAVING) {suspendEditor();return}
        if(draft.dirty)write(draft.copy(showDiscardConfirmation=true))else write(null)
    }
    fun keepEditing() {pendingExisting=null;pendingBinding=null;current.value?.let {write(it.copy(showDiscardConfirmation=false))}}
    fun discard() {
        if(current.value?.phase==TileEditorPhase.SAVING) {suspendEditor();return}
        val next=pendingExisting;val binding=pendingBinding;pendingExisting=null;pendingBinding=null;write(null)
        if(next!=null)openExisting(next) else if(binding!=null)beginAdd(binding)
    }
    fun editExisting() {
        val draft=current.value?:return
        val id=draft.existingTileId?:return
        if(draft.dirty) {pendingExisting=id;write(draft.copy(showDiscardConfirmation=true))}
        else {write(null);openExisting(id)}
    }
    fun reloadConflict() {
        val draft=current.value?:return
        if(shell.engine.state.value.start.document.placements.none {it.tileId==draft.tileId}) {
            write(draft.copy(errorText=os.t("原磁贴已移除，草稿仍在；更换内容后可以重新固定。","The original tile was removed. Choose content to pin this retained draft again.")))
            return
        }
        write(null);openExisting(draft.tileId)
    }
    fun resume() {departureObserved=false;current.value?.let {write(it.copy(visible=true,returnAutomatically=true,origin=origin()))}}
    fun suspendEditor() {departureObserved=false;current.value?.let {write(it.copy(visible=false,returnAutomatically=it.origin.pageKey!=null,showDiscardConfirmation=false))}}
    fun onShellState(state:LauncherEngineState) {
        val draft=current.value?:return
        val task=(state.surface as? ShellVisualSurface.Module)?.let {state.tasks.task(it.taskId)}
        val matches=if(draft.origin.pageKey!=null)task?.currentUiStateKey==draft.origin.pageKey
            else draft.origin.surface==surfaceName(state.surface)
        if(!matches) {
            departureObserved=true
            if(draft.visible)write(draft.copy(visible=false))
        } else if(!draft.visible&&draft.returnAutomatically&&departureObserved) {
            departureObserved=false;write(draft.copy(visible=true))
        }
    }

    /** 固定的是已保存资料；不能把乐观内存中的失败对象当成可靠内容。 */
    fun contentIssue(binding:TileBinding):String? {
        if(binding.kind !in setOf(TileBindingKind.SAVED_PLACE,TileBindingKind.SAVED_ROUTE))return null
        val spot=binding.kind==TileBindingKind.SAVED_PLACE&&binding.contentId.startsWith("spot:")
        if(spot&&(!os.sailing.loaded||os.sailing.error))return os.t("地点资料尚未读完，请稍后再固定。","Place details are not ready. Please try again later.")
        if(!spot&&(os.persistenceState.value.saving||os.persistenceState.value.failed||os.contentReadInProgress))
            return os.t("请先完成资料保存，再固定到开始屏幕。","Finish saving this content before pinning it to Start.")
        val exists=if(binding.kind==TileBindingKind.SAVED_ROUTE)os.routes.any {it.id==binding.contentId}else os.allPlaces.any {it.id==binding.contentId}
        return if(exists)null else os.t("这项收藏已不存在，可以更换内容。","This saved item no longer exists. Choose different content.")
    }
    fun save() {
        val draft=current.value?.takeUnless {it.phase in setOf(TileEditorPhase.SAVING,TileEditorPhase.ALREADY_PINNED)}?:return
        if(saveJob?.isActive==true)return
        if(!draft.isNew&&!draft.dirty) {write(null);return}
        contentIssue(draft.binding)?.let {write(draft.copy(phase=TileEditorPhase.FAILED,errorText=it));return}
        val choice=tileContentDescriptor(os,draft.binding)
        if(draft.size !in choice.sizes || (draft.presentation.legacyMode==null&&choice.styles.none {it.key==draft.presentation.style})) {
            write(draft.copy(phase=TileEditorPhase.FAILED,errorText=os.t("请选择此内容支持的尺寸和表现。","Choose a supported size and presentation.")));return
        }
        shell.ensureTileContent(draft.binding,draft.size)
        write(draft.copy(phase=TileEditorPhase.SAVING,errorText=null,showDiscardConfirmation=false))
        saveJob=os.scope.launch {
            val result=try {shell.engine.commitTile(TileCommitRequest(draft.id,draft.tileId,draft.binding,draft.presentation,draft.size,draft.originalRevision))}
                catch(cancelled:CancellationException){throw cancelled}
                catch(error:Exception){TileCommitResult.Failed(error.message ?: "Storage error")}
            val active=current.value?.takeIf {it.id==draft.id}
            when(result) {
                is TileCommitResult.Saved->{
                    if(active!=null)write(null)
                    if(active?.visible==true)show(TileWorkshopFeedback(
                        if(result.relocated)os.t("磁贴已保存；原处空间不足，已放到附近空位。","Tile saved in a nearby free space; its old position was too small.")
                        else os.t(if(draft.isNew)"已添加到开始屏幕"else"磁贴已保存",if(draft.isNew)"Added to Start"else"Tile saved"),result.tileId))
                }
                is TileCommitResult.AlreadyPinned->if(active!=null)write(active.copy(phase=TileEditorPhase.ALREADY_PINNED,existingTileId=result.tileId,errorText=null))
                is TileCommitResult.Conflict->if(active!=null)write(active.copy(phase=TileEditorPhase.CONFLICT,errorText=os.t(if(result.current==null)"这块磁贴已在别处移除，草稿仍保留。"else"这块磁贴已在别处更改，草稿仍保留。",if(result.current==null)"This tile was removed elsewhere. Your draft is retained."else"This tile changed elsewhere. Your draft is retained.")))
                is TileCommitResult.Failed->if(active!=null)write(active.copy(phase=TileEditorPhase.FAILED,errorText=os.t("未保存：","Not saved: ")+result.reason.take(180)))
            }
        }
    }
    fun reveal(tileId:TileInstanceId) {
        if(shell.engine.state.value.start.document.placements.none {it.tileId==tileId}) {show(TileWorkshopFeedback(os.t("这块磁贴已移除。","This tile was removed."),error=true));return}
        if(current.value?.dirty==true) {show(TileWorkshopFeedback(os.t("请先保存或放弃当前修改，再查看位置。","Save or discard this draft before viewing its location."),error=true));return}
        write(null);dismissFeedback();shell.dispatch(LauncherAction.RevealTile(tileId))
    }
    fun unpin(tileId:TileInstanceId) {
        val tile=shell.engine.state.value.start.document.placements.firstOrNull {it.tileId==tileId}?:return
        if(!pendingRemovals.add(tileId))return
        val request=removalRequests.getOrPut(tileId){uid() to tile.revision}
        os.scope.launch {
            try {
                when(val result=shell.engine.removeTile(request.first,tileId,request.second)) {
                    is TileCommitResult.Saved->{removalRequests.remove(tileId);if(current.value?.tileId==tileId)write(null);show(TileWorkshopFeedback(os.t("已从开始屏幕移除","Removed from Start"),removalRequestId=request.first))}
                    is TileCommitResult.Conflict->{removalRequests.remove(tileId);show(TileWorkshopFeedback(os.t("磁贴已更改，请重新选择要移除的磁贴。","The tile changed. Select it again to remove it."),error=true))}
                    is TileCommitResult.Failed->show(TileWorkshopFeedback(os.t("移除未保存：","Removal was not saved: ")+result.reason.take(160),error=true))
                    is TileCommitResult.AlreadyPinned->Unit
                }
            } catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){show(TileWorkshopFeedback(os.t("磁贴未移除，请重试。","The tile was not removed. Retry."),error=true))}
            finally {pendingRemovals.remove(tileId)}
        }
    }
    fun undo() {
        val removal=messages.value?.removalRequestId?:return
        val request=undoRequests.getOrPut(removal){uid()}
        os.scope.launch {
            val result=try {shell.engine.undoTile(request,removal)}
                catch(cancelled:CancellationException){throw cancelled}
                catch(error:Exception){TileCommitResult.Failed(error.message ?: "Storage error")}
            when(result) {
                is TileCommitResult.Saved->show(TileWorkshopFeedback(
                    if(result.relocated)os.t("磁贴已恢复到附近空位，保留了后来的排列。","Tile restored to nearby space, keeping your later arrangement.")
                    else os.t("磁贴已恢复","Tile restored"),result.tileId))
                is TileCommitResult.AlreadyPinned->show(TileWorkshopFeedback(os.t("此内容已重新固定","This content is already pinned"),result.tileId))
                is TileCommitResult.Conflict->show(TileWorkshopFeedback(os.t("桌面已更改，无法覆盖现有磁贴。","Start changed. Existing tiles were kept."),error=true))
                is TileCommitResult.Failed->show(TileWorkshopFeedback(os.t("暂未恢复：","Could not restore: ")+result.reason.take(160),removalRequestId=removal,error=true))
            }
            while(undoRequests.size>32)undoRequests.remove(undoRequests.keys.first())
        }
    }
    private fun show(value:TileWorkshopFeedback) {messages.value=value;feedbackJob?.cancel();feedbackJob=os.scope.launch {delay(10_000);if(messages.value===value)messages.value=null}}
    fun dismissFeedback() {feedbackJob?.cancel();messages.value=null}

    private fun bindingJson(value:TileBinding)=JSONObject().put("provider",value.providerId).put("kind",value.unknownKind?:value.kind.name).put("content",value.contentId)
    private fun presentationJson(value:TilePresentation)=JSONObject().put("style",value.style).put("legacy",value.legacyMode).put("rotate",value.rotate).put("interval",value.intervalSeconds)
    private fun encode(value:TileEditorSession)=JSONObject().put("schema",1).put("id",value.id).put("tile",value.tileId.value)
        .put("binding",bindingJson(value.binding)).put("presentation",presentationJson(value.presentation)).put("size",value.size.name)
        .put("revision",value.originalRevision).put("originalBinding",bindingJson(value.originalBinding))
        .put("originalPresentation",presentationJson(value.originalPresentation)).put("originalSize",value.originalSize.name)
        .put("surface",value.origin.surface).put("task",value.origin.taskId).put("page",value.origin.pageKey)
        .put("phase",value.phase.name).put("existing",value.existingTileId?.value).toString()
    private fun decode(payload:String):TileEditorSession {
        val json=JSONObject(payload);require(json.getInt("schema")==1)
        fun binding(key:String):TileBinding {val b=json.getJSONObject(key);val raw=b.getString("kind");val kind=TileBindingKind.entries.firstOrNull {it.name==raw}?:TileBindingKind.UNKNOWN
            return TileBinding(b.getString("provider"),kind,b.getString("content"),raw.takeIf {kind==TileBindingKind.UNKNOWN}).also {require(it.isStructurallyValid)}}
        fun presentation(key:String):TilePresentation {val p=json.getJSONObject(key);return TilePresentation(p.getString("style"),p.optString("legacy").takeIf {it.isNotBlank()},if(p.has("rotate"))p.getBoolean("rotate")else null,if(p.has("interval"))p.getInt("interval")else null)}
        return TileEditorSession(json.getString("id"),TileInstanceId(json.getString("tile")),binding("binding"),presentation("presentation"),MarineTileSize.valueOf(json.getString("size")),
            if(json.has("revision"))json.getLong("revision")else null,binding("originalBinding"),presentation("originalPresentation"),MarineTileSize.valueOf(json.getString("originalSize")),
            TileEditorOrigin(json.getString("surface"),json.optString("task").takeIf {it.isNotBlank()},json.optString("page").takeIf {it.isNotBlank()}),
            TileEditorPhase.valueOf(json.getString("phase")),existingTileId=json.optString("existing").takeIf {it.isNotBlank()}?.let(::TileInstanceId),visible=false,returnAutomatically=false)
    }
}
