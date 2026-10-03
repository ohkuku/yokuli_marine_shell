package com.yokuli.runtime.marine.planning

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.triangulate.polygon.ConstrainedDelaunayTriangulator
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.PriorityQueue
import kotlin.math.abs

/**
 * 精确可航面的受约束三角剖分。只在编译/船型变更时构建；持久坐标和邻接可直接用于路径搜索。
 * 三角形沿真实岸线和孔洞分割，和用于视觉 LOD 的地形三角网完全独立。
 */
internal class PassageNavigationMesh private constructor(
    private val coordinates:DoubleArray,private val triangles:IntArray,private val neighbors:IntArray,
) {
    val bytes:Long get()=coordinates.size*8L+(triangles.size+neighbors.size)*4L
    val estimatedBytes:Long get()=bytes+triangles.size/3*128L+coordinates.size*16L
    private val count get()=triangles.size/3
    private val index by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {STRtree().apply {
        for(i in 0 until count)insert(envelope(i),i)
        build()
    }}
    private fun vertex(id:Int)=Coordinate(coordinates[id*2],coordinates[id*2+1])
    private fun point(triangle:Int,corner:Int)=vertex(triangles[triangle*3+corner])
    private fun envelope(triangle:Int)=Envelope().apply{for(i in 0..2)expandToInclude(point(triangle,i))}
    private fun center(triangle:Int):Coordinate {
        val a=point(triangle,0);val b=point(triangle,1);val c=point(triangle,2)
        return Coordinate((a.x+b.x+c.x)/3,(a.y+b.y+c.y)/3)
    }
    private fun contains(triangle:Int,p:Coordinate):Boolean {
        val a=point(triangle,0);val b=point(triangle,1);val c=point(triangle,2)
        val ab=cross(a,b,p);val bc=cross(b,c,p);val ca=cross(c,a,p)
        val tolerance=1e-7
        return (ab>=-tolerance&&bc>=-tolerance&&ca>=-tolerance)||(ab<=tolerance&&bc<=tolerance&&ca<=tolerance)
    }
    private fun locate(p:Coordinate):List<Int> = index.query(Envelope(p).apply{expandBy(1e-7)}).map{it as Int}.filter{contains(it,p)}

    /** 三角邻接 A* + 内部门户点与有界直线化；返回已受精确水域约束的近最短路线，不宣称全局连续最优。 */
    fun route(start:Coordinate,end:Coordinate,water:PreparedGeometry,check:()->Unit):List<Coordinate>? {
        check();val factory=water.geometry.factory
        fun clear(a:Coordinate,b:Coordinate)=water.covers(factory.createLineString(arrayOf(a,b)))
        if(!water.covers(factory.createPoint(start))||!water.covers(factory.createPoint(end)))return null
        if(clear(start,end))return listOf(start,end)
        val starts=locate(start);val ends=locate(end).toSet()
        if(starts.isEmpty()||ends.isEmpty())return null
        data class Node(val id:Int,val score:Double,val cost:Double)
        val queue=PriorityQueue<Node>(compareBy<Node>{it.score}.thenBy{it.cost})
        val cost=DoubleArray(count){Double.POSITIVE_INFINITY};val parent=IntArray(count){-1}
        for(id in starts) {cost[id]=start.distance(center(id));queue+=Node(id,cost[id]+center(id).distance(end),cost[id])}
        var finish=-1
        while(queue.isNotEmpty()) {
            check();val node=queue.remove();if(node.cost>cost[node.id])continue
            if(node.id in ends){finish=node.id;break}
            val origin=center(node.id)
            for(edge in 0..2) {
                val next=neighbors[node.id*3+edge];if(next<0)continue
                val target=center(next);val candidate=node.cost+origin.distance(target)
                if(candidate>=cost[next])continue
                cost[next]=candidate;parent[next]=node.id
                queue+=Node(next,candidate+target.distance(end),candidate)
            }
        }
        if(finish<0)return null
        val chain=ArrayList<Int>();var cursor=finish
        while(cursor>=0){check();chain+=cursor;cursor=parent[cursor]};chain.reverse()
        val middle=ArrayList<Coordinate>();middle+=start
        for(i in 0 until chain.lastIndex) {
            check();val a=chain[i];val b=chain[i+1]
            val edge=(0..2).firstOrNull{neighbors[a*3+it]==b}?:return null
            val first=point(a,edge);val second=point(a,(edge+1)%3)
            middle+=Coordinate((first.x+second.x)/2,(first.y+second.y)/2)
        }
        middle+=end
        // Choose interior portal points, not coastline-touching funnel vertices. Exact
        // WGS84 projection round-trips can put a boundary vertex nanometres outside;
        // moving through the interior avoids that failure without enlarging the water.
        // Both the local path and the emitted WGS84 segments still receive exact checks.
        val candidate=middle
        if(candidate.zipWithNext().any{check();!clear(it.first,it.second)})return null
        val result=ArrayList<Coordinate>();result+=start;var anchor=0
        while(anchor<candidate.lastIndex) {
            check()
            val next=passageShortcutIndex(anchor,candidate.lastIndex) { index->
                check();clear(candidate[anchor],candidate[index])
            }
            if(result.last().distance(candidate[next])>1e-7)result+=candidate[next]
            anchor=next
        }
        result[0]=start;result[result.lastIndex]=end
        return result
    }
    fun write(out:DataOutputStream,check:()->Unit) {
        out.writeInt(coordinates.size/2);out.writeInt(count)
        coordinates.forEachIndexed{i,v->if(i%1024==0)check();out.writeDouble(v)}
        triangles.forEachIndexed{i,v->if(i%1024==0)check();out.writeInt(v)}
        neighbors.forEachIndexed{i,v->if(i%1024==0)check();out.writeInt(v)}
    }
    companion object {
        private const val MAX_TRIANGLES=500_000
        private fun cross(a:Coordinate,b:Coordinate,c:Coordinate)=(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)
        fun compile(water:Geometry,check:()->Unit):PassageNavigationMesh {
            check();if(water.isEmpty)return PassageNavigationMesh(DoubleArray(0),IntArray(0),IntArray(0))
            val vertices=ArrayList<Double>();val lookup=HashMap<Pair<Long,Long>,Int>()
            val triangles=ArrayList<Int>()
            fun vertex(c:Coordinate):Int {
                require(c.x.isFinite()&&c.y.isFinite()){"NAVIGATION_MESH_COORDINATE"}
                val x=if(c.x==0.0)0.0 else c.x;val y=if(c.y==0.0)0.0 else c.y
                return lookup.getOrPut(x.toBits() to y.toBits()){val id=vertices.size/2;vertices+=x;vertices+=y;id}
            }
            fun polygon(shape:Geometry) {
                check()
                when(shape) {
                    is Polygon -> {
                        val triangulator=ConstrainedDelaunayTriangulator(shape)
                        val parts=triangulator.triangles
                        require(triangles.size/3+parts.size<=MAX_TRIANGLES){"NAVIGATION_MESH_LIMIT"}
                        for(part in parts){check();for(i in 0..2)triangles+=vertex(part.getCoordinate(i))}
                    }
                    is GeometryCollection -> for(i in 0 until shape.numGeometries)polygon(shape.getGeometryN(i))
                    else->require(shape.isEmpty){"NAVIGATION_MESH_NOT_AREA"}
                }
            }
            polygon(water)
            val topology=triangles.toIntArray();val neighbors=IntArray(topology.size){-1}
            val edges=HashMap<Long,Int>()
            for(i in topology.indices) {
                if(i%1024==0)check()
                val a=topology[i];val b=topology[i/3*3+(i%3+1)%3]
                val key=(minOf(a,b).toLong() shl 32) or maxOf(a,b).toLong()
                val previous=edges.putIfAbsent(key,i)
                if(previous!=null){require(neighbors[previous]<0){"NAVIGATION_MESH_NON_MANIFOLD"};neighbors[i]=previous/3;neighbors[previous]=i/3}
            }
            return PassageNavigationMesh(vertices.toDoubleArray(),topology,neighbors)
        }
        fun read(input:DataInputStream,check:()->Unit):PassageNavigationMesh {
            val vertices=input.readInt();val count=input.readInt()
            require(vertices in 0..MAX_TRIANGLES*3&&count in 0..MAX_TRIANGLES){"NAVIGATION_MESH_SIZE"}
            val coordinates=DoubleArray(vertices*2){i->if(i%1024==0)check();input.readDouble().also{require(it.isFinite()){"NAVIGATION_MESH_COORDINATE"}}}
            val triangles=IntArray(count*3){i->if(i%1024==0)check();input.readInt().also{require(it in 0 until vertices){"NAVIGATION_MESH_VERTEX"}}}
            val neighbors=IntArray(count*3){i->if(i%1024==0)check();input.readInt().also{require(it in -1 until count){"NAVIGATION_MESH_NEIGHBOR"}}}
            for(i in neighbors.indices)if(neighbors[i]>=0) {
                if(i%1024==0)check()
                val next=neighbors[i];val a=triangles[i];val b=triangles[i/3*3+(i%3+1)%3]
                require((0..2).any{edge->val at=next*3+edge;val x=triangles[at];val y=triangles[next*3+(edge+1)%3]
                    neighbors[at]==i/3&&(a==x&&b==y||a==y&&b==x)}){"NAVIGATION_MESH_ADJACENCY"}
            }
            return PassageNavigationMesh(coordinates,triangles,neighbors)
        }
    }
}
