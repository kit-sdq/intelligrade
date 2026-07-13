/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.utils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Validates Artemis instance URLs and checks whether an instance is reachable. */
public final class ArtemisUrlValidator {
    private static final int CONNECTION_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 5_000;

    private ArtemisUrlValidator() {}

    public sealed interface Result permits Result.Valid, Result.Invalid {
        record Valid(@NonNull String normalizedUrl, @NonNull URI uri) implements Result {}

        record Invalid(@NonNull String message) implements Result {}
    }

    /**
     * Checks the URL without performing network I/O.
     *
     * @param url the user-provided Artemis URL
     * @return a valid, normalized URL or a descriptive validation error
     */
    public static @NonNull Result validate(String url) {
        String normalizedUrl = normalize(url);
        if (normalizedUrl.isBlank()) {
            return new Result.Invalid("Artemis URL must not be empty.");
        }

        final URI uri;
        try {
            uri = URI.create(normalizedUrl);
        } catch (IllegalArgumentException exception) {
            if (normalizedUrl.matches("(?i)https?://\\s*")) {
                return new Result.Invalid("Artemis URL must contain a valid hostname.");
            }
            return new Result.Invalid("Artemis URL is malformed: " + exception.getMessage());
        }

        if (!uri.isAbsolute()) {
            return new Result.Invalid("Artemis URL must include a scheme, such as https://.");
        }

        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            return new Result.Invalid("Artemis URL must use the http:// or https:// scheme.");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return new Result.Invalid("Artemis URL must contain a valid hostname.");
        }

        if (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65_535)) {
            return new Result.Invalid("Artemis URL contains a port outside the valid range 1-65535.");
        }

        return new Result.Valid(normalizedUrl, uri);
    }

    /**
     * Checks the URL syntax and then verifies that the Artemis root endpoint responds successfully.
     * This method must only be called from a background thread.
     *
     * @param url the user-provided Artemis URL
     * @return a valid, reachable URL or a descriptive validation error
     */
    public static @NonNull Result checkReachability(String url) {
        Result validation = validate(url);
        if (validation instanceof Result.Invalid) {
            return validation;
        }

        var validUrl = (Result.Valid) validation;
        try {
            var connection = (HttpURLConnection) validUrl.uri().toURL().openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(true);

            try {
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return new Result.Invalid("Artemis server returned HTTP " + responseCode + ".");
                }
            } finally {
                connection.disconnect();
            }
        } catch (UnknownHostException exception) {
            return new Result.Invalid(
                    "Could not resolve the Artemis hostname '" + validUrl.uri().getHost() + "'.");
        } catch (SocketTimeoutException exception) {
            return new Result.Invalid("The Artemis server did not respond before the connection timed out.");
        } catch (ConnectException exception) {
            return new Result.Invalid("Could not connect to the Artemis server.");
        } catch (IOException exception) {
            String detail = exception.getMessage();
            return new Result.Invalid(
                    detail == null || detail.isBlank()
                            ? "The Artemis server could not be reached."
                            : "The Artemis server could not be reached: " + detail);
        }

        return validUrl;
    }

    /**
     * Normalizes values before they are validated or persisted.
     *
     * @param url the user-provided URL
     * @return a trimmed URL without redundant trailing slashes
     */
    public static @NonNull String normalize(@Nullable String url) {
        if (url == null) {
            return "";
        }

        var normalizedUrl = url.trim();
        while (normalizedUrl.endsWith("/") && normalizedUrl.length() > "https://".length()) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }
        return normalizedUrl;
    }
}
