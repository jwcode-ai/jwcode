/**
 * Filesystem helpers — read/write JSON, ensure dir exists, write atomically.
 */
import { promises as fs } from "node:fs";
import path from "node:path";

export const Filesystem = {
  async exists(p: string): Promise<boolean> {
    try {
      await fs.access(p);
      return true;
    } catch {
      return false;
    }
  },

  async readJson<T>(p: string): Promise<T> {
    const text = await fs.readFile(p, "utf-8");
    return JSON.parse(text) as T;
  },

  async writeJson(p: string, data: unknown): Promise<void> {
    await fs.mkdir(path.dirname(p), { recursive: true });
    await fs.writeFile(p, JSON.stringify(data, null, 2), "utf-8");
  },
};
