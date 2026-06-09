import { useHooksStore } from '../../stores/hooksStore';
import { ChevronDown, ChevronUp } from 'lucide-react';

interface Props {
  expanded: boolean;
  onToggle: () => void;
}

const DECISION_COLORS: Record<string, string> = {
  CREATE: 'text-green-400',
  UPDATE: 'text-yellow-400',
  DELETE: 'text-red-400',
  TOGGLE: 'text-blue-400',
  BATCH_DELETE: 'text-red-400',
  BATCH_TOGGLE: 'text-blue-400',
  IMPORT: 'text-purple-400',
  UPDATE_MAPPINGS: 'text-cyan-400',
};

export function HookLogsPanel({ expanded, onToggle }: Props) {
  const { logs } = useHooksStore();

  return (
    <div className="flex-shrink-0 border border-gray-700 rounded">
      <button
        onClick={onToggle}
        className="w-full flex items-center justify-between p-3 bg-gray-800 hover:bg-gray-750 rounded transition"
      >
        <span className="text-sm font-medium text-gray-300">
          管理操作日志 ({logs.length})
        </span>
        {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
      </button>
      {expanded && (
        <div className="max-h-48 overflow-auto">
          {logs.length === 0 ? (
            <div className="p-4 text-center text-sm text-gray-600">暂无操作记录</div>
          ) : (
            <table className="w-full text-xs">
              <thead className="text-gray-500">
                <tr>
                  <th className="p-2 text-left">时间</th>
                  <th className="p-2 text-left">操作</th>
                  <th className="p-2 text-left">目标</th>
                  <th className="p-2 text-left">详情</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-700">
                {logs.map(log => (
                  <tr key={log.id} className="text-gray-400">
                    <td className="p-2">{new Date(log.timestamp).toLocaleTimeString()}</td>
                    <td className={`p-2 ${DECISION_COLORS[log.decision] || ''}`}>{log.decision}</td>
                    <td className="p-2 text-gray-300">{log.hookName}</td>
                    <td className="p-2">{log.reason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}
