import { ReactNode } from 'react';
import { Panel, PanelGroup, PanelResizeHandle } from 'react-resizable-panels';
import { cn } from '../../utils/cn';

interface ResponsiveLayoutProps {
  sidebar: ReactNode;
  main: ReactNode;
  sidebarWidth?: number;
  minSidebarWidth?: number;
  maxSidebarWidth?: number;
  showSidebar?: boolean;
  className?: string;
}

export function ResponsiveLayout({
  sidebar,
  main,
  sidebarWidth = 280,
  minSidebarWidth = 200,
  maxSidebarWidth = 400,
  showSidebar = true,
  className = '',
}: ResponsiveLayoutProps) {
  // On mobile, hide sidebar by default
  const isMobile = typeof window !== 'undefined' && window.innerWidth < 768;

  if (!showSidebar) {
    return (
      <div className={cn('h-full flex flex-col', className)}>
        {main}
      </div>
    );
  }

  if (isMobile) {
    return (
      <div className={cn('h-full flex flex-col', className)}>
        {main}
      </div>
    );
  }

  return (
    <div className={cn('h-full flex', className)}>
      <PanelGroup direction="horizontal" autoSaveId="jwcode-layout">
        <Panel
          defaultSize={sidebarWidth}
          minSize={minSidebarWidth}
          maxSize={maxSidebarWidth}
          className="shrink-0"
        >
          {sidebar}
        </Panel>
        
        <PanelResizeHandle className="w-1 bg-dark-border hover:bg-accent-blue transition-colors cursor-col-resize" />
        
        <Panel className="flex-1 min-w-0">
          {main}
        </Panel>
      </PanelGroup>
    </div>
  );
}

// Vertical layout for stacked panels (like chat + terminal)
interface VerticalPanelProps {
  top: ReactNode;
  bottom: ReactNode;
  topHeight?: number;
  minTopHeight?: number;
  maxTopHeight?: number;
  showBottom?: boolean;
  bottomHeight?: string;
  className?: string;
}

export function VerticalPanel({
  top,
  bottom,
  topHeight = 60,
  minTopHeight = 30,
  maxTopHeight = 80,
  showBottom = true,
  bottomHeight = 'h-80',
  className = '',
}: VerticalPanelProps) {
  if (!showBottom) {
    return (
      <div className={cn('h-full flex flex-col', className)}>
        {top}
      </div>
    );
  }

  return (
    <PanelGroup direction="vertical" autoSaveId="jwcode-vertical">
      <Panel
        defaultSize={topHeight}
        minSize={minTopHeight}
        maxSize={maxTopHeight}
        className="min-h-0"
      >
        {top}
      </Panel>
      
      <PanelResizeHandle className="h-1 bg-dark-border hover:bg-accent-blue transition-colors cursor-row-resize" />
      
      <Panel className={cn('min-h-[200px]', bottomHeight)}>
        {bottom}
      </Panel>
    </PanelGroup>
  );
}

// Three-panel layout
interface ThreePanelLayoutProps {
  sidebar: ReactNode;
  main: ReactNode;
  secondary: ReactNode;
  sidebarWidth?: number;
  secondaryWidth?: number;
  minSidebarWidth?: number;
  minSecondaryWidth?: number;
  showSecondary?: boolean;
  className?: string;
}

export function ThreePanelLayout({
  sidebar,
  main,
  secondary,
  sidebarWidth = 280,
  secondaryWidth = 320,
  minSidebarWidth = 200,
  minSecondaryWidth = 250,
  showSecondary = true,
  className = '',
}: ThreePanelLayoutProps) {
  if (!showSecondary) {
    return (
      <ResponsiveLayout
        sidebar={sidebar}
        main={main}
        sidebarWidth={sidebarWidth}
        minSidebarWidth={minSidebarWidth}
        className={className}
      />
    );
  }

  return (
    <div className={cn('h-full flex', className)}>
      <PanelGroup direction="horizontal" autoSaveId="jwcode-three-panel">
        <Panel
          defaultSize={sidebarWidth}
          minSize={minSidebarWidth}
          maxSize={400}
          className="shrink-0"
        >
          {sidebar}
        </Panel>
        
        <PanelResizeHandle className="w-1 bg-dark-border hover:bg-accent-blue transition-colors cursor-col-resize" />
        
        <Panel className="flex-1 min-w-0">
          {main}
        </Panel>
        
        <PanelResizeHandle className="w-1 bg-dark-border hover:bg-accent-blue transition-colors cursor-col-resize" />
        
        <Panel
          defaultSize={secondaryWidth}
          minSize={minSecondaryWidth}
          maxSize={500}
          className="shrink-0"
        >
          {secondary}
        </Panel>
      </PanelGroup>
    </div>
  );
}

export default ResponsiveLayout;
