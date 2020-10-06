package com.bel.android.dspmanager.preference;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

public class SummariedTextPreference extends EditTextPreference {

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
        if(getKey().equals("dsp.bassboost.freq")){
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
