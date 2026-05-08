import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './styles/globals.css'
import InspectorToggle from './components/InspectorToggle'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <InspectorToggle />
    <App />
  </React.StrictMode>,
)
