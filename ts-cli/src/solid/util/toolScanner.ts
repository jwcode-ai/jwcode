/**
 * toolScanner — scans system PATH for available CLI tools.
 * Runs once on startup, caches results in module-level variable.
 */
import { readdirSync, statSync } from "node:fs"
import path from "node:path"

// Extensions considered executable on Windows
const WIN_EXEC_EXT = new Set([".exe", ".bat", ".cmd", ".ps1", ".com"])
// Max directories to scan from PATH
const MAX_SCAN_DIRS = 50
// Min chars to consider a tool name valid
const MIN_NAME_LENGTH = 1
const MAX_NAME_LENGTH = 64

let cachedTools: string[] | null = null

/** Strip executable extension on Windows */
function normalizeToolName(name: string): string {
  if (process.platform === "win32") {
    const ext = path.extname(name).toLowerCase()
    if (WIN_EXEC_EXT.has(ext)) return name.slice(0, -ext.length)
  }
  return name
}

/** Check if a filename looks like an executable tool */
function isExecutable(name: string, filePath: string): boolean {
  if (name.length < MIN_NAME_LENGTH || name.length > MAX_NAME_LENGTH) return false
  // Skip dotfiles and hidden files
  if (name.startsWith(".")) return false
  if (name.startsWith("_")) return false
  // Ensure the normalized name is non-empty
  const normalized = normalizeToolName(name)
  if (!normalized || normalized.length < MIN_NAME_LENGTH) return false

  if (process.platform === "win32") {
    const ext = path.extname(name).toLowerCase()
    if (!WIN_EXEC_EXT.has(ext)) return false
    return true
  }

  // Unix: check executable bit
  try {
    const stat = statSync(filePath)
    // eslint-disable-next-line no-bitwise
    return stat.isFile() && (stat.mode & 0o111) !== 0
  } catch {
    return false
  }
}

/** Common system paths to skip on each platform */
const SKIP_DIRS = new Set<string>([
  "/dev", "/proc", "/sys", "/etc",
])

/** Scan PATH and return sorted list of available tool names */
export function scanSystemTools(): string[] {
  const pathEnv = process.env.PATH || ""
  const separator = process.platform === "win32" ? ";" : ":"
  const dirs = pathEnv.split(separator).filter(Boolean)

  const toolSet = new Set<string>()
  let scanned = 0

  for (const dir of dirs) {
    if (scanned >= MAX_SCAN_DIRS) break
    try {
      const resolved = path.resolve(dir)
      if (SKIP_DIRS.has(resolved)) continue
      const entries = readdirSync(resolved)
      for (const entry of entries) {
        if (toolSet.size >= 5000) break // safety limit
        const fullPath = path.join(resolved, entry)
        if (isExecutable(entry, fullPath)) {
          toolSet.add(normalizeToolName(entry))
        }
      }
      scanned++
    } catch {
      // Permission denied or directory doesn't exist — skip silently
    }
  }

  const sorted = [...toolSet].sort((a, b) => a.localeCompare(b))
  cachedTools = sorted
  return sorted
}

/** Returns cached tools without re-scanning */
export function getCachedTools(): string[] {
  if (cachedTools) return cachedTools
  cachedTools = scanSystemTools()
  return cachedTools
}
