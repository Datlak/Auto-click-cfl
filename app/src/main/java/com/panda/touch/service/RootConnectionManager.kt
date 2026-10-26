package com.panda.touch.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.panda.touch.ITouchInjectorService
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quản lý vòng đời kết nối IPC từ ứng dụng thông thường sang TouchRootService (UID 0).
 */
object RootConnectionManager {
    private const val TAG = "RootConnManager"

    private var service: ITouchInjectorService? = null
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "RootService connected successfully via IPC.")
            service = ITouchInjectorService.Stub.asInterface(binder)
            _isConnected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "RootService disconnected unexpectedly.")
            service = null
            _isConnected.value = false
        }
    }

    fun bind(context: Context) {
        if (_isConnected.value) return
        val intent = Intent(context, TouchRootService::class.java)
        Log.i(TAG, "Binding to TouchRootService using libsu RootService.bind...")
        RootService.bind(intent, connection)
    }

    fun unbind() {
        if (_isConnected.value) {
            try {
                RootService.unbind(connection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding RootService", e)
            }
            service = null
            _isConnected.value = false
        }
    }

    fun getService(): ITouchInjectorService? = service
}
