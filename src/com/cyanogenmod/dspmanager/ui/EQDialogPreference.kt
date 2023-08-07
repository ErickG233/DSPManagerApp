package com.cyanogenmod.dspmanager.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DialogPreference
import androidx.preference.PreferenceViewHolder
import com.cyanogenmod.dspmanager.R
import com.cyanogenmod.dspmanager.utils.EqualizerView

class EQDialogPreference constructor(context: Context, attributeSet: AttributeSet? = null) :
    DialogPreference(context, attributeSet) {

    companion object {
        private const val TAG = "EQDialogPreference"
        private lateinit var mListEqualizerView: EqualizerView
        const val defaultValue = "0.0;0.0;0.0;0.0;0.0;0.0"
    }

    /* 更新EQ值 */
    private fun updateEQValue() {
        val values = getEQValue()
        if (values.isNotEmpty()) {
            val eqLevel = values.split(";")
            for (index in eqLevel.indices) {
                mListEqualizerView.setSingleVisibleBand(index, eqLevel[index].toFloat())
            }
            /* 后台线程更新频谱条 */
            mListEqualizerView.postInvalidate()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        mListEqualizerView = holder.itemView.findViewById(R.id.EQFrequency)
        updateEQValue()
    }

    fun getEQValue(): String {
        return getPersistedString(defaultValue)
    }

    fun writeEQValues(levels: String) {
        persistString(levels)
        updateEQValue()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val values = getPersistedString("").ifEmpty { defaultValue as String }
        if (shouldPersist()) {
            persistString(values)
        }
    }

}