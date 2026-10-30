package com.RobinNotBad.BiliClient.activity.user.favorite;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.api.FavoriteApi;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.FavoriteFolder;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

public class FavoriteFolderEditActivity extends BaseActivity {
    private long mediaId;
    private long account;
    private FavoriteFolder folder;
    private EditText nameInput;
    private EditText introInput;
    private Switch privateInput;
    private Button save;
    private Button retry;
    private TextView status;
    private boolean saving;
    private boolean closed;
    private Bundle draft;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_favorite_folder);
        setPageName("编辑收藏夹");
        setTopbarExit();
        nameInput = findViewById(R.id.folder_edit_name);
        introInput = findViewById(R.id.folder_edit_intro);
        privateInput = findViewById(R.id.folder_edit_private);
        save = findViewById(R.id.folder_edit_save);
        retry = findViewById(R.id.folder_edit_retry);
        status = findViewById(R.id.folder_edit_status);
        account = getIntent().getLongExtra("owner_mid", 0);
        mediaId = getIntent().getLongExtra("media_id", 0);
        draft = savedInstanceState;
        save.setOnClickListener(view -> saveFolder());
        retry.setOnClickListener(view -> loadFolder());
        loadFolder();
    }

    private boolean validAccount() {
        return account > 0 && SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == account;
    }

    private boolean available() { return !closed && !isFinishing(); }

    private void loadFolder() {
        controls(false);
        if (mediaId <= 0 || !validAccount()) { status.setText("账号已切换，请返回后重试"); return; }
        status.setText("正在读取收藏夹资料…");
        retry.setVisibility(View.GONE);
        CenterThreadPool.run(() -> {
            try {
                FavoriteFolder loaded = FavoriteApi.getFavoriteFolderInfo(mediaId);
                if (loaded.id != mediaId || loaded.ownerMid != account) throw new IllegalStateException("只能编辑自己的收藏夹");
                runOnUiThread(() -> {
                    if (!available()) return;
                    if (!validAccount()) { status.setText("账号已切换，请返回后重试"); return; }
                    folder = loaded;
                    nameInput.setText(folder.name);
                    introInput.setText(folder.intro);
                    privateInput.setChecked(folder.isPrivate());
                    if (draft != null && draft.getLong("draft_media_id") == mediaId
                            && draft.getLong("draft_account") == account) {
                        if (!folder.isDefault()) {
                            nameInput.setText(draft.getString("draft_name", folder.name));
                            introInput.setText(draft.getString("draft_intro", folder.intro));
                        }
                        privateInput.setChecked(draft.getBoolean("draft_private", folder.isPrivate()));
                    }
                    draft = null;
                    status.setText(folder.isDefault() ? "默认收藏夹只能修改公开状态" : "修改后点击保存");
                    controls(true);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!available()) return;
                    status.setText("读取失败：" + error.getMessage());
                    retry.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void controls(boolean ready) {
        boolean editable = ready && !saving && folder != null;
        boolean custom = editable && !folder.isDefault();
        nameInput.setEnabled(custom);
        introInput.setEnabled(custom);
        privateInput.setEnabled(editable);
        save.setEnabled(editable);
    }

    private void saveFolder() {
        if (saving || folder == null || !validAccount()) { status.setText("账号已切换，请返回后重试"); return; }
        String title = nameInput.getText().toString().trim();
        if (title.isEmpty()) { nameInput.setError("名称不能为空"); return; }
        String intro = introInput.getText().toString();
        boolean privateFolder = privateInput.isChecked();
        saving = true;
        controls(true);
        status.setText("正在保存…");
        CenterThreadPool.run(() -> {
            try {
                if (!validAccount()) throw new IllegalStateException("账号已切换");
                ApiResult result = FavoriteApi.editFavoriteFolder(folder, title, intro, privateFolder);
                runOnUiThread(() -> {
                    if (!available()) return;
                    saving = false;
                    if (!validAccount()) { controls(false); status.setText("账号已切换，请返回后重试"); return; }
                    if (result.code == 0) { setResult(RESULT_OK); MsgUtil.showMsg("收藏夹已保存"); finish(); }
                    else { status.setText("保存失败：" + result.message + "（" + result.code + "）"); controls(true); }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!available()) return;
                    saving = false;
                    status.setText("保存失败，请刷新确认状态：" + error.getMessage());
                    controls(validAccount());
                });
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (folder != null) {
            state.putLong("draft_media_id", mediaId);
            state.putLong("draft_account", account);
            state.putString("draft_name", nameInput.getText().toString());
            state.putString("draft_intro", introInput.getText().toString());
            state.putBoolean("draft_private", privateInput.isChecked());
        }
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() { closed = true; super.onDestroy(); }
}
