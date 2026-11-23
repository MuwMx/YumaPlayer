/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

#include <jni.h>
#include <cstdint>
#include <cstring>
#include <string>

#ifdef HAS_STORE_GUARD_DATA
#include "store_guard_data.h"
#else
namespace {
constexpr size_t kStoreKeyLen = 0;
constexpr uint8_t kStoreMask[1] = {0};
constexpr uint8_t kStoreData[1] = {0};
constexpr int kRequireReleasePackage = 1;
constexpr int kHasExpectedCert = 0;
constexpr uint8_t kExpectedCertMask[32] = {0};
constexpr uint8_t kExpectedCertData[32] = {0};
}
#endif

namespace {

inline void wipe(void* ptr, size_t len) {
    volatile uint8_t* p = reinterpret_cast<volatile uint8_t*>(ptr);
    while (len--) *p++ = 0;
}

inline uint32_t rotr(uint32_t x, uint32_t n) { return (x >> n) | (x << (32 - n)); }

constexpr uint32_t kSha256K[64] = {
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
    0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
    0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
    0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
    0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffaU, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u,
};

struct Sha256Ctx {
    uint32_t state[8];
    uint64_t bitLen;
    uint8_t buf[64];
    size_t bufLen;
};

void sha256Transform(Sha256Ctx& ctx, const uint8_t block[64]) {
    uint32_t w[64];
    for (int i = 0; i < 16; ++i) {
        w[i] = (static_cast<uint32_t>(block[i * 4]) << 24) |
               (static_cast<uint32_t>(block[i * 4 + 1]) << 16) |
               (static_cast<uint32_t>(block[i * 4 + 2]) << 8) |
               static_cast<uint32_t>(block[i * 4 + 3]);
    }
    for (int i = 16; i < 64; ++i) {
        uint32_t s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3);
        uint32_t s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10);
        w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }
    uint32_t a = ctx.state[0], b = ctx.state[1], c = ctx.state[2], d = ctx.state[3];
    uint32_t e = ctx.state[4], f = ctx.state[5], g = ctx.state[6], h = ctx.state[7];
    for (int i = 0; i < 64; ++i) {
        uint32_t s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
        uint32_t ch = (e & f) ^ ((~e) & g);
        uint32_t t1 = h + s1 + ch + kSha256K[i] + w[i];
        uint32_t s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
        uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        uint32_t t2 = s0 + maj;
        h = g; g = f; f = e; e = d + t1;
        d = c; c = b; b = a; a = t1 + t2;
    }
    ctx.state[0] += a; ctx.state[1] += b; ctx.state[2] += c; ctx.state[3] += d;
    ctx.state[4] += e; ctx.state[5] += f; ctx.state[6] += g; ctx.state[7] += h;
}

void sha256Init(Sha256Ctx& ctx) {
    ctx.state[0] = 0x6a09e667u; ctx.state[1] = 0xbb67ae85u;
    ctx.state[2] = 0x3c6ef372u; ctx.state[3] = 0xa54ff53au;
    ctx.state[4] = 0x510e527fu; ctx.state[5] = 0x9b05688cu;
    ctx.state[6] = 0x1f83d9abu; ctx.state[7] = 0x5be0cd19u;
    ctx.bitLen = 0;
    ctx.bufLen = 0;
}

void sha256Update(Sha256Ctx& ctx, const uint8_t* data, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        ctx.buf[ctx.bufLen++] = data[i];
        if (ctx.bufLen == 64) {
            sha256Transform(ctx, ctx.buf);
            ctx.bitLen += 512;
            ctx.bufLen = 0;
        }
    }
}

void sha256Final(Sha256Ctx& ctx, uint8_t out[32]) {
    uint64_t totalBits = ctx.bitLen + static_cast<uint64_t>(ctx.bufLen) * 8u;
    ctx.buf[ctx.bufLen++] = 0x80u;
    if (ctx.bufLen > 56) {
        while (ctx.bufLen < 64) ctx.buf[ctx.bufLen++] = 0;
        sha256Transform(ctx, ctx.buf);
        ctx.bufLen = 0;
    }
    while (ctx.bufLen < 56) ctx.buf[ctx.bufLen++] = 0;
    for (int i = 7; i >= 0; --i) {
        ctx.buf[56 + (7 - i)] = static_cast<uint8_t>((totalBits >> (i * 8)) & 0xffu);
    }
    sha256Transform(ctx, ctx.buf);
    for (int i = 0; i < 8; ++i) {
        out[i * 4] = static_cast<uint8_t>((ctx.state[i] >> 24) & 0xffu);
        out[i * 4 + 1] = static_cast<uint8_t>((ctx.state[i] >> 16) & 0xffu);
        out[i * 4 + 2] = static_cast<uint8_t>((ctx.state[i] >> 8) & 0xffu);
        out[i * 4 + 3] = static_cast<uint8_t>(ctx.state[i] & 0xffu);
    }
}

void sha256Bytes(const uint8_t* data, size_t len, uint8_t out[32]) {
    Sha256Ctx ctx;
    sha256Init(ctx);
    sha256Update(ctx, data, len);
    sha256Final(ctx, out);
    wipe(&ctx, sizeof(ctx));
}

void hmacSha256(const uint8_t* key, size_t keyLen, const uint8_t* msg, size_t msgLen, uint8_t out[32]) {
    uint8_t kBlock[64];
    std::memset(kBlock, 0, sizeof(kBlock));
    if (keyLen > 64) {
        sha256Bytes(key, keyLen, kBlock);
    } else {
        std::memcpy(kBlock, key, keyLen);
    }
    uint8_t ipad[64], opad[64];
    for (int i = 0; i < 64; ++i) {
        ipad[i] = kBlock[i] ^ 0x36u;
        opad[i] = kBlock[i] ^ 0x5cu;
    }
    Sha256Ctx inner;
    sha256Init(inner);
    sha256Update(inner, ipad, 64);
    sha256Update(inner, msg, msgLen);
    uint8_t innerHash[32];
    sha256Final(inner, innerHash);

    Sha256Ctx outer;
    sha256Init(outer);
    sha256Update(outer, opad, 64);
    sha256Update(outer, innerHash, 32);
    sha256Final(outer, out);

    wipe(kBlock, sizeof(kBlock));
    wipe(ipad, sizeof(ipad));
    wipe(opad, sizeof(opad));
    wipe(&inner, sizeof(inner));
    wipe(&outer, sizeof(outer));
    wipe(innerHash, sizeof(innerHash));
}

bool constantTimeEq32(const uint8_t a[32], const uint8_t b[32]) {
    uint8_t diff = 0;
    for (int i = 0; i < 32; ++i) diff |= (a[i] ^ b[i]);
    return diff == 0;
}

bool clearIfException(JNIEnv* env) {
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return true;
    }
    return false;
}

bool isAllowedPackage(const std::string& pkg) {
    if (pkg == "com.mochyy.mochymusic") return true;
    if (kRequireReleasePackage == 0) {
        return pkg == "com.mochyy.mochymusic.debug" ||
               pkg == "com.mochyy.mochymusic.fast" ||
               pkg == "com.mochyy.mochymusic.profileable";
    }
    return false;
}

// checks both the running package id and, on signed official builds, the apk's signing certificate digest
bool verifyCaller(JNIEnv* env, jobject context) {
    if (kStoreKeyLen == 0 || context == nullptr) return false;

    jclass ctxCls = env->GetObjectClass(context);
    if (!ctxCls) return false;

    jmethodID getPkgId = env->GetMethodID(ctxCls, "getPackageName", "()Ljava/lang/String;");
    if (!getPkgId || clearIfException(env)) {
        env->DeleteLocalRef(ctxCls);
        return false;
    }
    auto pkgJstr = static_cast<jstring>(env->CallObjectMethod(context, getPkgId));
    if (!pkgJstr || clearIfException(env)) {
        env->DeleteLocalRef(ctxCls);
        return false;
    }
    const char* pkgChars = env->GetStringUTFChars(pkgJstr, nullptr);
    std::string pkg = pkgChars ? pkgChars : "";
    if (pkgChars) env->ReleaseStringUTFChars(pkgJstr, pkgChars);

    if (!isAllowedPackage(pkg)) {
        env->DeleteLocalRef(pkgJstr);
        env->DeleteLocalRef(ctxCls);
        return false;
    }

    if (kHasExpectedCert == 0) {
        env->DeleteLocalRef(pkgJstr);
        env->DeleteLocalRef(ctxCls);
        return true;
    }

    bool certMatched = false;
    jclass buildVerCls = env->FindClass("android/os/Build$VERSION");
    jint sdkInt = 0;
    if (buildVerCls && !clearIfException(env)) {
        jfieldID sdkField = env->GetStaticFieldID(buildVerCls, "SDK_INT", "I");
        if (sdkField && !clearIfException(env)) sdkInt = env->GetStaticIntField(buildVerCls, sdkField);
        env->DeleteLocalRef(buildVerCls);
    }

    jmethodID getPmId = env->GetMethodID(ctxCls, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jobject pm = (getPmId && !clearIfException(env)) ? env->CallObjectMethod(context, getPmId) : nullptr;
    if (pm && !clearIfException(env)) {
        jclass pmCls = env->GetObjectClass(pm);
        jmethodID getPkgInfoId = pmCls ? env->GetMethodID(pmCls, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;") : nullptr;
        jint flags = (sdkInt >= 28) ? 0x08000000 : 0x00000040;
        jobject pkgInfo = (getPkgInfoId && !clearIfException(env)) ? env->CallObjectMethod(pm, getPkgInfoId, pkgJstr, flags) : nullptr;
        if (pkgInfo && !clearIfException(env)) {
            jclass piCls = env->GetObjectClass(pkgInfo);
            jobjectArray sigs = nullptr;
            if (sdkInt >= 28) {
                jfieldID siField = piCls ? env->GetFieldID(piCls, "signingInfo", "Landroid/content/pm/SigningInfo;") : nullptr;
                jobject si = (siField && !clearIfException(env)) ? env->GetObjectField(pkgInfo, siField) : nullptr;
                if (si && !clearIfException(env)) {
                    jclass siCls = env->GetObjectClass(si);
                    jmethodID hasMultiId = siCls ? env->GetMethodID(siCls, "hasMultipleSigners", "()Z") : nullptr;
                    jboolean multi = (hasMultiId && !clearIfException(env)) ? env->CallBooleanMethod(si, hasMultiId) : JNI_FALSE;
                    jmethodID getSigsId = siCls ? env->GetMethodID(
                        siCls,
                        multi ? "getApkContentsSigners" : "getSigningCertificateHistory",
                        "()[Landroid/content/pm/Signature;"
                    ) : nullptr;
                    if (getSigsId && !clearIfException(env)) {
                        sigs = static_cast<jobjectArray>(env->CallObjectMethod(si, getSigsId));
                    }
                    if (siCls) env->DeleteLocalRef(siCls);
                    env->DeleteLocalRef(si);
                }
            } else {
                jfieldID sigsField = piCls ? env->GetFieldID(piCls, "signatures", "[Landroid/content/pm/Signature;") : nullptr;
                if (sigsField && !clearIfException(env)) {
                    sigs = static_cast<jobjectArray>(env->GetObjectField(pkgInfo, sigsField));
                }
            }

            if (sigs && !clearIfException(env)) {
                uint8_t expected[32];
                for (int i = 0; i < 32; ++i) {
                    expected[i] = kExpectedCertData[i] ^ kExpectedCertMask[i] ^ static_cast<uint8_t>(i * 0x3D + 0x71);
                }
                jsize count = env->GetArrayLength(sigs);
                for (jsize i = 0; i < count && !certMatched; ++i) {
                    jobject sigObj = env->GetObjectArrayElement(sigs, i);
                    if (!sigObj || clearIfException(env)) continue;
                    jclass sigCls = env->GetObjectClass(sigObj);
                    jmethodID toBytesId = sigCls ? env->GetMethodID(sigCls, "toByteArray", "()[B") : nullptr;
                    auto certArr = (toBytesId && !clearIfException(env)) ? static_cast<jbyteArray>(env->CallObjectMethod(sigObj, toBytesId)) : nullptr;
                    if (certArr && !clearIfException(env)) {
                        jsize certLen = env->GetArrayLength(certArr);
                        jbyte* certPtr = env->GetByteArrayElements(certArr, nullptr);
                        if (certPtr && certLen > 0) {
                            uint8_t digest[32];
                            sha256Bytes(reinterpret_cast<const uint8_t*>(certPtr), static_cast<size_t>(certLen), digest);
                            if (constantTimeEq32(digest, expected)) certMatched = true;
                            wipe(digest, sizeof(digest));
                        }
                        if (certPtr) env->ReleaseByteArrayElements(certArr, certPtr, JNI_ABORT);
                        env->DeleteLocalRef(certArr);
                    }
                    if (sigCls) env->DeleteLocalRef(sigCls);
                    env->DeleteLocalRef(sigObj);
                }
                wipe(expected, sizeof(expected));
                env->DeleteLocalRef(sigs);
            }
            if (piCls) env->DeleteLocalRef(piCls);
            env->DeleteLocalRef(pkgInfo);
        }
        if (pmCls) env->DeleteLocalRef(pmCls);
        env->DeleteLocalRef(pm);
    }

    clearIfException(env);
    env->DeleteLocalRef(pkgJstr);
    env->DeleteLocalRef(ctxCls);
    return certMatched;
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mochyy_mochymusic_modules_browser_StoreSignature_nativeIsOfficial(JNIEnv* env, jobject, jobject context) {
    return verifyCaller(env, context) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mochyy_mochymusic_modules_browser_StoreSignature_nativeSign(JNIEnv* env, jobject, jobject context, jstring message) {
    if (!message || !verifyCaller(env, context)) return nullptr;
    if (kStoreKeyLen == 0 || kStoreKeyLen > 512) return nullptr;

    const char* msgChars = env->GetStringUTFChars(message, nullptr);
    if (!msgChars) return nullptr;
    size_t msgLen = static_cast<size_t>(env->GetStringUTFLength(message));

    uint8_t key[512];
    for (size_t i = 0; i < kStoreKeyLen; ++i) {
        key[i] = kStoreData[i] ^ kStoreMask[i] ^ static_cast<uint8_t>(i * 0x5Au + 0xA5u);
    }

    uint8_t digest[32];
    hmacSha256(key, kStoreKeyLen, reinterpret_cast<const uint8_t*>(msgChars), msgLen, digest);
    wipe(key, kStoreKeyLen);
    env->ReleaseStringUTFChars(message, msgChars);

    static constexpr char kHex[] = "0123456789abcdef";
    char hex[65];
    for (int i = 0; i < 32; ++i) {
        hex[i * 2] = kHex[(digest[i] >> 4) & 0x0f];
        hex[i * 2 + 1] = kHex[digest[i] & 0x0f];
    }
    hex[64] = '\0';
    wipe(digest, sizeof(digest));
    return env->NewStringUTF(hex);
}
