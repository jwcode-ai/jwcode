#!/usr/bin/env bash
set -e

echo "=== JWCode Launcher ==="
echo ""

# Check Java
if ! command -v java &>/dev/null; then
    echo "[ERROR] Java not found. Please install JDK 17+."
    exit 1
fi

# Check Maven
if ! command -v mvn &>/dev/null; then
    echo "[ERROR] Maven not found. Please install Maven 3.8+."
    exit 1
fi

# Check Node
if ! command -v node &>/dev/null; then
    echo "[ERROR] Node.js not found. Please install Node 18+."
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/ts-cli"

echo "[1/2] Installing npm dependencies..."
npm install --silent

echo "[2/2] Building and starting JWCode..."
npm start
