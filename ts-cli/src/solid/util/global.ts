/**
 * Global path resolution — jwcode config/state/cache dirs.
 * Mirrors the MiMo-Code `Global.Path` interface.
 */
import os from "node:os";
import path from "node:path";
import fs from "node:fs";

function home(): string {
  return process.env.HOME || process.env.USERPROFILE || os.homedir();
}

function jwcodeHome(): string {
  const p = path.join(home(), ".jwcode");
  if (!fs.existsSync(p)) fs.mkdirSync(p, { recursive: true });
  return p;
}

export const Global = {
  Path: {
    home: jwcodeHome(),
    get config() {
      return path.join(this.home, "config");
    },
    get state() {
      return path.join(this.home, "state");
    },
    get cache() {
      return path.join(this.home, "cache");
    },
    get data() {
      return path.join(this.home, "data");
    },
    get log() {
      return path.join(this.home, "log");
    },
  },
};
