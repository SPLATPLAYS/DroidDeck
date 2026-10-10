package com.droiddeck.launcher.gpu

import android.content.Context
import android.os.Build
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.FileUtils
import java.io.File

/**
 * What GPU this is, in the terms the driver lists are sorted by. KGSL names the model
 * ("Adreno740v2", "Adreno825"); the family decides which Turnip builds run on it at all, and the
 * support level is what the app has actually been tested on (Adreno 650, 725 and newer) - an
 * Adreno 610 may start, but it is not "supported" just because it is a Qualcomm chip.
 */
data class GpuInfo(
    /** "Adreno 740", or the vendor's own name when this is not an Adreno. */
    val name: String,
    /** The three-digit Adreno model (740, 825), or 0 when KGSL does not say. */
    val model: Int,
    val family: Family,
    /** The SoC as a person would say it ("Snapdragon 8 Gen 2 (QCS8550)"), or "" when the phone does not say. */
    val soc: String,
    /** Samsung's One UI on an 8 Gen 2: its Turnip needs the OneUI build, or frames tear and flicker. */
    val oneUi8Gen2: Boolean,
    /** KGSL's own name for the GPU, as the kernel spells it ("Adreno740v2", "Adreno33v2"). */
    val kgslName: String = "",
    /** Where [model] came from: "kernel", "vulkan" or "platform", "" when unknown. */
    val modelSource: String = "",
    /** Which GPU this is, as the kernel and the Vulkan loader see it. */
    val vendor: Vendor = Vendor.ADRENO,
) {
    enum class Family(val label: String) {
        A8XX("Adreno 8xx"),
        /** Adreno 710/720/722: gen 7 cores cut down enough to need their own patches. */
        A7XX_LOW("Adreno 710/720/722"),
        A7XX("Adreno 7xx"),
        A6XX("Adreno 6xx"),
        /** An Adreno whose model KGSL does not give: treated as the newest family it could be. */
        ADRENO_UNKNOWN("Adreno"),
        /** Arm Mali (Mali-G715 and the rest of the Tensor generations). */
        MALI("Mali"),
        /** A GPU from neither vendor: PowerVR, Xclipse, or one the app cannot name. */
        OTHER("Not an Adreno or Mali GPU");

        /** [label] in the app's language: the Adreno and Mali families are names, the rest is words. */
        fun label(context: Context): String = when (this) {
            OTHER -> context.getString(R.string.gpuinfo_not_adreno)
            else -> label
        }
    }

    /**
     * The GPU vendor, which is what decides the driver rather than the model: an Adreno needs
     * Turnip over KGSL, a Mali needs PanVK or the vendor ICD over `mali_kbase`, and neither is a
     * fallback for the other. [UNKNOWN] covers a GPU the app can neither name nor drive.
     */
    enum class Vendor {
        ADRENO, MALI, UNKNOWN;

        /** True when this vendor's drivers are the AdrenoTools/Turnip ones the app ships. */
        val adreno: Boolean get() = this == ADRENO
    }

    enum class Support { TESTED, UNTESTED, UNSUPPORTED }

    val support: Support
        get() = when {
            family == Family.OTHER -> Support.UNSUPPORTED
            family == Family.MALI -> Support.UNTESTED
            family == Family.A6XX && model == 650 -> Support.TESTED
            family == Family.A8XX -> Support.TESTED
            family == Family.A7XX && model >= 725 -> Support.TESTED
            else -> Support.UNTESTED
        }

    /** One line for the device card and the system check, in English for the device report. */
    val supportText: String
        get() = when (support) {
            Support.TESTED -> "Supported"
            Support.UNTESTED -> when (family) {
                Family.A7XX_LOW -> "Experimental: its drivers are test builds"
                Family.MALI -> "Experimental: needs a PanVK or vendor Vulkan driver, not Turnip"
                else -> "Outside tested hardware (Adreno 650, 725 and newer): it may not run"
            }
            Support.UNSUPPORTED -> "Not supported: DroidDeck needs an Adreno (Snapdragon) or Mali GPU"
        }

    /** [supportText] in the app's language. */
    fun supportText(context: Context): String = context.getString(when (support) {
        Support.TESTED -> R.string.gpuinfo_supported
        Support.UNTESTED -> when (family) {
            Family.A7XX_LOW -> R.string.gpuinfo_experimental
            Family.MALI -> R.string.gpuinfo_experimental_mali
            else -> R.string.gpuinfo_below_tested
        }
        Support.UNSUPPORTED -> R.string.gpuinfo_unsupported
    })

    /** [name] for the screen: the stand-in for a GPU the device does not name is in the app's language. */
    fun displayName(context: Context): String = if (name == UNNAMED) context.getString(R.string.gpu_this_gpu) else name

    companion object {
        /** [name] when the device names no GPU; English, as the device report shows it. */
        private const val UNNAMED = "this GPU"

        /** The Qualcomm GPU node and the vendor driver that only a Qualcomm stack has. */
        @JvmStatic
        fun adrenoPresent(): Boolean =
            File("/sys/class/kgsl/kgsl-3d0").exists() || File("/vendor/lib64/hw/vulkan.adreno.so").exists()

        /** The kbase node a Mali is driven through; the number is the GPU's, not a fixed 0. */
        fun maliNode(): String? = File("/sys/class/misc").listFiles()
            ?.map { it.name }
            ?.firstOrNull { it.startsWith("mali") && it.substringAfter("mali").all(Char::isDigit) }
            ?.let { "/dev/$it" }
            ?: if (File("/dev/mali0").exists()) "/dev/mali0" else null

        fun maliPresent(): Boolean = maliNode() != null ||
            File("/sys/class/devfreq").listFiles()?.any { it.name.contains("mali", ignoreCase = true) } == true ||
            systemProperty("ro.hardware.egl").contains("mali", ignoreCase = true)

        /**
         * The vendor, from what only each vendor's stack has: an Adreno is a KGSL node or
         * Qualcomm's own Vulkan driver, a Mali is a kbase node or a Mali devfreq node.
         */
        fun detectVendor(): Vendor = when {
            adrenoPresent() -> Vendor.ADRENO
            maliPresent() -> Vendor.MALI
            else -> Vendor.UNKNOWN
        }

        fun detect(): GpuInfo {
            val vendor = detectVendor()
            val adreno = vendor == Vendor.ADRENO
            val raw = listOf("/sys/class/kgsl/kgsl-3d0/gpu_model", "/sys/class/kgsl/kgsl-3d0/gpu_chipid")
                .firstNotNullOfOrNull { FileUtils.readString(File(it))?.trim()?.takeIf(String::isNotEmpty) }
            // Where vendors put the chip's model, named when it is known: "Snapdragon 8 Gen 2 (QCS8550)".
            val soc = when {
                adreno -> SocNames.label()
                else -> socLabel()
            }
            val fromKernel = raw?.let { threeDigits(it) } ?: 0
            // Some kernels name the GPU without its model (AYANEO's Pocket FIT: "Adreno33v2"). The
            // Vulkan driver's own name, when this process has asked it, then the platform's code
            // name stand in - the GPU is the same on every phone of a platform.
            val fromVulkan = if (fromKernel > 0 || !adreno) 0 else VulkanInfo.cachedOrNull()?.get("device")?.let { threeDigits(it) } ?: 0
            val fromPlatform = if (fromKernel > 0 || fromVulkan > 0 || !adreno) 0 else platformModel()
            val model = maxOf(fromKernel, fromVulkan, fromPlatform)
            val source = when {
                fromKernel > 0 -> "kernel"
                fromVulkan > 0 -> "vulkan"
                fromPlatform > 0 -> "platform"
                else -> ""
            }
            val family = familyOf(adreno, model, vendor)
            val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            return GpuInfo(
                name = nameOf(vendor, model), model = model, family = family, soc = soc,
                oneUi8Gen2 = samsung && model == 740,
                kgslName = raw.orEmpty(), modelSource = source, vendor = vendor,
            )
        }

        /**
         * The GPU as the screen shows it. An Adreno is named by its model; every other vendor is
         * named by what the Vulkan driver reports for itself, which is the only place a Mali names
         * its own model ("Mali-G715-Immortalis MC7").
         */
        private fun nameOf(vendor: Vendor, model: Int): String = when (vendor) {
            Vendor.ADRENO -> if (model > 0) "Adreno $model" else "Adreno"
            Vendor.MALI -> VulkanInfo.cachedOrNull()?.get("device")?.takeIf { it.isNotBlank() }
                ?: Build.HARDWARE.ifBlank { UNNAMED }
            Vendor.UNKNOWN -> Build.HARDWARE.ifBlank { UNNAMED }
        }

        /** The SoC on a non-Qualcomm chip: Android's own name, which is the only one there is. */
        private fun socLabel(): String =
            if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: "" else ""

        private fun threeDigits(text: String): Int = Regex("""(\d{3})""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        /** Qualcomm platform code names whose GPU is known for certain. */
        private val PLATFORMS = mapOf(
            "msmnile" to 640, "kona" to 650, "lahaina" to 660, "taro" to 730, "cape" to 730,
            "kalama" to 740, "pineapple" to 750, "sun" to 830,
        )

        internal fun platformModel(platform: String = systemProperty("ro.board.platform")): Int =
            PLATFORMS[platform.lowercase()] ?: PLATFORMS[systemProperty("ro.vendor.qti.soc_name").lowercase()] ?: 0

        /** android.os.SystemProperties.get, which apps may read but not call directly. */
        fun systemProperty(name: String): String = try {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as? String ?: ""
        } catch (_: Exception) {
            ""
        }

        internal fun familyOf(adreno: Boolean, model: Int, vendor: Vendor = if (adreno) Vendor.ADRENO else Vendor.UNKNOWN): Family = when {
            vendor == Vendor.MALI -> Family.MALI
            !adreno -> Family.OTHER
            model == 0 -> Family.ADRENO_UNKNOWN
            model >= 800 -> Family.A8XX
            model in listOf(710, 720, 722) -> Family.A7XX_LOW
            model >= 700 -> Family.A7XX
            model >= 600 -> Family.A6XX
            else -> Family.ADRENO_UNKNOWN
        }
    }
}
