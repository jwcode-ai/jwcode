import { memo } from 'react';
import { useTranslation } from 'react-i18next';
import type { CustomThemeColors } from '../../types';

interface ThemePresetCardProps {
  id: string;
  nameKey: string;
  descriptionKey: string;
  colors: CustomThemeColors;
  isSelected: boolean;
  onClick: () => void;
}

/**
 * Small preset card (~140px wide) showing a 10-color preview strip
 * and the preset name. Selected state has an highlighted border.
 */
export const ThemePresetCard = memo(function ThemePresetCard({
  nameKey,
  descriptionKey,
  colors,
  isSelected,
  onClick,
}: ThemePresetCardProps) {
  const { t } = useTranslation();

  const colorKeys = [
    'bg', 'surface', 'border', 'text', 'muted',
    'accentBlue', 'accentGreen', 'accentRed', 'accentYellow', 'accentPurple',
  ] as const;

  return (
    <button
      onClick={onClick}
      className={`relative flex flex-col items-center rounded-lg p-2 transition-all focus:outline-none focus:ring-2 focus:ring-accent-blue/50 ${
        isSelected
          ? 'ring-2 ring-accent-blue bg-accent-blue/5 border border-accent-blue/40'
          : 'border border-dark-border hover:border-dark-text/30 bg-dark-bg/50 hover:bg-dark-hover/50'
      }`}
      title={t(descriptionKey)}
      aria-pressed={isSelected}
      aria-label={t(nameKey)}
    >
      {/* 10-color preview strip */}
      <div className="flex gap-[2px] rounded-md overflow-hidden w-full h-8 mb-1.5">
        {colorKeys.map((key) => (
          <div
            key={key}
            className="flex-1"
            style={{ backgroundColor: colors[key] }}
            title={key}
          />
        ))}
      </div>

      {/* Name label */}
      <span className={`text-[11px] font-medium truncate w-full text-center ${
        isSelected ? 'text-accent-blue' : 'text-dark-text'
      }`}>
        {t(nameKey)}
      </span>

      {/* Selected check indicator */}
      {isSelected && (
        <span className="absolute top-1 right-1 w-4 h-4 bg-accent-blue rounded-full flex items-center justify-center">
          <svg width="8" height="8" viewBox="0 0 8 8" fill="none">
            <path d="M1.5 4L3.5 6L6.5 2" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </span>
      )}
    </button>
  );
});
