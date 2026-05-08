import { memo } from 'react';

interface FeatureToggleProps {
  title: string;
  subtitle: string;
  enabled: boolean;
  onChange: (enabled: boolean) => void;
}

export const FeatureToggle = memo(function FeatureToggle({
  title, subtitle, enabled, onChange,
}: FeatureToggleProps) {
  return (
    <div className="flex items-center justify-between p-3 bg-dark-bg rounded-lg">
      <div>
        <div className="font-medium text-sm">{title}</div>
        <div className="text-xs text-dark-muted">{subtitle}</div>
      </div>
      <button
        onClick={() => onChange(!enabled)}
        className={`relative w-12 h-6 rounded-full transition-colors ${
          enabled ? 'bg-accent-green' : 'bg-dark-border'
        }`}
      >
        <span
          className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${
            enabled ? 'left-7' : 'left-1'
          }`}
        />
      </button>
    </div>
  );
});
