package org.builder.session.jackson.request;

import java.util.Optional;
import java.util.UUID;

import org.build.session.jackson.proto.Error;
import org.build.session.jackson.proto.ErrorCode;
import org.builder.session.jackson.exception.ConsumerClientException;
import org.builder.session.jackson.exception.ConsumerDependencyException;
import org.builder.session.jackson.exception.ConsumerInternalException;
import org.slf4j.Logger;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@NoArgsConstructor(access = AccessLevel.NONE)
public final class ErrorHandler {

    public static <R, T> ResultOrError<T> wrap(@NonNull final ApiCall<R, T> call,
                                               @NonNull R request,
                                               @NonNull final Logger log) {
        String requestType = request.getClass().getSimpleName();
        String requestId = UUID.randomUUID().toString();
        try {
            log.trace("Initiating {} request (id: {}) {}",
                     new Object[] { requestType, requestId, request });
            ResultOrError<T> result = ResultOrError.result(call.call(request, requestId));
            log.info("Handled {} request (id: {}) {} returned {}",
                     new Object[] { requestType, requestId, request, result.getResult() });
            return result;
        } catch (Throwable ex) {
            log.error("Failed {} request (id: {}) {}. Due to: {}",
                      new Object[] { requestType, requestId, request, ex });
            return ResultOrError.error(ex);
        }
    }



    /**
     * A way to model an error in a consistent manner so that the
     * client can properly handle the error.
     */
    @Data
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @ToString
    @EqualsAndHashCode
    public static class ResultOrError<T> {
        @NonNull
        private final Optional<T> result;
        @NonNull
        private final Optional<CapturedError> error;



        public boolean wasSuccessful() {
            return result.isPresent();
        }

        public boolean wasFailure() {
            return error.isPresent();
        }

        public T getResult() {
            return result.get();
        }

        public Error getError() {
            return error.map(e -> e.toError()).get();
        }

        public Throwable getThrownException() {
            return error.map(e -> e.getThrowable()).get();
        }

        public static <T> ResultOrError<T> result(@NonNull T result) {
            return new ResultOrError<T>(Optional.of(result), Optional.empty());
        }

        public static <T> ResultOrError<T> error(@NonNull Throwable throwable) {
            return new ResultOrError<T>(Optional.empty(), Optional.of(new CapturedError(throwable)));
        }
    }

    @Data
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @ToString
    @EqualsAndHashCode
    public static class CapturedError {
        @NonNull
        private final Throwable throwable;
        @NonNull
        private final ErrorCode error;


        public CapturedError(@NonNull Throwable t) {
            this(t, resolve(t));
        }

        private static ErrorCode resolve(Throwable t) {
            String exceptonClassName = t.getClass().getSimpleName();
            if(t instanceof IllegalArgumentException
                    || exceptonClassName.contains("InvalidParameter")
                    || exceptonClassName.contains("IllegalParameter")) {
                return ErrorCode.INVALID_PARAMETER;
            } else if (t instanceof ConsumerClientException) {
                return ErrorCode.CLIENT_FAILURE;
            } else if (t instanceof ConsumerDependencyException) {
                return ErrorCode.DEPENDENCY_FAILURE;
            } else if(t instanceof IllegalStateException
                    || t instanceof UnsupportedOperationException
                    || t instanceof ConsumerInternalException) {
                return ErrorCode.INTERNAL_FAILURE;
            } else {
                return ErrorCode.UNKNOWN;
            }
        }

        public Error toError() {
            return Error.newBuilder()
                        .setType(error)
                        .setMessage(throwable.toString())
                        .build();
        }
    }
}
