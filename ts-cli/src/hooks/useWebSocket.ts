/**
 * useWebSocket — convenience hook for accessing WS client from components.
 * Most of the actual WS wiring is in App.tsx; this provides a simpler
 * interface for components that need to call client methods directly.
 */
import { useRef, useEffect } from 'react';
import { JwCodeClient } from '../client.js';

// Module-level client reference for components that need it outside React context
let _client: JwCodeClient | null = null;

export function setClient(client: JwCodeClient): void {
  _client = client;
}

export function getClient(): JwCodeClient | null {
  return _client;
}
