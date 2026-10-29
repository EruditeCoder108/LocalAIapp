#include <jni.h>
#include <dlfcn.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <malloc.h>
#include <unistd.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <fstream>
#include <map>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#include <nlohmann/json.hpp>
#include "phonon2.h"
using json=nlohmann::json;
namespace {
std::mutex gate; // Official kernels have process-global scratch and thread pools.
void require(bool value,const char * message) {if(!value) throw std::runtime_error(message);}
struct Api {
    void * encoder=nullptr,*decoder=nullptr;
#define SYMS(X) X(phonon2_cpu_abi_version) X(phonon2_enc_abi_version) X(phonon2_tdt_abi_version) X(phonon2_cpu_features) X(phonon2_cpu_build_features) X(phonon2_cpu_kernel_in_use) X(phonon2_cpu_set_threads) X(phonon2_cpu_create_onedot_wire_many) X(phonon2_cpu_destroy) X(phonon2_enc_create) X(phonon2_enc_destroy) X(phonon2_enc_set_subsampling) X(phonon2_enc_set_layer) X(phonon2_enc_out_len) X(phonon2_enc_forward) X(phonon2_tdt_set_threads) X(phonon2_tdt_create) X(phonon2_tdt_destroy) X(phonon2_tdt_decode)
#define DECL(name) decltype(&::name) name=nullptr;
    SYMS(DECL)
#undef DECL
    Api() {
        const auto features=getauxval(AT_HWCAP);
        const bool fast=(features & (1UL<<20)) && (features & (1UL<<8));
        const std::string suffix=fast ? ".so" : "-base.so";
        encoder=dlopen(("libphonon2_enc-android-arm64-v8a"+suffix).c_str(),RTLD_NOW|RTLD_LOCAL);
        decoder=dlopen(("libphonon2_tdt-android-arm64-v8a"+suffix).c_str(),RTLD_NOW|RTLD_LOCAL);
        if(!encoder || !decoder) {if(encoder) dlclose(encoder);if(decoder) dlclose(decoder);throw std::runtime_error("Could not load the Android speech engine.");}
#define RESOLVE(name) name=reinterpret_cast<decltype(name)>(dlsym(encoder,#name));if(!name) name=reinterpret_cast<decltype(name)>(dlsym(decoder,#name));if(!name) {dlclose(decoder);dlclose(encoder);throw std::runtime_error("Speech engine ABI mismatch: " #name);}
        SYMS(RESOLVE)
#undef RESOLVE
        require(phonon2_cpu_abi_version()==1 && phonon2_enc_abi_version()==1 && phonon2_tdt_abi_version()==3,"Unsupported speech engine version.");
        require((phonon2_cpu_build_features() & ~phonon2_cpu_features())==0,"Speech engine does not support this CPU.");
    }
    ~Api() {if(decoder) dlclose(decoder);if(encoder) dlclose(encoder);}
};
struct Mapping {
    void * data=MAP_FAILED;size_t size=0;
    explicit Mapping(const std::string & path) {
        int fd=::open(path.c_str(),O_RDONLY|O_CLOEXEC);require(fd>=0,"Speech model is missing.");
        struct stat stat{};if(fstat(fd,&stat)!=0 || stat.st_size<8) {::close(fd);throw std::runtime_error("Invalid speech model.");}
        size=stat.st_size;data=mmap(nullptr,size,PROT_READ,MAP_PRIVATE,fd,0);::close(fd);require(data!=MAP_FAILED,"Could not map the speech model.");
    }
    ~Mapping() {if(data!=MAP_FAILED) munmap(data,size);}
};
// Kernel workers are process-global. Keep their code loaded until the dedicated
// service process exits, even after the model handles have been destroyed.
Api & sharedApi() {static Api * api=new Api();return *api;}
float half(uint16_t h) {
    const int e=(h>>10)&31,m=h&1023;float value=e==0 ? std::ldexp(float(m),-24) : e==31 ? INFINITY : std::ldexp(float(1024+m),e-25);
    return h&0x8000 ? -value:value;
}
struct Record {std::string kind;std::vector<int> shape;const uint8_t * data;size_t bytes;};
size_t elements(const Record & r) {size_t n=1;for(int d:r.shape) {require(d>0 && d<=32768,"Invalid speech tensor shape.");n*=d;require(n<=33554432,"Speech tensor too large.");}return n;}
struct Table {std::vector<int8_t> q;std::vector<uint16_t> scales;};
Table table(const Record & r) {
    require(r.kind=="int6" && !r.shape.empty(),"Missing quantized speech table.");
    const size_t n=elements(r),packed=n*6/8;const int rows=r.shape[0];
    require(n%4==0 && r.bytes==packed+rows*2,"Invalid quantized speech table.");
    Table t;t.q.resize(n);t.scales.resize(rows);std::memcpy(t.scales.data(),r.data+packed,rows*2);
    for(size_t i=0;i<n;i+=4) {const size_t p=i/4*3;const uint32_t bits=r.data[p]|uint32_t(r.data[p+1])<<8|uint32_t(r.data[p+2])<<16;for(int k=0;k<4;k++) t.q[i+k]=int((bits>>(k*6))&63)-32;}
    return t;
}
std::vector<float> dense(const Record & r) {
    const size_t n=elements(r);std::vector<float> v(n);
    if(r.kind=="fp16") {require(r.bytes==n*2,"Invalid speech vector.");for(size_t i=0;i<n;i++) {uint16_t h;std::memcpy(&h,r.data+i*2,2);v[i]=half(h);}}
    else {auto t=table(r);const size_t cols=n/r.shape[0];for(size_t i=0;i<n;i++) v[i]=t.q[i]*half(t.scales[i/cols]);}
    for(float f:v) require(std::isfinite(f),"Nonfinite speech weights.");return v;
}
std::vector<uint16_t> bias(const Record & r,size_t expected) {require(r.kind=="fp16" && r.bytes==expected*2 && elements(r)==expected,"Invalid speech bias.");std::vector<uint16_t> v(expected);std::memcpy(v.data(),r.data,r.bytes);return v;}
struct Engine {
    Api & api=sharedApi();void * enc=nullptr,*dec=nullptr;std::vector<void *> matrices;
    std::vector<float> projector,projectorBias;
    ~Engine() {if(dec) api.phonon2_tdt_destroy(dec);if(enc) api.phonon2_enc_destroy(enc);for(auto p:matrices) if(p) api.phonon2_cpu_destroy(p);}
    void load(const std::string & path,int threads) {
        Mapping map(path);const auto * data=static_cast<const uint8_t *>(map.data);uint64_t header;std::memcpy(&header,data,8);
        require(header>0 && header<=4194304 && header<map.size-8,"Invalid speech container header.");
        const auto meta=json::parse(data+8,data+8+header);require(meta.at("format")=="fermion-five-value-parakeet-v1","Wrong speech model format.");
        std::map<std::string,Record> records;size_t offset=8+header;const auto & index=meta.at("index");require(index.is_array() && index.size()<2000,"Invalid speech index.");
        for(const auto & item:index) {const auto bytes=item.at("b").get<size_t>();require(bytes<=map.size-offset,"Truncated speech model.");Record r{item.at("k"),item.at("shape").get<std::vector<int>>(),data+offset,bytes};require(records.emplace(item.at("n").get<std::string>(),r).second,"Duplicate speech tensor.");offset+=bytes;}
        require(offset==map.size,"Unexpected speech model data.");
        auto rec=[&](const std::string & name)->const Record & {auto p=records.find(name);if(p==records.end()) throw std::runtime_error("Missing speech tensor: "+name);return p->second;};
        api.phonon2_cpu_set_threads(std::clamp(threads,1,4));api.phonon2_tdt_set_threads(std::clamp(threads,1,4));
        const char * names[]={"feed_forward1.linear1","feed_forward1.linear2","self_attn.q_proj","self_attn.k_proj","self_attn.v_proj","self_attn.o_proj","self_attn.relative_k_proj","conv.pointwise_conv1","conv.pointwise_conv2","feed_forward2.linear1","feed_forward2.linear2"};
        std::vector<int> rows,cols;std::vector<const uint8_t *> blobs;std::vector<size_t> lens;
        const int expectedRows[]={4096,1024,1024,1024,1024,1024,1024,2048,1024,4096,1024};const int expectedCols[]={1024,4096,1024,1024,1024,1024,1024,1024,1024,1024,4096};
        for(int l=0;l<24;l++) for(int j=0;j<11;j++) {const auto & r=rec("encoder.layers."+std::to_string(l)+"."+names[j]);require(r.kind=="five_value" && r.shape==std::vector<int>({expectedRows[j],expectedCols[j]}),"Wrong speech encoder dimensions.");rows.push_back(r.shape[0]);cols.push_back(r.shape[1]);blobs.push_back(r.data);lens.push_back(r.bytes);}
        matrices.resize(264,nullptr);require(api.phonon2_cpu_create_onedot_wire_many(264,rows.data(),cols.data(),blobs.data(),lens.data(),matrices.data())==264,"Could not prepare speech weights.");
        enc=api.phonon2_enc_create(24,1024,4096,8,9,128,256);require(enc,"Could not create speech encoder.");
        const char * sub[]={"layers.0.weight","layers.0.bias","layers.2.weight","layers.2.bias","layers.3.weight","layers.3.bias","layers.5.weight","layers.5.bias","layers.6.weight","layers.6.bias","linear.weight","linear.bias"};
        const size_t subSize[]={2304,256,2304,256,65536,256,2304,256,65536,256,4194304,1024};
        std::vector<std::vector<float>> s;for(int i=0;i<12;i++) {s.push_back(dense(rec("encoder.subsampling."+std::string(sub[i]))));require(s.back().size()==subSize[i],"Wrong speech subsampling dimensions.");}
        std::vector<float> inv(512);for(int i=0;i<512;i++) inv[i]=1.0f/std::pow(10000.0f,2.0f*i/1024);
        require(api.phonon2_enc_set_subsampling(enc,s[0].data(),s[1].data(),s[2].data(),s[3].data(),s[4].data(),s[5].data(),s[6].data(),s[7].data(),s[8].data(),s[9].data(),s[10].data(),s[11].data(),inv.data())==0,"Speech subsampling initialization failed.");
        const char * vecNames[]={"norm_feed_forward1.weight","norm_feed_forward1.bias","norm_self_att.weight","norm_self_att.bias","norm_conv.weight","norm_conv.bias","norm_feed_forward2.weight","norm_feed_forward2.bias","norm_out.weight","norm_out.bias","self_attn.bias_u","self_attn.bias_v","conv.depthwise_conv.weight","conv.norm.weight","conv.norm.bias","conv.norm.running_mean","conv.norm.running_var"};
        for(int l=0;l<24;l++) {std::vector<std::vector<float>> v;const float * ptr[17];for(int j=0;j<17;j++) {v.push_back(dense(rec("encoder.layers."+std::to_string(l)+"."+vecNames[j])));require(v.back().size()==size_t(j==12 ? 9216:1024),"Wrong speech layer dimensions.");ptr[j]=v.back().data();}require(api.phonon2_enc_set_layer(enc,l,matrices.data()+11*l,ptr)==0,"Speech encoder initialization failed.");}
        projector=dense(rec("encoder_projector.weight"));projectorBias=dense(rec("encoder_projector.bias"));require(projector.size()==640*1024 && projectorBias.size()==640,"Wrong speech projector.");
        auto checkedTable=[&](const char * name,int r,int c) {const auto & value=rec(name);require(value.shape==std::vector<int>({r,c}),"Wrong speech table dimensions.");return table(value);};
        auto emb=checkedTable("decoder.embedding.weight",8193,640),ih0=checkedTable("decoder.lstm.weight_ih_l0",2560,640),hh0=checkedTable("decoder.lstm.weight_hh_l0",2560,640),ih1=checkedTable("decoder.lstm.weight_ih_l1",2560,640),hh1=checkedTable("decoder.lstm.weight_hh_l1",2560,640),proj=checkedTable("decoder.decoder_projector.weight",640,640),head=checkedTable("joint.head.weight",8198,640);
        require(emb.q.size()==8193*640 && ih0.q.size()==2560*640 && hh0.q.size()==2560*640 && ih1.q.size()==2560*640 && hh1.q.size()==2560*640 && proj.q.size()==640*640 && head.q.size()==8198*640,"Wrong speech decoder dimensions.");
        auto bi0=bias(rec("decoder.lstm.bias_ih_l0"),2560),bh0=bias(rec("decoder.lstm.bias_hh_l0"),2560),bi1=bias(rec("decoder.lstm.bias_ih_l1"),2560),bh1=bias(rec("decoder.lstm.bias_hh_l1"),2560),pb=bias(rec("decoder.decoder_projector.bias"),640),hb=bias(rec("joint.head.bias"),8198);
        int durations[]={0,1,2,3,4};dec=api.phonon2_tdt_create(8193,640,640,8198,5,durations,8192,10,emb.q.data(),emb.scales.data(),ih0.q.data(),ih0.scales.data(),bi0.data(),hh0.q.data(),hh0.scales.data(),bh0.data(),ih1.q.data(),ih1.scales.data(),bi1.data(),hh1.q.data(),hh1.scales.data(),bh1.data(),proj.q.data(),proj.scales.data(),pb.data(),head.q.data(),head.scales.data(),hb.data());require(dec,"Could not create speech decoder.");
    }
    std::vector<int32_t> transcribe(const float * mel,int frames) {
        for(int i=0;i<frames*128;i++) require(std::isfinite(mel[i]),"Invalid audio features.");
        const int n=api.phonon2_enc_out_len(enc,frames);require(n>0 && n<=376,"Recording is too long.");
        std::vector<float> encoded(n*1024),projected(n*640);
        require(api.phonon2_enc_forward(enc,mel,frames,encoded.data())==0,"Speech encoder could not process the recording.");
        for(int t=0;t<n;t++) for(int j=0;j<640;j++) {float sum=projectorBias[j];const float * w=projector.data()+j*1024;const float * x=encoded.data()+t*1024;for(int k=0;k<1024;k++) sum+=w[k]*x[k];projected[t*640+j]=sum;}
        std::vector<int32_t> ids(n*12+64);const int count=api.phonon2_tdt_decode(dec,projected.data(),n,ids.data(),ids.size());require(count>=0 && size_t(count)<=ids.size(),"Invalid speech decoder output.");ids.resize(count);return ids;
    }
};
void fail(JNIEnv * env,const std::exception & e) {env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_bonsai_pocket_PhononNative_open(JNIEnv * env,jobject,jstring path,jint threads) {
    std::lock_guard<std::mutex> lock(gate);try {const char * raw=env->GetStringUTFChars(path,nullptr);std::string filename(raw);env->ReleaseStringUTFChars(path,raw);auto e=std::make_unique<Engine>();e->load(filename,threads);return reinterpret_cast<jlong>(e.release());}catch(const std::exception & e) {fail(env,e);return 0;}
}
extern "C" JNIEXPORT jintArray JNICALL Java_dev_bonsai_pocket_PhononNative_transcribe(JNIEnv * env,jobject,jlong handle,jfloatArray data,jint frames) {
    std::lock_guard<std::mutex> lock(gate);try {require(handle && frames>=8 && frames<=3001 && env->GetArrayLength(data)==frames*128,"Invalid recording length.");std::vector<float> mel(frames*128);env->GetFloatArrayRegion(data,0,mel.size(),mel.data());const auto ids=reinterpret_cast<Engine *>(handle)->transcribe(mel.data(),frames);auto result=env->NewIntArray(ids.size());env->SetIntArrayRegion(result,0,ids.size(),ids.data());return result;}catch(const std::exception & e) {fail(env,e);return nullptr;}
}
extern "C" JNIEXPORT void JNICALL Java_dev_bonsai_pocket_PhononNative_close(JNIEnv *,jobject,jlong handle) {
    std::lock_guard<std::mutex> lock(gate);delete reinterpret_cast<Engine *>(handle);
#ifdef M_PURGE
    mallopt(M_PURGE,0); // Return freed speech weights before the chat model reloads.
#endif
}
