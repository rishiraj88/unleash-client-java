package io.getunleash.repository;

import static io.getunleash.util.UnleashConfig.UNLEASH_APP_NAME_HEADER;
import static io.getunleash.util.UnleashConfig.UNLEASH_INSTANCE_ID_HEADER;

import com.google.gson.JsonSyntaxException;
import io.getunleash.UnleashException;
import io.getunleash.util.UnleashConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import okhttp3.Cache;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OkHttpToggleFetcher implements ToggleFetcher {
    private static final Logger LOG = LoggerFactory.getLogger(OkHttpToggleFetcher.class);

    private static final int CONNECT_TIMEOUT = 10000;

    private final HttpUrl toggleUrl;
    private final UnleashConfig unleashConfig;
    private final OkHttpClient client;

    public OkHttpToggleFetcher(UnleashConfig unleashConfig) {
        File tempDir = null;
        try {
            tempDir = Files.createTempDirectory("http_cache").toFile();
        } catch (IOException ignored) {
        }

        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .callTimeout(Duration.ofSeconds(5));
        if (tempDir != null) {
            builder = builder.cache(new Cache(tempDir, 1024 * 1024 * 50));
        }
        if (unleashConfig.getProxy() != null) {
            builder = builder.proxy(unleashConfig.getProxy());
        }
        this.client =
                builder.followRedirects(true)
                        .addInterceptor(
                                (c) -> {
                                    Request.Builder headers =
                                            c.request()
                                                    .newBuilder()
                                                    .addHeader("Content-Type", "application/json")
                                                    .addHeader("Accept", "application/json")
                                                    .addHeader(
                                                            UNLEASH_APP_NAME_HEADER,
                                                            unleashConfig.getAppName())
                                                    .addHeader(
                                                            UNLEASH_INSTANCE_ID_HEADER,
                                                            unleashConfig.getInstanceId())
                                                    .addHeader(
                                                            "User-Agent",
                                                            unleashConfig.getAppName());
                                    for (Map.Entry<String, String> headerEntry :
                                            unleashConfig.getCustomHttpHeaders().entrySet()) {
                                        headers =
                                                headers.addHeader(
                                                        headerEntry.getKey(),
                                                        headerEntry.getValue());
                                    }
                                    for (Map.Entry<String, String> headerEntry :
                                            unleashConfig
                                                    .getCustomHttpHeadersProvider()
                                                    .getCustomHeaders()
                                                    .entrySet()) {
                                        headers =
                                                headers.addHeader(
                                                        headerEntry.getKey(),
                                                        headerEntry.getValue());
                                    }
                                    return c.proceed(headers.build());
                                })
                        .build();
        this.unleashConfig = unleashConfig;
        this.toggleUrl =
                unleashConfig
                        .getUnleashURLs()
                        .getFetchTogglesHttpUrl(
                                unleashConfig.getProjectName(), unleashConfig.getNamePrefix());
    }

    @Override
    public FeatureToggleResponse fetchToggles() throws UnleashException {
        Request request = new Request.Builder().url(toggleUrl).get().build();
        HttpUrl location = toggleUrl;
        int code = 200;
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                location = response.request().url();
                code = response.code();
                if (response.cacheResponse() != null) {
                    return new FeatureToggleResponse(FeatureToggleResponse.Status.NOT_CHANGED, 304);
                } else {
                    ToggleCollection toggles =
                            JsonToggleParser.fromJson(response.body().charStream());
                    return new FeatureToggleResponse(FeatureToggleResponse.Status.CHANGED, toggles);
                }
            } else if (response.code() >= 301 && response.code() <= 304) {
                return new FeatureToggleResponse(
                        FeatureToggleResponse.Status.NOT_CHANGED, response.code());
            } else {
                return new FeatureToggleResponse(
                        FeatureToggleResponse.Status.UNAVAILABLE, response.code());
            }
        } catch (IOException ioEx) {
            throw new UnleashException("Could not fetch toggles", ioEx);
        } catch (IllegalStateException | JsonSyntaxException ex) {
            return new FeatureToggleResponse(
                    FeatureToggleResponse.Status.UNAVAILABLE, code, location.toString());
        }
    }
}
