package dev.bonsai.pocket

import android.app.Service
import android.app.ActivityManager
import android.content.Intent
import android.os.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

object PhononNative {
    init {System.loadLibrary("emberphonon")}
    external fun open(path:String,threads:Int):Long
    external fun transcribe(handle:Long,mel:FloatArray,frames:Int):IntArray
    external fun close(handle:Long)
}

/** Runs only in :dictation. Native OOM/abort and cancellation cannot destroy chat state. */
class DictationService:Service() {
    companion object {const val TRANSCRIBE=1;const val RESULT=2;const val ERROR=3;const val PROGRESS=4}
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var busy=false
    private val messenger=Messenger(Handler(Looper.getMainLooper()) {request->
        if(request.what!=TRANSCRIBE) return@Handler false
        val reply=request.replyTo ?: return@Handler true
        fun respond(type:Int,text:String,metrics:Bundle=Bundle()) {runCatching {reply.send(android.os.Message.obtain().apply {what=type;data=metrics.apply {putString("text",text)}})}}
        if(busy) {respond(ERROR,"Dictation is already processing a recording.");return@Handler true}
        busy=true
        val name=request.data.getString("pcm").orEmpty()
        worker.execute {
            var handle=0L
            val started=SystemClock.elapsedRealtime()
            try {
                check(Regex("[0-9a-f-]{36}\\.pcm").matches(name)) {"Invalid recording."}
                val audio=File(File(cacheDir,"dictation"),name)
                check(audio.isFile && audio.length() in 3200..SpeechAudio.MAX_SAMPLES*2L) {"Recording is empty or too long."}
                val samples=SpeechAudio.fromPcm16(audio.readBytes())
                check(SpeechAudio.hasSignal(samples)) {"No clear audio was captured. Try speaking closer to the microphone."}
                respond(PROGRESS,"Preparing Phonon 2.5 English…")
                val dir=SpeechAssets.directory(this)
                SpeechAssets.verify(dir)
                val config=JSONObject(File(dir,"config.json").readText())
                val vocab=config.getJSONObject("joint").getJSONArray("vocabulary")
                check(vocab.length()==8192 && config.getJSONObject("fermion").getString("model")=="Phonon-2.5 English") {"Wrong dictation model."}
                val memory=ActivityManager.MemoryInfo();getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
                check(!memory.lowMemory && memory.availMem>1_100_000_000L) {"Not enough free memory for dictation. Close another app and try again."}
                val features=SpeechAudio.features(samples)
                handle=PhononNative.open(File(dir,"model.fermion").absolutePath,4)
                check(handle!=0L) {"Could not start dictation."}
                respond(PROGRESS,"Transcribing on your phone…")
                val decodeStart=SystemClock.elapsedRealtime()
                val ids=PhononNative.transcribe(handle,features.values,features.frames)
                val metrics=Bundle().apply {putLong("prepare_ms",decodeStart-started);putLong("decode_ms",SystemClock.elapsedRealtime()-decodeStart);putLong("pss_kb",Debug.getPss());putInt("samples",samples.size);putInt("pid",android.os.Process.myPid())}
                val text=SpeechAudio.text(ids,List(vocab.length()) {vocab.getString(it)})
                check(text.isNotBlank()) {"No speech was recognized. Try again in a quieter place."}
                check(text.length<=8000) {"The transcript was unusually long. Try a shorter recording."}
                // Release the model before the main process restores its chat model.
                PhononNative.close(handle);handle=0L
                metrics.putLong("idle_pss_kb",Debug.getPss())
                busy=false
                respond(RESULT,text,metrics)
            } catch(ex:Throwable) {if(handle!=0L) {runCatching {PhononNative.close(handle)};handle=0L};busy=false;respond(ERROR,ex.message ?: "Dictation could not finish.")}
            finally {if(handle!=0L) runCatching {PhononNative.close(handle)};busy=false}
        }
        true
    })
    override fun onBind(intent:Intent)=messenger.binder
    override fun onUnbind(intent:Intent):Boolean {
        // Kernels have no interrupt API. This service owns its process; ending it
        // cancels inference and releases process-global scratch/thread pools.
        if(busy) android.os.Process.killProcess(android.os.Process.myPid()) else stopSelf()
        return false
    }
    override fun onDestroy() {worker.shutdown();super.onDestroy()}
}
