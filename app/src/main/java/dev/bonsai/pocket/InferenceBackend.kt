package dev.bonsai.pocket

interface InferenceBackend {
    fun load(path:String,threads:Int):Long
    fun unload(handle:Long)
    fun stop(handle:Long)
    fun prepare(handle:Long)
    fun generateNative(handle:Long,roles:Array<String>,contents:Array<ByteArray>,images:Array<String>,projector:String,maxTokens:Int,callback:TokenCallback):String
    fun generate(handle:Long,roles:Array<String>,contents:Array<ByteArray>,maxTokens:Int,callback:TokenCallback)=
        generateNative(handle,roles,contents,Array(roles.size) { "" },"",maxTokens,callback)
}

object LiquidNativeBridge : InferenceBackend {
    init { System.loadLibrary("liquid") }
    external override fun load(path:String,threads:Int):Long
    external override fun unload(handle:Long)
    external override fun stop(handle:Long)
    external override fun prepare(handle:Long)
    external override fun generateNative(handle:Long,roles:Array<String>,contents:Array<ByteArray>,images:Array<String>,projector:String,maxTokens:Int,callback:TokenCallback):String
}
