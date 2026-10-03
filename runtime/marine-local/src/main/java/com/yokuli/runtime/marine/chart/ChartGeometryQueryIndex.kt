package com.yokuli.runtime.marine.chart

import com.yokuli.runtime.contract.chart.*
import java.util.IdentityHashMap
import kotlin.math.*

/**
 * 不可变来源几何的内存查询索引。目录 coverage 和同港区反复命中的对象只预处理一次；
 * 点查询只访问穿过该纬度的边，显示裁剪只交给 JTS 与窗口相交的真实环。
 * 不简化坐标，不把 bbox 作为覆盖证据，也不改变洞/岛的语义。
 */
internal class ChartGeometryQueryIndex {
    private val entries=IdentityHashMap<ChartGeometry,Entry>()
    private val order=java.util.LinkedList<ChartGeometry>()
    private var retainedBytes=0L

    private class Entry(val rings:List<Ring>,val bytes:Long)
    private class Ring(val part:ChartGeometryPart,val xs:DoubleArray,val west:Double,val east:Double,val south:Double,val north:Double) {
        @Volatile var edges:EdgeNode?=null
    }
    private class EdgeNode(val latitude:Double,val low:IntArray,val high:IntArray,val left:EdgeNode?,val right:EdgeNode?)

    @Synchronized fun clear(){entries.clear();order.clear();retainedBytes=0L}

    private fun entry(geometry:ChartGeometry,check:()->Unit):Entry {
        synchronized(this){entries[geometry]?.let {order.removeAll{old->old===geometry};order.addLast(geometry);return it}}
        val rings=geometry.parts.map {part->
            check()
            val xs=DoubleArray(part.points.size)
            var west=Double.POSITIVE_INFINITY;var east=Double.NEGATIVE_INFINITY
            var south=Double.POSITIVE_INFINITY;var north=Double.NEGATIVE_INFINITY
            part.points.forEachIndexed {i,p->
                if(i%256==0)check()
                // 从原坐标选连续分支，避免逐边累加误差使共用顶点与显示投影分离。
                xs[i]=if(i==0)p.longitude else p.longitude+round((xs[i-1]-p.longitude)/360.0)*360.0
                west=min(west,xs[i]);east=max(east,xs[i]);south=min(south,p.latitude);north=max(north,p.latitude)
            }
            Ring(part,xs,west,east,south,north)
        }
        val built=Entry(rings,192+geometry.parts.sumOf{it.points.size.toLong()*112+192})
        if(built.bytes<=MAX_BYTES)synchronized(this) {
            entries[geometry]?.let{return it}
            entries[geometry]=built;order.addLast(geometry);retainedBytes+=built.bytes
            while((retainedBytes>MAX_BYTES||entries.size>512)&&order.isNotEmpty())entries.remove(order.removeFirst())?.let{retainedBytes-=it.bytes}
        }
        return built
    }

    fun window(geometry:ChartGeometry,bounds:ChartBounds,check:()->Unit):ChartGeometry {
        if(geometry.parts.isEmpty())return geometry
        val rings=entry(geometry,check).rings
        val boxes=bounds.split()
        val parts=rings.filter {ring->
            check()
            boxes.any {box->
                if(ring.north<box.south||ring.south>box.north)false else {
                    val shift=round(((ring.west+ring.east)-(box.west+box.east))/720.0)*360.0
                    ring.east>=box.west+shift&&ring.west<=box.east+shift
                }
            }
        }.map {it.part}
        return if(parts.size==geometry.parts.size)geometry else geometry.copy(parts=parts)
    }

    fun contains(geometry:ChartGeometry,point:ChartPoint,check:()->Unit):Boolean {
        if(geometry.kind!=ChartGeometryKind.POLYGON)return false
        var winding=0
        for(ring in entry(geometry,check).rings) {
            check()
            if(ring.part.points.size<3||point.latitude<ring.south||point.latitude>ring.north)continue
            val x=point.longitude+round(((ring.west+ring.east)/2-point.longitude)/360.0)*360.0
            if(x<ring.west||x>ring.east)continue
            val edges=ring.edges?:synchronized(ring) {
                ring.edges?:build(ring,IntArray(ring.xs.size){it},check).also{ring.edges=it}
            }
            var inside=false;var boundary=false;var visits=0
            fun inspect(index:Int) {
                if(++visits%128==0)check()
                val previous=if(index==0)ring.xs.lastIndex else index-1
                val ax=ring.xs[previous];val bx=ring.xs[index]
                val ay=ring.part.points[previous].latitude;val by=ring.part.points[index].latitude
                val vx=bx-ax;val vy=by-ay
                val cross=(x-ax)*vy-(point.latitude-ay)*vx
                if(abs(cross)<=1e-10*(abs(vx)+abs(vy)).coerceAtLeast(1e-10)&&
                    x>=min(ax,bx)-1e-10&&x<=max(ax,bx)+1e-10&&
                    point.latitude>=min(ay,by)-1e-10&&point.latitude<=max(ay,by)+1e-10)boundary=true
                if((ay>point.latitude)!=(by>point.latitude)&&x<(bx-ax)*(point.latitude-ay)/(by-ay)+ax)inside=!inside
            }
            fun visit(node:EdgeNode?) {
                if(node==null||boundary)return
                if(point.latitude<=node.latitude) {
                    for(i in node.low){if(low(ring,i)>point.latitude)break;inspect(i);if(boundary)return}
                    if(point.latitude<node.latitude)visit(node.left)
                } else {
                    for(i in node.high){if(high(ring,i)<point.latitude)break;inspect(i);if(boundary)return}
                    visit(node.right)
                }
            }
            visit(edges)
            if(boundary||inside)winding+=if(ring.part.hole)-1 else 1
        }
        return winding>0
    }

    /**
     * 窗口内没有真实边时，整窗必定同属面内或面外。用纬度边索引作精确保守判定：
     * 边 bbox 相交便交回正常拓扑裁剪；只有肯定没有边时才直接返回整窗/空窗。
     * 因而大水深面或远岸全国轮廓不会在每张三维瓦片里重建 JTS 全环。
     */
    fun uniformWindow(geometry:ChartGeometry,bounds:ChartBounds,check:()->Unit):Boolean? {
        if(geometry.kind!=ChartGeometryKind.POLYGON)return null
        val width=if(bounds.west<=bounds.east)bounds.east-bounds.west else bounds.east+360.0-bounds.west
        if(width>180.0)return null
        val middle=bounds.west+width/2
        for(ring in entry(geometry,check).rings) {
            check()
            if(ring.north<bounds.south||ring.south>bounds.north||ring.part.points.size<3)continue
            val shift=round(((ring.west+ring.east)/2-middle)/360.0)*360.0
            val west=bounds.west+shift;val east=west+width
            if(ring.east<west||ring.west>east)continue
            val tree=ring.edges?:synchronized(ring) {
                ring.edges?:build(ring,IntArray(ring.xs.size){it},check).also{ring.edges=it}
            }
            var visits=0
            fun intersects(index:Int):Boolean {
                if(++visits%128==0)check()
                val previous=if(index==0)ring.xs.lastIndex else index-1
                return max(ring.xs[index],ring.xs[previous])>=west&&min(ring.xs[index],ring.xs[previous])<=east
            }
            fun visit(node:EdgeNode?):Boolean {
                if(node==null)return false
                if(bounds.north<=node.latitude) {
                    for(i in node.low){if(low(ring,i)>bounds.north)break;if(intersects(i))return true}
                    return visit(node.left)
                }
                if(bounds.south>=node.latitude) {
                    for(i in node.high){if(high(ring,i)<bounds.south)break;if(intersects(i))return true}
                    return visit(node.right)
                }
                return node.low.any(::intersects)||visit(node.left)||visit(node.right)
            }
            if(visit(tree))return null
        }
        return contains(geometry,ChartPoint((bounds.south+bounds.north)/2,normalize(middle)),check)
    }

    private fun build(ring:Ring,edges:IntArray,check:()->Unit,alreadySorted:Boolean=false):EdgeNode? {
        if(edges.isEmpty())return null
        check()
        // 只在根排序一次；按中点分区保留次序，避免每一层重复 O(n log n) 排序。
        val sorted=if(alreadySorted)edges else edges.sortedBy {i->(low(ring,i)+high(ring,i))/2}.toIntArray()
        val pivot=sorted[sorted.size/2]
        val latitude=(low(ring,pivot)+high(ring,pivot))/2
        val lower=ArrayList<Int>();val upper=ArrayList<Int>();val crossing=ArrayList<Int>()
        sorted.forEachIndexed {i,edge->
            if(i%256==0)check()
            when {high(ring,edge)<latitude->lower+=edge;low(ring,edge)>latitude->upper+=edge;else->crossing+=edge}
        }
        return EdgeNode(latitude,crossing.sortedBy{low(ring,it)}.toIntArray(),crossing.sortedByDescending{high(ring,it)}.toIntArray(),
            build(ring,lower.toIntArray(),check,true),build(ring,upper.toIntArray(),check,true))
    }

    private fun low(ring:Ring,index:Int)=min(ring.part.points[index].latitude,ring.part.points[if(index==0)ring.xs.lastIndex else index-1].latitude)
    private fun high(ring:Ring,index:Int)=max(ring.part.points[index].latitude,ring.part.points[if(index==0)ring.xs.lastIndex else index-1].latitude)
    private fun normalize(value:Double)=((value+180.0)%360.0+360.0)%360.0-180.0
    private companion object {const val MAX_BYTES=32L*1024*1024}
}
