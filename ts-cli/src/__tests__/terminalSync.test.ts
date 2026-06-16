import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  isSynchronizedOutputSupported,
  installSyncOutput,
  uninstallSyncOutput,
  _isInstalled,
} from '../terminalSync.js';

const BSU = '\x1b[?2026h';
const ESU = '\x1b[?2026l';

type WriteFn = (chunk: string | Uint8Array, ...args: unknown[]) => boolean;

describe('terminalSync', () => {
  // Snapshot env so we can restore it between tests.
  const originalEnv = { ...process.env };

  function clearTerminalEnv() {
    for (const key of ['TMUX', 'TERM_PROGRAM', 'TERM', 'WT_SESSION']) {
      delete process.env[key];
    }
  }

  beforeEach(() => {
    uninstallSyncOutput();
    clearTerminalEnv();
  });

  afterEach(() => {
    uninstallSyncOutput();
    for (const key of Object.keys(process.env)) delete process.env[key];
    Object.assign(process.env, originalEnv);
  });

  describe('isSynchronizedOutputSupported', () => {
    it('returns false when TMUX is set (breaks atomicity)', () => {
      process.env.TMUX = '/tmp/tmux-1000/default,12345,0';
      process.env.TERM_PROGRAM = 'iTerm.app';
      expect(isSynchronizedOutputSupported()).toBe(false);
    });

    it('returns true for known-supported TERM_PROGRAM values', () => {
      for (const tp of ['iTerm.app', 'WezTerm', 'ghostty', 'vscode', 'alacritty']) {
        process.env.TERM_PROGRAM = tp;
        expect(isSynchronizedOutputSupported()).toBe(true);
      }
    });

    it('returns true for Windows Terminal via WT_SESSION', () => {
      process.env.WT_SESSION = 'abc-123';
      expect(isSynchronizedOutputSupported()).toBe(true);
    });

    it('returns true for kitty via TERM', () => {
      process.env.TERM = 'xterm-kitty';
      expect(isSynchronizedOutputSupported()).toBe(true);
    });

    it('returns true for foot via TERM', () => {
      process.env.TERM = 'foot-extra';
      expect(isSynchronizedOutputSupported()).toBe(true);
    });

    it('returns false on unknown terminals', () => {
      process.env.TERM_PROGRAM = 'unknown-terminal';
      expect(isSynchronizedOutputSupported()).toBe(false);
    });

    it('returns false when no terminal hints are set', () => {
      expect(isSynchronizedOutputSupported()).toBe(false);
    });
  });

  describe('installSyncOutput', () => {
    /**
     * Helper: replace process.stdout.write with a capture function that records
     * all chunks. Returns the previous write function so the caller can restore
     * it in afterEach. Install must be called AFTER setting the capture so the
     * wrapper picks up the capture as its `origWrite`.
     */
    function captureWrites(): { captured: string[]; restore: () => void } {
      const captured: string[] = [];
      const previous = process.stdout.write;
      const cap: WriteFn = (chunk, ..._args) => {
        captured.push(typeof chunk === 'string' ? chunk : Buffer.from(chunk).toString('utf8'));
        return true;
      };
      (process.stdout as unknown as { write: WriteFn }).write = cap;
      return {
        captured,
        restore: () => {
          (process.stdout as unknown as { write: WriteFn }).write = previous as WriteFn;
        },
      };
    }

    it('returns false and does not install on unsupported terminal', () => {
      process.env.TERM_PROGRAM = 'unknown-terminal';
      const capture = captureWrites();
      try {
        expect(installSyncOutput()).toBe(false);
        expect(_isInstalled()).toBe(false);
        // Writes pass through unwrapped
        (process.stdout as unknown as { write: WriteFn }).write('plain');
        expect(capture.captured).toEqual(['plain']);
      } finally {
        capture.restore();
      }
    });

    it('wraps string writes with BSU/ESU on supported terminal', () => {
      process.env.TERM_PROGRAM = 'iTerm.app';
      const capture = captureWrites();
      try {
        expect(installSyncOutput()).toBe(true);
        expect(_isInstalled()).toBe(true);
        (process.stdout as unknown as { write: WriteFn }).write('hello');
        expect(capture.captured).toEqual([BSU + 'hello' + ESU]);
      } finally {
        capture.restore();
      }
    });

    it('is idempotent — second install is a no-op', () => {
      process.env.TERM_PROGRAM = 'iTerm.app';
      const capture = captureWrites();
      try {
        installSyncOutput();
        const afterFirst = process.stdout.write;
        installSyncOutput();
        expect(process.stdout.write).toBe(afterFirst);
      } finally {
        capture.restore();
      }
    });

    it('does not add BSU/ESU around empty string writes', () => {
      process.env.TERM_PROGRAM = 'iTerm.app';
      const capture = captureWrites();
      try {
        installSyncOutput();
        (process.stdout as unknown as { write: WriteFn }).write('');
        expect(capture.captured).toEqual(['']);
        (process.stdout as unknown as { write: WriteFn }).write('x');
        expect(capture.captured).toEqual(['', BSU + 'x' + ESU]);
      } finally {
        capture.restore();
      }
    });

    it('uninstallSyncOutput clears the installed flag', () => {
      process.env.TERM_PROGRAM = 'iTerm.app';
      const capture = captureWrites();
      try {
        installSyncOutput();
        expect(_isInstalled()).toBe(true);
        uninstallSyncOutput();
        expect(_isInstalled()).toBe(false);
      } finally {
        capture.restore();
      }
    });
  });
});
