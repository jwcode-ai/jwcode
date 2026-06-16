/**
 * First-run setup wizard — interactive provider configuration in the TUI.
 *
 * Shown when ~/.jwcode/config.yaml has no valid provider configured.
 * Guides user through: provider type → API key → model name → save.
 */
import React, { useState, useCallback, useEffect } from 'react';
import { Box, Text, useInput } from 'ink';

interface Props {
  backendUrl: string;
  onComplete: () => void;
}

const PROVIDER_TEMPLATES: Record<string, { name: string; baseUrl: string; apiType: string; defaultModel: string }> = {
  openai:     { name: 'OpenAI',            baseUrl: 'https://api.openai.com/v1',                        apiType: 'openai-completions', defaultModel: 'gpt-4o' },
  anthropic:  { name: 'Anthropic',         baseUrl: 'https://api.anthropic.com/v1',                     apiType: 'anthropic-messages',  defaultModel: 'claude-sonnet-4-6' },
  deepseek:   { name: 'DeepSeek',          baseUrl: 'https://api.deepseek.com/v1',                      apiType: 'openai-completions', defaultModel: 'deepseek-chat' },
  moonshot:   { name: 'Moonshot / Kimi',   baseUrl: 'https://api.moonshot.cn/v1',                       apiType: 'openai-completions', defaultModel: 'moonshot-v1-8k' },
  qwen:       { name: '通义千问 (Qwen)',    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiType: 'openai-completions', defaultModel: 'qwen-plus' },
  zhipu:      { name: '智谱 (GLM)',         baseUrl: 'https://open.bigmodel.cn/api/paas/v4',              apiType: 'openai-completions', defaultModel: 'glm-4-plus' },
  baichuan:   { name: '百川 (Baichuan)',     baseUrl: 'https://api.baichuan-ai.com/v1',                    apiType: 'openai-completions', defaultModel: 'Baichuan4' },
  minimax:    { name: 'MiniMax / 海螺',     baseUrl: 'https://api.minimax.chat/v1',                       apiType: 'openai-completions', defaultModel: 'abab6.5s-chat' },
  doubao:     { name: '豆包 (Doubao)',       baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',          apiType: 'openai-completions', defaultModel: 'doubao-pro-32k' },
  hunyuan:    { name: '腾讯混元 (Hunyuan)',   baseUrl: 'https://api.hunyuan.cloud.tencent.com/v1',         apiType: 'openai-completions', defaultModel: 'hunyuan-pro' },
  spark:      { name: '讯飞星火 (Spark)',     baseUrl: 'https://spark-api-open.xf-yun.com/v1',             apiType: 'openai-completions', defaultModel: 'generalv3.5' },
  custom:     { name: 'Custom (自定义)',      baseUrl: '',                                                 apiType: 'openai-completions', defaultModel: '' },
};

const PROVIDER_KEYS = Object.keys(PROVIDER_TEMPLATES);

type Step = 'select_provider' | 'enter_key' | 'enter_model' | 'saving' | 'done' | 'error';

export const SetupWizard: React.FC<Props> = ({ backendUrl, onComplete }) => {
  const [step, setStep] = useState<Step>('select_provider');
  const [selectedIdx, setSelectedIdx] = useState(0);
  const [providerKey, setProviderKey] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [baseUrl, setBaseUrl] = useState('');
  const [modelId, setModelId] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const [keyVisible, setKeyVisible] = useState(false);

  const saveConfig = useCallback(async () => {
    setStep('saving');
    try {
      const tmpl = PROVIDER_TEMPLATES[providerKey];
      const url = baseUrl || tmpl.baseUrl;
      const res = await fetch(`${backendUrl}/api/config/provider`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          provider: providerKey,
          baseUrl: url,
          apiType: tmpl.apiType,
          apiKey: apiKey,
          setDefault: true,
          models: [{
            id: modelId,
            name: modelId,
            enabled: true,
            priority: 10,
          }],
        }),
      });
      if (!res.ok) {
        const err = await res.text();
        throw new Error(err || `HTTP ${res.status}`);
      }
      setStep('done');
      // Auto-dismiss after short delay to show success
      setTimeout(onComplete, 1500);
    } catch (e: unknown) {
      setErrorMsg(String(e));
      setStep('error');
    }
  }, [backendUrl, providerKey, baseUrl, apiKey, modelId, onComplete]);

  useEffect(() => {
    if (step === 'done') return;
    const check = async () => {
      try {
        const r = await fetch(`${backendUrl}/api/config/provider`);
        const d = await r.json() as any;
        if (d?.data?.configured) {
          onComplete();
        }
      } catch { /* backend not ready yet */ }
    };
    check();
  }, [step, backendUrl, onComplete]);

  useInput((input, key) => {
    if (step === 'select_provider') {
      if (key.upArrow || input === 'k') {
        setSelectedIdx(s => (s - 1 + PROVIDER_KEYS.length) % PROVIDER_KEYS.length);
      } else if (key.downArrow || input === 'j') {
        setSelectedIdx(s => (s + 1) % PROVIDER_KEYS.length);
      } else if (key.return) {
        const pk = PROVIDER_KEYS[selectedIdx];
        setProviderKey(pk);
        const tmpl = PROVIDER_TEMPLATES[pk];
        setBaseUrl(tmpl.baseUrl);
        setModelId(tmpl.defaultModel);
        setStep('enter_key');
      }
      return;
    }

    if (step === 'enter_key') {
      if (key.return) {
        if (apiKey.trim().length < 10) {
          setErrorMsg('API key too short (min 10 characters).');
          return;
        }
        setErrorMsg('');
        if (providerKey === 'custom') {
          setStep('enter_model');
        } else {
          saveConfig();
        }
      } else if (key.backspace || key.delete) {
        setApiKey(s => s.slice(0, -1));
      } else if (input === '\t') {
        setKeyVisible(v => !v);
      } else if (input && input.length === 1 && !key.ctrl) {
        setApiKey(s => s + input);
      }
      return;
    }

    if (step === 'enter_model') {
      if (key.return) {
        if (modelId.trim().length === 0) {
          setErrorMsg('Model name is required.');
          return;
        }
        saveConfig();
      } else if (key.backspace || key.delete) {
        setModelId(s => s.slice(0, -1));
      } else if (input === '\t') {
        // toggle base URL editing
      } else if (input && input.length === 1 && !key.ctrl) {
        setModelId(s => s + input);
      }
      return;
    }

    if (step === 'error') {
      if (key.return || key.escape) {
        setStep('enter_key');
        setErrorMsg('');
      }
      return;
    }
  });

  if (step === 'done') {
    return (
      <Box flexDirection="column" padding={1}>
        <Text color="green">✓ Configuration saved! Starting JWCode...</Text>
      </Box>
    );
  }

  return (
    <Box flexDirection="column" padding={1} borderStyle="round" borderColor="cyan">
      <Box marginBottom={1}>
        <Text bold color="cyan">JWCode — First Run Setup</Text>
      </Box>
      <Text>Configure your AI provider to get started.</Text>
      <Text> </Text>

      {step === 'select_provider' && (
        <Box flexDirection="column">
          <Text>Select provider (↑↓ to navigate, Enter to confirm):</Text>
          {PROVIDER_KEYS.map((k, i) => (
            <Text key={k}>
              {i === selectedIdx ? '❯ ' : '  '}
              <Text color={i === selectedIdx ? 'green' : undefined} bold={i === selectedIdx}>
                {PROVIDER_TEMPLATES[k].name}
              </Text>
              {i === selectedIdx ? ' ◀' : ''}
            </Text>
          ))}
          {providerKey === 'custom' && (
            <Box flexDirection="column" marginTop={1}>
              <Text>Base URL:</Text>
              <Text color="yellow">{'> '}{baseUrl || '(enter after selecting Custom)'}</Text>
            </Box>
          )}
        </Box>
      )}

      {step === 'enter_key' && (
        <Box flexDirection="column">
          <Text>Provider: <Text color="green">{PROVIDER_TEMPLATES[providerKey].name}</Text></Text>
          <Text>Base URL: <Text color="blue">{baseUrl}</Text></Text>
          <Text> </Text>
          <Text>Enter API Key (Tab to toggle visibility):</Text>
          <Box>
            <Text color="yellow">{'> '}</Text>
            <Text>{keyVisible ? apiKey : apiKey.replace(/./g, '*')}</Text>
            <Text color="gray">{keyVisible ? ' [visible]' : ' [hidden]'}</Text>
          </Box>
          <Text dimColor>Press Enter to confirm, Backspace to edit</Text>
          {errorMsg && <Text color="red">Error: {errorMsg}</Text>}
        </Box>
      )}

      {step === 'enter_model' && (
        <Box flexDirection="column">
          <Text>Provider: <Text color="green">{PROVIDER_TEMPLATES[providerKey].name}</Text></Text>
          <Text>API Key: <Text color="green">configured</Text></Text>
          <Text> </Text>
          <Text>Base URL:</Text>
          <Box>
            <Text color="yellow">{'> '}</Text>
            <Text>{baseUrl}</Text>
          </Box>
          <Text>Model ID:</Text>
          <Box>
            <Text color="yellow">{'> '}</Text>
            <Text>{modelId}</Text>
          </Box>
          <Text dimColor>Press Enter to save, type to edit model name</Text>
          {errorMsg && <Text color="red">Error: {errorMsg}</Text>}
        </Box>
      )}

      {step === 'saving' && (
        <Text color="yellow">Saving configuration to ~/.jwcode/config.yaml...</Text>
      )}

      {step === 'error' && (
        <Box flexDirection="column">
          <Text color="red">Failed to save: {errorMsg}</Text>
          <Text>Press Enter to retry, Esc to go back.</Text>
        </Box>
      )}
    </Box>
  );
};
