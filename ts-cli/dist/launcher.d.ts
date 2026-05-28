/**
 * Java backend launcher — matches python-cli/jwcode/launcher.py behavior.
 * Finds Maven, builds (optional), and starts the Java WebServer as a child process.
 */
import { ChildProcess } from 'node:child_process';
export declare function findProjectRoot(): string;
export declare function findMvn(): string;
export declare function jarExists(projectRoot: string): string | null;
export declare function buildBackend(projectRoot: string): void;
export declare function waitForBackend(port: number, timeout?: number): Promise<void>;
export declare function startBackend(projectRoot: string, port: number, wsPort: number): ChildProcess;
export declare function cleanupBackend(proc: ChildProcess | null): void;
