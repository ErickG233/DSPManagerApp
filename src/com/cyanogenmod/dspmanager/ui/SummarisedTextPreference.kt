package com.cyanogenmod.dspmanager.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference

class SummarisedTextPreference(context: Context, attributeSet: AttributeSet) :
    EditTextPreference(context, attributeSet) {

    /* 重写设置文字方法 */
    override fun setText(text: String?) {
        super.setText(text)
    }
}