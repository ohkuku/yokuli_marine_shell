package com.yokuli.marine.shell.rebuild.chart

import androidx.core.content.FileProvider

/** 独立组件身份，避免 manifest 合并时覆盖已有 GPX 分享提供方的路径与权限。 */
class ChartBundleFileProvider : FileProvider()
