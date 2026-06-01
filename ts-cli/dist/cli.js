#!/usr/bin/env node
var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// src/main.ts
import { render } from "ink";
import { createElement } from "react";

// src/App.tsx
import { useState as useState6, useEffect as useEffect4, useRef as useRef4, useCallback as useCallback2 } from "react";
import { Box as Box6, Text as Text6, useInput as useInput4, useApp, useStdout as useStdout2 } from "ink";

// src/components/TextInput.tsx
import { useRef, useCallback } from "react";
import { Box, Text, useInput } from "ink";
import { jsx, jsxs } from "react/jsx-runtime";
function estimateTokens(text) {
  let cjk = 0;
  let other = 0;
  for (const ch of text) {
    if (/[一-鿿㐀-䶿豈-﫿　-〿＀-￯]/.test(ch)) {
      cjk++;
    } else {
      other++;
    }
  }
  return Math.ceil(cjk / 1.5 + other / 4);
}
__name(estimateTokens, "estimateTokens");
var MAX_HISTORY = 30;
var HISTORY_KEY = "jwcode-tscli-history";
function loadHistory() {
  try {
    const raw = process.env.JWCODE_HISTORY || (typeof sessionStorage !== "undefined" ? sessionStorage.getItem(HISTORY_KEY) : null);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}
__name(loadHistory, "loadHistory");
function saveHistory(entries) {
  try {
    if (typeof sessionStorage !== "undefined") {
      sessionStorage.setItem(HISTORY_KEY, JSON.stringify(entries));
    }
  } catch {
  }
}
__name(saveHistory, "saveHistory");
function saveToHistory(text) {
  const trimmed = text.trim();
  if (!trimmed) return;
  const history = loadHistory().filter((h) => h !== trimmed);
  history.unshift(trimmed);
  saveHistory(history.slice(0, MAX_HISTORY));
}
__name(saveToHistory, "saveToHistory");
function TextInput({ value, onChange, onSubmit, placeholder, disabled }) {
  const historyRef = useRef(loadHistory());
  const histIdxRef = useRef(-1);
  const draftRef = useRef("");
  const navigateHistory = useCallback((dir) => {
    const history = historyRef.current;
    if (history.length === 0) return null;
    if (histIdxRef.current === -1) {
      draftRef.current = value;
      if (dir === "up") {
        histIdxRef.current = 0;
        return history[0];
      }
      return null;
    }
    if (dir === "up") {
      const next = Math.min(histIdxRef.current + 1, history.length - 1);
      histIdxRef.current = next;
      return history[next];
    } else {
      const next = histIdxRef.current - 1;
      if (next < 0) {
        histIdxRef.current = -1;
        return draftRef.current;
      }
      histIdxRef.current = next;
      return history[next];
    }
  }, [value]);
  const resetHistory = useCallback(() => {
    histIdxRef.current = -1;
    draftRef.current = "";
  }, []);
  useInput((input, key) => {
    if (disabled) return;
    if (key.return) {
      onSubmit(value);
      resetHistory();
      return;
    }
    if (key.upArrow) {
      const hist = navigateHistory("up");
      if (hist !== null) onChange(hist);
      return;
    }
    if (key.downArrow) {
      const hist = navigateHistory("down");
      if (hist !== null) onChange(hist);
      return;
    }
    if (histIdxRef.current !== -1 && input) {
      resetHistory();
    }
    if (key.backspace || key.delete) {
      onChange(value.slice(0, -1));
      resetHistory();
    } else if (input && !key.ctrl && !key.meta && !key.tab && !key.escape) {
      onChange(value + input);
    }
  });
  const display = value || "";
  const showPlaceholder = !display && placeholder;
  const tokenEstimate = display ? estimateTokens(display) : 0;
  const charCount = display.length;
  return /* @__PURE__ */ jsxs(Box, { flexDirection: "column", children: [
    /* @__PURE__ */ jsxs(Box, { children: [
      display ? /* @__PURE__ */ jsx(Text, { children: display }) : /* @__PURE__ */ jsx(Text, { dimColor: true, children: placeholder }),
      /* @__PURE__ */ jsx(Text, { dimColor: true, children: "\u258A" })
    ] }),
    charCount > 0 && /* @__PURE__ */ jsxs(Box, { children: [
      /* @__PURE__ */ jsxs(Text, { dimColor: true, children: [
        "  ",
        charCount,
        " \u5B57\u7B26 \u2248 ",
        tokenEstimate,
        " tokens"
      ] }),
      tokenEstimate > 1e5 && /* @__PURE__ */ jsx(Text, { color: "red", children: "  \u26A0 \u63A5\u8FD1\u4E0A\u4E0B\u6587\u4E0A\u9650" })
    ] })
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
import { memo, useState as useState2, useEffect } from "react";
import { Box as Box2, Text as Text2 } from "ink";

// src/hooks/useAppState.ts
import { useSyncExternalStore, useRef as useRef2 } from "react";

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
  statusText: "connecting...",
  tokenRate: 0,
  toolCallsExpanded: false
};
var _store = null;
function getStore() {
  if (!_store) _store = createStore(initialState);
  return _store;
}
__name(getStore, "getStore");
var selCurrentMessage = /* @__PURE__ */ __name((s) => s.currentMessage, "selCurrentMessage");
var selPlanMode = /* @__PURE__ */ __name((s) => s.planMode, "selPlanMode");
var selPlanWaiting = /* @__PURE__ */ __name((s) => s.planWaiting, "selPlanWaiting");
var selConnected = /* @__PURE__ */ __name((s) => s.connected, "selConnected");
var selIsGenerating = /* @__PURE__ */ __name((s) => s.currentMessage !== null, "selIsGenerating");
var selToolCallsExpanded = /* @__PURE__ */ __name((s) => s.toolCallsExpanded, "selToolCallsExpanded");
var selChatArea = /* @__PURE__ */ __name((s) => ({
  messages: s.messages,
  currentMessage: s.currentMessage,
  scrollOffset: s.scrollOffset
}), "selChatArea");
var selStatusLine = /* @__PURE__ */ __name((s) => ({
  usage: s.usage,
  modelName: s.modelName,
  planMode: s.planMode,
  autoMode: s.autoMode,
  connected: s.connected,
  statusText: s.statusText,
  messagesLen: s.messages.length,
  tokenRate: s.tokenRate
}), "selStatusLine");
function shallowEqual(a, b) {
  if (Object.is(a, b)) return true;
  if (a === null || b === null) return false;
  if (typeof a !== "object" || typeof b !== "object") return false;
  const keysA = Object.keys(a);
  const keysB = Object.keys(b);
  if (keysA.length !== keysB.length) return false;
  return keysA.every(
    (k) => Object.is(
      a[k],
      b[k]
    )
  );
}
__name(shallowEqual, "shallowEqual");
function useAppSlice(selector) {
  const store = getStore();
  const cacheRef = useRef2(null);
  const getSnapshot = /* @__PURE__ */ __name(() => {
    const next = selector(store.getState());
    const cached = cacheRef.current;
    if (cached !== null && shallowEqual(next, cached.value)) {
      return cached.value;
    }
    cacheRef.current = { value: next };
    return next;
  }, "getSnapshot");
  return useSyncExternalStore(store.subscribe, getSnapshot, getSnapshot);
}
__name(useAppSlice, "useAppSlice");
var useAppCurrentMessage = /* @__PURE__ */ __name(() => useAppSlice(selCurrentMessage), "useAppCurrentMessage");
var useAppPlanMode = /* @__PURE__ */ __name(() => useAppSlice(selPlanMode), "useAppPlanMode");
var useAppPlanWaiting = /* @__PURE__ */ __name(() => useAppSlice(selPlanWaiting), "useAppPlanWaiting");
var useAppConnected = /* @__PURE__ */ __name(() => useAppSlice(selConnected), "useAppConnected");
var useAppIsGenerating = /* @__PURE__ */ __name(() => useAppSlice(selIsGenerating), "useAppIsGenerating");
var useAppChatArea = /* @__PURE__ */ __name(() => useAppSlice(selChatArea), "useAppChatArea");
var useAppStatusLine = /* @__PURE__ */ __name(() => useAppSlice(selStatusLine), "useAppStatusLine");
var useAppToolCallsExpanded = /* @__PURE__ */ __name(() => useAppSlice(selToolCallsExpanded), "useAppToolCallsExpanded");
function updateAppState(updater) {
  getStore().setState(updater);
}
__name(updateAppState, "updateAppState");

// src/components/StatusLine.tsx
import { Fragment, jsx as jsx2, jsxs as jsxs2 } from "react/jsx-runtime";
function formatTokens(n) {
  if (n >= 1e6) return `${(n / 1e6).toFixed(1)}M`;
  if (n >= 1e3) return `${Math.round(n / 1e3)}K`;
  return String(n);
}
__name(formatTokens, "formatTokens");
function formatRate(rate) {
  if (rate <= 0) return "";
  if (rate >= 100) return `${Math.round(rate)}t/s`;
  if (rate >= 10) return `${rate.toFixed(1)}t/s`;
  return `${rate.toFixed(1)}t/s`;
}
__name(formatRate, "formatRate");
function formatElapsed(sec) {
  if (sec <= 0) return "";
  if (sec >= 60) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}m${s}s`;
  }
  return `${sec}s`;
}
__name(formatElapsed, "formatElapsed");
var StatusLine = memo(/* @__PURE__ */ __name(function StatusLine2() {
  const {
    usage,
    modelName,
    planMode,
    autoMode,
    connected,
    statusText,
    messagesLen,
    tokenRate
  } = useAppStatusLine();
  const msgCount = messagesLen;
  const isGenerating = useAppIsGenerating();
  const currentMessage = useAppCurrentMessage();
  const [now, setNow] = useState2(Date.now());
  useEffect(() => {
    if (!currentMessage) return;
    const timer = setInterval(() => setNow(Date.now()), 1e3);
    return () => clearInterval(timer);
  }, [currentMessage?.id]);
  const generationElapsed = currentMessage ? Math.floor((now - (currentMessage.timestamp || Date.now())) / 1e3) : 0;
  const pct = Math.min(100, Math.round(usage.usageRatio * 100));
  const filled = Math.round(pct / 10);
  const bar = "\u2588".repeat(filled) + "\u2591".repeat(10 - filled);
  const model = modelName || (connected ? "ready" : "connecting...");
  const modeLabel = planMode ? " Plan " : " Act ";
  const modeColor = planMode ? "cyan" : "green";
  const connIcon = connected ? "\u25CF" : "\u25CB";
  const connColor = connected ? "green" : "red";
  const isError = statusText.startsWith("Error:");
  const barColor = pct > 90 ? "red" : pct > 70 ? "yellow" : "white";
  const rateStr = formatRate(tokenRate);
  const elapsedStr = formatElapsed(generationElapsed);
  const p = usage.promptTokens;
  const c = usage.completionTokens;
  return /* @__PURE__ */ jsxs2(Box2, { flexDirection: "column", width: "100%", paddingRight: 1, children: [
    /* @__PURE__ */ jsxs2(Box2, { height: 1, children: [
      /* @__PURE__ */ jsx2(Text2, { bold: true, color: "cyan", children: "jwcode" }),
      /* @__PURE__ */ jsx2(Text2, { children: "  " }),
      /* @__PURE__ */ jsxs2(Text2, { backgroundColor: modeColor, color: "black", children: [
        " ",
        modeLabel,
        " "
      ] }),
      /* @__PURE__ */ jsx2(Text2, { children: "  " }),
      autoMode && /* @__PURE__ */ jsxs2(Fragment, { children: [
        /* @__PURE__ */ jsx2(Text2, { backgroundColor: "magenta", color: "black", children: " AUTO " }),
        /* @__PURE__ */ jsx2(Text2, { children: "  " })
      ] }),
      /* @__PURE__ */ jsxs2(Text2, { color: connColor, children: [
        connIcon,
        " "
      ] }),
      /* @__PURE__ */ jsx2(Text2, { color: "green", children: model }),
      /* @__PURE__ */ jsx2(Text2, { children: "  " }),
      /* @__PURE__ */ jsxs2(Text2, { dimColor: true, children: [
        msgCount,
        "msgs"
      ] }),
      p > 0 || c > 0 ? /* @__PURE__ */ jsxs2(Fragment, { children: [
        /* @__PURE__ */ jsx2(Text2, { children: "  " }),
        /* @__PURE__ */ jsx2(Text2, { color: "blue", children: formatTokens(p) }),
        /* @__PURE__ */ jsx2(Text2, { dimColor: true, children: "+" }),
        /* @__PURE__ */ jsx2(Text2, { color: "green", children: formatTokens(c) }),
        /* @__PURE__ */ jsx2(Text2, { dimColor: true, children: "=" }),
        /* @__PURE__ */ jsx2(Text2, { color: "yellow", children: formatTokens(usage.totalTokens) })
      ] }) : /* @__PURE__ */ jsxs2(Fragment, { children: [
        /* @__PURE__ */ jsx2(Text2, { children: "  t:" }),
        /* @__PURE__ */ jsx2(Text2, { color: "yellow", children: formatTokens(usage.totalTokens) })
      ] }),
      /* @__PURE__ */ jsx2(Text2, { children: "  " }),
      /* @__PURE__ */ jsxs2(Text2, { color: barColor, children: [
        bar,
        " ",
        pct,
        "%"
      ] }),
      isGenerating && rateStr && /* @__PURE__ */ jsxs2(Fragment, { children: [
        /* @__PURE__ */ jsx2(Text2, { children: "  " }),
        /* @__PURE__ */ jsx2(Text2, { color: "magenta", children: rateStr })
      ] }),
      isGenerating && elapsedStr && /* @__PURE__ */ jsxs2(Fragment, { children: [
        /* @__PURE__ */ jsx2(Text2, { children: "  " }),
        /* @__PURE__ */ jsx2(Text2, { color: "cyan", children: elapsedStr })
      ] })
    ] }),
    statusText && statusText !== "connecting..." && /* @__PURE__ */ jsx2(Box2, { height: 1, children: /* @__PURE__ */ jsx2(Text2, { color: isError ? "red" : "grey", dimColor: !isError, children: statusText.slice(0, 100) }) })
  ] });
}, "StatusLine"));

// src/components/ChatArea.tsx
import { Box as Box3, Text as Text3 } from "ink";
import { useState as useState3, useMemo, memo as memo2 } from "react";
import { Fragment as Fragment2, jsx as jsx3, jsxs as jsxs3 } from "react/jsx-runtime";
var SEP = "\u2500".repeat(60);
var MAX_THINKING = 200;
function formatDuration(sec) {
  if (sec <= 0) return "";
  if (sec >= 60) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}m${s}s`;
  }
  return `${sec}s`;
}
__name(formatDuration, "formatDuration");
function shouldStartCollapsed(toolCalls, index) {
  const tc = toolCalls[index];
  if (!tc) return true;
  if (tc.status === "running") return false;
  let lastFinishedIdx = -1;
  for (let i = toolCalls.length - 1; i >= 0; i--) {
    if (toolCalls[i].status === "complete" || toolCalls[i].status === "error") {
      lastFinishedIdx = i;
      break;
    }
  }
  return index !== lastFinishedIdx;
}
__name(shouldStartCollapsed, "shouldStartCollapsed");
var ChatArea = memo2(/* @__PURE__ */ __name(function ChatArea2({ messages, currentMessage, scrollOffset, terminalRows, reservedRows, terminalCols, toolCallsExpanded }) {
  const [expandedTools, setExpandedTools] = useState3(/* @__PURE__ */ new Set());
  const [expandedMessages, setExpandedMessages] = useState3(/* @__PURE__ */ new Set());
  const allMessages = useMemo(
    () => currentMessage ? messages.filter((m) => m.id !== currentMessage.id) : messages,
    [messages, currentMessage?.id]
  );
  const { maxVisible, total, start, end, visibleMessages, isScrolledUp, scrollPercent } = useMemo(() => {
    const availableRows = Math.max(10, terminalRows - reservedRows);
    const linesPerMessage = availableRows > 60 ? 3 : availableRows > 30 ? 4 : 5;
    const maxVisible2 = Math.max(5, Math.floor(availableRows / linesPerMessage));
    const total2 = allMessages.length;
    const clampedOffset = Math.min(scrollOffset, Math.max(0, total2 - 1));
    const end2 = total2 - clampedOffset;
    const start2 = Math.max(0, end2 - maxVisible2);
    return {
      maxVisible: maxVisible2,
      total: total2,
      start: start2,
      end: end2,
      visibleMessages: allMessages.slice(start2, end2),
      isScrolledUp: clampedOffset > 0,
      scrollPercent: total2 > 0 ? Math.round((total2 - end2) / total2 * 100) : 0
    };
  }, [allMessages, scrollOffset, terminalRows, reservedRows]);
  const toggleExpandTool = /* @__PURE__ */ __name((id) => {
    setExpandedTools((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, "toggleExpandTool");
  const toggleExpandMessage = /* @__PURE__ */ __name((id) => {
    setExpandedMessages((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, "toggleExpandMessage");
  return /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", width: "100%", children: [
    total > 0 && /* @__PURE__ */ jsx3(Box3, { children: /* @__PURE__ */ jsxs3(Text3, { color: "grey", dimColor: true, children: [
      "[",
      start + 1,
      "-",
      end,
      "/",
      total,
      "] ",
      scrollPercent,
      "%",
      isScrolledUp ? " \u25B2 PgUp/PgDn\xB7Home\xB7End" : " \u2191/PgUp for older"
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
        msg.thinking && /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", children: [
          /* @__PURE__ */ jsx3(Text3, { dimColor: true, italic: true, children: expandedMessages.has(msg.id) ? msg.thinking : truncate(msg.thinking, MAX_THINKING) }),
          msg.thinking.length > MAX_THINKING && /* @__PURE__ */ jsxs3(Text3, { color: "blue", dimColor: true, children: [
            "[",
            msg.thinking.length - MAX_THINKING,
            " more chars]",
            /* @__PURE__ */ jsxs3(Text3, { color: "cyan", dimColor: true, children: [
              " ",
              "[\u2193 to expand]"
            ] })
          ] })
        ] }),
        msg.toolCalls.map((tc, i) => {
          const key = tc.id || `${msg.id}-${i}`;
          const startCollapsed = shouldStartCollapsed(msg.toolCalls, i);
          const isExpanded = expandedTools.has(key);
          return /* @__PURE__ */ jsx3(
            ToolCallDisplay,
            {
              tc,
              collapsed: toolCallsExpanded ? false : isExpanded ? false : startCollapsed,
              onToggle: () => toggleExpandTool(key)
            },
            key
          );
        }),
        msg.content && /* @__PURE__ */ jsx3(Text3, { children: msg.content }),
        /* @__PURE__ */ jsx3(Text3, { dimColor: true, children: SEP })
      ] }),
      msg.type === "system" && /* @__PURE__ */ jsx3(Box3, { children: /* @__PURE__ */ jsxs3(Text3, { color: "red", children: [
        "Error: ",
        msg.content
      ] }) })
    ] }, msg.id)),
    currentMessage && /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", children: [
      currentMessage.thinking && /* @__PURE__ */ jsx3(Text3, { dimColor: true, italic: true, children: truncate(currentMessage.thinking, MAX_THINKING) }),
      currentMessage.toolCalls.map((tc, i) => {
        const key = tc.id || `${currentMessage.id}-${i}`;
        const startCollapsed = shouldStartCollapsed(currentMessage.toolCalls, i);
        const isExpanded = expandedTools.has(key);
        return /* @__PURE__ */ jsx3(
          ToolCallDisplay,
          {
            tc,
            collapsed: toolCallsExpanded ? false : isExpanded ? false : startCollapsed,
            onToggle: () => toggleExpandTool(key)
          },
          key
        );
      }),
      currentMessage.content && /* @__PURE__ */ jsx3(Text3, { children: currentMessage.content })
    ] }, currentMessage.id)
  ] });
}, "ChatArea"));
function StepDisplay({ step }) {
  const icon = step.status === "success" ? "\u2713" : step.status === "error" ? "\u2717" : step.status === "running" ? "\u27F3" : "\u25B6";
  const color = step.status === "success" ? "green" : step.status === "error" ? "red" : step.status === "running" ? "cyan" : "cyan";
  const durStr = step.duration ? formatDuration(step.duration) : step.status === "running" && step.timestamp ? formatDuration(Math.floor((Date.now() - step.timestamp) / 1e3)) : "";
  return /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", children: [
    /* @__PURE__ */ jsxs3(Box3, { children: [
      /* @__PURE__ */ jsxs3(Text3, { color, children: [
        "  ",
        icon,
        " "
      ] }),
      /* @__PURE__ */ jsx3(Text3, { bold: true, color, children: step.title }),
      durStr && /* @__PURE__ */ jsxs3(Fragment2, { children: [
        /* @__PURE__ */ jsx3(Text3, { dimColor: true, children: "  " }),
        /* @__PURE__ */ jsx3(Text3, { color: "grey", dimColor: true, children: durStr })
      ] })
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
function ToolCallDisplay({ tc, collapsed, onToggle }) {
  const statusIcon = tc.status === "complete" ? "\u2713" : tc.status === "running" ? "\u27F3" : "\u2717";
  const statusColor = tc.status === "complete" ? "green" : tc.status === "running" ? "yellow" : "red";
  const durStr = tc.duration ? formatDuration(tc.duration) : tc.status === "running" && tc.timestamp ? formatDuration(Math.floor((Date.now() - tc.timestamp) / 1e3)) : "";
  return /* @__PURE__ */ jsxs3(Box3, { flexDirection: "column", paddingLeft: 1, children: [
    /* @__PURE__ */ jsxs3(Box3, { children: [
      /* @__PURE__ */ jsxs3(Text3, { color: statusColor, children: [
        "  ",
        statusIcon,
        " "
      ] }),
      /* @__PURE__ */ jsx3(Text3, { bold: true, color: "magenta", children: tc.name }),
      durStr && /* @__PURE__ */ jsxs3(Fragment2, { children: [
        /* @__PURE__ */ jsx3(Text3, { dimColor: true, children: "  " }),
        /* @__PURE__ */ jsx3(Text3, { color: "grey", dimColor: true, children: durStr })
      ] }),
      /* @__PURE__ */ jsx3(Text3, { dimColor: true, children: "  " }),
      /* @__PURE__ */ jsxs3(Text3, { color: "blue", dimColor: true, children: [
        "[",
        collapsed ? "+" : "-",
        "]"
      ] })
    ] }),
    !collapsed && tc.args && /* @__PURE__ */ jsx3(Box3, { paddingLeft: 4, children: /* @__PURE__ */ jsx3(Text3, { dimColor: true, children: truncate(formatJson(tc.args), 200) }) }),
    tc.result && /* @__PURE__ */ jsx3(Box3, { paddingLeft: 4, flexDirection: "column", children: /* @__PURE__ */ jsx3(Text3, { color: tc.status === "error" ? "red" : "green", dimColor: true, children: tc.result }) })
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
import { useState as useState4, useMemo as useMemo2, useEffect as useEffect2 } from "react";
import { Box as Box4, Text as Text4, useInput as useInput2, useStdout } from "ink";

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
\u2551  \u5FEB\u6377\u952E:                                  \u2551
\u2551  \u2191\u2193           \u6D4F\u89C8\u8F93\u5165\u5386\u53F2 (\u6700\u8FD130\u6761)     \u2551
\u2551  PgUp/PgDn    \u7FFB\u9875\u6D4F\u89C8\u6D88\u606F                \u2551
\u2551  Home/End     \u8DF3\u5230\u6700\u65E9/\u6700\u65B0\u6D88\u606F           \u2551
\u2551  Tab          \u5207\u6362 Plan/Act \u6A21\u5F0F          \u2551
\u2551  /            \u6253\u5F00\u547D\u4EE4\u9762\u677F (\u53EF\u7FFB\u9875)        \u2551
\u2551  Esc          \u5173\u95ED\u9762\u677F/\u53D6\u6D88\u5BA1\u6279            \u2551
\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563
\u2551  \u666E\u901A\u8F93\u5165\u5373\u53D1\u9001\u804A\u5929\u6D88\u606F                   \u2551
\u2551  \u8F93\u5165\u6846\u663E\u793A\u5B57\u7B26\u6570+token\u4F30\u7B97               \u2551
\u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D`;

// src/components/CommandPalette.tsx
import { jsx as jsx4, jsxs as jsxs4 } from "react/jsx-runtime";
function CommandPalette({ filter, onSelect }) {
  const [selected, setSelected] = useState4(0);
  const [scrollOffset, setScrollOffset] = useState4(0);
  const { stdout } = useStdout();
  const terminalRows = stdout?.rows || 24;
  const visible = useMemo2(() => {
    const f = filter.replace(/^\//, "").toLowerCase();
    if (!f) return ALL_COMMANDS;
    return ALL_COMMANDS.filter(
      (c) => c.cmd.toLowerCase().includes(f) || c.desc.includes(f)
    );
  }, [filter]);
  useEffect2(() => {
    setSelected(0);
    setScrollOffset(0);
  }, [filter]);
  const maxShow = Math.max(5, terminalRows - 13);
  useEffect2(() => {
    setScrollOffset((prev) => {
      if (selected < prev) return selected;
      if (selected >= prev + maxShow) return selected - maxShow + 1;
      return prev;
    });
  }, [selected, maxShow]);
  const sliced = visible.slice(scrollOffset, scrollOffset + maxShow);
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
    if (key.pageDown) {
      setSelected((prev) => Math.min(prev + maxShow, visible.length - 1));
      return;
    }
    if (key.pageUp) {
      setSelected((prev) => Math.max(prev - maxShow, 0));
      return;
    }
    if (key.home) {
      setSelected(0);
      return;
    }
    if (key.end) {
      setSelected(visible.length - 1);
      return;
    }
    if (key.return) {
      if (visible.length > 0 && selected >= 0 && selected < visible.length) {
        onSelect(visible[selected].cmd);
      }
    }
  });
  return /* @__PURE__ */ jsxs4(Box4, { flexDirection: "column", borderStyle: "single", borderColor: "cyan", paddingX: 1, width: 52, children: [
    /* @__PURE__ */ jsxs4(Box4, { children: [
      /* @__PURE__ */ jsx4(Text4, { bold: true, color: "cyan", children: "\u547D\u4EE4\u5217\u8868" }),
      /* @__PURE__ */ jsx4(Text4, { dimColor: true, children: "  \u2191\u2193\u9009\u62E9 / PgUp/PgDn\u7FFB\u9875 / \u56DE\u8F66\u786E\u8BA4 / Esc\u53D6\u6D88" })
    ] }),
    sliced.map((cmd, i) => {
      const idx = scrollOffset + i;
      return /* @__PURE__ */ jsxs4(Box4, { paddingLeft: 1, children: [
        /* @__PURE__ */ jsx4(Text4, { color: idx === selected ? "cyan" : void 0, bold: idx === selected, children: idx === selected ? "> " : "  " }),
        /* @__PURE__ */ jsx4(Text4, { color: "green", children: cmd.cmd }),
        /* @__PURE__ */ jsxs4(Text4, { dimColor: true, children: [
          "  ",
          cmd.desc
        ] }),
        /* @__PURE__ */ jsxs4(Text4, { color: cmd.via === "ws" ? "yellow" : "blue", dimColor: idx !== selected, children: [
          "(",
          cmd.via === "ws" ? "\u540E\u7AEF" : "\u672C\u5730",
          ")"
        ] })
      ] }, cmd.cmd);
    }),
    visible.length > maxShow && /* @__PURE__ */ jsx4(Box4, { children: /* @__PURE__ */ jsxs4(Text4, { dimColor: true, children: [
      "  ",
      scrollOffset + 1,
      "-",
      Math.min(scrollOffset + maxShow, visible.length),
      " / ",
      visible.length
    ] }) })
  ] });
}
__name(CommandPalette, "CommandPalette");

// src/components/ApprovalModal.tsx
import { useState as useState5, useEffect as useEffect3, useRef as useRef3 } from "react";
import { Box as Box5, Text as Text5, useInput as useInput3 } from "ink";
import { jsx as jsx5, jsxs as jsxs5 } from "react/jsx-runtime";
var COUNTDOWN_S = 15;
function classifyRisk(toolName, payload) {
  const name = toolName.toLowerCase();
  if (/\b(rm|del|delete|drop|truncate|format|mkfs)\b/.test(name)) {
    return { level: "CRITICAL", reason: "Destructive \u2014 may delete data" };
  }
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/.test(name)) {
    if (/\b(rm\s+-rf|sudo|chmod\s+777|curl.*\|\s*(ba)?sh|wget.*-O|>\/dev\/|mkfs)\b/i.test(payload)) {
      return { level: "CRITICAL", reason: "High-risk command \u2014 system-level operation" };
    }
    return { level: "HIGH", reason: "Shell command execution" };
  }
  if (/\b(write|edit|save|upload|deploy|publish)\b/i.test(name)) {
    return { level: "HIGH", reason: "File write operation" };
  }
  if (/\b(install|uninstall|npm|pip|cargo|gem|apt|brew)\b/i.test(name)) {
    return { level: "HIGH", reason: "Package manager operation" };
  }
  if (/\b(git)\b/.test(name) && /\b(push|force|hard\s*reset|rebase)\b/i.test(payload)) {
    return { level: "HIGH", reason: "Git destructive operation" };
  }
  if (/\b(http|fetch|curl|wget|api|request|download)\b/i.test(name)) {
    return { level: "MEDIUM", reason: "Network request" };
  }
  if (/\b(read|open|list|ls|dir|cat|view|search|find|grep|glob)\b/i.test(name)) {
    return { level: "LOW", reason: "Read-only operation" };
  }
  return { level: "MEDIUM", reason: "Tool invocation" };
}
__name(classifyRisk, "classifyRisk");
var RISK_COLOR = {
  CRITICAL: "red",
  HIGH: "yellow",
  MEDIUM: "yellow",
  LOW: "cyan"
};
var RISK_ICON = {
  CRITICAL: "\u26D4",
  HIGH: "\u26A0\uFE0F",
  MEDIUM: "\u26A1",
  LOW: "\u{1F4CB}"
};
function extractPreview(toolName, payload) {
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/i.test(toolName)) {
    return payload.slice(0, 200);
  }
  if (/\b(write|edit|save|create)\b/i.test(toolName)) {
    const match = payload.match(/(?:file_path|path|file)["\s:=]+([^\s",}]+)/i);
    if (match) return `File: ${match[1]}
${payload.slice(0, 160)}`;
  }
  return payload.length > 200 ? payload.slice(0, 200) + "..." : payload;
}
__name(extractPreview, "extractPreview");
function ApprovalModal({ toolName, payload, onAllow, onDeny }) {
  const [selected, setSelected] = useState5(0);
  const [countdown, setCountdown] = useState5(COUNTDOWN_S);
  const countdownRef = useRef3(null);
  const { level, reason } = classifyRisk(toolName, payload);
  const riskColor = RISK_COLOR[level];
  const riskIcon = RISK_ICON[level];
  const preview = extractPreview(toolName, payload);
  useEffect3(() => {
    setCountdown(COUNTDOWN_S);
    countdownRef.current = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          if (countdownRef.current) clearInterval(countdownRef.current);
          return 0;
        }
        return prev - 1;
      });
    }, 1e3);
    return () => {
      if (countdownRef.current) clearInterval(countdownRef.current);
    };
  }, [toolName, payload]);
  useEffect3(() => {
    if (countdown === 0) onAllow();
  }, [countdown]);
  const countdownUrgent = countdown <= 5;
  useInput3((_input, key) => {
    if (key.escape) {
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
  const borderColor = level === "CRITICAL" ? "red" : level === "HIGH" ? "yellow" : "yellow";
  return /* @__PURE__ */ jsxs5(Box5, { flexDirection: "column", borderStyle: "round", borderColor, paddingX: 2, paddingY: 1, marginTop: 1, children: [
    /* @__PURE__ */ jsxs5(Box5, { marginBottom: 1, children: [
      /* @__PURE__ */ jsxs5(Text5, { color: riskColor, children: [
        riskIcon,
        " "
      ] }),
      /* @__PURE__ */ jsx5(Text5, { bold: true, color: riskColor, children: level }),
      /* @__PURE__ */ jsxs5(Text5, { dimColor: true, children: [
        " \u2014 ",
        reason
      ] })
    ] }),
    /* @__PURE__ */ jsxs5(Box5, { marginBottom: 1, children: [
      /* @__PURE__ */ jsx5(Text5, { dimColor: true, children: "Tool: " }),
      /* @__PURE__ */ jsx5(Text5, { color: "cyan", bold: true, children: toolName })
    ] }),
    preview ? /* @__PURE__ */ jsxs5(Box5, { flexDirection: "column", marginBottom: 1, paddingX: 1, children: [
      /* @__PURE__ */ jsx5(Box5, { children: /* @__PURE__ */ jsx5(Text5, { dimColor: true, children: "\u250C\u2500 preview \u2500".slice(0, preview.length > 0 ? 12 : 0) }) }),
      /* @__PURE__ */ jsxs5(Box5, { children: [
        /* @__PURE__ */ jsx5(Text5, { dimColor: true, children: "\u2502 " }),
        /* @__PURE__ */ jsx5(Text5, { children: preview })
      ] }),
      /* @__PURE__ */ jsx5(Box5, { children: /* @__PURE__ */ jsx5(Text5, { dimColor: true, children: "\u2514" + "\u2500".repeat(Math.min(10, preview.length)) }) })
    ] }) : null,
    level === "CRITICAL" && /* @__PURE__ */ jsx5(Box5, { marginBottom: 1, children: /* @__PURE__ */ jsx5(Text5, { color: "red", bold: true, children: "\u26A0 This may cause irreversible changes \u2014 verify carefully" }) }),
    /* @__PURE__ */ jsxs5(Box5, { flexDirection: "column", marginLeft: 2, marginBottom: 1, children: [
      /* @__PURE__ */ jsx5(Box5, { children: /* @__PURE__ */ jsxs5(Text5, { color: selected === 0 ? "green" : void 0, bold: selected === 0, children: [
        selected === 0 ? " \u276F" : "  ",
        " 1. Allow"
      ] }) }),
      /* @__PURE__ */ jsx5(Box5, { children: /* @__PURE__ */ jsxs5(Text5, { color: selected === 1 ? "red" : void 0, bold: selected === 1, children: [
        selected === 1 ? " \u276F" : "  ",
        " 2. Deny"
      ] }) })
    ] }),
    /* @__PURE__ */ jsxs5(Box5, { marginBottom: 1, children: [
      /* @__PURE__ */ jsx5(Text5, { dimColor: true, children: "Auto-approve in: " }),
      /* @__PURE__ */ jsxs5(Text5, { color: countdownUrgent ? "red" : "green", bold: countdownUrgent, children: [
        countdown,
        "s"
      ] }),
      /* @__PURE__ */ jsx5(Text5, { children: "  " }),
      /* @__PURE__ */ jsxs5(Text5, { color: countdownUrgent ? "red" : "green", children: [
        "\u2588".repeat(Math.ceil(countdown / COUNTDOWN_S * 10)),
        "\u2591".repeat(10 - Math.ceil(countdown / COUNTDOWN_S * 10))
      ] })
    ] }),
    /* @__PURE__ */ jsx5(Box5, { children: /* @__PURE__ */ jsx5(Text5, { dimColor: true, children: "1/2/y/n to decide \xB7 \u2191\u2193 to select \xB7 Enter to confirm \xB7 Esc to deny" }) })
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
  const [input, setInput] = useState6("");
  const [showPalette, setShowPalette] = useState6(false);
  const [showHelp, setShowHelp] = useState6(false);
  const [helpScroll, setHelpScroll] = useState6(0);
  const [showApproval, setShowApproval] = useState6(null);
  const { exit } = useApp();
  const connected = useAppConnected();
  const planMode = useAppPlanMode();
  const planWaiting = useAppPlanWaiting();
  const currentMessage = useAppCurrentMessage();
  const isGenerating = useAppIsGenerating();
  const chatAreaProps = useAppChatArea();
  const toolCallsExpanded = useAppToolCallsExpanded();
  const clientRef = useRef4(null);
  const lastEscRef = useRef4(0);
  const currentMessageRef = useRef4(currentMessage);
  currentMessageRef.current = currentMessage;
  const planModeRef = useRef4(planMode);
  planModeRef.current = planMode;
  const { stdout } = useStdout2();
  const terminalRows = stdout?.rows || 24;
  const terminalCols = stdout?.columns || 80;
  const reservedRows = 6;
  const hline = "\u2500".repeat(terminalCols);
  useEffect4(() => {
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
        setHelpScroll(0);
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
          updateAppState((prev) => ({
            ...prev,
            messages: [],
            currentMessage: null
          }));
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
    saveToHistory(text);
    const msg = createMessage("user", text);
    updateAppState((prev) => ({ ...prev, messages: [...prev.messages, msg] }));
    clientRef.current.chat(text, planModeRef.current);
  }, [onExit]);
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
    let _pendingContent = "";
    let _pendingThinking = "";
    let _pendingToolFns = [];
    let _flushScheduled = false;
    function doStreamFlush() {
      _flushScheduled = false;
      const c = _pendingContent;
      _pendingContent = "";
      const t = _pendingThinking;
      _pendingThinking = "";
      const fns = _pendingToolFns;
      _pendingToolFns = [];
      if (!c && !t && fns.length === 0) return;
      updateAppState((prev) => {
        if (!prev.currentMessage) return prev;
        let msg = prev.currentMessage;
        if (c) msg = { ...msg, content: msg.content + c };
        if (t) msg = { ...msg, thinking: msg.thinking + t };
        for (const fn of fns) msg = fn(msg);
        return { ...prev, currentMessage: msg };
      });
    }
    __name(doStreamFlush, "doStreamFlush");
    function scheduleStreamFlush() {
      if (_flushScheduled) return;
      _flushScheduled = true;
      queueMicrotask(doStreamFlush);
      setTimeout(() => {
        if (_flushScheduled) doStreamFlush();
      }, 16);
    }
    __name(scheduleStreamFlush, "scheduleStreamFlush");
    function flushNow() {
      doStreamFlush();
    }
    __name(flushNow, "flushNow");
    let _lastTotal = 0;
    let _lastTotalTs = 0;
    let _firstTokenUpdate = true;
    let _pendingToken = null;
    let _tokenScheduled = false;
    function flushToken() {
      _tokenScheduled = false;
      const d = _pendingToken;
      if (!d) return;
      _pendingToken = null;
      const promptTokens = Number(d.promptTokens) || 0;
      const completionTokens = Number(d.completionTokens) || 0;
      const totalTokens = Number(d.totalTokens) || 0;
      const usageRatio = Number(d.usageRatio) || 0;
      if (totalTokens <= 0) return;
      const now = Date.now();
      let tokenRate = 0;
      if (_lastTotalTs > 0 && _lastTotal > 0 && now > _lastTotalTs && totalTokens > _lastTotal) {
        const deltaTokens = totalTokens - _lastTotal;
        const deltaSec = (now - _lastTotalTs) / 1e3;
        const instantRate = deltaTokens / deltaSec;
        const prevRate = getStore().getState().tokenRate;
        tokenRate = prevRate > 0 ? prevRate * 0.6 + instantRate * 0.4 : instantRate;
      }
      _lastTotal = totalTokens;
      _lastTotalTs = now;
      updateAppState((prev) => ({
        ...prev,
        usage: { promptTokens, completionTokens, totalTokens, usageRatio },
        modelName: d.model || prev.modelName,
        tokenRate
      }));
    }
    __name(flushToken, "flushToken");
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
      _pendingContent += text;
      scheduleStreamFlush();
    });
    client.on("thinking", (m) => {
      _pendingThinking += typeof m.data === "string" ? m.data : "";
      scheduleStreamFlush();
    });
    client.on("tool_call", (m) => {
      const d = parseData(m);
      _pendingToolFns.push((msg) => {
        let existingIdx = d.id ? msg.toolCalls.findIndex((t) => t.id === d.id) : -1;
        if (existingIdx < 0 && d.name) {
          existingIdx = msg.toolCalls.findIndex(
            (t) => t.name === d.name && t.status === "running"
          );
        }
        const tcs = [...msg.toolCalls];
        if (existingIdx >= 0) {
          const existing = { ...tcs[existingIdx] };
          if (d.args) existing.args = cleanArgs(d.args);
          if (d.complete) existing.status = "complete";
          if (d.result) existing.result = d.result;
          tcs[existingIdx] = existing;
        } else {
          tcs.push({
            id: d.id || (d.name ? `${d.name}-${Date.now()}` : ""),
            name: d.name || "",
            args: d.args ? cleanArgs(d.args) : void 0,
            status: d.complete ? "complete" : "running",
            complete: !!d.complete,
            timestamp: Date.now()
          });
        }
        return { ...msg, toolCalls: tcs };
      });
      scheduleStreamFlush();
    });
    client.on("tool_result", (m) => {
      const d = parseData(m);
      _pendingToolFns.push((msg) => {
        const tcs = [...msg.toolCalls];
        for (let i = tcs.length - 1; i >= 0; i--) {
          if (tcs[i].name === d.toolName && !tcs[i].result) {
            const tc = tcs[i];
            const duration = tc.timestamp ? Math.floor((Date.now() - tc.timestamp) / 1e3) : void 0;
            tcs[i] = { ...tc, result: d.result || "", status: "complete", duration };
            break;
          }
        }
        return { ...msg, toolCalls: tcs };
      });
      scheduleStreamFlush();
    });
    client.on("complete", () => {
      flushNow();
      updateAppState((prev) => {
        if (!prev.currentMessage) return prev;
        const msgs = [...prev.messages];
        const cm = prev.currentMessage;
        for (let i = msgs.length - 1; i >= 0; i--) {
          if (msgs[i].type === "assistant" && msgs[i].id === cm.id) {
            msgs[i] = cm;
            break;
          }
        }
        return { ...prev, currentMessage: null, messages: msgs };
      });
    });
    client.on("error", (m) => {
      const text = String(m.data || "Error");
      updateAppState((prev) => ({
        ...prev,
        statusText: `Error: ${text.slice(0, 120)}`
      }));
    });
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
      if (_firstTokenUpdate) {
        _firstTokenUpdate = false;
        console.log("[token_update] raw data type:", typeof m.data, "| parsed keys:", Object.keys(d).join(","));
      }
      const totalTokens = Number(d.totalTokens) || 0;
      if (totalTokens > 0) {
        _pendingToken = d;
        if (!_tokenScheduled) {
          _tokenScheduled = true;
          setTimeout(flushToken, 100);
        }
      }
    });
    client.on("context_compressed", (m) => {
      let d = {};
      if (typeof m.data === "string") {
        try {
          d = JSON.parse(m.data);
        } catch {
        }
      } else if (m.data && typeof m.data === "object") {
        d = m.data;
      }
      const orig = Number(d.originalCount) || 0;
      const comp = Number(d.compressedCount) || 0;
      const saved = Number(d.tokensSaved) || 0;
      const tokensStr = saved >= 1e3 ? `${(saved / 1e3).toFixed(1)}K` : String(saved);
      updateAppState((prev) => ({
        ...prev,
        statusText: `\u4E0A\u4E0B\u6587\u538B\u7F29: ${orig}\u2192${comp} \u6761\u6D88\u606F, \u91CA\u653E ${tokensStr} tokens`,
        usage: { ...prev.usage, usageRatio: Math.max(0, prev.usage.usageRatio - 0.15) }
      }));
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
      flushNow();
      const msg = createMessage("assistant");
      updateAppState((prev) => ({
        ...prev,
        planWaiting: false,
        currentMessage: msg,
        messages: [...prev.messages, msg],
        scrollOffset: prev.scrollOffset > 0 ? prev.scrollOffset + 1 : 0
      }));
    });
    client.on("plan_thinking", (m) => {
      const text = typeof m.data === "string" ? m.data : m.data ? String(m.data) : "";
      _pendingThinking += text + "\n";
      scheduleStreamFlush();
    });
    client.on("plan_tasks", () => {
      _pendingContent += "\n\u{1F4CB} \u4EFB\u52A1\u6E05\u5355\u5DF2\u751F\u6210\n";
      scheduleStreamFlush();
    });
    client.on("plan_complete", (m) => {
      flushNow();
      const status = m.status;
      const planText = typeof m.data === "string" ? m.data : "";
      updateAppState((prev) => {
        if (!prev.currentMessage) return prev;
        const msgs = [...prev.messages];
        const cm = prev.currentMessage;
        for (let i = msgs.length - 1; i >= 0; i--) {
          if (msgs[i].type === "assistant" && msgs[i].id === cm.id) {
            msgs[i] = { ...cm, content: planText || "Plan complete." };
            break;
          }
        }
        return {
          ...prev,
          currentMessage: null,
          messages: msgs,
          planWaiting: status === "waiting_confirm"
        };
      });
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
    if (key.escape) {
      if (showApproval) {
        handleApprovalDeny(showApproval.approvalId);
        return;
      }
      if (showHelp) {
        setShowHelp(false);
        return;
      }
      if (isGenerating) {
        const now = Date.now();
        const prev = lastEscRef.current;
        lastEscRef.current = now;
        if (prev > 0 && now - prev < 500) {
          clientRef.current?.stop();
          updateAppState((prev2) => ({ ...prev2, statusText: "\u23F9 \u5DF2\u7EC8\u6B62 (ESC\xD72)" }));
        } else {
          clientRef.current?.pause();
          updateAppState((prev2) => ({ ...prev2, statusText: "\u23F8 \u5DF2\u6682\u505C \u2014 \u518D\u6309 ESC \u7EC8\u6B62" }));
        }
        return;
      }
    }
    if (showHelp) {
      const helpLines = HELP_TEXT.split("\n");
      const helpMax = Math.max(5, terminalRows - 12);
      if (key.pageUp || key.upArrow) {
        setHelpScroll((prev) => Math.min(prev + (key.pageUp ? helpMax : 1), helpLines.length - 1));
        return;
      }
      if (key.pageDown || key.downArrow) {
        setHelpScroll((prev) => Math.max(0, prev - (key.pageDown ? helpMax : 1)));
        return;
      }
      if (key.home) {
        setHelpScroll(helpLines.length - 1);
        return;
      }
      if (key.end) {
        setHelpScroll(0);
        return;
      }
    }
    if (key.ctrl && input2 === "e") {
      updateAppState((prev) => ({
        ...prev,
        toolCallsExpanded: !prev.toolCallsExpanded
      }));
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
    if (key.home || key.home && key.ctrl) {
      updateAppState((prev) => ({
        ...prev,
        scrollOffset: prev.messages.length
      }));
      return;
    }
    if (key.end || key.end && key.ctrl) {
      updateAppState((prev) => ({
        ...prev,
        scrollOffset: 0
      }));
      return;
    }
    if (key.tab) {
      updateAppState((prev) => ({ ...prev, planMode: !prev.planMode, planWaiting: false }));
      return;
    }
  });
  const placeholder = "";
  return /* @__PURE__ */ jsxs6(Box6, { flexDirection: "column", width: "100%", height: "100%", children: [
    /* @__PURE__ */ jsx6(Box6, { flexGrow: 1, flexDirection: "column", children: /* @__PURE__ */ jsx6(ChatArea, { messages: chatAreaProps.messages, currentMessage: chatAreaProps.currentMessage, scrollOffset: chatAreaProps.scrollOffset, terminalRows, reservedRows, terminalCols, toolCallsExpanded }) }),
    /* @__PURE__ */ jsxs6(Box6, { flexDirection: "row", borderStyle: "single", borderColor: connected ? "cyan" : "red", paddingLeft: 1, children: [
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
          color: planMode ? "cyan" : "grey",
          bold: planMode,
          dimColor: !planMode,
          children: planMode ? "\u25C9 Plan" : "\u25CB Plan"
        }
      ),
      /* @__PURE__ */ jsx6(Text6, { children: "  " }),
      /* @__PURE__ */ jsx6(
        Text6,
        {
          color: !planMode ? "green" : "grey",
          bold: !planMode,
          dimColor: planMode,
          children: !planMode ? "\u25C9 Act" : "\u25CB Act"
        }
      ),
      /* @__PURE__ */ jsx6(Text6, { dimColor: true, children: "  Tab \u5207\u6362" })
    ] }),
    !connected && /* @__PURE__ */ jsxs6(Box6, { children: [
      /* @__PURE__ */ jsx6(Text6, { color: "red", children: "\u540E\u7AEF\u672A\u8FDE\u63A5 \u2014 WebSocket \u91CD\u8BD5\u4E2D\u3002\u5982\u540E\u7AEF\u672A\u542F\u52A8\u8BF7\u7528 " }),
      /* @__PURE__ */ jsx6(Text6, { color: "yellow", bold: true, children: "npm start" })
    ] }),
    planWaiting && /* @__PURE__ */ jsx6(Box6, { children: /* @__PURE__ */ jsx6(Text6, { color: "yellow", bold: true, children: "Plan ready \u2014 /confirm to execute, /cancel to discard." }) }),
    showPalette && /* @__PURE__ */ jsx6(CommandPalette, { filter: input, onSelect: handlePaletteSelect }),
    showHelp && (() => {
      const helpLines = HELP_TEXT.split("\n");
      const helpMax = Math.max(5, terminalRows - 12);
      const helpEnd = Math.max(0, helpLines.length - helpScroll);
      const helpStart = Math.max(0, helpEnd - helpMax);
      const visibleHelp = helpLines.slice(helpStart, helpEnd);
      return /* @__PURE__ */ jsxs6(Box6, { flexDirection: "column", borderStyle: "single", borderColor: "cyan", paddingX: 1, children: [
        helpLines.length > helpMax && /* @__PURE__ */ jsx6(Box6, { children: /* @__PURE__ */ jsxs6(Text6, { dimColor: true, children: [
          "  ",
          helpStart + 1,
          "-",
          helpEnd,
          " / ",
          helpLines.length,
          "  PgUp/PgDn\u7FFB\u9875 / Esc\u5173\u95ED"
        ] }) }),
        visibleHelp.map((line, i) => /* @__PURE__ */ jsx6(Text6, { color: "cyan", children: line }, i))
      ] });
    })(),
    showApproval && /* @__PURE__ */ jsx6(
      ApprovalModal,
      {
        toolName: showApproval.toolName,
        payload: showApproval.payload,
        onAllow: () => handleApprovalAllow(showApproval.approvalId),
        onDeny: () => handleApprovalDeny(showApproval.approvalId)
      }
    ),
    /* @__PURE__ */ jsx6(StatusLine, {})
  ] });
}
__name(App, "App");

// src/launcher.ts
import { spawn, spawnSync, execSync } from "node:child_process";
import { existsSync, readdirSync, statSync } from "node:fs";
import { join, dirname } from "node:path";
import { homedir } from "node:os";
import { fileURLToPath } from "node:url";
var __filename = fileURLToPath(import.meta.url);
var __dirname = dirname(__filename);
function findProjectRoot() {
  let dir = join(__dirname, "..", "..");
  while (dir !== dirname(dir)) {
    if (existsSync(join(dir, "pom.xml"))) return dir;
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
    const result = spawnSync(cmd, [], {
      cwd: projectRoot,
      stdio: "pipe",
      shell: true,
      windowsHide: true
    });
    if (result.status !== 0) {
      throw new Error(result.stderr.toString() || result.stdout.toString());
    }
  } catch (e) {
    console.error("[launcher] Build failed:", String(e));
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
      const out = execSync(`netstat -ano | findstr :${port} | findstr LISTENING`, { encoding: "utf-8" });
      for (const line of out.trim().split("\n")) {
        const pid = line.trim().split(/\s+/).pop();
        if (pid) {
          try {
            execSync(`taskkill /F /PID ${pid}`, { stdio: "ignore" });
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
  return new Promise((resolve) => {
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
function startBackend(projectRoot, port, wsPort) {
  const mvn = findMvn();
  console.log(`[launcher] Starting backend: ${mvn} exec:java ...`);
  killPort(port);
  killPort(wsPort);
  const cmd = `"${mvn}" exec:java -pl jwcode-web -Dexec.mainClass=com.jwcode.web.WebLauncher "-Dexec.args=${port} ${wsPort}" -q`;
  const proc = spawn(cmd, [], {
    cwd: projectRoot,
    env: { ...process.env, JWCODE_WS_PORT: String(wsPort) },
    stdio: ["ignore", "pipe", "pipe"],
    shell: true,
    windowsHide: true
  });
  proc.stdout?.on("data", () => {
  });
  proc.stderr?.on("data", (data) => {
    const msg = data.toString("utf-8").trim();
    if (!msg) return;
    if (msg.includes("Address already in use") || msg.includes("BindException")) {
      console.error(`[backend] ERROR: Port ${port} is in use.`);
    }
  });
  proc.on("exit", (code) => {
    if (code !== 0 && code !== null) {
      console.error(`[launcher] Backend process exited with code ${code}`);
    }
  });
  return proc;
}
__name(startBackend, "startBackend");
function cleanupBackend(proc) {
  if (!proc) return;
  console.log("\n[jwcode] Shutting down...");
  if (process.platform === "win32") {
    try {
      execSync(`taskkill /F /T /PID ${proc.pid}`, { stdio: "ignore" });
    } catch {
      try {
        proc.kill();
      } catch {
      }
    }
  } else {
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
  console.log("");
  console.log("Environment:");
  console.log("  JWCODE_THEME=dark|light   Color theme (default: dark)");
  console.log("");
  console.log("Keyboard shortcuts (in TUI):");
  console.log("  /             Open command palette");
  console.log("  Tab           Toggle Plan/Act mode");
  console.log("  Up/Down       Browse input history (last 30)");
  console.log("  PgUp/PgDn     Scroll message history");
  console.log("  Home/End      Jump to oldest/newest message");
  console.log("  Esc           Close palette / deny approval");
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
  const cmd = args._cmd || "run";
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
