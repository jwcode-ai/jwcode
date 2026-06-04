import { memo, useState, useEffect } from 'react';
import { Box, Text } from 'ink';
import { useAppStatusLine, useAppIsGenerating, useAppCurrentMessage, useAppDegradation, useAppPdcaPhase, useAppPlanTasks } from '../hooks/useAppState.js';

function formatTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${Math.round(n / 1_000)}K`;
  return String(n);
}

function formatRate(rate: number): string {
  if (rate <= 0) return '';
  if (rate >= 100) return `${Math.round(rate)}t/s`;
  if (rate >= 10) return `${rate.toFixed(1)}t/s`;
  return `${rate.toFixed(1)}t/s`;
}

function formatElapsed(sec: number): string {
  if (sec <= 0) return '';
  if (sec >= 60) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}m${s}s`;
  }
  return `${sec}s`;
}

export const StatusLine = memo(function StatusLine() {
  const { usage, modelName, planMode, autoMode, connected, statusText, messagesLen,
          tokenRate } = useAppStatusLine();
  const degradation = useAppDegradation();
  const pdcaPhase = useAppPdcaPhase();
  const planTasks = useAppPlanTasks();
  const msgCount = messagesLen;

  // Local elapsed timer — avoids global state updates every second
  const isGenerating = useAppIsGenerating();
  const currentMessage = useAppCurrentMessage();
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!currentMessage) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [currentMessage?.id]);
  const generationElapsed = currentMessage
    ? Math.floor((now - (currentMessage.timestamp || Date.now())) / 1000)
    : 0;

  const pct = Math.min(100, Math.round(usage.usageRatio * 100));
  const filled = Math.round(pct / 10);
  const bar = '█'.repeat(filled) + '░'.repeat(10 - filled);
  const model = modelName || (connected ? 'ready' : 'connecting...');

  const modeLabel = planMode ? ' Plan ' : ' Act ';
  const modeColor = planMode ? 'cyan' : 'green';

  const connIcon = connected ? '●' : '○';
  const connColor = connected ? 'green' : 'red';

  const isError = statusText.startsWith('Error:');
  const barColor = pct > 90 ? 'red' : pct > 70 ? 'yellow' : 'white';
  const rateStr = formatRate(tokenRate);
  const elapsedStr = formatElapsed(generationElapsed);

  // Prompt vs completion breakdown
  const p = usage.promptTokens;
  const c = usage.completionTokens;

  return (
    <Box flexDirection="column" width="100%" paddingRight={1}>
      <Box height={1}>
        <Text bold color="cyan">jwcode</Text>
        <Text>  </Text>
        <Text backgroundColor={modeColor} color="black"> {modeLabel} </Text>
        <Text>  </Text>
        {autoMode && (
          <>
            <Text backgroundColor="magenta" color="black"> AUTO </Text>
            <Text>  </Text>
          </>
        )}
        {pdcaPhase && (
          <>
            <Text backgroundColor="yellow" color="black"> {pdcaPhase} </Text>
            <Text>  </Text>
          </>
        )}
        {planTasks.length > 0 && (
          <>
            <Text color="cyan">{planTasks.filter(t => t.status === 'completed').length}</Text>
            <Text dimColor>/</Text>
            <Text color="white">{planTasks.length}</Text>
            <Text dimColor> tasks</Text>
            <Text>  </Text>
          </>
        )}
        <Text color={connColor}>{connIcon} </Text>
        <Text color="green">{model}</Text>
        <Text>  </Text>
        <Text dimColor>{msgCount}msgs</Text>

        {/* Prompt + Completion breakdown */}
        {p > 0 || c > 0 ? (
          <>
            <Text>  </Text>
            <Text color="blue">{formatTokens(p)}</Text>
            <Text dimColor>+</Text>
            <Text color="green">{formatTokens(c)}</Text>
            <Text dimColor>=</Text>
            <Text color="yellow">{formatTokens(usage.totalTokens)}</Text>
          </>
        ) : (
          <>
            <Text>  t:</Text>
            <Text color="yellow">{formatTokens(usage.totalTokens)}</Text>
          </>
        )}
        <Text>  </Text>
        <Text color={barColor}>{bar} {pct}%</Text>

        {/* Token rate during streaming */}
        {isGenerating && rateStr && (
          <>
            <Text>  </Text>
            <Text color="magenta">{rateStr}</Text>
          </>
        )}

        {/* Elapsed timer */}
        {isGenerating && elapsedStr && (
          <>
            <Text>  </Text>
            <Text color="cyan">{elapsedStr}</Text>
          </>
        )}
      </Box>
      {degradation.active && (
        <Box height={1}>
          <Text color="yellow" dimColor>
            [{degradation.mode.toUpperCase()}] {degradation.message}
            {degradation.retryCount > 0 ? ` (${degradation.retryCount}/${degradation.maxRetries})` : ''}
          </Text>
        </Box>
      )}
      {statusText && statusText !== 'connecting...' && (
        <Box height={1}>
          <Text color={isError ? 'red' : 'grey'} dimColor={!isError}>
            {statusText.slice(0, 100)}
          </Text>
        </Box>
      )}
    </Box>
  );
});
