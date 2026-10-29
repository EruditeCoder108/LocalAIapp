package dev.bonsai.pocket

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/** One bounded, foreground recording. No background microphone or automatic send. */
class VoiceCapture(private val context:Context) {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private class Capture {
        @Volatile var stop=false
        @Volatile var discard=false
        @Volatile var recorder:AudioRecord?=null
    }
    private val current=AtomicReference<Capture?>()
    init {
        // Recover temporary recordings left by a process crash, before this owner records.
        File(context.cacheDir,"dictation").listFiles()?.filter {it.name.matches(Regex("[0-9a-f-]{36}\\.pcm"))}?.forEach {it.delete()}
    }
    fun start(onLevel:(Float,Float)->Unit,onFinished:(File)->Unit,onError:(String)->Unit) {
        check(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {"Microphone permission is required."}
        check(current.get()==null || current.get()?.discard==true) {"A recording is already active."}
        val session=Capture();current.set(session)
        worker.execute {
            val file=File(File(context.cacheDir,"dictation").apply {mkdirs()},"${UUID.randomUUID()}.pcm")
            var record:AudioRecord?=null
            try {
                val min=AudioRecord.getMinBufferSize(SpeechAudio.RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(min>0) {"This microphone cannot record at 16 kHz."}
                record=AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(SpeechAudio.RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(min*2,4096)).build()
                check(record.state==AudioRecord.STATE_INITIALIZED) {"Could not open the microphone."}
                session.recorder=record
                if(session.stop) {if(!session.discard) main.post {if(!session.discard) onError("The recording was too short. Try again.")};return@execute}
                record.startRecording();check(record.recordingState==AudioRecord.RECORDSTATE_RECORDING) {"The microphone is unavailable. Close another recording app."}
                val samples=ShortArray(512);val bytes=ByteArray(1024);var total=0;var smooth=0f
                file.outputStream().buffered().use {out->
                    while(!session.stop && total<SpeechAudio.MAX_SAMPLES) {
                        val count=record.read(samples,0,minOf(samples.size,SpeechAudio.MAX_SAMPLES-total),AudioRecord.READ_BLOCKING)
                        if(count<=0) {if(session.stop) break;error("The microphone stopped. Try recording again.")}
                        var energy=0.0
                        for(i in 0 until count) {val x=samples[i].toInt();bytes[2*i]=x.toByte();bytes[2*i+1]=(x shr 8).toByte();energy+=x.toDouble()*x}
                        out.write(bytes,0,count*2);total+=count
                        val rms=sqrt(energy/count)/32768.0
                        val level=((20*kotlin.math.log10(rms.coerceAtLeast(1e-6))+60)/45).toFloat().coerceIn(0f,1f)
                        smooth+= (level-smooth)*(if(level>smooth) .65f else .2f)
                        val value=smooth;val seconds=total.toFloat()/SpeechAudio.RATE
                        main.post {if(!session.discard) onLevel(value,seconds)}
                    }
                }
                if(!session.discard) {check(file.length()>=3200) {"The recording was too short. Try again."};main.post {if(!session.discard) onFinished(file) else file.delete()}}
            } catch(ex:Exception) {file.delete();if(!session.discard) main.post {if(!session.discard) onError(ex.message ?: "Could not record audio.")}}
            finally {runCatching {record?.stop()};record?.release();session.recorder=null;current.compareAndSet(session,null);if(session.discard || !file.exists() || file.length()==0L) file.delete()}
        }
    }
    fun finish() {current.get()?.stop=true}
    fun cancel() {current.get()?.let {it.discard=true;it.stop=true;runCatching {it.recorder?.stop()}}}
    fun close() {cancel();worker.shutdown()}
}
