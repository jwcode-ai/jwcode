import { create } from 'zustand';
import { Message } from '../types';

interface SearchResult {
  sessionId: string;
  sessionTitle: string;
  messageId: string;
  message: Message;
  matchPreview: string;
  timestamp: number;
}

interface SearchState {
  query: string;
  isSearching: boolean;
  results: SearchResult[];
  showSearchPanel: boolean;
  
  setQuery: (query: string) => void;
  setShowSearchPanel: (show: boolean) => void;
  searchMessages: (query: string, sessions: { id: string; title: string; messages: Message[] }[]) => void;
  clearResults: () => void;
}

export const useSearchStore = create<SearchState>((set) => ({
  query: '',
  isSearching: false,
  results: [],
  showSearchPanel: false,

  setQuery: (query) => set({ query }),
  
  setShowSearchPanel: (show) => set({ showSearchPanel: show }),

  searchMessages: (query, sessions) => {
    if (!query.trim()) {
      set({ results: [], isSearching: false });
      return;
    }
    
    set({ isSearching: true, query });
    
    const lowerQuery = query.toLowerCase();
    const results: SearchResult[] = [];
    
    for (const session of sessions) {
      if (!session.messages) continue;
      for (const msg of session.messages) {
        if (msg.content.toLowerCase().includes(lowerQuery) || 
            (msg.thinking && msg.thinking.toLowerCase().includes(lowerQuery))) {
          // Create a preview snippet
          const idx = msg.content.toLowerCase().indexOf(lowerQuery);
          const start = Math.max(0, idx - 40);
          const end = Math.min(msg.content.length, idx + lowerQuery.length + 60);
          const preview = (start > 0 ? '...' : '') + 
            msg.content.slice(start, end) + 
            (end < msg.content.length ? '...' : '');
          
          results.push({
            sessionId: session.id,
            sessionTitle: session.title,
            messageId: msg.id,
            message: msg,
            matchPreview: preview,
            timestamp: msg.timestamp,
          });
        }
      }
    }
    
    // Sort by timestamp descending
    results.sort((a, b) => b.timestamp - a.timestamp);
    
    set({ results, isSearching: false });
  },

  clearResults: () => set({ results: [], query: '', isSearching: false }),
}));
