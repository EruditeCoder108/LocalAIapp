package dev.bonsai.pocket

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpeechAudioTest {
    private fun floats(name:String):FloatArray {
        val bytes=javaClass.getResourceAsStream("/speech/$name")!!.use {it.readBytes()}
        val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also {buffer.get(it)}
    }
    @Test fun frontendMatchesTheIndependentTorchImplementation() {
        val input=floats("input.f32");val expected=floats("expected.f32");val result=SpeechAudio.features(input)
        assertEquals(31,result.frames);assertEquals(expected.size,result.values.size)
        result.values.forEachIndexed {i,value->assertEquals("Feature $i",expected[i],value,.002f)}
    }
    @Test fun silentAndMalformedAudioDoesNotBecomeARequest() {
        assertFalse(SpeechAudio.hasSignal(FloatArray(16000)))
        assertFalse(SpeechAudio.hasSignal(floatArrayOf(Float.NaN)))
        assertThrows(IllegalArgumentException::class.java) {SpeechAudio.fromPcm16(ByteArray(3))}
        assertThrows(IllegalArgumentException::class.java) {SpeechAudio.features(FloatArray(SpeechAudio.MAX_SAMPLES+1))}
        assertTrue(SpeechAudio.features(FloatArray(1600)).values.all {it.isFinite()})
    }
    @Test fun pcmAndDetokenizationPreserveSignsAndSpecialTokenRules() {
        assertArrayEquals(floatArrayOf(-1f,32767f/32768f,0f),SpeechAudio.fromPcm16(byteArrayOf(0,-128,-1,127,0,0)),0f)
        val vocabulary=listOf("<unk>","<|nospeech|>","▁Hello",",","▁world","!")
        assertEquals("Hello, world!",SpeechAudio.text(intArrayOf(0,1,2,3,4,5,8192),vocabulary))
    }
    @Test fun insertingSpeechKeepsExistingWhitespaceAndNeverTruncatesTheDraft() {
        assertEquals("Existing\n\nSpoken",SpeechAudio.merge("Existing\n\n","Spoken"))
        assertEquals("Existing Spoken",SpeechAudio.merge("Existing","Spoken"))
        assertNull(SpeechAudio.merge("x".repeat(8000),"more"))
    }
}
