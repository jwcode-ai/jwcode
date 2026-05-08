import React from 'react';
import { SlashCommand } from '../hooks/useSlashCommands';

interface SlashCommandMenuProps {
  isOpen: boolean;
  commands: SlashCommand[];
  selectedIndex: number;
  filter: string;
  onSelect: (command: SlashCommand) => void;
  onHover: (index: number) => void;
  containerRef: React.RefObject<HTMLDivElement>;
}

export const SlashCommandMenu: React.FC<SlashCommandMenuProps> = ({
  isOpen,
  commands,
  selectedIndex,
  filter,
  onSelect,
  onHover,
  containerRef,
}) => {
  if (!isOpen || commands.length === 0) return null;

  return (
    <div
      ref={containerRef}
      className="absolute bottom-full left-0 right-0 mb-2 bg-dark-surface border border-dark-border rounded-lg shadow-lg overflow-hidden z-50 max-h-64 overflow-y-auto"
    >
      <div className="px-3 py-2 text-xs text-dark-muted border-b border-dark-border bg-dark-bg">
        {filter ? `搜索: "${filter}"` : '快捷命令'}
        <span className="ml-2 opacity-60">↑↓ 选择 · Enter 执行 · Esc 关闭</span>
      </div>
      {commands.map((cmd, index) => (
        <button
          key={cmd.id}
          data-index={index}
          onClick={() => onSelect(cmd)}
          onMouseEnter={() => onHover(index)}
          className={`w-full px-3 py-2 flex items-center gap-3 text-left transition-colors ${
            index === selectedIndex
              ? 'bg-accent-blue/10 border-l-2 border-accent-blue'
              : 'border-l-2 border-transparent hover:bg-dark-hover'
          }`}
        >
          <span className="text-lg shrink-0">{cmd.icon}</span>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-dark-text">/{cmd.name}</span>
                {cmd.args && (
                  <span className="text-xs text-dark-muted">{cmd.args}</span>
                )}
                {!cmd.local && (
                  <span className="text-[10px] px-1.5 py-0.5 rounded bg-accent-blue/20 text-accent-blue shrink-0">后端</span>
                )}
              </div>
              <div className="text-xs text-dark-muted truncate">{cmd.description}</div>
            </div>
        </button>
      ))}
    </div>
  );
};
