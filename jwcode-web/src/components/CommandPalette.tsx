import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Search, CornerDownLeft } from 'lucide-react';
import { useSlashCommands, SlashCommand, UseSlashCommandsOptions } from '../hooks/useSlashCommands';
import { toast } from '../stores/toastStore';

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
  options: UseSlashCommandsOptions;
}

/**
 * CommandPalette — 应用级 Ctrl/Cmd+K 命令面板。
 * 复用 useSlashCommands 的命令集（导航 + 本地/后端命令），居中模态展示。
 */
export function CommandPalette({ open, onClose, options }: CommandPaletteProps) {
  const { t } = useTranslation();
  const {
    filter,
    setFilter,
    filteredCommands,
    selectedIndex,
    setSelectedIndex,
    selectNext,
    selectPrev,
    executeCommand,
    openMenu,
    closeMenu,
  } = useSlashCommands(options);

  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const [running, setRunning] = useState(false);

  // 打开时重置过滤/选中并聚焦输入
  useEffect(() => {
    if (open) {
      openMenu();
      setRunning(false);
      setTimeout(() => inputRef.current?.focus(), 0);
    } else {
      closeMenu();
    }
  }, [open, openMenu, closeMenu]);

  // 选中项滚动进入视图
  useEffect(() => {
    if (!open || !listRef.current) return;
    const el = listRef.current.querySelector(`[data-cmd-index="${selectedIndex}"]`);
    if (el) (el as HTMLElement).scrollIntoView({ block: 'nearest' });
  }, [selectedIndex, open]);

  if (!open) return null;

  const runCommand = (cmd: SlashCommand) => {
    if (!cmd.local) {
      setRunning(true);
      toast.info(t('palette.running'));
    }
    const result = executeCommand(cmd, '');
    if (cmd.local && result?.message) {
      toast.success(result.message);
    }
    onClose();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      selectNext();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      selectPrev();
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const cmd = filteredCommands[selectedIndex];
      if (cmd) runCommand(cmd);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
    }
  };

  return (
    <div
      className="fixed inset-0 z-[100] flex items-start justify-center bg-black/50 backdrop-blur-sm pt-[12vh] px-4 animate-fade-in"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label={t('a11y.commandPalette')}
    >
      <div
        className="w-full max-w-xl bg-dark-surface border border-dark-border rounded-xl shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Search input */}
        <div className="flex items-center gap-2 px-4 py-3 border-b border-dark-border">
          <Search size={16} className="text-dark-muted shrink-0" aria-hidden="true" />
          <input
            ref={inputRef}
            value={filter}
            onChange={(e) => {
              setFilter(e.target.value);
              setSelectedIndex(0);
            }}
            onKeyDown={handleKeyDown}
            placeholder={t('palette.placeholder')}
            aria-label={t('palette.placeholder')}
            className="flex-1 bg-transparent text-sm text-dark-text placeholder-dark-muted outline-none"
          />
          {running && (
            <span className="text-[10px] text-accent-blue animate-pulse-soft shrink-0">{t('palette.running')}</span>
          )}
        </div>

        {/* Results */}
        <div ref={listRef} className="max-h-[50vh] overflow-y-auto custom-scrollbar py-1">
          {filteredCommands.length === 0 ? (
            <div className="px-4 py-8 text-center text-sm text-dark-muted">{t('palette.empty')}</div>
          ) : (
            filteredCommands.map((cmd, index) => (
              <button
                key={cmd.id}
                data-cmd-index={index}
                onClick={() => runCommand(cmd)}
                onMouseEnter={() => setSelectedIndex(index)}
                className={`w-full px-4 py-2.5 flex items-center gap-3 text-left transition-colors ${
                  index === selectedIndex
                    ? 'bg-accent-blue/10 border-l-2 border-accent-blue'
                    : 'border-l-2 border-transparent hover:bg-dark-hover'
                }`}
              >
                <span className="text-lg shrink-0" aria-hidden="true">{cmd.icon}</span>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-dark-text">/{cmd.name}</span>
                    {cmd.args && <span className="text-xs text-dark-muted">{cmd.args}</span>}
                    {!cmd.local && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-accent-blue/20 text-accent-blue shrink-0">
                        {t('slashCommands.backend')}
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-dark-muted truncate">{cmd.description}</div>
                </div>
                {index === selectedIndex && (
                  <CornerDownLeft size={14} className="text-dark-muted shrink-0" aria-hidden="true" />
                )}
              </button>
            ))
          )}
        </div>

        {/* Footer hint */}
        <div className="px-4 py-2 border-t border-dark-border text-[10px] text-dark-muted bg-dark-bg">
          {t('palette.hint')}
        </div>
      </div>
    </div>
  );
}
