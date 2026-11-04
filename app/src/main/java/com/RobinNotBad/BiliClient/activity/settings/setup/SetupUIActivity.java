package com.RobinNotBad.BiliClient.activity.settings.setup;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.settings.UIPreviewActivity;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.UiConfigurationUtil;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SetupUIActivity extends BaseActivity {

    private EditText uiScaleInput, uiPaddingH, uiPaddingV;

    @SuppressLint({"MissingInflatedId", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_ui);

        uiScaleInput = findViewById(R.id.ui_scale_input);
        uiScaleInput.setText(String.valueOf(SharedPreferencesUtil.getFloat("dpi", 1.0F)));

        uiPaddingH = findViewById(R.id.ui_padding_horizontal);
        uiPaddingH.setText(String.valueOf(SharedPreferencesUtil.getInt("paddingH_percent", 0)));
        uiPaddingV = findViewById(R.id.ui_padding_vertical);
        uiPaddingV.setText(String.valueOf(SharedPreferencesUtil.getInt("paddingV_percent", 0)));

        SwitchMaterial round = findViewById(R.id.switch_round);
        round.setChecked(SharedPreferencesUtil.getBoolean("player_ui_round", false));
        round.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                uiPaddingH.setText("5");
                uiPaddingV.setText("3");
                SharedPreferencesUtil.putBoolean("player_ui_round", true);
                MsgUtil.showMsg("界面边距已更改\n可以手动微调喵");
            } else {
                uiPaddingH.setText("0");
                uiPaddingV.setText("0");
                SharedPreferencesUtil.putBoolean("player_ui_round", false);
            }
        });

        findViewById(R.id.preview).setOnClickListener(view -> {
            if (!save()) return;
            Intent intent = new Intent();
            intent.setClass(SetupUIActivity.this, UIPreviewActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.confirm).setOnClickListener(view -> {
            if (!save()) return;
            Intent intent = new Intent();
            intent.setClass(SetupUIActivity.this, IntroductionActivity.class);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.reset).setOnClickListener(view -> {
            SharedPreferencesUtil.putInt("paddingH_percent", 0);
            SharedPreferencesUtil.putInt("paddingV_percent", 0);
            SharedPreferencesUtil.putFloat("dpi", 1.0f);
            SharedPreferencesUtil.putBoolean("player_ui_round", false);
            uiScaleInput.setText("1.0");
            uiPaddingH.setText("0");
            uiPaddingV.setText("0");
            round.setChecked(false);
            MsgUtil.showMsg("恢复完成");
        });
    }

    private boolean save() {
        EditText parsing = uiScaleInput;
        try {
            float scale = UiConfigurationUtil.parseScaleInput(uiScaleInput.getText().toString(),
                    SharedPreferencesUtil.getFloat("dpi", UiConfigurationUtil.DEFAULT_SCALE));
            parsing = uiPaddingH;
            int horizontal = UiConfigurationUtil.parsePaddingInput(parsing.getText().toString(),
                    SharedPreferencesUtil.getInt("paddingH_percent", 0));
            parsing = uiPaddingV;
            int vertical = UiConfigurationUtil.parsePaddingInput(parsing.getText().toString(),
                    SharedPreferencesUtil.getInt("paddingV_percent", 0));
            SharedPreferencesUtil.getSharedPreferences().edit().putFloat("dpi", scale)
                    .putInt("paddingH_percent", horizontal).putInt("paddingV_percent", vertical).apply();
            BiliTerminal.DPI_FORCE_CHANGE = true;
            for (EditText input : new EditText[]{uiScaleInput, uiPaddingH, uiPaddingV}) input.setError(null);
            return true;
        } catch (NumberFormatException error) {
            parsing.setError(error.getMessage());
            parsing.requestFocus();
            return false;
        }
    }
}
