package com.cyanogenmod.dspmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import com.cyanogenmod.dspmanager.service.EffectService

/**
 * This receiver starts our {@link HeadsetService} after system boot. Since
 * Android 2.3, we will always need a persistent process, because we are forced
 * to keep track of all open audio sessions.
 *
 * @author alankila
 */
class BootCompletedReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        /* 启用音效服务 */
        if (intent?.action == ACTION_BOOT_COMPLETED)
            context?.startService(Intent(context, EffectService::class.java))
    }
}