import { cn } from '../../utils/cn';

interface SkeletonProps {
  className?: string;
  variant?: 'text' | 'circular' | 'rectangular';
  width?: string | number;
  height?: string | number;
  shimmer?: boolean;
}

/**
 * Skeleton - 加载占位动画组件
 * 用于数据加载时显示的占位符
 */
export function Skeleton({
  className,
  variant = 'rectangular',
  width,
  height,
  shimmer = false,
}: SkeletonProps) {
  const variantStyles = {
    text: 'rounded h-4',
    circular: 'rounded-full',
    rectangular: 'rounded-lg',
  };

  return (
    <div
      aria-hidden="true"
      className={cn(
        shimmer ? 'skeleton-shimmer' : 'animate-pulse bg-dark-border/50',
        variantStyles[variant],
        className
      )}
      style={{ width, height }}
    />
  );
}

/**
 * SkeletonCard - 卡片占位符
 */
export function SkeletonCard({ className }: { className?: string }) {
  return (
    <div className={cn('bg-dark-surface border border-dark-border rounded-lg p-4 space-y-3', className)}>
      <Skeleton variant="text" width="60%" height={20} />
      <Skeleton variant="text" width="100%" />
      <Skeleton variant="text" width="80%" />
      <div className="flex gap-2 pt-2">
        <Skeleton variant="circular" width={32} height={32} />
        <div className="flex-1 space-y-2">
          <Skeleton variant="text" width="40%" height={14} />
          <Skeleton variant="text" width="60%" height={14} />
        </div>
      </div>
    </div>
  );
}

/**
 * SkeletonList - 列表占位符
 */
export function SkeletonList({ count = 3, className }: { count?: number; className?: string }) {
  return (
    <div className={cn('space-y-2', className)}>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="flex items-center gap-3 p-3 bg-dark-surface border border-dark-border rounded-lg">
          <Skeleton variant="circular" width={40} height={40} />
          <div className="flex-1 space-y-2">
            <Skeleton variant="text" width="30%" height={16} />
            <Skeleton variant="text" width="70%" height={14} />
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * LoadingSpinner - 加载旋钮
 */
interface LoadingSpinnerProps {
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

export function LoadingSpinner({ size = 'md', className }: LoadingSpinnerProps) {
  const sizeClasses = {
    sm: 'w-4 h-4',
    md: 'w-6 h-6',
    lg: 'w-8 h-8',
  };

  return (
    <div role="status" aria-label="Loading" className={cn('flex items-center justify-center', className)}>
      <div
        className={cn(
          'border-2 border-dark-border border-t-accent-blue rounded-full animate-spin',
          sizeClasses[size]
        )}
      />
    </div>
  );
}

/**
 * PageLoading - 全屏加载状态
 */
export function PageLoading({ message = '加载中...' }: { message?: string }) {
  return (
    <div role="status" className="flex flex-col items-center justify-center h-full gap-4 p-8">
      <LoadingSpinner size="lg" />
      <p className="text-dark-muted text-sm">{message}</p>
    </div>
  );
}