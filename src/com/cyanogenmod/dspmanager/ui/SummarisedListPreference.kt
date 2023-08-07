package com.cyanogenmod.dspmanager.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import com.cyanogenmod.dspmanager.R

/* 列表配置 */
class SummarisedListPreference(context: Context, attrs: AttributeSet?) :
    ListPreference(context, attrs) {
    override fun setValue(value: String?) {
        super.setValue(value)
        val cacheValues = entryValues
        val cacheEntries = entries
        for (i in cacheValues.indices) {
            if (cacheValues[i] == value) {
                summary = if (value == "custom")
                    context.getString(R.string.eq_preset_custom) else cacheEntries[i]
                break
            }
        }
    }

}