package com.cyanogenmod.dspmanager.utils

import com.cyanogenmod.dspmanager.R

class PreferenceConfig {
    companion object {
        /* 显示页面(受人为和广播接收器控制) */
        var prefPage: Int = 1
        /* 正在应用的配置页面 */
        var effectedPage: Int = 1
        private const val PAGE_HEADSET = "headset"
        const val PAGE_SPEAKER = "speaker"
        private const val PAGE_BLUETOOTH = "bluetooth"
        private const val PAGE_USB = "usb"

        /* 更新页面配置 */
        const val ACTION_UPDATE_PREFERENCES = "com.cyanogenmod.dspmanager.action.PERF_UPDATE"
        /* 切换页面 */
        const val ACTION_SWITCH_PREF_PAGE = "com.cyanogenmod.dspmanager.action.PAGE_SWITCH"
        /* 配置文件夹 */
        const val PRESETS_FOLDER = "DSPPresets"
        /* 应用包名称 */
        const val PACKAGE_NAME = "com.cyanogenmod.dspmanager"
        /* 应用内设置 */
        const val APP_SETTINGS = "com.cyanogenmod.dspmanager.app_settings"
        /* 均衡器设置 */


        /* 获取配置页面 */
        fun getPrefPage(): String {
            return when(prefPage) {
                1 -> PAGE_SPEAKER
                2 -> PAGE_BLUETOOTH
                3 -> PAGE_USB
                else -> PAGE_HEADSET
            }
        }

        /* 获取音效页面 */
        fun getEffectPage(): String {
            return when(effectedPage) {
                1 -> PAGE_SPEAKER
                2 -> PAGE_BLUETOOTH
                3 -> PAGE_USB
                else -> PAGE_HEADSET
            }
        }

        /* 获取对应配置页面的资源ID */
        fun getPrefPageID(): Int {
            return when(prefPage) {
                1 -> R.xml.preferences_speaker
                2 -> R.xml.preferences_bluetooth
                3 -> R.xml.preferences_usb
                else -> R.xml.preferences_headset
            }
        }

        /* 获取对应配置页面名称的字符串ID */
        fun getPageStringID(): Int {
            return when(prefPage) {
                1 -> R.string.nav_title_speaker
                2 -> R.string.nav_title_bluetooth
                3 -> R.string.nav_title_usb
                else -> R.string.nav_title_headset
            }
        }

        /* 获取侧边栏中的菜单ID */
        fun getNaviMenuID(): Int {
            return when(prefPage) {
                1 -> R.id.nav_speaker
                2 -> R.id.nav_bluetooth
                3 -> R.id.nav_usb_dok
                else -> R.id.nav_headset
            }
        }
    }
}