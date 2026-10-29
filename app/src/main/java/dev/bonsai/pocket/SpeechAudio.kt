package dev.bonsai.pocket

import kotlin.math.*

/** Phonon/Parakeet's 16 kHz frontend; no microphone or Android dependency. */
object SpeechAudio {
    const val RATE=16000
    const val MAX_SAMPLES=RATE*30
    data class Features(val values:FloatArray,val frames:Int)
    private val window=FloatArray(512) {i->if(i in 56 until 456) (0.5-0.5*cos(2*PI*(i-56)/399)).toFloat() else 0f}
    private fun hzMel(h:Double)=if(h<1000) h*3/200 else 15+27*ln(h/1000)/ln(6.4)
    private fun melHz(m:Double)=if(m<15) m*200/3 else 1000*exp((m-15)*ln(6.4)/27)
    private val filters=Array(128) {m->
        val lo=melHz(hzMel(8000.0)*m/129);val mid=melHz(hzMel(8000.0)*(m+1)/129);val hi=melHz(hzMel(8000.0)*(m+2)/129)
        FloatArray(257) {k->val h=16000.0*k/512;(max(0.0,min((h-lo)/(mid-lo),(hi-h)/(hi-mid)))*2/(hi-lo)).toFloat()}
    }
    fun fromPcm16(bytes:ByteArray):FloatArray {
        require(bytes.size%2==0 && bytes.size<=MAX_SAMPLES*2) {"Invalid recording size."}
        return FloatArray(bytes.size/2) {i->((bytes[2*i].toInt() and 255) or (bytes[2*i+1].toInt() shl 8)).toShort()/32768f}
    }
    fun hasSignal(samples:FloatArray):Boolean {
        if(samples.size<1600) return false
        var sum=0.0;for(x in samples) {if(!x.isFinite()) return false;sum+=x*x}
        return sqrt(sum/samples.size)>0.0003
    }
    fun features(samples:FloatArray):Features {
        require(samples.size in 1120..MAX_SAMPLES && samples.all {it.isFinite()}) {"Recording must be between 0.1 and 30 seconds."}
        val frames=1+samples.size/160
        val padded=FloatArray(samples.size+512)
        padded[256]=samples[0]
        for(i in 1 until samples.size) padded[256+i]=samples[i]-0.97f*samples[i-1]
        val values=FloatArray(frames*128);val fft=FloatArray(1024);val power=FloatArray(257)
        for(f in 0 until frames) {
            for(k in 0 until 512) {fft[2*k]=padded[f*160+k]*window[k];fft[2*k+1]=0f}
            transform(fft)
            for(k in 0..256) power[k]=fft[2*k]*fft[2*k]+fft[2*k+1]*fft[2*k+1]
            for(m in 0 until 128) {var sum=0f;for(k in 0..256) sum+=filters[m][k]*power[k];values[f*128+m]=ln(sum+5.9604645e-8f)}
        }
        for(m in 0 until 128) {
            var mean=0.0;for(f in 0 until frames) mean+=values[f*128+m];mean/=frames
            var variance=0.0;for(f in 0 until frames) {val d=values[f*128+m]-mean;variance+=d*d}
            val std=sqrt(variance/(frames-1))+1e-5
            for(f in 0 until frames) values[f*128+m]=((values[f*128+m]-mean)/std).toFloat()
        }
        return Features(values,frames)
    }
    private fun transform(data:FloatArray) {
        var j=0
        for(i in 1 until 512) {var bit=256;while(j and bit!=0) {j=j xor bit;bit=bit shr 1};j=j xor bit
            if(i<j) {val r=data[2*i];val q=data[2*i+1];data[2*i]=data[2*j];data[2*i+1]=data[2*j+1];data[2*j]=r;data[2*j+1]=q}}
        var len=2
        while(len<=512) {val wr=cos(-2*PI/len).toFloat();val wi=sin(-2*PI/len).toFloat()
            var base=0;while(base<512) {var cr=1f;var ci=0f
                for(k in 0 until len/2) {val a=2*(base+k);val b=2*(base+k+len/2);val re=data[b]*cr-data[b+1]*ci;val im=data[b]*ci+data[b+1]*cr
                    data[b]=data[a]-re;data[b+1]=data[a+1]-im;data[a]+=re;data[a+1]+=im;val nr=cr*wr-ci*wi;ci=cr*wi+ci*wr;cr=nr}
                base+=len};len*=2}
    }
    fun text(ids:IntArray,vocabulary:List<String>):String = ids.asSequence().mapNotNull {id->vocabulary.getOrNull(id)}
        .filterNot {it in listOf("<unk>","<pad>") || (it.startsWith("<|") && it.endsWith("|>"))}
        .joinToString("").replace('▁',' ').trim()
    fun merge(draft:String,transcript:String):String?=(if(draft.isEmpty()) transcript else draft+(if(draft.last().isWhitespace()) "" else " ")+transcript).takeIf {it.length<=8000}
}
