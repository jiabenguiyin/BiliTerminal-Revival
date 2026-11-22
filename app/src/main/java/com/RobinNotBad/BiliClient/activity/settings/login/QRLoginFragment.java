package com.RobinNotBad.BiliClient.activity.settings.login;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Guideline;
import androidx.fragment.app.Fragment;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.SplashActivity;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.api.CookiesApi;
import com.RobinNotBad.BiliClient.api.LoginApi;
import com.RobinNotBad.BiliClient.api.QrLoginResponse;
import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Response;

public class QRLoginFragment extends Fragment {
    private ImageView qrImageView;
    private TextView scanStat;
    Bitmap QRImage;
    Timer timer;
    boolean need_refresh = false;
    boolean from_setup = false;
    int qrScale = 0;

    public QRLoginFragment() {
    }

    public static QRLoginFragment newInstance(boolean from_setup) {
        Bundle args = new Bundle();
        args.putBoolean("from_setup", from_setup);
        QRLoginFragment fragment = new QRLoginFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);

        Bundle bundle = getArguments();
        if (bundle != null) from_setup = bundle.getBoolean("from_setup", false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_qr_login, container, false);

        qrImageView = view.findViewById(R.id.qrImage);
        scanStat = view.findViewById(R.id.scanStat);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MaterialCardView jump = view.findViewById(R.id.jump);
        jump.setOnClickListener(v -> {
            if (from_setup) startActivity(new Intent(requireContext(), SplashActivity.class));
            if (timer != null) timer.cancel();
            if (isAdded()) requireActivity().finish();
        });

        MaterialCardView special = view.findViewById(R.id.special);
        special.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SpecialLoginActivity.class);
            intent.putExtra("from_setup", from_setup);
            startActivity(intent);
            if (timer != null) timer.cancel();
            if (isAdded()) requireActivity().finish();
        });

        MaterialCardView webLogin = view.findViewById(R.id.webLogin);
        webLogin.setOnClickListener(v -> startActivity(new Intent(
                requireContext(), LoginStyleActivity.class)));

        MaterialCardView intlLogin = view.findViewById(R.id.intlLogin);
        intlLogin.setOnClickListener(v -> startActivity(new Intent(
                requireContext(), IntlPasswordLoginActivity.class)));

        qrImageView.setOnClickListener(v -> {
            if (need_refresh) {
                qrImageView.setImageResource(R.mipmap.loading_qr);
                qrImageView.setEnabled(false);
                refreshQrCode();
            } else {
                Guideline guideline_left = view.findViewById(R.id.guideline33);
                Guideline guideline_right = view.findViewById(R.id.guideline34);
                switch (qrScale) {
                    case 0:
                        guideline_left.setGuidelinePercent(0.00f);
                        guideline_right.setGuidelinePercent(1.00f);
                        MsgUtil.showMsg("切换为大二维码");
                        qrScale = 1;
                        break;
                    case 1:
                        guideline_left.setGuidelinePercent(0.30f);
                        guideline_right.setGuidelinePercent(0.70f);
                        MsgUtil.showMsg("切换为小二维码");
                        qrScale = 2;
                        break;
                    case 2:
                        guideline_left.setGuidelinePercent(0.15f);
                        guideline_right.setGuidelinePercent(0.85f);
                        MsgUtil.showMsg("切换为默认大小");
                        qrScale = 0;
                        break;
                }
            }
        });

        if (isAdded()) refreshQrCode();
    }

    @SuppressLint("SetTextI18n")
    public void refreshQrCode() {
        if (timer != null) timer.cancel();
        need_refresh = true;
        if (qrImageView != null) qrImageView.setEnabled(false);
        CenterThreadPool.run(() -> {
            try {
                CenterThreadPool.runOnUiThread(() -> scanStat.setText("正在获取二维码"));

                CookiesApi.checkCookies();
                QRImage = LoginApi.getLoginQR();
                //CookiesApi.activeCookieInfo();

                CenterThreadPool.runOnUiThread(() -> {
                    need_refresh = false;
                    qrImageView.setEnabled(true);
                    qrImageView.setImageBitmap(QRImage);
                    startLoginDetect();
                });
            } catch (IOException e) {
                CenterThreadPool.runOnUiThread(() -> {
                    need_refresh = true;
                    qrImageView.setEnabled(true);
                    scanStat.setText("获取二维码失败，网络错误\n点击二维码重试");
                });
                e.printStackTrace();
            } catch (JSONException e) {
                CenterThreadPool.runOnUiThread(() -> {
                    need_refresh = true;
                    qrImageView.setEnabled(true);
                    String detail = e.getMessage() == null ? "响应数据无效" : e.getMessage();
                    detail = detail.replaceAll("\\s+", " ");
                    if (detail.length() > 96) detail = detail.substring(0, 96);
                    scanStat.setText("获取二维码失败：" + detail + "\n点击二维码重试");
                });
                e.printStackTrace();
            } catch (Exception e) {
                CenterThreadPool.runOnUiThread(() -> {
                    need_refresh = true;
                    qrImageView.setEnabled(true);
                    scanStat.setText("获取二维码失败，请检查网络后点击二维码重试");
                });
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }

    public void startLoginDetect() {
        if (timer != null) timer.cancel();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                try {
                    Response response = LoginApi.getLoginState();
                    assert response.body() != null;
                    if (!isAdded()) {
                        this.cancel();
                        return;
                    }

                    String str = response.body().string();
                    QrLoginResponse loginState = QrLoginResponse.parse(str);
                    if (!loginState.hasStatus()) {
                        Logu.w("qr_poll", "missing status; responseCode=" + loginState.responseCode);
                        this.cancel();
                        CenterThreadPool.runOnUiThread(() -> {
                            if (!isAdded()) return;
                            need_refresh = true;
                            qrImageView.setEnabled(true);
                            scanStat.setText(loginState.failureSummary() + "\n点击二维码重试，或使用其他设备导入");
                        });
                        return;
                    }
                    JSONObject loginData = loginState.data;
                    int code = loginState.statusCode;
                    Logu.v("login_state", "code=" + code);
                    switch (code) {
                        case 86090:
                            CenterThreadPool.runOnUiThread(() -> scanStat.setText("已扫描，请在手机上点击登录"));
                            break;
                        case 86101:
                            CenterThreadPool.runOnUiThread(() -> scanStat.setText("请使用官方手机端哔哩哔哩扫码登录\n点击二维码可以进行放大和缩小"));
                            break;
                        case 86038:
                            CenterThreadPool.runOnUiThread(() -> {
                                need_refresh = true;
                                scanStat.setText("二维码已失效，点击上方重新获取");
                                qrImageView.setEnabled(true);
                            });
                            this.cancel();
                            break;
                        case 0:
                            this.cancel();
                            CenterThreadPool.runOnUiThread(() -> scanStat.setText("正在处理登录……"));
                            String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");

                            if (!AccountManager.saveLogin(cookies, response.headers("Set-Cookie"),
                                    loginData.optString("refresh_token"))) {
                                CenterThreadPool.runOnUiThread(() ->
                                        scanStat.setText("登录信息保存失败，请重新扫码"));
                                return;
                            }

                            InstanceActivity instance = BiliTerminal.getInstanceActivityOnTop();
                            if (instance != null && !instance.isDestroyed()) instance.finish();

                            LoginApi.requestSSOs();
                            if (loginData.has("url")) {
                                try {
                                    LoginApi.activateLoginRedirect(loginData.optString("url"));
                                } catch (Throwable ignored) {
                                }
                            }

                            startActivity(new Intent(requireContext(), SplashActivity.class));

                            if (isAdded()) requireActivity().finish();
                            break;
                        default:
                            this.cancel();
                            CenterThreadPool.runOnUiThread(() -> {
                                need_refresh = true;
                                qrImageView.setEnabled(true);
                                scanStat.setText("扫码状态暂不支持（" + code + "）\n点击二维码重试，或使用其他设备导入");
                            });
                            break;
                    }

                } catch (Exception e) {
                    if (isAdded()) CenterThreadPool.runOnUiThread(() -> {
                        need_refresh = true;
                        qrImageView.setEnabled(true);
                        scanStat.setText("无法获取扫码状态，点击二维码重试");
                        MsgUtil.err(e);
                    });
                    this.cancel();
                }
            }
        }, 500, 1000);
    }
}
