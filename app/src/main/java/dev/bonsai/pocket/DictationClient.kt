package dev.bonsai.pocket

import android.content.*
import android.os.*
import java.io.File

class DictationClient(private val context:Context) {
    private var connection:ServiceConnection?=null
    private var timeout:Runnable?=null
    private val main=Handler(Looper.getMainLooper())
    private var recording:File?=null
    private var epoch=0
    fun transcribe(file:File,onProgress:(String)->Unit,onResult:(String)->Unit,onError:(String)->Unit,onMetrics:(Bundle)->Unit = {}) {
        cancel();recording=file
        val id=epoch
        var finished=false
        fun finish(text:String,success:Boolean) {if(finished || id!=epoch) return;finished=true;cancel();if(success) onResult(text) else onError(text)}
        val incoming=Messenger(Handler(Looper.getMainLooper()) {message->
            if(id!=epoch) return@Handler true
            when(message.what) {
                DictationService.PROGRESS->if(!finished) onProgress(message.data.getString("text").orEmpty())
                DictationService.RESULT->{onMetrics(message.data);finish(message.data.getString("text").orEmpty(),true)}
                DictationService.ERROR->finish(message.data.getString("text") ?: "Dictation failed.",false)
            };true
        })
        val link=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                if(connection!==this) return
                try {Messenger(binder).send(android.os.Message.obtain().apply {what=DictationService.TRANSCRIBE;replyTo=incoming;data=Bundle().apply {putString("pcm",file.name)}})}
                catch(ex:Exception) {finish("Could not start the dictation engine. Try again.",false)}
            }
            override fun onServiceDisconnected(name:ComponentName) {finish("The dictation engine stopped. Your draft is unchanged. Close another app and try again.",false)}
            override fun onBindingDied(name:ComponentName) {onServiceDisconnected(name)}
            override fun onNullBinding(name:ComponentName) {onServiceDisconnected(name)}
        }
        connection=link
        try {check(context.bindService(Intent(context,DictationService::class.java),link,Context.BIND_AUTO_CREATE)) {"Could not bind dictation service."}}
        catch(ex:Exception) {finish("Could not start dictation. Try again.",false);return}
        timeout=Runnable {finish("Dictation took too long. Try a shorter recording.",false)}.also {main.postDelayed(it,120_000)}
    }
    fun cancel() {++epoch;timeout?.let {main.removeCallbacks(it)};timeout=null;connection?.let {runCatching {context.unbindService(it)}};connection=null;recording?.delete();recording=null}
}
