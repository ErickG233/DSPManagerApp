package com.cyanogenmod.dspmanager.utils

import android.content.Context
import android.util.TypedValue
import kotlin.math.ceil


/* 像素转换工具类 */
class DimensionConvert {

    companion object {
        fun dimensionToPixels(dip: Float, context: Context): Int {
            return ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, context.resources.displayMetrics)).toInt()
        }

        fun spToPixels(sp: Float, context: Context): Int {
            return ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics).toDouble()).toInt()
        }
    }


}