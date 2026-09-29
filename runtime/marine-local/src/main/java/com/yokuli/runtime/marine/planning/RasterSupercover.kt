package com.yokuli.runtime.marine.planning

import kotlin.math.*

/**
 * 遍历线段触及的每个原始像元。普通 Bresenham 只选一条像素中心线，会漏掉对角旁格。
 * 角点同时触及的两旁格都必须通行；沿格边行进时同时检查两侧。没有插值或水陆推测。
 */
internal object RasterSupercover {
    fun clear(x0:Double,y0:Double,x1:Double,y1:Double,water:(Int,Int)->Boolean):Boolean {
        if(!listOf(x0,y0,x1,y1).all(Double::isFinite))return false
        var x=floor(x0).toInt();var y=floor(y0).toInt()
        val endX=floor(x1).toInt();val endY=floor(y1).toInt()
        val dx=x1-x0;val dy=y1-y0
        val sx=when {dx>0->1;dx<0->-1;else->0}
        val sy=when {dy>0->1;dy<0->-1;else->0}
        val deltaX=if(sx==0)Double.POSITIVE_INFINITY else 1/abs(dx)
        val deltaY=if(sy==0)Double.POSITIVE_INFINITY else 1/abs(dy)
        var nextX=if(sx>0)(x+1-x0)/dx else if(sx<0)(x-x0)/dx else Double.POSITIVE_INFINITY
        var nextY=if(sy>0)(y+1-y0)/dy else if(sy<0)(y-y0)/dy else Double.POSITIVE_INFINITY
        val onVertical=sx==0&&abs(x0-round(x0))<1e-9
        val onHorizontal=sy==0&&abs(y0-round(y0))<1e-9
        fun visit(cx:Int,cy:Int):Boolean=water(cx,cy)&&(!onVertical||water(cx-1,cy))&&(!onHorizontal||water(cx,cy-1))
        val limit=abs(endX-x)+abs(endY-y)+4
        if(limit>2_000_000)return false
        repeat(limit) {
            if(!visit(x,y))return false
            if(x==endX&&y==endY)return true
            if(abs(nextX-nextY)<1e-12) {
                if(!visit(x+sx,y)||!visit(x,y+sy))return false
                x+=sx;y+=sy;nextX+=deltaX;nextY+=deltaY
            } else if(nextX<nextY) {x+=sx;nextX+=deltaX}
            else {y+=sy;nextY+=deltaY}
        }
        return false
    }
}
