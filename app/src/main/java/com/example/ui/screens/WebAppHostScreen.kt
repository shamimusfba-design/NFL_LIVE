package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

private const val VIRTUAL_ASSET_HOST = "appassets.androidplatform.net"
private const val START_URL = "https://$VIRTUAL_ASSET_HOST/index.html"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebAppHostScreen(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var canGoBack by remember { mutableStateOf(false) }

  BackHandler(enabled = canGoBack) {
    val wv = webViewRef
    if (wv != null && wv.canGoBack()) {
      wv.goBack()
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      webViewRef?.stopLoading()
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF061B43))
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .testTag("web_app_host_screen")
  ) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { ctx ->
        WebView(ctx).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          setBackgroundColor(AndroidColor.parseColor("#061B43"))

          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
          }

          webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
              return super.onConsoleMessage(consoleMessage)
            }
          }

          webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
              view: WebView?,
              request: WebResourceRequest?
            ): WebResourceResponse? {
              val uri = request?.url ?: return null
              if (!uri.host.equals(VIRTUAL_ASSET_HOST, ignoreCase = true)) {
                return null
              }

              val rawPath = uri.path?.trimStart('/').orEmpty()
              val assetRelativePath = if (rawPath.isEmpty()) "index.html" else rawPath
              val fullAssetPath = "webapp/$assetRelativePath"

              return try {
                val inputStream = ctx.assets.open(fullAssetPath)
                val mimeType = resolveMimeType(assetRelativePath)
                val headers = mapOf(
                  "Access-Control-Allow-Origin" to "*",
                  "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS",
                  "Cache-Control" to "no-cache"
                )
                WebResourceResponse(
                  mimeType,
                  "UTF-8",
                  200,
                  "OK",
                  headers,
                  inputStream
                )
              } catch (_: Exception) {
                null
              }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
              super.onPageFinished(view, url)
              canGoBack = view?.canGoBack() == true
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
              super.doUpdateVisitedHistory(view, url, isReload)
              canGoBack = view?.canGoBack() == true
            }
          }

          webViewRef = this
          loadUrl(START_URL)
        }
      },
      update = { view ->
        webViewRef = view
      }
    )
  }
}

private fun resolveMimeType(path: String): String {
  val lower = path.lowercase()
  return when {
    lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
    lower.endsWith(".js") || lower.endsWith(".mjs") -> "application/javascript"
    lower.endsWith(".css") -> "text/css"
    lower.endsWith(".svg") -> "image/svg+xml"
    lower.endsWith(".png") -> "image/png"
    lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
    lower.endsWith(".webp") -> "image/webp"
    lower.endsWith(".ico") -> "image/x-icon"
    lower.endsWith(".webmanifest") -> "application/manifest+json"
    lower.endsWith(".json") -> "application/json"
    lower.endsWith(".woff2") -> "font/woff2"
    lower.endsWith(".woff") -> "font/woff"
    lower.endsWith(".ttf") -> "font/ttf"
    else -> "application/octet-stream"
  }
}
