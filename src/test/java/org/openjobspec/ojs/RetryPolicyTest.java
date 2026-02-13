package org.openjobspec.ojs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    // ------------------------------------------------------------------ //
    //  DEFAULT constant
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("DEFAULT constant")
    class DefaultConstant {

        @Test
        @DisplayName("DEFAULT has expected maxAttempts")
        void defaultMaxAttempts() {
            assertEquals(3, RetryPolicy.DEFAULT.maxAttempts());
        }

        @Test
        @DisplayName("DEFAULT has 1-second initial interval")
        void defaultInitialInterval() {
            assertEquals(Duration.ofSeconds(1), RetryPolicy.DEFAULT.initialInterval());
        }

        @Test
        @DisplayName("DEFAULT has backoff coefficient of 2.0")
        void defaultBackoffCoefficient() {
            assertEquals(2.0, RetryPolicy.DEFAULT.backoffCoefficient());
        }

        @Test
        @DisplayName("DEFAULT has 5-minute max interval")
        void defaultMaxInterval() {
            assertEquals(Duration.ofMinutes(5), RetryPolicy.DEFAULT.maxInterval());
        }

        @Test
        @DisplayName("DEFAULT has jitter enabled")
        void defaultJitter() {
            assertTrue(RetryPolicy.DEFAULT.jitter());
        }

        @Test
        @DisplayName("DEFAULT has empty nonRetryableErrors")
        void defaultNonRetryableErrors() {
            assertNotNull(RetryPolicy.DEFAULT.nonRetryableErrors());
            assertTrue(RetryPolicy.DEFAULT.nonRetryableErrors().isEmpty());
        }

        @Test
        @DisplayName("DEFAULT has 'discard' onExhaustion")
        void defaultOnExhaustion() {
            assertEquals("discard", RetryPolicy.DEFAULT.onExhaustion());
        }
    }

    // ------------------------------------------------------------------ //
    //  Builder defaults
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Builder with defaults")
    class BuilderDefaults {

        private final RetryPolicy policy = RetryPolicy.builder().build();

        @Test
        @DisplayName("builder default matches DEFAULT constant")
        void builderDefaultMatchesConstant() {
            assertEquals(RetryPolicy.DEFAULT, policy);
        }

        @Test
        @DisplayName("builder default maxAttempts is 3")
        void builderDefaultMaxAttempts() {
            assertEquals(3, policy.maxAttempts());
        }

        @Test
        @DisplayName("builder default initialInterval is 1s")
        void builderDefaultInitialInterval() {
            assertEquals(Duration.ofSeconds(1), policy.initialInterval());
        }

        @Test
        @DisplayName("builder default backoffCoefficient is 2.0")
        void builderDefaultBackoffCoefficient() {
            assertEquals(2.0, policy.backoffCoefficient());
        }

        @Test
        @DisplayName("builder default maxInterval is 5m")
        void builderDefaultMaxInterval() {
            assertEquals(Duration.ofMinutes(5), policy.maxInterval());
        }

        @Test
        @DisplayName("builder default jitter is true")
        void builderDefaultJitter() {
            assertTrue(policy.jitter());
        }

        @Test
        @DisplayName("builder default nonRetryableErrors is empty")
        void builderDefaultNonRetryableErrors() {
            assertTrue(policy.nonRetryableErrors().isEmpty());
        }

        @Test
        @DisplayName("builder default onExhaustion is 'discard'")
        void builderDefaultOnExhaustion() {
            assertEquals("discard", policy.onExhaustion());
        }
    }

    // ------------------------------------------------------------------ //
    //  Builder custom values
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Builder with custom values")
    class BuilderCustom {

        @Test
        @DisplayName("can set maxAttempts")
        void customMaxAttempts() {
            var policy = RetryPolicy.builder().maxAttempts(5).build();
            assertEquals(5, policy.maxAttempts());
        }

        @Test
        @DisplayName("can set initialInterval")
        void customInitialInterval() {
            var policy = RetryPolicy.builder().initialInterval(Duration.ofMillis(500)).build();
            assertEquals(Duration.ofMillis(500), policy.initialInterval());
        }

        @Test
        @DisplayName("can set backoffCoefficient")
        void customBackoffCoefficient() {
            var policy = RetryPolicy.builder().backoffCoefficient(3.5).build();
            assertEquals(3.5, policy.backoffCoefficient());
        }

        @Test
        @DisplayName("can set maxInterval")
        void customMaxInterval() {
            var policy = RetryPolicy.builder().maxInterval(Duration.ofHours(1)).build();
            assertEquals(Duration.ofHours(1), policy.maxInterval());
        }

        @Test
        @DisplayName("can disable jitter")
        void customJitterDisabled() {
            var policy = RetryPolicy.builder().jitter(false).build();
            assertFalse(policy.jitter());
        }

        @Test
        @DisplayName("can set nonRetryableErrors")
        void customNonRetryableErrors() {
            var errors = List.of("IllegalArgumentException", "NullPointerException");
            var policy = RetryPolicy.builder().nonRetryableErrors(errors).build();
            assertEquals(errors, policy.nonRetryableErrors());
        }

        @Test
        @DisplayName("can set onExhaustion")
        void customOnExhaustion() {
            var policy = RetryPolicy.builder().onExhaustion("dead_letter").build();
            assertEquals("dead_letter", policy.onExhaustion());
        }

        @Test
        @DisplayName("can set all fields via builder")
        void allFieldsCustom() {
            var errors = List.of("FatalError");
            var policy = RetryPolicy.builder()
                    .maxAttempts(10)
                    .initialInterval(Duration.ofSeconds(2))
                    .backoffCoefficient(1.5)
                    .maxInterval(Duration.ofMinutes(10))
                    .jitter(false)
                    .nonRetryableErrors(errors)
                    .onExhaustion("dead_letter")
                    .build();

            assertAll(
                    () -> assertEquals(10, policy.maxAttempts()),
                    () -> assertEquals(Duration.ofSeconds(2), policy.initialInterval()),
                    () -> assertEquals(1.5, policy.backoffCoefficient()),
                    () -> assertEquals(Duration.ofMinutes(10), policy.maxInterval()),
                    () -> assertFalse(policy.jitter()),
                    () -> assertEquals(errors, policy.nonRetryableErrors()),
                    () -> assertEquals("dead_letter", policy.onExhaustion())
            );
        }

        @Test
        @DisplayName("maxAttempts of 0 disables retries")
        void zeroMaxAttempts() {
            var policy = RetryPolicy.builder().maxAttempts(0).build();
            assertEquals(0, policy.maxAttempts());
        }

        @Test
        @DisplayName("backoffCoefficient of exactly 1.0 is valid")
        void backoffCoefficientExactlyOne() {
            var policy = RetryPolicy.builder().backoffCoefficient(1.0).build();
            assertEquals(1.0, policy.backoffCoefficient());
        }
    }

    // ------------------------------------------------------------------ //
    //  Validation errors
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("negative maxAttempts throws IllegalArgumentException")
        void negativeMaxAttempts() {
            var builder = RetryPolicy.builder().maxAttempts(-1);
            var ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains("maxAttempts"));
        }

        @Test
        @DisplayName("backoffCoefficient below 1.0 throws IllegalArgumentException")
        void backoffCoefficientBelowOne() {
            var builder = RetryPolicy.builder().backoffCoefficient(0.5);
            var ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains("backoffCoefficient"));
        }

        @Test
        @DisplayName("backoffCoefficient of 0 throws IllegalArgumentException")
        void backoffCoefficientZero() {
            var builder = RetryPolicy.builder().backoffCoefficient(0.0);
            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        @DisplayName("null initialInterval throws NullPointerException")
        void nullInitialInterval() {
            var builder = RetryPolicy.builder().initialInterval(null);
            assertThrows(NullPointerException.class, builder::build);
        }

        @Test
        @DisplayName("null maxInterval throws NullPointerException")
        void nullMaxInterval() {
            var builder = RetryPolicy.builder().maxInterval(null);
            assertThrows(NullPointerException.class, builder::build);
        }

        @Test
        @DisplayName("null nonRetryableErrors defaults to empty list")
        void nullNonRetryableErrorsDefaultsToEmpty() {
            var policy = new RetryPolicy(3, Duration.ofSeconds(1), 2.0,
                    Duration.ofMinutes(5), true, null, "discard");
            assertNotNull(policy.nonRetryableErrors());
            assertTrue(policy.nonRetryableErrors().isEmpty());
        }

        @Test
        @DisplayName("null onExhaustion defaults to 'discard'")
        void nullOnExhaustionDefaultsToDiscard() {
            var policy = new RetryPolicy(3, Duration.ofSeconds(1), 2.0,
                    Duration.ofMinutes(5), true, List.of(), null);
            assertEquals("discard", policy.onExhaustion());
        }
    }

    // ------------------------------------------------------------------ //
    //  Record equality and accessors
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Record behavior")
    class RecordBehavior {

        @Test
        @DisplayName("two policies with identical fields are equal")
        void equalPolicies() {
            var a = RetryPolicy.builder().maxAttempts(5).build();
            var b = RetryPolicy.builder().maxAttempts(5).build();
            assertEquals(a, b);
        }

        @Test
        @DisplayName("two policies with different fields are not equal")
        void unequalPolicies() {
            var a = RetryPolicy.builder().maxAttempts(3).build();
            var b = RetryPolicy.builder().maxAttempts(7).build();
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("hashCode is consistent with equals")
        void hashCodeConsistent() {
            var a = RetryPolicy.builder().maxAttempts(5).jitter(false).build();
            var b = RetryPolicy.builder().maxAttempts(5).jitter(false).build();
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("toString contains field values")
        void toStringContainsFields() {
            var policy = RetryPolicy.DEFAULT;
            var str = policy.toString();
            assertTrue(str.contains("maxAttempts=3"));
            assertTrue(str.contains("discard"));
        }
    }
}
