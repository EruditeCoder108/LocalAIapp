package dev.bonsai.pocket

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CanvasDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    @Before fun keepTestVisible() {compose.runOnIdle {compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}
    @Test fun generatedScriptErrorsAreVisibleAndThemeMatched() {
        val broken=CodeArtifact("error.html","<!doctype html><html><body><script>throw new Error('Ember QA script error')</script></body></html>","html")
        var reported=""
        compose.activity.setContent {EmberTheme(true) {CanvasPreview(broken,emptyList(),Modifier.fillMaxSize(),onFix={reported=it})}}
        compose.waitUntil(20_000) {runCatching {compose.onNodeWithText("Ember QA script error",substring=true).assertExists()}.isSuccess}
        compose.onNodeWithText("Open Code to edit",substring=true).assertExists()
        compose.onNodeWithText("Fix with Ember").performClick()
        assertTrue(reported.contains("Ember QA script error"))
    }
    private fun findWebView(view:View):SandboxWebView? {
        if(view is SandboxWebView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let {return it}
        return null
    }
    private fun js(view:SandboxWebView,script:String):String {
        val done=CountDownLatch(1);var value=""
        inst.runOnMainSync {view.evaluateJavascript(script) {value=it;done.countDown()}}
        assertTrue("WebView JavaScript timed out",done.await(10,TimeUnit.SECONDS));return value
    }
    private fun nodeWithText(node:AccessibilityNodeInfo?,value:String):AccessibilityNodeInfo? {
        if(node==null) return null
        if(node.text?.toString()==value) return node
        for(i in 0 until node.childCount) nodeWithText(node.getChild(i),value)?.let {return it}
        return null
    }
    @Test fun realInlinePreviewIsInteractiveIsolatedAndDownloadsExactSource() {
        val vm=ViewModelProvider(compose.activity)[BonsaiViewModel::class.java]
        compose.waitUntil(20_000) {vm.historyLoaded && vm.memoryReady}
        assertEquals("Ember",compose.activity.applicationInfo.loadLabel(compose.activity.packageManager).toString())
        compose.onNodeWithContentDescription("Chat tools").performClick()
        compose.onNodeWithTag("canvasToggle").performClick()
        compose.onNodeWithText("Canvas · inline web preview").assertExists()
        val file=CodeArtifact("ember-qa-${System.currentTimeMillis()}.html","<!doctype html><html><body><button id=\"b\" onclick=\"this.textContent='Clicked'\">Loading</button><script>document.getElementById('b').textContent='Ready';</script></body></html>","html")
        var saved=false
        compose.runOnUiThread {compose.activity.setContent {MaterialTheme {ArtifactCard(file,listOf(file),canvas=true,streaming=false,downloading=false,onDownload={vm.downloadArtifact(it) {saved=true}})}}}
        // Replacing the activity's content attaches its new Compose root on the next frame.
        compose.waitUntil(20_000) {runCatching {compose.onNodeWithTag("artifact-${file.name}").assertExists()}.isSuccess}
        var webView:SandboxWebView?=null
        compose.waitUntil(20_000) {compose.runOnUiThread {webView=findWebView(compose.activity.window.decorView)};webView!=null}
        val view=webView!!
        compose.waitUntil(20_000) {nodeWithText(inst.uiAutomation.rootInActiveWindow,"Ready")!=null}
        val bounds=Rect();nodeWithText(inst.uiAutomation.rootInActiveWindow,"Ready")!!.getBoundsInScreen(bounds)
        val location=IntArray(2);compose.runOnUiThread {view.getLocationOnScreen(location)}
        File(inst.targetContext.cacheDir,"canvas-bounds.txt").writeText("button=$bounds webView=${location.toList()} size=${view.width}x${view.height}")
        fun shot(name:String) {inst.uiAutomation.takeScreenshot().let {bitmap->File(inst.targetContext.cacheDir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
        shot("canvas-before.png")
        val time=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            try {assertTrue(inst.uiAutomation.injectInputEvent(event,true))} finally {event.recycle()}
            if(action==MotionEvent.ACTION_DOWN) SystemClock.sleep(100)
        }
        SystemClock.sleep(300);shot("canvas-after.png")
        compose.waitUntil(10_000) {nodeWithText(inst.uiAutomation.rootInActiveWindow,"Clicked")!=null}
        assertEquals("true",js(view,"(function(){try{document.querySelector('iframe').contentWindow.document;return false}catch(e){return e.name==='SecurityError'}})()"))
        assertEquals("true",js(view,"typeof Android==='undefined' && document.querySelector('iframe').sandbox.value==='allow-scripts'"))
        compose.runOnUiThread {
            assertFalse(view.settings.allowFileAccess);assertFalse(view.settings.allowContentAccess)
            assertTrue(view.settings.blockNetworkLoads);assertFalse(view.settings.domStorageEnabled)
            assertEquals(403,SandboxWebView.blocked(android.net.Uri.parse("https://example.com/"))!!.statusCode)
            assertEquals(403,SandboxWebView.blocked(android.net.Uri.parse("file:///data/user/0/private"))!!.statusCode)
        }
        if(Build.VERSION.SDK_INT>=29) {
            compose.onNodeWithContentDescription("Download ${file.name}").performClick()
            compose.waitUntil(20_000) {saved || vm.error!=null};assertNull(vm.error);assertTrue(saved)
            val resolver=inst.targetContext.contentResolver
            val uri=resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Downloads._ID),"${MediaStore.Downloads.DISPLAY_NAME}=?",arrayOf(file.name),null)!!.use {cursor->
                assertTrue(cursor.moveToFirst());ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cursor.getLong(0))
            }
            try {assertEquals(file.source,resolver.openInputStream(uri)!!.bufferedReader().use {it.readText()})}
            finally {resolver.delete(uri,null,null)} // Only the uniquely named file created by this test.
        }
        inst.uiAutomation.takeScreenshot().let {bitmap->File(inst.targetContext.cacheDir,"canvas-device.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
}
