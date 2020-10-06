package com.bel.android.dspmanager.service;

import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.bel.android.dspmanager.activity.DSPManager;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * <p>This calls listen to events that affect DSP function and responds to them.</p>
 * <ol>
 * <li>new audio session declarations</li>
 * <li>headset plug / unplug events</li>
 * <li>preference update events.</li>
 * </ol>
 *
 * @author alankila
 */

public class HeadsetService extends Service {

    // 设定音效标签
    static final String TAG = "DSPManager";

    /**
     * 创建DSP模块
     * 静态类
     **/

    public static class DSPModule {
        // 实例四个音效
        public AudioEffect DSP_Compression;  // 动态范围压缩
        private Equalizer mEqualizer;        // 均衡器
        private BassBoost mBassBoost;        // 低频增益
        private Virtualizer mVirtualizer;    // 空间混响

        // 音效模块依赖库唯一标识符
        final static UUID EFFECT_TYPE_CUSTOM = UUID.fromString("09e8ede0-ddde-11db-b4f6-0002a5d5c51b");
        // DSP动态范围压缩唯一标识符
        final static UUID EFFECT_DSP_COMPRESSION = UUID.fromString("c3b61114-def3-5a85-a39d-5cc4020ab8af");

        // 实例一个音效渲染
        public DSPModule(int sessionId) {
            try {

                /**
                 * AudioEffect constructor is not part of SDK. We use reflection
                 * to access it.
                 **/

                DSP_Compression = AudioEffect.class.getConstructor(UUID.class, UUID.class, Integer.TYPE, Integer.TYPE)
                        .newInstance(EFFECT_TYPE_CUSTOM, EFFECT_DSP_COMPRESSION, 0, sessionId);

                mEqualizer = new Equalizer(0, sessionId);
                mBassBoost = new BassBoost(0, sessionId);
                mVirtualizer = new Virtualizer(0, sessionId);


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
         *
         * @param audioEffect
         * @param parameter
         * @param value
         */

        // 设定音效访问参数
        private void setParameter(AudioEffect audioEffect, int parameter, short value) {
            try {
                byte[] arguments = new byte[]{
                        (byte) (parameter), (byte) (parameter >> 8),
                        (byte) (parameter >> 16), (byte) (parameter >> 24)
                };
                byte[] result = new byte[]{
                        (byte) (value), (byte) (value >> 8)
                };

                Method setParameter = AudioEffect.class.getMethod(
                        "setParameter", byte[].class, byte[].class);
                setParameter.invoke(audioEffect,
                        arguments, result);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // 组件：输出模式检测，检测扬声器，耳机，蓝牙，usb底座

    /**
     * Is a wired headset plugged in?
     */
    public static boolean mUseHeadset = false;

    /**
     * Is bluetooth headset plugged in?
     */
    public static boolean mUseBluetooth = false;

    /**
     * Is USB plugged in?
     */
    public static boolean mUseUsb = false;

    // 组件检测完毕

    /**
     * Has DSPManager assumed control of equalizer levels?
     */
    // DSP管理器控制音频波段调整
    private float[] mOverriddenEqualizerLevels;

    /**
     * Receive new broadcast intents for adding DSP to session
     */
    // 创建广播接收器

    // 实例音效
    private DSPModule mDSPEffect;
    // 因为是全局渲染音效，所以当 effectMode 为0时就是打开音效总开关并开始全局音效配置
    private int effectMode = 1;
    // 音效广播接收器
    private final BroadcastReceiver mAudioSessionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR);
            // 保险，如果获取错误值直接返回不进行下一步操作
            if (sessionId == AudioEffect.ERROR) {
                return;
            }
            // 开始音效控制的处理工作
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                mDSPEffect = new DSPModule(sessionId);
                // 更新DSP方法
                // 因为在当前配置页面所以无需刷新页面
                updateDSP(false);
            }
            // 关闭音效控制时的清理工作
            if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                if (mDSPEffect != null) {
                    mDSPEffect.release();
                }
                mDSPEffect = null;
            }
        }
    };


    /**
     * Update audio parameters when preferences have been updated.
     */
    // 音效配置更新时，更新音效输出参数
    private final BroadcastReceiver mPreferenceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 更新DSP方法
            // 因为在当前配置页面所以无需刷新页面
            updateDSP(false);
        }
    };

    /**
     * This code listens for changes in bluetooth and headset events. It is
     * adapted from google's own MusicFX application, so it's presumably the
     * most correct design there is for this problem.
     */
    // 添加方法检测音频输出方式：蓝牙，有线，USB底座，扬声器
    // 耳机
    private final BroadcastReceiver mRoutingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            // 添加一个临时的耳机插入检测
            final boolean prevUseHeadset = mUseHeadset;
            // 通过音频输出状态检测耳机是否真正插入
            if (AudioManager.ACTION_HEADSET_PLUG.equals(action)) {
                mUseHeadset = intent.getIntExtra("state", 0) == 1;
            } else if ("android.intent.action.ANALOG_AUDIO_DOCK_PLUG".equals(action) && Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                mUseHeadset = intent.getIntExtra("state", 0) == 1;
            }
            // 如果检测到耳机变动，直接更新音效配置状态
            if (prevUseHeadset != mUseHeadset) {
                // 更新DSP方法
                // 此处需要切换音效配置页面
                updateDSP(true);
            }
        }
    };

    // 蓝牙
    private final BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_CONNECTED);

                if (state == BluetoothProfile.STATE_CONNECTED && !mUseBluetooth) {
                    mUseBluetooth = true;
                    // 更新DSP方法
                    // 此处需要切换音效配置页面
                    updateDSP(true);
                } else if (state != BluetoothProfile.STATE_CONNECTED && mUseBluetooth) {
                    mUseBluetooth = false;
                    // 更新DSP方法
                    // 此处需要切换音效配置页面
                    updateDSP(true);
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                String stateExtra = BluetoothAdapter.EXTRA_STATE;
                int state = intent.getIntExtra(stateExtra, -1);

                if (state == BluetoothAdapter.STATE_OFF && mUseBluetooth) {
                    mUseBluetooth = false;
                    // 更新DSP方法
                    // 此处需要切换音效配置页面
                    updateDSP(true);
                }
            }
        }
    };

    // USB底座
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final boolean prevUseUSB = mUseUsb;
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice usbAudio = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                // 保险，防止应用闪退
                if (usbAudio == null) {
                    return;
                }
                int count = usbAudio.getConfigurationCount();
                for (int i = 0; i < count; i++) {
                    UsbConfiguration configuration = usbAudio.getConfiguration(i);
                    int interfaceCount = configuration.getInterfaceCount();
                    for (int j = 0; j < interfaceCount; j++) {
                        UsbInterface usbInterface = configuration.getInterface(j);
                        if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO && !mUseUsb) {
                            mUseUsb = true;
                        }
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action) && mUseUsb) {
                mUseUsb = false;
            }
            if (prevUseUSB != mUseUsb) {
                // 更新DSP方法
                // 此处需要切换音效配置页面
                updateDSP(true);
            }
        }
    };

    // 构造运行方法
    @Override
    public void onCreate() {
        super.onCreate();
        // 创建启动服务
        // Starting service
        IntentFilter audioFilter = new IntentFilter();
        audioFilter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        audioFilter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        registerReceiver(mAudioSessionReceiver, audioFilter);

        // 耳机插入时意图过滤器
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        intentFilter.addAction("android.intent.action.ANALOG_AUDIO_DOCK_PLUG");

        // USB底座插入意图过滤器
        final IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);


        // 动态注册广播
        registerReceiver(mRoutingReceiver, intentFilter);
        registerReceiver(mUsbReceiver, usbFilter);
        registerReceiver(mPreferenceUpdateReceiver, new IntentFilter(DSPManager.ACTION_UPDATE_PREFERENCES));

        // 蓝牙
        final IntentFilter btFilter = new IntentFilter();
        btFilter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        btFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBtReceiver, btFilter);
    }

    // 写一个方法让音效得以渲染、释放
    // 因为这个默认是全局音效启动，所以不考虑会话不等于0的情况
    // 如果模式为0，则开始全局渲染音效，如果不是，关掉此应用的音效渲染
    private void setEffectMode(int effectMode) {
        if (effectMode == 0) {
            // 全局模式
            if (mDSPEffect != null) {
                mDSPEffect.release();
                mDSPEffect = null;
            } else {
                mDSPEffect = new DSPModule(0);
            }
        } else {
            // 不是全局模式直接杀掉
            if (mDSPEffect != null) {
                mDSPEffect.release();
            }
            mDSPEffect = null;
        }
    }

    // 重写销毁方法
    @Override
    public void onDestroy() {
        super.onDestroy();
        // 注销已注册的活动
        unregisterReceiver(mAudioSessionReceiver);
        unregisterReceiver(mRoutingReceiver);
        unregisterReceiver(mUsbReceiver);
        unregisterReceiver(mBtReceiver);
        unregisterReceiver(mPreferenceUpdateReceiver);

        if (mDSPEffect != null) {
            mDSPEffect.release();
        }
        mDSPEffect = null;
    }

    // 创建进程间通信
    public class LocalBinder extends Binder {
        public HeadsetService getService() {
            return HeadsetService.this;
        }
    }

    private final LocalBinder mBinder = new LocalBinder();

    /**
     * 服务绑定
     **/
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Gain temporary control over the global equalizer.
     * Used by DSPManager when testing a new equalizer setting.
     *
     * @param levels
     */

    // 创建修改均衡器频段方法
    public void setEqualizerLevels(float[] levels) {
        mOverriddenEqualizerLevels = levels;
        // 更新DSP方法
        // 此处不需要切换页面
        updateDSP(false);
    }

    // 创建均衡器波段控制条
    private float[] eqLevels = new float[6];

    /**
     * There appears to be no way to find out what the current actual audio routing is.
     * For instance, if a wired headset is plugged in, the following objects/classes are involved:</p>
     * <ol>
     * <li>wiredaccessoryobserver</li>
     * <li>audioservice</li>
     * <li>audiosystem</li>
     * <li>audiopolicyservice</li>
     * <li>audiopolicymanager</li>
     * </ol>
     * <p>Once the decision of new routing has been made by the policy manager, it is relayed to
     * audiopolicyservice, which waits for some time to let application buffers drain, and then
     * informs it to hardware. The full chain is:</p>
     * <ol>
     * <li>audiopolicymanager</li>
     * <li>audiopolicyservice</li>
     * <li>audiosystem</li>
     * <li>audioflinger</li>
     * <li>audioeffect (if any)</li>
     * </ol>
     * <p>However, the decision does not appear to be relayed to java layer, so we must
     * make a guess about what the audio output routing is.</p>
     *
     * @return string token that identifies configuration to use
     */

    // 检测音频输出方式
    public static String getAudioOutputRouting() {
        if (mUseBluetooth) {
            return "bluetooth";
        }
        if (mUseHeadset) {
            return "headset";
        }
        if (mUseUsb) {
            return "usb";
        }
        return "speaker";
    }

    // 添加音效渲染的方法
    // 这里后期可能需要sessionId判断音效是否开启
    private void updateDSPEffect(SharedPreferences preferences, DSPModule session) {

        // 动态范围压缩
        session.DSP_Compression.setEnabled(preferences.getBoolean("dsp.compression.enable", false));
        mDSPEffect.setParameter(session.DSP_Compression, 0,
                Short.parseShort(preferences.getString("dsp.compression.mode", "0")));

        // 低音增益
        session.mBassBoost.setEnabled(preferences.getBoolean("dsp.bass.enable", false));
        session.mBassBoost.setStrength(Short.parseShort(preferences.getString("dsp.bass.mode", "0")));

        // 低音频点
        short freq = Short.parseShort(preferences.getString("dsp.bassboost.freq", "55"));
        session.setParameter(session.mBassBoost, 133, freq);

        // 均衡器频段
        session.mEqualizer.setEnabled(preferences.getBoolean("dsp.tone.enable", false));
        if (mOverriddenEqualizerLevels != null) {
            for (short i = 0; i < mOverriddenEqualizerLevels.length; i++) {
                eqLevels[i] = mOverriddenEqualizerLevels[i];
                session.mEqualizer.setBandLevel(i, (short) Math.round(eqLevels[i] * 100));
            }
        } else {
            String[] levels = preferences.getString("dsp.tone.eq.custom", "0.0;0.0;0.0;0.0;0.0;0.0").split(";");
            // 注意，六个控制条只有五个分隔符
            for (short i = 0; i < levels.length; i++) {
                eqLevels[i] = Float.parseFloat(levels[i]);
                session.mEqualizer.setBandLevel(i, (short) Math.round(eqLevels[i] * 100));
            }
        }
        // 响度补偿
        mDSPEffect.setParameter(session.mEqualizer, 1000, Short.parseShort(preferences.getString("dsp.tone.loudness", "10000")));


        // 空间混响
        session.mVirtualizer.setEnabled(preferences.getBoolean("dsp.headphone.enable", false));
        session.mVirtualizer.setStrength(
                Short.parseShort(preferences.getString("dsp.headphone.mode", "0")));
    }

    /**
     * Push new configuration to audio stack.
     */
    // 将设置好的音频配置推送到音频堆栈中
    // 为了符合更新音频输出方式的时候切换音效配置界面，在此方法添加一个参数以便判断
    // 如果切换音频输出就刷新音效，不切换就不刷新
    // 并且在均衡器弹出时候不影响音效的刷新
    protected void updateDSP(boolean ifRefresh) {

        // 判断是否刷新页面配置，如果是，连同页面一起刷新
        // 传递需要切换页面的意图
        if (ifRefresh) {
            Intent refreshPageIntent = new Intent("dsp.activity.refreshPage");
            sendBroadcast(refreshPageIntent);
        }

        // 创建一个接收音频输出的字符串，判断当前音频输出是哪个
        final String outPutMode = getAudioOutputRouting();
        // 创建一个共享选项用来接收页面切换之后的音频配置
        SharedPreferences sharedPreferences = getSharedPreferences(DSPManager.SHARED_PREFERENCES_BASENAME + "." + outPutMode, 0);

        // 调试，查看音频输出模式用
        // Log.i("音频输出模式",""+ effectMode);

        // 根据音效总开关设置音效处理模式
        int thisEffectMode = sharedPreferences.getBoolean("dsp.masterswitch.enable", false) ? 0 : 1;

        // 调试，检测效果总开关有没有打开
        // Log.i("效果总开关", "模式：" + effectMode);

        // 判断音效模式，0为需要渲染，1为不需要
        // 此判断方法为一次性，有变动时检测模式才会再次运作
        // 如果持续运行则跳过此检测方法避免音效渲染被终止
        if (thisEffectMode == 0 && thisEffectMode != effectMode) {
            effectMode = 0;
            setEffectMode(thisEffectMode);
        } else if (thisEffectMode == 1 && thisEffectMode != effectMode) {
            effectMode = 1;
            setEffectMode(thisEffectMode);
        }

        // 全局音效渲染
        if (effectMode == 0) {
            try {
                updateDSPEffect(sharedPreferences, mDSPEffect);
            } catch (Exception e) {
                Log.e(TAG, "Could not effect audio.", e);
            }
        }

        // 调试，查看输出配置
        // 禁用此调试以获得更佳性能
        // Log.i(TAG,"Selected configuration: " + outPutMode);
    }

}