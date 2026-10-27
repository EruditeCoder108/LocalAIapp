package dev.bonsai.pocket

import java.io.File
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** One explicit, foreground download. Partial files are never usable models. */
class AssetDownloader {
    private val stopped=AtomicBoolean(false)
    @Volatile private var connection:HttpURLConnection?=null
    val cancelled get()=stopped.get()
    fun cancel() {stopped.set(true);connection?.disconnect()}
    private fun checkActive() {if(cancelled) throw InterruptedIOException("Download paused")}
    fun download(url:String,destination:File,expectedBytes:Long,sha256:String,onProgress:(Long)->Unit) {
        checkActive()
        require(expectedBytes>0 && sha256.matches(Regex("[a-f0-9]{64}")))
        val address=URL(url)
        require(address.protocol=="https" || address.host in listOf("127.0.0.1","localhost"))
        destination.parentFile!!.mkdirs()
        fun valid(file:File):Boolean {
            if(file.length()!=expectedBytes) return false
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use {input->
                val buffer=ByteArray(1024*1024)
                while(true) {checkActive();val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n)}
            }
            return digest.digest().joinToString("") {"%02x".format(it)}==sha256
        }
        if(destination.isFile && valid(destination)) {onProgress(expectedBytes);return}
        val partial=File(destination.parentFile,destination.name+".part")
        if(partial.length()>expectedBytes) check(partial.delete()) {"Could not reset partial download"}
        var offset=partial.length()
        if(offset<expectedBytes) {
            val request=address.openConnection() as HttpURLConnection
            connection=request
            try {
                request.connectTimeout=15_000;request.readTimeout=20_000
                request.setRequestProperty("Accept-Encoding","identity")
                if(offset>0) request.setRequestProperty("Range","bytes=$offset-")
                checkActive()
                val code=request.responseCode
                check(code==200 || code==206) {"Download server returned HTTP $code. Try again later."}
                if(code==206) {
                    val range=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(request.getHeaderField("Content-Range").orEmpty())
                    check(range!=null && range.groupValues[1].toLong()==offset && range.groupValues[3].toLong()==expectedBytes) {"Unexpected partial response"}
                } else offset=0 // Server ignored Range: safely restart instead of appending.
                check(request.contentLengthLong<0 || request.contentLengthLong==expectedBytes-offset) {"Unexpected download size"}
                var copied=offset;var lastProgress=0L
                java.io.FileOutputStream(partial,offset>0).buffered().use {output->
                    request.inputStream.use {input->
                        val buffer=ByteArray(256*1024)
                        while(true) {
                            checkActive();val n=input.read(buffer);if(n<0) break
                            check(copied+n<=expectedBytes) {"Downloaded file is larger than expected"}
                            output.write(buffer,0,n);copied+=n
                            val now=System.nanoTime()
                            if(now-lastProgress>200_000_000 || copied==expectedBytes) {onProgress(copied);lastProgress=now}
                        }
                    }
                }
                check(copied==expectedBytes) {"Download interrupted. Tap Download again to resume."}
            } finally {request.disconnect();if(connection===request) connection=null}
        }
        checkActive()
        if(!valid(partial)) {partial.delete();error("Checksum did not match. Download again; the installed model was kept.")}
        checkActive()
        Files.move(partial.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        onProgress(expectedBytes)
    }
}
