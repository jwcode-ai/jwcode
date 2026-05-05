import { useCallback, useMemo, useState, useRef, useEffect } from 'react';
import { TabId } from '../types';

export interface SlashCommand {
  id: string;
  name: string;
  description: string;
  args?: string;
  icon: string;
  action: (args: string) => { success: boolean; message?: string };
}

export interface UseSlashCommandsOptions {
  activeTab: TabId;
  setActiveTab: (tab: TabId) => void;
  createNewSession: () => void;
  clearMessages: () => void;
  setTheme: (theme: 'dark' | 'light' | 'auto') => void;
  toggleTerminal: () => void;
  setLogs: React.Dispatch<React.SetStateAction<import('../types').LogEntry[]>>;
  setUnreadLogs: React.Dispatch<React.SetStateAction<number>>;
}

export function useSlashCommands(options: UseSlashCommandsOptions) {
  const {
    activeTab,
    setActiveTab,
    createNewSession,
    clearMessages,
    setTheme,
    toggleTerminal,
    setLogs,
    setUnreadLogs,
  } = options;

  const [isOpen, setIsOpen] = useState(false);
  const [filter, setFilter] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);

  const commands: SlashCommand[] = useMemo(() => [
    // Tab switching commands
    {
      id: 'chat',
      name: 'chat',
      description: '切换到对话面板',
      icon: '💬',
      action: () => {
        setActiveTab('chat');
        return { success: true, message: '已切换到对话面板' };
      },
    },
    {
      id: 'terminal',
      name: 'terminal',
      description: '切换到终端面板',
      icon: '💻',
      action: () => {
        setActiveTab('terminal');
        return { success: true, message: '已切换到终端面板' };
      },
    },
    {
      id: 'files',
      name: 'files',
      description: '切换到文件面板',
      icon: '📁',
      action: () => {
        setActiveTab('files');
        return { success: true, message: '已切换到文件面板' };
      },
    },
    {
      id: 'models',
      name: 'models',
      description: '切换到模型面板',
      icon: '🧠',
      action: () => {
        setActiveTab('models');
        return { success: true, message: '已切换到模型面板' };
      },
    },
    {
      id: 'tools',
      name: 'tools',
      description: '切换到工具面板',
      icon: '🔧',
      action: () => {
        setActiveTab('tools');
        return { success: true, message: '已切换到工具面板' };
      },
    },
    {
      id: 'skills',
      name: 'skills',
      description: '切换到技能面板',
      icon: '🎯',
      action: () => {
        setActiveTab('skills');
        return { success: true, message: '已切换到技能面板' };
      },
    },
    {
      id: 'agents',
      name: 'agents',
      description: '切换到 Agents 面板',
      icon: '👥',
      action: () => {
        setActiveTab('agents');
        return { success: true, message: '已切换到 Agents 面板' };
      },
    },
    {
      id: 'tasks',
      name: 'tasks',
      description: '切换到任务面板',
      icon: '📋',
      action: () => {
        setActiveTab('tasks');
        return { success: true, message: '已切换到任务面板' };
      },
    },
    {
      id: 'settings',
      name: 'settings',
      description: '切换到设置面板',
      icon: '⚙️',
      action: () => {
        setActiveTab('settings');
        return { success: true, message: '已切换到设置面板' };
      },
    },
    {
      id: 'logs',
      name: 'logs',
      description: '切换到日志面板',
      icon: '📜',
      action: () => {
        setActiveTab('logs');
        setUnreadLogs(0);
        return { success: true, message: '已切换到日志面板' };
      },
    },
    // Action commands
    {
      id: 'new',
      name: 'new',
      description: '创建新会话',
      icon: '✨',
      action: () => {
        createNewSession();
        return { success: true, message: '新会话已创建' };
      },
    },
    {
      id: 'clear',
      name: 'clear',
      description: '清空当前会话消息',
      icon: '🗑️',
      action: () => {
        clearMessages();
        return { success: true, message: '消息已清空' };
      },
    },
    {
      id: 'theme',
      name: 'theme',
      description: '切换主题 (dark/light/auto)',
      args: '<dark|light|auto>',
      icon: '🎨',
      action: (args) => {
        const theme = args.trim().toLowerCase() as 'dark' | 'light' | 'auto';
        if (!theme || !['dark', 'light', 'auto'].includes(theme)) {
          return { success: false, message: '用法: /theme <dark|light|auto>' };
        }
        setTheme(theme);
        return { success: true, message: `主题已切换为 ${theme}` };
      },
    },
    {
      id: 'terminal-toggle',
      name: 'terminal-toggle',
      description: '切换底部终端面板显示',
      icon: '🔲',
      action: () => {
        toggleTerminal();
        return { success: true, message: '终端面板已切换' };
      },
    },
    {
      id: 'clear-logs',
      name: 'clear-logs',
      description: '清空日志',
      icon: '📭',
      action: () => {
        setLogs([]);
        setUnreadLogs(0);
        return { success: true, message: '日志已清空' };
      },
    },
    {
      id: 'help',
      name: 'help',
      description: '显示所有快捷命令',
      icon: '❓',
      action: () => {
        return { success: true, message: '' };
      },
    },
  ], [setActiveTab, createNewSession, clearMessages, setTheme, toggleTerminal, setLogs, setUnreadLogs]);

  const filteredCommands = useMemo(() => {
    if (!filter) return commands;
    const lowerFilter = filter.toLowerCase();
    return commands.filter(
      (cmd) =>
        cmd.name.toLowerCase().includes(lowerFilter) ||
        cmd.description.toLowerCase().includes(lowerFilter)
    );
  }, [commands, filter]);

  const openMenu = useCallback(() => {
    setIsOpen(true);
    setFilter('');
    setSelectedIndex(0);
  }, []);

  const closeMenu = useCallback(() => {
    setIsOpen(false);
    setFilter('');
    setSelectedIndex(0);
  }, []);

  const selectNext = useCallback(() => {
    setSelectedIndex((prev) =>
      prev >= filteredCommands.length - 1 ? 0 : prev + 1
    );
  }, [filteredCommands.length]);

  const selectPrev = useCallback(() => {
    setSelectedIndex((prev) =>
      prev <= 0 ? filteredCommands.length - 1 : prev - 1
    );
  }, [filteredCommands.length]);

  const executeCommand = useCallback(
    (command: SlashCommand, args: string) => {
      const result = command.action(args);
      closeMenu();
      return result;
    },
    [closeMenu]
  );

  // Scroll selected item into view
  useEffect(() => {
    if (isOpen && containerRef.current) {
      const selectedEl = containerRef.current.querySelector(
        `[data-index="${selectedIndex}"]`
      );
      if (selectedEl) {
        selectedEl.scrollIntoView({ block: 'nearest' });
      }
    }
  }, [selectedIndex, isOpen]);

  return {
    isOpen,
    filter,
    setFilter,
    selectedIndex,
    setSelectedIndex,
    filteredCommands,
    openMenu,
    closeMenu,
    selectNext,
    selectPrev,
    executeCommand,
    containerRef,
    commands,
  };
}

/**
 * Parse input to check if it's a slash command.
 * Returns null if not a command, otherwise returns { command, args }.
 */
export function parseSlashCommand(input: string): { command: string; args: string } | null {
  const trimmed = input.trim();
  if (!trimmed.startsWith('/')) return null;

  const withoutSlash = trimmed.slice(1);
  const parts = withoutSlash.split(/\s+/);
  const command = parts[0];
  const args = parts.slice(1).join(' ');

  return { command, args };
}
