// libmochat.so — MoChat native layer.
//
// Reverse-engineering target for chains #1, #6, #12. The functions here are
// deliberately weak on purpose:
//   - walletEncrypt/Decrypt replicate the single-byte-XOR crypto-misuse bug
//   - checkRoot / antiDebug are trivially Frida-bypassable
//   - obfKey returns the master key for the Java Obf class
//
// In the HARD flavor, OBFUSCATE() hides string literals; -fvisibility=hidden + R8
// full keep only the JNI exports visible. Analysts must use Ghidra/IDA + Frida.

#include <jni.h>
#include <vector>
#include <cstring>
#include <cstdio>
#include <unistd.h>
#include <android/log.h>

#include "obfuscation.h"

#define TAG "mochat-native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

// ---------------------------------------------------------------------------
// Wallet "crypto" — buggy XOR replicating a classic single-byte-key flaw.
//
// The bug: the inner loop runs over the ENTIRE key for every byte, so each byte
// ends up XORed only with the LAST key character. Reversing is then trivial:
//   plaintext[i] = ciphertext[i] ^ key[keyLen-1]
//
// XOR is symmetric, so encrypt and decrypt are the same operation; the function
// therefore takes no direction flag.
// ---------------------------------------------------------------------------
jbyteArray xor_buggy(JNIEnv* env, jbyteArray data, jbyteArray key) {
    jsize dlen = env->GetArrayLength(data);
    jsize klen = env->GetArrayLength(key);
    if (dlen <= 0 || klen <= 0) return env->NewByteArray(0);

    jbyte* d = env->GetByteArrayElements(data, nullptr);
    jbyte* k = env->GetByteArrayElements(key, nullptr);

    jbyte last = k[klen - 1];   // <-- the bug: only the last key byte matters
    std::vector<jbyte> out(dlen);
    for (jsize i = 0; i < dlen; ++i) {
        // intended: out[i] = d[i] ^ k[i % klen]
        // actual (buggy): every iteration overwrites with each k[j], so net effect is last byte.
        for (jsize j = 0; j < klen; ++j) {
            out[i] = static_cast<jbyte>(d[i] ^ k[j]);
        }
        // Defensive: the above loop already reduces to last; keep it explicit for readability:
        out[i] = static_cast<jbyte>(d[i] ^ last);
    }

    env->ReleaseByteArrayElements(data, d, JNI_ABORT);
    env->ReleaseByteArrayElements(key, k, JNI_ABORT);

    jbyteArray result = env->NewByteArray(dlen);
    env->SetByteArrayRegion(result, 0, dlen, out.data());
    return result;
}

// Su paths (OBFUSCATE hides them in the hard flavor).
const char* SU_PATHS[] = {
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/su/bin/su",
    "/data/local/xbin/su",
    "/data/local/bin/su",
};

}  // namespace

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_mochat_app_nbridge_NativeBridge_walletEncrypt(JNIEnv* env, jclass, jbyteArray data, jbyteArray key) {
    return xor_buggy(env, data, key);
}

JNIEXPORT jbyteArray JNICALL
Java_com_mochat_app_nbridge_NativeBridge_walletDecrypt(JNIEnv* env, jclass, jbyteArray data, jbyteArray key) {
    return xor_buggy(env, data, key);
}

JNIEXPORT jboolean JNICALL
Java_com_mochat_app_nbridge_NativeBridge_checkRoot(JNIEnv* env, jclass) {
    // 1. Check for su binaries.
    for (const char* p : SU_PATHS) {
        // In the hard flavor the paths above are cleartext arrays; for an extra
        // layer we'd OBFUSCATE each, but the access() call needs a runtime C string.
        if (access(p, F_OK) == 0) {
            LOGD("root indicator: %s", p);
            return JNI_TRUE;
        }
    }
    // 2. Check the tags (release-keys vs test-keys). Done in Java normally; mirrored here.
    jclass build = env->FindClass("android/os/Build");
    jfieldID tagsId = env->GetStaticFieldID(build, "TAGS", "Ljava/lang/String;");
    jstring tags = static_cast<jstring>(env->GetStaticObjectField(build, tagsId));
    const char* t = env->GetStringUTFChars(tags, nullptr);
    bool isTest = strstr(t, "test-keys") != nullptr;
    env->ReleaseStringUTFChars(tags, t);
    return isTest ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_mochat_app_nbridge_NativeBridge_antiDebug(JNIEnv* env, jclass) {
    // Read /proc/self/status and look for TracerPid != 0 — a non-invasive debug
    // detection (does NOT self-attach with PTRACE_TRACEME, which would hang the app).
    // Frida-bypassable by hooking this function to return JNI_FALSE.
    FILE* f = fopen("/proc/self/status", "r");
    if (!f) return JNI_FALSE;
    char line[256];
    int tracer_pid = 0;
    while (fgets(line, sizeof(line), f)) {
        if (sscanf(line, "TracerPid:%d", &tracer_pid) == 1) break;
    }
    fclose(f);
    return tracer_pid != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_mochat_app_nbridge_NativeBridge_obfKey(JNIEnv* env, jclass) {
    // Master key for the Java Obf class.
    // Real value: "MoChat!" — XOR-encoded by OBFUSCATE() in the .rodata section.
    // FLAG 10: the final flag is OBFUSCATE'd here. An analyst who reverses the
    // XOR routine and the OBFUSCATE macro recovers it from the .so binary.
    const char* k = OBFUSCATE("MoChat!\nflag{10-native-crypto-reverse}");
    jsize len = static_cast<jsize>(strlen(k));
    jbyteArray out = env->NewByteArray(len);
    env->SetByteArrayRegion(out, 0, len, reinterpret_cast<const jbyte*>(k));
    return out;
}

}  // extern "C"
