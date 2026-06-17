import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act } from "@testing-library/react";

describe("useGlobalTick", () => {
  beforeEach(async () => {
    vi.resetModules();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("tick starts at 0 and increments every second", async () => {
    const { useGlobalTick } = await import("../useGlobalTick");
    function TickDisplay() {
      const tick = useGlobalTick();
      return <div data-testid="tick-value">{tick}</div>;
    }
    render(<TickDisplay />);
    expect(screen.getByTestId("tick-value").textContent).toBe("0");
    act(() => { vi.advanceTimersByTime(1000); });
    expect(screen.getByTestId("tick-value").textContent).toBe("1");
    act(() => { vi.advanceTimersByTime(2000); });
    expect(screen.getByTestId("tick-value").textContent).toBe("3");
  });

  it("two components share the same tick value", async () => {
    const { useGlobalTick } = await import("../useGlobalTick");
    function TickDisplay() {
      const tick = useGlobalTick();
      return <div data-testid="tick-value">{tick}</div>;
    }
    function TwoTicks() {
      return (<div><TickDisplay /><TickDisplay /></div>);
    }
    render(<TwoTicks />);
    const elements = screen.getAllByTestId("tick-value");
    expect(elements).toHaveLength(2);
    expect(elements[0].textContent).toBe("0");
    expect(elements[1].textContent).toBe("0");
    act(() => { vi.advanceTimersByTime(2000); });
    expect(elements[0].textContent).toBe("2");
    expect(elements[1].textContent).toBe("2");
  });

  it("clears interval when last component unmounts", async () => {
    const { useGlobalTick } = await import("../useGlobalTick");
    function TickDisplay() {
      const tick = useGlobalTick();
      return <div data-testid="tick-value">{tick}</div>;
    }
    const spy = vi.spyOn(global, "clearInterval");
    const { unmount } = render(<TickDisplay />);
    act(() => { vi.advanceTimersByTime(1000); });
    expect(screen.getByTestId("tick-value").textContent).toBe("1");
    unmount();
    expect(spy).toHaveBeenCalled();
    spy.mockRestore();
  });
});
