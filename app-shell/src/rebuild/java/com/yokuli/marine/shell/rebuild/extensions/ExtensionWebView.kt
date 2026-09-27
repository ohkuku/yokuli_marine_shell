package com.yokuli.marine.shell.rebuild.extensions

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.os.Bundle
import android.os.Parcel
import android.webkit.PermissionRequest
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.yokuli.marine.shell.rebuild.ui.Label
import com.yokuli.marine.shell.rebuild.ui.AppBackHandler
import com.yokuli.shell.compose.LocalInternalAppPageKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * 每个包使用独立 HTTPS origin，SDK 只由来源受限的消息端口承接。
 * Android 管理 WebView renderer；独立 origin 不代表每个应用拥有独立 Android 进程。
 * 扩展无网络/文件/Android bridge，暂停或销毁页面会取消请求，Core 的真实采集不属于它。
 */
@Composable
fun ExtensionWebView(
    installed: ExtensionInstalled,
    assetLoader: (String) -> InputStream?,
    onRequest: suspend (method: String, params: JSONObject) -> JSONObject,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val pageKey = LocalInternalAppPageKey.current
    val installationIdentity = listOf(installed.manifest.id, installed.digest, installed.installedAt.toString(),
        installed.grants.sorted().joinToString(",")).joinToString("/")
    val visit = rememberSaveable(pageKey, installationIdentity, saver = ExtensionVisitSaver) { SavedExtensionVisit(null) }
    val currentRequest by rememberUpdatedState(onRequest)
    val currentLoader by rememberUpdatedState(assetLoader)
    val currentError by rememberUpdatedState(onError)
    val currentEnabled by rememberUpdatedState(enabled)
    var failure by remember(pageKey, installationIdentity) { mutableStateOf<String?>(null) }
    val session = remember(context, pageKey, installationIdentity) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            failure = "请先更新 Android System WebView，再打开这个应用。此设备尚不支持安全的应用消息通道。"
            null
        } else {
            runCatching {
                RestrictedExtensionSession(
                    WebView(context), installed, scope,
                    loader = { path -> currentLoader(path) },
                    request = { method, params -> currentRequest(method, params) },
                    failed = { reason -> failure = reason },
                    installationIdentity = installationIdentity,
                    savedPage = visit.saved,
                )
            }.getOrElse {
                failure = "无法启动应用。请确认 Android System WebView 已启用：${it.message.orEmpty().take(140)}"
                null
            }
        }
    }
    // SaveableStateHolder 取值时立即捕获，不能依赖子页面 onDispose 的执行先后。
    visit.capture = { session?.saveVisit() ?: visit.saved }
    LaunchedEffect(failure) { failure?.let { session?.close(); currentError(it) } }
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (currentEnabled) session?.resume() else session?.pause()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    visit.saved = session?.saveVisit() ?: visit.saved
                    session?.pause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    visit.saved = session?.saveVisit() ?: visit.saved
                    session?.close()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (currentEnabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session?.resume() else session?.pause()
        onDispose {
            lifecycle.removeObserver(observer)
            visit.saved = session?.saveVisit() ?: visit.saved
            visit.capture = null
            session?.close()
        }
    }
    LaunchedEffect(session, enabled) {
        if (enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session?.resume() else session?.pause()
    }
    AppBackHandler(enabled = enabled && failure == null && session?.canGoBack == true) { session?.goBack() }
    Box(modifier) {
        if (failure != null || session == null) Label(failure ?: "应用无法启动", modifier = Modifier.padding(24.dp))
        else AndroidView(factory = { session.view }, modifier = Modifier.fillMaxSize())
    }
}

/** 保存的是访问路径及滚动位置；业务表单/草稿仍应由扩展使用 SDK storage 持久化。 */
private class SavedExtensionVisit(var saved: Bundle?) {
    var capture: (() -> Bundle?)? = null
}

private val ExtensionVisitSaver = Saver<SavedExtensionVisit, Bundle>(
    save = { it.capture?.invoke() ?: it.saved ?: Bundle() },
    restore = { SavedExtensionVisit(it) },
)

private class RestrictedExtensionSession(
    val view: WebView,
    private val installed: ExtensionInstalled,
    private val scope: CoroutineScope,
    private val loader: (String) -> InputStream?,
    private val request: suspend (String, JSONObject) -> JSONObject,
    private val failed: (String) -> Unit,
    private val installationIdentity: String,
    private val savedPage: Bundle?,
) {
    private val host = "${installed.manifest.id}.yokuli.invalid"
    private val origin = "https://$host"
    private val root = installed.directory.canonicalFile
    private val jobs = mutableMapOf<String, Job>()
    private var active = false
    private var started = false
    @Volatile private var closed = false
    private var messageWindowStart = SystemClock.elapsedRealtime()
    private var messageCount = 0
    private var restoredScroll: Pair<Int, Int>? = null
    var canGoBack by mutableStateOf(false)
        private set

    init {
        try { configure() } catch (failure: Throwable) { view.destroy(); throw failure }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun configure() {
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // origin 的子域不是 Cookie 的隔离边界。扩展只通过宿主管理的私有存储持久化。
        CookieManager.getInstance().apply {
            setAcceptCookie(false)
            setAcceptThirdPartyCookies(view, false)
        }
        view.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            domStorageEnabled = false
            databaseEnabled = false
            blockNetworkLoads = true
            // 包内 HTTPS 图片仍交给资源拦截器；网络本身由 blockNetworkLoads 禁止。
            blockNetworkImage = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
            safeBrowsingEnabled = true
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
        }
        // ServiceWorker 请求不经过 WebViewClient；必须独立禁止，不能只依赖页面 CSP。
        android.webkit.ServiceWorkerController.getInstance().apply {
            serviceWorkerWebSettings.apply {
                blockNetworkLoads = true
                allowContentAccess = false
                allowFileAccess = false
            }
            setServiceWorkerClient(object : android.webkit.ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse =
                    WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", mapOf("Cache-Control" to "no-store"),
                        ByteArrayInputStream("Workers are disabled".toByteArray()))
            })
        }
        // 即便网页主动申请，也不能越过宿主的三个显式 SDK 权限。
        view.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback?) {
                callback?.invoke(origin, false, false)
            }
            override fun onShowFileChooser(webView: WebView?, filePathCallback: android.webkit.ValueCallback<Array<Uri>>?, fileChooserParams: WebChromeClient.FileChooserParams?): Boolean {
                filePathCallback?.onReceiveValue(null)
                return true
            }
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                result?.cancel(); return true
            }
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                result?.cancel(); return true
            }
            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: android.webkit.JsPromptResult?): Boolean {
                result?.cancel(); return true
            }
        }
        view.setDownloadListener { _, _, _, _, _ -> Unit }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !request.isForMainFrame || !isLocal(request.url) || request.method != "GET"

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = url == null || !isLocal(Uri.parse(url))

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
                if (closed || request.method != "GET" || !isLocal(request.url)) return blocked()
                return serve(request.url)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                // 顶层导航结束旧文档的权限请求，回复不能送往下一张页面。
                cancelRequests()
                if (url == null || !isLocal(Uri.parse(url))) {
                    view.stopLoading()
                    failed("应用只能打开安装包中的页面；请使用系统导航打开其他应用。")
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame && !closed) failed("应用页面未能打开，请在应用中心重新安装这个应用。")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && !closed) failed("应用没有可打开的页面，请返回后重新安装这个应用。")
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (!closed) {
                    canGoBack = view.canGoBack()
                    restoredScroll?.let { (x, y) -> view.post { if (!closed) view.scrollTo(x, y) } }
                    restoredScroll = null
                }
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) { if (!closed) canGoBack = view.canGoBack() }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                close()
                failed("应用显示进程已停止。返回后重新打开即可，航行和采集仍由系统继续。")
                return true
            }
        }
        WebViewCompat.addWebMessageListener(view, BRIDGE, setOf(origin)) { _, message, sourceOrigin, isMainFrame, reply ->
            if (closed || !active || !isMainFrame || sourceOrigin.toString().removeSuffix("/") != origin) return@addWebMessageListener
            if (message.type != WebMessageCompat.TYPE_STRING) return@addWebMessageListener
            receive(message.data, reply)
        }
    }

    fun saveVisit(): Bundle? {
        if (closed) return null
        return runCatching {
            val history = view.copyBackForwardList()
            val url = history.currentItem?.url ?: view.url
            if (!validHistoryUrl(url)) return@runCatching null
            val result = Bundle().apply {
                putString("installation", installationIdentity)
                putString("url", url)
                putInt("scrollX", view.scrollX.coerceIn(0, 2_000_000))
                putInt("scrollY", view.scrollY.coerceIn(0, 2_000_000))
            }
            if (history.size in 1..MAX_HISTORY_PAGES) {
                val urls = List(history.size) { history.getItemAtIndex(it).url }
                if (urls.all(::validHistoryUrl)) {
                    val nativeState = Bundle()
                    if (view.saveState(nativeState) != null && bundleSize(nativeState) <= MAX_HISTORY_BYTES) {
                        result.putBundle("webView", nativeState)
                        result.putStringArrayList("history", ArrayList(urls))
                    }
                }
            }
            result
        }.getOrNull()
    }

    private fun restoreOrOpen() {
        val saved = savedPage?.takeIf { it.getString("installation") == installationIdentity }
        val savedUrl = saved?.getString("url")?.takeIf(::validHistoryUrl)
        if (saved != null && savedUrl != null) {
            restoredScroll = saved.getInt("scrollX").coerceIn(0, 2_000_000) to saved.getInt("scrollY").coerceIn(0, 2_000_000)
            val urls = saved.getStringArrayList("history")
            val nativeState = saved.getBundle("webView")
            // 所有历史项须属于本包，绝不把未经检查的浏览器 Bundle 当作任意导航入口。
            if (nativeState != null && urls != null && urls.size in 1..MAX_HISTORY_PAGES &&
                urls.all(::validHistoryUrl) && bundleSize(nativeState) <= MAX_HISTORY_BYTES) {
                val restored = runCatching { view.restoreState(nativeState) }.getOrNull()
                if (restored != null && restored.size == urls.size &&
                    List(restored.size) { restored.getItemAtIndex(it).url } == urls) {
                    canGoBack = view.canGoBack()
                    return
                }
                view.stopLoading()
                view.clearHistory()
            }
            view.loadUrl(savedUrl)
        } else view.loadUrl("$origin/${installed.manifest.entry}")
    }

    private fun validHistoryUrl(url: String?): Boolean = url != null && url.length <= 2048 &&
        runCatching { isLocal(Uri.parse(url)) }.getOrDefault(false)

    private fun bundleSize(bundle: Bundle): Int {
        val parcel = Parcel.obtain()
        return try { parcel.writeBundle(bundle); parcel.dataSize() } finally { parcel.recycle() }
    }

    fun resume() {
        if (closed || active) return
        active = true
        view.onResume()
        // 入场期间 Shell 会禁止输入；首页必须等消息桥可用后再运行 SDK 初始化。
        if (!started) {
            started = true
            try { restoreOrOpen() } catch (_: Exception) {
                failed("应用页面未能恢复，请返回后重新打开这个应用。")
                return
            }
        }
        // JS 计时器状态同步只是 UI 生命周期，不采集、不拥有后台任务。
        view.evaluateJavascript("window.dispatchEvent(new Event('yokuli:resume'))", null)
    }

    fun goBack() { if (!closed && active && view.canGoBack()) view.goBack() }

    fun pause() {
        if (closed) return
        active = false
        cancelRequests()
        view.evaluateJavascript("window.dispatchEvent(new Event('yokuli:pause'))", null)
        view.onPause()
    }

    fun close() {
        if (closed) return
        closed = true
        active = false
        cancelRequests()
        WebViewCompat.removeWebMessageListener(view, BRIDGE)
        view.stopLoading()
        view.onPause()
        view.webChromeClient = null
        view.webViewClient = WebViewClient()
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.removeAllViews()
        view.destroy()
    }

    private fun cancelRequests() {
        jobs.values.toList().forEach { it.cancel() }
        jobs.clear()
    }

    private fun receive(data: String?, reply: JavaScriptReplyProxy) {
        if (data == null || data.length > MAX_MESSAGE_BYTES || data.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) return
        var id: String? = null
        try {
            val message = parseExtensionJson(data)
            val requestId = message.getString("id")
            id = requestId
            require(requestId.length in 1..80 && requestId.all { it.isLetterOrDigit() || it in "-_:" }) { "请求标识无效" }
            val method = message.getString("method")
            require(method.length in 1..80 && method.matches(Regex("[a-z][a-zA-Z0-9.]*"))) { "请求方法无效" }
            val params = message.optJSONObject("params") ?: JSONObject()
            val now = SystemClock.elapsedRealtime()
            if (now - messageWindowStart >= 1_000) { messageWindowStart = now; messageCount = 0 }
            if (++messageCount > 32 || jobs.size >= 8) {
                replyError(reply, requestId, "BUSY", "请求过于频繁，请稍后重试")
                return
            }
            if (jobs.containsKey(requestId)) { replyError(reply, requestId, "DUPLICATE_REQUEST", "请求编号正在使用"); return }
            // LAZY 使登记先于执行，立即完成的请求也会正确释放并发额度。
            val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    val result = request(method, params)
                    val output = JSONObject().put("id", requestId).put("result", result).toString()
                    require(output.toByteArray(Charsets.UTF_8).size <= 256 * 1024) { "响应过大，请缩小请求范围" }
                    if (active && !closed) reply.postMessage(output)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (failure: Exception) {
                    if (active && !closed) {
                        val bridgeError = failure as? ExtensionBridgeException
                        replyError(reply, requestId, bridgeError?.code ?: "REQUEST_FAILED", failure.message?.take(200) ?: "请求未完成", bridgeError?.retryAfterMillis)
                    }
                } finally { if (jobs[requestId] === coroutineContext[Job]) jobs.remove(requestId) }
            }
            jobs[requestId] = job
            job.start()
        } catch (failure: Exception) {
            id?.let { replyError(reply, it.take(80), "INVALID_REQUEST", failure.message?.take(120) ?: "请求格式无效") }
        }
    }

    private fun replyError(reply: JavaScriptReplyProxy, id: String, code: String, message: String, retryAfterMillis: Long? = null) {
        val error = JSONObject().put("code", code).put("message", message)
        retryAfterMillis?.let { error.put("retryAfterMillis", it) }
        runCatching { reply.postMessage(JSONObject().put("id", id).put("error", error).toString()) }
    }

    private fun isLocal(uri: Uri): Boolean = uri.scheme == "https" && uri.host == host && uri.port == -1 && uri.userInfo == null

    private fun serve(uri: Uri): WebResourceResponse { return try {
        val path = uri.path.orEmpty().removePrefix("/").ifEmpty { installed.manifest.entry }
        if (!ExtensionPackageManager.validResourcePath(path)) return blocked()
        val stream = if (path.startsWith("_sdk/")) {
            if (path != "_sdk/yokuli.js" && path != "_sdk/yokuli.css") return blocked()
            loader(path)
        } else {
            val file = File(root, path).canonicalFile
            if (!file.path.startsWith(root.path + File.separator)) null
            else if (file.isFile) file.inputStream() else loader(path)
        }
        if (stream == null) response(404, "Not Found", "text/plain", ByteArrayInputStream("Resource not found".toByteArray()))
        else response(200, "OK", mime(path), stream)
    } catch (_: Exception) { response(404, "Not Found", "text/plain", ByteArrayInputStream("Resource unavailable".toByteArray())) } }

    private fun blocked(): WebResourceResponse = response(403, "Forbidden", "text/plain", ByteArrayInputStream("Blocked by Yokuli".toByteArray()))

    private fun response(code: Int, reason: String, type: String, input: InputStream) = WebResourceResponse(
        type, "UTF-8", code, reason,
        mapOf(
            "Content-Security-Policy" to "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; media-src 'self'; connect-src 'none'; object-src 'none'; frame-src 'none'; frame-ancestors 'none'; worker-src 'none'; base-uri 'none'; form-action 'none'",
            "X-Content-Type-Options" to "nosniff",
            "Cache-Control" to "no-store",
            "Referrer-Policy" to "no-referrer",
            "Permissions-Policy" to "camera=(), microphone=(), geolocation=(), accelerometer=(), gyroscope=(), magnetometer=(), usb=(), bluetooth=(), fullscreen=(), clipboard-read=(), clipboard-write=(), payment=()",
        ), input,
    )

    private fun mime(path: String): String = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "html", "htm" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "ogg" -> "audio/ogg"
        "mp3" -> "audio/mpeg"
        else -> "text/plain"
    }

    companion object {
        private const val BRIDGE = "YokuliHost"
        private const val MAX_MESSAGE_BYTES = 96 * 1024
        private const val MAX_HISTORY_PAGES = 24
        private const val MAX_HISTORY_BYTES = 16 * 1024
    }
}
