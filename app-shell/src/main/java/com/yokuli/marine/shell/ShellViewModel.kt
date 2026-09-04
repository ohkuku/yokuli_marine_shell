package com.yokuli.marine.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yokuli.shell.engine.DefaultLauncherEngine
import com.yokuli.shell.engine.InMemoryLauncherPersistence
import com.yokuli.shell.engine.LauncherEngine

/**
 * 中文：Activity 只渲染 Engine；ViewModel 在配置重建期间保留唯一运行时状态。
 * English: The Activity only renders the Engine; this ViewModel retains its sole runtime state across recreation.
 */
class ShellViewModel : ViewModel() {
    private val persistence = InMemoryLauncherPersistence(defaultStartDocument)

    val engine: LauncherEngine = DefaultLauncherEngine(
        hostPort = productionHostPort,
        persistence = persistence,
        defaultDocument = defaultStartDocument,
        scope = viewModelScope,
    )
}
