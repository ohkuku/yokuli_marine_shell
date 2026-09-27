package com.yokuli.anchorwatch.runtime.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.yokuli.runtime.contract.hardware.VirtualHostServices
import java.util.IdentityHashMap
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 中文：生产 DataStore 的窄委托。故障不返回空偏好、不覆盖已有文件；异常交回原设置所有者。
 * 数据可能来自 DataStore 的缓存，但每次交付订阅者前仍检查读取权限/损坏故障。
 */
class FaultAwareDataStore<T>(private val delegate:DataStore<T>):DataStore<T> {
    override val data:Flow<T> = delegate.data.map { value -> VirtualHostServices.beforeRead();value }
    override suspend fun updateData(transform:suspend (T)->T):T {
        VirtualHostServices.beforeWrite()
        return delegate.updateData { current ->
            VirtualHostServices.beforeRead()
            VirtualHostServices.beforeWrite()
            val next=transform(current)
            // transform 可以挂起；返回磁盘提交点前重新检查当下故障，而非只检查调用时。
            VirtualHostServices.beforeWrite()
            next
        }
    }
}

/** 中文：保留 AndroidX 的每个应用文件唯一实例，只缓存其包装器，不创建第二个 DataStore。 */
fun faultAwarePreferencesDataStore(name:String):ReadOnlyProperty<Context,DataStore<Preferences>> {
    val delegate=preferencesDataStore(name)
    val wrappers=IdentityHashMap<DataStore<Preferences>,DataStore<Preferences>>()
    return object:ReadOnlyProperty<Context,DataStore<Preferences>> {
        override fun getValue(thisRef:Context,property:KProperty<*>):DataStore<Preferences> {
            val actual=delegate.getValue(thisRef,property)
            return synchronized(wrappers){wrappers.getOrPut(actual){FaultAwareDataStore(actual)}}
        }
    }
}
