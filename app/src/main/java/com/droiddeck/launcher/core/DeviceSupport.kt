package com.droiddeck.launcher.core

import android.content.Context
import android.os.Build
import com.droiddeck.launcher.R
import com.droiddeck.launcher.gpu.GpuInfo
import java.io.File

/**
 * Whether this device can draw a Linux session at all.
 *
 * The runtime draws with a Vulkan driver, and which one is the GPU vendor's business, not the
 * app's: an Adreno draws with Turnip, a Mali with PanVK or the vendor's own ICD. What the app
 * cannot do is pick a driver for a GPU it does not recognise at all - on such a device the
 * compositor gets no usable Vulkan device and a session comes up as sound over a black screen, a
 * failure with nothing in it to read. So the app says so before the download rather than after it.
 * Each vendor is recognised by what only its own stack has: Qualcomm's KGSL node or vendor driver,
 * Arm's kbase node or Mali devfreq node.
 */
object DeviceSupport {
    fun vendor(): GpuInfo.Vendor = GpuInfo.detectVendor()

    /** True when the device runs a GPU the app has a driver story for. */
    fun supported(): Boolean = vendor() != GpuInfo.Vendor.UNKNOWN

    /** The Adreno-only question the driver importer and the driver lists still ask. */
    fun adreno(): Boolean = GpuInfo.adrenoPresent()

    /** The chip as the device names it, for the card that explains the refusal. */
    fun gpuName(context: Context): String {
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } else null
        return soc?.let { "$it (${Build.HARDWARE})" } ?: Build.HARDWARE.ifBlank { context.getString(R.string.gpu_this_gpu) }
    }

    /** False on the Odin 3 (CQ8725S): a low-latency Wi-Fi lock makes its WLAN firmware hang, and
     *  that firmware reboots the whole device instead of restarting Wi-Fi. */
    fun lowLatencyWifiSafe(): Boolean = Build.VERSION.SDK_INT < 31 || Build.SOC_MODEL != "CQ8725S"
}
