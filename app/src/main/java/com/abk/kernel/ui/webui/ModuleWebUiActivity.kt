package com.abk.kernel.ui.webui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.abk.kernel.R
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.RootUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.concurrent.thread

class ModuleWebUiActivity : Activity() {

    private lateinit var webView: WebView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }
    private val moduleId: String by lazy {
        intent.getStringExtra(EXTRA_MODULE_ID).orEmpty().trim()
    }
    private val moduleName: String by lazy {
        intent.getStringExtra(EXTRA_MODULE_NAME).orEmpty().ifBlank { moduleId }
    }
    private val moduleDir: String
        get() = "/data/adb/modules/$moduleId"

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!RootUtils.isSafeModuleIdForPath(moduleId)) {
            Toast.makeText(this, getString(R.string.runtime_module_unavailable), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        enterImmersiveMode()
        title = moduleName
        thread(name = "abk-webui-init") {
            val debugEnabled = runCatching {
                runBlocking {
                    PreferencesRepository(applicationContext).webViewDebugEnabled.first()
                }
            }.getOrDefault(false)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                setupWebView(debugEnabled)
            }
        }
    }

    private fun setupWebView(debugEnabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(debugEnabled)
        }
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            webChromeClient = WebChromeClient()
            webViewClient = ModuleWebViewClient(moduleId)
            addJavascriptInterface(ModuleWebBridge(this@ModuleWebUiActivity, this, moduleId, moduleDir), "ksu")
        }
        setContentView(webView)
        loadModuleWebPage()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun loadModuleWebPage() {
        thread(name = "abk-webui-load") {
            val indexHtml = RootUtils.readModuleWebResource(moduleId, "index.html")
                ?: RootUtils.readModuleWebResource(moduleId, "index.htm")
            if (indexHtml == null) {
                webView.post {
                    Toast.makeText(this, getString(R.string.runtime_module_unavailable), Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@thread
            }
            val html = indexHtml.toString(Charsets.UTF_8)
            webView.post {
                webView.loadDataWithBaseURL(WEB_ORIGIN, html, "text/html", "utf-8", WEB_ORIGIN)
            }
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private class ModuleWebViewClient(
        private val moduleId: String
    ) : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url ?: return null
            if (url.host != WEB_HOST) return null
            val relativePath = url.path?.trimStart('/').orEmpty().ifBlank { "index.html" }
            val bytes = RootUtils.readModuleWebResource(moduleId, relativePath) ?: return null
            return WebResourceResponse(
                mimeType(relativePath),
                "utf-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream(bytes)
            )
        }
    }

    private class ModuleWebBridge(
        private val activity: Activity,
        private val webView: WebView,
        private val moduleId: String,
        private val moduleDir: String
    ) {
        @JavascriptInterface
        fun exec(command: String): String =
            RootUtils.execRootCommandForWebUi(command, cwd = moduleDir)
                .output
                .joinToString("\n")

        @JavascriptInterface
        fun exec(command: String, callbackFunc: String) {
            exec(command, null, callbackFunc)
        }

        @JavascriptInterface
        fun exec(command: String, options: String?, callbackFunc: String) {
            thread(name = "abk-webui-exec") {
                val finalCommand = commandWithOptions(command, options)
                val result = RootUtils.execRootCommandForWebUi(finalCommand, cwd = moduleDir)
                val stdout = result.output.joinToString("\n")
                val code = if (result.success) 0 else 1
                webView.post {
                    webView.evaluateJavascript(
                        "try { $callbackFunc($code, ${JSONObject.quote(stdout)}, \"\"); } catch(e) { console.error(e); }",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun spawn(command: String, args: String, options: String?, callbackFunc: String) {
            thread(name = "abk-webui-spawn") {
                val argString = runCatching {
                    val array = JSONArray(args)
                    buildString {
                        for (index in 0 until array.length()) {
                            if (isNotEmpty()) append(' ')
                            append(shellQuote(array.getString(index)))
                        }
                    }
                }.getOrDefault("")
                val finalCommand = commandWithOptions(listOf(command, argString).filter { it.isNotBlank() }.joinToString(" "), options)
                val result = RootUtils.execRootCommandForWebUi(finalCommand, cwd = moduleDir)
                val output = result.output.joinToString("\n")
                val code = if (result.success) 0 else 1
                webView.post {
                    val quotedOutput = JSONObject.quote(output)
                    webView.evaluateJavascript(
                        """
                        try {
                            var cb = $callbackFunc;
                            if (cb && cb.stdout && cb.stdout.emit) cb.stdout.emit('data', $quotedOutput);
                            if (cb && cb.emit) cb.emit('exit', $code);
                        } catch(e) { console.error(e); }
                        """.trimIndent(),
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun toast(message: String) {
            webView.post {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun moduleInfo(): String = RootUtils.moduleInfoJson(moduleId)

        @JavascriptInterface
        fun exit() {
            webView.post { activity.finish() }
        }

        private fun commandWithOptions(command: String, options: String?): String {
            if (options.isNullOrBlank()) return command
            val json = runCatching { JSONObject(options) }.getOrNull() ?: return command
            val prefix = buildString {
                json.optJSONObject("env")?.let { env ->
                    env.keys().forEach { key ->
                        append("export ")
                        append(key)
                        append("=")
                        append(shellQuote(env.optString(key)))
                        append('\n')
                    }
                }
                json.optString("cwd").takeIf { it.isNotBlank() }?.let { cwd ->
                    append("cd ")
                    append(shellQuote(cwd))
                    append(" 2>/dev/null || exit 2\n")
                }
            }
            return prefix + command
        }
    }

    companion object {
        const val EXTRA_MODULE_ID = "module_id"
        const val EXTRA_MODULE_NAME = "module_name"
        private const val WEB_HOST = "abk-module.local"
        private const val WEB_ORIGIN = "https://$WEB_HOST/index.html"

        private fun mimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "text/plain"
        }

        private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
    }
}
