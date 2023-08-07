package com.cyanogenmod.dspmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.IBinder
import android.util.Log
import com.cyanogenmod.dspmanager.R
import com.cyanogenmod.dspmanager.activity.DSPMainActivity
import com.cyanogenmod.dspmanager.utils.DSPModule
import com.cyanogenmod.dspmanager.utils.PreferenceConfig
import kotlin.math.ceil
import kotlin.math.round

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

class EffectService: Service() {
    companion object {
        private const val TAG = "EffectService"
    }

    private var dspModule: DSPModule ?= null

    /* 通过广播接收器更新音效罢(悲) */
    private fun updateEffectByReceiver() {
        Log.d(TAG, "updateEffectByReceiver: updated preset ${dspModule == null}")
        val pref = getSharedPreferences("${PreferenceConfig.PACKAGE_NAME}.${PreferenceConfig.getEffectPage()}", Context.MODE_PRIVATE)
        /* 效果总开关 */
        enableEffect(pref.getBoolean("dsp.${PreferenceConfig.getEffectPage()}.main.switch", false))
        if (PreferenceConfig.getEffectPage() != PreferenceConfig.PAGE_SPEAKER) {
            /* 动态范围压缩总开关 */
            enableDRC(pref.getBoolean("dsp.${PreferenceConfig.getEffectPage()}.compression.enable", false))
            /* 动态范围压缩强度 */
            setDrcValue(pref.getInt("dsp.${PreferenceConfig.getEffectPage()}.compression.values", 5))
            /* 响度补偿开关 */
            enableLoudness(pref.getBoolean("dsp.${PreferenceConfig.getEffectPage()}.eq.loudness.enable", false))
            /* 响度补偿强度 */
            setLoudnessStrength(pref.getInt("dsp.${PreferenceConfig.getEffectPage()}.eq.loudness.strength", 0))
            /* 空间混响开关 */
            enableVirtualizer(pref.getBoolean("dsp.${PreferenceConfig.getEffectPage()}.virtualize.enable", false))
            /* 空间混响类型 */
            setVirtualizerType(pref.getString("dsp.${PreferenceConfig.getEffectPage()}.virtualize.type","0")!!.toInt())
        } else {
            /* 关闭动态范围压缩 */
            enableDRC(false)
            /* 关闭响度补偿 */
            enableLoudness(false)
            /* 关闭空间音频 */
            enableVirtualizer(false)
        }
        /* 低频增益总开关 */
        enableBassBoost(pref.getBoolean("dsp.${PreferenceConfig.getEffectPage()}.bass.boost.enable", false))
        /* 低频增益频点 */
        setBassFreqPoint(pref.getInt("dsp.${PreferenceConfig.getEffectPage()}.bass.boost.freq.point", 20))
        /* 低频增益强度 */
        setBassBoostStrength(pref.getInt("dsp.${PreferenceConfig.getEffectPage()}.bass.boost.freq.strength", 0))
        /* 均衡器总开关 */
        enableEqualizer(pref.getBoolean("dsp.${PreferenceConfig.getEffectPage()}.tone.eq.enable", false))
    }

    /* 创建音效广播接收器 */
    private val audioEffectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

        }
    }

    /* 创建蓝牙广播接收器 */
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive: Bluetooth Receiver")
            val action = intent?.action
            if (action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                val bluetoothState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                    BluetoothProfile.STATE_CONNECTED)
                /* 监听蓝牙耳机接入 */
                if (bluetoothState == BluetoothProfile.STATE_CONNECTED) {
                    PreferenceConfig.effectedPage = 2
                    val pageSwitchIntent = Intent(PreferenceConfig.ACTION_SWITCH_PREF_PAGE)
                    sendBroadcast(pageSwitchIntent)
                    /* 更新音效加载 */
                    updateEffectByReceiver()
                }
            }

//            else if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
//                val bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
//                if (bluetoothState == BluetoothAdapter.STATE_ON)
//                    mRoutingListener?.onRoutingChanged(1)
//            }
        }
    }

    /* 创建耳机广播接收器 */
    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive: Headset receiver")
            if (intent?.action == AudioManager.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                /* 检测到耳机已插入 */
                if (state == 1) {
                    Log.d(TAG, "onReceive: headset Plugged")
                    PreferenceConfig.effectedPage = 0
                } else {
                    Log.d(TAG, "onReceive: headset Unplugged")
                    PreferenceConfig.effectedPage = 1
                }
                val pageSwitchIntent = Intent(PreferenceConfig.ACTION_SWITCH_PREF_PAGE)
                sendBroadcast(pageSwitchIntent)
                /* 更新音效加载 */
                updateEffectByReceiver()
            }
        }
    }

    /* 创建USB广播接收器 */
    private val usbReceiver = object : BroadcastReceiver() {
        /* 判断是否为USB音频输出 */
        private fun isAudioDevice(device: UsbDevice): Boolean {
            return device.deviceClass == UsbConstants.USB_CLASS_AUDIO &&
                    device.deviceProtocol == 0
        }
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive: Usb Receiver")
            val action = intent?.action
            /* 检测到USB已被插入 */
            if (action == UsbManager.ACTION_USB_ACCESSORY_ATTACHED) {
                /* 检查插入是否为音频设备 */
                val usbDevice = if (SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) else
                    @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                if (usbDevice != null && isAudioDevice(usbDevice)) {
                    PreferenceConfig.effectedPage = 3
                    val pageSwitchIntent = Intent(PreferenceConfig.ACTION_SWITCH_PREF_PAGE)
                    sendBroadcast(pageSwitchIntent)
                    /* 更新音效 */
                    updateEffectByReceiver()
                }
            }
        }
    }

    /** 通知点击返回退出前的活动  */
    private fun pendingIntentActivity(): PendingIntent? {
        val intent = Intent(this, DSPMainActivity::class.java)
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        return PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /* 创建通知 */
    private fun showNotification() {
        val notification = Notification.Builder(this, "Cyanogenmod DSP Manager")
            .setSmallIcon(R.drawable.ic_main_launcher_foreground)
            .setTicker("DSP Manager")
            .setContentTitle("DSPManager is running")
            .setContentIntent(pendingIntentActivity())
            .setAutoCancel(false)
            .setOngoing(true)
            .setWhen(System.currentTimeMillis())
            .build()

        /* 启动前台服务 */
        startForeground(14, notification)
    }

    /* 创建通知渠道 */
    private fun initNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val importance = NotificationManager.IMPORTANCE_LOW
        val notificationChannel = NotificationChannel("Cyanogenmod DSP Manager", "DSP Manager Notify", importance)
        notificationChannel.description = "Keep DSP Manager alive"
        notificationManager.createNotificationChannel(notificationChannel)
    }

    /* 效果总开关 */
    fun enableEffect(enable: Boolean) {
        dspModule?.dspMainEffect?.enabled = enable
    }

    /* 开启动态范围压缩 */
    fun enableDRC(enable: Boolean) {
        dspModule?.setSpecParam(dspModule?.dspMainEffect!!, 100, if (enable) 1 else 0)
    }

    /* 设置动态范围压缩强度 */
    fun setDrcValue(strength: Int) {
        dspModule?.setSpecParam(dspModule?.dspMainEffect!!, 101, strength.toShort())
    }

    /* 开启低音增益 */
    /* 后面的开启估计需要使用 */
    fun enableBassBoost(enable: Boolean) {
        dspModule?.setSpecParam(dspModule?.dspMainEffect!!, 102, if (enable) 1 else 0)
    }

    /* 设置低音频点 */
    fun setBassFreqPoint(point: Int) {
        dspModule?.setSpecParam(dspModule?.dspMainEffect!!, 104, point.toShort())
    }

    /* 设置低音增益强度 */
    fun setBassBoostStrength(strength: Int) {
        val realValue = ceil(1000f * (strength.toFloat() / 100)).toInt().toShort()
        dspModule?.setSpecParam(dspModule?.dspMainEffect!!, 103, realValue)
    }

    /* 设置均衡器开关 */
    fun enableEqualizer(enable: Boolean) {
        dspModule?.setSpecParam(dspModule?.dspMainEffect!!, 105, if (enable) 1 else 0)
    }

    /* 设置均衡器单个频段条值 */
    fun setEqSingleBand(bandIndex: Int, bandLevel: Float) {
        dspModule?.setEqualizerParam(dspModule?.dspMainEffect!!, bandIndex, round(bandLevel * 100f).toInt().toShort())
    }

    /* 响度补偿开关 */
    fun enableLoudness(enable: Boolean) {
        dspModule?.setSpecParam(dspModule?.dspMainEffect!!, 107, if (enable) 1 else 0)
    }

    /* 设置响度补偿强度 */
    fun setLoudnessStrength(strength: Int) {
        val realValue = ceil(10000f - (7500 * (strength.toFloat() / 100f))).toInt().toShort()
        dspModule?.setSpecParam(dspModule?.dspMainEffect!!, 108, realValue)
    }

    /* 开启空间混响 */
    fun enableVirtualizer(enable: Boolean) {
        dspModule?.setSpecParam(dspModule?.dspMainEffect!!, 109, if (enable) 1 else 0)
    }

    /* 设置空间混响类型 */
    fun setVirtualizerType(type: Int) {
        dspModule?.setSpecParam(dspModule?.dspMainEffect!!, 111, type.toShort())
    }

    /* 服务绑定创建 */
    inner class EffectBinder: Binder() {
        /* 获取服务实例(感觉用不到了) */
        fun getService(): EffectService {
            return this@EffectService
        }
    }

    /* 服务创建 */
    override fun onCreate() {
        initNotificationChannel()
        super.onCreate()
        Log.d(TAG, "onCreate: ")
        /* 注册蓝牙连接广播接收器 */
        val bluetoothFilter = IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        registerReceiver(bluetoothReceiver, bluetoothFilter)

        /* 注册耳机插入广播接收器 */
        val headsetFilter = IntentFilter(AudioManager.ACTION_HEADSET_PLUG)
        registerReceiver(headsetReceiver, headsetFilter)

        /* 注册USB音频解码接收器 */
        val usbFilter = IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        registerReceiver(usbReceiver, usbFilter)

        /* 开始创建DSPModule动态共享库 */
        dspModule = DSPModule(0)          /* 全局使用 */
        showNotification()
    }

    /* 在销毁方法里注销广播接收器 */
    override fun onDestroy() {
        super.onDestroy()
        /* 注销蓝牙接入广播接收器 */
        unregisterReceiver(bluetoothReceiver)
        /* 注销USB音频解码接收器 */
        unregisterReceiver(usbReceiver)
        /* 注销耳机插入广播接收器 */
        unregisterReceiver(headsetReceiver)
        /* 销毁音效 */
        dspModule?.releaseModule()
        dspModule = null
    }

    private val mEffectBinder = EffectBinder()

    /* 服务绑定方法 */
    override fun onBind(intent: Intent?): IBinder { return mEffectBinder }
}