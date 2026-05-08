import { Component, ErrorInfo, ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('[ErrorBoundary] Caught error:', error, errorInfo);
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="flex items-center justify-center h-full w-full bg-dark-bg">
          <div className="max-w-md p-8 text-center">
            <div className="flex justify-center mb-4">
              <div className="w-16 h-16 rounded-full bg-accent-red/10 flex items-center justify-center">
                <AlertTriangle size={32} className="text-accent-red" />
              </div>
            </div>
            <h2 className="text-xl font-semibold text-dark-text mb-2">
              页面出现错误
            </h2>
            <p className="text-sm text-dark-muted mb-6">
              {this.state.error?.message || '发生了未知错误，请尝试刷新页面'}
            </p>
            <div className="flex items-center justify-center gap-3">
              <button
                onClick={this.handleRetry}
                className="flex items-center gap-2 px-4 py-2 bg-accent-blue text-white rounded-lg hover:opacity-90 transition-opacity"
              >
                <RefreshCw size={16} />
                重试
              </button>
              <button
                onClick={() => window.location.reload()}
                className="px-4 py-2 bg-dark-hover text-dark-text rounded-lg hover:bg-dark-border transition-colors"
              >
                刷新页面
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
