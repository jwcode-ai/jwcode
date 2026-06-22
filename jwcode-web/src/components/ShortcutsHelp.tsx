import { useTranslation } from 'react-i18next';
import { Modal } from './common/Modal';

interface ShortcutsHelpProps {
  open: boolean;
  onClose: () => void;
}

/** 一行快捷键说明 */
function Row({ keys, label }: { keys: string[]; label: string }) {
  return (
    <div className="flex items-center justify-between py-2 border-b border-dark-border/50 last:border-0">
      <span className="text-sm text-dark-text">{label}</span>
      <span className="flex items-center gap-1">
        {keys.map((k) => (
          <kbd
            key={k}
            className="px-2 py-0.5 text-xs font-mono bg-dark-bg rounded border border-dark-border text-dark-muted"
          >
            {k}
          </kbd>
        ))}
      </span>
    </div>
  );
}

/**
 * ShortcutsHelp — Ctrl/Cmd+/ 触发的快捷键帮助浮层。
 */
export function ShortcutsHelp({ open, onClose }: ShortcutsHelpProps) {
  const { t } = useTranslation();
  const mod = navigator.platform.toLowerCase().includes('mac') ? '⌘' : 'Ctrl';

  return (
    <Modal isOpen={open} onClose={onClose} title={t('shortcuts.title')} size="md">
      <div className="space-y-0.5">
        <Row keys={[mod, 'K']} label={t('shortcuts.commandPalette')} />
        <Row keys={[mod, 'L']} label={t('shortcuts.clearLogs')} />
        <Row keys={[mod, '/']} label={t('shortcuts.help')} />
        <Row keys={[mod, 'Enter']} label={t('shortcuts.submit')} />
        <Row keys={['Shift', 'Enter']} label={t('shortcuts.newline')} />
        <Row keys={['/']} label={t('shortcuts.slashCommand')} />
        <Row keys={['@']} label={t('shortcuts.fileRef')} />
        <Row keys={['Esc']} label={t('shortcuts.closeDialog')} />
        <Row keys={['Esc', 'Esc']} label={t('shortcuts.stopGen')} />
      </div>
    </Modal>
  );
}
