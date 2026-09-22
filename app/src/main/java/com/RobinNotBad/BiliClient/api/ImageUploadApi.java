package com.RobinNotBad.BiliClient.api;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Small, streaming image uploader shared by dynamic publishing and replies. */
public final class ImageUploadApi {
    // The former create/upload route now returns Bilibili's HTML 404 page.
    private static final String UPLOAD_URL = "https://api.bilibili.com/x/dynamic/feed/draw/upload_bfs";
    private static final String AVATAR_UPLOAD_URL = "https://api.bilibili.com/x/member/web/face/update";
    private static final MediaType IMAGE_TYPE = MediaType.parse("image/*");

    private ImageUploadApi() {
    }

    public static JSONObject upload(Context context, Uri uri) throws IOException, JSONException {
        if (context == null || uri == null) throw new IOException("图片地址为空");
        ContentResolver resolver = context.getContentResolver();
        String name = displayName(resolver, uri);
        RequestBody imageBody = new UriRequestBody(resolver, uri, IMAGE_TYPE);
        String csrf = SharedPreferencesUtil.getString("csrf", "");
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("biz", "new_dyn")
                .addFormDataPart("category", "daily")
                .addFormDataPart("csrf", csrf)
                .addFormDataPart("file_up", name, imageBody)
                .build();

        String uploadUrl = UPLOAD_URL + "?csrf=" + java.net.URLEncoder.encode(csrf, "UTF-8");
        try (Response response = NetWorkUtil.postMultipart(uploadUrl, body)) {
            String raw = response.body() == null ? "" : response.body().string();
            String trimmed = raw.trim();
            if (isHtml(trimmed, response.header("Content-Type"))) {
                throw new IOException("图片上传接口返回了网页，请稍后重试或检查中继");
            }
            if (trimmed.isEmpty()) {
                throw new IOException("图片上传接口返回为空");
            }
            JSONObject result;
            try {
                result = new JSONObject(trimmed);
            } catch (JSONException e) {
                throw new IOException("图片上传接口返回格式异常", e);
            }
            if (result.optInt("code", -1) != 0) {
                throw new IOException(result.optString("message", result.optString("msg", "图片上传失败")));
            }
            JSONObject data = result.optJSONObject("data");
            if (data == null) throw new IOException("图片上传返回为空");
            return data;
        }
    }

    /** Uploads a new account avatar through the web profile endpoint. */
    public static JSONObject uploadAvatar(Context context, Uri uri) throws IOException, JSONException {
        if (context == null || uri == null) throw new IOException("头像地址为空");
        ContentResolver resolver = context.getContentResolver();
        String csrf = SharedPreferencesUtil.getString("csrf", "");
        RequestBody imageBody = new UriRequestBody(resolver, uri, IMAGE_TYPE);
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("dopost", "save")
                .addFormDataPart("Displayrank", "10000")
                .addFormDataPart("csrf", csrf)
                .addFormDataPart("face", displayName(resolver, uri), imageBody)
                .build();
        String uploadUrl = AVATAR_UPLOAD_URL + "?csrf="
                + java.net.URLEncoder.encode(csrf, "UTF-8");
        try (Response response = NetWorkUtil.postMultipart(uploadUrl, body)) {
            String raw = response.body() == null ? "" : response.body().string();
            String trimmed = raw.trim();
            if (isHtml(trimmed, response.header("Content-Type"))) {
                throw new IOException("头像上传接口返回了网页，请稍后重试或检查中继");
            }
            JSONObject result = new JSONObject(trimmed);
            if (result.optInt("code", -1) != 0) {
                throw new IOException(result.optString("message", result.optString("msg", "头像上传失败")));
            }
            return result.optJSONObject("data") == null ? result : result.getJSONObject("data");
        }
    }

    private static boolean isHtml(String body, String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        String lower = body == null ? "" : body.toLowerCase(Locale.US);
        return type.contains("text/html") || lower.startsWith("<!doctype")
                || lower.startsWith("<html") || lower.startsWith("<");
    }

    public static JSONObject toDynamicPicture(JSONObject data) throws JSONException {
        String url = data.optString("image_url", data.optString("url", data.optString("img_src", "")));
        if (url.isEmpty()) throw new JSONException("图片上传没有返回地址");
        return new JSONObject()
                .put("img_src", url)
                .put("img_width", data.optInt("image_width", data.optInt("width", 0)))
                .put("img_height", data.optInt("image_height", data.optInt("height", 0)))
                .put("img_size", data.optLong("image_size", data.optLong("size", 0)));
    }

    public static JSONObject toReplyPicture(JSONObject data) throws JSONException {
        JSONObject picture = toDynamicPicture(data);
        return new JSONObject()
                .put("img_src", picture.optString("img_src"))
                .put("img_width", picture.optInt("img_width"))
                .put("img_height", picture.optInt("img_height"));
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "image.jpg";
    }

    private static final class UriRequestBody extends RequestBody {
        private final ContentResolver resolver;
        private final Uri uri;
        private final MediaType mediaType;

        UriRequestBody(ContentResolver resolver, Uri uri, MediaType mediaType) {
            this.resolver = resolver;
            this.uri = uri;
            this.mediaType = mediaType;
        }

        @Override
        public MediaType contentType() {
            return mediaType;
        }

        @Override
        public long contentLength() {
            try (android.content.res.AssetFileDescriptor descriptor = resolver.openAssetFileDescriptor(uri, "r")) {
                return descriptor == null ? -1 : descriptor.getLength();
            } catch (Exception ignored) {
                return -1;
            }
        }

        @Override
        public void writeTo(@NonNull okio.BufferedSink sink) throws IOException {
            InputStream input = resolver.openInputStream(uri);
            if (input == null) throw new IOException("无法读取图片");
            try {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) sink.write(buffer, 0, count);
            } finally {
                input.close();
            }
        }
    }
}
