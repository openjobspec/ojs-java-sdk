package org.openjobspec.ojs;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * Thread-safe event emitter for OJS events. Supports typed subscriptions
 * by event type and wildcard subscriptions that receive all events.
 *
 * <p>Usage:
 * <pre>{@code
 * var emitter = new EventEmitter();
 *
 * // Subscribe to a specific event type
 * Runnable unsub = emitter.on(Event.JOB_COMPLETED, event -> {
 *     System.out.println("Completed: " + event.jobId());
 * });
 *
 * // Subscribe to all events
 * emitter.onAny(event -> {
 *     System.out.println("Event: " + event.type());
 * });
 *
 * // Emit an event
 * emitter.emit(Event.of(Event.JOB_COMPLETED, "job-123", Map.of()));
 *
 * // Unsubscribe
 * unsub.run();
 * }</pre>
 */
public class EventEmitter {

    private final Map<String, Set<EventHandler>> listeners = new ConcurrentHashMap<>();
    private final Set<EventHandler> wildcardListeners = new CopyOnWriteArraySet<>();

    /**
     * Subscribe to events of a specific type.
     *
     * @param eventType the event type to listen for (e.g., {@link Event#JOB_COMPLETED})
     * @param handler   the handler to invoke when a matching event is emitted
     * @return a {@link Runnable} that removes this subscription when called
     */
    public Runnable on(String eventType, EventHandler handler) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArraySet<>()).add(handler);
        return () -> {
            Set<EventHandler> set = listeners.get(eventType);
            if (set != null) set.remove(handler);
        };
    }

    /**
     * Subscribe to all events regardless of type (wildcard listener).
     *
     * @param handler the handler to invoke for every emitted event
     * @return a {@link Runnable} that removes this subscription when called
     */
    public Runnable onAny(EventHandler handler) {
        wildcardListeners.add(handler);
        return () -> wildcardListeners.remove(handler);
    }

    /**
     * Emit an event, notifying all matching and wildcard listeners.
     *
     * @param event the event to emit
     */
    public void emit(Event event) {
        // Notify type-specific listeners.
        Set<EventHandler> typeListeners = listeners.get(event.type());
        if (typeListeners != null) {
            for (EventHandler handler : typeListeners) {
                handler.handle(event);
            }
        }

        // Notify wildcard listeners.
        for (EventHandler handler : wildcardListeners) {
            handler.handle(event);
        }
    }

    /**
     * Remove all listeners for a specific event type.
     *
     * @param eventType the event type to clear
     */
    public void removeAll(String eventType) {
        listeners.remove(eventType);
    }

    /**
     * Remove all listeners (type-specific and wildcard).
     */
    public void clear() {
        listeners.clear();
        wildcardListeners.clear();
    }

    /**
     * Returns the number of listeners for a specific event type.
     */
    public int listenerCount(String eventType) {
        Set<EventHandler> set = listeners.get(eventType);
        return (set != null ? set.size() : 0) + wildcardListeners.size();
    }

    /**
     * Returns the total number of subscriptions (type-specific + wildcard).
     */
    public int totalListeners() {
        int count = wildcardListeners.size();
        for (Set<EventHandler> set : listeners.values()) {
            count += set.size();
        }
        return count;
    }
}
