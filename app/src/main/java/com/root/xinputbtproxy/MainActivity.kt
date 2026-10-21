@file:Suppress("SpellCheckingInspection", "PrivatePropertyName")

package com.root.xinputbtproxy

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.CompoundButtonCompat

@SuppressLint("SetTextI18n", "ClickableViewAccessibility", "InlinedApi")
class MainActivity : AppCompatActivity() {

    private var proxyService: GamepadProxyService? = null
    private var isBound = false
    private var isProxyActive = false
    private var isBlackoutActive = false

    private lateinit var rootContainer: FrameLayout
    private lateinit var controlsLayout: LinearLayout
    private lateinit var blackoutOverlay: TextView

    private lateinit var titleText: TextView
    private lateinit var hostStatusText: TextView
    private lateinit var debugKeyText: TextView
    private lateinit var toggleButton: Button
    private lateinit var rootSwitch: CheckBox
    private lateinit var linuxMappingSwitch: CheckBox
    private lateinit var triggersAsButtonsSwitch: CheckBox
    private lateinit var swapAbxySwitch: CheckBox

    private val blackoutHandler = Handler(Looper.getMainLooper())
    private val BLACKOUT_TIMEOUT_MS = 10_000L

    // КЭШ ОСЕЙ: защищает от обнуления данных составными устройствами
    private var axisLx = 0f; private var axisLy = 0f
    private var axisRx = 0f; private var axisRy = 0f
    private var axisLt = 0f; private var axisRt = 0f
    private var axisHatX = 0f; private var axisHatY = 0f

    // КЭШ НАЖАТИЯ ЦИФРОВЫХ КУРКОВ
    private var isL2Pressed = false
    private var isR2Pressed = false

    private val blackoutRunnable = Runnable { if (isProxyActive) enterBlackoutMode() }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as GamepadProxyService.LocalBinder
            proxyService = binder.getService()
            proxyService?.onHostConnectionChanged = { hostName -> runOnUiThread { updateHostStatus(hostName) } }
            proxyService?.startProxy(rootSwitch.isChecked)
            isBound = true; isProxyActive = true
            updateHostStatus(proxyService?.getConnectedHostName())
            updateUiState(); resetBlackoutTimer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            proxyService?.onHostConnectionChanged = null
            proxyService = null
            isBound = false; isProxyActive = false
            exitBlackoutMode()
            updateHostStatus(null); updateUiState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK); isFocusable = true; isFocusableInTouchMode = true
        }

        controlsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(56, 64, 56, 64)
        }

        titleText = TextView(this).apply {
            text = "BTBOX-mz"; textSize = 24f; setTextColor(Color.WHITE); setPadding(0, 0, 0, 12)
        }

        hostStatusText = TextView(this).apply {
            text = "Хост BT: ожидание подключения ПК..."
            textSize = 16f; setTextColor(0xFFFFB74D.toInt()); setPadding(0, 0, 0, 12)
        }

        debugKeyText = TextView(this).apply {
            textSize = 14f; setTextColor(0xFF81C784.toInt()); text = "Ввод: ожидание нажатий геймпада..."
            setPadding(0, 0, 0, 28)
        }

        val lightCheckColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(0xFF00E676.toInt(), 0xFFECEFF1.toInt())
        )

        rootSwitch = createStyledCheckBox("Режим Root (CoD + приоритет BT) [Проверка...]", checked = false, lightCheckColors)
        linuxMappingSwitch = createStyledCheckBox("Таблица битов Linux / Debian (Снять для Windows)", false, lightCheckColors)
        triggersAsButtonsSwitch = createStyledCheckBox("Режим курков: ТОЛЬКО цифровые кнопки (отключить оси)", true, lightCheckColors)
        swapAbxySwitch = createStyledCheckBox("Инверсия кнопок A↔B и X↔Y", false, lightCheckColors)

        toggleButton = Button(this).apply {
            textSize = 16f; setPadding(32, 40, 32, 40); isFocusable = false
            setOnClickListener { resetBlackoutTimer(); if (isProxyActive) disableProxy() else enableProxy() }
        }

        controlsLayout.addView(titleText); controlsLayout.addView(hostStatusText); controlsLayout.addView(debugKeyText)
        controlsLayout.addView(rootSwitch); controlsLayout.addView(linuxMappingSwitch)
        controlsLayout.addView(triggersAsButtonsSwitch); controlsLayout.addView(swapAbxySwitch)
        controlsLayout.addView(toggleButton)

        // --- ЛОГИКА СВАЙПА ГРОМКОСТИ ДЛЯ ЧЕРНОГО ЭКРАНА ---
        var blackoutStartY = 0f
        var blackoutLastY = 0f
        var isVolumeSwipe = false
        val volumeSwipeThreshold = 50f // Пикселей на 1 шаг громкости

        blackoutOverlay = TextView(this).apply {
            setBackgroundColor(Color.BLACK); visibility = View.GONE; gravity = Gravity.CENTER

            setOnTouchListener { v, event ->
                val x = event.x
                val y = event.y
                val screenWidth = v.width

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Если касание в правой четверти экрана
                        if (x > (screenWidth * 0.75f)) {
                            isVolumeSwipe = true
                            blackoutStartY = y
                            blackoutLastY = y
                        } else {
                            isVolumeSwipe = false
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isVolumeSwipe) {
                            val deltaY = y - blackoutLastY
                            if (kotlin.math.abs(deltaY) > volumeSwipeThreshold) {
                                if (deltaY < 0) { // Вверх
                                    proxyService?.sendVolumePulse(up = true)
                                } else { // Вниз
                                    proxyService?.sendVolumePulse(up = false)
                                }
                                blackoutLastY = y
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        // Если просто тапнули (палец сдвинулся меньше 15px) или тапнули слева - будим экран
                        if (!isVolumeSwipe || Math.abs(y - blackoutStartY) < 15f) {
                            exitBlackoutMode()
                            resetBlackoutTimer()
                        }
                        isVolumeSwipe = false
                    }
                }
                true
            }
        }

        rootContainer.addView(controlsLayout, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        rootContainer.addView(blackoutOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(rootContainer)
        rootContainer.requestFocus()

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN), 101)
        checkRootAccessAsync(); updateUiState()
    }

    private fun createStyledCheckBox(label: String, checked: Boolean, tint: ColorStateList): CheckBox {
        return CheckBox(this).apply {
            text = label; textSize = 14f; setTextColor(Color.WHITE); isChecked = checked; isFocusable = false; setPadding(16, 16, 16, 16)
            CompoundButtonCompat.setButtonTintList(this, tint)
            setOnCheckedChangeListener { _, _ -> resetBlackoutTimer() }
        }
    }

    private fun checkRootAccessAsync() {
        Thread {
            val hasRoot = RootBtSpoofer.isRootAvailable()
            runOnUiThread {
                if (hasRoot) {
                    rootSwitch.text = "Режим Root (CoD + высокий приоритет BT) [Root ОК]"
                    rootSwitch.isEnabled = !isProxyActive
                } else {
                    rootSwitch.text = "Режим Root недоступен (выдайте права в APatch)"
                    rootSwitch.isChecked = false; rootSwitch.isEnabled = false; rootSwitch.setTextColor(0xFF757575.toInt())
                }
            }
        }.start()
    }

    private fun updateHostStatus(hostName: String?) {
        if (!isProxyActive) {
            hostStatusText.text = "Статус: Выключено (обычный смартфон)"
            hostStatusText.setTextColor(0xFFB0BEC5.toInt())
        } else if (hostName != null) {
            hostStatusText.text = "Подключено к ПК: $hostName"
            hostStatusText.setTextColor(0xFF00E676.toInt())
        } else {
            hostStatusText.text = "Ожидание подключения Bluetooth с ПК..."
            hostStatusText.setTextColor(0xFFFFB74D.toInt())
        }
    }

    private fun resetBlackoutTimer() {
        blackoutHandler.removeCallbacks(blackoutRunnable)
        if (isProxyActive) blackoutHandler.postDelayed(blackoutRunnable, BLACKOUT_TIMEOUT_MS)
    }

    private fun enterBlackoutMode() {
        isBlackoutActive = true; blackoutOverlay.visibility = View.VISIBLE
        val params = window.attributes; params.screenBrightness = 0.01f; window.attributes = params
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, rootContainer).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        rootContainer.requestFocus()
    }

    private fun exitBlackoutMode() {
        isBlackoutActive = false; blackoutOverlay.visibility = View.GONE
        val params = window.attributes; params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; window.attributes = params
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, rootContainer).show(WindowInsetsCompat.Type.systemBars())
        rootContainer.requestFocus()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isProxyActive && !isBlackoutActive && ev.action == MotionEvent.ACTION_DOWN) resetBlackoutTimer()
        return super.dispatchTouchEvent(ev)
    }

    private fun enableProxy() {
        axisLx = 0f; axisLy = 0f; axisRx = 0f; axisRy = 0f
        axisLt = 0f; axisRt = 0f; axisHatX = 0f; axisHatY = 0f
        isL2Pressed = false; isR2Pressed = false

        Intent(this, GamepadProxyService::class.java).also { intent ->
            startForegroundService(intent)
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    private fun disableProxy() {
        blackoutHandler.removeCallbacks(blackoutRunnable); exitBlackoutMode()
        proxyService?.stopProxyAndRestorePhone()
        if (isBound) { unbindService(connection); isBound = false }
        isProxyActive = false; updateHostStatus(null); updateUiState()
    }

    private fun updateUiState() {
        if (rootSwitch.text.contains("Root ОК")) rootSwitch.isEnabled = !isProxyActive
        if (isProxyActive) {
            toggleButton.text = "ВЫКЛЮЧИТЬ И ВЕРНУТЬ СМАРТФОН\n(В фоне: свайп справа = Громкость)"
            toggleButton.setBackgroundColor(0xFFB71C1C.toInt()); toggleButton.setTextColor(Color.WHITE)
        } else {
            toggleButton.text = "ВКЛЮЧИТЬ BTBOX-mz"
            toggleButton.setBackgroundColor(0xFF2E7D32.toInt()); toggleButton.setTextColor(Color.WHITE)
        }
    }

    private fun mapKeyCodeToMask(keyCode: Int): Int {
        val swap = swapAbxySwitch.isChecked
        val code = if (swap) {
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
                KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
                KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
                KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
                else -> keyCode
            }
        } else keyCode

        val triggersAnalogDisabled = triggersAsButtonsSwitch.isChecked

        return if (linuxMappingSwitch.isChecked) {
            when (code) {
                KeyEvent.KEYCODE_BUTTON_A -> 1 shl 0
                KeyEvent.KEYCODE_BUTTON_B -> 1 shl 1
                KeyEvent.KEYCODE_BUTTON_X -> 1 shl 3
                KeyEvent.KEYCODE_BUTTON_Y -> 1 shl 4
                KeyEvent.KEYCODE_BUTTON_L1 -> 1 shl 6
                KeyEvent.KEYCODE_BUTTON_R1 -> 1 shl 7
                KeyEvent.KEYCODE_BUTTON_L2 -> if (triggersAnalogDisabled) (1 shl 8) else 0
                KeyEvent.KEYCODE_BUTTON_R2 -> if (triggersAnalogDisabled) (1 shl 9) else 0
                KeyEvent.KEYCODE_BUTTON_SELECT -> 1 shl 10
                KeyEvent.KEYCODE_BUTTON_START -> 1 shl 11
                KeyEvent.KEYCODE_BUTTON_MODE -> 1 shl 12
                KeyEvent.KEYCODE_BUTTON_THUMBL -> 1 shl 13
                KeyEvent.KEYCODE_BUTTON_THUMBR -> 1 shl 14
                else -> 0
            }
        } else {
            when (code) {
                KeyEvent.KEYCODE_BUTTON_A -> 1 shl 0
                KeyEvent.KEYCODE_BUTTON_B -> 1 shl 1
                KeyEvent.KEYCODE_BUTTON_X -> 1 shl 2
                KeyEvent.KEYCODE_BUTTON_Y -> 1 shl 3
                KeyEvent.KEYCODE_BUTTON_L1 -> 1 shl 4
                KeyEvent.KEYCODE_BUTTON_R1 -> 1 shl 5
                KeyEvent.KEYCODE_BUTTON_L2 -> if (triggersAnalogDisabled) (1 shl 8) else 0 // Кнопка 9
                KeyEvent.KEYCODE_BUTTON_R2 -> if (triggersAnalogDisabled) (1 shl 9) else 0 // Кнопка 10
                KeyEvent.KEYCODE_BUTTON_SELECT -> 1 shl 6
                KeyEvent.KEYCODE_BUTTON_START -> 1 shl 7
                KeyEvent.KEYCODE_BUTTON_THUMBL -> 1 shl 10
                KeyEvent.KEYCODE_BUTTON_THUMBR -> 1 shl 11
                KeyEvent.KEYCODE_BUTTON_MODE -> 1 shl 12
                else -> 0
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isGamepad = (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
                (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)

        if (isGamepad) {
            if (!isProxyActive) return true

            val isPressed = event.action == KeyEvent.ACTION_DOWN
            if (!isPressed && event.action != KeyEvent.ACTION_UP) return true

            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L2) isL2Pressed = isPressed
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_R2) isR2Pressed = isPressed

            val mask = mapKeyCodeToMask(event.keyCode)
            if (mask != 0) {
                proxyService?.updateButton(mask, isPressed)
            }

            sendAxesToService()

            if (!isBlackoutActive) {
                debugKeyText.text = "Ввод: ${KeyEvent.keyCodeToString(event.keyCode)} (${if (isPressed) "DOWN" else "UP"})"
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) &&
            event.action == MotionEvent.ACTION_MOVE) {

            if (!isProxyActive) return true
            val device = event.device ?: return true

            if (device.getMotionRange(MotionEvent.AXIS_X, event.source) != null) axisLx = event.getAxisValue(MotionEvent.AXIS_X)
            if (device.getMotionRange(MotionEvent.AXIS_Y, event.source) != null) axisLy = event.getAxisValue(MotionEvent.AXIS_Y)
            if (device.getMotionRange(MotionEvent.AXIS_Z, event.source) != null) axisRx = event.getAxisValue(MotionEvent.AXIS_Z)
            if (device.getMotionRange(MotionEvent.AXIS_RZ, event.source) != null) axisRy = event.getAxisValue(MotionEvent.AXIS_RZ)

            if (device.getMotionRange(MotionEvent.AXIS_HAT_X, event.source) != null) axisHatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            if (device.getMotionRange(MotionEvent.AXIS_HAT_Y, event.source) != null) axisHatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

            if (device.getMotionRange(MotionEvent.AXIS_LTRIGGER, event.source) != null ||
                device.getMotionRange(MotionEvent.AXIS_BRAKE, event.source) != null) {
                axisLt = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
            }
            if (device.getMotionRange(MotionEvent.AXIS_RTRIGGER, event.source) != null ||
                device.getMotionRange(MotionEvent.AXIS_GAS, event.source) != null) {
                axisRt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
            }

            sendAxesToService()
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun sendAxesToService() {
        val effectiveLt = if (isL2Pressed) 1.0f else axisLt
        val effectiveRt = if (isR2Pressed) 1.0f else axisRt

        val disableAnalogTriggers = triggersAsButtonsSwitch.isChecked
        val finalLt = if (disableAnalogTriggers) 0f else effectiveLt
        val finalRt = if (disableAnalogTriggers) 0f else effectiveRt

        proxyService?.updateAxes(axisLx, axisLy, axisRx, axisRy, finalLt, finalRt, axisHatX, axisHatY, 0, 0)

        if (!isBlackoutActive && (effectiveLt > 0.05f || effectiveRt > 0.05f)) {
            debugKeyText.text = "Курки: LT=${(effectiveLt * 100).toInt()}% | RT=${(effectiveRt * 100).toInt()}%"
        }
    }

    override fun onDestroy() {
        blackoutHandler.removeCallbacks(blackoutRunnable)
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }
}