import { Link2, ToggleRight, ToggleLeft } from 'lucide-react';
import type { HookStats } from '../../types';

interface Props {
  stats: HookStats | null;
}

export function HookStatsCards({ stats }: Props) {
  if (!stats) return null;

  const cards = [
    {
      label: '总规则数',
      value: stats.totalHooks,
      icon: Link2,
      iconWrap: 'bg-accent-blue/15 text-accent-blue',
    },
    {
      label: '已启用',
      value: stats.enabledCount,
      icon: ToggleRight,
      iconWrap: 'bg-accent-green/15 text-accent-green',
    },
    {
      label: '已禁用',
      value: stats.disabledCount,
      icon: ToggleLeft,
      iconWrap: 'bg-dark-hover text-dark-muted',
    },
  ];

  return (
    <div className="grid grid-cols-3 gap-3">
      {cards.map(card => {
        const Icon = card.icon;
        return (
          <div
            key={card.label}
            className="flex items-center gap-3 rounded-xl border border-dark-border bg-dark-surface px-4 py-3"
          >
            <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${card.iconWrap}`}>
              <Icon size={18} />
            </span>
            <div className="min-w-0">
              <div className="text-xl font-bold text-dark-text">{card.value}</div>
              <div className="text-xs text-dark-muted">{card.label}</div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
