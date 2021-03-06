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
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.bel.android.dspmanager.activity.DSPManager;
import com.bel.android.dspmanager.effectmodule.DSPModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
    private static final String TAG = "DSPManager";

    // 创建进程间通信
    public class LocalBinder extends Binder {
        public HeadsetService getService() {
            return HeadsetService.this;
        }
    }

    private final LocalBinder mBinder = new LocalBinder();

    /**
     * Known audio sessions and their associated audioeffect suites.
     */
    private final Map<Integer, DSPModule> mAudioSessions = new HashMap<>();

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
    private static float[] mOverriddenEqualizerLevels;

    /**
     * Receive new broadcast intents for adding DSP to session
     */
    // 创建广播接收器

    // 实例音效
    public static DSPModule mDSPEffect;
    // 添加音效模式(因为频繁访问所以使用静态变量)
    public static int effectMode;
    // 添加音效模式的配置
    private SharedPreferences preferencesEffectMode;

    // 音效广播接收器
    private final BroadcastReceiver mAudioSessionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            if (sessionId == 0) {
                return;
            }
            // 开始音效控制的处理工作
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                if (effectMode == 0) {
                    return;
                }
                if (!mAudioSessions.containsKey(sessionId)) {
                    DSPModule prevDSP_Module = new DSPModule(sessionId);
                    if (prevDSP_Module.DSP_Compression == null) {
                        Log.e(TAG, "Sound effect create fail");
                        prevDSP_Module.release();
                        // prevDSP_Module = null;
                    } else {
                        mAudioSessions.put(sessionId, prevDSP_Module);
                    }
                    updateDSP(false);
                }
            }
            // 关闭音效控制时的清理工作
            if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                DSPModule prevDSP_Module = mAudioSessions.remove(sessionId);
                if (prevDSP_Module != null) {
                    prevDSP_Module.release();
                    // prevDSP_Module = null;
                }
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
        public void onReceive(final Context context, final Intent intent) {
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
        public void onReceive(final Context context, final Intent intent) {
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
        public void onReceive(final Context context, final Intent intent) {
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
     * <p>
     * param levels
     */

    // 创建修改均衡器频段方法
    public void setEqualizerLevels(float[] levels) {
        mOverriddenEqualizerLevels = levels;
        // 更新DSP方法
        // 此处不需要切换页面
        updateDSP(false);
    }

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
    // 查找正确的页面配置避免冲突
    private void updateDSPEffect(SharedPreferences preferences, DSPModule session, String OutPutMode) {

        // 动态范围压缩
        session.DSP_Compression.setEnabled(preferences.getBoolean("dsp." + OutPutMode + ".compression.enable", false));
        DSPModule.setParameter(session.DSP_Compression, 0,
                Short.parseShort(preferences.getString("dsp." + OutPutMode + ".compression.mode", "0")));

        // 低音增益
        session.DSP_BassBoost.setEnabled(preferences.getBoolean("dsp." + OutPutMode + ".bass.enable", false));
        DSPModule.setParameter(session.DSP_BassBoost, 1, Short.parseShort(preferences.getString("dsp." + OutPutMode + ".bass.mode", "0")));

        // 低音频点
        DSPModule.setParameter(session.DSP_BassBoost, 133, Short.parseShort(preferences.getString("dsp." + OutPutMode + ".bassboost.freq", "55")));

        // 均衡器频段
        session.DSP_Equalizer.setEnabled(preferences.getBoolean("dsp." + OutPutMode + ".tone.enable", false));
        if (mOverriddenEqualizerLevels != null) {
            for (int i = 0; i < mOverriddenEqualizerLevels.length; i++) {
                DSPModule.setParameterEqualizer(session.DSP_Equalizer, i, (short) Math.round(mOverriddenEqualizerLevels[i] * 100));
            }
        } else {
            String[] levels = preferences.getString("dsp." + OutPutMode + ".tone.eq.custom", "0.0;0.0;0.0;0.0;0.0;0.0").split(";");
            for (int i = 0; i < levels.length; i++) {
                DSPModule.setParameterEqualizer(session.DSP_Equalizer, i, (short) Math.round(Float.parseFloat(levels[i]) * 100));
            }
        }
        // 响度补偿
        DSPModule.setParameter(session.DSP_Equalizer, 1000, Short.parseShort(preferences.getString("dsp." + OutPutMode + ".tone.loudness", "10000")));

        // 空间混响
        session.DSP_Virtualizer.setEnabled(preferences.getBoolean("dsp." + OutPutMode + ".headphone.enable", false));
        DSPModule.setParameter(session.DSP_Virtualizer,1, Short.parseShort(preferences.getString("dsp."+ OutPutMode + ".headphone.mode", "0")));

    }

    /**
     * Push new configuration to audio stack.
     */
    // 将设置好的音频配置推送到音频堆栈中
    // 为了符合更新音频输出方式的时候切换音效配置界面，在此方法添加一个参数以便判断
    // 如果切换音频输出就刷新音效，不切换就不刷新
    // 并且在均衡器弹出时候不影响音效的刷新
    protected void updateDSP(boolean ifRefresh) {

        // 获取全局模式状态
        effectMode = preferencesEffectMode.getInt("dsp.manager.effectMode", 0);

        // 创建一个接收音频输出的字符串，判断当前音频输出是哪个
        final String outPutMode = getAudioOutputRouting();
        // 创建一个共享选项用来接收页面切换之后的音频配置
        SharedPreferences sharedPreferences = getSharedPreferences(DSPManager.SHARED_PREFERENCES_BASENAME + "." + outPutMode, 0);

        // 判断是否刷新页面配置，如果是，连同页面一起刷新
        // 传递需要切换页面的意图
        if (ifRefresh) {
            Intent refreshPageIntent = new Intent("dsp.activity.refreshPage");
            sendBroadcast(refreshPageIntent);
        }

        if (effectMode == 0) {
            // 全局音效渲染
            try {
                updateDSPEffect(sharedPreferences, mDSPEffect, outPutMode);
            } catch (Exception e) {
                Log.e(TAG, "Could not effect audio.", e);
            }
        } else {
            for (Integer sessionId : new ArrayList<>(mAudioSessions.keySet())) {
                try {
                    updateDSPEffect(sharedPreferences, mAudioSessions.get(sessionId), outPutMode);
                } catch (Exception e) {
                    Log.w(TAG, String.format(
                            "Trouble trying to manage session %d, removing...", sessionId), e);
                    mAudioSessions.remove(sessionId);
                }
            }
        }

        // 调试，查看输出配置
        // 禁用此调试以获得更佳性能
        // Log.i(TAG,"Selected configuration: " + outPutMode);
    }

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

        // 查看&保存DSP全局设置
        preferencesEffectMode = getSharedPreferences(DSPManager.SHARED_PREFERENCES_BASENAME + "." + "pref_settings", 0);
        // 判断该文件里面有没有全局设置字样，没有就添加，并且默认模式为全局模式
        if (!preferencesEffectMode.contains("dsp.manager.effectMode")) {
            preferencesEffectMode.edit().putInt("dsp.manager.effectMode", 0).apply();
        }
        effectMode = preferencesEffectMode.getInt("dsp.manager.effectMode", 0);
        if (effectMode == 0) {
            if (mDSPEffect != null) {
                mDSPEffect.release();
                mDSPEffect = null;
            }
            if (mDSPEffect == null) {
                mDSPEffect = new DSPModule(0);
            }
            if (mDSPEffect.DSP_Compression == null) {
                Log.e(TAG, "DSPManager library load fail");
                mDSPEffect.release();
                mDSPEffect = null;
            }
        }
        // 刷新dsp
        updateDSP(true);
    }

    // 重写启动方法
    @Override
    public int onStartCommand(Intent intent, int flag, int startId) {
        effectMode = preferencesEffectMode.getInt("dsp.manager.effectMode", 0);
        if (effectMode == 0) {
            if (mDSPEffect == null) {
                mDSPEffect = new DSPModule(0);
                if (mDSPEffect.DSP_Compression == null) {
                    Log.e(TAG, "Global effect load fail, reloading");
                    mDSPEffect.release();
                    mDSPEffect = null;
                    return super.onStartCommand(intent, flag, startId);
                }
                updateDSP(false);
                return super.onStartCommand(intent, flag, startId);
            }
            if (mDSPEffect.DSP_Compression == null) {
                Log.e(TAG, "Global effect load fail, reloading");
                mDSPEffect.release();
                mDSPEffect = new DSPModule(0);
                if (mDSPEffect.DSP_Compression == null) {
                    Log.e(TAG, "Global effect load fail, reloading");
                    mDSPEffect.release();
                    mDSPEffect = null;
                    return super.onStartCommand(intent, flag, startId);
                }
                return super.onStartCommand(intent, flag, startId);
            }
            Log.i(TAG, "Global audio session created.");
            updateDSP(false);
        }
        return super.onStartCommand(intent, flag, startId);
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
        mAudioSessions.clear();
        if (mDSPEffect != null) {
            mDSPEffect.release();
            mDSPEffect = null;
        }
    }

}