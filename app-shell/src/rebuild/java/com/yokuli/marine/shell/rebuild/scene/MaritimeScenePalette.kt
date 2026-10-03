package com.yokuli.marine.shell.rebuild.scene

/** 海图与交通的共同空间层级：环境中性、路线蓝、危险橙；航标本身的红绿资料色保留。 */
internal object MaritimeScenePalette {
    fun sea(light:Boolean)=if(light)floatArrayOf(.34f,.39f,.42f)else floatArrayOf(.035f,.045f,.055f)
    fun horizon(light:Boolean)=if(light)floatArrayOf(.64f,.67f,.69f)else floatArrayOf(.018f,.023f,.030f)
    val route=floatArrayOf(.12f,.57f,.96f)
    val danger=floatArrayOf(1f,.30f,.12f)
}
