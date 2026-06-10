export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export function classifyRisk(toolName: string, askPayload: string): { level: RiskLevel; reason: string } {
  const name = toolName.toLowerCase();
  if (/\b(rm|del|delete|drop|truncate|format|mkfs)\b/.test(name)) {
    return { level: 'CRITICAL', reason: '破坏性操作 - 可能删除数据' };
  }
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/.test(name)) {
    if (/\b(rm\s+-rf|sudo|chmod\s+777|curl.*\|\s*(ba)?sh|wget.*-O|>\/dev\/|mkfs)\b/i.test(askPayload)) {
      return { level: 'CRITICAL', reason: '高危命令 - 包含系统级操作' };
    }
    return { level: 'HIGH', reason: 'Shell 命令执行' };
  }
  if (/\b(Write|Edit|save|upload|deploy|publish)\b/i.test(name)) {
    return { level: 'HIGH', reason: '文件写入操作' };
  }
  if (/\b(install|uninstall|npm|pip|cargo|gem|apt|brew|choco|yum|dnf)\b/i.test(name)) {
    return { level: 'HIGH', reason: '包管理操作' };
  }
  if (/\b(git|commit|push|merge|rebase)\b/i.test(name)) {
    const isDestructive = /\b(push|force|hard\s*reset|rebase)\b/i.test(askPayload);
    return { level: isDestructive ? 'HIGH' : 'MEDIUM', reason: isDestructive ? 'Git 强制操作' : 'Git 操作' };
  }
  if (/\b(http|fetch|curl|wget|api|request|download)\b/i.test(name)) {
    return { level: 'MEDIUM', reason: '网络请求' };
  }
  if (/\b(read|open|list|ls|dir|cat|view|search|find|grep|glob)\b/i.test(name)) {
    return { level: 'LOW', reason: '只读操作' };
  }
  return { level: 'MEDIUM', reason: '工具调用' };
}

export const RISK_CONFIG: Record<RiskLevel, { bg: string; border: string; text: string; badge: string; icon: string }> = {
  CRITICAL: { bg: 'bg-red-900/20', border: 'border-red-500/40', text: 'text-red-400', badge: 'bg-red-500/20 text-red-400 border-red-500/30', icon: '⛔' },
  HIGH:     { bg: 'bg-orange-900/15', border: 'border-orange-500/30', text: 'text-orange-400', badge: 'bg-orange-500/20 text-orange-400 border-orange-500/30', icon: '⚠️' },
  MEDIUM:   { bg: 'bg-yellow-900/10', border: 'border-yellow-500/25', text: 'text-yellow-400', badge: 'bg-yellow-500/15 text-yellow-400 border-yellow-500/25', icon: '⚡' },
  LOW:      { bg: 'bg-blue-900/10', border: 'border-blue-500/25', text: 'text-blue-400', badge: 'bg-blue-500/15 text-blue-400 border-blue-500/25', icon: '📋' },
};

export function extractPreview(toolName: string, askPayload: string): string | null {
  if (/\b(bash|shell|exec|cmd|powershell|terminal)\b/i.test(toolName)) {
    return askPayload || null;
  }
  if (/\b(write|edit|save|create)\b/i.test(toolName)) {
    const fileMatch = askPayload.match(/(?:file_path|path|file)["\s:=]+([^\s",}]+)/i);
    if (fileMatch) {
      return `📄 ${fileMatch[1]}\n${askPayload.slice(0, 200)}`;
    }
    return askPayload.slice(0, 200) || null;
  }
  if (askPayload && askPayload.length > 0) {
    return askPayload.length > 200 ? askPayload.slice(0, 200) + '...' : askPayload;
  }
  return null;
}
