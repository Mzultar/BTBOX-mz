package com.root.xinputbtproxy

object XboxHidDescriptor {
    const val REPORT_ID: Byte = 0x01
    const val VOLUME_REPORT_ID: Byte = 0x02

    val DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),       // Usage Page (Generic Desktop Ctrls)
        0x09.toByte(), 0x05.toByte(),       // Usage (Game Pad)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), REPORT_ID,           //   Report ID (1)

        // 16 Кнопок
        0x05.toByte(), 0x09.toByte(),       //   Usage Page (Button)
        0x19.toByte(), 0x01.toByte(),       //   Usage Minimum (0x01)
        0x29.toByte(), 0x10.toByte(),       //   Usage Maximum (0x10)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),       //   Report Size (1)
        0x95.toByte(), 0x10.toByte(),       //   Report Count (16)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data,Var,Abs)

        // Крестовина
        0x05.toByte(), 0x01.toByte(),       //   Usage Page (Generic Desktop Ctrls)
        0x09.toByte(), 0x39.toByte(),       //   Usage (Hat switch)
        0x15.toByte(), 0x01.toByte(),       //   Logical Minimum (1)
        0x25.toByte(), 0x08.toByte(),       //   Logical Maximum (8)
        0x35.toByte(), 0x00.toByte(),       //   Physical Minimum (0)
        0x46.toByte(), 0x3B.toByte(), 0x01.toByte(), // Physical Maximum (315)
        0x65.toByte(), 0x14.toByte(),       //   Unit (Eng Rot:Angular Pos)
        0x75.toByte(), 0x04.toByte(),       //   Report Size (4)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x81.toByte(), 0x42.toByte(),       //   Input (Data,Var,Abs,Null)
        0x75.toByte(), 0x04.toByte(),       //   Padding 4 bit
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x81.toByte(), 0x03.toByte(),       //   Input (Const,Var,Abs)

        // 4 Оси СТИКОВ: X, Y (Левый) и Z, Rz (Правый)
        0x05.toByte(), 0x01.toByte(),       //   Usage Page (Generic Desktop Ctrls)
        0x09.toByte(), 0x30.toByte(),       //   Usage (X)
        0x09.toByte(), 0x31.toByte(),       //   Usage (Y)
        0x09.toByte(), 0x32.toByte(),       //   Usage (Z)
        0x09.toByte(), 0x35.toByte(),       //   Usage (Rz)
        0x15.toByte(), 0x81.toByte(),       //   Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),       //   Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x95.toByte(), 0x04.toByte(),       //   Report Count (4)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data,Var,Abs)

        // 2 Оси ТРИГГЕРОВ (Simulation Controls: Brake и Accelerator, 0..255)
        0x05.toByte(), 0x02.toByte(),       //   Usage Page (Simulation Controls)
        0x09.toByte(), 0xC5.toByte(),       //   Usage Brake (LT)
        0x09.toByte(), 0xC4.toByte(),       //   Usage Accelerator (RT)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), // Logical Maximum (255)
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x95.toByte(), 0x02.toByte(),       //   Report Count (2)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data,Var,Abs)
        0xC0.toByte(),                      // End Collection (Gamepad)

        // --- УПРАВЛЕНИЕ ГРОМКОСТЬЮ (Consumer Control) ---
        0x05.toByte(), 0x0C.toByte(),       // Usage Page (Consumer)
        0x09.toByte(), 0x01.toByte(),       // Usage (Consumer Control)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), VOLUME_REPORT_ID,    //   Report ID (2)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),       //   Report Size (1 bit)
        0x95.toByte(), 0x02.toByte(),       //   Report Count (2)
        0x09.toByte(), 0xE9.toByte(),       //   Usage (Volume Increment / UP)
        0x09.toByte(), 0xEA.toByte(),       //   Usage (Volume Decrement / DOWN)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data,Var,Abs)
        0x75.toByte(), 0x06.toByte(),       //   Report Size (6 bits padding)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x81.toByte(), 0x03.toByte(),       //   Input (Const,Var,Abs)
        0xC0.toByte(),                      // End Collection
    )
}