package com.root.xinputbtproxy

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class GamepadProxyService : Service() {

    private var btHidDevice: BluetoothHidDevice? = null
    @Volatile private var connectedHost: BluetoothDevice? = null
    private val binder = LocalBinder()
    private var usedRootInSession = false

    var onHostConnectionChanged: ((String?) -> Unit)? = null

    // Экзекьютор для громкости
    private val volumeExecutor = Executors.newSingleThreadExecutor()

    private val targetState = ByteArray(9).apply { this[2] = 0x08 }
    private val lastAckedState = ByteArray(9).apply { this[2] = 0x08 }

    private val stateLock = java.lang.Object()
    @Volatile private var senderRunning = false
    private var senderThread: Thread? = null

    inner class LocalBinder : Binder() {
        fun getService(): GamepadProxyService = this@GamepadProxyService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
    }

    fun getConnectedHostName(): String? {
        val host = connectedHost ?: return null
        return "${host.name ?: "ПК"} (${host.address})"
    }

    fun startProxy(useRoot: Boolean) {
        usedRootInSession = useRoot
        if (useRoot) RootBtSpoofer.setGamepadMode(enable = true)

        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter ?: return

        val prefs = getSharedPreferences("bt_proxy_prefs", Context.MODE_PRIVATE)
        val currentName = adapter.name
        if ((!currentName.isNullOrEmpty()) && currentName != "Xbox Wireless Controller") {
            prefs.edit().putString("original_bt_name", currentName).apply()
        }

        adapter.name = "Xbox Wireless Controller"

        adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    btHidDevice = proxy as BluetoothHidDevice
                    registerHidApp()
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    btHidDevice = null
                    connectedHost = null
                    onHostConnectionChanged?.invoke(null)
                }
            }
        }, BluetoothProfile.HID_DEVICE)

        startDedicatedSenderThread()
    }

    fun stopProxyAndRestorePhone() {
        senderRunning = false
        synchronized(stateLock) { stateLock.notifyAll() }
        senderThread?.interrupt()
        senderThread = null

        btHidDevice?.unregisterApp()
        connectedHost = null
        onHostConnectionChanged?.invoke(null)

        val prefs = getSharedPreferences("bt_proxy_prefs", Context.MODE_PRIVATE)
        val originalName = prefs.getString("original_bt_name", null)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (!originalName.isNullOrEmpty()) btManager.adapter?.name = originalName

        if (usedRootInSession) {
            RootBtSpoofer.setGamepadMode(false)
            usedRootInSession = false
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerHidApp() {
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Xbox Wireless Controller",
            "BTBOX-mz Gamepad",
            "Microsoft",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            XboxHidDescriptor.DESCRIPTOR
        )

        val qosOut = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_GUARANTEED,
            900, 9, 8000, 8000, BluetoothHidDeviceAppQosSettings.MAX
        )

        btHidDevice?.registerApp(
            sdpSettings, null, qosOut, Executors.newCachedThreadPool(),
            object : BluetoothHidDevice.Callback() {
                override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                    if (registered) {
                        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                        btManager.adapter?.bondedDevices?.firstOrNull()?.let { pcDevice ->
                            btHidDevice?.connect(pcDevice)
                        }
                    }
                }
                override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                    if (state == BluetoothProfile.STATE_CONNECTED && device != null) {
                        connectedHost = device
                        onHostConnectionChanged?.invoke("${device.name ?: "ПК"} (${device.address})")
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        if (connectedHost == device) {
                            connectedHost = null
                            onHostConnectionChanged?.invoke(null)
                        }
                    }
                }
            }
        )
    }

    private fun startDedicatedSenderThread() {
        if (senderRunning) return
        senderRunning = true

        senderThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val packetToSend = ByteArray(9)

            while (senderRunning) {
                var hasChanges = false
                var buttonsChanged = false

                synchronized(stateLock) {
                    if (targetState.contentEquals(lastAckedState)) {
                        try { stateLock.wait(250) } catch (_: InterruptedException) {}
                    }
                    if (!targetState.contentEquals(lastAckedState)) {
                        hasChanges = true
                        buttonsChanged = (targetState[0] != lastAckedState[0]) || (targetState[1] != lastAckedState[1]) || (targetState[2] != lastAckedState[2])
                        System.arraycopy(targetState, 0, packetToSend, 0, 9)
                    }
                }

                val host = connectedHost
                val hid = btHidDevice

                if (hasChanges && host != null && hid != null) {
                    val sentOk = hid.sendReport(host, XboxHidDescriptor.REPORT_ID.toInt(), packetToSend)
                    if (sentOk) {
                        synchronized(stateLock) { System.arraycopy(packetToSend, 0, lastAckedState, 0, 9) }
                        if (!buttonsChanged) try { Thread.sleep(4) } catch (_: InterruptedException) {}
                    } else {
                        try { Thread.sleep(2) } catch (_: InterruptedException) {}
                    }
                }
            }
        }.apply { start() }
    }

    fun updateButton(buttonMask: Int, pressed: Boolean) {
        synchronized(stateLock) {
            var currentButtons = (targetState[0].toInt() and 0xFF) or ((targetState[1].toInt() and 0xFF) shl 8)
            currentButtons = if (pressed) (currentButtons or buttonMask) else (currentButtons and buttonMask.inv())
            val b0 = (currentButtons and 0xFF).toByte()
            val b1 = ((currentButtons shr 8) and 0xFF).toByte()

            if (targetState[0] != b0 || targetState[1] != b1) {
                targetState[0] = b0
                targetState[1] = b1
                stateLock.notifyAll()
            }
        }
    }

    fun updateAxes(lx: Float, ly: Float, rx: Float, ry: Float, lt: Float, rt: Float, hatX: Float, hatY: Float, ltButtonMask: Int, rtButtonMask: Int) {
        val newHat = calculateHatSwitch(hatX, hatY)
        val bLx = applyDeadzoneAndScale(lx)
        val bLy = applyDeadzoneAndScale(ly)
        val bRx = applyDeadzoneAndScale(rx)
        val bRy = applyDeadzoneAndScale(ry)

        val iLt = if (lt < 0.03f) 0 else (lt * 255f).roundToInt().coerceIn(0, 255)
        val iRt = if (rt < 0.03f) 0 else (rt * 255f).roundToInt().coerceIn(0, 255)

        synchronized(stateLock) {
            var changed = false

            if (ltButtonMask != 0 || rtButtonMask != 0) {
                var currentButtons = (targetState[0].toInt() and 0xFF) or ((targetState[1].toInt() and 0xFF) shl 8)
                if (ltButtonMask != 0) {
                    currentButtons = if (lt > 0.30f) (currentButtons or ltButtonMask) else (currentButtons and ltButtonMask.inv())
                }
                if (rtButtonMask != 0) {
                    currentButtons = if (rt > 0.30f) (currentButtons or rtButtonMask) else (currentButtons and rtButtonMask.inv())
                }
                val b0 = (currentButtons and 0xFF).toByte()
                val b1 = ((currentButtons shr 8) and 0xFF).toByte()
                if (targetState[0] != b0 || targetState[1] != b1) {
                    targetState[0] = b0; targetState[1] = b1; changed = true
                }
            }

            if (targetState[2] != newHat) { targetState[2] = newHat; changed = true }
            if (isStickChanged(targetState[3], bLx)) { targetState[3] = bLx; changed = true }
            if (isStickChanged(targetState[4], bLy)) { targetState[4] = bLy; changed = true }
            if (isStickChanged(targetState[5], bRx)) { targetState[5] = bRx; changed = true }
            if (isStickChanged(targetState[6], bRy)) { targetState[6] = bRy; changed = true }
            if (isTriggerChanged(targetState[7].toInt() and 0xFF, iLt)) { targetState[7] = iLt.toByte(); changed = true }
            if (isTriggerChanged(targetState[8].toInt() and 0xFF, iRt)) { targetState[8] = iRt.toByte(); changed = true }

            if (changed) stateLock.notifyAll()
        }
    }

    // --- ОТПРАВКА МУЛЬТИМЕДИА КОМАНД (ГРОМКОСТЬ) ---
    fun sendVolumePulse(up: Boolean) {
        val host = connectedHost ?: return
        val hid = btHidDevice ?: return

        volumeExecutor.execute {
            val volBuffer = ByteArray(1)
            // Бит 0 - Volume UP (1), Бит 1 - Volume DOWN (2)
            volBuffer[0] = if (up) 1.toByte() else 2.toByte()
            hid.sendReport(host, XboxHidDescriptor.VOLUME_REPORT_ID.toInt(), volBuffer)

            try { Thread.sleep(15) } catch (_: InterruptedException) {}

            volBuffer[0] = 0.toByte()
            hid.sendReport(host, XboxHidDescriptor.VOLUME_REPORT_ID.toInt(), volBuffer)
        }
    }

    private fun applyDeadzoneAndScale(value: Float): Byte {
        if (abs(value) < 0.04f) return 0
        return (value * 127f).roundToInt().coerceIn(-127, 127).toByte()
    }

    private fun isStickChanged(oldVal: Byte, newVal: Byte): Boolean {
        if (newVal == 0.toByte() && oldVal != 0.toByte()) return true
        if (newVal == 127.toByte() || newVal == (-127).toByte()) return oldVal != newVal
        return abs(oldVal.toInt() - newVal.toInt()) >= 2
    }

    private fun isTriggerChanged(oldUnsigned: Int, newUnsigned: Int): Boolean {
        return ((newUnsigned == 0 || newUnsigned == 255) && oldUnsigned != newUnsigned) || abs(oldUnsigned - newUnsigned) >= 3
    }

    private fun calculateHatSwitch(x: Float, y: Float): Byte {
        val up = y < -0.5f; val down = y > 0.5f; val left = x < -0.5f; val right = x > 0.5f
        return when { up && right -> 2; down && right -> 4; down && left -> 6; up && left -> 8; up -> 1; right -> 3; down -> 5; left -> 7; else -> 0 }.toByte()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "btbox_mz_channel"
        val channel = NotificationChannel(channelId, "BTBOX-mz Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(1, Notification.Builder(this, channelId).setContentTitle("BTBOX-mz").setContentText("Режим Bluetooth-геймпада активен").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build())
    }

    override fun onDestroy() {
        senderRunning = false
        volumeExecutor.shutdownNow()
        synchronized(stateLock) { stateLock.notifyAll() }
        btHidDevice?.unregisterApp()
        super.onDestroy()
    }
}