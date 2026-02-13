package org.openjobspec.ojs;

import org.openjobspec.ojs.transport.HttpTransport;
import org.openjobspec.ojs.transport.Transport;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OJS worker for processing jobs. Uses virtual threads (Project Loom) for concurrent execution.
 *
 * <pre>{@code
 * var worker = OJSWorker.builder()
 *     .url("http://localhost:8080")
 *     .queues(List.of("default", "email"))
 *     .concurrency(10)
 *     .build();
 *
 * worker.register("email.send", ctx -> {
 *     var to = (String) ctx.job().args().get("to");
 *     sendEmail(to);
 *     return Map.of("messageId", "...");
 * });
 *
 * worker.use((ctx, next) -> {
 *     System.out.printf("Processing %s%n", ctx.job().type());
 *     next.handle(ctx);
 * });
 *
 * worker.start();
 * }</pre>
 */
public final class OJSWorker {

    /** Worker lifecycle states. */
    public enum State {
        RUNNING("running"),
        QUIET("quiet"),
        TERMINATE("terminate");

        private final String value;

        State(String value) { this.value = value; }

        public String value() { return value; }
    }

    private final Transport transport;
    private final String workerId;
    private final List<String> queues;
    private final int concurrency;
    private final Duration gracePeriod;
    private final Duration heartbeatInterval;
    private final Duration pollInterval;
    private final List<String> labels;

    private final Map<String, JobHandler> handlers = new ConcurrentHashMap<>();
    private final List<NamedMiddleware> middlewareChain = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Thread> activeJobs = new ConcurrentHashMap<>();
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicReference<State> state = new AtomicReference<>(State.RUNNING);
    private final Semaphore semaphore;
    private volatile boolean stopped = false;
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    private record NamedMiddleware(String name, Middleware middleware) {}

    private OJSWorker(Builder builder) {
        this.transport = builder.transport != null ? builder.transport
                : HttpTransport.builder()
                    .url(builder.url)
                    .httpClient(builder.httpClient)
                    .authToken(builder.authToken)
                    .headers(builder.headers)
                    .build();
        this.workerId = "worker_" + UUID.randomUUID().toString().substring(0, 12);
        this.queues = builder.queues != null ? List.copyOf(builder.queues) : List.of("default");
        this.concurrency = builder.concurrency > 0 ? builder.concurrency : 10;
        this.gracePeriod = builder.gracePeriod != null ? builder.gracePeriod : Duration.ofSeconds(25);
        this.heartbeatInterval = builder.heartbeatInterval != null
                ? builder.heartbeatInterval : Duration.ofSeconds(5);
        this.pollInterval = builder.pollInterval != null
                ? builder.pollInterval : Duration.ofSeconds(1);
        this.labels = builder.labels != null ? List.copyOf(builder.labels) : List.of();
        this.semaphore = new Semaphore(this.concurrency);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Register a handler for a job type.
     *
     * @param jobType the job type (e.g. "email.send")
     * @param handler the handler function
     */
    public void register(String jobType, JobHandler handler) {
        Objects.requireNonNull(jobType, "jobType must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        handlers.put(jobType, handler);
    }

    /**
     * Add execution middleware to the chain.
     *
     * @param middleware the middleware to add
     */
    public void use(Middleware middleware) {
        use(null, middleware);
    }

    /**
     * Add named execution middleware to the chain.
     *
     * @param name       middleware name for identification
     * @param middleware the middleware to add
     */
    public void use(String name, Middleware middleware) {
        Objects.requireNonNull(middleware, "middleware must not be null");
        middlewareChain.add(new NamedMiddleware(name, middleware));
    }

    /**
     * Start the worker. This method blocks until the worker is stopped.
     * Uses virtual threads for concurrent job processing.
     */
    public void start() {
        if (handlers.isEmpty()) {
            throw new IllegalStateException("No handlers registered. Call register() before start().");
        }

        state.set(State.RUNNING);
        stopped = false;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Launch fetch loop and heartbeat loop as virtual threads
            var fetchFuture = executor.submit(this::fetchLoop);
            var heartbeatFuture = executor.submit(this::heartbeatLoop);

            // Block until stop is called
            stopLatch.await();

            // Graceful shutdown
            state.set(State.TERMINATE);

            // Wait for active jobs to complete within grace period
            var deadline = Instant.now().plus(gracePeriod);
            while (activeCount.get() > 0 && Instant.now().isBefore(deadline)) {
                Thread.sleep(100);
            }

            fetchFuture.cancel(true);
            heartbeatFuture.cancel(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stop the worker gracefully. Active jobs are given the grace period to complete.
     */
    public void stop() {
        state.set(State.QUIET);
        stopped = true;
        stopLatch.countDown();
    }

    /** Get the current worker state. */
    public State getState() {
        return state.get();
    }

    /** Get the number of currently active jobs. */
    public int getActiveJobCount() {
        return activeCount.get();
    }

    /** Get the worker ID. */
    public String getWorkerId() {
        return workerId;
    }

    private void fetchLoop() {
        while (!stopped && state.get() == State.RUNNING) {
            int acquired = 0;
            try {
                if (!semaphore.tryAcquire(pollInterval.toMillis(), TimeUnit.MILLISECONDS)) {
                    continue;
                }
                acquired = 1 + semaphore.drainPermits();
                int fetchCount = Math.min(acquired, concurrency);

                var jobs = fetchJobs(fetchCount);
                if (jobs.isEmpty()) {
                    semaphore.release(acquired);
                    acquired = 0;
                    Thread.sleep(pollInterval.toMillis());
                    continue;
                }

                for (var job : jobs) {
                    Thread.startVirtualThread(() -> processJob(job));
                }

                // Release unused permits if we got fewer jobs than requested
                int unused = acquired - jobs.size();
                if (unused > 0) {
                    semaphore.release(unused);
                }
                acquired = 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Release permits on error and backoff before retrying
                if (acquired > 0) {
                    semaphore.release(acquired);
                }
                try { Thread.sleep(pollInterval.toMillis()); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void heartbeatLoop() {
        while (!stopped) {
            try {
                Thread.sleep(heartbeatInterval.toMillis());
                sendHeartbeat();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
                // Continue heartbeating even if one fails
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Job> fetchJobs(int count) {
        var request = new LinkedHashMap<String, Object>();
        request.put("worker_id", workerId);
        request.put("queues", queues);
        request.put("count", count);

        var response = transport.post("/workers/fetch", request);
        var jobsList = response.get("jobs");
        if (jobsList instanceof List<?> list) {
            return list.stream()
                    .filter(o -> o instanceof Map)
                    .map(o -> OJSClient.parseJob((Map<String, Object>) o))
                    .toList();
        }
        return List.of();
    }

    private void processJob(Job job) {
        var thread = Thread.currentThread();
        activeJobs.put(job.id(), thread);
        activeCount.incrementAndGet();

        try {
            var handler = handlers.get(job.type());
            if (handler == null) {
                nackJob(job.id(), "handler_not_found",
                        "No handler registered for job type: " + job.type());
                return;
            }

            var ctx = new JobContext(
                    job, null, null,
                    jobId -> sendJobHeartbeat(jobId)
            );

            // Build middleware chain
            var chainedHandler = buildMiddlewareChain(handler);

            // Execute
            var result = chainedHandler.handle(ctx);

            // Use explicit result or context result
            var finalResult = result != null ? result : ctx.getResult();

            // ACK
            ackJob(job.id(), finalResult);

        } catch (Exception e) {
            nackJob(job.id(), e.getClass().getSimpleName(), e.getMessage());
        } finally {
            activeJobs.remove(job.id());
            activeCount.decrementAndGet();
            semaphore.release();
        }
    }

    private JobHandler buildMiddlewareChain(JobHandler handler) {
        var chain = handler;
        // Wrap in reverse order so first middleware is outermost
        for (int i = middlewareChain.size() - 1; i >= 0; i--) {
            var mw = middlewareChain.get(i).middleware();
            var next = chain;
            chain = ctx -> {
                Object[] result = {null};
                mw.apply(ctx, innerCtx -> {
                    result[0] = next.handle(innerCtx);
                    return result[0];
                });
                return result[0];
            };
        }
        return chain;
    }

    private void ackJob(String jobId, Object result) {
        var request = new LinkedHashMap<String, Object>();
        request.put("job_id", jobId);
        if (result != null) {
            request.put("result", result);
        }
        transport.post("/workers/ack", request);
    }

    private void nackJob(String jobId, String errorType, String message) {
        var request = new LinkedHashMap<String, Object>();
        request.put("job_id", jobId);

        var error = new LinkedHashMap<String, Object>();
        error.put("type", errorType);
        error.put("message", message != null ? message : "Unknown error");
        request.put("error", error);

        try {
            transport.post("/workers/nack", request);
        } catch (Exception ignored) {
            // Best effort - the visibility timeout will handle recovery
        }
    }

    @SuppressWarnings("unchecked")
    private void sendHeartbeat() {
        var request = new LinkedHashMap<String, Object>();
        request.put("worker_id", workerId);
        request.put("state", state.get().value());
        request.put("active_jobs", activeCount.get());
        request.put("active_job_ids", new ArrayList<>(activeJobs.keySet()));
        request.put("queues", queues);
        request.put("concurrency", concurrency);
        if (!labels.isEmpty()) {
            request.put("labels", labels);
        }

        var response = transport.post("/workers/heartbeat", request);

        // Check for server-initiated state change
        var directive = response.get("state");
        if (directive instanceof String s) {
            switch (s) {
                case "quiet" -> {
                    if (state.get() == State.RUNNING) {
                        state.set(State.QUIET);
                    }
                }
                case "terminate" -> stop();
            }
        }
    }

    private void sendJobHeartbeat(String jobId) {
        var request = new LinkedHashMap<String, Object>();
        request.put("worker_id", workerId);
        request.put("job_id", jobId);
        transport.post("/workers/heartbeat", request);
    }

    // --- Builder ---

    public static final class Builder {
        private String url;
        private List<String> queues;
        private int concurrency = 10;
        private Duration gracePeriod;
        private Duration heartbeatInterval;
        private Duration pollInterval;
        private List<String> labels;
        private HttpClient httpClient;
        private String authToken;
        private Map<String, String> headers;
        private Transport transport;

        private Builder() {}

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder queues(List<String> queues) {
            this.queues = queues;
            return this;
        }

        public Builder concurrency(int concurrency) {
            this.concurrency = concurrency;
            return this;
        }

        public Builder gracePeriod(Duration gracePeriod) {
            this.gracePeriod = gracePeriod;
            return this;
        }

        public Builder heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        public Builder labels(List<String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        /** Set a custom transport (for testing or non-HTTP transports). Overrides url/httpClient/authToken/headers. */
        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        public OJSWorker build() {
            if (transport == null) {
                Objects.requireNonNull(url, "url must not be null");
            }
            return new OJSWorker(this);
        }
    }
}
