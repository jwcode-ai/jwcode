/**
 * Setup wizard — interactive provider configuration in the TUI.
 * Ported from Ink/React to Solid/OpenTUI.
 */
import { createSignal, onCleanup } from "solid-js"
import { useTheme } from "../context/theme"
import { useKeyboard } from "@opentui/solid"
import { TextInput } from "./TextInput"

interface Props {
  backendUrl: string
  onComplete: () => void
  onCancel?: () => void
  mode?: "fullscreen" | "modal"
}

const PROVIDER_TEMPLATES: Record<string, { name: string; baseUrl: string; apiType: string; defaultModel: string }> = {
  openai:     { name: "OpenAI",            baseUrl: "https://api.openai.com/v1",                        apiType: "openai-completions", defaultModel: "gpt-4o" },
  anthropic:  { name: "Anthropic",         baseUrl: "https://api.anthropic.com/v1",                     apiType: "anthropic-messages",  defaultModel: "claude-sonnet-4-6" },
  deepseek:   { name: "DeepSeek",          baseUrl: "https://api.deepseek.com/v1",                      apiType: "openai-completions", defaultModel: "deepseek-chat" },
  moonshot:   { name: "Moonshot / Kimi",   baseUrl: "https://api.moonshot.cn/v1",                       apiType: "openai-completions", defaultModel: "moonshot-v1-8k" },
  qwen:       { name: "通义千问 (Qwen)",    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1", apiType: "openai-completions", defaultModel: "qwen-plus" },
  zhipu:      { name: "智谱 (GLM)",         baseUrl: "https://open.bigmodel.cn/api/paas/v4",              apiType: "openai-completions", defaultModel: "glm-4-plus" },
  baichuan:   { name: "百川 (Baichuan)",     baseUrl: "https://api.baichuan-ai.com/v1",                    apiType: "openai-completions", defaultModel: "Baichuan4" },
  minimax:    { name: "MiniMax / 海螺",     baseUrl: "https://api.minimax.chat/v1",                       apiType: "openai-completions", defaultModel: "abab6.5s-chat" },
  doubao:     { name: "豆包 (Doubao)",       baseUrl: "https://ark.cn-beijing.volces.com/api/v3",          apiType: "openai-completions", defaultModel: "doubao-pro-32k" },
  hunyuan:    { name: "腾讯混元 (Hunyuan)",   baseUrl: "https://api.hunyuan.cloud.tencent.com/v1",         apiType: "openai-completions", defaultModel: "hunyuan-pro" },
  spark:      { name: "讯飞星火 (Spark)",     baseUrl: "https://spark-api-open.xf-yun.com/v1",             apiType: "openai-completions", defaultModel: "generalv3.5" },
  custom:     { name: "Custom (自定义)",      baseUrl: "",                                                 apiType: "openai-completions", defaultModel: "" },
}

const PROVIDER_KEYS = Object.keys(PROVIDER_TEMPLATES)

type Step = "select_provider" | "enter_key" | "enter_model" | "saving" | "done" | "error"

export function SetupWizard(props: Props) {
  const { theme } = useTheme()
  const [step, setStep] = createSignal<Step>("select_provider")
  const [selectedIdx, setSelectedIdx] = createSignal(0)
  const [providerKey, setProviderKey] = createSignal("")
  const [apiKey, setApiKey] = createSignal("")
  const [baseUrl, setBaseUrl] = createSignal("")
  const [modelId, setModelId] = createSignal("")
  const [errorMsg, setErrorMsg] = createSignal("")

  const isModal = props.mode === "modal"

  async function saveConfig(key: string, apikey: string, url: string, model: string) {
    setStep("saving")
    try {
      const tmpl = PROVIDER_TEMPLATES[key]
      const finalUrl = url || tmpl.baseUrl
      const res = await fetch(`${props.backendUrl}/api/config/provider`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          provider: key,
          baseUrl: finalUrl,
          apiType: tmpl.apiType,
          apiKey: apikey,
          setDefault: true,
          models: [{ id: model, name: model, enabled: true, priority: 10 }],
        }),
      })
      if (!res.ok) {
        const err = await res.text()
        throw new Error(err || `HTTP ${res.status}`)
      }
      setStep("done")
      setTimeout(props.onComplete, 1200)
    } catch (e: unknown) {
      setErrorMsg(String(e))
      setStep("error")
    }
  }

  // Poll for backend-side config changes
  // Skipped for simplicity — the setup handles it directly

  useKeyboard((evt: any) => {
    const key = evt || {}
    const keyName = key.name ?? ""
    const input = key.sequence ?? ""

    if (keyName === "escape" && isModal && props.onCancel) {
      props.onCancel()
      return
    }

    if (step() === "select_provider") {
      if (keyName === "up" || input === "k") {
        setSelectedIdx((s) => (s - 1 + PROVIDER_KEYS.length) % PROVIDER_KEYS.length)
      } else if (keyName === "down" || input === "j") {
        setSelectedIdx((s) => (s + 1) % PROVIDER_KEYS.length)
      } else if (keyName === "enter" || keyName === "return") {
        const pk = PROVIDER_KEYS[selectedIdx()]
        setProviderKey(pk)
        const tmpl = PROVIDER_TEMPLATES[pk]
        setBaseUrl(tmpl.baseUrl)
        setModelId(tmpl.defaultModel)
        setStep("enter_key")
      }
      return
    }

    if (step() === "error") {
      if (keyName === "escape" && isModal && props.onCancel) {
        props.onCancel()
        return
      }
      if (keyName === "enter" || keyName === "return") {
        setStep("enter_key")
        setErrorMsg("")
      }
    }
  })

  const handleKeySubmit = () => {
    if (apiKey().trim().length < 10) {
      setErrorMsg("API key too short (min 10 characters).")
      return
    }
    setErrorMsg("")
    if (providerKey() === "custom") {
      setStep("enter_model")
    } else {
      saveConfig(providerKey(), apiKey(), baseUrl(), modelId())
    }
  }

  const handleModelSubmit = () => {
    if (modelId().trim().length === 0) {
      setErrorMsg("Model name is required.")
      return
    }
    saveConfig(providerKey(), apiKey(), baseUrl(), modelId())
  }

  if (step() === "done") {
    return (
      <box flexDirection="column">
        <text fg={theme.success}>✓ Configuration saved!</text>
        {props.mode === "fullscreen" && <text fg={theme.textMuted}>Starting JWCode...</text>}
      </box>
    )
  }

  const title = isModal ? "Add Provider" : "JWCode — First Run Setup"

  return (
    <box flexDirection="column" padding={1}>
      <box>
        <text fg={theme.primary} attributes={1}>{title}</text>
      </box>
      {props.mode === "fullscreen" && (
        <>
          <text>Configure your AI provider to get started.</text>
        </>
      )}
      {isModal && props.onCancel && (
        <text fg={theme.textMuted}>Esc to close</text>
      )}

      {step() === "select_provider" && (
        <box flexDirection="column">
          <text>Select provider (↑↓ to navigate, Enter to confirm):</text>
          {PROVIDER_KEYS.map((k, i) => (
            <box key={k}>
              <text fg={i === selectedIdx() ? theme.success : undefined} attributes={i === selectedIdx() ? 1 : 0}>
                {i === selectedIdx() ? "> " : "  "}{PROVIDER_TEMPLATES[k].name}
              </text>
            </box>
          ))}
        </box>
      )}

      {step() === "enter_key" && (
        <box flexDirection="column">
          <box>
            <text>Provider: </text>
            <text fg={theme.success}>{PROVIDER_TEMPLATES[providerKey()].name}</text>
          </box>
          <text fg={theme.info}>Base URL: {baseUrl()}</text>
          <text>Enter API Key:</text>
          <TextInput
            value={apiKey()}
            onChange={setApiKey}
            onSubmit={handleKeySubmit}
            placeholder="sk-..."
          />
          <text fg={theme.textMuted}>Press Enter to confirm</text>
          {errorMsg() && <text fg={theme.error}>Error: {errorMsg()}</text>}
        </box>
      )}

      {step() === "enter_model" && (
        <box flexDirection="column">
          <box>
            <text>Provider: </text>
            <text fg={theme.success}>{PROVIDER_TEMPLATES[providerKey()].name}</text>
          </box>
          <text fg={theme.success}>API Key: configured</text>
          <text fg={theme.info}>Base URL: {baseUrl()}</text>
          <text>Model ID:</text>
          <TextInput
            value={modelId()}
            onChange={setModelId}
            onSubmit={handleModelSubmit}
            placeholder="model-name"
          />
          <text fg={theme.textMuted}>Press Enter to confirm, type to edit model name</text>
          {errorMsg() && <text fg={theme.error}>Error: {errorMsg()}</text>}
        </box>
      )}

      {step() === "saving" && (
        <text fg={theme.warning}>Saving configuration to ~/.jwcode/config.yaml...</text>
      )}

      {step() === "error" && (
        <box flexDirection="column">
          <text fg={theme.error}>Failed to save: {errorMsg()}</text>
          <text>Press Enter to retry{isModal ? ", Esc to cancel" : ""}.</text>
        </box>
      )}
    </box>
  )
}
