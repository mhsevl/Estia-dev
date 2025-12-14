package com.example.estia;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public final class DownloaderImpl extends Downloader {

    // -------------------- Constants --------------------

    private static final int READ_TIMEOUT_SECONDS = 30;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";

    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE_KEY =
            "youtube_restricted_mode_key";
    public static final String YOUTUBE_RESTRICTED_MODE_COOKIE =
            "PREF=f2=8000000";
    public static final String YOUTUBE_DOMAIN =
            "youtube.com";
    public static final String RECAPTCHA_COOKIES_KEY =
            "recaptcha_cookies";

    // -------------------- Singleton --------------------

    private static DownloaderImpl instance;

    // -------------------- Members --------------------

    private final OkHttpClient httpClient;
    private final Map<String, String> cookies;

    // -------------------- Constructor --------------------

    private DownloaderImpl(@NonNull OkHttpClient.Builder builder) {
        this.httpClient = builder
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        this.cookies = new HashMap<>();
    }

    /**
     * Initializes the downloader.
     * Should be called exactly once during application lifecycle.
     *
     * @param builder Custom OkHttp builder or null for default configuration
     * @return Singleton instance of DownloaderImpl
     */
    public static synchronized DownloaderImpl init(
            @Nullable OkHttpClient.Builder builder
    ) {
        if (instance == null) {
            instance = new DownloaderImpl(
                    builder != null ? builder : new OkHttpClient.Builder()
            );
        }
        return instance;
    }

    public static DownloaderImpl getInstance() {
        return instance;
    }

    // -------------------- Core Logic --------------------

    @Override
    public Response execute(@NonNull Request request)
            throws IOException, ReCaptchaException {

        okhttp3.Request okHttpRequest = buildOkHttpRequest(request);
        okhttp3.Response okHttpResponse = httpClient.newCall(okHttpRequest).execute();

        if (isReCaptchaResponse(okHttpResponse)) {
            okHttpResponse.close();
            throw new ReCaptchaException(
                    "reCaptcha Challenge requested",
                    request.url()
            );
        }

        return buildExtractorResponse(okHttpResponse);
    }

    // -------------------- Helper Methods --------------------

    private okhttp3.Request buildOkHttpRequest(@NonNull Request request) {
        RequestBody requestBody = createRequestBody(request.dataToSend());

        okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                .url(request.url())
                .method(request.httpMethod(), requestBody)
                .addHeader("User-Agent", USER_AGENT);

        addHeaders(builder, request.headers());
        return builder.build();
    }

    private RequestBody createRequestBody(@Nullable byte[] data) {
        return data != null ? RequestBody.create(null, data) : null;
    }

    private void addHeaders(
            okhttp3.Request.Builder builder,
            Map<String, List<String>> headers
    ) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            List<String> values = entry.getValue();

            builder.removeHeader(headerName);
            for (String value : values) {
                builder.addHeader(headerName, value);
            }
        }
    }

    private boolean isReCaptchaResponse(okhttp3.Response response) {
        return response.code() == 429;
    }

    private Response buildExtractorResponse(okhttp3.Response response)
            throws IOException {

        ResponseBody body = response.body();
        String responseBody = body != null ? body.string() : null;

        String latestUrl = response.request().url().toString();

        return new Response(
                response.code(),
                response.message(),
                response.headers().toMultimap(),
                responseBody,
                latestUrl
        );
    }
}
