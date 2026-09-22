package com.RobinNotBad.BiliClient.activity.user;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.api.ImageUploadApi;
import com.RobinNotBad.BiliClient.api.UserInfoApi;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.util.Calendar;

public class EditProfileActivity extends InstanceActivity {
    private ImageView avatar;
    private EditText name;
    private Spinner sex;
    private EditText birthday;
    private EditText sign;
    private EditText school;
    private Button save;
    private Uri selectedAvatar;

    private final ActivityResultLauncher<Intent> imageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                selectedAvatar = result.getData().getData();
                if (selectedAvatar != null) avatar.setImageURI(selectedAvatar);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        setPageName("编辑个人资料");
        setTopbarExit();

        avatar = findViewById(R.id.edit_avatar);
        name = findViewById(R.id.edit_name);
        sex = findViewById(R.id.edit_sex);
        birthday = findViewById(R.id.edit_birthday);
        sign = findViewById(R.id.edit_sign);
        school = findViewById(R.id.edit_school);
        save = findViewById(R.id.edit_save);

        ArrayAdapter<CharSequence> sexAdapter = ArrayAdapter.createFromResource(this,
                R.array.profile_sex_options, android.R.layout.simple_spinner_item);
        sexAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sex.setAdapter(sexAdapter);
        birthday.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        birthday.setFocusable(false);
        birthday.setOnClickListener(view -> showBirthdayPicker());
        avatar.setOnClickListener(view -> chooseAvatar());
        save.setOnClickListener(view -> saveProfile());
        loadProfile();
    }

    private void chooseAvatar() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        imageLauncher.launch(Intent.createChooser(intent, "选择头像"));
    }

    private void showBirthdayPicker() {
        Calendar calendar = Calendar.getInstance();
        String current = birthday.getText().toString().trim();
        try {
            if (current.length() == 10) {
                calendar.set(Calendar.YEAR, Integer.parseInt(current.substring(0, 4)));
                calendar.set(Calendar.MONTH, Integer.parseInt(current.substring(5, 7)) - 1);
                calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(current.substring(8, 10)));
            }
        } catch (Exception ignored) {
        }
        new DatePickerDialog(this, (view, year, month, day) -> birthday.setText(
                String.format("%04d-%02d-%02d", year, month + 1, day)),
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadProfile() {
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0) {
            MsgUtil.showMsg("还没有登录");
            finish();
            return;
        }
        save.setEnabled(false);
        CenterThreadPool.run(() -> {
            try {
                UserInfo profile = UserInfoApi.getCurrentUserEditableProfile();
                runOnUiThread(() -> bindProfile(profile));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    MsgUtil.showMsg("个人资料加载失败：" + error.getMessage());
                    finish();
                });
            }
        });
    }

    private void bindProfile(UserInfo profile) {
        if (profile == null) {
            MsgUtil.showMsg("个人资料为空");
            finish();
            return;
        }
        name.setText(profile.name == null ? "" : profile.name);
        birthday.setText(profile.birthday == null ? "" : profile.birthday);
        sign.setText(profile.sign == null ? "" : profile.sign);
        school.setText(profile.school == null ? "" : profile.school);
        String profileSex = profile.sex == null ? "保密" : profile.sex;
        for (int i = 0; i < sex.getCount(); i++) {
            if (profileSex.equals(sex.getItemAtPosition(i).toString())) {
                sex.setSelection(i);
                break;
            }
        }
        Glide.with(this).load(GlideUtil.url(profile.avatar))
                .placeholder(R.mipmap.akari)
                .apply(RequestOptions.circleCropTransform())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(avatar);
        save.setEnabled(true);
    }

    private void saveProfile() {
        String newName = name.getText().toString().trim();
        if (newName.isEmpty()) {
            name.setError("昵称不能为空");
            return;
        }
        save.setEnabled(false);
        String newSex = sex.getSelectedItem() == null ? "保密" : sex.getSelectedItem().toString();
        String newBirthday = birthday.getText().toString().trim();
        String newSign = sign.getText().toString();
        String newSchool = school.getText().toString().trim();
        CenterThreadPool.run(() -> {
            try {
                if (selectedAvatar != null) ImageUploadApi.uploadAvatar(getApplicationContext(), selectedAvatar);
                int code = UserInfoApi.updateCurrentUserProfile(newName, newSex, newBirthday,
                        newSign, newSchool);
                if (code != 0) throw new IllegalStateException("接口返回错误码 " + code);
                runOnUiThread(() -> {
                    MsgUtil.showMsg("个人资料已保存");
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    save.setEnabled(true);
                    MsgUtil.showMsg("保存失败：" + error.getMessage());
                });
            }
        });
    }
}
