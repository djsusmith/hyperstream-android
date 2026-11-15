package com.hyperstream.downloadmanager.shizuku

import java.io.File

sealed class ShizukuResult {
    data class Success(val output: String) : ShizukuResult()
    data class Failure(val exitCode: Int, val error: String) : ShizukuResult()
    data class Unavailable(val reason: String) : ShizukuResult()
}

class ShizukuManager(
    val enabled: Boolean = true,
    private val packageName: String = "com.hyperstream.downloadmanager",
    private val commandRunner: (String) -> Pair<Int, String> = ::defaultRishRunner
) {
    fun isReady(): Boolean {
        if (!enabled) return false
        val (code, out) = commandRunner("id")
        return code == 0 && (out.contains("uid=2000") || out.contains("uid=0"))
    }

    fun whitelistFromDoze(): ShizukuResult {
        if (!enabled) return ShizukuResult.Unavailable("Shizuku mode is disabled in HyperStream settings")
        val (code, out) = commandRunner("dumpsys deviceidle whitelist +$packageName")
        return if (code == 0) {
            ShizukuResult.Success(out.trim())
        } else if (code < 0) {
            ShizukuResult.Unavailable(out)
        } else {
            ShizukuResult.Failure(code, out)
        }
    }

    private fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    fun silentInstallApk(apkPath: String): ShizukuResult {
        if (!enabled) return ShizukuResult.Unavailable("Shizuku mode is disabled in HyperStream settings")
        val safePath = escapeShellArg(apkPath)
        val (code, out) = commandRunner("pm install -r $safePath")
        return if (code == 0 && out.contains("Success", ignoreCase = true)) {
            ShizukuResult.Success(out.trim())
        } else if (code < 0) {
            ShizukuResult.Unavailable(out)
        } else {
            ShizukuResult.Failure(code, out)
        }
    }

    fun moveToRestrictedDir(src: String, dest: String): ShizukuResult {
        if (!enabled) return ShizukuResult.Unavailable("Shizuku mode is disabled in HyperStream settings")
        val safeSrc = escapeShellArg(src)
        val safeDest = escapeShellArg(dest)
        val (code, out) = commandRunner("cp -f $safeSrc $safeDest")
        return if (code == 0) {
            ShizukuResult.Success(out.trim())
        } else if (code < 0) {
            ShizukuResult.Unavailable(out)
        } else {
            ShizukuResult.Failure(code, out)
        }
    }

    fun runHelperBinary(bin: String, args: List<String>): ShizukuResult {
        if (!enabled) return ShizukuResult.Unavailable("Shizuku mode is disabled in HyperStream settings")
        val cmd = buildString {
            append(bin)
            args.forEach { arg ->
                append(" ")
                append(escapeShellArg(arg))
            }
        }
        val (code, out) = commandRunner(cmd)
        return if (code == 0) {
            ShizukuResult.Success(out.trim())
        } else if (code < 0) {
            ShizukuResult.Unavailable(out)
        } else {
            ShizukuResult.Failure(code, out)
        }
    }

    companion object {
        fun candidateRishPaths(): List<String> = listOf(
            "/data/user/0/com.agy/files/usr/bin/rish",
            "/data/data/com.termux/files/usr/bin/rish",
            "rish"
        )

        fun defaultRishRunner(command: String): Pair<Int, String> {
            val binary = candidateRishPaths().firstOrNull { path ->
                path == "rish" || File(path).canExecute()
            } ?: "rish"
            return try {
                val proc = ProcessBuilder(binary, "-c", command)
                    .redirectErrorStream(true)
                    .start()
                val output = proc.inputStream.bufferedReader().readText()
                val exit = proc.waitFor()
                exit to output
            } catch (e: Throwable) {
                -1 to (e.message ?: "Shizuku rish unavailable")
            }
        }
    }
}
