package com.yokuli.marine.shell.rebuild.scene.navigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.TextureView
import android.view.ViewConfiguration
import com.yokuli.anchorwatch.location.vessel.DeviceViewOrientationSample
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

internal data class SpatialRenderInput(
    val snapshot: NavigationSpatialSnapshot = NavigationSpatialSnapshot(null),
    val orientation: DeviceViewOrientationSample = DeviceViewOrientationSample(),
    val conversion: SpatialNorthConversion? = null,
    val screenRotation: Int = 0,
    val freeYaw: Double? = null,
    val freePitch: Double = -8.0,
    val chinese: Boolean = false,
    val distanceLabels: Map<String, String> = emptyMap(),
    val fontScale: Float = 1f,
)
internal data class SpatialHit(val id: String, val bounds: RectF)
internal data class SpatialPresentedFrame(val camera: SpatialCamera, val targetDelta: Double?, val hits: List<SpatialHit>)

/**
 * 与其他本地scene一致采用TextureView/OpenGL；单一EGL上下文归一个绘图线程。
 * 前台Choreographer仅在新样本或尚未收敛时请求下一帧；不对业务流写回插值。
 */
internal class NavigationSpatialSurface(context: Context) : TextureView(context), TextureView.SurfaceTextureListener, Choreographer.FrameCallback {
    private val main = Handler(Looper.getMainLooper())
    private val clock = Choreographer.getInstance()
    private val thread = HandlerThread("Yokuli-navigation-view").apply { start() }
    private val worker = Handler(thread.looper)
    private val queued = AtomicBoolean(false)
    private var native: SpatialGlRenderer? = null
    private var scheduled = false
    private var active = false
    private var closed = false
    private var surfaceGeneration = 0L
    @Volatile private var input = SpatialRenderInput()
    @Volatile private var latestFrame: SpatialPresentedFrame? = null
    @Volatile private var surfaceWidth = 1
    @Volatile private var surfaceHeight = 1
    private var downX = 0f; private var downY = 0f
    private var dragYaw = 0.0; private var dragPitch = -8.0; private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    var inputEnabled = true
    var onFailure: () -> Unit = {}
    var onTarget: (String) -> Unit = {}
    var onFreeChanged: (Boolean) -> Unit = {}
    init { surfaceTextureListener = this; isOpaque = true; isClickable = true; contentDescription = "Navigation direction" }
    fun update(value: SpatialRenderInput, visible: Boolean) {
        val latest=input.orientation.takeIf{it.generation==value.orientation.generation&&(it.elapsedRealtimeMillis?:0L)>(value.orientation.elapsedRealtimeMillis?:0L)}?:value.orientation
        input = value.copy(orientation=latest,freeYaw = if(value.freeYaw != null) input.freeYaw ?: value.freeYaw else null, freePitch = input.freePitch)
        active = visible
        if (active) requestFrame() else cancelFrames()
    }
    fun orientation(value: DeviceViewOrientationSample) { input = input.copy(orientation = value); requestFrame() }
    fun resetView() { input = input.copy(freeYaw = null, freePitch = -8.0); onFreeChanged(false); requestFrame() }
    fun turnBy(degrees: Double) {
        val yaw = input.freeYaw ?: latestFrame?.camera?.trueBearing ?: input.snapshot.current?.bearingTrueDegrees ?: 0.0
        input = input.copy(freeYaw = wrapBearing(yaw + degrees)); onFreeChanged(true); requestFrame()
    }
    fun frame(): SpatialPresentedFrame? = latestFrame
    private fun requestFrame() {
        if (closed || !active || !isAvailable || scheduled) return
        scheduled = true; clock.postFrameCallback(this)
    }
    private fun cancelFrames() { clock.removeFrameCallback(this); scheduled = false }
    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!active || closed || !isAvailable || !queued.compareAndSet(false, true)) return
        val value = input; val w = surfaceWidth; val h = surfaceHeight; val generation = surfaceGeneration
        worker.post {
            var moving = false
            try {
                native?.let { renderer ->
                    val result = renderer.draw(value, w, h, frameTimeNanos)
                    latestFrame = result
                    moving = renderer.moving
                }
            } catch (_: Throwable) { fail(generation) }
            finally { queued.set(false); main.post { if (!closed && active && (moving || value != input)) requestFrame() } }
        }
    }
    private fun fail(generation: Long) { main.post { if (!closed && generation == surfaceGeneration) { active = false; cancelFrames(); onFailure() } } }
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1); surfaceHeight = height.coerceAtLeast(1)
        val generation = ++surfaceGeneration
        worker.post {
            try { native?.close(); native = SpatialGlRenderer(surface, resources.displayMetrics.density, androidx.core.content.res.ResourcesCompat.getFont(context,com.yokuli.marine.core.design.R.font.selawik_regular)?:android.graphics.Typeface.DEFAULT); main.post { if (!closed && generation == surfaceGeneration) requestFrame() } }
            catch (_: Throwable) { native?.close(); native = null; fail(generation) }
        }
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { surfaceWidth=width.coerceAtLeast(1);surfaceHeight=height.coerceAtLeast(1);requestFrame() }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        surfaceGeneration++; cancelFrames(); latestFrame = null
        if (!closed) worker.post { native?.close(); native=null;surface.release() } else surface.release()
        return false
    }
    override fun onDetachedFromWindow() { cancelFrames(); super.onDetachedFromWindow() }
    fun close() {
        if (closed) return
        closed = true; active = false; surfaceGeneration++; cancelFrames()
        worker.post { native?.close();native=null;thread.quitSafely() }
        onTarget = {}; onFailure = {}; onFreeChanged = {}
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled || !active) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX=event.x;downY=event.y;dragging=false
                dragYaw=input.freeYaw?:latestFrame?.camera?.trueBearing?:input.snapshot.current?.bearingTrueDegrees?:0.0
                dragPitch=latestFrame?.camera?.forward?.y?.coerceIn(-1.0,1.0)?.let{Math.toDegrees(asin(it))}?:input.freePitch
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx=event.x-downX;val dy=event.y-downY
                if (!dragging && hypot(dx,dy)>slop) { dragging=true;onFreeChanged(true) }
                if (dragging) { input=input.copy(freeYaw=wrapBearing(dragYaw-dx/width.coerceAtLeast(1)*80),freePitch=(dragPitch+dy/height.coerceAtLeast(1)*60).coerceIn(-60.0,50.0));requestFrame() }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) latestFrame?.hits?.lastOrNull { it.bounds.contains(event.x,event.y) }?.let { onTarget(it.id) }
                performClick(); parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> { dragging=false;parent?.requestDisallowInterceptTouchEvent(false) }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick();return true }
}

private class SpatialGlRenderer(surfaceTexture: SurfaceTexture, private val density: Float, private val face:android.graphics.Typeface) : AutoCloseable {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var colorProgram = 0
    private var textProgram = 0
    private val motion = DeviceViewMotion()
    private val cameraMotion=SpatialCameraMotion()
    private val textures = linkedMapOf<String, TextTexture>()
    private val identity = FloatArray(16).apply { this[0]=1f;this[5]=1f;this[10]=1f;this[15]=1f }
    private var fontScale=1f
    private var behindSide = 1f
    private val scratch=ByteBuffer.allocateDirect(8192*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val uvBuffer=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(floatArrayOf(0f,0f,0f,1f,1f,0f,1f,1f));position(0)}
    private var cameraDriven=false
    val moving get() = if(cameraDriven)cameraMotion.moving else motion.moving
    private data class TextTexture(val id: Int, val width: Int, val height: Int)
    init {
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY)
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1))
            val configs = arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_DEPTH_SIZE,16,EGL14.EGL_NONE),0,configs,0,1,count,0) && count[0]>0)
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            check(context!=EGL14.EGL_NO_CONTEXT)
            surface=EGL14.eglCreateWindowSurface(display,configs[0],surfaceTexture,intArrayOf(EGL14.EGL_NONE),0)
            check(surface!=EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display,surface,surface,context))
            colorProgram=program("attribute vec3 p;uniform mat4 m;void main(){gl_Position=m*vec4(p,1.0);}","precision mediump float;uniform vec4 c;void main(){gl_FragColor=c;}")
            textProgram=program("attribute vec3 p;attribute vec2 uv;varying vec2 t;void main(){t=uv;gl_Position=vec4(p,1.0);}","precision mediump float;uniform sampler2D image;varying vec2 t;void main(){gl_FragColor=texture2D(image,t);}")
        } catch (failure: Throwable) { close();throw failure }
    }
    fun draw(input: SpatialRenderInput, width: Int, height: Int, nanos: Long): SpatialPresentedFrame {
        fontScale=input.fontScale.coerceIn(.7f,2f)
        motion.update(input.orientation);motion.advance(nanos)
        val desiredCamera=resolveSpatialCamera(input.snapshot,input.orientation,motion.shown,input.screenRotation,input.conversion,SystemClock.elapsedRealtime(),input.freeYaw,input.freePitch)
        cameraDriven=input.freeYaw!=null||input.snapshot.mountMode==SpatialMountMode.VESSEL_MOUNTED
        val camera=if(cameraDriven)
            cameraMotion.present(desiredCamera,if(input.freeYaw!=null)"free"else "heading:${input.snapshot.vesselHeading?.source}",input.snapshot.vesselHeading?.observedElapsedMillis?.takeIf{input.freeYaw==null}?:SystemClock.elapsedRealtime(),nanos)else desiredCamera
        val projection=SpatialProjection(width,height,camera)
        GLES20.glViewport(0,0,width,height);GLES20.glClearColor(.022f,.022f,.022f,1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA)
        // 地平与少量地面线提供纵深；没有雷达环，也不伪造地形/可航水域。
        val subdued=camera.issue!=null
        val referenceColor=floatArrayOf(.45f,.45f,.45f,if(subdued).22f else .42f)
        val centerBearing=camera.trueBearing?:0.0
        val forward=bearingVector(centerBearing,1.0,0.0)
        val right=SpatialVector(-forward.z,0.0,forward.x)
        for(distance in listOf(3.0,6.0,12.0,24.0,48.0,90.0)) {
            val center=forward.scale(distance)
            val left=center+right.scale(-100.0);val end=center+right.scale(100.0)
            mesh(floatArrayOf(left.x.toFloat(),-.03f,left.z.toFloat(),end.x.toFloat(),-.03f,end.z.toFloat()),GLES20.GL_LINES,projection.matrix,referenceColor)
        }
        val guide=input.snapshot.steering?:input.snapshot.current
        guide?.bearingTrueDegrees?.takeIf{it.isFinite()&&input.snapshot.live&&!guide.nearTarget&&camera.issue==null}?.let { bearing ->
            val f=bearingVector(bearing,1.0,0.0);val side=SpatialVector(-f.z,0.0,f.x)
            for(distance in listOf(3.0,4.8,7.0)) {
                val tip=f.scale(distance+.35)
                val left=f.scale(distance)+side.scale(-.28);val rightPoint=f.scale(distance)+side.scale(.28)
                mesh(floatArrayOf(left.x.toFloat(),.02f,left.z.toFloat(),tip.x.toFloat(),.02f,tip.z.toFloat(),rightPoint.x.toFloat(),.02f,rightPoint.z.toFloat()),GLES20.GL_LINE_STRIP,projection.matrix,floatArrayOf(.94f,.94f,.94f,.8f))
            }
        }
        val hits=mutableListOf<SpatialHit>()
        val live = input.snapshot.live && camera.issue == null
        val current=input.snapshot.current
        // 下一目标较远且更弱，仍来源于同一冻结路线；不反算不存在的外部坐标。
        input.snapshot.next?.let { target -> target(target,12.0,false,projection,camera,input,hits,live) }
        current?.let { target -> target(target,10.0,true,projection,camera,input,hits,live) }
        input.snapshot.steering?.let{target->target(target,8.8,true,projection,camera,input,hits,live)}
        input.snapshot.vesselHeading?.takeIf{it.ageMillis in 0..10_000L&&it.trueDegrees.isFinite()}?.let {
            val p=projection.project(bearingVector(it.trueDegrees,9.0,.85))
            if(p.inside) label(if(input.chinese)"船艏" else "BOW",p.x,p.y,13f,0xffeeeeee.toInt(),width,height)
        }
        input.snapshot.courseOverGround?.takeIf{it.ageMillis in 0..10_000L&&it.trueDegrees.isFinite()}?.let {
            val p=projection.project(bearingVector(it.trueDegrees,9.0,.3))
            if(p.inside) label("COG",p.x,p.y,12f,0xff999999.toInt(),width,height)
        }
        val pixelsPerDegree=(width-32*density).coerceAtLeast(1f)/90f
        val tapeY=26*density
        for(bearing in 0 until 360 step 10) {
            val delta=signedBearing(bearing-centerBearing)
            if(abs(delta)>43)continue
            val x=width/2f+delta.toFloat()*pixelsPerDegree
            screenLines(floatArrayOf(x,tapeY+12*density,x,tapeY+(if(bearing%30==0)19 else 16)*density),width,height,floatArrayOf(.65f,.65f,.65f,.8f))
            if(bearing%30==0)label(when(bearing){0->"N";90->"E";180->"S";270->"W";else->"$bearing°"},x,tapeY-2*density,12f,0xffbcbcbc.toInt(),width,height)
        }
        label("${centerBearing.roundToInt()}° T",width/2f,tapeY+40*density,13f,0xffeeeeee.toInt(),width,height)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        // 中央准线属于视线，不表示船艏；不同图元均不使用另一套方位计算。
        val cx=width/2f;val cy=height/2f;val r=7*density
        screenLines(floatArrayOf(cx-r,cy,cx+r,cy,cx,cy-r,cx,cy+r),width,height,floatArrayOf(.85f,.85f,.85f,.65f))
        val delta=guide?.bearingTrueDegrees?.takeIf{it.isFinite()&&!guide.nearTarget}?.let{ b->camera.trueBearing?.let{signedBearing(b-it)}}
        check(EGL14.eglSwapBuffers(display,surface))
        if(textures.size>80){textures.entries.take(textures.size-64).toList().forEach{(key,t)->GLES20.glDeleteTextures(1,intArrayOf(t.id),0);textures.remove(key)}}
        return SpatialPresentedFrame(camera,delta,hits)
    }
    private fun target(target: SpatialNavigationTarget,radius:Double,primary:Boolean,p:SpatialProjection,camera:SpatialCamera,input:SpatialRenderInput,hits:MutableList<SpatialHit>,live:Boolean) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val bearing=target.bearingTrueDegrees?.takeIf{it.isFinite()}?:return
        if(target.nearTarget)return
        val point=bearingVector(bearing,radius,1.6)
        val projected=p.project(point)
        val color=if(primary)floatArrayOf(.95f,.95f,.95f,if(live)1f else .45f)else floatArrayOf(.56f,.56f,.56f,if(live).7f else .3f)
        if(projected.inside){
            val a=if(primary).55f else .38f;val x=point.x.toFloat();val z=point.z.toFloat()
            val angle=Math.toRadians(bearing);val rx=cos(angle).toFloat()*a;val rz=sin(angle).toFloat()*a
            if(target.steering) {
                mesh(floatArrayOf(x-rx,1.15f,z-rz,x,1.5f,z,x+rx,1.15f,z+rz),GLES20.GL_LINE_STRIP,p.matrix,color)
            } else {
                // 目标门是抽象方向符号，其高度不是物理障碍物的高度。
                mesh(floatArrayOf(x-rx,.1f,z-rz,x-rx,2.05f,z-rz,x+rx,2.05f,z+rz,x+rx,.1f,z+rz),GLES20.GL_LINE_STRIP,p.matrix,color)
                mesh(floatArrayOf(x-rx,.1f,z-rz,x+rx,.1f,z+rz),GLES20.GL_LINES,p.matrix,floatArrayOf(color[0],color[1],color[2],.3f))
            }
            if (target.steering) {
                hits+=SpatialHit(target.id,RectF(projected.x-28*density,projected.y-28*density,projected.x+28*density,projected.y+28*density))
                return
            }
            val caption=(if(primary)"" else if(input.chinese)"下一站 · " else "Next · ")+target.name.take(26)
            val textY=projected.y+(if(primary)-40 else 42)*density
            val bounds=label(caption,projected.x,textY,if(primary)17f else 13f,if(primary)0xffeeeeee.toInt()else 0xffaaaaaa.toInt(),p.width,p.height)
            input.distanceLabels[target.id]?.takeUnless{target.steering}?.let{label(it,projected.x,textY+22*density,13f,0xffcccccc.toInt(),p.width,p.height)}
            bounds.union(projected.x-28*density,projected.y-28*density,projected.x+28*density,projected.y+28*density)
            hits+=SpatialHit(target.id,bounds)
        }else if(primary){
            var dx=projected.nx;var dy=-projected.ny
            val relative=camera.trueBearing?.let{signedBearing(bearing-it)}
            if(projected.behind){
                if(relative!=null && abs(relative)<172)behindSide=if(relative<0)-1f else 1f
                else if(relative!=null && abs(relative)<178 && abs(dx)>.2f)behindSide=if(dx<0)-1f else 1f
                dx=behindSide;dy=0f
            }
            val length=hypot(dx,dy).coerceAtLeast(.001f);dx/=length;dy/=length
            val margin=40*density
            val scale=min((p.width/2f-margin)/abs(dx).coerceAtLeast(.001f),(p.height/2f-margin)/abs(dy).coerceAtLeast(.001f)).coerceAtLeast(0f)
            val x=p.width/2f+dx*scale;val y=p.height/2f+dy*scale;val size=10*density
            screenLines(floatArrayOf(x-dx*size-dy*size,y-dy*size+dx*size,x,y,x,y,x-dx*size+dy*size,y-dy*size-dx*size),p.width,p.height,color)
            label(if(projected.behind){if(input.chinese)"身后"else"Behind"}else target.name.take(16),x-dx*22*density,y-dy*22*density,13f,0xffeeeeee.toInt(),p.width,p.height)
            hits+=SpatialHit(target.id,RectF(x-30*density,y-30*density,x+30*density,y+30*density))
        }
    }
    private fun screenLines(points:FloatArray,w:Int,h:Int,color:FloatArray){
        val vertices=FloatArray(points.size/2*3)
        for(i in points.indices step 2){vertices[i/2*3]=points[i]/w*2-1;vertices[i/2*3+1]=1-points[i+1]/h*2}
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);mesh(vertices,GLES20.GL_LINES,identity,color)
    }
    private fun mesh(points:FloatArray,mode:Int,m:FloatArray,color:FloatArray){
        GLES20.glUseProgram(colorProgram)
        val position=GLES20.glGetAttribLocation(colorProgram,"p")
        GLES20.glEnableVertexAttribArray(position);GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,0,buffer(points))
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(colorProgram,"m"),1,false,m,0)
        GLES20.glUniform4fv(GLES20.glGetUniformLocation(colorProgram,"c"),1,color,0)
        GLES20.glLineWidth(max(1f,density));GLES20.glDrawArrays(mode,0,points.size/3);GLES20.glDisableVertexAttribArray(position)
    }
    private fun label(text:String,cx:Float,cy:Float,sp:Float,color:Int,w:Int,h:Int):RectF{
        val key="$text:$sp:$color:$fontScale"
        val texture=textures.getOrPut(key){
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=sp*density*fontScale;typeface=face}
            val fm=paint.fontMetrics;val tw=ceil(paint.measureText(text)+12*density).toInt().coerceIn(1,2048);val th=ceil(fm.bottom-fm.top+6*density).toInt().coerceAtLeast(1)
            val bitmap=Bitmap.createBitmap(tw,th,Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawText(text,6*density,-fm.top+3*density,paint)
            val ids=IntArray(1);GLES20.glGenTextures(1,ids,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();TextTexture(ids[0],tw,th)
        }
        val tw=min(texture.width.toFloat(),(w-16*density).coerceAtLeast(1f));val th=texture.height*tw/texture.width
        val left=(cx-tw/2).coerceIn(0f,(w-tw).coerceAtLeast(0f));val top=(cy-th/2).coerceIn(0f,(h-th).coerceAtLeast(0f))
        val l=left/w*2-1;val r=(left+tw)/w*2-1;val t=1-top/h*2;val b=1-(top+th)/h*2
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glUseProgram(textProgram);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture.id)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(textProgram,"image"),0)
        val position=GLES20.glGetAttribLocation(textProgram,"p");val uv=GLES20.glGetAttribLocation(textProgram,"uv")
        GLES20.glEnableVertexAttribArray(position);GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,0,buffer(floatArrayOf(l,t,0f,l,b,0f,r,t,0f,r,b,0f)))
        GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,0,uvBuffer)
        GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glDisableVertexAttribArray(position);GLES20.glDisableVertexAttribArray(uv)
        return RectF(left,top,left+tw,top+th)
    }
    private fun program(vertex:String,fragment:String):Int{
        fun shader(type:Int,source:String):Int{
            val id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id)
            val ok=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0)
            if(ok[0]==0){GLES20.glDeleteShader(id);error("Navigation shader")};return id
        }
        val v=shader(GLES20.GL_VERTEX_SHADER,vertex);var f=0;var p=0
        try{f=shader(GLES20.GL_FRAGMENT_SHADER,fragment);p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);val ok=IntArray(1);GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);check(ok[0]!=0);return p}
        catch(failure:Throwable){if(p!=0)GLES20.glDeleteProgram(p);throw failure}
        finally{GLES20.glDeleteShader(v);if(f!=0)GLES20.glDeleteShader(f)}
    }
    private fun buffer(values:FloatArray):FloatBuffer=scratch.apply{clear();put(values);position(0)}
    override fun close(){
        if(display==EGL14.EGL_NO_DISPLAY)return
        if(context!=EGL14.EGL_NO_CONTEXT){
            textures.values.forEach{GLES20.glDeleteTextures(1,intArrayOf(it.id),0)};textures.clear()
            if(colorProgram!=0)GLES20.glDeleteProgram(colorProgram);if(textProgram!=0)GLES20.glDeleteProgram(textProgram)
        }
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
        if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface)
        if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context)
        EGL14.eglReleaseThread();EGL14.eglTerminate(display)
        display=EGL14.EGL_NO_DISPLAY;context=EGL14.EGL_NO_CONTEXT;surface=EGL14.EGL_NO_SURFACE
    }
}
