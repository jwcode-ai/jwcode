import { memo, useState, useEffect, useRef } from 'react';
import { Box, Text } from 'ink';
import { useAppStatusLine, useAppIsGenerating, useAppDegradation, useAppPdcaPhase, useAppPlanTasks } from '../hooks/useAppState.js';
import { debugLog } from '../client.js';
import { t } from '../theme.js';

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
  debugLog('app', 'StatusLine mount planMode=' + useAppStatusLine().planMode);
  const { usage, modelName, planMode, autoMode, connected, statusText, messagesLen,
          tokenRate, compactionProgress } = useAppStatusLine();
  const degradation = useAppDegradation();
  const pdcaPhase = useAppPdcaPhase();
  const planTasks = useAppPlanTasks();
  const msgCount = messagesLen;

  const isGenerating = useAppIsGenerating();
  const startTimeRef = useRef<number>(0);
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (isGenerating) {
      if (startTimeRef.current === 0) startTimeRef.current = Date.now();
      const timer = setInterval(() => setNow(Date.now()), 1000);
      return () => clearInterval(timer);
    } else {
      startTimeRef.current = 0;
    }
  }, [isGenerating]);
  const generationElapsed = isGenerating
    ? Math.floor((now - startTimeRef.current) / 1000)
    : 0;

  const pct = Math.min(100, Math.round(usage.usageRatio * 100));
  const filled = Math.round(pct / 10);
  const bar = '\u2588'.repeat(filled) + '\u2591'.repeat(10 - filled);
  const model = modelName || (connected ? 'ready' : 'connecting...');

  const modeLabel = planMode ? ' Plan ' : ' Act ';
  const modeColor = planMode ? t.plan : t.success;
  // Force re-mount on mode switch to prevent terminal ghosting
  const modeKey = planMode ? 'mode-plan' : 'mode-act';

  const connIcon = connected ? '\u25CF' : '\u25CB';
  const connColor = connected ? t.connected : t.disconnected;

  const isError = statusText.startsWith('Error:');
  const barColor = pct > 90 ? t.error : pct > 70 ? t.warning : t.text;
  const rateStr = formatRate(tokenRate);
  const elapsedStr = formatElapsed(generationElapsed);

  const p = usage.promptTokens;
  const c = usage.completionTokens;

  return (
    <Box flexDirection="column" width="100%" paddingRight={1}>
      <Box height={1}>
        <Text bold color={t.primary}>jwcode</Text>
        <Text>  </Text>
        <Text key={modeKey} backgroundColor={modeColor} color="black"> {modeLabel} </Text>
        <Text>  </Text>
        {autoMode && (
          <>
            <Text backgroundColor={t.auto} color="black"> AUTO </Text>
            <Text>  </Text>
          </>
        )}
        {pdcaPhase && (
          <>
            <Text backgroundColor={t.warning} color="black"> {pdcaPhase} </Text>
            <Text>  </Text>
          </>
        )}
        {planTasks.length > 0 && (
          <>
            <Text color={t.primary}>{planTasks.filter(t => t.status === 'completed').length}</Text>
            <Text dimColor>/</Text>
            <Text color={t.text}>{planTasks.length}</Text>
            <Text dimColor> tasks</Text>
            <Text>  </Text>
          </>
        )}
        <Text color={connColor}>{connIcon} </Text>
        <Text color={t.success}>{model}</Text>
        <Text>  </Text>
        <Text dimColor>{msgCount}msgs</Text>

        {p > 0 || c > 0 ? (
          <>
            <Text>  </Text>
            <Text color={t.info}>{formatTokens(p)}</Text>
            <Text dimColor>+</Text>
            <Text color={t.success}>{formatTokens(c)}</Text>
            <Text dimColor>=</Text>
            <Text color={t.warning}>{formatTokens(usage.totalTokens)}</Text>
          </>
        ) : (
          <>
            <Text>  t:</Text>
            <Text color={t.warning}>{formatTokens(usage.totalTokens)}</Text>
          </>
        )}
        <Text>  </Text>
        <Text color={barColor}>{bar} {pct}%</Text>

        {isGenerating && rateStr && (
          <>
            <Text>  </Text>
            <Text color={t.tool}>{rateStr}</Text>
          </>
        )}

        {isGenerating && elapsedStr && (
          <>
            <Text>  </Text>
            <Text color={t.primary}>{elapsedStr}</Text>
          </>
        )}
      </Box>
      <Box height={1}>
        {degradation.active ? (
          <Text color={t.warning} dimColor>
            [{degradation.mode.toUpperCase()}] {degradation.message}
            {degradation.retryCount > 0 ? ` (${degradation.retryCount}/${degradation.maxRetries})` : ''}
          </Text>
        ) : (
          <Text> </Text>
        )}
      </Box>
      <Box height={1}>
        {compactionProgress ? (
          <>
            <Text color={compactionProgress.percent >= 100 ? t.success : t.primary}>
              {compactionProgress.percent >= 100 ? '\u2713' : '\u2699'} {compactionProgress.message}
            </Text>
            <Text> </Text>
            <Text color={t.warning}>{'\u2588'.repeat(Math.round(compactionProgress.percent / 10))}{'\u2591'.repeat(10 - Math.round(compactionProgress.percent / 10))}</Text>
            <Text dimColor> {compactionProgress.percent}%</Text>
          </>
        ) : (
          <Text> </Text>
        )}
      </Box>
      <Box height={1}>
        {statusText && statusText !== 'connecting...' ? (
          <Text color={isError ? t.error : t.muted} dimColor={!isError}>
            {statusText.slice(0, 100)}
          </Text>
        ) : (
          <Text> </Text>
        )}
      </Box>
    </Box>
  );
});
