package dev.bonsai.pocket

data class ModelSpec(
    val id:String,val label:String,val bytes:Long,val sha256:String,val size:String,val visionBytes:Long=0L,val visionHash:String="",
    val family:String="Bonsai 2",val badge:String="BONSAI",
    val filename:String="bonsai-2-$id.gguf",val visionFilename:String="bonsai-2-$id-vision.gguf",
    val officialFilename:String="Ternary-Bonsai-2-${label}-PQ2_0.gguf",
    val officialVisionFilename:String="Ternary-Bonsai-2-${label}-mmproj-Q8_0.gguf",
    val repository:String="prism-ml/Ternary-Bonsai-2-${label}-gguf",val revision:String,
    val publisher:String="PrismML",val liquidRuntime:Boolean=false
) {
    val displayName get()="$family · $label"
    val header get()="$badge / $label"
    val shortName get()=if(badge=="BONSAI") label else "$badge $label"
    val supportsVision get()=visionBytes>0 && visionHash.isNotEmpty()
    fun downloadUrl(name:String):String {
        require(name==officialFilename || (supportsVision && name==officialVisionFilename))
        return "https://huggingface.co/$repository/resolve/$revision/$name"
    }
}
object Models {
    val FOUR=ModelSpec("4b","4B",1146465120L,"d406b1e912c0d2a4c124ad10bf38a76ae662fdc4d24b176451403dc7b7f30409","1.15 GB",366894624L,"db939d5f9b7014137810205e91d05e5f54afd72b0b3af9d33bd7d82f7906a55d",revision="8d6557168168f91ea6697a4a49ed2ef622b53adb")
    val TWO=ModelSpec("2b","2B",517312992L,"d5f01a4ecaf49239653308ee732ad1a0c5f802c0ddb997549fc7b694c8b45928","517 MB",364664352L,"ac9934e8d7966d0d62442c202ec8fdad99a045dea79366c1a4a246a46f5c0533",revision="683f7be2629342a50987627b40881e54761a2430")
    // Retain the optional model supported by the earlier personal phone build.
    val HERETIC_TWO=ModelSpec("heretic-2b","2B",1312164832L,"ff31f688573119093db583a75eafc84b15c9cc2a20ab5a31b981e106efd8c9f1","1.31 GB",668227200L,"6becc759f69ea6777d9f73a26954e01dacff70ca4ce78803114c08db66371246",
        family="Qwen3.5 Heretic",badge="HERETIC",filename="qwen3.5-2b-heretic-q4_k_m.gguf",visionFilename="qwen3.5-2b-heretic-vision-f16.gguf",
        officialFilename="Qwen3.5-2B-Heretic-Q4_K_M.gguf",officialVisionFilename="Qwen3.5-2B-Heretic-mmproj-F16.gguf",
        repository="darrellbest/Qwen3.5-2B-Heretic-GGUF",revision="85bf5e6e623d71d712f978c8a8cab220fa6f31c2",publisher="darrellbest")
    val LFM3=ModelSpec("lfm3-1.7b","1.7B",1049615680L,"43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452","1.05 GB",
        family="Liquid LFM3",badge="LFM3",filename="lfm3-1.7b-qad-q4_0.gguf",visionFilename="",officialFilename="LFM3-1.7B-QAD-Q4_0.gguf",officialVisionFilename="",
        repository="LiquidAI/LFM3-1.7B-GGUF",revision="b3f2ae5d73cd3c91bf40e8ba752c648aa05dffdf",publisher="Liquid AI",liquidRuntime=true)
    val all=listOf(TWO,FOUR,LFM3,HERETIC_TWO)
    fun find(id:String)=all.firstOrNull { it.id==id } ?: FOUR
}
