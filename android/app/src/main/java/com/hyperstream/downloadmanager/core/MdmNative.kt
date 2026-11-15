package com.hyperstream.downloadmanager.core

object MdmNative {
    val isNativeLoaded: Boolean = try {
        System.loadLibrary("mdm_android")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    external fun init(dataDir: String, downloadsDir: String): Boolean

    @JvmStatic
    external fun executeCommand(cmdJson: String): String

    @JvmStatic
    external fun getSnapshotJson(): String

    @JvmStatic
    external fun getLocalesJson(): String

    fun safeInit(dataDir: String, downloadsDir: String): Boolean {
        return if (isNativeLoaded) {
            runCatching { init(dataDir, downloadsDir) }.getOrDefault(false)
        } else {
            false
        }
    }

    fun safeExecuteCommand(cmdJson: String): String {
        return if (isNativeLoaded) {
            runCatching { executeCommand(cmdJson) }.getOrDefault("""{"ok":false}""")
        } else {
            """{"ok":true,"fallback":true}"""
        }
    }

    fun safeGetSnapshotJson(): String? {
        return if (isNativeLoaded) {
            runCatching { getSnapshotJson() }.getOrNull()
        } else {
            null
        }
    }

    fun safeGetLocalesJson(): String? {
        return if (isNativeLoaded) {
            runCatching { getLocalesJson() }.getOrNull()
        } else {
            null
        }
    }
}
