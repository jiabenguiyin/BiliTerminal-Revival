package com.RobinNotBad.BiliClient.activity.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.UiConfigurationUtil;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingUIActivity extends BaseActivity {

    private EditText uiScaleInput, uiPaddingH, uiPaddingV, density_input;
    private String defaultDensityText;
    private AlertDialog confirmationDialog;

    @SuppressLint({"MissingInflatedId", "SetTextI18n", "InflateParams"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        asyncInflate(R.layout.activity_setting_ui, (layoutView, resId) -> {

            uiScaleInput = findViewById(R.id.ui_scale_input);
            uiScaleInput.setText(String.valueOf(SharedPreferencesUtil.getFloat("dpi", 1.0F)));

            uiPaddingH = findViewById(R.id.ui_padding_horizontal);
            uiPaddingH.setText(String.valueOf(SharedPreferencesUtil.getInt("paddingH_percent", 0)));
            uiPaddingV = findViewById(R.id.ui_padding_vertical);
            uiPaddingV.setText(String.valueOf(SharedPreferencesUtil.getInt("paddingV_percent", 0)));

            density_input = findViewById(R.id.density_input);
            int density = SharedPreferencesUtil.getInt("density", -1);
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            defaultDensityText = displayMetrics.densityDpi + "(默认)";
            density_input.setText(density == -1 ? defaultDensityText : String.valueOf(density));

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
                if (!save(true)) return;
                Intent intent = new Intent();
                intent.setClass(SettingUIActivity.this, UIPreviewActivity.class);
                startActivity(intent);
            });
            findViewById(R.id.reset).setOnClickListener(view -> {
                SharedPreferencesUtil.putInt("paddingH_percent", 0);
                SharedPreferencesUtil.putInt("paddingV_percent", 0);
                SharedPreferencesUtil.putFloat("dpi", 1.0f);
                SharedPreferencesUtil.putInt("density", -1);
                SharedPreferencesUtil.putBoolean("player_ui_round", false);
                uiScaleInput.setText("1.0");
                uiPaddingH.setText("0");
                uiPaddingV.setText("0");
                getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                defaultDensityText = displayMetrics.densityDpi + "(默认)";
                density_input.setText(defaultDensityText);
                round.setChecked(false);
                MsgUtil.showMsg("恢复完成");
            });
            findViewById(R.id.confirm_ui_settings).setOnClickListener(view -> {
                if (confirmationDialog != null && confirmationDialog.isShowing()) return;
                if (!save(true)) return;
                confirmationDialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.ui_settings_saved_title)
                        .setMessage(R.string.ui_settings_saved_message)
                        .setPositiveButton(android.R.string.ok, null).create();
                MsgUtil.prepareAlertDialog(confirmationDialog);
                confirmationDialog.show();
            });

            View scrollView = findViewById(R.id.scrollView);
            scrollView.setFocusable(true);
            scrollView.setFocusableInTouchMode(true);
            scrollView.requestFocus();
        });
    }

    private boolean save(boolean reportErrors) {
        if (uiScaleInput == null || uiPaddingH == null || uiPaddingV == null || density_input == null) return false;
        EditText parsing = uiScaleInput;
        float scale;
        int horizontal, vertical, density;
        try {
            String value = uiScaleInput.getText().toString().trim();
            scale = UiConfigurationUtil.parseScaleInput(value,
                    SharedPreferencesUtil.getFloat("dpi", UiConfigurationUtil.DEFAULT_SCALE));
            parsing = uiPaddingH;
            value = parsing.getText().toString().trim();
            horizontal = UiConfigurationUtil.parsePaddingInput(value, SharedPreferencesUtil.getInt("paddingH_percent", 0));
            parsing = uiPaddingV;
            value = parsing.getText().toString().trim();
            vertical = UiConfigurationUtil.parsePaddingInput(value, SharedPreferencesUtil.getInt("paddingV_percent", 0));
            parsing = density_input;
            value = parsing.getText().toString().trim();
            density = value.isEmpty() || value.equals(defaultDensityText) ? -1
                    : UiConfigurationUtil.normalizeDensityForContext(Integer.parseInt(value), old_context);
        } catch (NumberFormatException error) {
            if (reportErrors) {
                parsing.setError(parsing == density_input ? getString(R.string.ui_settings_invalid_number) : error.getMessage());
                parsing.requestFocus();
            }
            return false;
        }
        boolean densityChanged = scale != SharedPreferencesUtil.getFloat("dpi", UiConfigurationUtil.DEFAULT_SCALE)
                || density != SharedPreferencesUtil.getInt("density", -1);
        // Save all validated values together before showing the success message.
        SharedPreferencesUtil.getSharedPreferences().edit().putFloat("dpi", scale)
                .putInt("paddingH_percent", horizontal).putInt("paddingV_percent", vertical)
                .putInt("density", density).apply();
        if (densityChanged) BiliTerminal.DPI_FORCE_CHANGE = true;
        for (EditText input : new EditText[]{uiScaleInput, uiPaddingH, uiPaddingV, density_input}) input.setError(null);
        uiScaleInput.setText(String.valueOf(scale));
        uiPaddingH.setText(String.valueOf(horizontal));
        uiPaddingV.setText(String.valueOf(vertical));
        density_input.setText(density == -1 ? defaultDensityText : String.valueOf(density));
        Logu.i("ui-settings", "界面设置已保存");
        return true;
    }

    @Override
    protected void onDestroy() {
        if (confirmationDialog != null) confirmationDialog.dismiss();
        save(false);
        super.onDestroy();
    }
}
