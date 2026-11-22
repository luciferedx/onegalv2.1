package com.onegal.gallery.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

sealed interface UsbDriveEvent {
    data class Attached(val deviceName: String) : UsbDriveEvent
    data class Detached(val deviceName: String) : UsbDriveEvent
    data class MediaMounted(val path: String?) : UsbDriveEvent
    data class MediaUnmounted(val path: String?) : UsbDriveEvent
}

/**
 * Plug-and-play receiver listening for USB-C / NVMe mass storage attachment and volume mount events.
 */
class UsbDriveReceiver(
    private val onEvent: (UsbDriveEvent) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = extractUsbDevice(intent)
                if (device == null || isMassStorageDevice(device)) {
                    val label = device?.productName ?: device?.manufacturerName ?: "USB-C Storage"
                    onEvent(UsbDriveEvent.Attached(label))
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = extractUsbDevice(intent)
                val label = device?.productName ?: device?.manufacturerName ?: "USB-C Storage"
                onEvent(UsbDriveEvent.Detached(label))
            }
            Intent.ACTION_MEDIA_MOUNTED -> {
                onEvent(UsbDriveEvent.MediaMounted(intent.data?.path))
            }
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_BAD_REMOVAL -> {
                onEvent(UsbDriveEvent.MediaUnmounted(intent.data?.path))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    private fun isMassStorageDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                return true
            }
        }
        return false
    }

    companion object {
        fun register(context: Context, receiver: UsbDriveReceiver) {
            val usbFilter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            val mediaFilter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                addDataScheme("file")
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                usbFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            ContextCompat.registerReceiver(
                context,
                receiver,
                mediaFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }

        fun unregister(context: Context, receiver: UsbDriveReceiver) {
            runCatching { context.unregisterReceiver(receiver) }
        }

        fun openAndroidStorageSettings(context: Context) {
            val intents = listOf(
                Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                Intent(Settings.ACTION_MEMORY_CARD_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            for (intent in intents) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return
                }
            }
        }
    }
}
