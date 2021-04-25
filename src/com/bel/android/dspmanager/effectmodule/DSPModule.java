package com.bel.android.dspmanager.effectmodule;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;

import org.chickenhook.restrictionbypass.RestrictionBypass;

import java.util.UUID;

/**
 * 创建DSP渲染模块
 **/

public class DSPModule {

    // 音效模块依赖库唯一标识符
    public final static UUID EFFECT_TYPE_CUSTOM = UUID.fromString("09e8ede0-ddde-11db-b4f6-0002a5d5c51b");
    // DSP动态范围压缩唯一标识符
    public final static UUID EFFECT_DSP_COMPRESSION = UUID.fromString("c3b61114-def3-5a85-a39d-5cc4020ab8af");

    // 实例四个音效
    public AudioEffect DSP_Compression;  // 动态范围压缩
    public Equalizer mEqualizer;        // 均衡器
    public BassBoost mBassBoost;        // 低频增益
    public Virtualizer mVirtualizer;    // 空间混响

    // 实例一个音效渲染
    public DSPModule(int sessionId) {
        try {

                /*
                 AudioEffect constructor is not part of SDK. We use reflection
                 to access it.
                 */

            DSP_Compression = AudioEffect.class.getDeclaredConstructor(UUID.class, UUID.class, Integer.TYPE, Integer.TYPE)
                    .newInstance(EFFECT_TYPE_CUSTOM, EFFECT_DSP_COMPRESSION, 0, sessionId);

            mEqualizer = new Equalizer(0, sessionId);   // 已将均衡器关联
            mBassBoost = new BassBoost(0, sessionId);   // 已将动态低频关联
            mVirtualizer = new Virtualizer(0, sessionId);   // 已将混响关联

        } catch (Exception e) {
            // 运行异常抛出
            throw new RuntimeException(e);
        }
    }

    // 音效释放
    public void release() {
        DSP_Compression.release();
        mBassBoost.release();
        mEqualizer.release();
        mVirtualizer.release();
    }

    /**
     * Proxies call to AudioEffect.setParameter(byte[], byte[]) which is
     * available via reflection.
     * <p>
     * param audioEffect
     * param parameter
     * param value
     **/

    // 设定音效访问参数
    public void setParameter(AudioEffect audioEffect, int parameter, short value) {
        try {
            byte[] arguments = new byte[]{
                    (byte) (parameter), (byte) (parameter >> 8),
                    (byte) (parameter >> 16), (byte) (parameter >> 24)
            };
            byte[] result = new byte[]{
                    (byte) (value), (byte) (value >> 8)
            };

            // For API 21 to 29
            // AudioEffect.class.getMethod("setParameter", byte[].class, byte[].class).invoke(audioEffect, arguments, result);

            // For API 30 use ByPassMethod
            RestrictionBypass.getMethod(AudioEffect.class, "setParameter", byte[].class, byte[].class).invoke(audioEffect, arguments, result);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
