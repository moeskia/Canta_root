package io.github.samolego.canta.util.root

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.github.samolego.canta.APP_NAME
import io.github.samolego.canta.util.LogUtils

/**
 * Utility object for performing privileged package operations via root (su) commands.
 * This provides an alternative to Shizuku for devices with root access.
 */
object RootPackageInstallerUtils {
    private const val TAG = "RootPackageInstaller"

    /**
     * Checks if root (su) is available on the device.
     * @return true if su binary is accessible
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes a shell command as root via 'su -c'.
     * @param command the shell command to execute
     * @return Pair of (exitCode, output)
     */
    private fun execSuCommand(command: String): Pair<Int, String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            LogUtils.d(TAG, "Command: $command -> exit=$exitCode, output=$output, error=$error")
            Pair(exitCode, output + error)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to execute su command: $command", e)
            Pair(-1, e.message ?: "Unknown error")
        }
    }

    /**
     * Uninstalls an app using root 'pm uninstall' command.
     *
     * @param packageName package name of the app to uninstall
     * @param isSystem whether the app is a system app
     * @param resetToFactory whether to first reset system app to factory version
     * @param packageManager for querying app info
     * @return true if uninstall was successful
     */
    fun uninstallApp(
        packageName: String,
        isSystem: Boolean,
        resetToFactory: Boolean = false,
        packageManager: PackageManager
    ): Boolean {
        LogUtils.i(
            TAG,
            "Uninstalling '$packageName' via root [system=$isSystem, resetFirst=$resetToFactory]"
        )

        val hasUpdates = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        // Step 1: If reset to factory is requested and the system app has updates,
        // first remove the updates to restore factory version
        if (resetToFactory && isSystem && hasUpdates) {
            LogUtils.i(TAG, "Resetting system app '$packageName' to factory version first")
            // pm install-existing reinstalls the factory version, removing updates
            val (resetExit, resetOutput) = execSuCommand("pm install-existing $packageName")
            if (resetExit == 0 && (resetOutput.contains("Success") || resetOutput.contains("installed"))) {
                LogUtils.i(TAG, "Successfully reset '$packageName' to factory version")
            } else {
                LogUtils.w(TAG, "Failed to reset '$packageName': exit=$resetExit, output=$resetOutput")
                // Continue with uninstall anyway
            }
        }

        // Step 2: Uninstall the app
        // For system apps: use --user 0 to uninstall for current user (safer, doesn't modify /system)
        // For user apps: use --user all to uninstall for all users
        val command = if (isSystem) {
            "pm uninstall --user 0 $packageName"
        } else {
            "pm uninstall --user all $packageName"
        }
        val (exitCode, output) = execSuCommand(command)

        val success = exitCode == 0 && output.contains("Success")
        if (success) {
            LogUtils.i(TAG, "Successfully uninstalled '$packageName' via root")
        } else {
            LogUtils.e(TAG, "Failed to uninstall '$packageName' via root: exit=$exitCode, output=$output")
        }
        return success
    }

    /**
     * Reinstalls (restores) a previously uninstalled system app using root 'pm install-existing'.
     *
     * @param packageName package name of the app to reinstall
     * @return true if reinstall was successful
     */
    fun reinstallApp(packageName: String): Boolean {
        LogUtils.i(TAG, "Reinstalling '$packageName' via root")

        val (exitCode, output) = execSuCommand("pm install-existing $packageName")
        val success = exitCode == 0 &&
                (output.contains("Success") || output.contains("installed"))

        if (success) {
            LogUtils.i(TAG, "Successfully reinstalled '$packageName' via root")
        } else {
            LogUtils.e(TAG, "Failed to reinstall '$packageName' via root: exit=$exitCode, output=$output")
        }
        return success
    }

    /**
     * Checks if a system app can be reset to factory version (has updates installed).
     *
     * @param packageName package name of the app
     * @param packageManager for querying app info
     * @return true if the app is a system app with updates
     */
    fun canResetToFactory(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val hasUpdates = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            isSystem && hasUpdates
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
