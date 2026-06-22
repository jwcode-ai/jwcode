import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ensureBackendJar } from '../launcher.js';

const VALID_JAR_BYTES = 210 * 1024;

function responseFromBytes(bytes: Uint8Array, contentLength = String(bytes.length)): Response {
  return new Response(bytes, {
    status: 200,
    headers: { 'content-length': contentLength },
  });
}

describe('ensureBackendJar', () => {
  let installDir: string;
  let logSpy: ReturnType<typeof vi.spyOn>;
  let errorSpy: ReturnType<typeof vi.spyOn>;
  let stdoutSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    installDir = mkdtempSync(join(tmpdir(), 'jwcode-launcher-'));
    logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    stdoutSpy = vi.spyOn(process.stdout, 'write').mockImplementation(() => true);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    rmSync(installDir, { recursive: true, force: true });
  });

  it('falls through from an empty ghproxy response to direct GitHub for the same version', async () => {
    const directJar = Buffer.alloc(VALID_JAR_BYTES, 7);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(responseFromBytes(new Uint8Array(), '0'))
      .mockResolvedValueOnce(responseFromBytes(directJar));
    vi.stubGlobal('fetch', fetchMock);

    const jarPath = await ensureBackendJar(installDir);

    expect(jarPath).toBe(join(installDir, 'backend', 'jwcode-web.jar'));
    expect(readFileSync(jarPath!)).toEqual(directJar);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toContain('ghproxy.com');
    expect(fetchMock.mock.calls[1][0]).toContain('github.com/jwcode-ai/jwcode/releases/download/v3.0.18');
    expect(logSpy).toHaveBeenCalledWith(expect.stringContaining('Download too small'));
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it('rejects tiny successful responses without writing a jar', async () => {
    const fetchMock = vi.fn().mockResolvedValue(responseFromBytes(Buffer.alloc(500), '500'));
    vi.stubGlobal('fetch', fetchMock);

    const jarPath = await ensureBackendJar(installDir);

    expect(jarPath).toBeNull();
    expect(existsSync(join(installDir, 'backend', 'jwcode-web.jar'))).toBe(false);
    expect(logSpy).toHaveBeenCalledWith(expect.stringContaining('500 bytes'));
    expect(errorSpy).toHaveBeenCalledWith('[launcher] All download sources failed.');
  });

  it('writes and returns a valid download above the minimum size', async () => {
    const validJar = Buffer.alloc(VALID_JAR_BYTES, 3);
    const fetchMock = vi.fn().mockResolvedValue(responseFromBytes(validJar));
    vi.stubGlobal('fetch', fetchMock);

    const jarPath = await ensureBackendJar(installDir);

    expect(jarPath).toBe(join(installDir, 'backend', 'jwcode-web.jar'));
    expect(readFileSync(jarPath!)).toEqual(validJar);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(stdoutSpy).toHaveBeenCalled();
  });
});
