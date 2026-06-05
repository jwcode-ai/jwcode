import { useCallback, useMemo, useState, useRef, useEffect } from 'react';
import { TabId } from '../types';
import { useCommandStore, BackendCommand } from '../stores/commandStore';
import wsService from '../services/websocket';
import { useSessionStore } from '../stores/sessionStore';

export interface SlashCommand {
  id: string;
  name: string;
  description: string;
  args?: string;
  icon: string;
  /** true = 本地执行, false = 发送到后端执行 */
  local: boolean;
  action: (args: string) => { success: boolean; message?: string };
}

export interface UseSlashCommandsOptions {
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
    setActiveTab,
    createNewSession,
    clearMessages,
    setTheme,
    toggleTerminal,
    setLogs,
    setUnreadLogs,
  } = options;

  // 从 store 获取后端命令
  const backendCommands = useCommandStore((s) => s.backendCommands);

  const [isOpen, setIsOpen] = useState(false);
  const [filter, setFilter] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);

  // 将命令通过 chat 消息发送到后端执行
  const sendToBackend = useCallback((cmd: string, args?: string) => {
    const sessionId = useSessionStore.getState().activeSessionId;
    if (sessionId) {
      wsService.setSessionId(sessionId);
      wsService.send({
        type: 'chat',
        sessionId,
        message: args ? `/${cmd} ${args}` : `/${cmd}`,
      });
      return { success: true, message: `已发送命令 /${cmd}` };
    }
    return { success: false, message: '无活跃会话' };
  }, []);

  const commands: SlashCommand[] = useMemo(() => [
    // Tab switching commands
    {
      id: 'chat',
      name: 'chat',
      description: '切换到对话面板',
      icon: '💬',
      local: true,
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
      local: true,
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
      local: true,
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
      local: true,
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
      local: true,
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
      local: true,
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
      local: true,
      action: () => {
        setActiveTab('agents');
        return { success: true, message: '已切换到 Agents 面板' };
      },
    },
    {
      id: 'settings',
      name: 'settings',
      description: '切换到设置面板',
      icon: '⚙️',
      local: true,
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
      local: true,
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
      local: true,
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
      local: true,
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
      local: true,
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
      local: true,
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
      local: true,
      action: () => {
        setLogs([]);
        setUnreadLogs(0);
        return { success: true, message: '日志已清空' };
      },
    },
    {
      id: 'doctor',
      name: 'doctor',
      description: '系统自诊断 (Java/Maven/Config/API Key/Network/Docker/Disk)',
      icon: '🩺',
      local: false,
      action: () => sendToBackend('doctor'),
    },
    {
      id: 'compact',
      name: 'compact',
      description: '压缩当前会话上下文',
      args: '[aggressive|summary]',
      icon: '🗜️',
      local: false,
      action: (args) => sendToBackend('compact', args),
    },
    {
      id: 'help',
      name: 'help',
      description: '显示所有快捷命令',
      icon: '❓',
      local: true,
      action: () => { return { success: true, message: '' }; },
    },
    // ============== 后端命令 ==============
    {
      id: 'cost',
      name: 'cost',
      description: '显示 token 使用量和 API 费用统计',
      args: '[detail|reset]',
      icon: '💰',
      local: false,
      action: (args) => sendToBackend('cost', args),
    },
    {
      id: 'review',
      name: 'review',
      description: '代码审查 — 检查变更的正确性、安全性和代码质量',
      args: '[file|diff|all] [--level low|medium|high]',
      icon: '🔍',
      local: false,
      action: (args) => sendToBackend('review', args),
    },
    {
      id: 'security-review',
      name: 'security-review',
      description: '安全审查 — OWASP Top 10 + 命令注入/路径遍历/权限提升',
      args: '[file|diff|full]',
      icon: '🔒',
      local: false,
      action: (args) => sendToBackend('security-review', args),
    },
    {
      id: 'memory',
      name: 'memory',
      description: '管理 AI 持久记忆 (list/add/delete/clear)',
      args: '[list|add|delete|clear]',
      icon: '🧠',
      local: false,
      action: (args) => sendToBackend('memory', args),
    },
    {
      id: 'tasks',
      name: 'tasks',
      description: '查看和管理后台任务、子代理和 shell 进程',
      args: '[list|stop|output]',
      icon: '📋',
      local: false,
      action: (args) => sendToBackend('tasks', args),
    },
    {
      id: 'model',
      name: 'model',
      description: '查看或切换 AI 模型',
      args: '[model-name]',
      icon: '🤖',
      local: false,
      action: (args) => sendToBackend('model', args),
    },
    {
      id: 'status',
      name: 'status',
      description: '显示当前会话状态 (会话ID/消息数/模型/工作目录)',
      icon: '📊',
      local: false,
      action: () => sendToBackend('status'),
    },
    {
      id: 'config',
      name: 'config',
      description: '管理应用程序配置 (get/set/list/delete)',
      args: '<get|set|list|delete> [key] [value]',
      icon: '🔧',
      local: false,
      action: (args) => sendToBackend('config', args),
    },
    {
      id: 'exit',
      name: 'exit',
      description: '退出应用',
      icon: '🚪',
      local: false,
      action: () => {
        const sessionId = useSessionStore.getState().activeSessionId;
        if (sessionId) {
          wsService.setSessionId(sessionId);
          wsService.send({ type: 'exit', sessionId });
        }
        return { success: true, message: '正在关闭服务...' };
      },
    },
    {
      id: 'test',
      name: 'test',
      description: '运行 JWCode 能力评测 (simple/medium/complex/full/list)',
      args: '[simple|medium|complex|full|list|help]',
      icon: '🧪',
      local: false,
      action: (args) => sendToBackend('test', args),
    },
  ], [setActiveTab, createNewSession, clearMessages, setTheme, toggleTerminal, setLogs, setUnreadLogs, sendToBackend]);

  // 将后端命令转换为 SlashCommand 格式
  const backendSlashCommands: SlashCommand[] = useMemo(() => {
    return backendCommands.map((bc: BackendCommand) => ({
      id: `backend-${bc.name}`,
      name: bc.name,
      description: bc.description || bc.usage || '后端命令',
      icon: '🔌',
      local: false,
      action: (args: string) => {
        // 后端命令通过 WebSocket 发送到后端执行
        const sessionId = useSessionStore.getState().activeSessionId;
        if (sessionId) {
          wsService.setSessionId(sessionId);
          wsService.send({
            type: 'chat',
            sessionId,
            message: `/${bc.name}${args ? ' ' + args : ''}`,
          });
          return { success: true, message: `已发送命令 /${bc.name}` };
        }
        return { success: false, message: '无活跃会话' };
      },
    }));
  }, [backendCommands]);

  const allCommands = useMemo(() => {
    // 合并本地命令和后端命令，后端命令排后面
    return [...commands, ...backendSlashCommands];
  }, [commands, backendSlashCommands]);

  const filteredCommands = useMemo(() => {
    if (!filter) return allCommands;
    const lowerFilter = filter.toLowerCase();
    return allCommands.filter(
      (cmd) =>
        cmd.name.toLowerCase().includes(lowerFilter) ||
        cmd.description.toLowerCase().includes(lowerFilter)
    );
  }, [allCommands, filter]);

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
  const command = parts[0] || '';
  const args = parts.slice(1).join(' ');

  return { command, args };
}
