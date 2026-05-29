import { describe, it, expect, beforeEach } from 'vitest';
import { useTerminalStore } from '../terminalStore';

describe('terminalStore', () => {
  beforeEach(() => {
    useTerminalStore.setState({
      isOpen: false,
      status: 'idle',
      ttydPort: null,
      ttydWsUrl: null,
      errorMessage: null,
    });
  });

  it('initial state is idle', () => {
    const state = useTerminalStore.getState();
    expect(state.status).toBe('idle');
    expect(state.isOpen).toBe(false);
    expect(state.ttydPort).toBeNull();
  });

  it('openTerminal sets isOpen to true', () => {
    useTerminalStore.getState().openTerminal();
    expect(useTerminalStore.getState().isOpen).toBe(true);
  });

  it('closeTerminal sets isOpen to false', () => {
    useTerminalStore.getState().openTerminal();
    useTerminalStore.getState().closeTerminal();
    expect(useTerminalStore.getState().isOpen).toBe(false);
  });

  it('toggleTerminal flips isOpen', () => {
    const store = useTerminalStore.getState();
    store.toggleTerminal();
    expect(useTerminalStore.getState().isOpen).toBe(true);
    useTerminalStore.getState().toggleTerminal();
    expect(useTerminalStore.getState().isOpen).toBe(false);
  });

  it('setStarting transitions to starting state', () => {
    useTerminalStore.getState().setStarting();
    const s = useTerminalStore.getState();
    expect(s.status).toBe('starting');
    expect(s.errorMessage).toBeNull(); // clears errors
  });

  it('setRunning stores port and wsUrl', () => {
    useTerminalStore.getState().setRunning(8090, 'ws://127.0.0.1:8090/ws');
    const s = useTerminalStore.getState();
    expect(s.status).toBe('running');
    expect(s.ttydPort).toBe(8090);
    expect(s.ttydWsUrl).toBe('ws://127.0.0.1:8090/ws');
  });

  it('setError stores error message and status', () => {
    useTerminalStore.getState().setError('ttyd not found');
    const s = useTerminalStore.getState();
    expect(s.status).toBe('error');
    expect(s.errorMessage).toBe('ttyd not found');
  });

  it('setIdle resets everything', () => {
    useTerminalStore.getState().setRunning(8090, 'ws://x');
    useTerminalStore.getState().setIdle();
    const s = useTerminalStore.getState();
    expect(s.status).toBe('idle');
    expect(s.ttydPort).toBeNull();
    expect(s.ttydWsUrl).toBeNull();
  });

  it('setStarting clears previous error', () => {
    useTerminalStore.getState().setError('prev error');
    useTerminalStore.getState().setStarting();
    expect(useTerminalStore.getState().errorMessage).toBeNull();
  });
});
