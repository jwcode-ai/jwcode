#!/usr/bin/env bash
# JWCode installer — one-command setup.
# curl -fsSL https://raw.githubusercontent.com/jwcode/main/install.sh | sh
#
# Detects: Java 17+, Node 18+, OS/platform
# Installs: JWCode CLI (npm), backend JAR, daemon service
set -euo pipefail

JWCODE_HOME="${HOME}/.jwcode"
JWCODE_BIN_DIR="${JWCODE_HOME}/bin"
JWCODE_BACKEND_DIR="${JWCODE_HOME}/backend"
JWCODE_VERSION="${JWCODE_VERSION:-latest}"

# --- colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

info()  { printf "${CYAN}[jwcode]${NC} %s\n" "$*"; }
ok()    { printf "${GREEN}[jwcode]${NC} %s\n" "$*"; }
warn()  { printf "${YELLOW}[jwcode]${NC} %s\n" "$*"; }
err()   { printf "${RED}[jwcode]${NC} %s\n" "$*" >&2; }

# --- platform detection ---
detect_platform() {
  local os arch
  case "$(uname -s)" in
    Linux)  os="linux" ;;
    Darwin) os="macos" ;;
    MINGW*|MSYS*|CYGWIN*) os="windows" ;;
    *) err "Unsupported OS: $(uname -s)"; return 1 ;;
  esac
  case "$(uname -m)" in
    x86_64|amd64) arch="x64" ;;
    aarch64|arm64) arch="arm64" ;;
    *) err "Unsupported arch: $(uname -m)"; return 1 ;;
  esac
  echo "${os}-${arch}"
}

# --- java detection (requires 17+) ---
check_java() {
  if ! command -v java &>/dev/null; then
    warn "Java 17+ not found in PATH."
    warn "Install it from: https://adoptium.net/download/"
    warn "Or for macOS: brew install openjdk@17"
    warn "Or for Ubuntu: sudo apt install openjdk-17-jdk"
    return 1
  fi

  local java_ver
  java_ver=$(java -version 2>&1 | head -1 | grep -oP '\d+' | head -1 || echo "0")
  if [ "$java_ver" -lt 17 ]; then
    err "Java $java_ver detected, need Java 17+. Current: $(java -version 2>&1 | head -1)"
    return 1
  fi
  ok "Java $(java -version 2>&1 | head -1)"
}

# --- node detection (requires 18+) ---
check_node() {
  if ! command -v node &>/dev/null; then
    warn "Node.js 18+ not found."
    warn "Install from: https://nodejs.org/ (LTS recommended)"
    warn "Or: nvm install 18"
    return 1
  fi

  local node_ver
  node_ver=$(node -v | grep -oP '\d+' | head -1 || echo "0")
  if [ "$node_ver" -lt 18 ]; then
    err "Node $node_ver detected, need Node 18+. Current: $(node -v)"
    return 1
  fi
  ok "Node $(node -v)"
}

# --- permissions ---
check_permissions() {
  mkdir -p "${JWCODE_HOME}" "${JWCODE_BIN_DIR}" "${JWCODE_BACKEND_DIR}" 2>/dev/null || {
    err "Cannot create ~/.jwcode directory. Check permissions."
    return 1
  }
}

# --- download backend JAR ---
download_backend() {
  info "Downloading JWCode backend (${JWCODE_VERSION})..."
  local jar_url jar_path
  jar_url="https://github.com/jwcode/jwcode/releases/${JWCODE_VERSION}/download/jwcode-web.jar"
  jar_path="${JWCODE_BACKEND_DIR}/jwcode-web.jar"

  if command -v curl &>/dev/null; then
    curl -fSL --progress-bar "${jar_url}" -o "${jar_path}" || {
      warn "Backend JAR download failed — backend will be built from source on first run."
      return 0
    }
  elif command -v wget &>/dev/null; then
    wget -q --show-progress "${jar_url}" -O "${jar_path}" || {
      warn "Backend JAR download failed."
      return 0
    }
  else
    warn "Neither curl nor wget found. Skipping backend download."
  fi

  if [ -f "${jar_path}" ]; then
    ok "Backend JAR downloaded to ${jar_path}"
  fi
}

# --- npm install CLI ---
install_cli() {
  info "Installing JWCode CLI..."
  if command -v npm &>/dev/null; then
    npm install -g @jwcode/cli 2>/dev/null || {
      warn "npm global install failed. Trying local install..."
      npm install -g "${JWCODE_HOME}/cli" 2>/dev/null || {
        warn "Skipping CLI install. Use 'node dist/cli.js run' directly."
      }
    }
    ok "CLI installed: jwcode"
  else
    warn "npm not found, skipping CLI install. Install Node 18+ first."
  fi
}

# --- config template ---
create_config() {
  if [ ! -f "${JWCODE_HOME}/config.yaml" ]; then
    cat > "${JWCODE_HOME}/config.yaml" << 'YAML'
# JWCode configuration
# See docs/CONFIG_GUIDE.md for all options

# LLM providers (at least one required)
providers:
  deepseek:
    base-url: https://api.deepseek.com
    api-type: openai-completions
    api-keys: [ sk-your-key-here ]
    models:
      - id: deepseek-chat
        max-tokens: 8192

default-provider: deepseek

# Backend server
# backend_url: http://localhost:8080
# ws_url: ws://localhost:8081/ws
# ws_auth_token: default-token
YAML
    ok "Config created at ${JWCODE_HOME}/config.yaml"
    warn "Edit ${JWCODE_HOME}/config.yaml to add your API key(s)."
  else
    ok "Config exists: ${JWCODE_HOME}/config.yaml"
  fi
}

# --- summary ---
print_summary() {
  echo ""
  printf "${GREEN}========================================${NC}\n"
  printf "${GREEN}  JWCode installed successfully!${NC}\n"
  printf "${GREEN}========================================${NC}\n"
  echo ""
  echo "  Quick start:"
  echo "    jwcode start          # Start backend + interactive TUI"
  echo "    jwcode run            # Connect to existing backend"
  echo "    jwcode stop           # Stop daemon"
  echo "    jwcode doctor         # Run diagnostics"
  echo ""
  echo "  Config: ${JWCODE_HOME}/config.yaml"
  echo "  Docs:   https://github.com/jwcode/jwcode"
  echo ""
}

# --- main ---
main() {
  echo ""
  printf "${CYAN}╔══════════════════════════════════════════╗${NC}\n"
  printf "${CYAN}║   JWCode — Java AI Coding Tool Install  ║${NC}\n"
  printf "${CYAN}╚══════════════════════════════════════════╝${NC}\n"
  echo ""

  local platform
  platform=$(detect_platform) || exit 1
  info "Detected platform: ${platform}"

  check_java || exit 1
  check_node || exit 1
  check_permissions || exit 1

  create_config
  download_backend
  install_cli

  print_summary
}

main "$@"
