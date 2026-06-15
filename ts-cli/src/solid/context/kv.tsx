/**
 * KV store — persists key/value pairs to disk under ~/.jwcode/state/kv.json.
 * Ported from MiMo-Code (simplified — we drop the Flock IPC lock since jwcode
 * runs as a single CLI process; same-process queue is enough).
 */
import { createSignal } from "solid-js";
import { createStore, unwrap } from "solid-js/store";
import { createSimpleContext } from "./helper";
import { Global } from "../util/global";
import { Filesystem } from "../util/filesystem";
import { rename, rm } from "node:fs/promises";
import path from "node:path";
import type { Setter } from "solid-js";

export const { use: useKV, provider: KVProvider } = createSimpleContext({
  name: "KV",
  init: () => {
    const [ready, setReady] = createSignal(false);
    const [store, setStore] = createStore<Record<string, unknown>>({});
    const filePath = path.join(Global.Path.state, "kv.json");
    // Queue same-process writes so rapid updates persist in order.
    let write: Promise<unknown> = Promise.resolve();

    // Write to a temp file first so kv.json is only replaced once the JSON is
    // complete, avoiding partial writes if shutdown interrupts persistence.
    function writeSnapshot(snapshot: Record<string, unknown>) {
      const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
      return Filesystem.writeJson(tempPath, snapshot)
        .then(() => rename(tempPath, filePath))
        .catch(async (error) => {
          await rm(tempPath, { force: true }).catch(() => undefined);
          throw error;
        });
    }

    // Initial load.
    Filesystem.readJson<Record<string, unknown>>(filePath)
      .then((x) => {
        for (const k of Object.keys(x)) setStore(k, x[k]);
      })
      .catch(() => {
        // File doesn't exist yet — start empty.
      })
      .finally(() => {
        setReady(true);
      });

    const result = {
      get ready() {
        return ready();
      },
      get store() {
        return store;
      },
      signal<T>(name: string, defaultValue: T) {
        if (store[name] === undefined) setStore(name, defaultValue);
        return [
          function () {
            return result.get(name) as T;
          },
          function setter(next: Setter<T>) {
            result.set(name, next as unknown);
          },
        ] as const;
      },
      get(key: string, defaultValue?: unknown) {
        return store[key] ?? defaultValue;
      },
      set(key: string, value: unknown) {
        setStore(key, value);
        const snapshot = JSON.parse(JSON.stringify(unwrap(store))) as Record<string, unknown>;
        write = write
          .then(() => writeSnapshot(snapshot))
          .catch((error) => {
            console.error("Failed to write KV state", { filePath, error });
          });
      },
    };
    return result;
  },
});
