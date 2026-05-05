import { ReactNode } from 'react';
import { cn } from '../../utils/cn';

/**
 * Badge - 标签组件
 */
interface BadgeProps {
  children: ReactNode;
  variant?: 'default' | 'success' | 'warning' | 'error' | 'info';
  size?: 'sm' | 'md';
  className?: string;
}

export function Badge({ 
  children, 
  variant = 'default',
  size = 'md',
  className 
}: BadgeProps) {
  const variants = {
    default: 'bg-dark-border text-dark-muted',
    success: 'bg-accent-green/20 text-accent-green',
    warning: 'bg-accent-yellow/20 text-accent-yellow',
    error: 'bg-accent-red/20 text-accent-red',
    info: 'bg-accent-blue/20 text-accent-blue',
  };

  const sizes = {
    sm: 'px-1.5 py-0.5 text-[10px]',
    md: 'px-2 py-0.5 text-xs',
  };

  return (
    <span className={cn(
      'inline-flex items-center rounded-full font-medium',
      variants[variant],
      sizes[size],
      className
    )}>
      {children}
    </span>
  );
}

/**
 * StatusBadge - 状态标签
 */
interface StatusBadgeProps {
  status: 'online' | 'offline' | 'busy' | 'away' | 'unknown';
  showText?: boolean;
  className?: string;
}

export function StatusBadge({ 
  status, 
  showText = true,
  className 
}: StatusBadgeProps) {
  const config = {
    online: { color: 'bg-green-500', text: '在线' },
    offline: { color: 'bg-gray-500', text: '离线' },
    busy: { color: 'bg-red-500', text: '忙碌' },
    away: { color: 'bg-yellow-500', text: '离开' },
    unknown: { color: 'bg-gray-400', text: '未知' },
  };

  const { color, text } = config[status];

  return (
    <span className={cn('flex items-center gap-1.5', className)}>
      <span className={cn('w-2 h-2 rounded-full', color)} />
      {showText && <span className="text-xs text-dark-muted">{text}</span>}
    </span>
  );
}

/**
 * CountBadge - 计数徽章
 */
interface CountBadgeProps {
  count: number;
  max?: number;
  className?: string;
}

export function CountBadge({ count, max = 99, className }: CountBadgeProps) {
  if (count <= 0) return null;
  
  const display = count > max ? `${max}+` : count;
  
  return (
    <span className={cn(
      'inline-flex items-center justify-center min-w-[20px] h-5 px-1.5',
      'bg-accent-red text-white text-xs font-bold rounded-full',
      className
    )}>
      {display}
    </span>
  );
}