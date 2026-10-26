package com.panda.touch.service

import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.panda.touch.ITouchInjectorService
import com.panda.touch.core.NativeTouchEngine
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.*
import java.util.concurrent.Executors

/**
 * Root Service khởi chạy dưới quyền UID 0 (root) thông qua libsu IPC daemon.
 * Service này trực tiếp sở hữu Native Touch Engine và kết nối với App thông qua AIDL.
 */
class TouchRootService : RootService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val actionExecutor = Executors.newFixedThreadPool(4)

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "TouchRootService onBind called. Running under UID: ${android.os.Process.myUid()}")
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "TouchRootService onUnbind called.")
        NativeTouchEngine.nativeReleaseAllVirtualSlots()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "TouchRootService onDestroy called.")
        serviceScope.cancel()
        actionExecutor.shutdown()
        NativeTouchEngine.nativeCloseDevice()
        super.onDestroy()
    }

    private val binder = object : ITouchInjectorService.Stub() {

        override fun initTouchDevice(): Boolean {
            return NativeTouchEngine.nativeInitDevice()
        }

        override fun getDevicePath(): String {
            return NativeTouchEngine.nativeGetDevicePath()
        }

        override fun getDeviceName(): String {
            return NativeTouchEngine.nativeGetDeviceName()
        }

        override fun getHwMinX(): Int {
            return NativeTouchEngine.nativeGetHwMinX()
        }

        override fun getHwMaxX(): Int {
            return NativeTouchEngine.nativeGetHwMaxX()
        }

        override fun getHwMinY(): Int {
            return NativeTouchEngine.nativeGetHwMinY()
        }

        override fun getHwMaxY(): Int {
            return NativeTouchEngine.nativeGetHwMaxY()
        }

        override fun getMaxSlots(): Int {
            return NativeTouchEngine.nativeGetMaxSlots()
        }

        override fun updateDisplayMetrics(screenWidth: Int, screenHeight: Int, displayRotation: Int) {
            NativeTouchEngine.nativeUpdateDisplayMetrics(screenWidth, screenHeight, displayRotation)
        }

        override fun injectTouchDown(slot: Int, screenX: Float, screenY: Float): Boolean {
            return NativeTouchEngine.nativeInjectTouchDown(slot, screenX, screenY)
        }

        override fun injectTouchMove(slot: Int, screenX: Float, screenY: Float): Boolean {
            return NativeTouchEngine.nativeInjectTouchMove(slot, screenX, screenY)
        }

        override fun injectTouchUp(slot: Int): Boolean {
            return NativeTouchEngine.nativeInjectTouchUp(slot)
        }

        override fun injectTap(slot: Int, screenX: Float, screenY: Float, durationMs: Int): Boolean {
            actionExecutor.execute {
                NativeTouchEngine.nativeInjectTouchDown(slot, screenX, screenY)
                val sleepTime = if (durationMs > 0) durationMs.toLong() else 25L
                SystemClock.sleep(sleepTime)
                NativeTouchEngine.nativeInjectTouchUp(slot)
            }
            return true
        }

        override fun injectSwipe(
            slot: Int,
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Int,
            steps: Int
        ): Boolean {
            actionExecutor.execute {
                val totalSteps = if (steps > 1) steps else 10
                val interval = (durationMs.toLong() / totalSteps).coerceAtLeast(5L)

                NativeTouchEngine.nativeInjectTouchDown(slot, startX, startY)
                SystemClock.sleep(interval)

                for (i in 1..totalSteps) {
                    val progress = i.toFloat() / totalSteps.toFloat()
                    val curX = startX + (endX - startX) * progress
                    val curY = startY + (endY - startY) * progress
                    NativeTouchEngine.nativeInjectTouchMove(slot, curX, curY)
                    SystemClock.sleep(interval)
                }

                NativeTouchEngine.nativeInjectTouchUp(slot)
            }
            return true
        }

        override fun releaseAllVirtualSlots() {
            NativeTouchEngine.nativeReleaseAllVirtualSlots()
        }

        override fun closeDevice() {
            NativeTouchEngine.nativeCloseDevice()
        }
    }

    companion object {
        private const val TAG = "TouchRootService"
    }
}
