                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    package com.bel.android.dspmanager.effectmodule;

import android.media.audiofx.AudioEffect;

import org.chickenhook.restrictionbypass.RestrictionBypass;

import java.util.UUID;

/**
 * 创建DSP渲染模块
 **/

public class DSPModule {

    /**
     * 创建DSP渲染特定接口标识
     **/

    // DSP动态范围类型
    private static final UUID EFFECT_TYPE_COMPRESSION = UUID.fromString("09e8ede0-ddde-11db-b4f6-0002a5d5c51b");
    private static final UUID EFFECT_DSP_COMPRESSION = UUID.fromString("c3b61114-def3-5a85-a39d-5cc4020ab8af");

    // DSP低音类型
    private static final UUID EFFECT_TYPE_BASS = UUID.fromString("3345d821-bf49-50d1-84db-5f7a4990c01b");
    private static final UUID EFFECT_DSP_BASSBOOST = UUID.fromString("eb888559-23db-515f-bd90-5360565b1a46");

    // DSP均衡器类型
    private static final UUID EFFECT_TYPE_EQ = UUID.fromString("a9d9ecab-1521-506a-a6aa-c8abbaf2ca26");
    private static final UUID EFFECT_DSP_EQUALIZER = UUID.fromString("06cc8ec6-15a0-5b8c-9460-e379bba6c090");

    // 均衡器参数访问
    private static final int PARAM_BAND_LEVEL = 2;

    // DSP混响类型
    private static final UUID EFFECT_TYPE_VIR = UUID.fromString("27cf8d17-2060-5ebc-9ac8-190c7f5ebc15");
    private static final UUID EFFECT_DSP_VIRTUALIZER = UUID.fromString("38e9eea4-b7c9-5230-bf5c-60203bf6423c");

    // 实例四个音效
    public final AudioEffect DSP_Compression;  // 动态范围压缩
    public final AudioEffect DSP_BassBoost;    // 低频
    public final AudioEffect DSP_Equalizer;    // 均衡器
    public final AudioEffect DSP_Virtualizer;  // 空间混响


    // 实例一个音效渲染
    public DSPModule(int sessionId) {
        try {

                /*
                 AudioEffect constructor is not part of SDK. We use reflection
                 to access it.
                 */
            DSP_Compression = AudioEffect.class.getDeclaredConstructor(UUID.class, UUID.class, Integer.TYPE, Integer.TYPE)
                    .newInstance(EFFECT_TYPE_COMPRESSION, EFFECT_DSP_COMPRESSION, 0, sessionId);

            DSP_BassBoost = AudioEffect.class.getDeclaredConstructor(UUID.class, UUID.class, Integer.TYPE, Integer.TYPE)
                    .newInstance(EFFECT_TYPE_BASS, EFFECT_DSP_BASSBOOST, 0, sessionId);

            DSP_Equalizer = AudioEffect.class.getDeclaredConstructor(UUID.class, UUID.class, Integer.TYPE, Integer.TYPE)
                    .newInstance(EFFECT_TYPE_EQ, EFFECT_DSP_EQUALIZER, 0, sessionId);

            DSP_Virtualizer = AudioEffect.class.getDeclaredConstructor(UUID.class, UUID.class, Integer.TYPE, Integer.TYPE)
                    .newInstance(EFFECT_TYPE_VIR, EFFECT_DSP_VIRTUALIZER, 0, sessionId);

        } catch (Exception e) {
            // 运行异常抛出
            throw new RuntimeException(e);
        }
    }

    // 音效释放
    public void release() {
        DSP_Compression.release();
        DSP_BassBoost.release();
        DSP_Equalizer.release();
        DSP_Virtualizer.release();
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
    public static void setParameter(AudioEffect audioEffect, int parameter, short value) {
        try {

            // For API 21 to 29
            // AudioEffect.class.getMethod("setParameter", byte[].class, byte[].class).invoke(audioEffect, arguments, result);

            // For API 30 use ByPassMethod
            RestrictionBypass.getMethod(AudioEffect.class, "setParameter", int.class, short.class).invoke(audioEffect, parameter, value);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    // 测试：测试音效均衡器获取
    public static void setParameterEqualizer(AudioEffect audioEffect, int band, short level) {
        try {
            int[] param = new int[2];
            param[0] = PARAM_BAND_LEVEL;
            param[1] = band;
            short[] value = new short[1];
            value[0] = level;

            // For API 21 to 29
            // AudioEffect.class.getMethod("setParameter", byte[].class, byte[].class).invoke(audioEffect, arguments, result);

            // For API 30 use ByPassMethod
            RestrictionBypass.getMethod(AudioEffect.class, "setParameter", int[].class, short[].class).invoke(audioEffect, param, value);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
