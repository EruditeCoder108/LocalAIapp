package dev.bonsai.pocket

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest

class AssetDownloaderTest {
    private val data=ByteArray(64*1024) {(it%251).toByte()}
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    private fun withServer(handler:(com.sun.net.httpserver.HttpExchange)->Unit,body:(String,File)->Unit) {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/model") {exchange->try {handler(exchange)}finally{exchange.close()}}
        val directory=Files.createTempDirectory("ember-download-test").toFile()
        server.start()
        try {body("http://127.0.0.1:${server.address.port}/model",File(directory,"model.gguf"))}
        finally {server.stop(0);directory.deleteRecursively()}
    }
    @Test fun resumesAnInterruptedTransferAndVerifiesBeforeActivation() {
        var calls=0;var range:String?=null
        withServer({exchange->
            calls++
            range=exchange.requestHeaders.getFirst("Range")
            if(calls==1) {exchange.sendResponseHeaders(200,data.size.toLong());exchange.responseBody.write(data,0,data.size/2)}
            else {val offset=range!!.removePrefix("bytes=").removeSuffix("-").toInt();exchange.responseHeaders.add("Content-Range","bytes $offset-${data.lastIndex}/${data.size}");exchange.sendResponseHeaders(206,(data.size-offset).toLong());exchange.responseBody.write(data,offset,data.size-offset)}
        }) {url,target->
            try {AssetDownloader().download(url,target,data.size.toLong(),hash(data)) {};fail("Expected interrupted transfer")} catch(_:Exception) {}
            assertFalse(target.exists())
            assertTrue(File(target.path+".part").length()>0)
            AssetDownloader().download(url,target,data.size.toLong(),hash(data)) {}
            assertNotNull(range);assertArrayEquals(data,target.readBytes());assertFalse(File(target.path+".part").exists())
        }
    }
    @Test fun restartsSafelyWhenServerIgnoresRange() {
        withServer({exchange->exchange.sendResponseHeaders(200,data.size.toLong());exchange.responseBody.write(data)}) {url,target->
            File(target.path+".part").writeBytes(ByteArray(77) {42})
            AssetDownloader().download(url,target,data.size.toLong(),hash(data)) {}
            assertArrayEquals(data,target.readBytes())
        }
    }
    @Test fun badChecksumKeepsPreviouslyInstalledFile() {
        withServer({exchange->exchange.sendResponseHeaders(200,data.size.toLong());exchange.responseBody.write(data)}) {url,target->
            val original="existing model".toByteArray();target.writeBytes(original)
            try {AssetDownloader().download(url,target,data.size.toLong(),"0".repeat(64)) {};fail("Expected checksum rejection")}catch(_:IllegalStateException) {}
            assertArrayEquals(original,target.readBytes());assertFalse(File(target.path+".part").exists())
        }
    }
    @Test fun cancellationBeforeNetworkLeavesInstalledFileUntouched() {
        val directory=Files.createTempDirectory("ember-cancel-test").toFile()
        try {val target=File(directory,"model.gguf");target.writeText("keep");val task=AssetDownloader();task.cancel()
            try {task.download("https://example.invalid/model",target,data.size.toLong(),hash(data)) {};fail("Expected cancellation")}catch(_:java.io.InterruptedIOException) {}
            assertEquals("keep",target.readText())
        }finally {directory.deleteRecursively()}
    }
}
