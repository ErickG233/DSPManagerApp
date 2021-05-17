package com.bel.android.dspmanager.preference;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

import com.bel.android.dspmanager.activity.DSPManager;

public class SummariedTextPreference extends EditTextPreference {

    // 获取对应传递过来的页面设定对应的Key
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

    public SummariedTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    @Override
    public void setText(String value){
        float valueFloat = 0.0f;
        try {
            valueFloat = Float.parseFloat(value);
        }
        catch (NumberFormatException e){
            // NULL
        }
        if(getKey().equals("dsp." + getPage() +".bassboost.freq")){
            if(valueFloat < 20.0f){
                value = "20";
            }
            if(valueFloat > 200.0f){
                value = "200";
            }
            setSummary(value +"Hz");
        }
        super.setText(value);
    }

    public void refreshFromPreference() {
        onSetInitialValue(true, null);
    }
}
