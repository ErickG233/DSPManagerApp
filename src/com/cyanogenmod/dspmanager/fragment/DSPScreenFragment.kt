package com.cyanogenmod.dspmanager.fragment

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
// import com.cyanogenmod.dspmanager.R
import com.cyanogenmod.dspmanager.service.EffectService
import com.cyanogenmod.dspmanager.ui.EQDialogPreference
import com.cyanogenmod.dspmanager.ui.EQDialogPreferenceFragment
import com.cyanogenmod.dspmanager.ui.SummarisedListPreference
import com.cyanogenmod.dspmanager.utils.PreferenceConfig

class DSPScreenFragment: PreferenceFragmentCompat(), ServiceConnection {
    /* 判断是否为预设的EQ更改 */
    private var eqUpdateFromPresets = false

    /* 音频服务绑定 */
//    private var effectBinder: EffectService.EffectBinder ?= null
    /* 音频服务 */
    private var effectService: EffectService ?= null
    companion object {
        private const val TAG = "DSPScreenFragment"
    }

    /* 监听均衡器配置更新 */
    private val prefEqualizerChangedListener = OnSharedPreferenceChangeListener { pref, key ->
        /* 监听EQ均衡器 */
        /* EQ均衡器预设发生改变 */
        if ("dsp.${PreferenceConfig.getPrefPage()}.tone.eq.presets" == key) {
            /* 设置不为自定义的类型 */
            val eqType = pref.getString(key, EQDialogPreference.defaultValue)!!
            eqUpdateFromPresets = true
            if (eqType != "custom")
                preferenceScreen.findPreference<EQDialogPreference>(
                    "dsp.${PreferenceConfig.getPrefPage()}.tone.eq.values")!!.writeEQValues(eqType)
        }
        /* EQ均衡器图形化设置发生改变 */
        if ("dsp.${PreferenceConfig.getPrefPage()}.tone.eq.values" == key) {
            val eqValues = pref.getString(key, EQDialogPreference.defaultValue)!!
            val eqPreset = preferenceScreen.findPreference<SummarisedListPreference>(
                "dsp.${PreferenceConfig.getPrefPage()}.tone.eq.presets")!!

            /* 设置均衡器预设对应描述 */
            if (!eqUpdateFromPresets) {
                var isCustomEQ = true
                for (index in eqPreset.entryValues.indices) {
                    if (eqPreset.entryValues[index] == eqValues) {
                        isCustomEQ = false
                        break
                    }
                }

                /* 写入配置 */
                eqPreset.value = if (isCustomEQ) "custom" else eqValues
            }

            eqUpdateFromPresets = false
            /* 更新均衡器 */
            if (PreferenceConfig.prefPage == PreferenceConfig.effectedPage) {
                val levelsArray = eqValues.split(";")
                for (index in levelsArray.indices) {
                    effectService?.setEqSingleBand(index, levelsArray[index].toFloat())
                }
            }
        }
    }

    /* 设置其他配置监听器 */
    private val commonPrefChangedListener = OnSharedPreferenceChangeListener { pref, key ->
        if (PreferenceConfig.prefPage == PreferenceConfig.effectedPage) {
            /* 效果总开关 */
            if (key == "dsp.${PreferenceConfig.getPrefPage()}.main.switch") {
                effectService?.enableEffect(pref.getBoolean(key, false))
            }
            /* 动态范围压缩开关 */
            if (key == "dsp.${PreferenceConfig.getPrefPage()}.compression.enable") {
                effectService?.enableDRC(pref.getBoolean(key, false))
            }
            /* 动态范围压缩强度 */
            if (key == "dsp.${PreferenceConfig.getPrefPage()}.compression.values") {
                effectService?.setDrcValue(pref.getInt(key, 5))
            }
            /* 低音增益开关 */
            if (key == "dsp.${PreferenceConfig.getPrefPage()}.bass.boost.enable") {
                effectService?.enableBassBoost(pref.getBoolean(key, false))
            }
            /* 低音频点设置 */
            if (key == "dsp.${PreferenceConfig.getPrefPage()}.bass.boost.freq.point") {
                effectService?.setBassFreqPoint(pref.getInt(key, 20))
            }
            /* 低音增益强度 */
            if (key == "dsp.${PreferenceConfig.getPrefPage()}.bass.boost.freq.strength") {
                effectService?.setBassBoostStrength(pref.getInt(key, 0))
            }
            /* 均衡器开关 */
            if (key == "dsp.${PreferenceConfig.getPrefPage()}.tone.eq.enable") {
                Log.d(TAG, "preference changed: ")
                effectService?.enableEqualizer(pref.getBoolean(key, false))
            }
            /* 均衡器响度补偿强度开关 */
            if (key == "dsp.${PreferenceConfig.getPrefPage()}.eq.loudness.enable") {
                effectService?.enableLoudness(pref.getBoolean(key, false))
            }
            /* 均衡器响度补偿强度 */
            if (key == "dsp.${PreferenceConfig.getPrefPage()}.eq.loudness.strength") {
                effectService?.setLoudnessStrength(pref.getInt(key, 0))
            }
            /* 空间混响开启 */
            if (key == "dsp.${PreferenceConfig.getPrefPage()}.virtualize.enable") {
                effectService?.enableVirtualizer(pref.getBoolean(key, false))
            }
            /* 空间混响类型 */
            if (key == "dsp.${PreferenceConfig.getPrefPage()}.virtualize.type") {
                effectService?.setVirtualizerType(pref.getString(key, "0")?.toInt()!!)
            }
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.d(TAG, "onServiceConnected: bind success")
//        effectBinder = service as EffectService.EffectBinder

        effectService = (service as EffectService.EffectBinder).getService()
    }

    override fun onServiceDisconnected(name: ComponentName?) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* 服务绑定 */
        val intent = Intent(requireContext(), EffectService::class.java)
        requireActivity().bindService(intent, this, BIND_AUTO_CREATE)
    }

    /* 创建Preference, 该方法在onCreate()之后执行 */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        /* 获取对应页面, 并设置配置名称和配置模式 */
        preferenceManager.sharedPreferencesName = "${PreferenceConfig.PACKAGE_NAME}.${PreferenceConfig.getPrefPage()}"
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE

        /* 设置对应配置页面 */
        val xmlID = PreferenceConfig.getPrefPageID()
        setPreferencesFromResource(xmlID, rootKey)

        /* 注: 设置好配置页面后才能注册监听器 */
        /* 注册配置更改监听器 */
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(prefEqualizerChangedListener)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(commonPrefChangedListener)

        /* 动态范围压缩滑动条 */
        val compressionPref = findPreference<SeekBarPreference>("dsp.${PreferenceConfig.getPrefPage()}.compression.values")
        compressionPref?.setOnPreferenceChangeListener { pref, strength ->
            val percentage = ((strength as Int - compressionPref.min).toFloat() / (compressionPref.max - compressionPref.min) * 100).toInt()
            pref.summary = "$percentage %"
            true
        }
        /* 响度补偿滑动条 */
        val loudnessPref = findPreference<SeekBarPreference>("dsp.${PreferenceConfig.getPrefPage()}.eq.loudness.strength")
        loudnessPref?.setOnPreferenceChangeListener { pref, strength ->
            pref.summary = "${strength as Int} %"
            true
        }
        /* 低音频点 */
        val bassBoostFreqPref = findPreference<SeekBarPreference>("dsp.${PreferenceConfig.getPrefPage()}.bass.boost.freq.point")
        bassBoostFreqPref?.setOnPreferenceChangeListener { pref, freq ->
            pref.summary = "$freq Hz"
            true
        }
        /* 低音增益强度 */
        val bassBoostStrengthPref = findPreference<SeekBarPreference>("dsp.${PreferenceConfig.getPrefPage()}.bass.boost.freq.strength")
        bassBoostStrengthPref?.setOnPreferenceChangeListener{ pref, strength ->
            pref.summary = "${strength as Int} %"
            true
        }
    }

    /* 注销片段 */
    override fun onDestroy() {
        super.onDestroy()
        /* 注销配置更改监听器 */
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(prefEqualizerChangedListener)
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(commonPrefChangedListener)
        /* 解绑服务 */
        requireActivity().unbindService(this)
    }

    /* 显示配置选项对话框 */
    override fun onDisplayPreferenceDialog(preference: Preference) {
        /* 判断配置对话框是否属于EQ的 */
        if (preference is EQDialogPreference) {
            val eqFragment = EQDialogPreferenceFragment.newInstance(preference.key)
            /* 某公司不给解决方案, 那只能忽略警告了 */
            @Suppress("DEPRECATION")
            eqFragment.setTargetFragment(this, 0)
            eqFragment.show(parentFragmentManager, EQDialogPreferenceFragment.TAG)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }
}