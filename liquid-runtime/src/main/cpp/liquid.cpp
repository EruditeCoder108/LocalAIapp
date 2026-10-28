#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include "llama.h"
#include "chat.h"
#include "reasoning-budget.h"

using Clock = std::chrono::steady_clock;
namespace {
constexpr int CONTEXT = 4096;
struct Engine {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    common_chat_templates_ptr templates;
    std::atomic<bool> stop{false};
    ~Engine() { if(ctx) llama_free(ctx); if(model) llama_model_free(model); }
};
double ms(Clock::time_point start) { return std::chrono::duration<double,std::milli>(Clock::now()-start).count(); }
void fail(JNIEnv * env,const std::exception & ex) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),ex.what()); }
void log_message(ggml_log_level level,const char * text,void *) {
    if(level==GGML_LOG_LEVEL_CONT || level==GGML_LOG_LEVEL_DEBUG) return;
    __android_log_print(level==GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_DEBUG,"LiquidNative","%s",text);
}
std::string text(JNIEnv * env,jbyteArray bytes) {
    std::string result(env->GetArrayLength(bytes),'\0');
    env->GetByteArrayRegion(bytes,0,result.size(),reinterpret_cast<jbyte *>(result.data()));
    return result;
}
}

extern "C" JNIEXPORT jlong JNICALL Java_dev_bonsai_pocket_LiquidNativeBridge_load(JNIEnv * env,jobject,jstring path,jint threads) {
    try {
        llama_log_set(log_message,nullptr);
        llama_backend_init();
        auto e=std::make_unique<Engine>();
        auto params=llama_model_default_params(); params.n_gpu_layers=0;
        const char * filename=env->GetStringUTFChars(path,nullptr);
        e->model=llama_model_load_from_file(filename,params);
        env->ReleaseStringUTFChars(path,filename);
        if(!e->model) throw std::runtime_error("Could not open LFM3. Check the model file and available memory.");
        char architecture[32]{};
        llama_model_meta_val_str(e->model,"general.architecture",architecture,sizeof(architecture));
        if(std::string(architecture)!="lfm3") throw std::runtime_error("This file is not an LFM3 model.");
        auto context=llama_context_default_params();
        context.n_ctx=CONTEXT;context.n_batch=256;context.n_ubatch=128;context.n_seq_max=1;
        context.n_threads=context.n_threads_batch=std::clamp<int>(threads,1,8);
        context.flash_attn_type=LLAMA_FLASH_ATTN_TYPE_ENABLED;
        context.abort_callback=[](void * data) {return static_cast<Engine *>(data)->stop.load();};
        context.abort_callback_data=e.get();
        e->ctx=llama_init_from_model(e->model,context);
        if(!e->ctx) throw std::runtime_error("Not enough memory to start LFM3. Close another app and try again.");
        e->templates=common_chat_templates_init(e->model,"");
        return reinterpret_cast<jlong>(e.release());
    } catch(const std::exception & ex) {fail(env,ex);return 0;}
}
extern "C" JNIEXPORT void JNICALL Java_dev_bonsai_pocket_LiquidNativeBridge_unload(JNIEnv *,jobject,jlong handle) {delete reinterpret_cast<Engine *>(handle);}
extern "C" JNIEXPORT void JNICALL Java_dev_bonsai_pocket_LiquidNativeBridge_stop(JNIEnv *,jobject,jlong handle) {if(handle) reinterpret_cast<Engine *>(handle)->stop.store(true);}
extern "C" JNIEXPORT void JNICALL Java_dev_bonsai_pocket_LiquidNativeBridge_prepare(JNIEnv *,jobject,jlong handle) {if(handle) reinterpret_cast<Engine *>(handle)->stop.store(false);}

extern "C" JNIEXPORT jstring JNICALL Java_dev_bonsai_pocket_LiquidNativeBridge_generateNative(JNIEnv * env,jobject,jlong handle,jobjectArray roles,jobjectArray contents,jobjectArray images,jstring,jint maxTokens,jobject callback) {
    try {
        if(!handle) throw std::runtime_error("Load LFM3 before sending a message.");
        if(maxTokens<1 || maxTokens>2048) throw std::runtime_error("Unsupported response length.");
        const int reasoningBudget=maxTokens>=1024 ? 128 : 32;
        const int reasoningReserve=reasoningBudget+32; // tags and UTF-8 completion
        const auto size=env->GetArrayLength(roles);
        if(size!=env->GetArrayLength(contents) || size!=env->GetArrayLength(images)) throw std::runtime_error("Invalid conversation.");
        auto & e=*reinterpret_cast<Engine *>(handle);
        llama_memory_clear(llama_get_memory(e.ctx),true);
        common_chat_templates_inputs input;
        // LFM3 can emit an implicit reasoning span even with thinking disabled.
        // Use its native reasoning template and parser so only the answer is streamed.
        input.enable_thinking=true;
        input.reasoning_format=COMMON_REASONING_FORMAT_AUTO;
        input.chat_template_kwargs["enable_thinking"]="true";
        for(jsize i=0;i<size;++i) {
            auto image=static_cast<jstring>(env->GetObjectArrayElement(images,i));
            const bool hasImage=env->GetStringLength(image)>0;env->DeleteLocalRef(image);
            if(hasImage) throw std::runtime_error("LFM3-1.7B supports text only. Choose a model with image support.");
            auto role=static_cast<jstring>(env->GetObjectArrayElement(roles,i));
            auto content=static_cast<jbyteArray>(env->GetObjectArrayElement(contents,i));
            const char * roleText=env->GetStringUTFChars(role,nullptr);
            common_chat_msg message;message.role=roleText;message.content=text(env,content);
            input.messages.push_back(std::move(message));
            env->ReleaseStringUTFChars(role,roleText);env->DeleteLocalRef(role);env->DeleteLocalRef(content);
        }
        const auto * vocab=llama_model_get_vocab(e.model);
        auto started=Clock::now();
        std::vector<llama_token> tokens;
        std::unique_ptr<common_chat_session> session;
        int dropped=0;
        while(true) {
            session=std::make_unique<common_chat_session>(e.templates.get(),vocab,input);
            const auto prompt=session->prompt().str();
            const int n=-llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true);
            if(n<=0) throw std::runtime_error("Could not prepare this message.");
            if(n+maxTokens+reasoningReserve<=CONTEXT) {
                tokens.resize(n);llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),n,true,true);break;
            }
            if(input.messages.size()<=2) throw std::runtime_error("This message is too long. Try a shorter message or a new chat.");
            const auto end=std::min<size_t>(3,input.messages.size()-1);
            input.messages.erase(input.messages.begin()+1,input.messages.begin()+end);++dropped;
        }
        auto sampler=std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)>(llama_sampler_chain_init(llama_sampler_chain_default_params()),llama_sampler_free);
        auto tag=[&](const std::string & value) {
            const int n=-llama_tokenize(vocab,value.data(),value.size(),nullptr,0,false,true);
            if(n<=0) throw std::runtime_error("Could not prepare LFM3 reasoning control.");
            std::vector<llama_token> result(n);
            llama_tokenize(vocab,value.data(),value.size(),result.data(),n,false,true);
            return result;
        };
        const auto beginThinking=tag("<think>"), endThinking=tag("</think>");
        // The generation prompt already opens <think>. Bound that span before sampling
        // so a short response limit cannot be consumed entirely by hidden reasoning.
        llama_sampler_chain_add(sampler.get(),common_reasoning_budget_init(vocab,{beginThinking},{endThinking},endThinking,reasoningBudget,REASONING_BUDGET_COUNTING));
        // Liquid's general chat defaults, independent of the Bonsai sampling profile.
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_k(50));
        auto * penalties=llama_sampler_init_penalties(llama_vocab_n_tokens(vocab),64,1.1f,0.0f,0.0f);
        llama_sampler_chain_add(sampler.get(),penalties);
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_temp(0.1f));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        // Prefill only the repetition sampler; prompt tokens must not spend the budget.
        for(auto token:tokens) llama_sampler_accept(penalties,token);
        for(size_t offset=0;offset<tokens.size() && !e.stop;offset+=256) {
            auto batch=llama_batch_get_one(tokens.data()+offset,std::min<size_t>(256,tokens.size()-offset));
            if(llama_decode(e.ctx,batch)!=0 && !e.stop) throw std::runtime_error("LFM3 could not process this prompt. Try reloading the model.");
        }
        const double promptMs=ms(started);const auto decodeStart=Clock::now();
        auto callbackClass=env->GetObjectClass(callback);
        auto method=env->GetMethodID(callbackClass,"onUpdate","([BIDDII)V");env->DeleteLocalRef(callbackClass);
        int count=0, answerTokens=0;
        auto emit=[&](const std::string & content) {
            auto data=env->NewByteArray(content.size());
            env->SetByteArrayRegion(data,0,content.size(),reinterpret_cast<const jbyte *>(content.data()));
            env->CallVoidMethod(callback,method,data,count,promptMs,ms(decodeStart),dropped,static_cast<int>(tokens.size()));
            env->DeleteLocalRef(data);
        };
        for(;count<maxTokens+reasoningReserve && answerTokens<maxTokens && !e.stop;) {
            auto token=llama_sampler_sample(sampler.get(),e.ctx,-1);
            if(llama_vocab_is_eog(vocab,token)) break;
            char piece[512];const int n=llama_token_to_piece(vocab,token,piece,sizeof(piece),0,true);
            if(n<0) throw std::runtime_error("Unexpected LFM3 token size.");
            common_chat_input chunk;chunk.append(std::string(piece,n),token);++count;
            // Parse reasoning separately, including tags split across streaming tokens.
            const auto & answer=session->feed(chunk).content;
            if(!answer.empty()) ++answerTokens;
            emit(answer);
            if(env->ExceptionCheck()) return nullptr;
            auto batch=llama_batch_get_one(&token,1);
            if(llama_decode(e.ctx,batch)!=0 && !e.stop) throw std::runtime_error("LFM3 generation failed. Try a new chat.");
        }
        emit(session->finish().content);
        if(env->ExceptionCheck()) return nullptr;
        const std::string finish=e.stop ? "stopped" : (answerTokens>=maxTokens || count>=maxTokens+reasoningReserve) ? "length" : "complete";
        __android_log_print(ANDROID_LOG_INFO,"LiquidMetrics","tokens=%d prompt_tokens=%zu prompt_ms=%.0f decode_ms=%.0f dropped=%d finish=%s",count,tokens.size(),promptMs,ms(decodeStart),dropped,finish.c_str());
        return env->NewStringUTF(finish.c_str());
    } catch(const std::exception & ex) {fail(env,ex);return nullptr;}
}
