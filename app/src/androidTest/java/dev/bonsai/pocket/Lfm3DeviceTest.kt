package dev.bonsai.pocket

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises both native libraries without reading or modifying the user's conversations. */
@RunWith(AndroidJUnit4::class)
class Lfm3DeviceTest {
    @Test fun generationCancellationAndCrossRuntimeReload() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val report=JSONObject();val samples=JSONArray()
        val model=File(ctx.filesDir,"models/${Models.LFM3.filename}")
        assertEquals(Models.LFM3.bytes,model.length())
        fun sample(backend:InferenceBackend,handle:Long,prompt:String,history:List<Message> = emptyList(),cancel:Boolean=false):String {
            var output="";var tokenCount=0;var prefillMs=0.0;var generationMs=0.0
            backend.prepare(handle)
            val messages=listOf(Message("system","You are a helpful assistant. Give short, direct answers."))+history+Message("user",prompt)
            val finish=backend.generate(handle,messages.map {it.role}.toTypedArray(),messages.map {it.text.toByteArray()}.toTypedArray(),192,object:TokenCallback {
                override fun onUpdate(data:ByteArray,count:Int,promptMs:Double,decodeMs:Double,dropped:Int,promptTokens:Int) {
                    output=data.toString(Charsets.UTF_8)
                    // Cancellation must work even before visible answer text arrives.
                    if(cancel && count>=3) backend.stop(handle)
                    tokenCount=count;prefillMs=promptMs;generationMs=decodeMs
                }
            })
            samples.put(JSONObject().put("prompt",prompt).put("answer",output).put("finish",finish).put("tokens",tokenCount)
                .put("prompt_ms",prefillMs).put("decode_ms",generationMs).put("decode_tok_s",(tokenCount-1)/(generationMs/1000).coerceAtLeast(.001)).put("process_pss_kb",Debug.getPss()))
            report.put("samples",samples)
            File(ctx.cacheDir,"lfm3-device-results.json").writeText(report.toString(2))
            assertFalse("Reasoning tags leaked: $output",output.contains("<think>") || output.contains("</think>"))
            if(cancel) assertEquals("stopped",finish) else assertEquals("complete",finish)
            return output
        }
        val started=System.nanoTime()
        var handle=LiquidNativeBridge.load(model.absolutePath,4)
        report.put("load_ms",(System.nanoTime()-started)/1e6)
        try {
            assertTrue(sample(LiquidNativeBridge,handle,"What is the capital of France? Answer in one short sentence.").contains("Paris",true))
            assertTrue(sample(LiquidNativeBridge,handle,"What was my test word? Reply with just the word.",listOf(Message("user","My test word is mango."),Message("assistant","Your test word is mango."))).contains("mango",true))
            assertTrue(sample(LiquidNativeBridge,handle,"Compute 17 times 6. Answer with only the number.").contains("102"))
            sample(LiquidNativeBridge,handle,"Write a long story about a tree.",cancel=true)
            assertTrue(sample(LiquidNativeBridge,handle,"What is 2 plus 2? Answer briefly.").contains("4"))
            LiquidNativeBridge.prepare(handle)
            assertThrows(IllegalStateException::class.java) {
                LiquidNativeBridge.generate(handle,arrayOf("system","user"),arrayOf("Be helpful".toByteArray(),"hello ".repeat(6000).toByteArray()),512,noUpdates)
            }
            assertThrows(IllegalStateException::class.java) {
                LiquidNativeBridge.generateNative(handle,arrayOf("user"),arrayOf("Describe this image".toByteArray()),arrayOf("image.jpg"),"",128,noUpdates)
            }
        } finally {LiquidNativeBridge.unload(handle)}
        // Load Bonsai in the same process after LFM3: checks symbol and recurrent-state isolation.
        val bonsai=File(ctx.filesDir,"models/${Models.TWO.filename}")
        if(bonsai.exists()) {
            handle=NativeBridge.load(bonsai.absolutePath,4)
            try {assertTrue(sample(NativeBridge,handle,"What is the capital of France? Answer briefly.").contains("Paris",true))}
            finally {NativeBridge.unload(handle)}
            report.put("bonsai_after_liquid_passed",true)
        }
        handle=LiquidNativeBridge.load(model.absolutePath,4)
        try {assertTrue(sample(LiquidNativeBridge,handle,"What is the capital of France? Answer briefly.").contains("Paris",true))}
        finally {LiquidNativeBridge.unload(handle)}
        report.put("liquid_reload_passed",true).put("samples",samples)
        File(ctx.cacheDir,"lfm3-device-results.json").writeText(report.toString(2))
    }
    private val noUpdates=object:TokenCallback {override fun onUpdate(data:ByteArray,count:Int,promptMs:Double,decodeMs:Double,dropped:Int,promptTokens:Int) {}}
}
