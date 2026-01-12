package com.hanto.ridesync.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

fun View.applyWindowInsets(applyTop: Boolean = true, applyBottom: Boolean = true) {
    val initialPaddingTop = paddingTop
    val initialPaddingBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

        v.updatePadding(
            top = if (applyTop) initialPaddingTop + bars.top else initialPaddingTop,
            bottom = if (applyBottom) initialPaddingBottom + bars.bottom else initialPaddingBottom
        )
        insets
    }
}