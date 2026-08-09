package com.RobinNotBad.BiliClient.helper;

import android.content.Context;

import androidx.annotation.NonNull;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;

@GlideModule
public class CustomGlideModule extends AppGlideModule {
    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setDefaultRequestOptions(
                new RequestOptions().format(DecodeFormat.PREFER_RGB_565)
        );
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        OkHttpClient.Builder builder = NetWorkUtil.setOkHttpSsl(new OkHttpClient.Builder());
        builder.addInterceptor(chain -> {
            String requestUrl = chain.request().url().toString();
            Request.Builder requestBuilder = chain.request().newBuilder();
            requestBuilder.header("User-Agent", NetWorkUtil.USER_AGENT_WEB);
            if (NetWorkUtil.shouldAttachBiliCredentials(requestUrl)) {
                ArrayList<String> headers = NetWorkUtil.webHeaders;
                for (int i = 0; i < headers.size(); i += 2)
                    requestBuilder.header(headers.get(i), headers.get(i + 1));
            }
            NetWorkUtil.addRelayHeaders(requestBuilder, requestUrl);

            return chain.proceed(requestBuilder.build());
        });

        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(builder
                .dns(new NetWorkUtil.Inet4Selector())
                .pingInterval(8, TimeUnit.SECONDS)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(16, TimeUnit.SECONDS).build()));
    }
}
