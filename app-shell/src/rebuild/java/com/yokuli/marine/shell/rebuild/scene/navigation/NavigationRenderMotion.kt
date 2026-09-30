package com.yokuli.marine.shell.rebuild.scene.navigation

import kotlin.math.*

/** 仅插值已经收到的船位，绝不按 COG 推算未来位置或刷新观测时刻。 */
internal class NavigationPositionMotion {
    var x=0.0;private set
    var z=0.0;private set
    private var targetX=0.0
    private var targetZ=0.0
    private var observed=0L
    private var initialized=false
    private var cadence=.35
    fun rebase(east:Double,south:Double){x+=east;z+=south;targetX+=east;targetZ+=south}
    fun update(east:Double,south:Double,observedUtc:Long,dt:Double,discontinuityMeters:Double){
        if(!initialized||observedUtc<observed||hypot(east-targetX,south-targetZ)>discontinuityMeters){
            x=east;z=south;initialized=true
        }else if(observedUtc!=observed){
            cadence=((observedUtc-observed)/2_000.0).coerceIn(.12,.65)
        }
        targetX=east;targetZ=south;observed=observedUtc
        val t=1-exp(-dt/cadence)
        x+=(targetX-x)*t;z+=(targetZ-z)*t
        if(abs(targetX-x)<.001)x=targetX
        if(abs(targetZ-z)<.001)z=targetZ
    }
    fun clear(){initialized=false;observed=0L}
}

/** 不产生 Pair/数组的投影器。只用实际提交成功帧的矩阵来命中，不能混用未来相机。 */
internal class NavigationScreenProjection {
    val matrix=DoubleArray(16)
    var width=0;var height=0
    var x=0f;private set
    var y=0f;private set
    fun project(east:Double,elevation:Double,south:Double):Boolean {
        val m=matrix
        val w=m[3]*east+m[7]*elevation+m[11]*south+m[15]
        if(!w.isFinite()||w<=.001)return false
        val nx=(m[0]*east+m[4]*elevation+m[8]*south+m[12])/w
        val ny=(m[1]*east+m[5]*elevation+m[9]*south+m[13])/w
        val nz=(m[2]*east+m[6]*elevation+m[10]*south+m[14])/w
        if(!nx.isFinite()||!ny.isFinite()||nz !in -1.0..1.0||nx !in -1.0..1.0||ny !in -1.0..1.0)return false
        x=((nx+1)*width*.5).toFloat();y=((1-ny)*height*.5).toFloat()
        return true
    }
}

/** 三个导航符号的帧缓冲；不为每次船位更新创建点击矩形。 */
internal class NavigationGuideHit {
    var id:String?=null
    var east=0.0
    var height=0.0
    var south=0.0
    fun set(id:String?,east:Double=0.0,height:Double=0.0,south:Double=0.0){this.id=id;this.east=east;this.height=height;this.south=south}
    fun copyFrom(other:NavigationGuideHit)=set(other.id,other.east,other.height,other.south)
}
