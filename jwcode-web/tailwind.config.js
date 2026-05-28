/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        dark: {
          bg: '#1a1a2e',
          surface: '#16213e',
          border: '#0f3460',
          hover: '#1a1a3e',
          text: '#eaeaea',
          muted: '#a0a0b0',
        },
        accent: {
          blue: '#58a6ff',
          green: '#3fb950',
          red: '#f85149',
          yellow: '#d29922',
          purple: '#a855f7',
          orange: '#d77753',
          cyan: '#39c5cf',
        },
        diff: {
          added: '#2ea043',
          removed: '#f85149',
          'added-bg': '#122d16',
          'removed-bg': '#2d1215',
        },
        spinner: {
          frame1: '#58a6ff', frame2: '#3fb950', frame3: '#d29922', frame4: '#f85149',
        },
      },
      fontFamily: {
        mono: ['SF Mono', 'Monaco', 'Menlo', 'Consolas', 'monospace'],
      },
      animation: {
        'fade-in': 'fadeIn 0.3s ease-out',
        'fade-in-up': 'fadeInUp 0.4s ease-out',
        'slide-in': 'slideIn 0.3s ease-out',
        'blink': 'blink 1.2s ease-in-out infinite',
        'spin-frame': 'spinFrame 0.8s steps(4) infinite',
      },
      keyframes: {
        fadeIn: {
          from: { opacity: '0', transform: 'translateY(10px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        fadeInUp: {
          from: { opacity: '0', transform: 'translateY(4px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        slideIn: {
          from: { opacity: '0', transform: 'translateX(-10px)' },
          to: { opacity: '1', transform: 'translateX(0)' },
        },
        blink: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.2' },
        },
        spinFrame: {
          '0%': { transform: 'rotate(0deg)' },
          '25%': { transform: 'rotate(90deg)' },
          '50%': { transform: 'rotate(180deg)' },
          '75%': { transform: 'rotate(270deg)' },
          '100%': { transform: 'rotate(360deg)' },
        },
      },
    },
  },
  plugins: [],
}
