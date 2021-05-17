package com.bel.android.dspmanager.preference;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.bel.android.dspmanager.R;
import com.bel.android.dspmanager.activity.DSPManager;
import com.bel.android.dspmanager.service.HeadsetService;

import java.util.Locale;

public class EqualizerPreference extends DialogPreference {
    protected static final String TAG = EqualizerPreference.class.getSimpleName();

    protected EqualizerSurface mListEqualizer, mDialogEqualizer;

    private HeadsetService mHeadsetService;

    private final ServiceConnection connectionForDialog = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.i(TAG, "Acquiring connection to headsetservice");
            mHeadsetService = ((HeadsetService.LocalBinder) binder).getService();
            updateDspFromDialogEqualizer();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mHeadsetService = null;
        }
    };

    public EqualizerPreference(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setLayoutResource(R.layout.equalizer);
        setDialogLayoutResource(R.layout.equalizer_popup);
    }

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

    protected void updateDspFromDialogEqualizer() {
        if (mHeadsetService != null) {
            float[] levels = new float[6];
            for (int i = 0; i < levels.length; i++) {
                levels[i] = mDialogEqualizer.getBand(i);
            }
            if (HeadsetService.getAudioOutputRouting().equals(getPage())) {
                mHeadsetService.setEqualizerLevels(levels);
            }
        }
    }

    private void updateListEqualizerFromValue() {
        String value = getPersistedString(null);
        if (value != null && mListEqualizer != null) {
            String[] levelsStr = value.split(";");
            for (int i = 0; i < 6; i++) {
                mListEqualizer.setBand(i, Float.parseFloat(levelsStr[i]));
            }
        }
    }

    // I can't solve this method's warning. Sorry
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mDialogEqualizer = view.findViewById(R.id.FrequencyResponse);
        mDialogEqualizer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x = event.getX();
                float y = event.getY();

                //* Which band is closest to the position user pressed? *//
                int band = mDialogEqualizer.findClosest(x);

                int wy = v.getHeight();
                float level = (y / wy) * (EqualizerSurface.MIN_DB - EqualizerSurface.MAX_DB) - EqualizerSurface.MIN_DB;
                if (level < EqualizerSurface.MIN_DB) {
                    level = EqualizerSurface.MIN_DB;
                }
                if (level > EqualizerSurface.MAX_DB) {
                    level = EqualizerSurface.MAX_DB;
                }

                mDialogEqualizer.setBand(band, level);
                updateDspFromDialogEqualizer();
                return true;
            }
        });

        if (mListEqualizer != null) {
            for (int i = 0; i < 6; i++) {
                mDialogEqualizer.setBand(i, mListEqualizer.getBand(i));
            }
        }

        getContext().bindService(new Intent(getContext(), HeadsetService.class), connectionForDialog, 0);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            StringBuilder value = new StringBuilder();
            int i = 0;
            while (i < 5) {
                value.append(String.format(Locale.ROOT, "%.1f", Math.round(mDialogEqualizer.getBand(i) * 10.f) / 10.f)).append(";");
                ++i;
            }
            value.append(String.format(Locale.ROOT, "%.1f", Math.round(mDialogEqualizer.getBand(i) * 10.f) / 10.f));
            persistString(value.toString());
            updateListEqualizerFromValue();
        }

        if (mHeadsetService != null) {
            mHeadsetService.setEqualizerLevels(null);
        }
        getContext().unbindService(connectionForDialog);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mListEqualizer = view.findViewById(R.id.FrequencyResponse);
        updateListEqualizerFromValue();
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        String value = restorePersistedValue ? getPersistedString(null) : (String) defaultValue;
        if (shouldPersist()) {
            persistString(value);
        }
    }

    public void refreshFromPreference() {
        onSetInitialValue(true, null);
    }
}
