package dev.bonsai.pocket

import android.content.Context
import com.github.luben.zstd.ZstdInputStream
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object SpeechAssets {
    const val REVISION="da200358d638886bc96c758c672ad5568828b119"
    const val REPO="FermionResearch/Phonon-2.5-English"
    const val ARCHIVE_BYTES=163693770L
    const val ARCHIVE_HASH="6bd8119ac5a637e5ece0666f5c1e43dbcd99e1c36b3d0ef3ec5f9b58260a9dd4"
    const val MODEL_HASH="c57119c0f04756932acc1d60d90506346e3708d69f00e654084acc7fe507fe27"
    data class Asset(val bytes:Long,val hash:String)
    val files=linkedMapOf(
        "model.fermion" to Asset(177479389,MODEL_HASH),
        "config.json" to Asset(277503,"9e5dfcc4e49a6c169d53540f07f4115e591007759b835132c849166b235dac95"),
        "packed_manifest.json" to Asset(507,"9584d9ad34be1fd5fa1347310fc8c37cd7ee185e10036b2b3615c9f989759ae0"))
    fun directory(context:Context)=File(context.filesDir,"speech/phonon-2.5-en")
    fun installed(context:Context):Boolean {
        val dir=directory(context)
        return runCatching {File(dir,"ready").let {it.isFile && it.readText()==MODEL_HASH} && files.all {(name,pin)->File(dir,name).length()==pin.bytes}}.getOrDefault(false)
    }
    fun hash(file:File,cancelled:()->Boolean = {false}):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use {input->val buffer=ByteArray(256*1024);while(true) {if(cancelled()) throw InterruptedIOException("Dictation setup paused");val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n)}}
        return digest.digest().joinToString("") {"%02x".format(it)}
    }
    fun verify(dir:File) {files.forEach {(name,pin)->val file=File(dir,name);if(file.length()!=pin.bytes || hash(file)!=pin.hash) {File(dir,"ready").delete();error("Dictation file verification failed. Download Phonon again.")}}}
    @Synchronized fun download(context:Context,task:AssetDownloader,onProgress:(Float,String)->Unit) {
        // A cancelled ViewModel may still be leaving blocking IO when its replacement starts.
        if(task.cancelled) throw InterruptedIOException("Dictation setup paused")
        val parent=File(context.filesDir,"speech").apply {mkdirs()}
        val archive=File(parent,"phonon-2.5-en.bps.tar.zst")
        task.download("https://huggingface.co/$REPO/resolve/$REVISION/${archive.name}",archive,ARCHIVE_BYTES,ARCHIVE_HASH) {onProgress(it.toFloat()/ARCHIVE_BYTES,"Downloading Phonon 2.5 English…")}
        val stage=File(parent,"phonon-2.5-en.installing").apply {mkdirs()}
        try {
            onProgress(1f,"Verifying and preparing dictation…")
            ZstdInputStream(archive.inputStream().buffered()).use {unpack(it,stage) {task.cancelled}}
            verify(stage)
            if(task.cancelled) throw InterruptedIOException("Dictation setup paused")
            val target=directory(context).apply {mkdirs()}
            files.keys.forEach {name->Files.move(File(stage,name).toPath(),File(target,name).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
            File(target,"ready.tmp").writeText(MODEL_HASH)
            Files.move(File(target,"ready.tmp").toPath(),File(target,"ready").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            archive.delete()
        } finally {files.keys.forEach {File(stage,it).delete()};stage.delete()}
    }
    /** Only the three pinned regular files are accepted; links and path traversal never reach disk. */
    fun unpack(input:InputStream,dir:File,cancelled:()->Boolean = {false}) {
        val seen=mutableSetOf<String>();val header=ByteArray(512)
        fun read(buffer:ByteArray,length:Int) {var at=0;while(at<length) {if(cancelled()) throw InterruptedIOException("Dictation setup paused");val n=input.read(buffer,at,length-at);check(n>0) {"Truncated dictation archive."};at+=n}}
        fun text(start:Int,count:Int)=header.copyOfRange(start,start+count).toString(Charsets.US_ASCII).substringBefore('\u0000').trim()
        fun octal(value:String)=value.trim().ifEmpty {"0"}.toLong(8)
        while(true) {
            read(header,512);if(header.all {it==0.toByte()}) break
            val checksum=octal(text(148,8));var sum=0L
            header.forEachIndexed {i,b->sum+=if(i in 148..155) 32 else b.toInt() and 255};check(sum==checksum) {"Invalid dictation archive header."}
            val path=(text(345,155).let {if(it.isBlank()) "" else "$it/"})+text(0,100)
            val size=octal(text(124,12));val type=header[156].toInt().toChar()
            if(type=='5') {check(size==0L && path.trimEnd('/')=="model_phonon25_en_pen2u1125_int6") {"Unexpected dictation archive directory."};continue}
            if(type=='x' || type=='g') {check(size in 0..32768);val skip=ByteArray(((size+511)/512*512).toInt());read(skip,skip.size);continue}
            check(type=='0' || type=='\u0000') {"Dictation archive contains an unsupported entry."}
            if(path=="bps_manifest.json") {check(size==799L);val metadata=ByteArray(1024);read(metadata,1024);continue}
            val name=path.removePrefix("model_phonon25_en_pen2u1125_int6/")
            val pin=files[name];check(pin!=null && (path==name || path=="model_phonon25_en_pen2u1125_int6/$name") && size==pin.bytes && seen.add(name)) {"Unexpected dictation archive file."}
            File(dir,name).outputStream().buffered().use {output->val buffer=ByteArray(64*1024);var left=size;while(left>0) {val n=minOf(left,buffer.size.toLong()).toInt();read(buffer,n);output.write(buffer,0,n);left-=n}}
            val padding=((512-size%512)%512).toInt();if(padding>0) read(ByteArray(padding),padding)
        }
        check(seen==files.keys) {"Dictation archive is incomplete."}
    }
}
