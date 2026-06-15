/**
 * initProject — project scanner and agent.md generator for the /init command.
 *
 * scanProject() reads the workspace directory, detects project type/metadata.
 * generateAgentMd() produces an agent.md file from that metadata.
 * writeAgentMd() persists it to disk with backup support.
 */
import { readdir, stat, readFile, writeFile, rename } from "node:fs/promises"
import { join } from "node:path"

export interface ProjectMetadata {
  name: string
  description: string
  type: "node" | "java-maven" | "python" | "go" | "rust" | "other"
  languages: string[]
  packageManager: string
  buildCommand: string
  testCommand: string
  devCommand: string
  startCommand: string
  keyFiles: string[]
  detectedAt: string
}

export interface InitProgress {
  stage: "scanning" | "analyzing" | "generating" | "complete" | "error"
  message: string
  percent: number
  error?: string
}

async function fileExists(p: string): Promise<boolean> {
  try {
    await stat(p)
    return true
  } catch {
    return false
  }
}

async function tryReadJson(p: string): Promise<Record<string, unknown> | null> {
  try {
    const raw = await readFile(p, "utf-8")
    return JSON.parse(raw)
  } catch {
    return null
  }
}

/**
 * Scan a project directory and detect project type, metadata, and key files.
 * Limits scanning to the first 100 entries to avoid hangs on large directories.
 */
export async function scanProject(
  workspaceDir: string,
  onProgress?: (p: InitProgress) => void,
): Promise<ProjectMetadata> {
  onProgress?.({ stage: "scanning", message: "Scanning project directory...", percent: 10 })

  let entries: string[]
  try {
    entries = await readdir(workspaceDir)
  } catch {
    return defaultMetadata(workspaceDir)
  }

  onProgress?.({ stage: "scanning", message: `Found ${entries.length} entries`, percent: 30 })

  // Limit to first 100 entries
  const scanEntries = entries.slice(0, 100)
  const keyFiles: string[] = []
  const hasFile = (name: string) => {
    if (scanEntries.includes(name)) {
      keyFiles.push(name)
      return true
    }
    return false
  }

  onProgress?.({ stage: "analyzing", message: "Analyzing project structure...", percent: 50 })

  // Detect project type
  let type: ProjectMetadata["type"] = "other"
  let languages: string[] = []
  let packageManager = ""
  let buildCommand = ""
  let testCommand = ""
  let devCommand = ""
  let startCommand = ""
  let name = workspaceDir.split(/[/\\]/).filter(Boolean).pop() || "unknown"
  let description = ""

  if (hasFile("package.json")) {
    type = "node"
    languages = ["JavaScript", "TypeScript"]
    packageManager = "npm"
    buildCommand = "npm run build"
    testCommand = "npm test"
    devCommand = "npm run dev"
    startCommand = "npm start"

    const pkg = await tryReadJson(join(workspaceDir, "package.json"))
    if (pkg) {
      if (typeof pkg.name === "string") name = pkg.name
      if (typeof pkg.description === "string") description = pkg.description
      const scripts = pkg.scripts as Record<string, string> | undefined
      if (scripts) {
        if (scripts.build) buildCommand = `npm run build`
        if (scripts.test) testCommand = `npm test`
        if (scripts.dev) devCommand = `npm run dev`
        if (scripts.start) startCommand = `npm start`
      }
    }

    if (hasFile("bun.lock") || hasFile("bun.lockb")) {
      packageManager = "bun"
      buildCommand = "bun run build"
      testCommand = "bun test"
      devCommand = "bun run dev"
      startCommand = "bun start"
    } else if (hasFile("yarn.lock")) {
      packageManager = "yarn"
      buildCommand = "yarn build"
      testCommand = "yarn test"
      devCommand = "yarn dev"
      startCommand = "yarn start"
    } else if (hasFile("pnpm-lock.yaml")) {
      packageManager = "pnpm"
      buildCommand = "pnpm build"
      testCommand = "pnpm test"
      devCommand = "pnpm dev"
      startCommand = "pnpm start"
    }
  } else if (hasFile("pom.xml")) {
    type = "java-maven"
    languages = ["Java"]
    packageManager = "maven"
    buildCommand = "mvn package"
    testCommand = "mvn test"
    devCommand = "mvn compile"
    startCommand = "mvn exec:java"

    // Try to extract name/description from pom.xml
    try {
      const pom = await readFile(join(workspaceDir, "pom.xml"), "utf-8")
      const artMatch = pom.match(/<artifactId>([^<]+)<\/artifactId>/)
      if (artMatch) name = artMatch[1]
      const descMatch = pom.match(/<description>([^<]+)<\/description>/)
      if (descMatch) description = descMatch[1]
    } catch { /* ignore */ }
  } else if (hasFile("go.mod")) {
    type = "go"
    languages = ["Go"]
    packageManager = "go"
    buildCommand = "go build"
    testCommand = "go test ./..."
    devCommand = "go run ."
    startCommand = "go run ."

    try {
      const mod = await readFile(join(workspaceDir, "go.mod"), "utf-8")
      const nameMatch = mod.match(/^module\s+(\S+)/m)
      if (nameMatch) name = nameMatch[1]
    } catch { /* ignore */ }
  } else if (hasFile("Cargo.toml")) {
    type = "rust"
    languages = ["Rust"]
    packageManager = "cargo"
    buildCommand = "cargo build"
    testCommand = "cargo test"
    devCommand = "cargo build"
    startCommand = "cargo run"
  } else if (hasFile("setup.py") || hasFile("pyproject.toml") || hasFile("requirements.txt")) {
    type = "python"
    languages = ["Python"]
    packageManager = "pip"
    buildCommand = "pip install -e ."
    testCommand = "pytest"
    devCommand = ""
    startCommand = "python main.py"
  }

  onProgress?.({ stage: "analyzing", message: "Detected: " + type, percent: 70 })

  // Collect additional key config files
  for (const name of ["tsconfig.json", ".gitignore", "Dockerfile", "docker-compose.yml", "Makefile", ".env.example", "README.md", "CLAUDE.md"]) {
    if (hasFile(name)) continue
    try {
      if (await fileExists(join(workspaceDir, name))) keyFiles.push(name)
    } catch { /* ignore */ }
  }

  return {
    name,
    description,
    type,
    languages,
    packageManager,
    buildCommand,
    testCommand,
    devCommand,
    startCommand,
    keyFiles,
    detectedAt: new Date().toISOString(),
  }
}

function defaultMetadata(workspaceDir: string): ProjectMetadata {
  const name = workspaceDir.split(/[/\\]/).filter(Boolean).pop() || "unknown"
  return {
    name,
    description: "",
    type: "other",
    languages: [],
    packageManager: "",
    buildCommand: "",
    testCommand: "",
    devCommand: "",
    startCommand: "",
    keyFiles: [],
    detectedAt: new Date().toISOString(),
  }
}

/**
 * Generate an agent.md file from project metadata.
 */
export function generateAgentMd(meta: ProjectMetadata): string {
  const lines: string[] = []
  lines.push(`# Project: ${meta.name}`)
  lines.push("")
  if (meta.description) {
    lines.push(`## Description`)
    lines.push("")
    lines.push(meta.description)
    lines.push("")
  }

  lines.push(`## Build/Run Commands`)
  lines.push("")
  lines.push("| Command | Action |")
  lines.push("|---|---|")
  if (meta.buildCommand) lines.push(`| \`${meta.buildCommand}\` | Build |`)
  if (meta.testCommand) lines.push(`| \`${meta.testCommand}\` | Test |`)
  if (meta.devCommand) lines.push(`| \`${meta.devCommand}\` | Dev |`)
  if (meta.startCommand) lines.push(`| \`${meta.startCommand}\` | Start |`)
  lines.push("")

  lines.push(`## Architecture Notes`)
  lines.push("")
  lines.push(`- Type: ${meta.type}`)
  if (meta.languages.length > 0) lines.push(`- Language: ${meta.languages.join(", ")}`)
  if (meta.packageManager) lines.push(`- Package Manager: ${meta.packageManager}`)
  if (meta.keyFiles.length > 0) lines.push(`- Key Files: ${meta.keyFiles.join(", ")}`)
  lines.push("")

  lines.push(`## Configuration Preferences`)
  lines.push("")
  lines.push(`<!-- Generated by jwcode init on ${meta.detectedAt} -->`)
  lines.push("<!-- Edit this file to customize agent behavior for this project -->")
  lines.push("")
  lines.push("- Agent should prioritize understanding the project structure")
  lines.push("- Review key config files before making changes")
  lines.push("- Follow existing code style and conventions")

  return lines.join("\n")
}

/**
 * Write agent.md to the workspace directory.
 * Backs up existing agent.md to agent.md.bak if it exists.
 */
export async function writeAgentMd(workspaceDir: string, content: string): Promise<string> {
  const filePath = join(workspaceDir, "agent.md")
  const bakPath = join(workspaceDir, "agent.md.bak")

  // Backup existing file
  try {
    if (await fileExists(filePath)) {
      await rename(filePath, bakPath)
    }
  } catch { /* best-effort backup */ }

  await writeFile(filePath, content, "utf-8")
  return filePath
}
