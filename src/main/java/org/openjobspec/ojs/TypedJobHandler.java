package org.openjobspec.ojs;

/**
 * A type-safe job handler that automatically deserializes job arguments into a typed object.
 *
 * <p>Requires Jackson on the classpath. Uses the job's {@code argsMap()} to populate the typed object.
 *
 * <pre>{@code
 * // Define your args type
 * public record EmailArgs(String to, String subject, String body) {}
 *
 * // Register with automatic deserialization
 * worker.register("email.send", TypedJobHandler.of(EmailArgs.class, (args, ctx) -> {
 *     sendEmail(args.to(), args.subject(), args.body());
 *     return Map.of("sent", true);
 * }));
 * }</pre>
 *
 * @param <T> the type to deserialize job arguments into
 */
@FunctionalInterface
public interface TypedJobHandler<T> {

    /**
     * Handle a job with typed arguments.
     *
     * @param args the deserialized job arguments
     * @param ctx  the job context
     * @return the job result (may be null)
     * @throws Exception if the handler fails
     */
    Object handle(T args, JobContext ctx) throws Exception;

    /**
     * Create a {@link JobHandler} that deserializes job arguments into the given type
     * using Jackson's {@code ObjectMapper}, then delegates to the typed handler.
     *
     * @param type    the class to deserialize into
     * @param handler the typed handler
     * @param <T>     the argument type
     * @return a standard JobHandler
     * @throws IllegalStateException if Jackson is not on the classpath
     */
    static <T> JobHandler of(Class<T> type, TypedJobHandler<T> handler) {
        var mapper = JacksonSupport.requireMapper();
        return ctx -> {
            var argsMap = ctx.job().argsMap();
            T typedArgs = mapper.convertValue(argsMap, type);
            return handler.handle(typedArgs, ctx);
        };
    }
}
