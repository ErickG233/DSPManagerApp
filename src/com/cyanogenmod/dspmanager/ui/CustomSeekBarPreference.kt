package com.cyanogenmod.dspmanager.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SeekBarPreference
import com.cyanogenmod.dspmanager.utils.PreferenceConfig

class CustomSeekBarPreference(context: Context, attrs: AttributeSet?) :
    SeekBarPreference(context, attrs) {

    override fun setValue(seekBarValue: Int) {
        super.setValue(seekBarValue)
        summary = if (key == "dsp.${PreferenceConfig.getPrefPage()}.bass.boost.freq.point")
            "$seekBarValue Hz"
        else {
            val percentage = ((seekBarValue - min).toFloat() / (max - min) * 100).toInt()
            "$percentage %"
        }
    }

}
