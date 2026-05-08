import { memo, useRef } from 'react';
import { SessionTab, TabId, LogEntry } from '../../types';
import { ChatPanel } from './ChatPanel';
import { useChatStore } from '../../stores/chatStore';

interface SessionGridProps {
  tabs: SessionTab[];
  activeSessionId: string | null;
  onSend: (sessionId: string, content: string) => void;
  onSwitch: (sessionId: string) => void;
  // 传递给每个 ChatPanel 用于独立创建 slashCommands
  activeTab: TabId;
  setActiveTab: (tab: TabId) => void;
  createNewSession: () => void;
  clearMessages: () => void;
  setTheme: (theme: 'dark' | 'light' | 'auto') => void;
  toggleTerminal: () => void;
  setLogs: React.Dispatch<React.SetStateAction<LogEntry[]>>;
  setUnreadLogs: React.Dispatch<React.SetStateAction<number>>;
}

export const SessionGrid = memo(function SessionGrid({
  tabs,
  activeSessionId,
  onSend,
  activeTab,
  setActiveTab,
  createNewSession,
  clearMessages,
  setTheme,
  toggleTerminal,
  setLogs,
  setUnreadLogs,
}: SessionGridProps) {

  // 直接订阅响应式 state，而不是函数引用
  // 这样当 messagesBySession 或 generatingSessions 变化时，React 会触发重渲染
  const messagesBySession = useChatStore((s) => s.messagesBySession);
  const generatingSessions = useChatStore((s) => s.generatingSessions);
  const setSessionInput = useChatStore((s) => s.setSessionInput);
  const getSessionInput = useChatStore((s) => s.getSessionInput);

  // 每个面板独立的 ref
  const messagesEndRefs = useRef<Record<string, HTMLDivElement>>({});

  return (
    <div className="flex-1 flex flex-col overflow-hidden min-h-0">
      {tabs
        .filter((tab) => tab.id === activeSessionId)
        .map((tab) => {
          const messages = messagesBySession[tab.id] || [];
          const generating = generatingSessions.includes(tab.id);
          const input = getSessionInput(tab.id);

          return (
            <ChatPanel
              key={tab.id}
              messages={messages}
              isGenerating={generating}
              onSend={(content) => onSend(tab.id, content)}
              input={input}
              setInput={(val) => setSessionInput(tab.id, val)}
              messagesEndRef={{
                current: messagesEndRefs.current[tab.id] || null,
              } as React.MutableRefObject<HTMLDivElement | null>}
              sessionId={tab.id}
              activeTab={activeTab}
              setActiveTab={setActiveTab}
              createNewSession={createNewSession}
              clearMessages={clearMessages}
              setTheme={setTheme}
              toggleTerminal={toggleTerminal}
              setLogs={setLogs}
              setUnreadLogs={setUnreadLogs}
            />
          );
        })}
    </div>
  );
});
