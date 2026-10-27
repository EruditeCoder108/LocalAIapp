package dev.bonsai.pocket

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@RunWith(AndroidJUnit4::class)
class OnDeviceTest {
    @Test fun longerReplySample() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val h=NativeBridge.load(File(ctx.filesDir,"models/bonsai-2-4b.gguf").absolutePath,4)
        val result=JSONObject()
        try {
            NativeBridge.prepare(h)
            val finish=NativeBridge.generate(h,arrayOf("system","user"),arrayOf("You are a helpful assistant. Give clear, direct answers.".toByteArray(),"Explain photosynthesis in simple words in about 100 words.".toByteArray()),192,object:TokenCallback {
                override fun onUpdate(data:ByteArray,count:Int,promptMs:Double,decodeMs:Double,dropped:Int,promptTokens:Int) {
                    result.put("text",data.toString(Charsets.UTF_8)).put("tokens",count).put("prompt_tokens",promptTokens).put("prompt_ms",promptMs).put("decode_ms_at_last_token",decodeMs).put("tokens_per_second",count/(decodeMs/1000).coerceAtLeast(0.001))
                }
            })
            result.put("finish",finish).put("threads",4)
            assertTrue(result.getString("text").contains("sunlight",true))
            assertTrue(result.getInt("tokens")>32)
            File(ctx.filesDir,"longer-reply-results.json").writeText(result.toString(2))
            Log.i("BonsaiDeviceTest",result.toString())
        } finally { NativeBridge.unload(h) }
    }
    @Test fun realBonsaiGenerationAndCancellation() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("App must work without network permission",PackageManager.PERMISSION_DENIED,ctx.checkSelfPermission(Manifest.permission.INTERNET))
        val file=File(ctx.filesDir,"models/bonsai-2-4b.gguf")
        assertEquals(BonsaiViewModel.MODEL_BYTES,file.length())
        val report=JSONObject(); val runs=JSONArray()
        val loadStart=System.nanoTime()
        var h=NativeBridge.load(file.absolutePath,4)
        assertTrue(h!=0L)
        report.put("load_ms",(System.nanoTime()-loadStart)/1e6)
        try {
            fun run(messages:List<Message>,cancel:Boolean=false):Pair<String,String> {
                var output=""; var tokens=0; var rate=0.0; var promptTime=0.0
                NativeBridge.prepare(h)
                val finish=NativeBridge.generate(h,messages.map { it.role }.toTypedArray(),messages.map { it.text.toByteArray(Charsets.UTF_8) }.toTypedArray(),128,object:TokenCallback {
                    override fun onUpdate(data:ByteArray,count:Int,promptMs:Double,decodeMs:Double,dropped:Int,promptTokens:Int) {
                        output=data.toString(Charsets.UTF_8); tokens=count; rate=count/(decodeMs/1000).coerceAtLeast(0.001); promptTime=promptMs
                        if(cancel && count>=3) NativeBridge.stop(h)
                    }
                })
                val item=JSONObject().put("prompt",messages.last().text).put("output",output).put("tokens",tokens).put("tokens_per_second",rate).put("prompt_ms",promptTime).put("finish",finish)
                runs.put(item); Log.i("BonsaiDeviceTest",item.toString())
                return output to finish
            }
            val system=Message("system","You are a helpful assistant. Give short, direct answers.")
            val first=run(listOf(system,Message("user","What is the capital of France? Reply in one short sentence.")))
            assertTrue("Expected coherent answer mentioning Paris: ${first.first}",first.first.contains("Paris",true))
            val recall=run(listOf(system,Message("user","My secret test word is mango. Remember it."),Message("assistant","I will remember mango."),Message("user","What was my secret test word? Reply with just the word.")))
            assertTrue("Multi-turn context lost: ${recall.first}",recall.first.contains("mango",true))
            val stopped=run(listOf(system,Message("user","Write a long story about a tree.")),true)
            assertEquals("stopped",stopped.second)
            try {
                NativeBridge.prepare(h)
                NativeBridge.generate(h,arrayOf("system","user"),arrayOf("You are helpful".toByteArray(),"hello ".repeat(5000).toByteArray()),512,object:TokenCallback { override fun onUpdate(data:ByteArray,count:Int,promptMs:Double,decodeMs:Double,dropped:Int,promptTokens:Int) {} })
                fail("Oversized prompt must be rejected")
            } catch(expected:IllegalStateException) { assertTrue(expected.message!!.contains("too long")) }
            report.put("context_overflow_rejected",true)
        } finally { NativeBridge.unload(h) }
        h=NativeBridge.load(file.absolutePath,4)
        assertTrue(h!=0L); NativeBridge.unload(h)
        report.put("reload_passed",true).put("no_internet_permission",true).put("runs",runs)
        File(ctx.filesDir,"device-test-results.json").writeText(report.toString(2))
    }
}
