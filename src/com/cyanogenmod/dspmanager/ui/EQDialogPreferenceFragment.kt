package com.cyanogenmod.dspmanager.ui

import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.preference.PreferenceDialogFragmentCompat
import com.cyanogenmod.dspmanager.R
import com.cyanogenmod.dspmanager.service.EffectService
import com.cyanogenmod.dspmanager.utils.EqualizerView
import com.cyanogenmod.dspmanager.utils.PreferenceConfig
import java.util.Locale
import kotlin.math.round

class EQDialogPreferenceFragment: PreferenceDialogFragmentCompat(), ServiceConnection {
    private lateinit var mDialogEqualizerView: EqualizerView
//    private var effectBinder: EffectService.EffectBinder ?= null
    private var effectService: EffectService ?= null
    companion object {
        const val TAG = "EQPreferenceFragment"

        /* 设置实例化 */
        fun newInstance(key: String): EQDialogPreferenceFragment {
            val cacheFragment = EQDialogPreferenceFragment()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            cacheFragment.arguments = bundle
            return cacheFragment
        }
    }

    /* 点击Preference弹出的对话框 */
    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        /* 绑定服务 */
        val serviceIntent = Intent(requireContext(), EffectService::class.java)
        requireActivity().bindService(serviceIntent, this, BIND_AUTO_CREATE)
        mDialogEqualizerView = view.findViewById(R.id.EQDialog)
        mDialogEqualizerView.forDialog()
        mDialogEqualizerView.setBandChangedListener { bandIndex, bandLevel ->
            /* 若设置的配置与正在应用的配置页面相同, 则通过服务绑定更新均衡器 */
            if (PreferenceConfig.prefPage == PreferenceConfig.effectedPage)
                effectService?.setEqSingleBand(bandIndex, bandLevel)
        }
        /* 在弹出对话框的视图更新视图 */
        updateEQValue(updateUI = true, updateEffect = false)
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.d(TAG, "onServiceConnected: bind success")
        effectService = (service as EffectService.EffectBinder).getService()
        /* 更新均衡器音效 */
        updateEQValue(updateUI = false, updateEffect = true)
    }

    override fun onServiceDisconnected(name: ComponentName?) {

    }

    /* 获取EQ配置 */
    private fun getEQPreference(): EQDialogPreference {
        return preference as EQDialogPreference
    }

    /* 更新视图内的频段, 添加判断UI更新, 音效更新 */
    private fun updateEQValue(updateUI: Boolean, updateEffect: Boolean) {
        val values = getEQPreference().getEQValue()
        if (values.isNotEmpty()) {
            val eqLevel = values.split(";")
            for (index in eqLevel.indices) {
                if (updateUI) {
                    /* 设置可视化的频率条 */
                    mDialogEqualizerView.setSingleVisibleBand(index, eqLevel[index].toFloat())
                    /* 设置要更改音效的频率条 */
                    mDialogEqualizerView.setEffectedSingleBand(index, eqLevel[index].toFloat())
                }
                if (updateEffect) {
                    /* 若设置的配置与正在应用的配置页面相同, 则通过服务绑定更新均衡器 */
                    if (PreferenceConfig.prefPage == PreferenceConfig.effectedPage)
                        effectService?.setEqSingleBand(index, eqLevel[index].toFloat())
                }
            }
        }
    }

    /* 关闭片段对话框时 */
    override fun onDialogClosed(positiveResult: Boolean) {
        val eqValueBuilder = StringBuilder()
        val eqLevels = FloatArray(6)
        if (positiveResult) {
            /* 添加分隔符";" 设置各个频段的值 */
            for (i in 0..5) {
                eqValueBuilder.append(String.format(Locale.ROOT, "%.1f", round(mDialogEqualizerView.getSingleBand(i) * 10f) / 10f))
                eqLevels[i] = mDialogEqualizerView.getSingleBand(i)
                if (i < 5)
                    eqValueBuilder.append(";")
            }
            getEQPreference().writeEQValues(eqValueBuilder.toString())
        }
        /* 有时候设置了预设但是没有保存更新, 得重新渲染下音效 */
        updateEQValue(updateUI = false, updateEffect = true)

        /* 解绑服务 */
        requireActivity().unbindService(this)
    }
}