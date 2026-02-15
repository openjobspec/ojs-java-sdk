package org.openjobspec.ojs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjobspec.ojs.transport.Transport;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OJSClient async operations")
class OJSClientAsyncTest {

    @Mock
    Transport transport;

    OJSClient client;

    @BeforeEach
    void setUp() {
        client = OJSClient.withTransport(transport);
    }

    @Test
    void enqueueAsyncReturnsJob() throws Exception {
        when(transport.post(eq("/jobs"), any())).thenReturn(
                Map.of("job", Map.of("id", "j1", "type", "test", "state", "available")));

        var future = client.enqueueAsync("test", Map.of("key", "value"));
        var job = future.get();

        assertEquals("j1", job.id());
        assertEquals("test", job.type());
    }

    @Test
    void getJobAsyncReturnsJob() throws Exception {
        when(transport.get("/jobs/j1")).thenReturn(
                Map.of("job", Map.of("id", "j1", "type", "email.send", "state", "completed")));

        var job = client.getJobAsync("j1").get();

        assertEquals("j1", job.id());
        assertEquals("completed", job.state());
    }

    @Test
    void cancelJobAsyncReturnsJob() throws Exception {
        when(transport.delete("/jobs/j1")).thenReturn(
                Map.of("job", Map.of("id", "j1", "type", "test", "state", "cancelled")));

        var job = client.cancelJobAsync("j1").get();

        assertEquals("cancelled", job.state());
    }

    @Test
    void healthAsyncReturnsStatus() throws Exception {
        when(transport.get("/health")).thenReturn(Map.of("status", "ok"));

        var result = client.healthAsync().get();

        assertEquals("ok", result.get("status"));
    }

    @Test
    void enqueueBatchAsyncReturnsJobs() throws Exception {
        when(transport.post(eq("/jobs/batch"), any())).thenReturn(
                Map.of("jobs", List.of(
                        Map.of("id", "j1", "type", "a"),
                        Map.of("id", "j2", "type", "b"))));

        var jobs = client.enqueueBatchAsync(List.of(Map.of("type", "a"), Map.of("type", "b"))).get();

        assertEquals(2, jobs.size());
    }

    @Test
    void asyncPropagatesExceptions() {
        when(transport.get("/jobs/bad")).thenThrow(
                new OJSError.OJSException(new OJSError.ApiError(
                        "not_found", "Job not found", false, Map.of(), null, 404)));

        var future = client.getJobAsync("bad");

        var ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(OJSError.OJSException.class, ex.getCause());
    }
}
