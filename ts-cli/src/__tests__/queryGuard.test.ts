import { describe, it, expect, beforeEach } from "vitest";
import { QueryGuard } from "../hooks/useQueryGuard";

describe("QueryGuard state machine", () => {
  let guard: QueryGuard;

  beforeEach(() => {
    guard = new QueryGuard();
  });

  it("reserve transitions from idle to dispatching and returns gen number", () => {
    expect(guard.state).toBe("idle");
    const gen = guard.reserve();
    expect(gen).not.toBeNull();
    expect(guard.state).toBe("dispatching");
    expect(guard.generation).toBe(gen);
  });

  it("reserve returns null when already dispatching or running", () => {
    const firstGen = guard.reserve();
    expect(firstGen).not.toBeNull();
    const secondGen = guard.reserve();
    expect(secondGen).toBeNull();
    guard.markRunning();
    const thirdGen = guard.reserve();
    expect(thirdGen).toBeNull();
  });

  it("enqueue/dequeue follows FIFO order", () => {
    guard.enqueue("q1");
    guard.enqueue("q2");
    guard.enqueue("q3");
    expect(guard.queueLength).toBe(3);
    expect(guard.markComplete()).toBe("q1");
    expect(guard.queueLength).toBe(2);
    expect(guard.markComplete()).toBe("q2");
    expect(guard.queueLength).toBe(1);
    expect(guard.markComplete()).toBe("q3");
    expect(guard.queueLength).toBe(0);
  });

  it("markComplete returns undefined when queue is empty", () => {
    expect(guard.markComplete()).toBeUndefined();
    expect(guard.state).toBe("idle");
  });

  it("isStale detects stale generation numbers", () => {
    const gen1 = guard.reserve()!;
    expect(guard.isStale(gen1)).toBe(false);
    guard.markRunning();
    guard.markComplete();
    // gen1 was from old cycle; after new reserve, gen1 is stale
    const gen2 = guard.reserve()!;
    expect(guard.isStale(gen1)).toBe(true);
    expect(guard.isStale(gen2)).toBe(false);
  });

  it("reset clears queue and returns to idle", () => {
    guard.reserve();
    guard.markRunning();
    guard.enqueue("p1");
    guard.enqueue("p2");
    expect(guard.queueLength).toBe(2);
    expect(guard.state).toBe("running");
    guard.reset();
    expect(guard.state).toBe("idle");
    expect(guard.queueLength).toBe(0);
  });

  it("generation monotonically increases", () => {
    const g1 = guard.generation;
    guard.reserve();
    expect(guard.generation).toBeGreaterThan(g1);
    guard.markRunning();
    guard.markComplete();
    guard.reserve();
    expect(guard.generation).toBeGreaterThan(g1 + 1);
  });
});
