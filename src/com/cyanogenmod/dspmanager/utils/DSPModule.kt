package com.cyanogenmod.dspmanager.utils

import android.media.audiofx.AudioEffect
import android.util.Log
import org.chickenhook.restrictionbypass.RestrictionBypass
import java.util.UUID

class DSPModule(sessionId: Int) {
    companion object {
        private const val TAG = "DSPModule"

        // DSP动态范围类型
        private val EFFECT_TYPE_COMPRESSION = UUID.fromString("09e8ede0-ddde-11db-b4f6-0002a5d5c51b")
        private val EFFECT_DSP_COMPRESSION = UUID.fromString("c3b61114-def3-5a85-a39d-5cc4020ab8af")
    }

    /* 动态范围压缩 */
    val dspMainEffect: AudioEffect

    init {
        val audioConstructor = AudioEffect::class.java.getDeclaredConstructor(UUID::class.java, UUID::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        /* 访问系统访问权限 */
        audioConstructor.isAccessible = true
        dspMainEffect = audioConstructor.newInstance(EFFECT_TYPE_COMPRESSION, EFFECT_DSP_COMPRESSION, 0, sessionId)
    }

    /* 释放音频资源 */
    fun releaseModule() {
        dspMainEffect.release()
    }

    /* 设置DSP内特定值 */
    fun setSpecParam(audioEffect: AudioEffect, parameter: Int, value: Short) {
        RestrictionBypass.getMethod(AudioEffect::class.java, "setParameter", Int::class.java, Short::class.java)
            .invoke(audioEffect, parameter, value)
    }

    /* 设置EQ均衡器 */
    fun setEqualizerParam(audioEffect: AudioEffect, bandIndex: Int, bandLevel: Short) {
        try {
            /* 第一个参数是指令, 第二个参数是均衡器EQ频段条位置 */
            val eqParam = intArrayOf(106, bandIndex)
            val eqValue = shortArrayOf(bandLevel)
            RestrictionBypass.getMethod(AudioEffect::class.java, "setParameter", IntArray::class.java, ShortArray::class.java)
                .invoke(audioEffect, eqParam, eqValue)
        } catch (e: Exception) {
            Log.d(TAG, "setEqualizerParam: Error setting EQLevel!")
        }
    }
}