package com.cyanogenmod.dspmanager.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.widget.addTextChangedListener
import androidx.drawerlayout.widget.DrawerLayout
import com.cyanogenmod.dspmanager.R
import com.cyanogenmod.dspmanager.databinding.ActivityMainBinding
import com.cyanogenmod.dspmanager.fragment.DSPScreenFragment
import com.cyanogenmod.dspmanager.service.EffectService
import com.cyanogenmod.dspmanager.utils.PreferenceConfig
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path


class DSPMainActivity: AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
    }
    /* 创建视图绑定 */
    private lateinit var binding: ActivityMainBinding
    /* 创建侧滑菜单效果 */
    private lateinit var drawerLayout: DrawerLayout
    /* 创建侧边导航栏 */
    private lateinit var naviView: NavigationView
    /* 创建顶栏 */
    private lateinit var materialToolbar: MaterialToolbar
    /* 创建顶栏与侧边栏关联工具 */
    private lateinit var actionToggle: ActionBarDrawerToggle
    /* 创建导航栏高度 */
    private var navigationBarHeight: Int = 0

    /* 切换配置页面
     * 同时判断是否为人为操作 */
    private fun switchPrefPage(isManual: Boolean, pagePos: Int) {
        /* 获取旧页面 */
        val beforeFragment = supportFragmentManager.findFragmentByTag(PreferenceConfig.getPrefPage()) as? DSPScreenFragment
        /* 更新页面 */
        PreferenceConfig.prefPage = pagePos
        /* 更新侧边栏 */
        if (!isManual) {
            naviView.menu.findItem(PreferenceConfig.getNaviMenuID()).isChecked = true
        }
        var screenFragment = supportFragmentManager.findFragmentByTag(PreferenceConfig.getPrefPage()) as? DSPScreenFragment
        if (screenFragment == null) {
            screenFragment = DSPScreenFragment()
            supportFragmentManager.beginTransaction().add(R.id.pref_container, screenFragment, PreferenceConfig.getPrefPage()).commit()
        }

        materialToolbar.title = getString(PreferenceConfig.getPageStringID())

        if (beforeFragment != null) {
            beforeFragment.onPause()
            supportFragmentManager.beginTransaction().hide(beforeFragment).show(screenFragment).commit()
            Log.d(TAG, "switchPrefPage: switched page")
        }
        else {
            supportFragmentManager.beginTransaction().show(screenFragment).commit()
        }
    }

    /* 创建页面切换广播接收器 */
    private val pageSwitchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == PreferenceConfig.ACTION_SWITCH_PREF_PAGE) {
                switchPrefPage(false, PreferenceConfig.effectedPage)
            }
        }
    }

    /* 状态栏透明 */
    private fun transStatusBar() {
        /* 状态栏透明 */
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT in 26..29) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        } else {
            window.setDecorFitsSystemWindows(false)
        }
    }

    /* 导航栏设置视图高度 */
    private fun navigationBarHeight() {
        binding.root.setOnApplyWindowInsetsListener { _, insets ->
            navigationBarHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            (binding.root.layoutParams as FrameLayout.LayoutParams).apply { bottomMargin = navigationBarHeight }
            insets
        }
//        window.navigationBarColor = getColor(android.R.color.white)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
//        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
//            window.decorView.
//        }
        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        transStatusBar()
        navigationBarHeight()


        /* 设置顶栏 */
        materialToolbar = binding.containerToolbar
        setSupportActionBar(materialToolbar)
        /* 取消Toolbar默认标题 */
        supportActionBar!!.setDisplayShowTitleEnabled(false)

        /* 设置侧边栏 */
        drawerLayout = binding.drawerLayout.root
        /* 设置侧边菜单遮罩阴影 */
        drawerLayout.setScrimColor(getColor(R.color.shadow))
        naviView = binding.drawerLayout.navView

        actionToggle = object : ActionBarDrawerToggle(this, drawerLayout, materialToolbar,
            R.string.nav_drawer_open, R.string.nav_drawer_close) {
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                materialToolbar.title = getString(R.string.app_name)
            }

            override fun onDrawerClosed(drawerView: View) {
                super.onDrawerClosed(drawerView)
                materialToolbar.title = getString(PreferenceConfig.getPageStringID())
            }
        }
        drawerLayout.addDrawerListener(actionToggle)

        naviView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_headset -> {
                    switchPrefPage(true, 0)
                }
                R.id.nav_speaker -> {
                    switchPrefPage(true, 1)
                }
                R.id.nav_bluetooth -> {
                    switchPrefPage(true, 2)
                }
                R.id.nav_usb_dok -> {
                    switchPrefPage(true, 3)
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        /* 注册页面更新广播接收器 */
        val pageSwitchIntentFilter = IntentFilter(PreferenceConfig.ACTION_SWITCH_PREF_PAGE)
        registerReceiver(pageSwitchReceiver, pageSwitchIntentFilter)

        /* 注册开启服务 */
        val serviceIntent = Intent(this, EffectService::class.java)
        startService(serviceIntent)

        /* 页面设置 */
        switchPrefPage(false, PreferenceConfig.effectedPage)
    }

    /* 活动销毁(注: 不应该注销服务, 否则没任何意义) */
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pageSwitchReceiver)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        /* 将顶栏与侧边栏进行联动 */
        actionToggle.syncState()
    }

    /* 右侧菜单设置 */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    /* 项目点击 */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings_save_preset
                /* 保存配置 */ -> { savePresetDialog() }
            R.id.settings_load_preset
                /* 加载配置 */ -> { loadPresetDialog() }
            R.id.settings_help
                /* 软件帮助 */ -> {
                val helpScrollView = layoutInflater.inflate(R.layout.app_help, findViewById(android.R.id.content), false)
                val helpTextView = helpScrollView.findViewById<TextView>(R.id.tv_help)
                helpTextView.text = getString(R.string.help_text)
                val alterDialog = MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.help_title))
                    .setView(helpScrollView)
                    .setCancelable(false)
                    .setPositiveButton(getString(android.R.string.ok)) { _, _ -> }
                    .create()
                alterDialog.show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /* 权限获取结果 */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /* 保存配置对话框 */
    private fun savePresetDialog() {
        /* 创建文件实例 */
        val presetDir = getExternalFilesDir("DspPresets")!!
        /* 判断文件夹是否存在, 若不存在则创建 */
        var dirCreated = true
        if (!presetDir.exists())
            dirCreated = presetDir.mkdir()
        if (dirCreated) {
            /* The first entry is "New preset", so we offset */
            /* 第一个选项用来创建新预设, 剩下的预设选项从第二个开始 */
            val presetFiles = presetDir.listFiles(null as FileFilter?)
            val presetNames = Array(if (presetFiles == null) 1 else presetFiles.size + 1) {""}

            presetNames[0] = getString(R.string.create_preset)
            if (presetFiles != null) {
                for (i in presetFiles.indices)
                    presetNames[i + 1] = presetFiles[i].name
            }

            /* 保存预设对话框 */
            val presetAlertDialog = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_save_preset))
                .setItems(presetNames) { _, index ->
                    if (index == 0) {
                        /* 新建预设 */

                        /* 创建输入框 */
                        val inputFieldLayout = layoutInflater.inflate(R.layout.preset_input, findViewById(android.R.id.content), false)
                        val textInputLayout = inputFieldLayout.findViewById<TextInputLayout>(R.id.text_input_layout)
                        val editText = textInputLayout.editText!!

                        val createdAlertDialog = MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.create_preset))
                            .setView(inputFieldLayout)
                            .setCancelable(false)
                            .setPositiveButton(getString(android.R.string.ok)){ _, _ ->
                                val presetName = editText.text.toString()
                                savePreset(presetName = presetName)
                            }
                            .setNegativeButton(getString(android.R.string.cancel)) { _, _ -> }
                            .create()

                        createdAlertDialog.show()
                        editText.requestFocus()
                        /* 设置输入框监听 */
                        editText.addTextChangedListener(
                            onTextChanged = { text, _, _, _ ->
                                /* 非法字符 */
                                val hasIllegalCharacters =
                                    text!!.toString().matches(Regex(".*[/<>*?|\"].*"))
                                /* 错误提示1: 字符串中含有非法字符 */
                                if (hasIllegalCharacters) {
                                    textInputLayout.error = getString(R.string.name_err)
                                    createdAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility =
                                        View.INVISIBLE
                                } else if (text.toString().first() == ' ') {
                                    /* 错误提示2: 首个字符为空格 */
                                    textInputLayout.error = getString(R.string.space_first_err)
                                    createdAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility =
                                        View.INVISIBLE
                                } else {
                                    textInputLayout.error = ""
                                    createdAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility =
                                        View.VISIBLE
                                }
                            }
                        )
                    }
                    else
                    {
                        /* 覆盖预设 */
                        /* 确认覆盖对话框 */
                        val confirmAlertDialog = MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.confirm_overwrite))
                            .setMessage(getString(R.string.confirm_message))
                            .setCancelable(false)
                            .setPositiveButton(getString(android.R.string.ok)) {_, _ -> savePreset(presetName = presetNames[index])}
                            .setNegativeButton(getString(android.R.string.cancel)) {_, _ -> }
                            .create()
                        confirmAlertDialog.show()
                    }
                }
                .create()
            /* 显示对话框 */
            presetAlertDialog.show()
        }
    }

    /* 文件复制方法 (旧方法, 安卓8.0以上用不到) */
    @Suppress("UNUSED")
    private fun copy(input: File, output: File) {
        try {
            val inputStream = FileInputStream(input)
            val outputStream = FileOutputStream(output)

            /* 传输缓存 */
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0)
                outputStream.write(buffer, 0, length)

            outputStream.close()
            inputStream.close()
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.copy_err), Toast.LENGTH_SHORT).show()
        }
    }

    /* 保存配置方法 */
    private fun savePreset(presetName: String) {
        /* 应用内置配置目录 */
        val spDir = dataDir.absolutePath + "/shared_prefs/"

        /* 导出配置的文件夹目录 */
        val outputDirString = getExternalFilesDir("DspPresets")!!.absolutePath + "/" + presetName
        val outputDir = File(outputDirString)
        var outputDirCreated = true
        if (!outputDir.exists())
            outputDirCreated = outputDir.mkdir()
        if (outputDirCreated) {
            /* 尝试复制文件 */
            try { /* 耳机 */
                val sourcePath = Path("${spDir}${PreferenceConfig.PACKAGE_NAME}.headset.xml")
                val targetPath = Path("${outputDir.absolutePath}/headset.xml")
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                val errMsg = String.format(getString(R.string.copy_err), getString(R.string.nav_title_headset))
                Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
            }
            try { /* 扬声器 */
                val sourcePath = Path("${spDir}${PreferenceConfig.PACKAGE_NAME}.speaker.xml")
                val targetPath = Path("${outputDir.absolutePath}/speaker.xml")
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                val errMsg = String.format(getString(R.string.copy_err), getString(R.string.nav_title_speaker))
                Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
            }
            try { /* 蓝牙 */
                val sourcePath = Path("${spDir}${PreferenceConfig.PACKAGE_NAME}.bluetooth.xml")
                val targetPath = Path("${outputDir.absolutePath}/bluetooth.xml")
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                val errMsg = String.format(getString(R.string.copy_err), getString(R.string.nav_title_bluetooth))
                Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
            }
            try { /* usb音频 */
                val sourcePath = Path("${spDir}${PreferenceConfig.PACKAGE_NAME}.usb.xml")
                val targetPath = Path("${outputDir.absolutePath}/usb.xml")
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                val errMsg = String.format(getString(R.string.copy_err), getString(R.string.nav_title_usb))
                Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
            }

            Toast.makeText(this, getString(R.string.preset_save_finished), Toast.LENGTH_SHORT).show()
        }
    }

    /* 加载配置对话框 */
    private fun loadPresetDialog() {
        /* 创建文件实例 */
        val presetDir = getExternalFilesDir("DspPresets")!!
        /* 判断文件夹是否存在, 若不存在则创建 */
        var dirCreated = true
        if (!presetDir.exists())
            dirCreated = presetDir.mkdir()
        if (dirCreated) {
            /* 浏览文件夹内配置目录 */
            val presetFiles = presetDir.listFiles(null as FileFilter?)
            val presetNames = Array(presetFiles?.size ?: 0) {""}

            /* 设置配置名称字符串数组 */
            if (presetFiles != null) {
                for (i in presetFiles.indices)
                    presetNames[i] = presetFiles[i].name
            }

            /* 加载配置, 设置配置加载对话框 */
            val alertDialog = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.load_preset))
                .setItems(presetNames) { _, index ->
                    loadPreset(presetName = presetNames[index])
                }
                .create()
                /* 显示对话框 */
            alertDialog.show()
        }
    }

    /* 加载配置方法 */
    private fun loadPreset(presetName: String) {
        /* 应用内置配置目录 */
        val spDir = dataDir.absolutePath + "/shared_prefs/"
        /* 外部保存配置目录 */
        val presetDir = getExternalFilesDir("DspPresets")!!.absolutePath + "/" + presetName

        /* 复制配置到应用内置配置目录中 */
        try { /* 耳机 */
            val sourcePath = Path("${presetDir}/headset.xml")
            val targetPath = Path("${spDir}${PreferenceConfig.PACKAGE_NAME}.headset.xml")
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            val errMsg = String.format(getString(R.string.copy_err), getString(R.string.nav_title_headset))
            Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
        }

        try { /* 扬声器 */
            val sourcePath = Path("${presetDir}/speaker.xml")
            val targetPath = Path("${spDir}${PreferenceConfig.PACKAGE_NAME}.speaker.xml")
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            val errMsg = String.format(getString(R.string.copy_err), getString(R.string.nav_title_speaker))
            Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
        }

        try { /* 蓝牙 */
            val sourcePath = Path("${presetDir}/bluetooth.xml")
            val targetPath = Path("${spDir}${PreferenceConfig.PACKAGE_NAME}.bluetooth.xml")
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            val errMsg = String.format(getString(R.string.copy_err), getString(R.string.nav_title_bluetooth))
            Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
        }

        try { /* usb音频输出 */
            val sourcePath = Path("${presetDir}/usb.xml")
            val targetPath = Path("${spDir}${PreferenceConfig.PACKAGE_NAME}.usb.xml")
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            val errMsg = String.format(getString(R.string.copy_err), getString(R.string.nav_title_usb))
            Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
        }

        /* 显示配置加载 */
        Toast.makeText(this, getString(R.string.preset_load_finished), Toast.LENGTH_SHORT).show()
        /* 重新加载配置 */
        startActivity(Intent(this, DSPMainActivity::class.java))
        finish()
    }

}