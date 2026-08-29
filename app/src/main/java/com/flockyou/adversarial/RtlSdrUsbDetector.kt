package com.flockyou.adversarial

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build

data class RtlSdrCapability(
    val usbHostSupported: Boolean,
    val device: UsbDevice? = null,
    val permissionGranted: Boolean = false,
    val nativeBackendAvailable: Boolean = false,
    val status: String,
    val proofBoundary: String = "USB hardware presence alone is not ADS-B reception"
) {
    val readyForAdsB: Boolean get() = device != null && permissionGranted && nativeBackendAvailable
}

object RtlSdrUsbDetector {
    const val ACTION_USB_PERMISSION = "com.flockyou.action.RTL_SDR_USB_PERMISSION"

    // Common RTL2832U / RTL-SDR Blog / FlightAware VID:PID combinations.
    private val supportedIds = setOf(
        0x0bda to 0x2832,
        0x0bda to 0x2838,
        0x0bda to 0x283e,
        0x1209 to 0x2832,
        0x1d50 to 0x6089
    )

    fun detect(context: Context): RtlSdrCapability {
        val usbHost = context.packageManager.hasSystemFeature("android.hardware.usb.host")
        if (!usbHost) {
            return RtlSdrCapability(false, status = "USB host/OTG unavailable")
        }
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = manager.deviceList.values.firstOrNull { isSupported(it) }
            ?: return RtlSdrCapability(true, status = "No supported RTL2832U SDR attached")
        val permission = manager.hasPermission(device)
        val nativeAvailable = RtlSdrNativeBridge.isAvailable
        val status = when {
            !permission -> "RTL-SDR detected — USB permission required"
            !nativeAvailable -> "RTL-SDR detected — Android-native I/Q backend not bundled"
            else -> "RTL-SDR ready for local 1090 MHz ADS-B"
        }
        return RtlSdrCapability(
            usbHostSupported = true,
            device = device,
            permissionGranted = permission,
            nativeBackendAvailable = nativeAvailable,
            status = status
        )
    }

    fun requestPermission(context: Context, device: UsbDevice) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pending = PendingIntent.getBroadcast(
            context,
            1090,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags
        )
        manager.requestPermission(device, pending)
    }

    fun isSupported(device: UsbDevice): Boolean =
        (device.vendorId to device.productId) in supportedIds
}

/**
 * Native backend load gate. It intentionally fails closed until an Android-compatible
 * RTL2832U backend that accepts Android's brokered USB file descriptor is packaged.
 *
 * Desktop librtlsdr's rtlsdr_open(index) cannot safely be treated as equivalent on a
 * stock sideloaded Android app. This gate prevents "dongle found" from being mislabeled
 * as decoded ADS-B.
 */
object RtlSdrNativeBridge {
    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("flock_rtlsdr")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
