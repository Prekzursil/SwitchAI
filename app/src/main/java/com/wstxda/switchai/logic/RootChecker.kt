package com.wstxda.switchai.logic

import android.util.Log
import com.wstxda.switchai.utils.Constants
import java.io.File

/**
 * Well-known absolute locations of the su binary. Invoking "su" through a
 * relative name resolves via PATH, which a non-root app cannot fully control;
 * probing these paths pins the executable explicitly.
 */
private val SU_BINARIES = listOf(
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/system/sd/bin/su",
    "/debug_ramdisk/su",
)

private fun resolveSuBinary(): String =
    SU_BINARIES.firstOrNull { File(it).exists() } ?: "/system/bin/su"

fun isRootAvailable(): Boolean = runCatching {
    val process = ProcessBuilder(resolveSuBinary(), "-c", "id").start()
    process.waitFor() == 0
}.getOrElse {
    Log.e(Constants.ROOT_CHECKER, "Root check failed", it)
    false
}

fun launchRootActivity(packageName: String, activityName: String): Boolean = runCatching {
    val process = ProcessBuilder(
        resolveSuBinary(), "-c", "am", "start", "-n", "$packageName/$activityName"
    ).redirectErrorStream(true).start()

    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()

    Log.d(Constants.ROOT_CHECKER, "am start output: $output | exit: $exitCode")
    exitCode == 0
}.getOrElse {
    Log.e(Constants.ROOT_CHECKER, "Failed to launch root activity", it)
    false
}
