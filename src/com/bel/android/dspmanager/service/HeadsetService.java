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
import android.widget.Toast;

import com.bel.android.dspmanager.activity.DSPManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

public class HeadsetService extends Service{
    // 效仿开关
    public static int modeEffect;
    // 设置音效唯一标识符
    public final static UUID EFFECT_TYPE_CUSTOM = UUID.fromString("09e8ede0-ddde-11db-b4f6-0002a5d5c51b");
    public final static UUID EFFECT_DSPCOMPRESSION = UUID.fromString("c3b61114-def3-5a85-a39d-5cc4020ab8af");

    // 设定音效标签
    private static final String TAG = "DSPManager";

    // 音效模块
    public class MDSPModule
    {
        // 创四个音效
        public AudioEffect DSPcompression;       // 动态范围压缩
        private Equalizer mEqualizer;             // 均衡器
        private BassBoost mBassBoost;             // 低频
        private Virtualizer mVirtualizer;         // 空间立体声

        // 创一个模块
        public MDSPModule(int sessionId){
            try
            {
                /*
                 * AudioEffect constructor is not part of SDK. We use reflection
                 * to access it.
                 */
                // 通过映射访问 AudioEffect
                // 分别映射 compression bassboost equalizer virtualizer
                DSPcompression = AudioEffect.class.getConstructor(UUID.class,UUID.class,Integer.TYPE,Integer.TYPE)
                        .newInstance(EFFECT_TYPE_CUSTOM,EFFECT_DSPCOMPRESSION,0,sessionId);

                // 尝试开启多音效
                mEqualizer = new Equalizer(0, sessionId);
                mBassBoost = new BassBoost(0, sessionId);
                mVirtualizer = new Virtualizer(0, sessionId);
            }
            catch (Exception e)
            {
                // 运行异常抛出
                throw new RuntimeException(e);
            }
        }

        // 音效渲染
        public void release() {
            DSPcompression.release();
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
        private  void setParameter(AudioEffect audioEffect, int parameter, short value) {
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

        // 设置均衡器音效参数
        // 目前用不到
        /*
        private void setParameterFloatArray(AudioEffect audioEffect, int parameter, float value[])
        {
            try
            {
                byte[] arguments = new byte[]
                        {
                                (byte)(parameter), (byte)(parameter >> 8),
                                (byte)(parameter >> 16), (byte)(parameter >> 24)
                        };
                byte[] result = new byte[value.length * 4];
                ByteBuffer byteDataBuffer = ByteBuffer.wrap(result);
                byteDataBuffer.order(ByteOrder.nativeOrder());
                for (float v : value) byteDataBuffer.putFloat(v);
                Method setParameter = AudioEffect.class.getMethod("setParameter", byte[].class, byte[].class);
                setParameter.invoke(audioEffect, arguments, result);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        */

        // 推测设定输入输出
        // 输入
        // 目前用不到
        /*
        private byte[] IntToByte(int[] input)
        {
            int int_index, byte_index;
            int iterations = input.length;
            byte[] buffer = new byte[input.length * 4];
            int_index = byte_index = 0;
            for (; int_index != iterations;)
            {
                buffer[byte_index] = (byte)(input[int_index] & 0x00FF);
                buffer[byte_index + 1] = (byte)((input[int_index] & 0xFF00) >> 8);
                buffer[byte_index + 2] = (byte)((input[int_index] & 0xFF0000) >> 16);
                buffer[byte_index + 3] = (byte)((input[int_index] & 0xFF000000) >> 24);
                ++int_index;
                byte_index += 4;
            }
            return buffer;
        }
        */

        // 输出
        // 目前用不到
        /*
        private int byteArrayToInt(byte[] encodedValue)
        {
            int value = (encodedValue[3] << 24);
            value |= (encodedValue[2] & 0xFF) << 16;
            value |= (encodedValue[1] & 0xFF) << 8;
            value |= (encodedValue[0] & 0xFF);
            return value;
        }
        */
    }

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
    private final Map<Integer, MDSPModule> mAudioSessions = new HashMap<Integer, MDSPModule>();

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

    /**
     * Has DSPManager assumed control of equalizer levels?
     */
    private float[] mOverriddenEqualizerLevels;

    /**
     * Receive new broadcast intents for adding DSP to session
     */

    // 开始设定全局音效
    public static MDSPModule MDSPGbEf;
    private SharedPreferences preferencesMode;
    private final BroadcastReceiver mAudioSessionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,0);
            if (sessionId == 0){
                return;
            }
            if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)){
                if (modeEffect == 0) {
                    return;
                }
                if (!mAudioSessions.containsKey(sessionId)){
                    MDSPModule fxId = new MDSPModule(sessionId);
                    if (fxId.DSPcompression == null){
                        Log.e(DSPManager.TAG,"Compression load fail");
                        fxId.release();
                        fxId = null;
                    }
                    else {
                        mAudioSessions.put(sessionId, fxId);
                    }
                    updateDsp(false);
                }
            }
            if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action))
            {
                MDSPModule gone = mAudioSessions.remove(sessionId);
                if (gone != null) {
                    gone.release();
                }
                gone = null;
            }
        }
    };

    /**
     * Update audio parameters when preferences have been updated.
     */
    // 更新音频参数
    private final BroadcastReceiver mPreferenceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateDsp(false);
        }
    };

    /**
     * This code listens for changes in bluetooth and headset events. It is
     * adapted from google's own MusicFX application, so it's presumably the
     * most correct design there is for this problem.
     */
    // 耳机事件广播接收器
    private final BroadcastReceiver mRoutingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            final boolean prevUseHeadset = mUseHeadset;
            if (AudioManager.ACTION_HEADSET_PLUG.equals(action)) {
                mUseHeadset = intent.getIntExtra("state", 0) == 1;
            } else if (Build.VERSION.SDK_INT >= 21 && "android.intent.action.ANALOG_AUDIO_DOCK_PLUG".equals(action)) {
                mUseHeadset = intent.getIntExtra("state", 0) == 1;
            }
            if (prevUseHeadset != mUseHeadset) {
                updateDsp(true);
            }
        }
    };

    // 蓝牙事件广播接收器
    private final BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                                                BluetoothProfile.STATE_CONNECTED);

                if (state == BluetoothProfile.STATE_CONNECTED && !mUseBluetooth) {
                    mUseBluetooth = true;
                    updateDsp(true);
                } else if (mUseBluetooth) {
                    mUseBluetooth = false;
                    updateDsp(true);
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                String stateExtra = BluetoothAdapter.EXTRA_STATE;
                int state = intent.getIntExtra(stateExtra, -1);

                if (state == BluetoothAdapter.STATE_OFF && mUseBluetooth) {
                    mUseBluetooth = false;
                    updateDsp(true);
                }
            }
        }
    };

    // USB底座连接广播接收器
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            final boolean prevUseUSB = mUseUsb;
            if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(action)) {
                UsbDevice usbAudio = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                assert usbAudio != null;
                int count = usbAudio.getConfigurationCount();
                for(int i = 0; i < count; i++) {
                    UsbConfiguration configuration = usbAudio.getConfiguration(i);
                    int interfaceCount = configuration.getInterfaceCount();
                    for (int j = 0; j < interfaceCount; j++){
                        UsbInterface usbInterface = configuration.getInterface(j);
                        if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO) {
                            mUseUsb = intent.getIntExtra("state",0) == 1;
                        }
                    }
                }
            }
            if (prevUseUSB != mUseUsb) {
                updateDsp(true);
            }
        }
    };

    // 开始构造
    @Override
    public void onCreate(){
        super.onCreate();
        // Starting service
        IntentFilter audioFilter = new IntentFilter();
        audioFilter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        audioFilter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        registerReceiver(mAudioSessionReceiver, audioFilter);

        final IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_HEADSET_PLUG);
        // 判断安卓SDK版本
        if (Build.VERSION.SDK_INT >= 21) {
            audioFilter.addAction("android.intent.action.ANALOG_AUDIO_DOCK_PLUG");
        }
        // USB底座
        audioFilter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
        // 音效动态注册
        registerReceiver(mRoutingReceiver, intentFilter);
        registerReceiver(mUsbReceiver, intentFilter);
        registerReceiver(mPreferenceUpdateReceiver, new IntentFilter(DSPManager.ACTION_UPDATE_PREFERENCES));
        final IntentFilter btFilter = new IntentFilter();
        btFilter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        btFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBtReceiver,btFilter);
        // 音频管理器
        AudioManager mAudioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        if (mAudioManager != null)
        {
            mUseBluetooth = mAudioManager.isBluetoothA2dpOn();
            if (mUseBluetooth)
            {
                Log.i(DSPManager.TAG, "Bluetooth mode");
                mUseHeadset = false;
            }
            else
            {
                mUseHeadset = mAudioManager.isWiredHeadsetOn();
                if (mUseHeadset)
                    Log.i(DSPManager.TAG, "Headset mode");
                else if (mUseUsb)
                    Log.i(DSPManager.TAG,"USB mode");
                else
                    Log.i(DSPManager.TAG, "Speaker mode");
            }
        }
        preferencesMode = getSharedPreferences(DSPManager.SHARED_PREFERENCES_BASENAME + "." + "settings", 0);
        if (!preferencesMode.contains("dsp.manager.modeEffect")) {
            preferencesMode.edit().putInt("dsp.manager.modeEffect",0).apply();
        }
        modeEffect = preferencesMode.getInt("dsp.manager.modeEffect",0);
        if (MDSPGbEf != null){
            MDSPGbEf.release();
            MDSPGbEf = null;
        }
        if (modeEffect == 0){
            if (MDSPGbEf == null){
                MDSPGbEf = new MDSPModule(0);
            }
            if (MDSPGbEf.DSPcompression == null){
                Toast.makeText(HeadsetService.this,"Library load failed(Global effect", Toast.LENGTH_SHORT).show();
                MDSPGbEf.release();
                MDSPGbEf = null;
            }
        }
        updateDsp(true);
    }

    // 销毁
    @Override
    public void onDestroy(){
        super.onDestroy();
        unregisterReceiver(mAudioSessionReceiver);
        unregisterReceiver(mRoutingReceiver);
        unregisterReceiver(mUsbReceiver);
        unregisterReceiver(mBtReceiver);
        unregisterReceiver(mPreferenceUpdateReceiver);
        mAudioSessions.clear();
        if (MDSPGbEf != null)
        {
            MDSPGbEf.release();
        }
        MDSPGbEf = null;
    }

    // 添加启动方法
    @Override
    public int onStartCommand(Intent intent,int flags,int startId){
        modeEffect = preferencesMode.getInt("dsp.manager.modeEffect", 0);
        if (modeEffect == 0)
        {
            if (MDSPGbEf == null) {
                MDSPGbEf = new MDSPModule(0);
                if (MDSPGbEf.DSPcompression == null) {
                    Log.e(DSPManager.TAG, "Global audio session load fail, reload it now!");
                    MDSPGbEf.release();
                    MDSPGbEf = null;
                    return super.onStartCommand(intent, flags, startId);
                }
                updateDsp(false);
                return super.onStartCommand(intent, flags, startId);
            }
            if (MDSPGbEf.DSPcompression == null) {
                Log.e(DSPManager.TAG, "Global audio session load fail, reload it now!");
                MDSPGbEf.release();
                MDSPGbEf = new MDSPModule(0);
                if (MDSPGbEf.DSPcompression == null) {
                    Log.e(DSPManager.TAG, "Global audio session load fail, reload it now!");
                    MDSPGbEf.release();
                    MDSPGbEf = null;
                    return super.onStartCommand(intent, flags, startId);
                }
                return super.onStartCommand(intent, flags, startId);
            }
            Log.i(DSPManager.TAG, "Global audio session created!");
            updateDsp(false);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    // 创建公用进程通信→时刻更新均衡器中的状态
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

    // 创建均衡器波段调度方法
    public void setEqualizerLevels(float[] levels) {
        mOverriddenEqualizerLevels = levels;
        updateDsp(false);
    }

    private float[] eqLevels = new float[6];

    /* 一手叮 没用的玩意
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

    // 检测音频输出设备
    public static String getAudioOutputRouting(){
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

    /**
     * Push new configuration to audio stack.
     */
    // updateDsp方法
    protected void updateDsp(boolean useRefresh){
        modeEffect = preferencesMode.getInt("dsp.manager.modeEffect", 0);
        final String mode = getAudioOutputRouting();
        SharedPreferences preferences = getSharedPreferences(DSPManager.SHARED_PREFERENCES_BASENAME + "." + mode, 0);
        // 建立intent，发送广播耳机插入状态标识符
        // 增加判断标识
        if (useRefresh) {
            Intent intent = new Intent("dsp.activity.refreshPage");
            sendBroadcast(intent);
        }
        if (modeEffect == 0){
            try{
                updateDsp(preferences,MDSPGbEf,0);
            }catch (Exception e){
                // 此处留空
            }
        }
        else{
            for (Integer sessionId : new ArrayList<Integer>(mAudioSessions.keySet())){
                try {
                    updateDsp(preferences,mAudioSessions.get(sessionId),sessionId);
                }catch (Exception e){
                    // 记录错误日志
                    Log.w(TAG, String.format(
                            "Trouble trying to manage session %d, removing...", sessionId), e);
                    mAudioSessions.remove(sessionId);
                }
            }
        }
        Log.i(TAG, "Selected configuration: " + mode);
    }

    // 定义音效渲染
    private void updateDsp(SharedPreferences preferences,MDSPModule session,int sessionId){
        // 动态范围压缩
        session.DSPcompression.setEnabled(preferences.getBoolean("dsp.compression.enable", false));
        MDSPGbEf.setParameter(session.DSPcompression, 0,
                Short.valueOf(preferences.getString("dsp.compression.mode", "0")));

        // 低音
        session.mBassBoost.setEnabled(preferences.getBoolean("dsp.bass.enable",false));
        session.mBassBoost.setStrength(Short.valueOf(preferences.getString("dsp.bass.mode","0")));
        // 低音频点
        short freq = Short.valueOf(preferences.getString("dsp.bassboost.freq", "55"));
        session.setParameter(session.mBassBoost,133, freq);

        // 均衡器
        session.mEqualizer.setEnabled(preferences.getBoolean("dsp.tone.enable", false));
        if (mOverriddenEqualizerLevels != null){
            for (short i = 0; i < mOverriddenEqualizerLevels.length; i++) {
                eqLevels[i] = mOverriddenEqualizerLevels[i];
                session.mEqualizer.setBandLevel(i, (short) Math.round(eqLevels[i] * 100));
            }
        }
        else {
            String[] levels = preferences.getString("dsp.tone.eq.custom", "0.0;0.0;0.0;0.0;0.0;0.0").split(";");
            for (short i = 0; i < levels.length; i++) {
                eqLevels[i] = Float.valueOf(levels[i]);
                session.mEqualizer.setBandLevel(i, (short) Math.round(eqLevels[i] * 100));
            }
        }
        // 响度补偿
        MDSPGbEf.setParameter(session.mEqualizer,1000,Short.valueOf(preferences.getString("dsp.tone.loudness", "10000")));

        // 空间立体声
        session.mVirtualizer.setEnabled(preferences.getBoolean("dsp.headphone.enable", false));
        session.mVirtualizer.setStrength(
                Short.valueOf(preferences.getString("dsp.headphone.mode", "0")));
    }
}