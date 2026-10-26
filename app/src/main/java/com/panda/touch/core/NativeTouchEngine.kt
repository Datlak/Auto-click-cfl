package com.panda.touch.core

import android.util.Log

/**
 * Singleton trung gian tương tác với thư viện C++ qua JNI.
 * Được khởi chạy bên trong tiến trình Root Daemon (UID 0).
 */
object NativeTouchEngine {
    private const val TAG = "NativeTouchEngine"

    init {
        try {
            System.loadLibrary("touch_engine")
            Log.i(TAG, "Native library 'touch_engine' loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library 'touch_engine'", e)
        }
    }

    // Native declarations
    external fun nativeInitDevice(): Boolean
    external fun nativeOpenDevice(path: String): Boolean
    external fun nativeCloseDevice()
    external fun nativeUpdateDisplayMetrics(width: Int, height: Int, rotation: Int)
    external fun nativeInjectTouchDown(slot: Int, x: Float, y: Float): Boolean
    external fun nativeInjectTouchMove(slot: Int, x: Float, y: Float): Boolean
    external fun nativeInjectTouchUp(slot: Int): Boolean
    external fun nativeReleaseAllVirtualSlots()
    external fun nativeGetDevicePath(): String
    external fun nativeGetDeviceName(): String
    external fun nativeGetHwMinX(): Int
    external fun nativeGetHwMaxX(): Int
    external fun nativeGetHwMinY(): Int
    external fun nativeGetHwMaxY(): Int
    external fun nativeGetMaxSlots(): Int
}
