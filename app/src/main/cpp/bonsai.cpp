#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <algorithm>
#include <chrono>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include "llama.h"
#include "chat.h"
#include "mtmd.h"
#include "mtmd-helper.h"

using Clock = std::chrono::steady_clock;
struct Engine {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    common_chat_templates_ptr templates;
    std::atomic<bool> stop{false};
    int threads = 4;
    ~Engine() { if (ctx) llama_free(ctx); if (model) llama_model_free(model); }
};
static double ms(Clock::time_point start) { return std::chrono::duration<double, std::milli>(Clock::now()-start).count(); }
static void fail(JNIEnv * env, const std::exception & e) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what()); }
static std::string bytes(JNIEnv * env, jbyteArray data) {
    std::string out(env->GetArrayLength(data), '\0');
    env->GetByteArrayRegion(data, 0, out.size(), reinterpret_cast<jbyte *>(out.data()));
    return out;
}
static void log_message(ggml_log_level level, const char * text, void *) {
    if(level==GGML_LOG_LEVEL_CONT || level==GGML_LOG_LEVEL_DEBUG) return;
    __android_log_print(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_DEBUG, "BonsaiNative", "%s", text);
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_bonsai_pocket_NativeBridge_load(JNIEnv * env, jobject, jstring path, jint threads) {
    try {
        llama_log_set(log_message, nullptr);
        llama_backend_init();
        auto e = std::make_unique<Engine>();
        e->threads = threads;
        const char * filename = env->GetStringUTFChars(path, nullptr);
        auto p = llama_model_default_params(); p.n_gpu_layers = 0;
        e->model = llama_model_load_from_file(filename, p);
        env->ReleaseStringUTFChars(path, filename);
        if (!e->model) throw std::runtime_error("Could not open the selected model. Check the model file and available memory.");
        auto c = llama_context_default_params();
        c.n_ctx = 4096; c.n_batch = 256; c.n_ubatch = 128;
        c.n_threads = threads; c.n_threads_batch = threads;
        c.n_seq_max = 1; c.no_perf = false;
        c.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
        c.abort_callback = [](void * data) { return static_cast<Engine *>(data)->stop.load(); };
        c.abort_callback_data = e.get();
        e->ctx = llama_init_from_model(e->model, c);
        if (!e->ctx) throw std::runtime_error("Not enough memory to start the model. Close another app and try again.");
        e->templates = common_chat_templates_init(e->model, "");
        return reinterpret_cast<jlong>(e.release());
    } catch (const std::exception & e) { fail(env, e); return 0; }
}
extern "C" JNIEXPORT void JNICALL Java_dev_bonsai_pocket_NativeBridge_stop(JNIEnv *, jobject, jlong handle) {
    if (handle) reinterpret_cast<Engine *>(handle)->stop.store(true);
}
extern "C" JNIEXPORT void JNICALL Java_dev_bonsai_pocket_NativeBridge_prepare(JNIEnv *, jobject, jlong handle) {
    if (handle) reinterpret_cast<Engine *>(handle)->stop.store(false);
}
extern "C" JNIEXPORT void JNICALL Java_dev_bonsai_pocket_NativeBridge_unload(JNIEnv *, jobject, jlong handle) { delete reinterpret_cast<Engine *>(handle); }
extern "C" JNIEXPORT jstring JNICALL Java_dev_bonsai_pocket_NativeBridge_generateNative(JNIEnv * env, jobject, jlong handle, jobjectArray roles, jobjectArray contents, jobjectArray imagePaths, jstring projector, jint maxTokens, jobject callback) {
    try {
        auto & e = *reinterpret_cast<Engine *>(handle);
        llama_memory_clear(llama_get_memory(e.ctx), true);
        common_chat_templates_inputs input;
        input.enable_thinking = false;
        input.chat_template_kwargs["enable_thinking"] = "false";
        std::vector<std::string> paths;
        bool hasImages = false;
        for (int i=0; i<env->GetArrayLength(roles); ++i) {
            auto r = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
            auto b = static_cast<jbyteArray>(env->GetObjectArrayElement(contents, i));
            const char * role = env->GetStringUTFChars(r, nullptr);
            common_chat_msg msg; msg.role = role; msg.content = bytes(env,b); input.messages.push_back(msg);
            auto img = static_cast<jstring>(env->GetObjectArrayElement(imagePaths,i));
            const char * imgPath = env->GetStringUTFChars(img,nullptr);
            paths.emplace_back(imgPath); hasImages |= !paths.back().empty();
            env->ReleaseStringUTFChars(img,imgPath); env->DeleteLocalRef(img);
            env->ReleaseStringUTFChars(r,role); env->DeleteLocalRef(r); env->DeleteLocalRef(b);
        }
        auto vocab = llama_model_get_vocab(e.model);
        auto start = Clock::now();
        std::unique_ptr<mtmd_context,decltype(&mtmd_free)> vision(nullptr,mtmd_free);
        std::unique_ptr<mtmd_input_chunks,decltype(&mtmd_input_chunks_free)> chunks(nullptr,mtmd_input_chunks_free);
        if (hasImages) {
            mtmd_helper_log_set(log_message,nullptr);
            auto vp = mtmd_context_params_default();
            vp.use_gpu=false; vp.n_threads=e.threads; vp.warmup=false;
            vp.image_min_tokens=64; vp.image_max_tokens=256;
            vp.progress_callback=[](float,void * data) { return !static_cast<Engine *>(data)->stop.load(); };
            vp.progress_callback_user_data=&e;
            const char * projectPath=env->GetStringUTFChars(projector,nullptr);
            vision.reset(mtmd_init_from_file(projectPath,e.model,vp));
            env->ReleaseStringUTFChars(projector,projectPath);
            if (!vision) {
                if (e.stop) return env->NewStringUTF("stopped");
                throw std::runtime_error("Could not load the image model. Check its installation and available memory.");
            }
            for (size_t i=0;i<paths.size();++i) if (!paths[i].empty()) input.messages[i].content=std::string(mtmd_default_marker())+"\n"+input.messages[i].content;
        }
        std::vector<llama_token> tokens;
        size_t promptTokens=0;
        int dropped = 0;
        while (true) {
            auto prompt = common_chat_templates_apply(e.templates.get(),input).prompt;
            int n;
            if (vision) {
                std::vector<std::unique_ptr<mtmd_bitmap,decltype(&mtmd_bitmap_free)>> owned;
                std::vector<const mtmd_bitmap *> bitmaps;
                for (const auto & path:paths) if (!path.empty()) {
                    auto bitmap=mtmd_helper_bitmap_init_from_file(vision.get(),path.c_str(),false).bitmap;
                    if (!bitmap) throw std::runtime_error("An image in this conversation could not be opened.");
                    owned.emplace_back(bitmap,mtmd_bitmap_free); bitmaps.push_back(bitmap);
                }
                chunks.reset(mtmd_input_chunks_init());
                mtmd_input_text text{prompt.data(),prompt.size(),true,true};
                if (mtmd_tokenize(vision.get(),chunks.get(),&text,bitmaps.data(),bitmaps.size())!=0) throw std::runtime_error("Could not prepare this image. Try another photo or a new chat.");
                n=static_cast<int>(mtmd_helper_get_n_tokens(chunks.get()));
            } else n = -llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true);
            if (n <= 0) throw std::runtime_error("Could not tokenize this message.");
            if (n + maxTokens <= 4096) {
                promptTokens=n;
                if (!vision) { tokens.resize(n); llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),n,true,true); }
                break;
            }
            if (input.messages.size() <= 2) throw std::runtime_error("This message is too long. Try a shorter message or a new chat.");
            // Remove complete old turns; retain the system message and latest user message.
            auto end=std::min<size_t>(3,input.messages.size()-1);
            input.messages.erase(input.messages.begin()+1, input.messages.begin()+end);
            paths.erase(paths.begin()+1,paths.begin()+end);
            dropped++;
        }
        auto s = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>(llama_sampler_chain_init(llama_sampler_chain_default_params()),llama_sampler_free);
        llama_sampler_chain_add(s.get(),llama_sampler_init_penalties(llama_vocab_n_tokens(vocab),64,1.0f,0.0f,1.5f));
        llama_sampler_chain_add(s.get(),llama_sampler_init_top_k(20));
        llama_sampler_chain_add(s.get(),llama_sampler_init_top_p(0.8f,1));
        llama_sampler_chain_add(s.get(),llama_sampler_init_temp(0.7f));
        llama_sampler_chain_add(s.get(),llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        for (auto t: tokens) llama_sampler_accept(s.get(),t);
        if (vision) {
            llama_pos past=0;
            auto count=mtmd_input_chunks_size(chunks.get());
            for (size_t i=0;i<count && !e.stop;++i) {
                auto chunk=mtmd_input_chunks_get(chunks.get(),i);
                if (mtmd_helper_eval_chunk_single(vision.get(),e.ctx,chunk,past,0,256,i==count-1,&past)!=0 && !e.stop) throw std::runtime_error("Image processing failed. Try a smaller image or a new chat.");
                if (mtmd_input_chunk_get_type(chunk)==MTMD_INPUT_CHUNK_TYPE_TEXT) {
                    size_t n=0; auto textTokens=mtmd_input_chunk_get_tokens_text(chunk,&n);
                    for(size_t j=0;j<n;++j) llama_sampler_accept(s.get(),textTokens[j]);
                }
            }
            // Image embeddings are now in the language context; release the vision tower.
            chunks.reset(); vision.reset();
        } else {
            for (size_t offset=0; offset<tokens.size() && !e.stop; offset+=256) {
                auto batch = llama_batch_get_one(tokens.data()+offset,std::min<size_t>(256,tokens.size()-offset));
                if (llama_decode(e.ctx,batch) != 0 && !e.stop) throw std::runtime_error("Prompt processing failed. Try unloading and reloading the model.");
            }
        }
        double promptMs = ms(start);
        auto decodeStart = Clock::now();
        auto method = env->GetMethodID(env->GetObjectClass(callback),"onUpdate","([BIDDII)V");
        std::string output;
        int count=0;
        for (;count<maxTokens && !e.stop; ) {
            auto t = llama_sampler_sample(s.get(),e.ctx,-1);
            if (llama_vocab_is_eog(vocab,t)) break;
            char piece[512]; int n = llama_token_to_piece(vocab,t,piece,sizeof(piece),0,false);
            if (n < 0) throw std::runtime_error("Unexpected token size.");
            output.append(piece,n); ++count;
            auto data = env->NewByteArray(output.size());
            env->SetByteArrayRegion(data,0,output.size(),reinterpret_cast<const jbyte *>(output.data()));
            env->CallVoidMethod(callback,method,data,count,promptMs,ms(decodeStart),dropped,static_cast<int>(promptTokens));
            env->DeleteLocalRef(data);
            if (env->ExceptionCheck()) return nullptr;
            auto batch = llama_batch_get_one(&t,1);
            if (llama_decode(e.ctx,batch) != 0 && !e.stop) throw std::runtime_error("Generation failed. Try a new chat.");
        }
        std::string result = e.stop ? "stopped" : (count==maxTokens ? "length" : "complete");
        __android_log_print(ANDROID_LOG_INFO,"BonsaiMetrics","tokens=%d prompt_tokens=%zu prompt_ms=%.0f decode_ms=%.0f dropped=%d finish=%s",count,promptTokens,promptMs,ms(decodeStart),dropped,result.c_str());
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception & ex) { fail(env,ex); return nullptr; }
}
