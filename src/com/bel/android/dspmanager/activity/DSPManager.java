/*
 * Modifications Copyright (C) 2013 The OmniROM Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.bel.android.dspmanager.activity;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.legacy.app.FragmentPagerAdapter;
import androidx.viewpager.widget.PagerTabStrip;
import androidx.viewpager.widget.ViewPager;

import com.bel.android.dspmanager.R;
import com.bel.android.dspmanager.service.HeadsetService;
import com.bel.android.dspmanager.widgets.CustomDrawerLayout;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Setting utility for CyanogenMod's DSP capabilities. This page is displays the
 * activity_main-level configurations menu.
 *
 * @author alankila@gmail.com
 */
public final class  DSPManager extends Activity {

    //==================================
    // Static Fields
    //==================================
    public static final String TAG = "DSPManager";
    private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";
    private static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learned";
    private static final String PREF_IS_TABBED = "pref_is_tabbed";
    //==================================
    public static final String SHARED_PREFERENCES_BASENAME = "com.bel.android.dspmanager";
    public static final String ACTION_UPDATE_PREFERENCES = "com.bel.android.dspmanager.UPDATE";
    private static final String PRESETS_FOLDER = "DSPPresets";
    // 音频输出类型（音频路由）
    private static int routing;
    // 人为跳转
    public static int manualPosition;
    // 音频渲染模式
    public static int effectMode;
    //==================================
    private static String[] mEntries;
    private static List<HashMap<String, String>> mTitles;


    //==================================
    // Drawer
    //==================================
    private ActionBarDrawerToggle mDrawerToggle;
    private CustomDrawerLayout mDrawerLayout;
    private ListView mDrawerListView;
    private View mFragmentContainerView;
    private int mCurrentSelectedPosition = 0;
    private boolean mFromSavedInstanceState;
    private boolean mUserLearnedDrawer;

    //==================================
    // ViewPager
    //==================================
    protected MyAdapter pagerAdapter;
    protected ViewPager viewPager;
    protected PagerTabStrip pagerTabStrip;

    //==================================
    // Fields
    //==================================
    private SharedPreferences mPreferences,preferencesEffectMode;
    private boolean mIsTabbed = true;
    private CharSequence mTitle;


    // 添加存储、电话权限获取声明
    private void getPermission(){
        List<String> permissionLists = new ArrayList<>();
        // 添加权限获取弹窗
        // 存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            permissionLists.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            //ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        // 电话权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED){
            permissionLists.add(Manifest.permission.READ_PHONE_STATE);
            //ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_PHONE_STATE}, 1);
        }
        // requestCode = 1 开启权限
        if (!permissionLists.isEmpty()){
            ActivityCompat.requestPermissions(this,permissionLists.toArray(new String[0]),1);
        }
    }


    //==================================
    // Overridden Methods
    //==================================

    // 更新耳机插拔
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            String action = intent.getAction();
            if("dsp.activity.refreshPage".equals(action)) {
                if (HeadsetService.mUseBluetooth) {
                    routing = 2;
                } else if (HeadsetService.mUseHeadset) {
                    routing = 0;
                } else if (HeadsetService.mUseUsb) {
                    routing = 3;
                } else {
                    routing = 1;
                }
                selectItem(routing);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 音频输出配置
        mPreferences = getSharedPreferences(DSPManager.SHARED_PREFERENCES_BASENAME + "." + HeadsetService.getAudioOutputRouting(), 0);
        mUserLearnedDrawer = mPreferences.getBoolean(PREF_USER_LEARNED_DRAWER, false);

        // 音频渲染模式，默认全局
        // 获取渲染模式配置文件
        preferencesEffectMode = getSharedPreferences(DSPManager.SHARED_PREFERENCES_BASENAME + "." + "pref_settings", 0);
        // 判断该文件里面有没有全局设置字样，没有就添加，并且默认模式为全局模式
        if (!preferencesEffectMode.contains("dsp.manager.effectMode")) {
            preferencesEffectMode.edit().putInt("dsp.manager.effectMode", 0).apply();
        }
        effectMode = preferencesEffectMode.getInt("dsp.manager.effectMode", 0);

        // 调用权限获取方法
        // 判断当前系统版本
        if (Build.VERSION.SDK_INT >Build.VERSION_CODES.M){
            getPermission();
        }

        if (getResources().getBoolean(R.bool.config_allow_toggle_tabbed)) {
            mIsTabbed = mPreferences.getBoolean(PREF_IS_TABBED,
                    getResources().getBoolean(R.bool.config_use_tabbed));
        } else {
            mIsTabbed = getResources().getBoolean(R.bool.config_use_tabbed);
        }

        mTitle = getTitle();

        // 导航栏
        ActionBar mActionBar = getActionBar();
        assert mActionBar != null;
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setHomeButtonEnabled(true);
        mActionBar.setDisplayShowTitleEnabled(true);

        if (savedInstanceState != null) {
            mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION);
            mFromSavedInstanceState = true;
        }

        // 启动音效服务
        Intent serviceIntent = new Intent(this, HeadsetService.class);
        startService(serviceIntent);
        sendOrderedBroadcast(new Intent(DSPManager.ACTION_UPDATE_PREFERENCES),null);
        setUpUi();

        // 动态注册页面更新
        registerReceiver(updateReceiver, new IntentFilter("dsp.activity.refreshPage"));
        if (HeadsetService.mUseBluetooth) {
            routing = 2;
        } else if (HeadsetService.mUseHeadset) {
            routing = 0;
        } else if (HeadsetService.mUseUsb) {
            routing = 3;
        } else {
            routing = 1;
        }
        // 让手动界面的值与当前页面值相等
        manualPosition = routing;
        selectItem(routing);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 音频设备检测动态注册
        registerReceiver(updateReceiver, new IntentFilter("dsp.activity.refreshPage"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 注销音频设备检测
        unregisterReceiver(updateReceiver);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mIsTabbed) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!mIsTabbed) {
            outState.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isDrawerClose()) {
            getMenuInflater().inflate(mIsTabbed ? R.menu.menu_tabbed : R.menu.menu, menu);
            if (!getResources().getBoolean(R.bool.config_allow_toggle_tabbed)) {
                menu.removeItem(R.id.dsp_action_tabbed);
            }
            getActionBar().setTitle(mTitle);
            return true;
        } else {
            getActionBar().setTitle(getString(R.string.app_name));
            return super.onCreateOptionsMenu(menu);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu){
        if (isDrawerClose()){
            if (effectMode == 0) {
                menu.findItem(R.id.dsp_effect_mode).setTitle(getString(R.string.dsp_effect_mode_title) + getString(R.string.dsp_effect_global));
            } else {
                menu.findItem(R.id.dsp_effect_mode).setTitle(getString(R.string.dsp_effect_mode_title) + getString(R.string.dsp_effect_general));
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (!mIsTabbed) {
            if (mDrawerToggle.onOptionsItemSelected(item)) {
                return true;
            }
        }

        int choice = item.getItemId();
        if (choice == R.id.help) {
            DialogFragment df = new HelpFragment();
            df.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
            df.show(getFragmentManager(), "help");
            return true;
        }
        else if (choice == R.id.save_preset) {
            savePresetDialog();
            return true;
        }
        else if (choice == R.id.load_preset) {
            loadPresetDialog();
            return true;
        }
        else if (choice == R.id.dsp_effect_mode) {
            effectMode++;
            if (effectMode > 1)
                effectMode = 0;
            preferencesEffectMode.edit().putInt("dsp.manager.effectMode", effectMode).apply();
            Intent serviceIntent = new Intent(this, HeadsetService.class);
            startService(serviceIntent);
            return true;
        }
        else if (choice == R.id.dsp_action_tabbed) {
            mIsTabbed = !mIsTabbed;
            mPreferences.edit().putBoolean(PREF_IS_TABBED, mIsTabbed).apply();
            return true;
        }
        else {
            return false;
        }

    }

    //==================================
    // Methods
    //==================================

    private void setUpUi() {

        mTitles = getTitles();
        mEntries = getEntries();

        if (!mIsTabbed) {
            setContentView(R.layout.activity_main);
            mDrawerListView = findViewById(R.id.dsp_navigation_drawer);
            mDrawerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    // 传送intent
                    manualPosition = position;
                    selectItem(position);
                }
            });

            mDrawerListView.setAdapter(new SimpleAdapter(
                    this,
                    mTitles,
                    R.layout.drawer_item,
                    new String[]{"ICON", "TITLE"},
                    new int[]{R.id.drawer_icon, R.id.drawer_title}));
            mDrawerListView.setItemChecked(mCurrentSelectedPosition, true);

            setUpNavigationDrawer(
                    findViewById(R.id.dsp_navigation_drawer),
                    findViewById(R.id.dsp_drawer_layout));

        } else {
            setContentView(R.layout.activity_main_tabbed);

            pagerAdapter = new MyAdapter(getFragmentManager());
            viewPager = findViewById(R.id.viewPager);
            viewPager.setAdapter(pagerAdapter);
            viewPager.setCurrentItem(0);
            pagerTabStrip = findViewById(R.id.pagerTabStrip);

            pagerTabStrip.setDrawFullUnderline(true);
            pagerTabStrip.setTabIndicatorColor(
                    ContextCompat.getColor(this, android.R.color.holo_blue_light));

        }
    }

    public void savePresetDialog() {
        // We first list existing presets
        File presetsDir;
        // 添加系统版本判断，如果小于安卓10，文件夹在内置存储根目录
        //                  如果大于等于安卓10，文件夹在android/data/应用名内
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            presetsDir = new File(Environment.getExternalStorageDirectory()
                    .getAbsolutePath() + "/" + PRESETS_FOLDER);
        } else {
            presetsDir = getExternalFilesDir(PRESETS_FOLDER);
        }
        presetsDir.mkdirs();

        Log.e("DSP", "Saving preset to " + presetsDir.getAbsolutePath());

        // The first entry is "New preset", so we offset
        File[] presets = presetsDir.listFiles((FileFilter) null);
        final String[] names = new String[presets != null ? presets.length + 1 : 1];
        names[0] = getString(R.string.new_preset);
        if (presets != null) {
            for (int i = 0; i < presets.length; i++) {
                names[i + 1] = presets[i].getName();
            }
        }

        // 保存设置对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(DSPManager.this);
        builder.setTitle(R.string.save_preset)
                .setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            // New preset, we ask for the name
                            AlertDialog.Builder inputBuilder =
                                    new AlertDialog.Builder(DSPManager.this);

                            inputBuilder.setTitle(R.string.new_preset);

                            // Set an EditText view to get user input
                            final EditText input = new EditText(DSPManager.this);
                            inputBuilder.setView(input);

                            inputBuilder.setPositiveButton(
                                    R.string.preset_confirm, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            String value = input.getText().toString();
                                            savePreset(value);
                                        }
                                    });
                            inputBuilder.setNegativeButton(
                                    R.string.preset_cancel, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            // Canceled.
                                            // 取消
                                        }
                                    });

                            inputBuilder.show();
                        } else {
                            savePreset(names[which]);
                        }
                    }
                });
        Dialog dlg = builder.create();
        dlg.show();
    }

    public void loadPresetDialog() {
        File presetsDir;
        // 添加系统版本判断，如果小于安卓10，文件夹在内置存储根目录
        //                  如果大于等于安卓10，文件夹在android/data/应用名内
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            presetsDir = new File(Environment.getExternalStorageDirectory()
                    .getAbsolutePath() + "/" + PRESETS_FOLDER);
        } else {
            presetsDir = getExternalFilesDir(PRESETS_FOLDER);
        }
        presetsDir.mkdirs();

        File[] presets = presetsDir.listFiles((FileFilter) null);
        final String[] names = new String[presets != null ? presets.length : 0];
        if (presets != null) {
            for (int i = 0; i < presets.length; i++) {
                names[i] = presets[i].getName();
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(DSPManager.this);
        builder.setTitle(R.string.load_preset)
                .setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        loadPreset(names[which]);
                    }
                });
        builder.create().show();
    }

    public void savePreset(String name) {
        final String spDir = getApplicationInfo().dataDir + "/shared_prefs/";

        // Copy the SharedPreference to our output directory
        File presetDir;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            presetDir = new File(Environment.getExternalStorageDirectory()
                    .getAbsolutePath() + "/" + PRESETS_FOLDER + "/" + name);
        } else {
            presetDir = getExternalFilesDir(PRESETS_FOLDER + "/" + name);
        }

        presetDir.mkdirs();

        Log.e("DSP", "Saving preset to " + presetDir.getAbsolutePath());

        final String packageName = "com.bel.android.dspmanager.";

        try {
            copy(new File(spDir + packageName + "bluetooth.xml"),
                    new File(presetDir, packageName + "bluetooth.xml"));
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.save_preset_bluetooth_failed, Toast.LENGTH_LONG).show();
        }
        try {
            copy(new File(spDir + packageName + "headset.xml"),
                    new File(presetDir, packageName + "headset.xml"));
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.save_preset_headset_failed, Toast.LENGTH_LONG).show();
        }
        try{
            copy(new File(spDir + packageName + "speaker.xml"),
                    new File(presetDir, packageName + "speaker.xml"));
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.save_preset_speaker_failed, Toast.LENGTH_LONG).show();
        }
        try {
            copy(new File(spDir + packageName + "usb.xml"),
                    new File(presetDir, packageName + "usb.xml"));
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.save_preset_usb_failed, Toast.LENGTH_LONG).show();
        }
    }

    public void loadPreset(String name) {
        // Copy the SharedPreference to our local directory
        File presetDir;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            presetDir = new File(Environment.getExternalStorageDirectory()
                    .getAbsolutePath() + "/" + PRESETS_FOLDER + "/" + name);
        }else {
            presetDir = getExternalFilesDir(PRESETS_FOLDER + "/" + name);
        }
        if (!presetDir.exists()) presetDir.mkdirs();

        final String packageName = "com.bel.android.dspmanager.";
        final String spDir = getApplicationInfo().dataDir + "/shared_prefs/";

        try {
            copy(new File(presetDir, packageName + "bluetooth.xml"),
                    new File(spDir + packageName + "bluetooth.xml"));
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.load_preset_bluetooth_failed, Toast.LENGTH_LONG).show();
        }
        try {
            copy(new File(presetDir, packageName + "headset.xml"),
                    new File(spDir + packageName + "headset.xml"));
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.load_preset_headset_failed, Toast.LENGTH_LONG).show();
        }
        try {
            copy(new File(presetDir, packageName + "speaker.xml"),
                    new File(spDir + packageName + "speaker.xml"));
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.load_preset_speaker_failed, Toast.LENGTH_LONG).show();
        }
        try {
            copy(new File(presetDir, packageName + "usb.xml"),
                    new File(spDir + packageName + "usb.xml"));
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.load_preset_usb_failed, Toast.LENGTH_LONG).show();
        }

        // Reload preferences
        startActivity(new Intent(this, DSPManager.class));
        finish();
    }

    public static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        Log.e("DSP", "Copying " + src.getAbsolutePath() + " to " + dst.getAbsolutePath());

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    /**
     * Users of this fragment must call this method to set up the
     * navigation menu_drawer interactions.
     *
     * @param fragmentContainerView The view of this fragment in its activity's layout.
     * @param drawerLayout          The DrawerLayout containing this fragment's UI.
     */
    public void setUpNavigationDrawer(View fragmentContainerView, View drawerLayout) {
        mFragmentContainerView = fragmentContainerView;
        mDrawerLayout = (CustomDrawerLayout) drawerLayout;

        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        ) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);

                invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);

                if (!mUserLearnedDrawer) {
                    mUserLearnedDrawer = true;
                    mPreferences.edit().putBoolean(PREF_USER_LEARNED_DRAWER, true).apply();
                }

                invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }
        };

        if (!mUserLearnedDrawer && !mFromSavedInstanceState) {
            mDrawerLayout.openDrawer(mFragmentContainerView);
        }

        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.syncState();
            }
        });

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.addDrawerListener(mDrawerToggle);

        selectItem(mCurrentSelectedPosition);
    }

    public boolean isDrawerClose() {
        return mDrawerLayout == null || !mDrawerLayout.isDrawerOpen(mFragmentContainerView);
    }

    private void selectItem(int position) {
        mCurrentSelectedPosition = position;
        if (mDrawerListView != null) {
            mDrawerListView.setItemChecked(position, true);
        }
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(mFragmentContainerView);
        }

        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.dsp_container, PlaceholderFragment.newInstance(position))
                .commit();
        mTitle = mTitles.get(position).get("TITLE");
        getActionBar().setTitle(mTitle);
    }

    /**
     * @return String[] containing titles
     */
    // 音频输出设备，音效配置页面
    private List<HashMap<String, String>> getTitles() {
        // TODO: use real drawables
        ArrayList<HashMap<String, String>> tmpList = new ArrayList<>();
        // Headset
        HashMap<String, String> mTitleMap = new HashMap<>();
        mTitleMap.put("ICON", R.drawable.empty_icon + "");
        mTitleMap.put("TITLE", getString(R.string.headset_title));
        tmpList.add(mTitleMap);
        // Speaker
        mTitleMap = new HashMap<>();
        mTitleMap.put("ICON", R.drawable.empty_icon + "");
        mTitleMap.put("TITLE", getString(R.string.speaker_title));
        tmpList.add(mTitleMap);
        // Bluetooth
        mTitleMap = new HashMap<>();
        mTitleMap.put("ICON", R.drawable.empty_icon + "");
        mTitleMap.put("TITLE", getString(R.string.bluetooth_title));
        tmpList.add(mTitleMap);
        // USB
        mTitleMap = new HashMap<>();
        mTitleMap.put("ICON",R.drawable.empty_icon + "");
        mTitleMap.put("TITLE", getString(R.string.usb_title));
        tmpList.add(mTitleMap);

        return tmpList;
    }

    /**
     * @return String[] containing titles
     */
    private String[] getEntries() {
        ArrayList<String> entryString = new ArrayList<>();
        entryString.add("headset");
        entryString.add("speaker");
        entryString.add("bluetooth");
        entryString.add("usb");

        return entryString.toArray(new String[0]);
    }

    //==================================
    // Internal Classes
    //==================================

    /**
     * Loads our Fragments.
     */
    public static class PlaceholderFragment extends Fragment {

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static Fragment newInstance(int fragmentId) {

            final DSPScreen dspFragment = new DSPScreen();
            Bundle b = new Bundle();
            b.putString("config", mEntries[fragmentId]);
            dspFragment.setArguments(b);
            return dspFragment;
        }

        public PlaceholderFragment() {
            //intentionally left blank
        }
    }

    public class MyAdapter extends FragmentPagerAdapter {
        private final String[] entries;
        private final List<HashMap<String, String>> titles;

        public MyAdapter(FragmentManager fm) {
            super(fm);
            entries = DSPManager.mEntries;
            titles = DSPManager.mTitles;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return titles.get(position).get("TITLE");
        }

        public String[] getEntries() {
            return entries;
        }

        @Override
        public int getCount() {
            return entries.length;
        }

        @Override
        public Fragment getItem(int position) {

            final DSPScreen dspFragment = new DSPScreen();
            Bundle b = new Bundle();
            b.putString("config", entries[position]);
            dspFragment.setArguments(b);
            return dspFragment;
        }
    }

    public static class HelpFragment extends DialogFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
            View v = inflater.inflate(R.layout.help, container, false);
            TextView tv = v.findViewById(R.id.help);
            tv.setText(R.string.help_text);
            return v;
        }
    }

}
