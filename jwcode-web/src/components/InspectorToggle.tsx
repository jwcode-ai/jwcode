import { useState, useEffect } from 'react'
import { Inspector } from 'react-dev-inspector'

/**
 * 自定义悬浮按钮 + Inspector 组件
 * 提供可视化的悬浮按钮来切换 react-dev-inspector 的激活状态
 */
export default function InspectorToggle() {
  const [active, setActive] = useState(false)

  // 键盘快捷键 Ctrl+Shift+Alt+C 切换
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.shiftKey && e.altKey && (e.key === 'c' || e.key === 'C')) {
        e.preventDefault()
        setActive(prev => !prev)
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  return (
    <>
      <Inspector
        active={active}
        onActiveChange={setActive}
      />
      {/* 悬浮按钮 */}
      <button
        onClick={() => setActive(prev => !prev)}
        title={active ? '关闭检查模式' : '开启检查模式 (Ctrl+Shift+Alt+C)'}
        style={{
          position: 'fixed',
          bottom: '20px',
          right: '20px',
          zIndex: 99999,
          width: '44px',
          height: '44px',
          borderRadius: '50%',
          border: 'none',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: '20px',
          boxShadow: '0 2px 12px rgba(0,0,0,0.25)',
          transition: 'all 0.2s ease',
          background: active ? '#22c55e' : '#1e293b',
          color: '#fff',
        }}
      >
        <svg
          width="22"
          height="22"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <circle cx="12" cy="12" r="3" />
          <path d="M22 12c0 5.52-4.48 10-10 10S2 17.52 2 12 6.48 2 12 2s10 4.48 10 10z" />
          <path d="M12 2v20" />
          <path d="M2 12h20" />
        </svg>
      </button>
    </>
  )
}
