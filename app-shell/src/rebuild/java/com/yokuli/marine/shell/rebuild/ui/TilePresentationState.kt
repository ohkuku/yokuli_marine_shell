package com.yokuli.marine.shell.rebuild.ui

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** 中文：所有可见磁贴共用时效时钟；最后一个观察者离开后立即停表，不启动业务采集。 */
private object TileAgeClock {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    val ticks=flow {
        while(currentCoroutineContext().isActive) {
            val now=SystemClock.elapsedRealtime()
            emit(now)
            delay(1_000L-now%1_000L)
        }
    }.shareIn(scope,SharingStarted.WhileSubscribed(stopTimeoutMillis=0),replay=1)
}

@Composable internal fun tilePresentationActive(requested:Boolean):Boolean {
    val owner=LocalLifecycleOwner.current
    var resumed by remember(owner) {mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver {_,_->resumed=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
    return requested&&resumed
}

/** 显示暂停时保留最后快照并释放订阅；重新显示从唯一所有者重新取值。 */
@Composable internal fun <T> activeTileValue(flow:Flow<T>?,initial:T,active:Boolean):T {
    val result=produceState(initialValue=initial,flow,active) {
        if(active&&flow!=null)flow.collect {value=it}
    }
    return result.value
}
@Composable internal fun tileElapsed(active:Boolean):Long=activeTileValue(TileAgeClock.ticks,SystemClock.elapsedRealtime(),active)
