package com.skd.notebook.util

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/** "1.0 (build 3)" — read from the installed package so it always matches the published build. */
fun Context.appVersionLabel(): String = runCatching {
    val info = packageManager.getPackageInfo(packageName, 0)
    "${info.versionName} "
}.getOrDefault("1.0")

fun Context.appVersionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "1.0"
