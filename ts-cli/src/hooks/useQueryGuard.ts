/**
 * QueryGuard state machine — concurrency control for user queries.
 *
 * States: idle → dispatching → running → idle
 *   - idle: no query in flight, new submissions are dispatched immediately
 *   - dispatching: preparing to send (queryGuard.reserve phase)
 *   - running: query active on the backend, new submissions are enqueued
 *
 * Each transition increments a generation number so stale async callbacks
 * can be detected and discarded (race-condition / double-render guard).
 *
 * The input queue holds pending inputs that are drained on query completion.
 */

type GuardState = 'idle' | 'dispatching' | 'running';

export class QueryGuard {
  private _state: GuardState = 'idle';
  private _generation = 0;
  private _queue: string[] = [];

  get state(): GuardState { return this._state; }
  get generation(): number { return this._generation; }
  get queueLength(): number { return this._queue.length; }

  /**
   * Reserve the guard before dispatching a query.
   * @returns the generation number if the reservation succeeded, or null if
   *          already dispatching/running (caller should enqueue instead).
   */
  reserve(): number | null {
    if (this._state !== 'idle') return null;
    this._state = 'dispatching';
    this._generation++;
    return this._generation;
  }

  /** Mark that the query is actively streaming (called on first "start" event). */
  markRunning(): void {
    this._state = 'running';
  }

  /**
   * Mark that the query has completed. Drains the queue if any pending inputs.
   * @returns the next queued input to execute, or undefined if the queue is empty.
   */
  markComplete(): string | undefined {
    this._state = 'idle';
    return this._queue.shift();
  }

  /** Check whether a callback from a previous generation should be discarded. */
  isStale(gen: number): boolean {
    return gen !== this._generation;
  }

  /** Enqueue an input for later execution. */
  enqueue(input: string): void {
    this._queue.push(input);
  }

  /** Reset the guard to idle (e.g. on connection loss). */
  reset(): void {
    this._state = 'idle';
    this._generation++;
    this._queue = [];
  }
}

/**
 * Singleton guard shared across the app.
 * App.tsx creates it, handlers use it.
 */
export const queryGuard = new QueryGuard();
