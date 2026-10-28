package dev.bonsai.pocket

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/** No native bridge, filesystem, network, popups, browser downloads, or device permissions. */
@SuppressLint("SetJavaScriptEnabled")
class SandboxWebView(context:Context,onFailure:()->Unit = {}) : WebView(context) {
    var scriptError:String?=null;private set
    var onScriptError:(String)->Unit = {}
    var onRendererFailure:()->Unit = onFailure
    init {
        layoutParams=ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
        background=GradientDrawable().apply {setColor(Color.WHITE);cornerRadius=16*resources.displayMetrics.density}
        clipToOutline=true
        settings.apply {
            javaScriptEnabled=true // The separate opaque-origin iframe runs explicitly requested web code.
            allowFileAccess=false;allowContentAccess=false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs=false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs=false
            blockNetworkLoads=true;blockNetworkImage=true
            domStorageEnabled=false;javaScriptCanOpenWindowsAutomatically=false
            setSupportMultipleWindows(false);setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture=true;cacheMode=WebSettings.LOAD_NO_CACHE
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
        removeJavascriptInterface("searchBoxJavaBridge_");removeJavascriptInterface("accessibility");removeJavascriptInterface("accessibilityTraversal")
        setDownloadListener {_,_,_,_,_-> /* Downloads require the native file button. */ }
        webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=request.url.toString() !in setOf("about:blank","about:srcdoc")
            @Deprecated("Legacy WebView callback")
            override fun shouldOverrideUrlLoading(view:WebView,url:String)=url !in setOf("about:blank","about:srcdoc")
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? = blocked(request.url)
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean {onRendererFailure();return true}
        }
        webChromeClient=object:WebChromeClient() {
            override fun onConsoleMessage(message:ConsoleMessage):Boolean {
                if(message.messageLevel()==ConsoleMessage.MessageLevel.ERROR) {
                    val detail="Line ${message.lineNumber()}: ${message.message().take(200)}"
                    // CSP/resource blocks are useful preview diagnostics, but are not
                    // JavaScript exceptions. Keep those distinct for runtime verification.
                    if(Regex("\\b(?:Syntax|Reference|Type|Range|Eval|URI)?Error:").containsMatchIn(message.message())) scriptError=detail
                    onScriptError(detail)
                }
                return true
            }
            override fun onPermissionRequest(request:PermissionRequest) {request.deny()}
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback) {callback.invoke(origin,false,false)}
            override fun onShowFileChooser(view:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {callback.onReceiveValue(null);return true}
            override fun onJsAlert(view:WebView,url:String,message:String,result:JsResult):Boolean {result.cancel();return true}
            override fun onJsConfirm(view:WebView,url:String,message:String,result:JsResult):Boolean {result.cancel();return true}
            override fun onJsPrompt(view:WebView,url:String,message:String,defaultValue:String?,result:JsPromptResult):Boolean {result.cancel();return true}
            override fun onJsBeforeUnload(view:WebView,url:String,message:String,result:JsResult):Boolean {result.cancel();return true}
        }
        if(Build.VERSION.SDK_INT>=29) setWebViewRenderProcessClient(object:WebViewRenderProcessClient() {
            override fun onRenderProcessUnresponsive(view:WebView,renderer:WebViewRenderProcess?) {renderer?.terminate()}
            override fun onRenderProcessResponsive(view:WebView,renderer:WebViewRenderProcess?) {}
        })
        contentDescription="Canvas preview"
    }
    fun showDocument(html:String) {scriptError=null;loadDataWithBaseURL(CanvasDocument.ORIGIN,html,"text/html","UTF-8",null)}
    companion object {
        fun blocked(uri:Uri):WebResourceResponse? = if(uri.scheme in setOf("data","blob","about")) null else
            WebResourceResponse("text/plain","UTF-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
    }
}

@Composable fun CanvasPreview(file:CodeArtifact,companions:List<CodeArtifact>,modifier:Modifier=Modifier,onFix:((String)->Unit)?=null) {
    val document=remember(file,companions) {CanvasDocument.html(file,companions)}
    var failed by remember(document) {mutableStateOf(false)}
    var scriptError by remember(document) {mutableStateOf<String?>(null)}
    if(failed) Box(modifier.padding(20.dp)) {Text("Preview stopped. Close and reopen it to run again.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
    else Box(modifier) {
        AndroidView(
        factory={context->SandboxWebView(context) {failed=true}.also {it.onScriptError={detail->scriptError=detail}}},
        modifier=Modifier.fillMaxSize().testTag("canvas-${file.name}"),
        onReset=null,
        onRelease={view->view.stopLoading();view.removeAllViews();view.destroy()},
        update={view->view.onScriptError={detail->scriptError=detail};view.onRendererFailure={failed=true};if(view.tag!=document) {view.tag=document;view.showDocument(document)}}
    )
        scriptError?.let {detail->Surface(color=MaterialTheme.colorScheme.errorContainer,contentColor=MaterialTheme.colorScheme.onErrorContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("Preview error · $detail\nOpen Code to edit, or ask Ember to correct the file.",style=MaterialTheme.typography.bodySmall)
                if(onFix!=null) TextButton(onClick={onFix(detail)}) {Text("Fix with Ember")}
            }
        }}
    }
}
