package dev.bonsai.pocket

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import com.github.luben.zstd.ZstdInputStream

class SpeechArchiveTest {
    @get:Rule val temp=TemporaryFolder()
    private fun tar(name:String,type:Char='0',size:Int=0):ByteArray {
        val header=ByteArray(512)
        fun put(at:Int,value:String) {value.toByteArray(Charsets.US_ASCII).copyInto(header,at)}
        put(0,name);put(124,size.toString(8).padStart(11,'0'));header[156]=type.code.toByte()
        for(i in 148..155) header[i]=32
        val sum=header.sumOf {it.toInt() and 255}
        put(148,sum.toString(8).padStart(6,'0'));header[154]=0;header[155]=32
        return ByteArrayOutputStream().apply {write(header);write(ByteArray((size+511)/512*512));write(ByteArray(1024))}.toByteArray()
    }
    @Test fun traversalLinksAndUnexpectedFilesNeverReachDisk() {
        listOf("../model.fermion" to '0',"config.json" to '2',"unexpected.txt" to '0').forEach {(name,type)->
            val dir=temp.newFolder()
            assertThrows(IllegalStateException::class.java) {SpeechAssets.unpack(ByteArrayInputStream(tar(name,type)),dir)}
            assertTrue(dir.listFiles()!!.isEmpty())
        }
    }
    @Test fun truncatedArchivesAndBadChecksumsAreRefused() {
        assertThrows(IllegalStateException::class.java) {SpeechAssets.unpack(ByteArrayInputStream(ByteArray(10)),temp.newFolder())}
        val bytes=tar("config.json");bytes[30]=99
        assertThrows(IllegalStateException::class.java) {SpeechAssets.unpack(ByteArrayInputStream(bytes),temp.newFolder())}
    }
    @Test fun cancellationStopsBeforeWritingAFile() {
        val dir=temp.newFolder()
        assertThrows(java.io.InterruptedIOException::class.java) {SpeechAssets.unpack(ByteArrayInputStream(tar("config.json")),dir) {true}}
        assertTrue(dir.listFiles()!!.isEmpty())
    }
    @Test fun publishedBundleExtractsToThePinnedModelWhenProvided() {
        val path=System.getProperty("ember.phononArchive","")
        org.junit.Assume.assumeTrue("Pass -PphononArchive to validate the published bundle",path.isNotBlank())
        val archive=File(path);assertEquals(SpeechAssets.ARCHIVE_HASH,SpeechAssets.hash(archive))
        val dir=temp.newFolder()
        ZstdInputStream(archive.inputStream().buffered()).use {SpeechAssets.unpack(it,dir)}
        SpeechAssets.verify(dir)
        assertEquals(SpeechAssets.files.keys,dir.listFiles()!!.map {it.name}.toSet())
    }
}
