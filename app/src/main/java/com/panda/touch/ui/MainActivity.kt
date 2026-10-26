package com.panda.touch.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.panda.touch.R
import com.panda.touch.overlay.OverlayKeymapperService
import com.panda.touch.service.RootConnectionManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var tvRootStatus: TextView
    private lateinit var tvDeviceNode: TextView
    private lateinit var tvResolution: TextView
    private lateinit var btnStartOverlay: Button
    private lateinit var btnTestTap: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvRootStatus = findViewById(R.id.tvRootStatus)
        tvDeviceNode = findViewById(R.id.tvDeviceNode)
        tvResolution = findViewById(R.id.tvResolution)
        btnStartOverlay = findViewById(R.id.btnStartOverlay)
        btnTestTap = findViewById(R.id.btnTestTap)

        btnStartOverlay.setOnClickListener {
            checkOverlayAndStartService()
        }

        btnTestTap.setOnClickListener {
            testCenterTap()
        }

        checkRootAndConnectService()
    }

    private fun checkRootAndConnectService() {
        lifecycleScope.launch(Dispatchers.IO) {
            val isRootGranted = Shell.isAppGrantedRoot() == true
            withContext(Dispatchers.Main) {
                if (isRootGranted) {
                    tvRootStatus.text = "Root Status: GRANTED (UID 0 Daemon Ready)"
                    tvRootStatus.setTextColor(android.graphics.Color.GREEN)
                    // Tiến hành bind RootService
                    RootConnectionManager.bind(this@MainActivity)
                } else {
                    tvRootStatus.text = "Root Status: DENIED / NOT ROOTED"
                    tvRootStatus.setTextColor(android.graphics.Color.RED)
                    Toast.makeText(this@MainActivity, "Root permission is required for hardware evdev injection!", Toast.LENGTH_LONG).show()
                }
            }
        }

        lifecycleScope.launch {
            RootConnectionManager.isConnected.collectLatest { connected ->
                if (connected) {
                    initTouchDevice()
                }
            }
        }
    }

    private fun initTouchDevice() {
        val service = RootConnectionManager.getService() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val success = service.initTouchDevice()
            if (success) {
                // Gửi thông số màn hình sang Root Engine
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION")
                    wm.defaultDisplay
                }
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                display?.getRealMetrics(metrics)
                val rotation = display?.rotation ?: Surface.ROTATION_0

                service.updateDisplayMetrics(metrics.widthPixels, metrics.heightPixels, rotation)

                val path = service.devicePath
                val name = service.deviceName
                val minX = service.hwMinX
                val maxX = service.hwMaxX
                val minY = service.hwMinY
                val maxY = service.hwMaxY
                val slots = service.maxSlots

                withContext(Dispatchers.Main) {
                    tvDeviceNode.text = "Event Node: $path ($name)\nMax Slots: $slots"
                    tvResolution.text = "Digitizer Bounds: X[$minX..$maxX], Y[$minY..$maxY]\nDisplay: ${metrics.widthPixels}x${metrics.heightPixels}"
                }
            } else {
                withContext(Dispatchers.Main) {
                    tvDeviceNode.text = "Event Node: FAILED TO AUTO-DETECT TOUCHSCREEN"
                    tvDeviceNode.setTextColor(android.graphics.Color.RED)
                }
            }
        }
    }

    private fun checkOverlayAndStartService() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Vui lòng cấp quyền Hiển thị trên ứng dụng khác", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceIntent = Intent(this, OverlayKeymapperService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Keymapper HUD Started!", Toast.LENGTH_SHORT).show()
    }

    private fun testCenterTap() {
        val service = RootConnectionManager.getService()
        if (service == null) {
            Toast.makeText(this, "Root Service not connected!", Toast.LENGTH_SHORT).show()
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val centerX = metrics.widthPixels / 2f
        val centerY = metrics.heightPixels / 2f

        lifecycleScope.launch(Dispatchers.IO) {
            // Test tap trên Slot ảo số 5
            service.injectTap(5, centerX, centerY, 50)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Injected Tap at ($centerX, $centerY) on Slot 5", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RootConnectionManager.unbind()
    }
}
