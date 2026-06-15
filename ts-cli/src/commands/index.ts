/**
 * Command definitions — Chinese descriptions.
 * Local commands handled by TUI; WS commands sent to backend.
 */
export interface CmdEntry {
  cmd: string;
  desc: string;
  via: 'local' | 'ws';
  action: string | null;
  category?: string;
  isAlias?: boolean;
}

// Local TUI commands
export const LOCAL_COMMANDS: CmdEntry[] = [
  { cmd: '/help', desc: '显示所有命令', via: 'local', action: null, category: '本地' },
  { cmd: '/plan', desc: '切换规划模式 (先规划再执行)', via: 'local', action: 'plan_mode', category: '本地' },
  { cmd: '/auto', desc: '切换自动模式 (自动批准工具执行)', via: 'local', action: 'auto_mode', category: '本地' },
  { cmd: '/context', desc: '显示当前会话状态', via: 'local', action: 'show_context', category: '本地' },
  { cmd: '/config-ui', desc: '显示配置面板 (连接/模型/主题/用量)', via: 'local', action: 'show_config', category: '本地' },
  { cmd: '/exit', desc: '退出 JWCode', via: 'local', action: '__exit__', category: '本地' },
  { cmd: '/quit', desc: '退出 JWCode (别名)', via: 'local', action: '__exit__', category: '本地', isAlias: true },
];

// WebSocket commands sent to backend
export const WS_COMMANDS: CmdEntry[] = [
  { cmd: '/confirm', desc: '确认当前规划并开始执行', via: 'ws', action: '__confirm_plan', category: '规划' },
  { cmd: '/cancel', desc: '取消当前规划', via: 'ws', action: '__cancel_plan', category: '规划' },
  { cmd: '/stop', desc: '停止当前 AI 生成', via: 'ws', action: 'stop', category: '控制' },
  { cmd: '/pause', desc: '暂停当前 AI 生成', via: 'ws', action: 'pause', category: '控制' },
  { cmd: '/resume', desc: '恢复暂停的 AI 生成', via: 'ws', action: 'resume', category: '控制' },
  { cmd: '/clear', desc: '清除当前会话消息', via: 'ws', action: 'clear', category: '控制' },
  { cmd: '/doctor', desc: '运行系统自诊断', via: 'ws', action: 'doctor', category: '诊断' },
  { cmd: '/rewind', desc: '回滚到最近的检查点', via: 'ws', action: 'rewind', category: '控制' },
  { cmd: '/compact', desc: '压缩会话上下文 (释放 token)', via: 'ws', action: 'compact', category: '控制' },
  { cmd: '/model', desc: '切换 AI 模型 (用法: /model <模型名> 或 /model 打开选择器)', via: 'ws', action: 'model_change', category: '模型' },
  { cmd: '/setup', desc: '配置 AI 提供商和 API Key', via: 'local', action: 'setup_wizard', category: '配置' },
  { cmd: '/init', desc: '分析项目并生成 agent.md 项目记忆文件', via: 'local', action: 'init_project', category: '项目' },
  { cmd: '/effort', desc: '设置任务努力级别 (low/medium/high)', via: 'ws', action: 'effort', category: '控制' },
  { cmd: '/branch', desc: '创建分支会话 (用法: /branch <名称>)', via: 'ws', action: 'branch', category: '会话' },
  { cmd: '/mcp', desc: 'MCP 服务器管理 (list/add/remove)', via: 'ws', action: 'mcp', category: '工具' },
  { cmd: '/skills', desc: '查看可用 Skills 列表', via: 'ws', action: 'skills', category: '工具' },
  { cmd: '/agents', desc: '列出配置的 Agent 代理', via: 'ws', action: 'agents', category: '工具' },
  { cmd: '/config', desc: '管理配置 (get/set/list)', via: 'ws', action: 'config', category: '配置' },
  { cmd: '/plugin', desc: '插件管理 (install/list/remove)', via: 'ws', action: 'plugin', category: '工具' },
  { cmd: '/tokens', desc: '显示 Token 使用详情', via: 'ws', action: 'tokens', category: '诊断' },
  { cmd: '/memory', desc: '浏览项目记忆', via: 'ws', action: 'memory', category: '项目' },
  { cmd: '/export', desc: '导出会话 (用法: /export <路径>)', via: 'ws', action: 'export', category: '会话' },
  { cmd: '/checkpoint', desc: '设置或恢复检查点', via: 'ws', action: 'checkpoint', category: '控制' },
  { cmd: '/test', desc: '运行当前项目测试', via: 'ws', action: 'test', category: '诊断' },
  { cmd: '/lint', desc: '对变更文件运行 Linter', via: 'ws', action: 'lint', category: '诊断' },
  { cmd: '/search', desc: '搜索代码库 (用法: /search <关键词>)', via: 'ws', action: 'search', category: '项目' },
  { cmd: '/project', desc: '生成项目文档', via: 'ws', action: 'project', category: '项目' },
];

export const ALL_COMMANDS: CmdEntry[] = [...LOCAL_COMMANDS, ...WS_COMMANDS];

// Action map: slash command -> { action, needsArg? }
export const SLASH_COMMANDS: Record<string, { action: string; needsArg?: boolean } | null> = {
  '/help': null,
  '/plan': { action: 'plan_mode' },
  '/auto': { action: 'auto_mode' },
  '/context': { action: 'show_context' },
  '/exit': { action: '__exit__' },
  '/quit': { action: '__exit__' },
  '/confirm': { action: '__confirm_plan' },
  '/cancel': { action: '__cancel_plan' },
  '/stop': { action: 'stop' },
  '/pause': { action: 'pause' },
  '/resume': { action: 'resume' },
  '/clear': { action: 'clear' },
  '/doctor': { action: 'doctor' },
  '/rewind': { action: 'rewind' },
  '/compact': { action: 'compact' },
  '/model': { action: 'model_change', needsArg: false },
  '/setup': { action: 'setup_wizard' },
  '/init': { action: 'init_project' },
  '/effort': { action: 'effort', needsArg: true },
  '/branch': { action: 'branch', needsArg: true },
  '/mcp': { action: 'mcp', needsArg: true },
  '/skills': { action: 'skills' },
  '/agents': { action: 'agents' },
  '/config': { action: 'config', needsArg: true },
  '/config-ui': { action: 'show_config' },
  '/plugin': { action: 'plugin', needsArg: true },
  '/tokens': { action: 'tokens' },
  '/memory': { action: 'memory' },
  '/export': { action: 'export', needsArg: true },
  '/checkpoint': { action: 'checkpoint' },
  '/test': { action: 'test' },
  '/lint': { action: 'lint' },
  '/search': { action: 'search', needsArg: true },
  '/project': { action: 'project' },
};

export const HELP_TEXT = `
╔══════════════════════════════════════════╗
║        JWCode 命令帮助                    ║
╠══════════════════════════════════════════╣
║  本地命令:                                ║
║  /help        显示此帮助信息              ║
║  /plan        切换规划模式                ║
║  /auto        切换自动模式                ║
║  /context     显示当前会话状态            ║
║  /config-ui   显示配置面板                ║
║  /init        生成项目 agent.md           ║
║  /exit        退出 JWCode                ║
╠══════════════════════════════════════════╣
║  后端命令:                                ║
║  /confirm     确认执行当前规划            ║
║  /cancel      取消当前规划                ║
║  /stop        停止当前 AI 生成            ║
║  /pause       暂停当前 AI 生成            ║
║  /resume      恢复暂停的生成              ║
║  /clear       清除当前会话消息            ║
║  /model [名]  切换/选择 AI 模型            ║
║  /setup       配置 AI 提供商和 API Key      ║
║  /compact     压缩会话上下文              ║
║  /doctor      系统自诊断                  ║
║  /rewind      回滚到最近检查点            ║
║  /effort <级> 设置努力级别 low/med/high   ║
║  /branch <名> 创建分支会话                ║
║  /mcp <操作>  MCP 服务器管理              ║
║  /skills      查看 Skills 列表            ║
║  /agents      列出 Agent 代理             ║
║  /config <操> 管理配置 (get/set/list)     ║
║  /plugin <操> 插件管理                    ║
║  /tokens      显示 Token 使用详情         ║
║  /memory      浏览项目记忆                ║
║  /export <路> 导出会话到文件              ║
║  /checkpoint  设置或恢复检查点            ║
║  /test        运行当前项目测试            ║
║  /lint        对变更文件运行 Linter       ║
║  /search <词> 搜索代码库                  ║
║  /project     生成项目文档                ║
╠══════════════════════════════════════════╣
║  快捷键:                                  ║
║  ↑↓           浏览输入历史 (最近30条)     ║
║  Ctrl+I       显示配置面板                ║
║  PgUp/PgDn    翻页浏览消息                ║
║  Home/End     跳到最早/最新消息           ║
║  Tab          切换 Plan/Act 模式          ║
║  /            打开命令面板 (可翻页)        ║
║  Esc          关闭面板/取消审批            ║
╠══════════════════════════════════════════╣
║  普通输入即发送聊天消息                   ║
║  输入框显示字符数+token估算               ║
╚══════════════════════════════════════════╝`;
