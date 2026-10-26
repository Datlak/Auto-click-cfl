#include <jni.h>
#include <string>
#include "include/touch_injector.h"

using namespace panda;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeInitDevice(JNIEnv *env, jobject thiz) {
    return TouchInjector::getInstance().autoDetectAndOpen() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeOpenDevice(JNIEnv *env, jobject thiz, jstring path) {
    if (!path) return JNI_FALSE;
    const char *native_path = env->GetStringUTFChars(path, nullptr);
    bool result = TouchInjector::getInstance().openDevice(native_path);
    env->ReleaseStringUTFChars(path, native_path);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeCloseDevice(JNIEnv *env, jobject thiz) {
    TouchInjector::getInstance().closeDevice();
}

JNIEXPORT void JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeUpdateDisplayMetrics(JNIEnv *env, jobject thiz,
                                                                      jint width, jint height, jint rotation) {
    TouchInjector::getInstance().updateDisplayMetrics(width, height, rotation);
}

JNIEXPORT jboolean JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeInjectTouchDown(JNIEnv *env, jobject thiz,
                                                                 jint slot, jfloat x, jfloat y) {
    return TouchInjector::getInstance().injectTouchDown(slot, x, y) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeInjectTouchMove(JNIEnv *env, jobject thiz,
                                                                 jint slot, jfloat x, jfloat y) {
    return TouchInjector::getInstance().injectTouchMove(slot, x, y) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeInjectTouchUp(JNIEnv *env, jobject thiz, jint slot) {
    return TouchInjector::getInstance().injectTouchUp(slot) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeReleaseAllVirtualSlots(JNIEnv *env, jobject thiz) {
    TouchInjector::getInstance().releaseAllVirtualSlots();
}

JNIEXPORT jstring JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeGetDevicePath(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(TouchInjector::getInstance().getDevicePath().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeGetDeviceName(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(TouchInjector::getInstance().getDeviceName().c_str());
}

JNIEXPORT jint JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeGetHwMinX(JNIEnv *env, jobject thiz) {
    return TouchInjector::getInstance().getBounds().min_x;
}

JNIEXPORT jint JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeGetHwMaxX(JNIEnv *env, jobject thiz) {
    return TouchInjector::getInstance().getBounds().max_x;
}

JNIEXPORT jint JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeGetHwMinY(JNIEnv *env, jobject thiz) {
    return TouchInjector::getInstance().getBounds().min_y;
}

JNIEXPORT jint JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeGetHwMaxY(JNIEnv *env, jobject thiz) {
    return TouchInjector::getInstance().getBounds().max_y;
}

JNIEXPORT jint JNICALL
Java_com_panda_touch_core_NativeTouchEngine_nativeGetMaxSlots(JNIEnv *env, jobject thiz) {
    return TouchInjector::getInstance().getBounds().max_slots;
}

} // extern "C"
