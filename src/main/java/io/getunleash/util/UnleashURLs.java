package io.getunleash.util;

import io.getunleash.lang.Nullable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import okhttp3.HttpUrl;

public class UnleashURLs {
    private final URL fetchTogglesURL;
    private final URL clientMetricsURL;
    private final URL clientRegisterURL;
    private final HttpUrl fetchTogglesHttpUrl;
    private final HttpUrl clientMetricsHttpUrl;
    private final HttpUrl clientRegisterHttpUrl;

    public UnleashURLs(URI unleashAPI) {
        try {
            String unleashAPIstr = unleashAPI.toString();
            HttpUrl baseUrl = HttpUrl.get(unleashAPIstr);
            fetchTogglesURL = URI.create(unleashAPIstr + "/client/features").normalize().toURL();
            clientMetricsURL = URI.create(unleashAPIstr + "/client/metrics").normalize().toURL();
            clientRegisterURL = URI.create(unleashAPIstr + "/client/register").normalize().toURL();
            fetchTogglesHttpUrl =
                    baseUrl.newBuilder()
                            .addPathSegment("client")
                            .addPathSegment("features")
                            .build();
            clientMetricsHttpUrl =
                    baseUrl.newBuilder().addPathSegment("client").addPathSegment("metrics").build();
            clientRegisterHttpUrl =
                    baseUrl.newBuilder()
                            .addPathSegment("client")
                            .addPathSegment("register")
                            .build();

        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Unleash API is not a valid URL: " + unleashAPI);
        }
    }

    public URL getFetchTogglesURL() {
        return fetchTogglesURL;
    }

    public URL getClientMetricsURL() {
        return clientMetricsURL;
    }

    public URL getClientRegisterURL() {
        return clientRegisterURL;
    }

    public HttpUrl getFetchTogglesHttpUrl(
            @Nullable String projectName, @Nullable String namePrefix) {
        HttpUrl.Builder fetchToggles = fetchTogglesHttpUrl.newBuilder();
        HttpUrl.Builder projectUrl =
                Optional.ofNullable(projectName)
                        .map(p -> fetchToggles.addQueryParameter("project", p))
                        .orElse(fetchToggles);
        return Optional.ofNullable(namePrefix)
                .map(n -> projectUrl.addQueryParameter("namePrefix", n))
                .orElse(projectUrl)
                .build();
    }

    public URL getFetchTogglesURL(@Nullable String projectName, @Nullable String namePrefix) {
        StringBuilder suffix = new StringBuilder("");
        appendParam(suffix, "project", projectName);
        appendParam(suffix, "namePrefix", namePrefix);

        try {
            return URI.create(fetchTogglesURL + suffix.toString()).normalize().toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new IllegalArgumentException(
                    "fetchTogglesURL [" + fetchTogglesURL + suffix + "] was not URL friendly.", e);
        }
    }

    private void appendParam(StringBuilder suffix, String key, @Nullable String value) {
        if (value == null) {
            return;
        }
        if (suffix.length() == 0) {
            suffix.append("?");
        } else {
            suffix.append("&");
        }
        suffix.append(key).append("=").append(value);
    }
}
