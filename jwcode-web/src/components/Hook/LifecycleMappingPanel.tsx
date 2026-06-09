import { useState } from 'react';
import { useHooksStore } from '../../stores/hooksStore';
import type { HookEventCategory, HookAgentInfo } from '../../types';

interface Props {
  events: HookEventCategory[];
  agents: HookAgentInfo[];
}

/** Events relevant for lifecycle-to-agent mapping */
const MAPPABLE_EVENTS = [
  'SUBAGENT_START', 'SUBAGENT_STOP', 'TASK_CREATED', 'TASK_COMPLETED',
  'TASK_DISPATCH', 'USER_PROMPT_SUBMIT', 'TEAMMATE_IDLE',
  'STATE_TRANSITION', 'STATE_ENTERED',
];

export function LifecycleMappingPanel({ agents }: Props) {
  const { lifecycleMappings, saveLifecycleMappings } = useHooksStore();
  const [localMappings, setLocalMappings] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  // Initialize local state from store
  const combined = { ...lifecycleMappings, ...localMappings };

  const handleSave = async () => {
    setSaving(true);
    const mappingsToSave = { ...lifecycleMappings, ...localMappings };
    // Remove empty entries
    Object.keys(mappingsToSave).forEach(k => {
      if (!mappingsToSave[k]) delete mappingsToSave[k];
    });
    await saveLifecycleMappings(mappingsToSave);
    setLocalMappings({});
    setSaving(false);
  };

  const hasChanges = Object.keys(localMappings).length > 0;

  return (
    <div className="flex-shrink-0 border border-gray-700 rounded p-3">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-medium text-gray-300">生命周期 → Agent 映射</h3>
        <button
          onClick={handleSave}
          disabled={!hasChanges || saving}
          className={`px-3 py-1 text-xs rounded transition ${
            hasChanges
              ? 'bg-blue-600 hover:bg-blue-500 text-white'
              : 'bg-gray-700 text-gray-500 cursor-not-allowed'
          }`}
        >
          {saving ? '保存中...' : '保存映射'}
        </button>
      </div>
      <div className="grid grid-cols-2 gap-2">
        {MAPPABLE_EVENTS.map(eventName => (
          <div key={eventName} className="flex items-center justify-between bg-gray-800 rounded px-3 py-1.5">
            <span className="text-xs text-gray-400">{eventName}</span>
            <select
              value={combined[eventName] || ''}
              onChange={e => setLocalMappings(prev => ({ ...prev, [eventName]: e.target.value }))}
              className="bg-gray-700 border border-gray-600 rounded px-2 py-0.5 text-xs text-gray-200 w-32"
            >
              <option value="">不指定</option>
              {agents.map(a => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </select>
          </div>
        ))}
      </div>
    </div>
  );
}
