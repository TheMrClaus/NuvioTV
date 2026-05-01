package com.omnio.phone.diag

import android.content.Context
import android.os.Build
import com.omnio.phone.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions to a file so sideloaded beta users without ADB
 * can surface a stack trace through the in-app dialog. Always re-throws to the
 * platform default handler so the app still crashes naturally — we just leave
 * a breadcrumb behind.
 */
internal object CrashReporter {

    private const val DIR = "crash"
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrash(context: Context): String? {
        val file = crashFile(context)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun clearLastCrash(context: Context) {
        val file = crashFile(context)
        runCatching { if (file.exists()) file.delete() }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val file = crashFile(context)
        file.parentFile?.mkdirs()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        file.writeText(
            buildString {
                append("OmnioTV Phone ")
                append(BuildConfig.VERSION_NAME)
                append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                append("Time: ").append(timestamp).append('\n')
                append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                append(" / Android ").append(Build.VERSION.RELEASE)
                append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                append("Thread: ").append(thread.name).append('\n')
                append('\n')
                append(sw.toString())
            }
        )
    }

    private fun crashFile(context: Context): File =
        File(File(context.cacheDir, DIR), FILE)
}
