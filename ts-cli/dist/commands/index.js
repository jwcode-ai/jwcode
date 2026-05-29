// Local TUI commands
export const LOCAL_COMMANDS = [
    { cmd: '/help', desc: '显示所有命令', via: 'local', action: null },
    { cmd: '/plan', desc: '切换规划模式 (先规划再执行)', via: 'local', action: 'plan_mode' },
    { cmd: '/auto', desc: '切换自动模式 (自动批准工具执行)', via: 'local', action: 'auto_mode' },
    { cmd: '/context', desc: '显示当前会话状态', via: 'local', action: 'show_context' },
    { cmd: '/exit', desc: '退出 JWCode', via: 'local', action: '__exit__' },
];
// WebSocket commands sent to backend
export const WS_COMMANDS = [
    { cmd: '/confirm', desc: '确认当前规划并开始执行', via: 'ws', action: '__confirm_plan' },
    { cmd: '/cancel', desc: '取消当前规划', via: 'ws', action: '__cancel_plan' },
    { cmd: '/stop', desc: '停止当前 AI 生成', via: 'ws', action: 'stop' },
    { cmd: '/pause', desc: '暂停当前 AI 生成', via: 'ws', action: 'pause' },
    { cmd: '/resume', desc: '恢复暂停的 AI 生成', via: 'ws', action: 'resume' },
    { cmd: '/clear', desc: '清除当前会话消息', via: 'ws', action: 'clear' },
    { cmd: '/doctor', desc: '运行系统自诊断', via: 'ws', action: 'doctor' },
    { cmd: '/rewind', desc: '回滚到最近的检查点', via: 'ws', action: 'rewind' },
    { cmd: '/compact', desc: '压缩会话上下文 (释放 token)', via: 'ws', action: 'compact' },
    { cmd: '/model', desc: '切换 AI 模型 (用法: /model <模型名>)', via: 'ws', action: 'model_change' },
    { cmd: '/init', desc: '分析项目并生成 JWCODE.md 项目记忆文件', via: 'ws', action: 'init' },
    { cmd: '/effort', desc: '设置任务努力级别 (low/medium/high)', via: 'ws', action: 'effort' },
    { cmd: '/branch', desc: '创建分支会话 (用法: /branch <名称>)', via: 'ws', action: 'branch' },
    { cmd: '/mcp', desc: 'MCP 服务器管理 (list/add/remove)', via: 'ws', action: 'mcp' },
    { cmd: '/skills', desc: '查看可用 Skills 列表', via: 'ws', action: 'skills' },
    { cmd: '/agents', desc: '列出配置的 Agent 代理', via: 'ws', action: 'agents' },
    { cmd: '/config', desc: '管理配置 (get/set/list)', via: 'ws', action: 'config' },
    { cmd: '/plugin', desc: '插件管理 (install/list/remove)', via: 'ws', action: 'plugin' },
];
export const ALL_COMMANDS = [...LOCAL_COMMANDS, ...WS_COMMANDS];
// Action map: slash command -> { action, needsArg? }
export const SLASH_COMMANDS = {
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
    '/model': { action: 'model_change', needsArg: true },
    '/init': { action: 'init' },
    '/effort': { action: 'effort', needsArg: true },
    '/branch': { action: 'branch', needsArg: true },
    '/mcp': { action: 'mcp', needsArg: true },
    '/skills': { action: 'skills' },
    '/agents': { action: 'agents' },
    '/config': { action: 'config', needsArg: true },
    '/plugin': { action: 'plugin', needsArg: true },
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
║  /exit        退出 JWCode                ║
╠══════════════════════════════════════════╣
║  后端命令:                                ║
║  /confirm     确认执行当前规划            ║
║  /cancel      取消当前规划                ║
║  /stop        停止当前 AI 生成            ║
║  /pause       暂停当前 AI 生成            ║
║  /resume      恢复暂停的生成              ║
║  /clear       清除当前会话消息            ║
║  /model <名>  切换 AI 模型                ║
║  /compact     压缩会话上下文              ║
║  /doctor      系统自诊断                  ║
║  /rewind      回滚到最近检查点            ║
║  /init        生成项目 JWCODE.md           ║
║  /effort <级> 设置努力级别 low/med/high   ║
║  /branch <名> 创建分支会话                ║
║  /mcp <操作>  MCP 服务器管理              ║
║  /skills      查看 Skills 列表            ║
║  /agents      列出 Agent 代理             ║
║  /config <操> 管理配置 (get/set/list)     ║
║  /plugin <操> 插件管理                    ║
╠══════════════════════════════════════════╣
║  快捷键:                                  ║
║  ↑↓           浏览输入历史 (最近30条)     ║
║  PgUp/PgDn    翻页浏览消息                ║
║  Home/End     跳到最早/最新消息           ║
║  Tab          切换 Plan/Act 模式          ║
║  /            打开命令面板 (可翻页)        ║
║  Esc          关闭面板/取消审批            ║
╠══════════════════════════════════════════╣
║  普通输入即发送聊天消息                   ║
║  输入框显示字符数+token估算               ║
╚══════════════════════════════════════════╝`;
