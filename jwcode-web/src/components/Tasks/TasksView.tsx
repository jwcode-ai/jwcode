import { useState, useEffect } from 'react';
import { api, Task, TaskStatus, CreateTaskInput } from '../../services/api';

// 状态颜色映射
const statusColors: Record<TaskStatus, string> = {
  PENDING: 'text-yellow-500',
  RUNNING: 'text-blue-500',
  COMPLETED: 'text-green-500',
  FAILED: 'text-red-500',
  CANCELLED: 'text-gray-500',
};

const statusLabels: Record<TaskStatus, string> = {
  PENDING: '待处理',
  RUNNING: '进行中',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
};

export function TasksView() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [newTask, setNewTask] = useState<CreateTaskInput>({ title: '', description: '' });

  // 加载任务列表
  useEffect(() => {
    loadTasks();
  }, []);

  const loadTasks = async () => {
    setLoading(true);
    setError(null);
    const result = await api.tasks.list();
    if (result.success && result.data) {
      setTasks(result.data);
    } else {
      setError(result.error || '加载失败');
    }
    setLoading(false);
  };

  const handleCreate = async () => {
    if (!newTask.title.trim()) return;
    const result = await api.tasks.create(newTask);
    if (result.success) {
      setShowCreate(false);
      setNewTask({ title: '', description: '' });
      loadTasks();
    }
  };

  const handleStatusChange = async (id: string, status: TaskStatus) => {
    const result = await api.tasks.updateStatus(id, status);
    if (result.success) {
      loadTasks();
    }
  };

  const handleDelete = async (id: string) => {
    const result = await api.tasks.delete(id);
    if (result.success) {
      loadTasks();
    }
  };

  const handleClearCompleted = async () => {
    const result = await api.tasks.clearCompleted();
    if (result.success) {
      loadTasks();
    }
  };

  const activeTasks = tasks.filter(t => t.status === 'PENDING' || t.status === 'RUNNING');
  const completedTasks = tasks.filter(t => t.status === 'COMPLETED' || t.status === 'FAILED' || t.status === 'CANCELLED');

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">任务管理</h1>
        <div className="flex gap-2">
          <button
            onClick={handleClearCompleted}
            className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded"
          >
            清除已完成
          </button>
          <button
            onClick={() => setShowCreate(true)}
            className="px-4 py-2 text-sm bg-blue-500 text-white rounded hover:bg-blue-600"
          >
            + 新建任务
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-4 p-4 bg-red-50 text-red-600 rounded">
          {error}
        </div>
      )}

      {/* 创建任务表单 */}
      {showCreate && (
        <div className="mb-6 p-4 bg-gray-50 rounded-lg">
          <h3 className="font-semibold mb-3">新建任务</h3>
          <input
            type="text"
            placeholder="任务标题"
            value={newTask.title}
            onChange={e => setNewTask({ ...newTask, title: e.target.value })}
            className="w-full px-3 py-2 border rounded mb-2"
          />
          <textarea
            placeholder="任务描述（可选）"
            value={newTask.description}
            onChange={e => setNewTask({ ...newTask, description: e.target.value })}
            className="w-full px-3 py-2 border rounded mb-3"
            rows={2}
          />
          <div className="flex gap-2">
            <button
              onClick={handleCreate}
              className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              创建
            </button>
            <button
              onClick={() => setShowCreate(false)}
              className="px-4 py-2 text-gray-600 hover:bg-gray-200 rounded"
            >
              取消
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <div className="text-center py-8 text-gray-500">加载中...</div>
      ) : tasks.length === 0 ? (
        <div className="text-center py-8 text-gray-500">
          暂无任务
        </div>
      ) : (
        <div className="space-y-6">
          {/* 活跃任务 */}
          {activeTasks.length > 0 && (
            <div>
              <h2 className="text-lg font-semibold mb-3">进行中的任务 ({activeTasks.length})</h2>
              <div className="space-y-2">
                {activeTasks.map(task => (
                  <TaskItem
                    key={task.id}
                    task={task}
                    onStatusChange={handleStatusChange}
                    onDelete={handleDelete}
                  />
                ))}
              </div>
            </div>
          )}

          {/* 已完成任务 */}
          {completedTasks.length > 0 && (
            <div>
              <h2 className="text-lg font-semibold mb-3 text-gray-600">
                已完成的任务 ({completedTasks.length})
              </h2>
              <div className="space-y-2 opacity-75">
                {completedTasks.map(task => (
                  <TaskItem
                    key={task.id}
                    task={task}
                    onStatusChange={handleStatusChange}
                    onDelete={handleDelete}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// 单个任务项
interface TaskItemProps {
  task: Task;
  onStatusChange: (id: string, status: TaskStatus) => void;
  onDelete: (id: string) => void;
}

function TaskItem({ task, onStatusChange, onDelete }: TaskItemProps) {
  const [showActions, setShowActions] = useState(false);

  return (
    <div
      className="p-4 bg-white border rounded-lg hover:shadow-md transition-shadow"
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => setShowActions(false)}
    >
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h3 className="font-medium">{task.title}</h3>
            <span className={`text-xs font-medium ${statusColors[task.status]}`}>
              {statusLabels[task.status]}
            </span>
          </div>
          {task.description && (
            <p className="text-sm text-gray-500 mt-1">{task.description}</p>
          )}
          <div className="flex items-center gap-4 mt-2 text-xs text-gray-400">
            <span>优先级: {task.priority}</span>
            {task.progress > 0 && <span>进度: {task.progress}%</span>}
            <span>创建: {new Date(task.createdAt).toLocaleDateString()}</span>
          </div>
        </div>

        {showActions && (
          <div className="flex gap-1">
            {task.status === 'PENDING' && (
              <button
                onClick={() => onStatusChange(task.id, 'RUNNING')}
                className="px-2 py-1 text-xs bg-blue-100 text-blue-600 rounded hover:bg-blue-200"
              >
                开始
              </button>
            )}
            {task.status === 'RUNNING' && (
              <button
                onClick={() => onStatusChange(task.id, 'COMPLETED')}
                className="px-2 py-1 text-xs bg-green-100 text-green-600 rounded hover:bg-green-200"
              >
                完成
              </button>
            )}
            {(task.status === 'PENDING' || task.status === 'RUNNING') && (
              <button
                onClick={() => onStatusChange(task.id, 'CANCELLED')}
                className="px-2 py-1 text-xs bg-gray-100 text-gray-600 rounded hover:bg-gray-200"
              >
                取消
              </button>
            )}
            <button
              onClick={() => onDelete(task.id)}
              className="px-2 py-1 text-xs bg-red-100 text-red-600 rounded hover:bg-red-200"
            >
              删除
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default TasksView;
