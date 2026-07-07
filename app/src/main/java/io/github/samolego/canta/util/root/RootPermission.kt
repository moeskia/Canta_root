package io.github.samolego.canta.util.root

import io.github.samolego.canta.util.LogUtils

/**
 * Utility object for checking root (su) availability on the device.
 */
object RootPermission {
    private const val TAG = "RootPermission"

    /**
     * Checks if the device has root access available via 'su' binary.
     * @return true if 'su' command can be executed successfully
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val result = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()

            val hasRoot = result.contains("uid=0") || error.contains("uid=0")
            LogUtils.i(TAG, "Root availability check: $hasRoot (result: $result, error: $error)")
            hasRoot
        } catch (e: Exception) {
            LogUtils.i(TAG, "Root not available: ${e.message}")
            false
        }
    }

    /**
     * Checks if Canta has been granted root permission by attempting a simple command.
     * @return true if root permission is granted
     */
    fun isRootGranted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val result = process.inputStream.bufferedReader().readText()
            process.waitFor()
            result.contains("uid=0")
        } catch (e: Exception) {
            LogUtils.i(TAG, "Root permission check failed: ${e.message}")
            false
        }
    }
}
