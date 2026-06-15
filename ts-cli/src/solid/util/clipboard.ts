/**
 * Clipboard — write text to clipboard via OSC 52 (works over SSH) and
 * platform-specific helpers (pbcopy / wl-copy / xclip / powershell).
 * Ported from MiMo-Code (simplified — no clipboardy fallback yet; OSC 52 is
 * supported by virtually every modern terminal, including Windows Terminal,
 * iTerm2, WezTerm, kitty, Ghostty, and VS Code's integrated terminal).
 */
import { platform, release } from "node:os"
import { spawn } from "node:child_process"
import { tmpdir } from "node:os"
import path from "node:path"
import fs from "node:fs/promises"

export interface Content {
  data: string
  mime: string
}

/** OSC 52 escape sequence (terminal handles clipboard locally — works over SSH). */
function writeOsc52(text: string): void {
  if (!process.stdout.isTTY) return
  const base64 = Buffer.from(text).toString("base64")
  const osc52 = `\x1b]52;c;${base64}\x07`
  const passthrough = process.env["TMUX"] || process.env["STY"]
  const sequence = passthrough ? `\x1bPtmux;\x1b${osc52}\x1b\\` : osc52
  process.stdout.write(sequence)
}

export async function read(): Promise<Content | undefined> {
  const os = platform()
  if (os === "darwin") {
    const tmpfile = path.join(tmpdir(), "jwcode-clipboard.png")
    try {
      const proc = spawn(
        "osascript",
        [
          "-e",
          'set imageData to the clipboard as "PNGf"',
          "-e",
          `set fileRef to open for access POSIX file "${tmpfile}" with write permission`,
          "-e",
          "set eof fileRef to 0",
          "-e",
          "write imageData to fileRef",
          "-e",
          "close access fileRef",
        ],
        { stdio: "ignore" },
      )
      await new Promise<void>((resolve) => proc.on("exit", () => resolve()))
      const buffer = await fs.readFile(tmpfile)
      return { data: buffer.toString("base64"), mime: "image/png" }
    } catch {
    } finally {
      await fs.rm(tmpfile, { force: true }).catch(() => {})
    }
  }
  if (os === "win32" || release().includes("WSL")) {
    const script =
      "Add-Type -AssemblyName System.Windows.Forms; $img = [System.Windows.Forms.Clipboard]::GetImage(); if ($img) { $ms = New-Object System.IO.MemoryStream; $img.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png); [System.Convert]::ToBase64String($ms.ToArray()) }"
    const proc = spawn("powershell.exe", ["-NonInteractive", "-NoProfile", "-command", script])
    let text = ""
    proc.stdout.on("data", (chunk) => (text += chunk.toString()))
    await new Promise<void>((resolve) => proc.on("exit", () => resolve()))
    if (text.trim()) {
      const imageBuffer = Buffer.from(text.trim(), "base64")
      if (imageBuffer.length > 0) {
        return { data: imageBuffer.toString("base64"), mime: "image/png" }
      }
    }
  }
  return undefined
}

/** Read text from system clipboard. Windows: Get-Clipboard, macOS: pbpaste, Linux: xclip/wl-paste. */
export async function readText(): Promise<string | undefined> {
  const os = platform()
  if (os === "win32") {
    return new Promise((resolve) => {
      const proc = spawn("powershell.exe", ["-NoProfile", "-Command", "Get-Clipboard"])
      let text = ""
      proc.stdout.on("data", (chunk: Buffer) => (text += chunk.toString()))
      proc.on("exit", () => resolve(text.trim() || undefined))
      proc.on("error", () => resolve(undefined))
    })
  }
  if (os === "darwin") {
    return new Promise((resolve) => {
      const proc = spawn("pbpaste")
      let text = ""
      proc.stdout.on("data", (chunk: Buffer) => (text += chunk.toString()))
      proc.on("exit", () => resolve(text.trim() || undefined))
      proc.on("error", () => resolve(undefined))
    })
  }
  if (os === "linux") {
    return new Promise((resolve) => {
      const bin = process.env.WAYLAND_DISPLAY ? "wl-paste" : "xclip"
      const args = process.env.WAYLAND_DISPLAY ? [] : ["-o", "-selection", "clipboard"]
      const proc = spawn(bin, args)
      let text = ""
      proc.stdout.on("data", (chunk: Buffer) => (text += chunk.toString()))
      proc.on("exit", () => resolve(text.trim() || undefined))
      proc.on("error", () => resolve(undefined))
    })
  }
  return undefined
}

function runSpawn(args: string[], text: string, inputStdin = true): Promise<void> {
  return new Promise((resolve) => {
    const proc = spawn(args[0]!, args.slice(1), {
      stdio: ["pipe", "ignore", "ignore"],
    })
    if (inputStdin && proc.stdin) {
      proc.stdin.write(text)
      proc.stdin.end()
    }
    proc.on("exit", () => resolve())
    proc.on("error", () => resolve())
  })
}

export async function copy(text: string): Promise<void> {
  // OSC 52 is the primary path — it works in every modern terminal over SSH.
  writeOsc52(text)
  const os = platform()
  if (os === "darwin") {
    const escaped = text.replace(/\\/g, "\\\\").replace(/"/g, '\\"')
    await runSpawn(["osascript", "-e", `set the clipboard to "${escaped}"`], text, false)
    return
  }
  if (os === "linux") {
    if (process.env["WAYLAND_DISPLAY"]) {
      await runSpawn(["wl-copy"], text)
      return
    }
    if (await which("xclip")) {
      await runSpawn(["xclip", "-selection", "clipboard"], text)
      return
    }
    if (await which("xsel")) {
      await runSpawn(["xsel", "--clipboard", "--input"], text)
      return
    }
  }
  if (os === "win32") {
    // Pipe via stdin to avoid PowerShell string interpolation of $env:/$/()
    await runSpawn(
      [
        "powershell.exe",
        "-NonInteractive",
        "-NoProfile",
        "-Command",
        "[Console]::InputEncoding = [System.Text.Encoding]::UTF8; Set-Clipboard -Value ([Console]::In.ReadToEnd())",
      ],
      text,
    )
    return
  }
}

function which(bin: string): Promise<boolean> {
  return new Promise((resolve) => {
    const proc = spawn(process.platform === "win32" ? "where" : "which", [bin], { stdio: "ignore" })
    proc.on("exit", (code) => resolve(code === 0))
    proc.on("error", () => resolve(false))
  })
}
