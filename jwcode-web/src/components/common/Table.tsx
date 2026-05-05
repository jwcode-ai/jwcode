import { ReactNode } from 'react';
import { cn } from '../../utils/cn';

// 表格列定义
interface Column<T> {
  key: keyof T extends string ? keyof T : string;
  title: string;
  width?: string;
  render?: (value: unknown, row: T, index: number) => ReactNode;
  sortable?: boolean;
}

// 表格属性
interface TableProps<T> {
  columns: Column<T>[];
  data: T[];
  loading?: boolean;
  emptyText?: string;
  className?: string;
  onRowClick?: (row: T) => void;
}

/**
 * Table - 表格组件
 */
export function Table<T extends { id?: string }>({
  columns,
  data,
  loading,
  emptyText = '暂无数据',
  className,
  onRowClick,
}: TableProps<T>) {
  return (
    <div className={cn('overflow-x-auto', className)}>
      <table className="w-full">
        {/* 表头 */}
        <thead>
          <tr className="border-b border-dark-border">
            {columns.map((col) => (
              <th
                key={String(col.key)}
                className="px-4 py-3 text-left text-xs font-medium text-dark-muted uppercase tracking-wider"
                style={{ width: col.width }}
              >
                {col.title}
              </th>
            ))}
          </tr>
        </thead>

        {/* 表体 */}
        <tbody className="divide-y divide-dark-border">
          {loading ? (
            // 加载状态
            columns.map((col, i) => (
              <tr key={i}>
                <td colSpan={columns.length} className="px-4 py-8">
                  <div className="flex items-center justify-center gap-2 text-dark-muted">
                    <div className="w-4 h-4 border-2 border-dark-border border-t-accent-blue rounded-full animate-spin" />
                    <span>加载中...</span>
                  </div>
                </td>
              </tr>
            ))
          ) : data.length === 0 ? (
            // 空状态
            <tr>
              <td colSpan={columns.length} className="px-4 py-8 text-center text-dark-muted">
                {emptyText}
              </td>
            </tr>
          ) : (
            // 数据行
            data.map((row, index) => (
              <tr
                key={row.id || index}
                className={cn(
                  'hover:bg-dark-hover transition-colors',
                  onRowClick && 'cursor-pointer'
                )}
                onClick={() => onRowClick?.(row)}
              >
                {columns.map((col) => (
                  <td key={String(col.key)} className="px-4 py-3 text-sm text-dark-text">
                    {col.render
                      ? col.render(row[col.key as keyof T], row, index)
                      : String(row[col.key as keyof T] ?? '')}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

/**
 * DataTable - 带分页的表格
 */
interface DataTableProps<T> extends TableProps<T> {
  page?: number;
  pageSize?: number;
  total?: number;
  onPageChange?: (page: number) => void;
}

export function DataTable<T extends { id?: string }>({
  page = 1,
  pageSize = 10,
  total,
  onPageChange,
  ...tableProps
}: DataTableProps<T>) {
  const totalPages = total ? Math.ceil(total / pageSize) : 0;
  const start = (page - 1) * pageSize + 1;
  const end = Math.min(page * pageSize, total || 0);

  return (
    <div>
      <Table {...tableProps} />
      {total !== undefined && totalPages > 1 && (
        <div className="flex items-center justify-between px-4 py-3 border-t border-dark-border">
          <span className="text-sm text-dark-muted">
            显示 {start}-{end} 条，共 {total} 条
          </span>
          <div className="flex gap-2">
            <button
              onClick={() => onPageChange?.(page - 1)}
              disabled={page === 1}
              className="px-3 py-1 text-sm rounded border border-dark-border disabled:opacity-50 disabled:cursor-not-allowed hover:bg-dark-hover"
            >
              上一页
            </button>
            <button
              onClick={() => onPageChange?.(page + 1)}
              disabled={page >= totalPages}
              className="px-3 py-1 text-sm rounded border border-dark-border disabled:opacity-50 disabled:cursor-not-allowed hover:bg-dark-hover"
            >
              下一页
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// 使用示例:
// const columns = [
//   { key: 'name', title: '名称' },
//   { key: 'status', title: '状态', render: (v) => <Badge>{v}</Badge> },
//   { key: 'actions', title: '操作', render: (_, row) => <button>编辑</button> },
// ];
// <Table columns={columns} data={users} />;