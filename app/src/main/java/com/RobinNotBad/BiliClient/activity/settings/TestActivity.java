package com.RobinNotBad.BiliClient.activity.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.method.TransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.widget.TooltipCompat;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.settings.login.SpecialLoginActivity;
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity;
import com.RobinNotBad.BiliClient.api.ConfInfoApi;
import com.RobinNotBad.BiliClient.api.DeepSeekApi;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class TestActivity extends BaseActivity {

    private static final long DEVELOPER_MID = 313733893L;
    private static final String DEEPSEEK_KEY = "dev_deepseek_apikey";
    private static final String LEGACY_DEEPSEEK_KEY = "dev_catgirl_apikey";
    SwitchMaterial sw_wbi, sw_post, sw_deepseek_model;
    EditText input_link, input_data, output;
    MaterialCardView btn_deepseek, btn_request, btn_cookies, btn_profile;

    JSONArray conversation;
    private Call deepSeekCall;
    private OkHttpClient deepSeekClient;
    private TextView deepSeekStatus;
    private boolean destroyed;
    private boolean deepSeekKeyVisible;

    @SuppressLint({"MutatingSharedPrefs", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        sw_wbi = findViewById(R.id.switch_wbi);
        sw_post = findViewById(R.id.switch_post);
        input_link = findViewById(R.id.input_link);
        input_data = findViewById(R.id.input_data);
        output = findViewById(R.id.output_json);
        btn_deepseek = findViewById(R.id.deepseek);
        btn_profile = findViewById(R.id.developer_profile);
        sw_deepseek_model = findViewById(R.id.switch_deepseek_model);

        runPlayUrlProbeIfRequested();

        input_link.setText(SharedPreferencesUtil.getString("dev_test_link", ""));

        sw_post.setOnCheckedChangeListener((compoundButton, checked) ->
                input_data.setVisibility(checked ? View.VISIBLE : View.GONE));

        btn_request = findViewById(R.id.request);

        btn_request.setOnClickListener(view -> {
            if (conversation != null) {
                sendDeepSeek();
                return;
            }
            CenterThreadPool.run(() -> {
            try {
                String url = input_link.getText().toString();
                if (!url.startsWith("https://") && !url.startsWith("http://"))
                    url = "https://" + url;

                if (sw_wbi.isChecked()) url = ConfInfoApi.signWBI(url);

                runOnUiThread(() -> {
                    output.setText("");
                    MsgUtil.showMsg("发出请求！");
                });
                String result;
                if (sw_post.isChecked()) {
                    String data = input_data.getText().toString();
                    result = Objects.requireNonNull(NetWorkUtil.post(url, data).body()).string();
                } else {
                    result = Objects.requireNonNull(NetWorkUtil.get(url).body()).string();
                }

                runOnUiThread(() -> {
                    output.setText(result);
                    MsgUtil.showMsg("请求成功！");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    output.setText(e.toString());
                    MsgUtil.showMsg("请求失败！");
                });
                e.printStackTrace();
            }
            });
        });

        btn_cookies = findViewById(R.id.cookies);
        btn_cookies.setOnClickListener(view -> {
            Intent intent = new Intent(this, SpecialLoginActivity.class);
            intent.putExtra("login", false);
            startActivity(intent);
        });


        btn_profile.setOnClickListener(v -> startActivity(
                new Intent(this, UserInfoActivity.class).putExtra("mid", DEVELOPER_MID)));


        btn_deepseek.setOnClickListener(v -> enterDeepSeek());
    }

    private void resetConversation() {
        conversation = new JSONArray();
        try {
            conversation.put(new JSONObject().put("role", "system")
                    .put("content", getString(R.string.dev_deepseek_prompt)));
        } catch (JSONException ignored) {}
        output.setText("");
    }

    private void enterDeepSeek() {
        if (conversation != null) return;
        SharedPreferencesUtil.putString("dev_test_link", input_link.getText().toString());
        resetConversation();
        btn_deepseek.setVisibility(View.GONE);
        input_data.setVisibility(View.VISIBLE);
        input_data.setText("");
        input_data.setHint("输入问题");
        input_link.setHint("DeepSeek API Key");
        deepSeekKeyVisible = false;
        input_link.setSingleLine(true);
        applyDeepSeekKeyMask();
        input_link.setSaveEnabled(false);
        input_data.setSaveEnabled(false);
        output.setSaveEnabled(false);
        output.setKeyListener(null);
        output.setTextIsSelectable(true);
        String saved = SharedPreferencesUtil.getString("dev_deepseek_apikey",
                SharedPreferencesUtil.getString("dev_catgirl_apikey", ""));
        input_link.setText(saved);
        // Apply password masking after single-line configuration and restored text.
        applyDeepSeekKeyMask();
        sw_wbi.setText("启用深度思考（High）");
        sw_wbi.setChecked(true);
        sw_post.setVisibility(View.GONE);
        sw_deepseek_model.setVisibility(View.VISIBLE);
        btn_cookies.setVisibility(View.GONE);
        ((TextView) findViewById(R.id.desc)).setText(R.string.dev_deepseek_desc);
        ((TextView) findViewById(R.id.request_label)).setText("发送");
        input_link.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) {
                saveDeepSeekKey();
            }
        });
        deepSeekStatus = findViewById(R.id.deepseek_status);
        deepSeekStatus.setVisibility(View.VISIBLE);
        findViewById(R.id.deepseek_actions).setVisibility(View.VISIBLE);
        View toggleKeyVisibility = findViewById(R.id.deepseek_toggle_key_visibility);
        toggleKeyVisibility.setVisibility(View.VISIBLE);
        toggleKeyVisibility.setOnClickListener(v -> {
            deepSeekKeyVisible = !deepSeekKeyVisible;
            int position = input_link.getSelectionStart();
            TransformationMethod method = deepSeekKeyVisible
                    ? null : PasswordTransformationMethod.getInstance();
            input_link.setTransformationMethod(method);
            input_link.setSelection(Math.max(0, Math.min(position, input_link.length())));
            toggleKeyVisibility.setContentDescription(deepSeekKeyVisible ? "隐藏密钥" : "显示密钥");
            toggleKeyVisibility.setAlpha(deepSeekKeyVisible ? 0.65f : 1f);
        });
        View forget = findViewById(R.id.deepseek_forget_key);
        View reset = findViewById(R.id.deepseek_reset_chat);
        TooltipCompat.setTooltipText(forget, "清除密钥");
        TooltipCompat.setTooltipText(reset, "清空对话");
        forget.setOnClickListener(v -> {
            forgetSavedKey();
            input_link.setText("");
            deepSeekStatus.setText("已清除密钥");
        });
        reset.setOnClickListener(v -> {
            resetConversation();
            deepSeekStatus.setText("已清空对话");
        });
    }

    private void applyDeepSeekKeyMask() {
        input_link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input_link.setTransformationMethod(PasswordTransformationMethod.getInstance());
    }

    private void forgetSavedKey() {
        SharedPreferencesUtil.removeValue(DEEPSEEK_KEY);
        SharedPreferencesUtil.removeValue(LEGACY_DEEPSEEK_KEY);
    }

    private void saveDeepSeekKey() {
        String key = input_link.getText().toString().trim();
        if (key.isEmpty()) {
            forgetSavedKey();
        } else {
            SharedPreferencesUtil.putString(DEEPSEEK_KEY, key);
            SharedPreferencesUtil.removeValue(LEGACY_DEEPSEEK_KEY);
        }
    }

    private void setDeepSeekBusy(boolean busy) {
        input_link.setEnabled(!busy);
        input_data.setEnabled(!busy);
        sw_wbi.setEnabled(!busy);
        sw_deepseek_model.setEnabled(!busy);
        findViewById(R.id.deepseek_forget_key).setEnabled(!busy);
        findViewById(R.id.deepseek_reset_chat).setEnabled(!busy);
        ((TextView) findViewById(R.id.request_label)).setText(busy ? "停止" : "发送");
    }

    private void sendDeepSeek() {
        if (deepSeekCall != null) {
            deepSeekCall.cancel();
            deepSeekStatus.setText("正在停止...");
            return;
        }
        String key = input_link.getText().toString().trim();
        String question = input_data.getText().toString().trim();
        if (key.isEmpty() || question.isEmpty()) {
            deepSeekStatus.setText(key.isEmpty() ? "请填写有效的 API Key" : "请输入问题");
            return;
        }
        if (question.length() > 8192) {
            deepSeekStatus.setText("问题过长，请缩短后重试");
            return;
        }
        try {
            JSONArray pending = new JSONArray(conversation.toString());
            pending.put(new JSONObject().put("role", "user").put("content", question));
            if (deepSeekClient == null) deepSeekClient = NetWorkUtil.setOkHttpSsl(DeepSeekApi.clientBuilder()).build();
            Call call = DeepSeekApi.newCall(deepSeekClient, key, pending,
                    sw_deepseek_model.isChecked(), sw_wbi.isChecked());
            saveDeepSeekKey();
            deepSeekCall = call;
            setDeepSeekBusy(true);
            output.setText("");
            deepSeekStatus.setText("正在等待 DeepSeek...");
            CenterThreadPool.run(() -> {
                StringBuilder display = new StringBuilder();
                long[] lastRender = {0};
                boolean[] sawReasoning = {false};
                boolean[] sawContent = {false};
                try (Response response = call.execute()) {
                    String reply = DeepSeekApi.readReply(response, (reasoning, content) -> {
                        if (!reasoning.isEmpty()) {
                            if (!sawReasoning[0]) display.append("思考中\n");
                            sawReasoning[0] = true;
                            display.append(reasoning);
                        }
                        if (!content.isEmpty()) {
                            if (!sawContent[0] && sawReasoning[0]) display.append("\n\n回答\n");
                            sawContent[0] = true;
                            display.append(content);
                        }
                        long now = SystemClock.elapsedRealtime();
                        if (now - lastRender[0] >= 100) {
                            lastRender[0] = now;
                            String text = display.toString();
                            runOnUiThread(() -> {
                                if (!destroyed && deepSeekCall == call) {
                                    output.setText(text);
                                    deepSeekStatus.setText("正在接收回答...");
                                }
                            });
                        }
                    });
                    pending.put(new JSONObject().put("role", "assistant").put("content", reply));
                    JSONArray completed = new JSONArray();
                    completed.put(pending.get(0));
                    // Keep the prompt and the last six complete turns on small devices.
                    for (int i = Math.max(1, pending.length() - 12); i < pending.length(); i++) completed.put(pending.get(i));
                    String finalDisplay = display.toString();
                    runOnUiThread(() -> {
                        if (destroyed || deepSeekCall != call) return;
                        if (!call.isCanceled()) {
                            conversation = completed;
                            output.setText(finalDisplay);
                            input_data.setText("");
                            deepSeekStatus.setText("回答完成");
                        } else {
                            deepSeekStatus.setText("已停止，本次回答未加入上下文");
                        }
                    });
                } catch (Exception error) {
                    String message = DeepSeekApi.failureMessage(error, call.isCanceled());
                    String partial = display.toString();
                    runOnUiThread(() -> {
                        if (!destroyed && deepSeekCall == call) {
                            output.setText(partial);
                            deepSeekStatus.setText(message);
                        }
                    });
                } finally {
                    runOnUiThread(() -> {
                        if (!destroyed && deepSeekCall == call) {
                            deepSeekCall = null;
                            setDeepSeekBusy(false);
                        }
                    });
                }
            });
        } catch (Exception error) {
            deepSeekStatus.setText(DeepSeekApi.failureMessage(error, false));
        }
    }

    private void runPlayUrlProbeIfRequested() {
        long aid = getIntent().getLongExtra("play_aid", -1);
        long cid = getIntent().getLongExtra("play_cid", -1);
        if (aid <= 0 || cid <= 0) return;

        int qn = getIntent().getIntExtra("play_qn", 16);
        boolean badWbi = getIntent().getBooleanExtra("play_bad_wbi", false);
        boolean retryAfterFailure = getIntent().getBooleanExtra("play_retry_after_failure", false);
        boolean startPlayer = getIntent().getBooleanExtra("play_start_player", false);
        if (badWbi) {
            SharedPreferencesUtil.putString("wbi_mixin_key", "00000000000000000000000000000000");
            SharedPreferencesUtil.putInt("last_wbi", ConfInfoApi.getDateCurr());
        }

        CenterThreadPool.run(() -> {
            PlayerData data = new PlayerData(PlayerData.TYPE_VIDEO);
            data.aid = aid;
            data.cid = retryAfterFailure ? 1 : cid;
            data.qn = qn;
            try {
                if (retryAfterFailure) {
                    try {
                        PlayerApi.getVideo(data, false);
                    } catch (Exception expected) {
                        Log.i("play-url-probe", "expected first failure, timestamp=" + data.timeStamp);
                    }
                    data.cid = cid;
                }
                PlayerApi.getVideo(data, false);
                String result = "OK timestamp=" + data.timeStamp + " url=" + data.videoUrl;
                Log.i("play-url-probe", result);
                runOnUiThread(() -> output.setText(result));
                if (startPlayer) {
                    data.title = "播放取流测试";
                    SharedPreferencesUtil.putString("player", "terminalPlayer");
                    getIntent().removeExtra("play_start_player");
                    finish();
                    startActivity(PlayerApi.jumpToPlayer(data));
                }
            } catch (Exception error) {
                String result = "FAIL timestamp=" + data.timeStamp + " error=" + error;
                Log.e("play-url-probe", result, error);
                runOnUiThread(() -> output.setText(result));
            }
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (deepSeekCall != null) deepSeekCall.cancel();
        if (deepSeekClient != null) {
            OkHttpClient client = deepSeekClient;
            Thread cleanup = new Thread(() -> {
                client.dispatcher().cancelAll();
                client.dispatcher().executorService().shutdown();
                client.connectionPool().evictAll();
            }, "deepseek-client-cleanup");
            cleanup.setDaemon(true);
            cleanup.start();
        }
        if (conversation == null)
            SharedPreferencesUtil.putString("dev_test_link", input_link.getText().toString());
        else saveDeepSeekKey();
        super.onDestroy();
    }
}
