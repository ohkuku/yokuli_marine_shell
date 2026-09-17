package com.yokuli.marine.shell.rebuild

/** 上滑关闭只在抬手时判定；位移足够或已有明确上抛速度。单位均为 dp、dp/s。 */
internal fun shouldDismissTask(offset: Float, velocity: Float, height: Float): Boolean =
    height > 0f && (offset < -height * .28f || (offset < -32f && velocity < -900f))
