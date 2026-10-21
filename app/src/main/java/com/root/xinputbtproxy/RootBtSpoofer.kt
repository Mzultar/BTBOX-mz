package com.root.xinputbtproxy

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootBtSpoofer {

    // Проверка, выдан ли Root в APatch / Magisk
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            process.waitFor()
            output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    fun setGamepadMode(enable: Boolean): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            if (enable) {
                os.writeBytes("setenforce 0\n")
                os.writeBytes("setprop persist.bluetooth.Metrics.class_of_device 0x002508\n")
                // Повышаем приоритет системного процесса Bluetooth для минимальной задержки
                os.writeBytes("""renice -n -15 -p ${'$'}(pidof com.android.bluetooth)""" + "\n")
            } else {
                os.writeBytes("setprop persist.bluetooth.Metrics.class_of_device 0x5A020C\n")
                os.writeBytes("setenforce 1\n")
            }

            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}