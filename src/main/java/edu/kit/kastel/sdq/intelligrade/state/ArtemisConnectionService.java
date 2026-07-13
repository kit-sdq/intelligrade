/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.state;

import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import edu.kit.kastel.sdq.artemis4j.ArtemisClientException;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.client.ArtemisInstance;
import edu.kit.kastel.sdq.artemis4j.grading.ArtemisConnection;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisCredentialsProvider;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.listeners.ArtemisConnectionListener;
import edu.kit.kastel.sdq.intelligrade.login.ArtemisBrowserLoginSession;
import edu.kit.kastel.sdq.intelligrade.login.ArtemisTokenLoginService;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisLoginValidator;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUrlValidator;
import edu.kit.kastel.sdq.intelligrade.utils.ArtemisUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class ArtemisConnectionService {
    private static final Logger LOG = Logger.getInstance(ArtemisConnectionService.class);
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("^Got response code (\\d{3})\\b");
    public static final String JWT_COOKIE_KEY = "jwt";

    private final Project project;

    private volatile ArtemisConnectionState state = new ArtemisConnectionState.Disconnected();

    public ArtemisConnectionService(@NonNull Project project) {
        this.project = project;
    }

    public static ArtemisConnectionService getInstance(@NonNull Project project) {
        return project.getService(ArtemisConnectionService.class);
    }

    public void subscribe(Disposable parentDisposable, ArtemisConnectionListener listener) {
        this.project.getMessageBus().connect(parentDisposable).subscribe(ArtemisConnectionListener.TOPIC, listener);

        ApplicationManager.getApplication().invokeLater(() -> listener.connectionStateChanged(getState()));
    }

    public void logout() {
        clearProjectStates();
        ArtemisTokenLoginService.getInstance().clearStoredJwt();
        ArtemisCredentialsProvider.getInstance().setArtemisPassword(null);
        this.updateState(new ArtemisConnectionState.Disconnected());
    }

    private static void clearProjectStates() {
        for (var project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed()) {
                ProjectState.getInstance(project).clearProjectSessionState();
            }
        }
    }

    /**
     * This function should be called after the project has been loaded to establish a connection based on the stored credentials.
     */
    public void onProjectLoad() {
        // Reading from PasswordSafe may block until the application-level credential
        // prefetch has completed. Keep both that work and the connection attempt off
        // the EDT.
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            var settings = ArtemisSettingsState.getInstance();
            var credentials = ArtemisCredentialsProvider.getInstance();

            if (!hasStoredCredentialsForConnection(settings, credentials)) {
                return;
            }

            LoginMethod loginMethod = settings.isUseTokenLogin()
                    ? new TokenLogin()
                    : new PasswordLogin(settings.getUsername(), credentials.getArtemisPassword());

            var url = settings.getArtemisInstanceUrl();
            if (url != null) {
                connectInBackground(url, loginMethod);
            }
        });
    }

    public ArtemisConnectionState getState() {
        return state;
    }

    private void updateState(ArtemisConnectionState newState) {
        this.state = newState;

        ApplicationManager.getApplication()
                .invokeLater(() -> project.getMessageBus()
                        .syncPublisher(ArtemisConnectionListener.TOPIC)
                        .connectionStateChanged(newState));
    }

    public sealed interface LoginMethod permits TokenLogin, PasswordLogin {}

    public record TokenLogin() implements LoginMethod {}

    public record PasswordLogin(String username, String password) implements LoginMethod {}

    public void connectInBackground(@NonNull String artemisUrl, LoginMethod loginMethod) {
        var urlValidation = ArtemisUrlValidator.validate(artemisUrl);
        if (urlValidation instanceof ArtemisUrlValidator.Result.Invalid(String message)) {
            updateState(new ArtemisConnectionState.Failed(message, ArtemisConnectionState.FailureTarget.URL));
            return;
        }

        if (loginMethod instanceof PasswordLogin(var username, var password)) {
            var usernameError = ArtemisLoginValidator.validateUsername(username);
            if (usernameError != null) {
                updateState(new ArtemisConnectionState.Failed(
                        usernameError, ArtemisConnectionState.FailureTarget.CREDENTIALS));
                return;
            }

            var passwordError = ArtemisLoginValidator.validatePassword(password);
            if (passwordError != null) {
                updateState(new ArtemisConnectionState.Failed(
                        passwordError, ArtemisConnectionState.FailureTarget.CREDENTIALS));
                return;
            }
        }

        updateState(new ArtemisConnectionState.Connecting());
        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> this.establishConnection(artemisUrl, loginMethod));
    }

    @RequiresBackgroundThread
    private void establishConnection(@NonNull String artemisUrl, LoginMethod loginMethod) {
        var urlCheck = ArtemisUrlValidator.checkReachability(artemisUrl);
        if (urlCheck instanceof ArtemisUrlValidator.Result.Invalid(String message)) {
            ArtemisUtils.displayGenericErrorBalloon("Artemis URL validation failed", message);
            updateState(new ArtemisConnectionState.Failed(message, ArtemisConnectionState.FailureTarget.URL));
            return;
        }

        var normalizedUrl = ((ArtemisUrlValidator.Result.Valid) urlCheck).normalizedUrl();
        ArtemisConnection connection;
        try {
            var instance = new ArtemisInstance(normalizedUrl);
            connection = switch (loginMethod) {
                case PasswordLogin passwordLogin ->
                    ArtemisConnection.connectWithUsernamePassword(
                            instance, passwordLogin.username(), passwordLogin.password());
                case TokenLogin _ -> establishTokenConnection(instance);
            };
        } catch (ArtemisNetworkException exception) {
            var target = isAuthenticationFailure(exception)
                    ? ArtemisConnectionState.FailureTarget.CREDENTIALS
                    : ArtemisConnectionState.FailureTarget.GENERAL;
            handleNetworkFailure(exception, target);
            return;
        }

        if (connection == null) {
            return;
        }

        try {
            // This triggers a request and forces a connection error if the token is invalid
            connection.getAssessor();

            completeConnection(normalizedUrl, loginMethod, connection);
        } catch (ArtemisNetworkException exception) {
            handleNetworkFailure(exception, ArtemisConnectionState.FailureTarget.GENERAL);
        }
    }

    @RequiresBackgroundThread
    private @Nullable ArtemisConnection establishTokenConnection(@NonNull ArtemisInstance instance) {
        String previousJwt = ArtemisCredentialsProvider.getInstance().getJwt();
        if (isStoredJwtUsable(ArtemisSettingsState.getInstance(), previousJwt)) {
            return ArtemisConnection.fromToken(instance, previousJwt);
        }

        if (previousJwt != null && !previousJwt.isBlank()) {
            ArtemisTokenLoginService.getInstance().clearStoredJwt();
        }

        if (!ArtemisTokenLoginService.getInstance().isBrowserLoginSupported()) {
            String message = "Browser login is unavailable because JCEF is not supported";
            ArtemisUtils.displayGenericErrorBalloon("Failed to open Artemis login", message);
            updateState(new ArtemisConnectionState.Failed(message));
            return null;
        }

        ArtemisTokenLoginService.TokenLoginResult token;
        try {
            token = ArtemisTokenLoginService.getInstance().requestToken(instance, project);
        } catch (Exception exception) {
            handleTokenLoginFailure(exception);
            return null;
        }

        var connection = ArtemisConnection.fromToken(instance, token.token());
        ArtemisTokenLoginService.getInstance().storeJwt(token);
        return connection;
    }

    @RequiresBackgroundThread
    private void completeConnection(@NonNull String artemisUrl, LoginMethod loginMethod, ArtemisConnection connection) {
        if (loginMethod instanceof PasswordLogin(var username, var password)) {
            ArtemisSettingsState.getInstance().setUsername(username);
            ArtemisCredentialsProvider.getInstance().setArtemisPassword(password);
        }

        ArtemisSettingsState.getInstance().setArtemisInstanceUrl(artemisUrl);
        ArtemisSettingsState.getInstance().setUseTokenLogin(loginMethod instanceof TokenLogin);

        updateState(new ArtemisConnectionState.Connected(connection));
    }

    private void handleTokenLoginFailure(Throwable throwable) {
        Throwable cause = unwrapCompletionException(throwable);
        if (cause instanceof ArtemisBrowserLoginSession.LoginCanceledException) {
            updateState(new ArtemisConnectionState.Disconnected());
            return;
        }

        LOG.warn("Artemis browser login failed", cause);
        ArtemisUtils.displayGenericErrorBalloon("Failed to open Artemis login", cause.getMessage());
        updateState(new ArtemisConnectionState.Failed("Failed to open Artemis login"));
    }

    private static String networkFailureMessage(ArtemisNetworkException exception) {
        String responseCode = responseCode(exception);
        if (responseCode != null) {
            return "Artemis server returned HTTP " + responseCode + ".";
        }

        String detail = exception.getMessage();
        return detail == null || detail.isBlank()
                ? "The Artemis server could not be reached."
                : "The Artemis server could not be reached: " + detail;
    }

    private void handleNetworkFailure(ArtemisNetworkException exception, ArtemisConnectionState.FailureTarget target) {
        String message = target == ArtemisConnectionState.FailureTarget.CREDENTIALS
                ? credentialsFailureMessage(exception)
                : networkFailureMessage(exception);
        if (target == ArtemisConnectionState.FailureTarget.CREDENTIALS) {
            ArtemisUtils.displayGenericErrorBalloon("Artemis login failed", message);
        } else {
            ArtemisUtils.displayNetworkErrorBalloon("Failed to establish connection", exception);
        }
        updateState(new ArtemisConnectionState.Failed(message, target));
    }

    private static boolean isAuthenticationFailure(ArtemisNetworkException exception) {
        String message = exception.getMessage();
        return message != null
                && (message.startsWith("Got response code ")
                        || message.startsWith("Authentication was not successful"));
    }

    private static String credentialsFailureMessage(ArtemisNetworkException exception) {
        String responseCode = responseCode(exception);
        if (responseCode != null) {
            return switch (responseCode) {
                case "400" -> "Artemis rejected the login request (HTTP 400). Check username and password.";
                case "401", "403" -> "Artemis rejected the username or password.";
                default -> "Artemis login request failed with HTTP " + responseCode + ".";
            };
        }

        String detail = exception.getMessage();
        return detail == null || detail.isBlank()
                ? "Artemis rejected the login credentials."
                : "Artemis rejected the login credentials: " + detail;
    }

    private static @Nullable String responseCode(ArtemisClientException exception) {
        var matcher = HTTP_STATUS_PATTERN.matcher(String.valueOf(exception.getMessage()));
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private static boolean hasStoredCredentialsForConnection(
            ArtemisSettingsState settings, ArtemisCredentialsProvider credentials) {
        if (settings.getArtemisInstanceUrl() == null
                || settings.getArtemisInstanceUrl().isBlank()) {
            return false;
        }

        if (settings.isUseTokenLogin()) {
            return isStoredJwtUsable(settings, credentials.getJwt());
        }

        String password = credentials.getArtemisPassword();
        return ArtemisLoginValidator.validateUsername(settings.getUsername()) == null
                && ArtemisLoginValidator.validatePassword(password) == null;
    }

    private static boolean isStoredJwtUsable(ArtemisSettingsState settings, String jwt) {
        Instant expiry = settings.getJwtExpiry();
        return jwt != null && !jwt.isBlank() && (expiry == null || expiry.isAfter(Instant.now()));
    }
}
