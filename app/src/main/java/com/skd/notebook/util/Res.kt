package com.skd.notebook.util

import android.content.Context
import androidx.annotation.DimenRes
import androidx.annotation.StringRes

/**
 * One place to read the app's text and sizes from code.
 *
 * Every string lives in res/values/strings.xml and every size (dp, sp) in res/values/dimens.xml; layouts
 * use them as @string/... and @dimen/.... Code reads them the same way:
 *  - [Str] for text, from anywhere (also where there is no Context at hand, such as enums or fields);
 *  - [dimen] / [dimenPx] on a Context for sizes.
 */
object Str {
    private lateinit var appContext: Context

    /** Called once from the Application. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    operator fun invoke(@StringRes id: Int): String = appContext.getString(id)

    operator fun invoke(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)
}

/** A size from dimens.xml in pixels, as a float (for drawing). */
fun Context.dimen(@DimenRes id: Int): Float = resources.getDimension(id)

/** A size from dimens.xml in whole pixels (for view sizes and padding). */
fun Context.dimenPx(@DimenRes id: Int): Int = resources.getDimensionPixelSize(id)
