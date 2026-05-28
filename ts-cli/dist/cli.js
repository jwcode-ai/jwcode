#!/usr/bin/env node
var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// src/main.ts
import { render } from "ink";
import { createElement } from "react";

// src/App.tsx
import { useState as useState4, useEffect as useEffect3, useRef, useCallback as useCallback2 } from "react";
import { Box as Box6, Text as Text6, useInput as useInput4, useApp, useStdout } from "ink";

// src/components/TextInput.tsx
import { Box, Text, useInput } from "ink";
import { jsx, jsxs } from "react/jsx-runtime";
function TextInput({ value, onChange, onSubmit, placeholder, disabled }) {
  useInput((input, key) => {
    if (disabled) return;
    if (key.return) {
      onSubmit(value);
    } else if (key.backspace || key.delete) {
      onChange(value.slice(0, -1));
    } else if (input && !key.ctrl && !key.meta && !key.tab && !key.escape) {
      onChange(value + input);
    }
  });
  const display = value || "";
  const showPlaceholder = !display && placeholder;
  return /* @__PURE__ */ jsxs(Box, { children: [
    display ? /* @__PURE__ */ jsx(Text, { children: display }) : /* @__PURE__ */ jsx(Text, { dimColor: true, children: placeholder }),
    /* @__PURE__ */ jsx(Text, { dimColor: true, children: "\u258A" })
  ] });
}
__name(TextInput, "TextInput");

// src/client.ts
import WebSocket from "ws";
var PING_INTERVAL = 3e4;
var MAX_RECONNECT_DELAY = 3e4;
var JwCodeClient = class {
  static {
    __name(this, "JwCodeClient");
  }
  ws = null;
  handlers = /* @__PURE__ */ new Map();
  _running = false;
  _reconnecting = false;
  pingTimer = null;
  reconnectDelay = 1e3;
  reconnectTimer = null;
  sessionId = null;
  backendUrl;
  wsUrl;
  token;
  constructor(backendUrl, wsUrl, token = "default-token") {
    this.backendUrl = backendUrl;
    this.wsUrl = wsUrl;
    this.token = token;
  }
  on(msgType, handler) {
    if (!this.handlers.has(msgType)) this.handlers.set(msgType, /* @__PURE__ */ new Set());
    this.handlers.get(msgType).add(handler);
    return () => this.handlers.get(msgType)?.delete(handler);
  }
  async connect() {
    this._running = true;
    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(this.wsUrl);
      } catch (e) {
        reject(e);
        return;
      }
      this.ws.on("message", (raw) => {
        let data;
        try {
          data = JSON.parse(raw.toString());
        } catch {
          return;
        }
        if (data.type === "auth_required") {
          this.ws.send(JSON.stringify({ type: "auth", token: this.token }));
          return;
        }
        if (data.type === "auth_success") {
          this._createSession().then((sid) => {
            this.sessionId = sid;
            this._startHeartbeat();
            this._startReadLoop();
            this._reconnecting = false;
            this.reconnectDelay = 1e3;
            resolve(sid);
          }).catch(reject);
          return;
        }
        if (data.type === "auth_failed") {
          reject(new Error(`Auth failed: ${JSON.stringify(data)}`));
          return;
        }
      });
      this.ws.on("error", (err) => {
        if (!this.sessionId) reject(err);
        else this.dispatch({ type: "error", data: `WebSocket error: ${err.message}` });
      });
      this.ws.on("close", () => {
        this._stopHeartbeat();
        if (this._running && !this._reconnecting) {
          this._startReconnect();
        }
      });
    });
  }
  async _createSession() {
    const resp = await fetch(`${this.backendUrl}/api/sessions`, { method: "POST" });
    const data = await resp.json();
    const inner = data.data || {};
    const sid = inner.sessionId || data.sessionId || data.id || "default-session";
    console.log(`[ws] Session: ${sid}`);
    return sid;
  }
  _startReadLoop() {
    if (!this.ws) return;
    this.ws.on("message", (raw) => {
      let data;
      try {
        data = JSON.parse(raw.toString());
      } catch {
        return;
      }
      if (data.type === "auth_required" || data.type === "auth_success" || data.type === "auth_failed") return;
      this.dispatch(data);
    });
  }
  _startHeartbeat() {
    this._stopHeartbeat();
    this.pingTimer = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: "ping" }));
      }
    }, PING_INTERVAL);
  }
  _stopHeartbeat() {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }
  _startReconnect() {
    if (!this._running || this._reconnecting) return;
    this._reconnecting = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.reconnectTimer = setTimeout(async () => {
      try {
        console.log(`[ws] Reconnecting...`);
        this.dispatch({ type: "notification", data: "Reconnecting..." });
        await this.connect();
        this._reconnecting = false;
        this.dispatch({ type: "notification", data: "Reconnected." });
      } catch {
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, MAX_RECONNECT_DELAY);
        this._reconnecting = false;
        this._startReconnect();
      }
    }, this.reconnectDelay);
  }
  dispatch(msg) {
    const handlers = this.handlers.get(msg.type);
    if (!handlers) return;
    for (const h of handlers) {
      try {
        h(msg);
      } catch (e) {
        console.error(`Handler error [${msg.type}]:`, e);
      }
    }
  }
  send(msgType, message, data) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.warn(`[ws] Not connected, dropping: ${msgType}`);
      return;
    }
    const payload = { type: msgType, sessionId: this.sessionId };
    if (message !== void 0) payload.message = message;
    if (data) payload.data = JSON.stringify(data);
    try {
      this.ws.send(JSON.stringify(payload));
    } catch (e) {
      console.error(`[ws] send error [${msgType}]:`, e);
    }
  }
  chat(content, planMode = false) {
    this.send(planMode ? "plan" : "chat", content);
  }
  stop() {
    this.send("stop");
  }
  pause() {
    this.send("pause");
  }
  resume() {
    this.send("resume");
  }
  planConfirm() {
    this.send("plan_confirm");
  }
  updateDocs() {
    this.send("update_docs");
  }
  doctor() {
    this.send("doctor");
  }
  rewind() {
    this.send("rewind");
  }
  compact() {
    this.send("compact");
  }
  switchModel(model) {
    this.send("model_change", void 0, { model });
  }
  approveHook(approvalId) {
    this.send("hook_allow", void 0, { approvalId });
  }
  denyHook(approvalId) {
    this.send("hook_deny", void 0, { approvalId });
  }
  init() {
    this.send("init");
  }
  effort(level) {
    this.send("effort", void 0, { level });
  }
  branch(name) {
    this.send("branch", void 0, { name });
  }
  mcp(action) {
    this.send("mcp", void 0, { action });
  }
  skills() {
    this.send("skills");
  }
  agents() {
    this.send("agents");
  }
  config(action) {
    this.send("config", void 0, { action });
  }
  plugin(action) {
    this.send("plugin", void 0, { action });
  }
  async close() {
    this._running = false;
    this._reconnecting = false;
    this._stopHeartbeat();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }
};

// src/components/StatusLine.tsx
import { Box as Box2, Text as Text2 } from "ink";

// src/hooks/useAppState.ts
import { useEffect, useState, useCallback } from "react";

// src/store.ts
function createStore(initialState2, onChange) {
  let state = initialState2;
  const listeners = /* @__PURE__ */ new Set();
  return {
    getState: /* @__PURE__ */ __name(() => state, "getState"),
    setState: /* @__PURE__ */ __name((updater) => {
      const prev = state;
      const next = updater(prev);
      if (Object.is(next, prev)) return;
      state = next;
      onChange?.({ newState: next, oldState: prev });
      for (const listener of listeners) listener();
    }, "setState"),
    subscribe: /* @__PURE__ */ __name((listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    }, "subscribe")
  };
}
__name(createStore, "createStore");

// src/hooks/useAppState.ts
var initialState = {
  messages: [],
  currentMessage: null,
  usage: { promptTokens: 0, completionTokens: 0, totalTokens: 0, usageRatio: 0 },
  planMode: false,
  autoMode: false,
  planWaiting: false,
  scrollOffset: 0,
  modelName: "",
  connected: false,
  statusText: "connecting..."
};
var _store = null;
function getStore() {
  if (!_store) _store = createStore(initialState);
  return _store;
}
__name(getStore, "getStore");
function useAppState() {
  const store = getStore();
  const [state, setState] = useState(store.getState());
  useEffect(() => {
    return store.subscribe(() => setState(store.getState()));
  }, []);
  return state;
}
__name(useAppState, "useAppState");
function updateAppState(updater) {
  getStore().setState(updater);
}
__name(updateAppState, "updateAppState");

// src/components/StatusLine.tsx
import { jsx as jsx2, jsxs as jsxs2 } from "react/jsx-runtime";
function formatTokens(n) {
  if (n >= 1e6) return `${(n / 1e6).toFixed(1)}M`;
  if (n >= 1e3) return `${Math.round(n / 1e3)}K`;
  return String(n);
}
__name(formatTokens, "formatTokens");
function StatusLine() {
  const state = useAppState();
  const { usage, modelName, planMode, autoMode, connected, statusText } = state;
  const pct = Math.min(100, Math.round(usage.usageRatio * 100));
  const filled = Math.round(pct / 10);
  const bar = "=".repeat(filled) + "-".repeat(10 - filled);
  const model = modelName || (connected ? "ready" : "connecting...");
  const plan = planMode ? " [PLAN]" : "";
  const auto = autoMode ? " [AUTO]" : "";
  const isError = statusText.startsWith("Error:");
  return /* @__PURE__ */ jsxs2(Box2, { flexDirection: "column", width: "100%", paddingRight: 1, children: [
    /* @__PURE__ */ jsxs2(Box2, { height: 1, children: [
      /* @__PURE__ */ jsx2(Text2, { bold: true, color: "cyan", children: "jwcode" }),
      /* @__PURE__ */ jsx2(Text2, { color: "yellow", children: plan }),
      /* @__PURE__ */ jsx2(Text2, { color: "magenta", children: auto }),
      /* @__PURE__ */ jsx2(Text2, { children: "   " }),
      /* @__PURE__ */ jsx2(Text2, { color: "green", children: model }),
      /* @__PURE__ */ jsx2(Text2, { children: "   tokens: " }),
      /* @__PURE__ */ jsx2(Text2, { color: "yellow", children: formatTokens(usage.totalTokens) }),
      /* @__PURE__ */ jsx2(Text2, { children: "  " }),
      /* @__PURE__ */ jsxs2(Text2, { color: pct > 90 ? "red" : "white", children: [
        bar,
        " ",
        pct,
        "%"
      ] })
    ] }),
    statusText && statusText !== "connecting..." && /* @__PURE__ */ jsx2(Box2, { height: 1, children: /* @__PURE__ */ jsx2(Text2, { color: isError ? "red" : "grey", dimColor: !isError, children: statusText.slice(0, 100) }) })
  ] });
}
__name(StatusLine, "StatusLine");

// src/components/ChatArea.tsx
import { Box as Box3, Text as Text3 } from "ink";
import { jsx as jsx3, jsxs as jsxs3 } from "react/jsx-runtime";
var SEP = "\u2500".repeat(60);
function ChatArea({ messages, currentMessage, scrollOffset, terminalRows, reservedRows }) {
  const allMessages = currentMessage ? [...messages.filter((m) => m.id !== currentMessage.id)] : messages;
  const availableRows = Math.max(10, terminalRows - reservedRows);
  const maxVisible = Math.max(5, Math.floor(availableRows / 4));
  const total = allMessages.length;
  const clampedOffset = Math.min(scrollOffset, total - 1);
  const end = total - clampedOffset;
  const start = Math.max(0, end - maxVisible);
  const visibleMessages = allMessages.slice(start, end);
  const isScrolledUp = clampedOffset > 0;
  const hiddenAbove = start;
  const hiddenBelow = clampedOffset;
  return /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", width: "100%", children: [
    isScrolledUp && /* @__PURE__ */ jsx3(Box3, { children: /* @__PURE__ */ jsxs3(Text3, { color: "yellow", dimColor: true, children: [
      "\u25B2 \u4E0A\u65B9 ",
      hiddenAbove,
      " \u6761\u6D88\u606F (\u2191/PgUp \u4E0A\u7FFB, PgDn \u4E0B\u7FFB, End \u5230\u5E95\u90E8)"
    ] }) }),
    !isScrolledUp && total > maxVisible && /* @__PURE__ */ jsx3(Box3, { children: /* @__PURE__ */ jsxs3(Text3, { color: "grey", dimColor: true, children: [
      "... ",
      total - maxVisible,
      " \u6761\u66F4\u65E9\u7684\u6D88\u606F (\u2191/PgUp \u67E5\u770B)"
    ] }) }),
    visibleMessages.map((msg) => /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", marginBottom: 1, children: [
      msg.type === "user" && /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", children: [
        /* @__PURE__ */ jsx3(Text3, { dimColor: true, children: SEP }),
        /* @__PURE__ */ jsxs3(Text3, { color: "green", bold: true, children: [
          "> ",
          msg.content
        ] })
      ] }),
      msg.type === "assistant" && /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", children: [
        /* @__PURE__ */ jsx3(Text3, { children: " " }),
        msg.steps.map((step, i) => /* @__PURE__ */ jsx3(StepDisplay, { step }, step.id || i)),
        msg.thinking && /* @__PURE__ */ jsx3(Text3, { dimColor: true, italic: true, children: truncate(msg.thinking, 200) }),
        msg.toolCalls.map((tc, i) => /* @__PURE__ */ jsx3(ToolCallDisplay, { tc }, tc.id || i)),
        msg.content && /* @__PURE__ */ jsx3(Text3, { children: msg.content }),
        /* @__PURE__ */ jsx3(Text3, { dimColor: true, children: SEP })
      ] }),
      msg.type === "system" && /* @__PURE__ */ jsx3(Box3, { children: /* @__PURE__ */ jsxs3(Text3, { color: "red", children: [
        "Error: ",
        msg.content
      ] }) })
    ] }, msg.id)),
    currentMessage && /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", children: [
      currentMessage.thinking && /* @__PURE__ */ jsx3(Text3, { dimColor: true, italic: true, children: truncate(currentMessage.thinking, 200) }),
      currentMessage.toolCalls.map((tc, i) => /* @__PURE__ */ jsx3(ToolCallDisplay, { tc }, tc.id || i)),
      currentMessage.content && /* @__PURE__ */ jsx3(Text3, { children: currentMessage.content })
    ] }, currentMessage.id)
  ] });
}
__name(ChatArea, "ChatArea");
function StepDisplay({ step }) {
  const icon = step.status === "success" ? "\u2713" : step.status === "error" ? "\u2717" : "\u25B6";
  const color = step.status === "success" ? "green" : step.status === "error" ? "red" : "cyan";
  return /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", children: [
    /* @__PURE__ */ jsxs3(Text3, { color, children: [
      "  ",
      icon,
      " ",
      step.title
    ] }),
    step.thought && /* @__PURE__ */ jsxs3(Text3, { color: "blue", dimColor: true, children: [
      "    ",
      truncate(step.thought, 200)
    ] }),
    step.action && /* @__PURE__ */ jsxs3(Text3, { color: "yellow", children: [
      "    ",
      truncate(step.action, 200)
    ] }),
    step.result && /* @__PURE__ */ jsxs3(Text3, { color: "green", children: [
      "    ",
      truncate(step.result, 300)
    ] })
  ] });
}
__name(StepDisplay, "StepDisplay");
function ToolCallDisplay({ tc }) {
  const argsStr = tc.args ? truncate(formatJson(tc.args), 200) : "";
  const statusIcon = tc.status === "complete" ? "\u2713" : tc.status === "running" ? "\u25F7" : "\u2717";
  const statusColor = tc.status === "complete" ? "green" : tc.status === "running" ? "yellow" : "red";
  return /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", paddingLeft: 1, children: [
    /* @__PURE__ */ jsxs3(Box3, { children: [
      /* @__PURE__ */ jsxs3(Text3, { color: statusColor, children: [
        "  ",
        statusIcon,
        " "
      ] }),
      /* @__PURE__ */ jsx3(Text3, { bold: true, color: "magenta", children: tc.name }),
      argsStr && /* @__PURE__ */ jsxs3(Text3, { dimColor: true, children: [
        "  ",
        argsStr
      ] })
    ] }),
    tc.result && /* @__PURE__ */ jsx3(Box3, { paddingLeft: 4, children: /* @__PURE__ */ jsx3(Text3, { color: "green", dimColor: true, children: truncate(tc.result, 200) }) })
  ] });
}
__name(ToolCallDisplay, "ToolCallDisplay");
function formatJson(s) {
  try {
    return JSON.stringify(JSON.parse(s), null, 2);
  } catch {
    return s;
  }
}
__name(formatJson, "formatJson");
function truncate(s, max) {
  if (s.length <= max) return s;
  return s.slice(0, max) + "...";
}
__name(truncate, "truncate");

// src/components/CommandPalette.tsx
import { useState as useState2, useMemo, useEffect as useEffect2 } from "react";
import { Box as Box4, Text as Text4, useInput as useInput2 } from "ink";

// src/commands/index.ts
var LOCAL_COMMANDS = [
  { cmd: "/help", desc: "\u663E\u793A\u6240\u6709\u547D\u4EE4", via: "local", action: null },
  { cmd: "/plan", desc: "\u5207\u6362\u89C4\u5212\u6A21\u5F0F (\u5148\u89C4\u5212\u518D\u6267\u884C)", via: "local", action: "plan_mode" },
  { cmd: "/auto", desc: "\u5207\u6362\u81EA\u52A8\u6A21\u5F0F (\u81EA\u52A8\u6279\u51C6\u5DE5\u5177\u6267\u884C)", via: "local", action: "auto_mode" },
  { cmd: "/context", desc: "\u663E\u793A\u5F53\u524D\u4F1A\u8BDD\u72B6\u6001", via: "local", action: "show_context" },
  { cmd: "/exit", desc: "\u9000\u51FA JWCode", via: "local", action: "__exit__" }
];
var WS_COMMANDS = [
  { cmd: "/confirm", desc: "\u786E\u8BA4\u5F53\u524D\u89C4\u5212\u5E76\u5F00\u59CB\u6267\u884C", via: "ws", action: "__confirm_plan" },
  { cmd: "/cancel", desc: "\u53D6\u6D88\u5F53\u524D\u89C4\u5212", via: "ws", action: "__cancel_plan" },
  { cmd: "/stop", desc: "\u505C\u6B62\u5F53\u524D AI \u751F\u6210", via: "ws", action: "stop" },
  { cmd: "/pause", desc: "\u6682\u505C\u5F53\u524D AI \u751F\u6210", via: "ws", action: "pause" },
  { cmd: "/resume", desc: "\u6062\u590D\u6682\u505C\u7684 AI \u751F\u6210", via: "ws", action: "resume" },
  { cmd: "/clear", desc: "\u6E05\u9664\u5F53\u524D\u4F1A\u8BDD\u6D88\u606F", via: "ws", action: "clear" },
  { cmd: "/doctor", desc: "\u8FD0\u884C\u7CFB\u7EDF\u81EA\u8BCA\u65AD", via: "ws", action: "doctor" },
  { cmd: "/rewind", desc: "\u56DE\u6EDA\u5230\u6700\u8FD1\u7684\u68C0\u67E5\u70B9", via: "ws", action: "rewind" },
  { cmd: "/compact", desc: "\u538B\u7F29\u4F1A\u8BDD\u4E0A\u4E0B\u6587 (\u91CA\u653E token)", via: "ws", action: "compact" },
  { cmd: "/model", desc: "\u5207\u6362 AI \u6A21\u578B (\u7528\u6CD5: /model <\u6A21\u578B\u540D>)", via: "ws", action: "model_change" },
  { cmd: "/init", desc: "\u5206\u6790\u9879\u76EE\u5E76\u751F\u6210 JWCODE.md \u9879\u76EE\u8BB0\u5FC6\u6587\u4EF6", via: "ws", action: "init" },
  { cmd: "/effort", desc: "\u8BBE\u7F6E\u4EFB\u52A1\u52AA\u529B\u7EA7\u522B (low/medium/high)", via: "ws", action: "effort" },
  { cmd: "/branch", desc: "\u521B\u5EFA\u5206\u652F\u4F1A\u8BDD (\u7528\u6CD5: /branch <\u540D\u79F0>)", via: "ws", action: "branch" },
  { cmd: "/mcp", desc: "MCP \u670D\u52A1\u5668\u7BA1\u7406 (list/add/remove)", via: "ws", action: "mcp" },
  { cmd: "/skills", desc: "\u67E5\u770B\u53EF\u7528 Skills \u5217\u8868", via: "ws", action: "skills" },
  { cmd: "/agents", desc: "\u5217\u51FA\u914D\u7F6E\u7684 Agent \u4EE3\u7406", via: "ws", action: "agents" },
  { cmd: "/config", desc: "\u7BA1\u7406\u914D\u7F6E (get/set/list)", via: "ws", action: "config" },
  { cmd: "/plugin", desc: "\u63D2\u4EF6\u7BA1\u7406 (install/list/remove)", via: "ws", action: "plugin" }
];
var ALL_COMMANDS = [...LOCAL_COMMANDS, ...WS_COMMANDS];
var SLASH_COMMANDS = {
  "/help": null,
  "/plan": { action: "plan_mode" },
  "/auto": { action: "auto_mode" },
  "/context": { action: "show_context" },
  "/exit": { action: "__exit__" },
  "/quit": { action: "__exit__" },
  "/confirm": { action: "__confirm_plan" },
  "/cancel": { action: "__cancel_plan" },
  "/stop": { action: "stop" },
  "/pause": { action: "pause" },
  "/resume": { action: "resume" },
  "/clear": { action: "clear" },
  "/doctor": { action: "doctor" },
  "/rewind": { action: "rewind" },
  "/compact": { action: "compact" },
  "/model": { action: "model_change", needsArg: true },
  "/init": { action: "init" },
  "/effort": { action: "effort", needsArg: true },
  "/branch": { action: "branch", needsArg: true },
  "/mcp": { action: "mcp", needsArg: true },
  "/skills": { action: "skills" },
  "/agents": { action: "agents" },
  "/config": { action: "config", needsArg: true },
  "/plugin": { action: "plugin", needsArg: true }
};
var HELP_TEXT = `
\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557
\u2551        JWCode \u547D\u4EE4\u5E2E\u52A9                    \u2551
\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563
\u2551  \u672C\u5730\u547D\u4EE4:                                \u2551
\u2551  /help        \u663E\u793A\u6B64\u5E2E\u52A9\u4FE1\u606F              \u2551
\u2551  /plan        \u5207\u6362\u89C4\u5212\u6A21\u5F0F                \u2551
\u2551  /auto        \u5207\u6362\u81EA\u52A8\u6A21\u5F0F                \u2551
\u2551  /context     \u663E\u793A\u5F53\u524D\u4F1A\u8BDD\u72B6\u6001            \u2551
\u2551  /exit        \u9000\u51FA JWCode                \u2551
\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563
\u2551  \u540E\u7AEF\u547D\u4EE4:                                \u2551
\u2551  /confirm     \u786E\u8BA4\u6267\u884C\u5F53\u524D\u89C4\u5212            \u2551
\u2551  /cancel      \u53D6\u6D88\u5F53\u524D\u89C4\u5212                \u2551
\u2551  /stop        \u505C\u6B62\u5F53\u524D AI \u751F\u6210            \u2551
\u2551  /pause       \u6682\u505C\u5F53\u524D AI \u751F\u6210            \u2551
\u2551  /resume      \u6062\u590D\u6682\u505C\u7684\u751F\u6210              \u2551
\u2551  /clear       \u6E05\u9664\u5F53\u524D\u4F1A\u8BDD\u6D88\u606F            \u2551
\u2551  /model <\u540D>  \u5207\u6362 AI \u6A21\u578B                \u2551
\u2551  /compact     \u538B\u7F29\u4F1A\u8BDD\u4E0A\u4E0B\u6587              \u2551
\u2551  /doctor      \u7CFB\u7EDF\u81EA\u8BCA\u65AD                  \u2551
\u2551  /rewind      \u56DE\u6EDA\u5230\u6700\u8FD1\u68C0\u67E5\u70B9            \u2551
\u2551  /init        \u751F\u6210\u9879\u76EE JWCODE.md           \u2551
\u2551  /effort <\u7EA7> \u8BBE\u7F6E\u52AA\u529B\u7EA7\u522B low/med/high   \u2551
\u2551  /branch <\u540D> \u521B\u5EFA\u5206\u652F\u4F1A\u8BDD                \u2551
\u2551  /mcp <\u64CD\u4F5C>  MCP \u670D\u52A1\u5668\u7BA1\u7406              \u2551
\u2551  /skills      \u67E5\u770B Skills \u5217\u8868            \u2551
\u2551  /agents      \u5217\u51FA Agent \u4EE3\u7406             \u2551
\u2551  /config <\u64CD> \u7BA1\u7406\u914D\u7F6E (get/set/list)     \u2551
\u2551  /plugin <\u64CD> \u63D2\u4EF6\u7BA1\u7406                    \u2551
\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563
\u2551  \u666E\u901A\u8F93\u5165\u5373\u53D1\u9001\u804A\u5929\u6D88\u606F                   \u2551
\u2551  \u8F93\u5165 / \u5F39\u51FA\u547D\u4EE4\u9762\u677F                      \u2551
\u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D`;

// src/components/CommandPalette.tsx
import { jsx as jsx4, jsxs as jsxs4 } from "react/jsx-runtime";
function CommandPalette({ filter, onSelect }) {
  const [selected, setSelected] = useState2(0);
  const visible = useMemo(() => {
    const f = filter.replace(/^\//, "").toLowerCase();
    if (!f) return ALL_COMMANDS;
    return ALL_COMMANDS.filter(
      (c) => c.cmd.toLowerCase().includes(f) || c.desc.includes(f)
    );
  }, [filter]);
  useEffect2(() => {
    setSelected(0);
  }, [filter]);
  useInput2((_input, key) => {
    if (key.escape) {
      onSelect(null);
      return;
    }
    if (key.downArrow) {
      setSelected((prev) => Math.min(prev + 1, visible.length - 1));
      return;
    }
    if (key.upArrow) {
      setSelected((prev) => Math.max(prev - 1, 0));
      return;
    }
    if (key.return) {
      if (visible.length > 0 && selected >= 0 && selected < visible.length) {
        onSelect(visible[selected].cmd);
      }
    }
  });
  const maxShow = 12;
  const sliced = visible.slice(0, maxShow);
  return /* @__PURE__ */ jsxs4(Box4, { flexDirection: "column", borderStyle: "single", borderColor: "cyan", paddingX: 1, width: 52, children: [
    /* @__PURE__ */ jsxs4(Box4, { children: [
      /* @__PURE__ */ jsx4(Text4, { bold: true, color: "cyan", children: "\u547D\u4EE4\u5217\u8868" }),
      /* @__PURE__ */ jsx4(Text4, { dimColor: true, children: "  \u2191\u2193\u9009\u62E9 / \u56DE\u8F66\u786E\u8BA4 / Esc\u53D6\u6D88" })
    ] }),
    sliced.map((cmd, i) => /* @__PURE__ */ jsxs4(Box4, { paddingLeft: 1, children: [
      /* @__PURE__ */ jsx4(Text4, { color: i === selected ? "cyan" : void 0, bold: i === selected, children: i === selected ? "> " : "  " }),
      /* @__PURE__ */ jsx4(Text4, { color: "green", children: cmd.cmd }),
      /* @__PURE__ */ jsxs4(Text4, { dimColor: true, children: [
        "  ",
        cmd.desc
      ] }),
      /* @__PURE__ */ jsxs4(Text4, { color: cmd.via === "ws" ? "yellow" : "blue", dimColor: i !== selected, children: [
        "(",
        cmd.via === "ws" ? "\u540E\u7AEF" : "\u672C\u5730",
        ")"
      ] })
    ] }, cmd.cmd)),
    visible.length > maxShow && /* @__PURE__ */ jsx4(Box4, { children: /* @__PURE__ */ jsxs4(Text4, { dimColor: true, children: [
      "  ... \u8FD8\u6709 ",
      visible.length - maxShow,
      " \u6761\u547D\u4EE4"
    ] }) })
  ] });
}
__name(CommandPalette, "CommandPalette");

// src/components/ApprovalModal.tsx
import { useState as useState3 } from "react";
import { Box as Box5, Text as Text5, useInput as useInput3 } from "ink";
import { jsx as jsx5, jsxs as jsxs5 } from "react/jsx-runtime";
function ApprovalModal({ toolName, payload, onAllow, onDeny }) {
  const [selected, setSelected] = useState3(0);
  useInput3((_input, key) => {
    if (key.escape || key.tab) {
      onDeny();
      return;
    }
    if (key.upArrow || key.downArrow) {
      setSelected((prev) => prev === 0 ? 1 : 0);
      return;
    }
    if (key.return) {
      if (selected === 0) onAllow();
      else onDeny();
      return;
    }
    if (_input === "1") {
      onAllow();
      return;
    }
    if (_input === "2") {
      onDeny();
      return;
    }
    if (_input === "y" || _input === "Y") {
      onAllow();
      return;
    }
    if (_input === "n" || _input === "N") {
      onDeny();
      return;
    }
  });
  const desc = payload ? payload.length > 200 ? payload.slice(0, 200) + "..." : payload : "";
  return /* @__PURE__ */ jsxs5(Box5, { flexDirection: "column", borderStyle: "round", borderColor: "yellow", paddingX: 2, paddingY: 1, marginTop: 1, children: [
    /* @__PURE__ */ jsx5(Box5, { marginBottom: 1, children: /* @__PURE__ */ jsx5(Text5, { bold: true, children: "Do you want to proceed?" }) }),
    /* @__PURE__ */ jsxs5(Box5, { flexDirection: "column", marginLeft: 2, marginBottom: 1, children: [
      /* @__PURE__ */ jsx5(Box5, { children: /* @__PURE__ */ jsxs5(Text5, { color: selected === 0 ? "green" : void 0, children: [
        selected === 0 ? " \u276F" : "  ",
        " 1. Allow"
      ] }) }),
      /* @__PURE__ */ jsx5(Box5, { children: /* @__PURE__ */ jsxs5(Text5, { color: selected === 1 ? "red" : void 0, children: [
        selected === 1 ? " \u276F" : "  ",
        " 2. Deny"
      ] }) })
    ] }),
    /* @__PURE__ */ jsxs5(Box5, { marginBottom: 1, children: [
      /* @__PURE__ */ jsx5(Text5, { dimColor: true, children: "Tool: " }),
      /* @__PURE__ */ jsx5(Text5, { color: "cyan", children: toolName }),
      desc ? /* @__PURE__ */ jsxs5(Text5, { dimColor: true, children: [
        "  ",
        desc
      ] }) : null
    ] }),
    /* @__PURE__ */ jsxs5(Box5, { children: [
      /* @__PURE__ */ jsx5(Text5, { dimColor: true, children: " Esc to cancel \xB7 " }),
      /* @__PURE__ */ jsx5(Text5, { dimColor: true, children: "\u2191\u2193 to select \xB7 " }),
      /* @__PURE__ */ jsx5(Text5, { dimColor: true, children: "Enter to confirm" })
    ] })
  ] });
}
__name(ApprovalModal, "ApprovalModal");

// src/protocol.ts
function parseData(m) {
  if (typeof m.data === "string") {
    try {
      return JSON.parse(m.data);
    } catch {
      return {};
    }
  }
  return m.data || {};
}
__name(parseData, "parseData");
function createMessage(type, content = "") {
  return {
    id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    type,
    content,
    thinking: "",
    steps: [],
    toolCalls: [],
    timestamp: Date.now()
  };
}
__name(createMessage, "createMessage");

// src/App.tsx
import { jsx as jsx6, jsxs as jsxs6 } from "react/jsx-runtime";
function cleanArgs(raw) {
  let s = raw;
  for (let i = 0; i < 10; i++) {
    try {
      const obj = JSON.parse(s);
      if (obj && typeof obj === "object" && !Array.isArray(obj)) {
        if (typeof obj.command === "string") return obj.command;
        if (typeof obj.command === "object") {
          s = JSON.stringify(obj.command);
          continue;
        }
        return JSON.stringify(obj, null, 2);
      }
      return s;
    } catch {
      return s;
    }
  }
  return s;
}
__name(cleanArgs, "cleanArgs");
function App({ backendUrl, wsUrl, onExit }) {
  const [input, setInput] = useState4("");
  const [showPalette, setShowPalette] = useState4(false);
  const [showHelp, setShowHelp] = useState4(false);
  const [showApproval, setShowApproval] = useState4(null);
  const { exit } = useApp();
  const state = useAppState();
  const clientRef = useRef(null);
  const { stdout } = useStdout();
  const terminalRows = stdout?.rows || 24;
  const terminalCols = stdout?.columns || 80;
  const reservedRows = 8;
  const hline = "\u2500".repeat(terminalCols);
  useEffect3(() => {
    const client = new JwCodeClient(backendUrl, wsUrl);
    clientRef.current = client;
    wireHandlers(client);
    client.connect().then(() => {
      updateAppState((s) => ({ ...s, connected: true }));
      fetch(`${backendUrl}/api/models`).then((r) => r.json()).then((d) => {
        const models = d.data?.models;
        if (models?.length) {
          updateAppState((s) => ({ ...s, modelName: models[0].name || "" }));
        }
      }).catch(() => {
      });
    }).catch((err) => {
      updateAppState((s) => ({ ...s, statusText: `Connection failed: ${err.message}` }));
    });
    return () => {
      client.close();
    };
  }, [backendUrl, wsUrl]);
  const executeCommand = useCallback2((value) => {
    const text = value.trim();
    if (!text || !clientRef.current) return;
    setInput("");
    setShowHelp(false);
    setShowPalette(false);
    const parts = text.startsWith("/") ? text.split(/\s+/) : [];
    const cmd = parts[0] || null;
    const cmdArg = parts.slice(1).join(" ");
    if (cmd && cmd in SLASH_COMMANDS) {
      const def = SLASH_COMMANDS[cmd];
      if (def === null) {
        setShowHelp(true);
        return;
      }
      const { action, needsArg } = def;
      const client = clientRef.current;
      switch (action) {
        case "__exit__":
          onExit();
          return;
        case "__confirm_plan":
          updateAppState((prev) => {
            if (!prev.planWaiting) return prev;
            client?.planConfirm();
            return { ...prev, planWaiting: false };
          });
          return;
        case "__cancel_plan":
          updateAppState((prev) => ({ ...prev, planWaiting: false }));
          return;
        case "plan_mode":
          updateAppState((prev) => ({ ...prev, planMode: !prev.planMode }));
          return;
        case "auto_mode":
          updateAppState((prev) => ({ ...prev, autoMode: !prev.autoMode }));
          return;
        case "clear":
          updateAppState((prev) => ({ ...prev, messages: [], currentMessage: null }));
          return;
        case "model_change":
          if (needsArg && cmdArg) client?.switchModel(cmdArg);
          return;
        case "show_context":
          updateAppState((prev) => ({
            ...prev,
            statusText: `\u4F1A\u8BDD\u6D88\u606F: ${prev.messages.length} | \u6A21\u5F0F: ${prev.planMode ? "\u89C4\u5212" : "\u6267\u884C"} | \u81EA\u52A8: ${prev.autoMode ? "\u5F00" : "\u5173"} | \u6A21\u578B: ${prev.modelName || "\u672A\u8FDE\u63A5"}`
          }));
          return;
        // WS commands — send directly via client method
        case "stop":
          client?.stop();
          return;
        case "pause":
          client?.pause();
          return;
        case "resume":
          client?.resume();
          return;
        case "doctor":
          client?.doctor();
          return;
        case "rewind":
          client?.rewind();
          return;
        case "compact":
          client?.compact();
          return;
        case "init":
          client?.init();
          return;
        case "effort":
          if (cmdArg) client?.effort(cmdArg);
          return;
        case "branch":
          if (cmdArg) client?.branch(cmdArg);
          return;
        case "mcp":
          if (cmdArg) client?.mcp(cmdArg);
          return;
        case "skills":
          client?.skills();
          return;
        case "agents":
          client?.agents();
          return;
        case "config":
          if (cmdArg) client?.config(cmdArg);
          return;
        case "plugin":
          if (cmdArg) client?.plugin(cmdArg);
          return;
      }
      return;
    }
    if (text.startsWith("/") && !(cmd && cmd in SLASH_COMMANDS)) return;
    const msg = createMessage("user", text);
    updateAppState((prev) => ({ ...prev, messages: [...prev.messages, msg] }));
    clientRef.current.chat(text, state.planMode);
  }, [onExit, state.planMode]);
  const handleSubmit = useCallback2((value) => {
    if (showPalette) return;
    executeCommand(value);
  }, [executeCommand, showPalette]);
  const handleChange = useCallback2((value) => {
    setInput(value);
    if (value.startsWith("/")) {
      setShowPalette(true);
    } else {
      setShowPalette(false);
    }
  }, []);
  const handlePaletteSelect = useCallback2((cmd) => {
    if (cmd) {
      executeCommand(cmd);
    } else {
      setShowPalette(false);
      setInput("");
    }
  }, [executeCommand]);
  const handleApprovalAllow = useCallback2((approvalId) => {
    clientRef.current?.approveHook(approvalId);
    setShowApproval(null);
  }, []);
  const handleApprovalDeny = useCallback2((approvalId) => {
    clientRef.current?.denyHook(approvalId);
    setShowApproval(null);
  }, []);
  function wireHandlers(client) {
    const INTERVAL = 150;
    let pendingContent = "";
    let pendingThinking = "";
    let pendingToolCalls = [];
    let batchTimer = null;
    let batchChanged = false;
    function applyBatch() {
      batchTimer = null;
      if (!batchChanged) return;
      batchChanged = false;
      const c = pendingContent;
      pendingContent = "";
      const t = pendingThinking;
      pendingThinking = "";
      const tcfns = pendingToolCalls;
      pendingToolCalls = [];
      updateAppState((prev) => {
        if (!prev.currentMessage) return prev;
        if (c) prev.currentMessage.content += c;
        if (t) prev.currentMessage.thinking += t;
        for (const fn of tcfns) fn(prev.currentMessage);
        return { ...prev };
      });
    }
    __name(applyBatch, "applyBatch");
    function scheduleBatch() {
      batchChanged = true;
      if (!batchTimer) batchTimer = setTimeout(applyBatch, INTERVAL);
    }
    __name(scheduleBatch, "scheduleBatch");
    function flushNow() {
      if (batchTimer) {
        clearTimeout(batchTimer);
        batchTimer = null;
      }
      applyBatch();
    }
    __name(flushNow, "flushNow");
    client.on("start", () => {
      flushNow();
      const msg = createMessage("assistant");
      updateAppState((prev) => ({
        ...prev,
        currentMessage: msg,
        messages: [...prev.messages, msg],
        scrollOffset: prev.scrollOffset > 0 ? prev.scrollOffset + 1 : 0
      }));
    });
    client.on("content", (m) => {
      const text = typeof m.data === "string" ? m.data : m.data ? String(m.data) : "";
      pendingContent += text;
      scheduleBatch();
    });
    client.on("thinking", (m) => {
      pendingThinking += typeof m.data === "string" ? m.data : "";
      scheduleBatch();
    });
    client.on("tool_call", (m) => {
      const d = parseData(m);
      pendingToolCalls.push((msg) => {
        let existingIdx = d.id ? msg.toolCalls.findIndex((t) => t.id === d.id) : -1;
        if (existingIdx < 0 && d.name) {
          existingIdx = msg.toolCalls.findIndex(
            (t) => t.name === d.name && t.status === "running"
          );
        }
        if (existingIdx >= 0) {
          const existing = { ...msg.toolCalls[existingIdx] };
          if (d.args) existing.args = cleanArgs(d.args);
          if (d.complete) existing.status = "complete";
          if (d.result) existing.result = d.result;
          msg.toolCalls = [...msg.toolCalls];
          msg.toolCalls[existingIdx] = existing;
        } else {
          const updated = [...msg.toolCalls];
          updated.push({
            id: d.id || (d.name ? `${d.name}-${Date.now()}` : ""),
            name: d.name || "",
            args: d.args ? cleanArgs(d.args) : void 0,
            status: d.complete ? "complete" : "running",
            complete: !!d.complete
          });
          msg.toolCalls = updated;
        }
      });
      scheduleBatch();
    });
    client.on("tool_result", (m) => {
      const d = parseData(m);
      pendingToolCalls.push((msg) => {
        const tcs = [...msg.toolCalls];
        for (let i = tcs.length - 1; i >= 0; i--) {
          if (tcs[i].name === d.toolName && !tcs[i].result) {
            tcs[i] = { ...tcs[i], result: d.result || "", status: "complete" };
            break;
          }
        }
        msg.toolCalls = tcs;
      });
      scheduleBatch();
    });
    client.on("complete", () => {
      flushNow();
      updateAppState((prev) => ({ ...prev, currentMessage: null }));
    });
    client.on("error", (m) => {
      const text = String(m.data || "Error");
      updateAppState((prev) => ({
        ...prev,
        statusText: `Error: ${text.slice(0, 120)}`
      }));
    });
    let firstTokenUpdate = true;
    client.on("token_update", (m) => {
      let d = {};
      if (typeof m.data === "string") {
        try {
          d = JSON.parse(m.data);
        } catch {
        }
      } else if (m.data && typeof m.data === "object") {
        d = m.data;
      }
      if (firstTokenUpdate) {
        firstTokenUpdate = false;
        console.log("[token_update] raw data type:", typeof m.data, "| parsed keys:", Object.keys(d).join(","));
      }
      const promptTokens = Number(d.promptTokens) || 0;
      const completionTokens = Number(d.completionTokens) || 0;
      const totalTokens = Number(d.totalTokens) || 0;
      const usageRatio = Number(d.usageRatio) || 0;
      if (totalTokens > 0) {
        updateAppState((prev) => ({
          ...prev,
          usage: { promptTokens, completionTokens, totalTokens, usageRatio },
          modelName: d.model || prev.modelName
        }));
      }
    });
    client.on("hook_ask", (m) => {
      const d = parseData(m);
      const approvalId = d.approvalId || "";
      if (getStore().getState().autoMode) {
        client.approveHook(approvalId);
        return;
      }
      setShowApproval({
        approvalId,
        toolName: d.toolName || "",
        payload: d.askPayload || d.payload || JSON.stringify(d)
      });
    });
    client.on("plan_start", () => {
      updateAppState((prev) => ({ ...prev, planWaiting: false }));
    });
    client.on("plan_complete", (m) => {
      const d = parseData(m);
      const status = d.status;
      if (status === "waiting_confirm") {
        updateAppState((prev) => ({ ...prev, planWaiting: true }));
      } else {
        updateAppState((prev) => ({ ...prev, planWaiting: false }));
      }
    });
    client.on("notification", (m) => {
      const text = String(m.data || "");
      updateAppState((prev) => ({
        ...prev,
        statusText: text,
        connected: text === "Reconnected." ? true : prev.connected
      }));
    });
  }
  __name(wireHandlers, "wireHandlers");
  useInput4((input2, key) => {
    if (key.escape && showApproval) {
      handleApprovalDeny(showApproval.approvalId);
      return;
    }
    if (key.pageUp) {
      updateAppState((prev) => ({
        ...prev,
        scrollOffset: Math.min(prev.scrollOffset + 5, prev.messages.length)
      }));
      return;
    }
    if (key.upArrow && !showApproval) {
      updateAppState((prev) => ({
        ...prev,
        scrollOffset: Math.min(prev.scrollOffset + 1, prev.messages.length)
      }));
      return;
    }
    if (key.pageDown) {
      updateAppState((prev) => ({
        ...prev,
        scrollOffset: Math.max(0, prev.scrollOffset - 5)
      }));
      return;
    }
    if (key.downArrow && !showApproval) {
      updateAppState((prev) => ({
        ...prev,
        scrollOffset: Math.max(0, prev.scrollOffset - 1)
      }));
      return;
    }
    if (key.home) {
      updateAppState((prev) => ({ ...prev, scrollOffset: prev.messages.length }));
      return;
    }
    if (key.end) {
      updateAppState((prev) => ({ ...prev, scrollOffset: 0 }));
      return;
    }
    if (key.tab) {
      updateAppState((prev) => ({ ...prev, planMode: !prev.planMode }));
      return;
    }
  });
  const placeholder = "";
  return /* @__PURE__ */ jsxs6(Box6, { flexDirection: "column", width: "100%", height: "100%", children: [
    /* @__PURE__ */ jsx6(StatusLine, {}),
    /* @__PURE__ */ jsx6(Box6, { flexGrow: 1, flexDirection: "column", children: /* @__PURE__ */ jsx6(ChatArea, { messages: state.messages, currentMessage: state.currentMessage, scrollOffset: state.scrollOffset, terminalRows, reservedRows }) }),
    /* @__PURE__ */ jsxs6(Box6, { flexDirection: "row", borderStyle: "single", borderColor: state.connected ? "cyan" : "red", paddingLeft: 1, children: [
      /* @__PURE__ */ jsx6(Text6, { color: "green", bold: true, children: "> " }),
      /* @__PURE__ */ jsx6(Box6, { flexGrow: 1, children: /* @__PURE__ */ jsx6(
        TextInput,
        {
          value: input,
          onChange: handleChange,
          onSubmit: handleSubmit,
          placeholder,
          disabled: showApproval !== null
        }
      ) })
    ] }),
    /* @__PURE__ */ jsxs6(Box6, { paddingLeft: 2, height: 1, children: [
      /* @__PURE__ */ jsx6(
        Text6,
        {
          color: state.planMode ? "cyan" : "grey",
          bold: state.planMode,
          dimColor: !state.planMode,
          children: state.planMode ? "\u25C9 Plan" : "\u25CB Plan"
        }
      ),
      /* @__PURE__ */ jsx6(Text6, { children: "  " }),
      /* @__PURE__ */ jsx6(
        Text6,
        {
          color: !state.planMode ? "green" : "grey",
          bold: !state.planMode,
          dimColor: state.planMode,
          children: !state.planMode ? "\u25C9 Act" : "\u25CB Act"
        }
      ),
      /* @__PURE__ */ jsx6(Text6, { dimColor: true, children: "  Tab \u5207\u6362" })
    ] }),
    !state.connected && /* @__PURE__ */ jsxs6(Box6, { children: [
      /* @__PURE__ */ jsx6(Text6, { color: "red", children: "\u540E\u7AEF\u672A\u8FDE\u63A5 \u2014 WebSocket \u91CD\u8BD5\u4E2D\u3002\u5982\u540E\u7AEF\u672A\u542F\u52A8\u8BF7\u7528 " }),
      /* @__PURE__ */ jsx6(Text6, { color: "yellow", bold: true, children: "npm start" })
    ] }),
    state.planWaiting && /* @__PURE__ */ jsx6(Box6, { children: /* @__PURE__ */ jsx6(Text6, { color: "yellow", bold: true, children: "Plan ready \u2014 /confirm to execute, /cancel to discard." }) }),
    showPalette && /* @__PURE__ */ jsx6(CommandPalette, { filter: input, onSelect: handlePaletteSelect }),
    showHelp && /* @__PURE__ */ jsx6(Box6, { flexDirection: "column", borderStyle: "single", borderColor: "cyan", paddingX: 1, children: HELP_TEXT.split("\n").map((line, i) => /* @__PURE__ */ jsx6(Text6, { color: "cyan", children: line }, i)) }),
    showApproval && /* @__PURE__ */ jsx6(
      ApprovalModal,
      {
        toolName: showApproval.toolName,
        payload: showApproval.payload,
        onAllow: () => handleApprovalAllow(showApproval.approvalId),
        onDeny: () => handleApprovalDeny(showApproval.approvalId)
      }
    )
  ] });
}
__name(App, "App");

// src/launcher.ts
import { exec, execSync } from "node:child_process";
import { existsSync, readdirSync, statSync } from "node:fs";
import { join, dirname } from "node:path";
import { homedir } from "node:os";
import { fileURLToPath } from "node:url";
var __filename = fileURLToPath(import.meta.url);
var __dirname = dirname(__filename);
function findProjectRoot() {
  let dir = join(__dirname, "..", "..");
  while (dir !== dirname(dir)) {
    if (existsSync(join(dir, "jwcode-parent", "pom.xml"))) return dir;
    dir = dirname(dir);
  }
  return process.cwd();
}
__name(findProjectRoot, "findProjectRoot");
function findMvn() {
  const paths = process.env.PATH?.split(";") || [];
  for (const dir of paths) {
    for (const name of ["mvn.cmd", "mvn.bat", "mvn"]) {
      const full = join(dir, name);
      if (existsSync(full)) return full;
    }
  }
  for (const root of ["C:\\Program Files", homedir()]) {
    try {
      for (const entry of readdirSync(root, { withFileTypes: true })) {
        if (entry.isDirectory() && entry.name.startsWith("apache-maven")) {
          const mvn = join(root, entry.name, "bin", "mvn.cmd");
          if (existsSync(mvn)) return mvn;
        }
      }
    } catch {
    }
  }
  return "mvn";
}
__name(findMvn, "findMvn");
function jarExists(projectRoot) {
  const targetDir = join(projectRoot, "jwcode-web", "target");
  if (!existsSync(targetDir)) return null;
  try {
    const jars = readdirSync(targetDir).filter((f) => f.startsWith("jwcode-web-") && f.endsWith(".jar")).map((f) => ({ name: f, mtime: statSync(join(targetDir, f)).mtimeMs })).sort((a, b) => b.mtime - a.mtime);
    return jars.length > 0 ? join(targetDir, jars[0].name) : null;
  } catch {
    return null;
  }
}
__name(jarExists, "jarExists");
function buildBackend(projectRoot) {
  const mvn = findMvn();
  const cmd = `"${mvn}" package -pl jwcode-web -am -q -DskipTests`;
  console.log(`[launcher] Building: ${cmd}`);
  try {
    execSync(cmd, { cwd: projectRoot, stdio: "pipe" });
  } catch (e) {
    const err = e;
    console.error("[launcher] Build failed:");
    console.error(err.stderr?.toString() || err.stdout?.toString() || String(e));
    process.exit(1);
  }
  if (!jarExists(projectRoot)) {
    console.error("[launcher] Build succeeded but jar not found");
    process.exit(1);
  }
}
__name(buildBackend, "buildBackend");
function killPort(port) {
  try {
    if (process.platform === "win32") {
      execSync(`netstat -ano | findstr :${port} | findstr LISTENING`, { stdio: "pipe" });
      const out = execSync(`netstat -ano | findstr :${port} | findstr LISTENING`, { encoding: "utf-8" });
      for (const line of out.trim().split("\n")) {
        const pid = line.trim().split(/\s+/).pop();
        if (pid) {
          try {
            process.kill(parseInt(pid), "SIGTERM");
            console.log(`[launcher] Killed process on port ${port} (PID ${pid})`);
          } catch {
          }
        }
      }
    } else {
      execSync(`lsof -ti:${port} | xargs kill -9 2>/dev/null`, { stdio: "ignore" });
    }
  } catch {
  }
}
__name(killPort, "killPort");
function waitForBackend(port, timeout = 60) {
  const start = Date.now();
  const url = `http://localhost:${port}/api/system/status`;
  return new Promise((resolve, reject) => {
    function check() {
      if (Date.now() - start > timeout * 1e3) {
        console.log(`[launcher] WARNING: Backend not responding after ${timeout}s`);
        resolve();
        return;
      }
      fetch(url, { signal: AbortSignal.timeout(2e3) }).then(async (r) => {
        const text = await r.text();
        if (r.status === 200 && (text.includes("running") || text.includes("status"))) {
          console.log(`[launcher] Backend ready on port ${port}`);
          resolve();
        } else {
          setTimeout(check, 1e3);
        }
      }).catch(() => setTimeout(check, 1e3));
    }
    __name(check, "check");
    check();
  });
}
__name(waitForBackend, "waitForBackend");
function toUtf8(data) {
  try {
    const s = data.toString("utf-8");
    if (s.includes("\uFFFD")) throw new Error("replacement chars");
    return s;
  } catch {
    return data.toString("latin1");
  }
}
__name(toUtf8, "toUtf8");
function startBackend(projectRoot, port, wsPort) {
  const mvn = findMvn();
  const cmd = `"${mvn}" exec:java -pl jwcode-web -Dexec.mainClass=com.jwcode.web.WebLauncher "-Dexec.args=${port} ${wsPort}" -q`;
  console.log(`[launcher] Starting backend: ${mvn} exec:java ...`);
  killPort(port);
  killPort(wsPort);
  const proc = exec(cmd, {
    cwd: projectRoot,
    env: { ...process.env, JWCODE_WS_PORT: String(wsPort) },
    encoding: "buffer"
    // Get raw Buffer for manual decoding
  });
  let started = false;
  let bindError = false;
  proc.stderr?.on("data", (data) => {
    const msg = toUtf8(data).trim();
    if (!msg) return;
    if (msg.includes("Address already in use") || msg.includes("BindException")) {
      bindError = true;
      console.error(`[backend] ERROR: Port ${port} is in use.`);
    }
  });
  proc.stdout?.on("data", () => {
  });
  proc.on("exit", (code) => {
    if (!started && code !== 0 && code !== null) {
      console.error(`[launcher] Backend process exited with code ${code}`);
      if (bindError) {
        console.error("[launcher] Port conflict detected. Trying to kill stale process and retry...");
        killPort(port);
        killPort(wsPort);
      }
    }
  });
  return proc;
}
__name(startBackend, "startBackend");
function cleanupBackend(proc) {
  if (!proc) return;
  console.log("\n[jwcode] Shutting down...");
  try {
    process.kill(-proc.pid, "SIGTERM");
  } catch {
  }
  proc.kill("SIGTERM");
  setTimeout(() => {
    if (proc && !proc.killed) {
      try {
        process.kill(-proc.pid, "SIGKILL");
      } catch {
      }
      proc.kill("SIGKILL");
    }
  }, 5e3);
}
__name(cleanupBackend, "cleanupBackend");

// src/config.ts
import { readFileSync, existsSync as existsSync2 } from "node:fs";
import { join as join2 } from "node:path";
import { homedir as homedir2 } from "node:os";
var DEFAULTS = {
  backend_url: "http://localhost:8080",
  ws_url: "ws://localhost:8081/ws",
  ws_auth_token: "default-token"
};
function parseYaml(content) {
  const result = {};
  for (const line of content.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const colonIdx = trimmed.indexOf(":");
    if (colonIdx === -1) continue;
    const key = trimmed.slice(0, colonIdx).trim();
    let value = trimmed.slice(colonIdx + 1).trim();
    if (typeof value === "string") {
      if (value === "true" || value === "false") value = value === "true";
      else if (/^\d+$/.test(value)) value = parseInt(value);
      else {
        const quoted = value.match(/^["'](.*)["']$/);
        if (quoted) value = quoted[1];
      }
    }
    result[key] = value;
  }
  return result;
}
__name(parseYaml, "parseYaml");
function loadConfig() {
  const configDir = join2(homedir2(), ".jwcode");
  const configPath = join2(configDir, "config.yaml");
  if (!existsSync2(configPath)) {
    return { ...DEFAULTS };
  }
  try {
    const content = readFileSync(configPath, "utf-8");
    const parsed = parseYaml(content);
    return {
      backend_url: parsed.backend_url || DEFAULTS.backend_url,
      ws_url: parsed.ws_url || DEFAULTS.ws_url,
      ws_auth_token: parsed.ws_auth_token || DEFAULTS.ws_auth_token
    };
  } catch {
    return { ...DEFAULTS };
  }
}
__name(loadConfig, "loadConfig");

// src/main.ts
var VERSION = "3.0.0";
function printUsage() {
  console.log(`JWCode CLI v${VERSION}`);
  console.log("");
  console.log("Usage:");
  console.log("  jwcode start [options]    Start backend + interactive terminal");
  console.log("  jwcode run [options]      Connect to existing backend");
  console.log("  jwcode version            Print version");
  console.log("");
  console.log("Options:");
  console.log("  --port, -p <port>         Backend HTTP port (default: 8080)");
  console.log("  --ws-port <port>          WebSocket port (default: 8081)");
  console.log("  --build, -B               Force rebuild backend");
  console.log("  --backend, -b <url>       Backend URL (run mode)");
  console.log("  --ws <url>                WebSocket URL (run mode)");
}
__name(printUsage, "printUsage");
function parseArgs() {
  const args = {};
  const argv = process.argv.slice(2);
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--build" || arg === "-B") args.build = true;
    else if (arg === "--port" || arg === "-p") args.port = argv[++i];
    else if (arg === "--ws-port") args["ws-port"] = argv[++i];
    else if (arg === "--backend" || arg === "-b") args.backend = argv[++i];
    else if (arg === "--ws") args.ws = argv[++i];
    else if (!arg.startsWith("-")) args._cmd = arg;
  }
  return args;
}
__name(parseArgs, "parseArgs");
async function cmdStart(args) {
  const port = parseInt(String(args.port || "8080"), 10);
  const wsPort = parseInt(String(args["ws-port"] || "8081"), 10);
  const build = !!args.build;
  const root = findProjectRoot();
  console.log("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
  console.log("\u2551   JWCode \u2014 Java AI Coding Tool       \u2551");
  console.log("\u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D");
  console.log("");
  if (build || !jarExists(root)) {
    buildBackend(root);
  }
  const backendProc = startBackend(root, port, wsPort);
  let stopping = false;
  const cleanup = /* @__PURE__ */ __name(() => {
    if (stopping) return;
    stopping = true;
    console.log("\n[jwcode] Shutting down...");
    cleanupBackend(backendProc);
  }, "cleanup");
  process.on("SIGINT", () => {
    cleanup();
    process.exit(0);
  });
  process.on("SIGTERM", () => {
    cleanup();
    process.exit(0);
  });
  process.on("exit", cleanup);
  const backendDead = new Promise((resolve) => {
    backendProc.on("exit", (code) => {
      if (code !== 0 && code !== null) resolve();
    });
  });
  await Promise.race([waitForBackend(port), backendDead]);
  if (backendProc.exitCode !== null && backendProc.exitCode !== 0) {
    console.error("[launcher] Backend failed to start. Exiting.");
    cleanup();
    process.exit(1);
  }
  const backendUrl = `http://localhost:${port}`;
  const wsUrl = `ws://localhost:${wsPort}/ws`;
  const { unmount } = render(
    createElement(App, { backendUrl, wsUrl, onExit: /* @__PURE__ */ __name(() => {
      cleanup();
      process.exit(0);
    }, "onExit") })
  );
  await new Promise((resolve) => {
    process.on("SIGINT", () => resolve());
    process.on("SIGTERM", () => resolve());
  });
}
__name(cmdStart, "cmdStart");
async function cmdRun(args) {
  if (!process.stdin.isTTY) {
    console.error("Error: jwcode requires a real terminal (TTY).");
    console.error("Please run from a terminal emulator like Windows Terminal, CMD, or PowerShell.");
    process.exit(1);
  }
  const config = loadConfig();
  const backendUrl = String(args.backend || config.backend_url);
  const wsUrl = String(args.ws || config.ws_url);
  const { unmount, waitUntilExit } = render(
    createElement(App, {
      backendUrl,
      wsUrl,
      onExit: /* @__PURE__ */ __name(() => process.exit(0), "onExit")
    })
  );
  await waitUntilExit();
}
__name(cmdRun, "cmdRun");
async function main() {
  if (process.argv.includes("--help") || process.argv.includes("-h") || process.argv.includes("help")) {
    printUsage();
    return;
  }
  if (process.argv.includes("--version") || process.argv.includes("-v") || process.argv.includes("version")) {
    console.log(`JWCode CLI v${VERSION}`);
    return;
  }
  const args = parseArgs();
  const cmd = args._cmd || "start";
  switch (cmd) {
    case "start":
      await cmdStart(args);
      break;
    case "run":
      await cmdRun(args);
      break;
    default:
      console.error(`Unknown command: ${cmd}`);
      printUsage();
      process.exit(1);
  }
}
__name(main, "main");
main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(1);
});
