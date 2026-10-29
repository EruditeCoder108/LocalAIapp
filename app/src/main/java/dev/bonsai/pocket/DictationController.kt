package dev.bonsai.pocket

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.compose.runtime.*
import kotlinx.coroutines.*

enum class DictationPhase {IDLE,SETUP,DOWNLOADING,READY,PREPARING,LISTENING,TRANSCRIBING,RESULT,ERROR,CANCELLING}

class DictationController(
    private val app:Application,private val scope:CoroutineScope,
    private val beforeRecording:suspend ()->Boolean,private val restoreChat:(Boolean)->Unit
) {
    var phase by mutableStateOf(DictationPhase.IDLE);private set
    var assetsReady by mutableStateOf(SpeechAssets.installed(app));private set
    var progress by mutableFloatStateOf(0f);private set
    var level by mutableFloatStateOf(0f);private set
    var seconds by mutableFloatStateOf(0f);private set
    var status by mutableStateOf("");private set
    var transcript by mutableStateOf("");private set
    var permissionProblem by mutableStateOf(false);private set
    val busy get()=phase!=DictationPhase.IDLE
    val processing get()=phase in listOf(DictationPhase.DOWNLOADING,DictationPhase.PREPARING,DictationPhase.LISTENING,DictationPhase.TRANSCRIBING,DictationPhase.CANCELLING)
    private val main=Handler(Looper.getMainLooper())
    private val capture=VoiceCapture(app)
    private val client=DictationClient(app)
    private var task:AssetDownloader?=null
    private var serial=0
    private var restore=false
    private var closed=false
    fun open() {if(busy || closed) return;assetsReady=SpeechAssets.installed(app);status="";permissionProblem=false;phase=if(assetsReady) DictationPhase.READY else DictationPhase.SETUP}
    fun permissionDenied() {if(phase!=DictationPhase.READY || closed) return;permissionProblem=true;status="Microphone access was not granted. You can try again or allow it in Android app permissions.";phase=DictationPhase.ERROR}
    fun download() {
        if(phase !in listOf(DictationPhase.SETUP,DictationPhase.ERROR) || task!=null) return
        val id=++serial;val download=AssetDownloader();task=download;phase=DictationPhase.DOWNLOADING;progress=0f;status="Downloading Phonon 2.5 English…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    check(StatFs(app.filesDir.absolutePath).availableBytes>370_000_000L) {"Keep at least 370 MB free while setting up dictation."}
                    SpeechAssets.download(app,download) {value,message->main.post {if(id==serial) {progress=value;status=message}}}
                }
                if(id==serial) {assetsReady=true;phase=DictationPhase.READY;status=""}
            } catch(ex:Exception) {if(id==serial) {status=ex.message ?: "Dictation download failed.";phase=DictationPhase.ERROR}}
            finally {task=null;if(phase==DictationPhase.CANCELLING) phase=DictationPhase.IDLE}
        }
    }
    fun start() {
        if(phase!=DictationPhase.READY || closed) return
        val id=++serial;level=0f;seconds=0f;transcript="";permissionProblem=false;phase=DictationPhase.PREPARING;status="Making room for dictation…"
        scope.launch {
            try {
                val released=beforeRecording()
                restore=restore || released
                if(id!=serial || closed) {if(!closed && phase==DictationPhase.IDLE) finish();return@launch}
                phase=DictationPhase.LISTENING;status="Listening…"
                capture.start(onLevel={value,duration->if(id==serial && phase==DictationPhase.LISTENING) {level=value;seconds=duration}},onFinished=finished@{file->
                    if(id!=serial) {file.delete();return@finished}
                    phase=DictationPhase.TRANSCRIBING;level=0f;status="Preparing your recording…"
                    client.transcribe(file,onProgress={message->if(id==serial) status=message},onResult={text->if(id==serial) {transcript=text;phase=DictationPhase.RESULT}},onError={message->if(id==serial) fail(message)})
                },onError={message->if(id==serial) fail(message)})
            } catch(ex:Exception) {if(id==serial) fail(ex.message ?: "Could not start the microphone.")}
        }
    }
    private fun fail(message:String) {level=0f;status=message;assetsReady=SpeechAssets.installed(app);phase=DictationPhase.ERROR}
    fun retry() {if(assetsReady) {status="";phase=DictationPhase.READY} else download()}
    fun finishRecording() {if(phase==DictationPhase.LISTENING) {status="Finishing recording…";capture.finish()}}
    fun consume() {transcript="";finish()}
    fun cancel() {
        ++serial;capture.cancel();client.cancel();level=0f;transcript=""
        if(task!=null) {task?.cancel();phase=DictationPhase.CANCELLING;status="Pausing dictation setup…"} else finish()
    }
    private fun finish() {phase=DictationPhase.IDLE;val reload=restore;restore=false;if(!closed) restoreChat(reload)}
    fun close() {closed=true;++serial;task?.cancel();capture.close();client.cancel()}
}
