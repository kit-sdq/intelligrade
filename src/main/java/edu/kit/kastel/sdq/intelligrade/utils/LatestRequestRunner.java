/* Licensed under EPL-2.0 2026. */
package edu.kit.kastel.sdq.intelligrade.utils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import org.jspecify.annotations.NonNull;

/**
 * This class provides an easy to use api to correctly fetch and update the UI on the right threads.
 * <p>
 * A common pattern is to request some data (e.g. a network call) and then update the UI with the results.
 * The UI thread (EDT) must not be blocked, a background thread should be used for this.
 * In addition to that, it implements event deduplication, discarding outdated events to prevent them from influencing the UI.
 */
public final class LatestRequestRunner {
    private static final Logger LOG = Logger.getInstance(ArtemisUtils.class);

    private final Project project;
    private final RequestCounter requests;

    public LatestRequestRunner(@NonNull Project project) {
        this.project = project;
        this.requests = new RequestCounter();
    }

    public void invalidate() {
        requests.next();
    }

    public <T> RequestBuilder<T> fetchArtemis(ArtemisSupplier<? extends T> supplier) {
        return new RequestBuilder<T>(supplier::call, requests, project)
                .handle(ArtemisNetworkException.class)
                .withErrorNotification();
    }

    public <T> RequestBuilder<T> fetch(Callable<? extends T> callable) {
        return new RequestBuilder<T>(callable, requests, project).withoutErrorNotification();
    }

    public static final class RequestBuilder<T> {
        private final Project project;
        private final Callable<? extends T> callable;
        private final RequestCounter requests;
        private Function<? super Exception, String> buildErrorMessage;
        private Consumer<? super Exception> onFailureInEdt;
        private @NonNull List<Class<? extends Exception>> exceptions;
        private @NonNull ModalityState modalityState;
        private boolean showErrorNotification;
        private boolean showErrorLog;

        private RequestBuilder(Callable<? extends T> callable, RequestCounter requests, Project project) {
            this.project = project;
            this.callable = callable;
            this.requests = requests;
            this.exceptions = List.of();
            this.modalityState = ModalityState.defaultModalityState();
            this.showErrorNotification = false;
            this.showErrorLog = true;
        }

        @SafeVarargs
        public final RequestBuilder<T> handle(Class<? extends Exception>... exceptions) {
            this.exceptions = Arrays.asList(exceptions);

            return this;
        }

        public RequestBuilder<T> withModalityState(@NonNull ModalityState modalityState) {
            this.modalityState = modalityState;
            return this;
        }

        public RequestBuilder<T> withErrorNotification() {
            this.showErrorNotification = true;

            return this;
        }

        public RequestBuilder<T> withErrorNotification(String errorMessage) {
            return this.withErrorNotification(_ -> errorMessage);
        }

        public RequestBuilder<T> withErrorNotification(Function<? super Exception, String> errorMessage) {
            this.buildErrorMessage = errorMessage;
            this.showErrorNotification = true;

            return this;
        }

        public RequestBuilder<T> withoutErrorNotification() {
            this.showErrorNotification = false;
            return this;
        }

        public RequestBuilder<T> withoutErrorLogging() {
            this.showErrorLog = false;
            return this;
        }

        public RequestBuilder<T> onFailureInEdt(Consumer<? super Exception> onFailureInEdt) {
            this.onFailureInEdt = onFailureInEdt;
            return this;
        }

        public void thenIf(BooleanSupplier stillRelevant, Consumer<? super T> apply) {
            int requestId = this.requests.next();
            Runnable request = () -> {
                try {
                    T result = this.callable.call();
                    invokeOnEDTIfRelevant(requestId, stillRelevant, () -> apply.accept(result));
                } catch (Exception exception) {
                    handleException(requestId, stillRelevant, exception);
                }
            };

            if (ApplicationManager.getApplication().isDispatchThread()) {
                ApplicationManager.getApplication().executeOnPooledThread(request);
                return;
            }

            request.run();
        }

        private void handleException(int requestId, BooleanSupplier stillRelevant, Exception exception) {
            if (!handlesException(exception)) {
                // Pass along any exception that the code does not want to handle.
                if (exception instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }

                // Checked exceptions must be handled, but the caller does not want
                // to handle these, which is why they are wrapped in a RuntimeException.
                throw new IllegalStateException(exception);
            }

            if (this.showErrorLog) {
                LOG.warn(exception);
            }

            showErrorNotification(exception);
            if (onFailureInEdt != null) {
                invokeOnEDTIfRelevant(requestId, stillRelevant, () -> onFailureInEdt.accept(exception));
            }
        }

        private boolean handlesException(Exception exception) {
            return this.exceptions.stream().anyMatch(handledException -> handledException.isInstance(exception));
        }

        private void showErrorNotification(Exception exception) {
            if (!this.showErrorNotification) {
                return;
            }

            String title = exception instanceof ArtemisNetworkException ? "Network Error" : "Error";
            var content = exception.getMessage();
            if (buildErrorMessage != null) {
                content = "%s (%s)".formatted(buildErrorMessage.apply(exception), exception.getMessage());
            }

            ArtemisUtils.displayGenericErrorBalloon(title, content);
        }

        private void invokeOnEDTIfRelevant(int requestId, BooleanSupplier stillRelevant, Runnable action) {
            ApplicationManager.getApplication()
                    .invokeLater(
                            () -> {
                                if (project.isDisposed()
                                        || !requests.isCurrent(requestId)
                                        || !stillRelevant.getAsBoolean()) {
                                    return;
                                }

                                action.run();
                            },
                            modalityState);
        }
    }

    @FunctionalInterface
    public interface ArtemisSupplier<T> {
        T call() throws ArtemisNetworkException;
    }

    private static class RequestCounter {
        private final AtomicInteger current = new AtomicInteger();

        int next() {
            return this.current.incrementAndGet();
        }

        boolean isCurrent(int request) {
            return this.current.get() == request;
        }
    }
}
