package com.droiddeck.launcher.gpu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which libraries the Linux runtime driver importer accepts. Turnip is the Adreno driver, PanVK the
 * Mali one, and lavapipe the software fallback the runtime already ships; the check is what tells
 * a runtime driver apart from the AdrenoTools and Wayland zips it must refuse.
 */
class RuntimeDriverNameTest {
    @Test
    fun turnipAndPanvkAreBothRuntimeDrivers() {
        assertTrue(LinuxVulkanDriverManager.isRuntimeDriverName("libvulkan_freedreno.so"))
        assertTrue(LinuxVulkanDriverManager.isRuntimeDriverName("libvulkan_panvk.so"))
        assertTrue(LinuxVulkanDriverManager.isRuntimeDriverName("libvulkan_panfrost.so"))
        assertTrue(LinuxVulkanDriverManager.isRuntimeDriverName("libvulkan_lvp.so"))
    }

    @Test
    fun aSuffixedTurnipIsStillTurnip() {
        assertTrue(LinuxVulkanDriverManager.isRuntimeDriverName("libvulkan_freedreno_g610.so"))
    }

    @Test
    fun whatIsNotAVulkanDriverIsRefused() {
        assertFalse(LinuxVulkanDriverManager.isRuntimeDriverName("libvulkan_adreno.so"))
        assertFalse(LinuxVulkanDriverManager.isRuntimeDriverName("vulkan.adreno.so"))
        assertFalse(LinuxVulkanDriverManager.isRuntimeDriverName("libEGL_mali.so"))
        assertFalse(LinuxVulkanDriverManager.isRuntimeDriverName("icd.json"))
        assertFalse(LinuxVulkanDriverManager.isRuntimeDriverName("meta.json"))
    }

    @Test
    fun aNameIsNeededBeforeItCanBeJudged() {
        assertFalse(LinuxVulkanDriverManager.isRuntimeDriverName(null))
        assertFalse(LinuxVulkanDriverManager.isRuntimeDriverName(""))
        // A prefix match on its own is not enough: it has to be a shared library.
        assertFalse(LinuxVulkanDriverManager.isRuntimeDriverName("libvulkan_panvk"))
    }
}