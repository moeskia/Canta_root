package io.github.samolego.canta

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.IPackageInstaller
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.data.SettingsStore
import io.github.samolego.canta.extension.getInfoForPackage
import io.github.samolego.canta.ui.CantaApp
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.root.RootPackageInstallerUtils
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku

const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
const val APP_NAME = "Canta"
const val packageName = "io.github.samolego.canta"

/**
 * Enum representing the privileged mode for package operations.
 */
enum class PrivilegedMode {
    AUTO,       // Try Shizuku first, fall back to root
    SHIZUKU,    // Use Shizuku only
    ROOT        // Use root (su) only
}

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)

        setContent {
            CantaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val settingsStore = SettingsStore.getInstance()
                    val privilegedMode by settingsStore.privilegedModeFlow.collectAsStateWithLifecycle(initialValue = 0)

                    CantaApp(
                        privilegedMode = privilegedMode,
                        uninstallApp = { packageName, resetToFactory ->
                            uninstallApp(packageName, resetToFactory, PrivilegedMode.entries[privilegedMode])
                        },
                        canResetAppToFactory = { packageName ->
                            checkIfCanResetToFactory(packageName, PrivilegedMode.entries[privilegedMode])
                        },
                        reinstallApp = { reinstallApp(it, PrivilegedMode.entries[privilegedMode]) },
                        closeApp = { finishAndRemoveTask() },
                    )
                }
            }
        }
    }

    /**
     * Checks if an app can be reset to factory version.
     * @param packageName package name of the app to check
     * @param mode the privileged mode to use
     * @return true if the app is a system app with updates
     */
    private fun checkIfCanResetToFactory(packageName: String, mode: PrivilegedMode): Boolean {
        return when (mode) {
            PrivilegedMode.ROOT -> RootPackageInstallerUtils.canResetToFactory(packageName, packageManager)
            else -> {
                val appInfo = packageManager.getInfoForPackage(packageName)?.applicationInfo ?: return false
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val hasUpdates = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                isSystem && hasUpdates
            }
        }
    }

    /**
     * Uninstalls app using Shizuku or root.
     * @param packageName package name of the app to uninstall
     * @param resetToFactory whether to reset system app to factory version before uninstall
     * @param mode the privileged mode to use (AUTO, SHIZUKU, or ROOT)
     */
    private fun uninstallApp(packageName: String, resetToFactory: Boolean = false, mode: PrivilegedMode = PrivilegedMode.AUTO): Boolean {
        // Determine which method to use based on mode and availability
        val useRoot = when (mode) {
            PrivilegedMode.ROOT -> true
            PrivilegedMode.SHIZUKU -> false
            PrivilegedMode.AUTO -> {
                // Try Shizuku first, fall back to root
                !ShizukuPermission.isCantaAuthorized() && RootPackageInstallerUtils.isRootAvailable()
            }
        }

        if (useRoot) {
            val packageInfo = packageManager.getInfoForPackage(packageName) ?: return false
            val isSystem = (packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            return RootPackageInstallerUtils.uninstallApp(packageName, isSystem, resetToFactory, packageManager)
        }

        // Shizuku implementation (for SHIZUKU mode or AUTO mode with Shizuku available)
        val packageInfo = packageManager.getInfoForPackage(packageName) ?: return false
        val isSystem = (packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val hasUpdates =
            (packageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        val shouldReset = resetToFactory && isSystem && hasUpdates
        LogUtils.i(
            APP_NAME,
            "Uninstalling '$packageName' [system: $isSystem, hasUpdates: $hasUpdates, resetFirst: $shouldReset]"
        )
        val broadcastIntent = Intent("io.github.samolego.canta.UNINSTALL_RESULT_ACTION")
        val intent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val packageInstaller = getPackageInstaller()

        // 0x00000004 = PackageManager.DELETE_SYSTEM_APP
        // 0x00000002 = PackageManager.DELETE_ALL_USERS
        val flags = if (isSystem) 0x00000004 else 0x00000002

        if (shouldReset) {
            try {
                LogUtils.i(
                    APP_NAME,
                    "Attempting to reset system app '$packageName' before uninstalling"
                )


                HiddenApiBypass.invoke(
                    PackageInstaller::class.java,
                    packageInstaller,
                    "uninstall",
                    packageName,
                    flags,
                    intent.intentSender
                )

                LogUtils.i(APP_NAME, "Successfully reset system app '$packageName'")

                try {
                    val updatedPackageInfo =
                        packageManager.getInfoForPackage(packageName) ?: return false
                    val stillHasUpdates =
                        (updatedPackageInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    LogUtils.i(APP_NAME, "After reset: Package still has updates: $stillHasUpdates")
                } catch (e: Exception) {
                    LogUtils.e(APP_NAME, "Failed to check update status after reset: ${e.message}")
                }

            } catch (e: Exception) {
                LogUtils.e(APP_NAME, "Failed to reset system app: ${e.message}")
                LogUtils.w(APP_NAME, "Falling back to user uninstall")
            }
        }



        return try {
            HiddenApiBypass.invoke(
                PackageInstaller::class.java,
                packageInstaller,
                "uninstall",
                packageName,
                flags,
                intent.intentSender
            )
            true
        } catch (e: Exception) {
            LogUtils.e(APP_NAME, "Failed to uninstall '$packageName'")
            LogUtils.e(APP_NAME, "Error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Reinstalls app using Shizuku or root. See <a
     * href="https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/pm/PackageManagerShellCommand.java;drc=bcb2b436bde55ee40050400783a9c083e77ce2fe;l=1408>PackageManagerShellCommand.java</a>
     * @param packageName package name of the app to reinstall (must preinstalled on the phone)
     * @param mode the privileged mode to use (AUTO, SHIZUKU, or ROOT)
     */
    private fun reinstallApp(packageName: String, mode: PrivilegedMode = PrivilegedMode.AUTO): Boolean {
        // Determine which method to use based on mode and availability
        val useRoot = when (mode) {
            PrivilegedMode.ROOT -> true
            PrivilegedMode.SHIZUKU -> false
            PrivilegedMode.AUTO -> {
                // Try Shizuku first, fall back to root
                !ShizukuPermission.isCantaAuthorized() && RootPackageInstallerUtils.isRootAvailable()
            }
        }

        if (useRoot) {
            return RootPackageInstallerUtils.reinstallApp(packageName)
        }

        // Shizuku implementation (for SHIZUKU mode or AUTO mode with Shizuku available)
        val installReason = PackageManager.INSTALL_REASON_UNKNOWN
        val broadcastIntent = Intent("io.github.samolego.canta.INSTALL_RESULT_ACTION")
        val intent =
            PendingIntent.getBroadcast(
                applicationContext,
                0,
                broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        LogUtils.i(APP_NAME, "Reinstalling '$packageName'")

        // PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS
        val installFlags = 0x00400000

        return try {
            HiddenApiBypass.invoke(
                IPackageInstaller::class.java,
                ShizukuPackageInstallerUtils.getPrivilegedPackageInstaller(),
                "installExistingPackage",
                packageName,
                installFlags,
                installReason,
                intent.intentSender,
                0,
                null
            )
            true
        } catch (e: Exception) {
            LogUtils.e(APP_NAME, "Failed to reinstall '$packageName'")
            LogUtils.e(APP_NAME, "Error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun getPackageInstaller(): PackageInstaller {
        val iPackageInstaller = ShizukuPackageInstallerUtils.getPrivilegedPackageInstaller()
        val root = Shizuku.getUid() == 0
        val userId = if (root) android.os.Process.myUserHandle().hashCode() else 0

        // The reason for use "com.android.shell" as installer package under adb is that
        // getMySessions will check installer package's owner
        return ShizukuPackageInstallerUtils.createPackageInstaller(
            iPackageInstaller,
            "com.android.shell",
            userId,
            this
        )
    }
}
