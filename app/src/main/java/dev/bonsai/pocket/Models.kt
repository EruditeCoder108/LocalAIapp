package dev.bonsai.pocket

data class ModelSpec(
    val id:String,val label:String,val bytes:Long,val sha256:String,val size:String,val visionBytes:Long,val visionHash:String,
    val family:String="Bonsai 2",val badge:String="BONSAI",
    val filename:String="bonsai-2-$id.gguf",val visionFilename:String="bonsai-2-$id-vision.gguf",
    val officialFilename:String="Ternary-Bonsai-2-${label}-PQ2_0.gguf",
    val officialVisionFilename:String="Ternary-Bonsai-2-${label}-mmproj-Q8_0.gguf"
) {
    val displayName get()="$family · $label"
    val header get()="$badge / $label"
    val shortName get()=if(badge=="BONSAI") label else "$badge $label"
    fun downloadUrl(name:String):String {
        require(name==officialFilename || name==officialVisionFilename)
        val revision=if(id=="2b") "683f7be2629342a50987627b40881e54761a2430" else "8d6557168168f91ea6697a4a49ed2ef622b53adb"
        return "https://huggingface.co/prism-ml/Ternary-Bonsai-2-${label}-gguf/resolve/$revision/$name"
    }
}
object Models {
    val FOUR=ModelSpec("4b","4B",1146465120L,"d406b1e912c0d2a4c124ad10bf38a76ae662fdc4d24b176451403dc7b7f30409","1.15 GB",366894624L,"db939d5f9b7014137810205e91d05e5f54afd72b0b3af9d33bd7d82f7906a55d")
    val TWO=ModelSpec("2b","2B",517312992L,"d5f01a4ecaf49239653308ee732ad1a0c5f802c0ddb997549fc7b694c8b45928","517 MB",364664352L,"ac9934e8d7966d0d62442c202ec8fdad99a045dea79366c1a4a246a46f5c0533")
    val all=listOf(TWO,FOUR)
    fun find(id:String)=all.firstOrNull { it.id==id } ?: FOUR
}
