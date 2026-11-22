package com.RobinNotBad.BiliClient.activity.settings.login;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.SplashActivity;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.api.CookieImportApi;
import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.LoginTextFile;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.android.material.card.MaterialCardView;
import com.RobinNotBad.BiliClient.ui.TerminalDialogStyle;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Map;

public class SpecialLoginActivity extends BaseActivity {

    private EditText textInput;
    private View fileAction, confirmAction;
    private boolean loginMode, busy;
    private String pendingExport, displayedCookies = "";
    private AlertDialog dialog;
    private static final int IMPORT_FILE = 301, EXPORT_FILE = 302, READ_PERMISSION = 303;

    @SuppressLint({"MissingInflatedId", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_special);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        Logu.i("debug", "使用特殊登录方式");

        textInput = findViewById(R.id.loginInput);
        MaterialCardView confirm = findViewById(R.id.confirm);
        MaterialCardView refuse = findViewById(R.id.refuse);
        MaterialCardView copy = findViewById(R.id.copy);
        TextView desc = findViewById(R.id.desc);
        fileAction = findViewById(R.id.login_file_action);
        confirmAction = confirm;
        loginMode = getIntent().getBooleanExtra("login", true);
        ((TextView)findViewById(R.id.login_file_label)).setText(loginMode ? "从文本文件导入" : "导出文本文件");
        fileAction.setOnClickListener(v -> { if (!busy) { if (loginMode) chooseImport(); else chooseExport(); } });

        if (loginMode) {
            copy.setVisibility(View.GONE);
            refuse.setOnClickListener(v -> {
                if (getIntent().getBooleanExtra("from_setup", false))
                    startActivity(new Intent(this, SplashActivity.class));
                else finish();
            });

            confirm.setOnClickListener(view -> verifyAndImport(true));
        } else {
            desc.setText(R.string.special_login_export);

            updateDisplayedLogin();
            textInput.clearFocus();

            refuse.setVisibility(View.GONE);
            if (BiliTerminal.isDebugBuild()) {
                confirm.setOnClickListener(v -> verifyAndImport(false));
            } else confirm.setVisibility(View.GONE);
            copy.setOnClickListener((view) -> {
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("label", textInput.getText());
                clipboardManager.setPrimaryClip(clipData);
                MsgUtil.showMsg("已复制");
            });
        }
    }

    private void verifyAndImport(boolean leave) {
        if(busy)return;
        final LoginTextFile.Login login;
        try{login=LoginTextFile.parse(textInput.getText().toString());}
        catch(IOException error){MsgUtil.showMsgLong(error.getMessage());return;}
        final long account=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);
        final String previous=SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"");
        setBusy(true);MsgUtil.showMsg("正在验证登录信息…");
        CenterThreadPool.run(()->{
            String failure=null;
            try{CookieImportApi.verify(login);}catch(Exception error){
                failure=error instanceof CookieImportApi.VerificationFailure?error.getMessage():"登录验证失败，请检查网络后重试；登录信息未保存";
            }
            final String message=failure;
            runOnUiThread(()->{
                if(!alive())return;setBusy(false);
                if(message!=null){MsgUtil.showMsgLong(message);return;}
                if(account!=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)
                        ||!AccountManager.sameSession(previous,SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,""))){
                    MsgUtil.showMsgLong("账号已变化，本次导入已取消，请重新确认");return;
                }
                if(!AccountManager.saveLogin(login.cookies,login.refreshToken)){MsgUtil.showMsgLong("登录信息保存失败，请重试");return;}
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.setup,true);
                MsgUtil.showMsg("登录验证成功");
                if(leave){startActivity(new Intent(this,SplashActivity.class));finish();}
                else updateDisplayedLogin();
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (textInput != null && !loginMode && !AccountManager.sameSession(displayedCookies,
                SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))) {
            pendingExport = null;
            updateDisplayedLogin();
        }
    }

    private void updateDisplayedLogin() {
        Map<String, ?> snapshot = SharedPreferencesUtil.getSharedPreferences().getAll();
        Object cookies = snapshot.get(SharedPreferencesUtil.cookies);
        Object token = snapshot.get(SharedPreferencesUtil.refresh_token);
        displayedCookies = cookies instanceof String ? (String)cookies : "";
        try { textInput.setText(LoginTextFile.encode(displayedCookies, token instanceof String ? (String)token : "")); }
        catch (IOException ignored) { textInput.setText(""); }
    }

    private void chooseImport() {
        if (Build.VERSION.SDK_INT >= 19 && launchImport(Intent.ACTION_OPEN_DOCUMENT)) return;
        if (!launchImport(Intent.ACTION_GET_CONTENT)) requestLegacyImport();
    }

    private boolean launchImport(String action) {
        Intent pick = new Intent(action).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(pick, IMPORT_FILE); return true; }
        catch (ActivityNotFoundException ignored) { return false; }
    }

    private void requestLegacyImport() {
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, READ_PERMISSION);
        } else showPathInput();
    }

    private void showPathInput() {
        EditText path = new EditText(this);
        path.setSingleLine(true);
        path.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        path.setText(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "BiliTerminal-login.txt").getAbsolutePath());
        AlertDialog next = new AlertDialog.Builder(this).setTitle("导入文本文件")
                .setMessage("系统没有可用的文件选择器，请输入文本文件的完整路径。")
                .setView(path).setNegativeButton("取消", null)
                .setPositiveButton("导入", (d, which) -> {
                    File file = new File(path.getText().toString().trim());
                    if (!file.isAbsolute() || !file.isFile()) { MsgUtil.showMsg("请填写有效的文件路径"); return; }
                    importFile(Uri.fromFile(file));
                }).create();
        showDialog(next);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == READ_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) showPathInput();
            else MsgUtil.showMsg("未获得读取权限，请使用系统文件选择器导入");
        }
    }

    private void chooseExport() {
        if (!AccountManager.sameSession(displayedCookies,
                SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))) updateDisplayedLogin();
        try {
            LoginTextFile.Login login = LoginTextFile.parse(textInput.getText().toString());
            pendingExport = LoginTextFile.encode(login.cookies, login.refreshToken);
        } catch (IOException ignored) { MsgUtil.showMsg("当前没有可导出的有效登录信息"); return; }
        if (Build.VERSION.SDK_INT >= 19) {
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain")
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "BiliTerminal-login.txt");
            try { startActivityForResult(save, EXPORT_FILE); return; }
            catch (ActivityNotFoundException ignored) { /* Old watches may omit DocumentsUI. */ }
        }
        exportLegacy();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != IMPORT_FILE && request != EXPORT_FILE) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            if (request == EXPORT_FILE) pendingExport = null;
            return;
        }
        Uri uri = data.getData();
        if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme())) {
            pendingExport = null;
            MsgUtil.showMsg("请选择本地文本文件");
            return;
        }
        if (request == IMPORT_FILE) importFile(uri);
        else {
            // Never export a different account after switching while the picker was open.
            if (!AccountManager.sameSession(displayedCookies,
                    SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))) pendingExport = null;
            if (pendingExport == null) { MsgUtil.showMsg("导出已取消，请重新导出"); return; }
            String text = pendingExport;
            pendingExport = null;
            setBusy(true);
            CenterThreadPool.run(() -> {
                boolean success = false;
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    LoginTextFile.write(output, text);
                    success = true;
                } catch (Exception ignored) {}
                final boolean saved = success;
                runOnUiThread(() -> {
                    if (!alive()) return;
                    setBusy(false);
                    MsgUtil.showMsg(saved ? "登录信息已导出，请妥善保管文本文件" : "导出失败，请检查文件是否可写");
                });
            });
        }
    }

    private void importFile(Uri uri) {
        if (!loginMode || busy) return;
        setBusy(true);
        CenterThreadPool.run(() -> {
            String text = null;
            try (InputStream input = getContentResolver().openInputStream(uri)) { text = LoginTextFile.read(input); }
            catch (Exception ignored) {}
            final String imported = text;
            runOnUiThread(() -> {
                if (!alive()) return;
                setBusy(false);
                if (imported == null) {
                    MsgUtil.showMsgLong("导入失败，请选择有效的登录信息文本（UTF-8/UTF-16，最大64KB）");
                } else {
                    textInput.setText(imported);
                    textInput.clearFocus();
                    MsgUtil.showMsg("已导入，请点击登录确认");
                }
            });
        });
    }

    private void exportLegacy() {
        String text = pendingExport;
        pendingExport = null;
        setBusy(true);
        CenterThreadPool.run(() -> {
            File file = null;
            boolean created = false;
            boolean success = false;
            try {
                File base = getExternalFilesDir(null);
                if (base == null) throw new IOException("External storage unavailable");
                File directory = new File(base, "login-export");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create directory");
                file = new File(directory, "BiliTerminal-login-" + System.currentTimeMillis() + ".txt");
                if (!file.createNewFile()) throw new IOException("File already exists");
                created = true;
                try (OutputStream output = new FileOutputStream(file)) { LoginTextFile.write(output, text); }
                success = true;
            } catch (Exception ignored) {
                if (created) file.delete();
            }
            final File saved = success ? file : null;
            runOnUiThread(() -> {
                if (!alive()) return;
                setBusy(false);
                if (saved == null) MsgUtil.showMsg("导出失败，请检查设备存储是否可用");
                else showDialog(new AlertDialog.Builder(this).setTitle("登录信息已导出")
                        .setMessage("请通过文件管理器或USB取出文本，导入后删除。\n\n" + saved.getAbsolutePath())
                        .setPositiveButton("知道了", null).create());
            });
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        fileAction.setEnabled(!value);
        confirmAction.setEnabled(!value);
        textInput.setEnabled(!value);
    }

    private boolean alive() { return !isFinishing() && (Build.VERSION.SDK_INT < 17 || !isDestroyed()); }

    private void showDialog(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next;
        next.setOnDismissListener(d -> { if (dialog == next) dialog = null; });
        TerminalDialogStyle.prepare(next);
        next.show();
    }

    @Override protected void onDestroy() {
        pendingExport = null;
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }
}
