#include <android/log.h>
#include <jni.h>

namespace {

// Public, process-local liblog callback (API 30+). Never copy or inspect message
// payloads, tags, filenames, or exception strings: any SDK can place text there.
// Preserve warning/error severity with a constant diagnostic instead of contents.
void private_logger(const __android_log_message* message) {
    if (message == nullptr || message->priority < ANDROID_LOG_WARN) return;
    __android_log_message safe{};
    safe.struct_size = sizeof(safe);
    safe.buffer_id = LOG_ID_MAIN;
    safe.priority = message->priority;
    safe.tag = "ScreenLingoPrivacy";
    safe.message = "Runtime diagnostic redacted to protect screen text.";
    __android_log_logd_logger(&safe);
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_example_livescreentranslation_privacy_NativeLogPrivacy_install(
    JNIEnv*, jobject) {
    // A minimum-priority setting alone is insufficient: a per-tag system property
    // can override it. The callback is the payload boundary, without setprop/root.
    __android_log_set_logger(private_logger);
}
