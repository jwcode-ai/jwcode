import type { HookStats } from '../../types';

interface Props {
  stats: HookStats | null;
}

export function HookStatsCards({ stats }: Props) {
  if (!stats) return null;

  const cards = [
    { label: '总规则数', value: stats.totalHooks, color: 'text-blue-400' },
    { label: '已启用', value: stats.enabledCount, color: 'text-green-400' },
    { label: '已禁用', value: stats.disabledCount, color: 'text-gray-400' },
  ];

  return (
    <div className="grid grid-cols-3 gap-3 flex-shrink-0">
      {cards.map(card => (
        <div key={card.label} className="bg-gray-800 border border-gray-700 rounded p-3 text-center">
          <div className={`text-2xl font-bold ${card.color}`}>{card.value}</div>
          <div className="text-xs text-gray-500 mt-1">{card.label}</div>
        </div>
      ))}
    </div>
  );
}
