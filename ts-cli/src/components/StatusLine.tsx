import { memo } from 'react';
import { Box, Text } from 'ink';
import { useAppStatusLine, useAppDegradation, useAppPdcaPhase, useAppPlanTasks } from '../hooks/useAppState.js';

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
    return m + 'm' + s + 's';
  }
  return sec + 's';
}

function formatCost(cost: number): string {
  if (cost <= 0) return '';
  return '$' + cost.toFixed(4);
}

// Inner presentational component (pure)
const StatusLineInner = memo(function StatusLineInner({
  connected,
  statusText,
  modelName,
  tokenRate,
  usage,
  planMode,
  autoMode,
  degradation,
  pdcaPhase,
  planTasks,
}: {
  connected: boolean;
  statusText: string;
  modelName: string;
  tokenRate: number;
  usage: { tokensIn: number; tokensOut: number; costDollars: number };
  planMode: boolean;
  autoMode: boolean;
  degradation: boolean;
  pdcaPhase: string;
  planTasks: Array<{ status: string }>;
}) {
  const connectColor = connected ? 'green' : 'red';
  const connectLabel = connected ? '●' : '○';
  const modeColor = planMode ? 'yellow' : autoMode ? 'magenta' : 'cyan';
  const modeLabel = planMode ? '[PLAN]' : autoMode ? '[AUTO]' : '[CHAT]';

  const statusItems: string[] = [];
  if (modelName) statusItems.push(modelName);
  if (usage.tokensIn + usage.tokensOut > 0) {
    statusItems.push(formatTokens(usage.tokensIn + usage.tokensOut) + 't');
  }
  const rateStr = formatRate(tokenRate);
  if (rateStr) statusItems.push(rateStr);
  const costStr = formatCost(usage.costDollars);
  if (costStr) statusItems.push(costStr);

  // PDCA phase
  const phaseStr = pdcaPhase ? ` PDCA:${pdcaPhase}` : '';

  // Plan task summary
  const planTaskSummary = planTasks.length > 0
    ? ` tasks:${planTasks.filter(t => t.status === 'completed').length}/${planTasks.length}`
    : '';

  return (
    <Box>
      <Text color={connectColor}>{connectLabel}</Text>
      <Text> </Text>
      <Text color={modeColor}>{modeLabel}</Text>
      <Text> </Text>
      {statusItems.length > 0 && (
        <Text dimColor>{statusItems.join(' | ')}</Text>
      )}
      {degradation && (
        <Text color="red"> ⚠ degraded</Text>
      )}
      {statusText && (
        <Text color="yellow"> {statusText}</Text>
      )}
      <Text dimColor>{phaseStr}{planTaskSummary}</Text>
    </Box>
  );
});

export const StatusLine = memo(function StatusLine() {
  const status = useAppStatusLine();
  const degradation = useAppDegradation();
  const pdcaPhase = useAppPdcaPhase();
  const planTasks = useAppPlanTasks();

  return (
    <StatusLineInner
      connected={status.connected}
      statusText={status.statusText}
      modelName={status.modelName}
      tokenRate={status.tokenRate}
      usage={status.usage}
      planMode={status.planMode}
      autoMode={status.autoMode}
      degradation={degradation}
      pdcaPhase={pdcaPhase}
      planTasks={planTasks}
    />
  );
});
