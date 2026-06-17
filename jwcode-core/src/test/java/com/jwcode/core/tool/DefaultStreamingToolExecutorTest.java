package com.jwcode.core.tool;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("DefaultStreamingToolExecutor -- streaming tool executor test")
class DefaultStreamingToolExecutorTest {

    private DefaultStreamingToolExecutor<String> executor;

    @BeforeEach
    void setUp() {
        executor = new DefaultStreamingToolExecutor<>();
    }

    @Test
    @DisplayName("Case 10: Submit unpending future increments pendingCount")
    void submitSingleToolIncrementsPendingCount() {
        CompletableFuture<String> future = new CompletableFuture<>();
        executor.submit(future);
        assertEquals(1, executor.pendingCount());
        future.complete("done");
    }

    @Test
    @DisplayName("Case 11: Completion callbacks fire for each completed tool")
    void callbacksFireInOrder() throws Exception {
        CopyOnWriteArrayList<String> order = new CopyOnWriteArrayList<>();
        executor.onToolComplete(result -> order.add("cb-" + result));

        CompletableFuture<String> futureA = new CompletableFuture<>();
        CompletableFuture<String> futureB = new CompletableFuture<>();
        executor.submit(futureA);
        executor.submit(futureB);

        futureB.complete("B");
        futureA.complete("A");
        executor.whenAllComplete().get(2, TimeUnit.SECONDS);

        assertEquals(2, order.size());
        assertTrue(order.contains("cb-A"));
        assertTrue(order.contains("cb-B"));
    }

    @Test
    @DisplayName("Case 12: whenAllComplete waits for all 3 tools")
    void whenAllCompleteWaitsForAll() throws Exception {
        CompletableFuture<String> f1 = new CompletableFuture<>();
        CompletableFuture<String> f2 = new CompletableFuture<>();
        CompletableFuture<String> f3 = new CompletableFuture<>();
        executor.submit(f1);
        executor.submit(f2);
        executor.submit(f3);

        f2.complete("B");
        f1.complete("A");
        f3.complete("C");

        executor.whenAllComplete().get(2, TimeUnit.SECONDS);
        assertEquals(3, executor.getCompletedResults().size());
    }

    @Test
    @DisplayName("Case 13: getCompletedResults returns 2 results")
    void getCompletedResultsReturnsAllCompleted() throws Exception {
        CompletableFuture<String> f1 = CompletableFuture.completedFuture("result1");
        CompletableFuture<String> f2 = CompletableFuture.completedFuture("result2");
        executor.submit(f1);
        executor.submit(f2);
        executor.whenAllComplete().get(2, TimeUnit.SECONDS);

        List<String> results = executor.getCompletedResults();
        assertEquals(2, results.size());
        assertTrue(results.contains("result1"));
        assertTrue(results.contains("result2"));
    }

    @Test
    @DisplayName("Case 14: discard clears everything")
    void discardClearsEverything() {
        CompletableFuture<String> f1 = new CompletableFuture<>();
        CompletableFuture<String> f2 = new CompletableFuture<>();
        executor.submit(f1);
        executor.submit(f2);
        assertEquals(2, executor.pendingCount());

        executor.discard();
        assertEquals(0, executor.pendingCount());
        assertTrue(executor.getCompletedResults().isEmpty());
    }

    @Test
    @DisplayName("pendingCount decreases as tools complete")
    void pendingCountDecreasesAsToolsComplete() throws Exception {
        CompletableFuture<String> f1 = new CompletableFuture<>();
        CompletableFuture<String> f2 = new CompletableFuture<>();
        executor.submit(f1);
        executor.submit(f2);
        assertEquals(2, executor.pendingCount());

        f1.complete("done");
        Thread.sleep(50);
        assertEquals(1, executor.pendingCount());

        f2.complete("done");
        executor.whenAllComplete().get(2, TimeUnit.SECONDS);
        assertEquals(0, executor.pendingCount());
    }

    @Test
    @DisplayName("Failed tool does not trigger callbacks or add results")
    void failedToolDoesNotAddResult() throws Exception {
        AtomicInteger callbackCount = new AtomicInteger(0);
        executor.onToolComplete(result -> callbackCount.incrementAndGet());

        CompletableFuture<String> f1 = CompletableFuture.failedFuture(new RuntimeException("error"));
        executor.submit(f1);

        // whenAllComplete will propagate the exception
        try {
            executor.whenAllComplete().get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            // expected - the failed future propagates
        }

        assertEquals(0, callbackCount.get());
        assertTrue(executor.getCompletedResults().isEmpty());
    }
}
