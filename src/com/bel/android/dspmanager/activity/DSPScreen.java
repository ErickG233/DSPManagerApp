
package com.bel.android.dspmanager.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.bel.android.dspmanager.R;
import com.bel.android.dspmanager.preference.EqualizerPreference;
import com.bel.android.dspmanager.preference.SummariedListPreference;
import com.bel.android.dspmanager.service.HeadsetService;

/**
 * This class implements a general PreferencesActivity that we can use to
 * adjust DSP settings. It adds a menu to clear the preferences on this page,
 * and a listener that ensures that our {@link HeadsetService} is running if
 * required.
 *
 * @author alankila
 */
public final class DSPScreen extends PreferenceFragment {
    protected static final String TAG = DSPScreen.class.getSimpleName();

    // 获取对应传递过来的页面设定对应的配置作为判断用
    private static String getPage() {
        if (DSPManager.manualPosition == 1) {
            return "speaker";
        }else if (DSPManager.manualPosition == 2) {
            return "bluetooth";
        } else if (DSPManager.manualPosition == 3) {
            return "usb";
        } else {
            return "headset";
        }
    }

    // 配置更改监听器
    private final OnSharedPreferenceChangeListener listener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            /* If the listpref is updated, copy the changed setting to the eq. */
            // 更新音效配置
            if (("dsp." + getPage() + ".tone.eq.type").equals(key)) {
                String newValue = sharedPreferences.getString(key, null);
                if (!"custom".equals(newValue)) {
                    Editor e = sharedPreferences.edit();
                    e.putString(("dsp." + getPage() + ".tone.eq.values"), newValue);
                    e.apply();

                    /* Now tell the equalizer that it must display something else. */
                    EqualizerPreference eq = (EqualizerPreference)
                            getPreferenceScreen().findPreference(("dsp." + getPage() + ".tone.eq.values"));
                    eq.refreshFromPreference();
                }
            }
            /* If the equalizer surface is updated, select matching pref entry or "custom". */
            // 从预设获取EQ值
            if (("dsp." + getPage() + ".tone.eq.values").equals(key)) {
                String newValue = sharedPreferences.getString(key, null);
                String desiredValue = "custom";
                SummariedListPreference preset = (SummariedListPreference)
                        getPreferenceScreen().findPreference(("dsp." + getPage() + ".tone.eq.type"));
                for (CharSequence entry : preset.getEntryValues()) {
                    if (entry.equals(newValue)) {
                        desiredValue = newValue;
                        break;
                    }
                }

                /* Tell listpreference that it must display something else. */
                if (!preset.getEntry().equals(desiredValue)) {
                    Editor e = sharedPreferences.edit();
                    e.putString(("dsp." + getPage() + ".tone.eq.type"), desiredValue);
                    e.apply();
                    preset.refreshFromPreference();
                }
            }

            getActivity().sendBroadcast(new Intent(DSPManager.ACTION_UPDATE_PREFERENCES));
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 获取页面标题
        String thePage = getArguments().getString("setPageTo");

        getPreferenceManager().setSharedPreferencesName(
                DSPManager.SHARED_PREFERENCES_BASENAME + "." + thePage);
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);

        // 获取本地页面的配置
        // 设备名_preferences.xml
        try {
            int xmlId = R.xml.class.getField(thePage + "_preferences").getInt(null);
            addPreferencesFromResource(xmlId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(listener);
    }
}