package dev.bonsai.pocket

import android.Manifest
import android.content.ComponentName
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SpeechDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val ctx get()=inst.targetContext
    @Before fun keepVisible() {compose.runOnIdle {compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}
    private fun pcm():ByteArray {
        val wave=inst.context.assets.open("phonon-speech-test.wav").use {it.readBytes()}
        val b=ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN);b.position(12)
        while(b.remaining()>=8) {
            val id=ByteArray(4).also {b.get(it)}.toString(Charsets.US_ASCII);val n=b.int;require(n>=0 && n<=b.remaining())
            val start=b.position()
            if(id=="fmt ") {assertEquals(1,b.short.toInt());assertEquals(1,b.short.toInt());assertEquals(16000,b.int)}
            if(id=="data") return ByteArray(n).also {b.get(it)}
            b.position(start+n+(n%2))
        };error("WAV fixture has no PCM data")
    }
    private fun recording()=File(File(ctx.cacheDir,"dictation").apply {mkdirs()},"${UUID.randomUUID()}.pcm").also {it.writeBytes(pcm())}
    private fun words(text:String)=Regex("[a-z]+").findAll(text.lowercase()).map {it.value}.toList()
    private fun wer(actual:String):Double {
        val ref=words("IF THE COUNT WERE ON BOARD A STRANGE FATALITY WAS BRINGING HIM TO THE PRESENCE OF HIS RIVAL")
        val hyp=words(actual);var row=IntArray(hyp.size+1) {it}
        ref.forEachIndexed {i,w->val next=IntArray(hyp.size+1);next[0]=i+1;hyp.forEachIndexed {j,h->next[j+1]=minOf(row[j+1]+1,next[j]+1,row[j]+if(w==h) 0 else 1)};row=next}
        return row.last().toDouble()/ref.size
    }
    @Test fun officialBundleAndIsolatedNativeTranscriptionWorkWithoutMicrophonePermission() {
        val permission=ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val info=ctx.packageManager.getServiceInfo(ComponentName(ctx,DictationService::class.java),0)
        assertEquals("${ctx.packageName}:dictation",info.processName);assertFalse(info.exported)
        val archive=File(ctx.filesDir,"speech/phonon-2.5-en.bps.tar.zst")
        assertTrue("Provision the pinned archive or model before this test",archive.isFile || SpeechAssets.installed(ctx))
        if(archive.exists()) SpeechAssets.download(ctx,AssetDownloader()) {_,_->}
        assertTrue(SpeechAssets.installed(ctx));SpeechAssets.verify(SpeechAssets.directory(ctx))
        val client=DictationClient(ctx);val file=recording();val done=CountDownLatch(1)
        var text="";var error:String?=null;var metrics=Bundle()
        try {
            inst.runOnMainSync {client.transcribe(file,onProgress={},onResult={text=it;done.countDown()},onError={error=it;done.countDown()},onMetrics={metrics=it})}
            assertTrue("Native speech timed out",done.await(150,TimeUnit.SECONDS));assertNull(error)
            assertNotEquals(android.os.Process.myPid(),metrics.getInt("pid"));assertTrue("Unexpected transcript: $text",wer(text)<=.25)
            assertFalse(file.exists());assertEquals(permission,ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            File(ctx.cacheDir,"phonon-device-results.json").writeText(JSONObject().put("text",text).put("fixture_wer",wer(text)).put("prepare_ms",metrics.getLong("prepare_ms")).put("decode_ms",metrics.getLong("decode_ms")).put("process_pss_kb",metrics.getLong("pss_kb")).put("idle_pss_kb",metrics.getLong("idle_pss_kb")).put("isolated_process",true).toString(2))
            val cancelled=recording();val decoding=CountDownLatch(1);var stale=false;var cancelRequested=false
            inst.runOnMainSync {client.transcribe(cancelled,onProgress={if(it.contains("Transcribing")) decoding.countDown()},onResult={if(cancelRequested) stale=true},onError={if(cancelRequested) stale=true})}
            assertTrue(decoding.await(60,TimeUnit.SECONDS))
            inst.runOnMainSync {cancelRequested=true;client.cancel()}
            SystemClock.sleep(300);assertFalse(stale);assertFalse(cancelled.exists())
        } finally {inst.runOnMainSync {client.cancel()};file.delete()}
    }
    @Test fun microphoneSheetKeepsDraftAndMatchesBothThemes() {
        val vm=ViewModelProvider(compose.activity)[BonsaiViewModel::class.java]
        compose.waitUntil(20_000) {vm.historyLoaded}
        val originalTheme=vm.theme;val permission=ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val old=compose.onNodeWithTag("messageInput").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        try {
            compose.onNodeWithTag("messageInput").performTextReplacement("Keep this draft")
            for(theme in listOf(1,2)) {
                compose.runOnIdle {vm.updateTheme(theme)}
                compose.onNodeWithContentDescription("Dictate message").assertIsEnabled()
                // Inspect the sheet without starting a live recording or changing permission.
                compose.runOnIdle {vm.dictation.open()}
                compose.onNodeWithText("PHONON 2.5 · ENGLISH").assertExists()
                compose.onNodeWithTag("voiceMatrix").assertExists()
                compose.onNodeWithText(if(SpeechAssets.installed(ctx)) "Start dictation" else "Download dictation model").assertExists()
                SystemClock.sleep(350)
                inst.uiAutomation.takeScreenshot().let {b->File(ctx.cacheDir,"phonon-sheet-$theme.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
                compose.onNodeWithText("Close").performClick()
                compose.onNodeWithTag("messageInput").assertTextContains("Keep this draft")
            }
            assertEquals(permission,ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        } finally {compose.runOnIdle {vm.dictation.cancel();vm.updateTheme(originalTheme)};compose.onNodeWithTag("messageInput").performTextReplacement(old)}
    }
}
