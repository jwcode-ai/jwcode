import { ReactNode } from 'react';
import { cn } from '../../utils/cn';

/**
 * Avatar - 头像组件
 */
interface AvatarProps {
  src?: string;
  alt?: string;
  name?: string;
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  className?: string;
}

export function Avatar({ src, alt, name, size = 'md', className }: AvatarProps) {
  const sizes = {
    xs: 'w-6 h-6 text-xs',
    sm: 'w-8 h-8 text-sm',
    md: 'w-10 h-10 text-base',
    lg: 'w-12 h-12 text-lg',
    xl: 'w-16 h-16 text-xl',
  };

  // 获取名称首字母
  const getInitials = (name?: string) => {
    if (!name) return '?';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  };

  // 生成随机背景色
  const colors = [
    'bg-accent-blue',
    'bg-accent-green',
    'bg-accent-red',
    'bg-accent-yellow',
    'bg-purple-500',
    'bg-pink-500',
    'bg-indigo-500',
  ];
  
  const getColor = (name?: string) => {
    if (!name) return colors[0];
    const index = name.charCodeAt(0) % colors.length;
    return colors[index];
  };

  return (
    <div className={cn(
      'relative rounded-full overflow-hidden flex items-center justify-center font-medium',
      sizes[size],
      !src && getColor(name),
      className
    )}>
      {src ? (
        <img src={src} alt={alt || name || 'avatar'} className="w-full h-full object-cover" />
      ) : (
        <span className="text-white">{getInitials(name)}</span>
      )}
    </div>
  );
}

/**
 * AvatarGroup - 头像组
 */
interface AvatarGroupProps {
  children: ReactNode;
  max?: number;
  size?: 'xs' | 'sm' | 'md' | 'lg';
  className?: string;
}

export function AvatarGroup({ children, max = 4, size = 'md', className }: AvatarGroupProps) {
  const childArray = Array.isArray(children) ? children : [children];
  const visibleAvatars = childArray.slice(0, max);
  const remainingCount = childArray.length - max;

  const sizeClasses = {
    xs: 'w-6 h-6 text-xs -ml-2',
    sm: 'w-8 h-8 text-sm -ml-2',
    md: 'w-10 h-10 text-base -ml-3',
    lg: 'w-12 h-12 text-lg -ml-4',
  };

  return (
    <div className={cn('flex items-center', className)}>
      {visibleAvatars}
      {remainingCount > 0 && (
        <div className={cn(
          'rounded-full bg-dark-border flex items-center justify-center text-white font-medium',
          sizeClasses[size]
        )}>
          +{remainingCount}
        </div>
      )}
    </div>
  );
}