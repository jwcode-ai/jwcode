import { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, FileUp, Plus, RefreshCw, Save, Target, ToggleLeft, ToggleRight, Upload } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Modal } from '../common';
import { api, type Skill } from '../../services/api';

type FormState = {
  id: string;
  name: string;
  description: string;
  category: string;
  trigger: string;
  systemPrompt: string;
  injection: string;
  tags: string;
  requiredTools: string;
};

const EMPTY_FORM: FormState = {
  id: '',
  name: '',
  description: '',
  category: '',
  trigger: '',
  systemPrompt: '',
  injection: 'lazy',
  tags: '',
  requiredTools: '',
};

export function SkillsView() {
  const { t } = useTranslation();
  const [skills, setSkills] = useState<Skill[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showEditor, setShowEditor] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [showImport, setShowImport] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement | null>(null);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    const res = await api.skills.list();
    const data = res.data ?? [];
    if (res.success) {
      setSkills(data);
      setSelectedId(prev => (prev && data.some(s => s.id === prev) ? prev : (data[0]?.id || null)));
    } else {
      setError(res.error || t('skills.loadFailedFallback'));
    }
    setLoading(false);
  };

  useEffect(() => {
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const selectedSkill = useMemo(
    () => skills.find(skill => skill.id === selectedId) || skills[0] || null,
    [skills, selectedId]
  );

  const filteredSkills = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return skills;
    return skills.filter(skill =>
      [skill.id, skill.name, skill.description, skill.category, ...(skill.tags || [])]
        .join(' ')
        .toLowerCase()
        .includes(q)
    );
  }, [skills, query]);

  const openCreate = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setFormError(null);
    setShowEditor(true);
  };

  const openEdit = (skill: Skill) => {
    setEditingId(skill.id);
    setForm({
      id: skill.id,
      name: skill.name,
      description: skill.description,
      category: skill.category,
      trigger: skill.trigger || '',
      systemPrompt: skill.systemPrompt || '',
      injection: skill.injection || 'lazy',
      tags: (skill.tags || []).join(', '),
      requiredTools: (skill.requiredTools || []).join(', '),
    });
    setFormError(null);
    setShowEditor(true);
  };

  const handleSave = async () => {
    const id = form.id.trim();
    if (!id) {
      setFormError(t('skills.fieldId') + ' is required');
      return;
    }
    setSaving(true);
    setFormError(null);
    const payload = {
      id,
      name: form.name.trim(),
      description: form.description.trim(),
      category: form.category.trim(),
      trigger: form.trigger.trim(),
      systemPrompt: form.systemPrompt,
      injection: form.injection.trim(),
      tags: splitList(form.tags),
      requiredTools: splitList(form.requiredTools),
    };
    const res = editingId ? await api.skills.update(editingId, payload) : await api.skills.create(payload);
    if (res.success) {
      setShowEditor(false);
      setEditingId(null);
      setForm(EMPTY_FORM);
      await loadData();
      setSelectedId(id);
    } else {
      setFormError(res.error || t('skills.save'));
    }
    setSaving(false);
  };

  const handleToggle = async (skill: Skill) => {
    const res = await api.skills.toggle(skill.id, !skill.enabled);
    if (res.success) {
      setSkills(prev => prev.map(item => item.id === skill.id ? { ...item, enabled: !item.enabled } : item));
    }
  };

  const handleImportFile = async (file: File) => {
    setImporting(true);
    setImportError(null);
    try {
      const content = await file.text();
      const res = await api.skills.import({ fileName: file.name, content });
      if (res.success) {
        setShowImport(false);
        await loadData();
      } else {
        setImportError(res.error || t('skills.import'));
      }
    } catch (err) {
      setImportError(err instanceof Error ? err.message : t('skills.import'));
    } finally {
      setImporting(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center">
          <RefreshCw size={32} className="animate-spin mx-auto mb-2 text-accent-blue" />
          <p className="text-dark-muted">Loading...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center">
          <AlertTriangle size={32} className="mx-auto mb-2 text-accent-red" />
          <p className="text-accent-red mb-4">{error}</p>
          <button onClick={loadData} className="px-4 py-2 bg-accent-blue text-white rounded-lg">{t('skills.retry')}</button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col overflow-hidden p-4 gap-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3 min-w-0">
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <Target size={20} />
            {t('skills.title')}
            <span className="text-sm font-normal text-dark-muted">({skills.length})</span>
          </h2>
          <input
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="Search skills"
            className="w-56 px-3 py-2 bg-dark-surface border border-dark-border rounded-lg text-sm outline-none focus:border-accent-blue"
          />
        </div>
        <div className="flex items-center gap-2">
          <button onClick={openCreate} className="flex items-center gap-2 px-3 py-2 bg-accent-blue text-white rounded-lg">
            <Plus size={14} />
            {t('skills.new')}
          </button>
          <button onClick={() => setShowImport(true)} className="flex items-center gap-2 px-3 py-2 bg-dark-surface border border-dark-border rounded-lg">
            <Upload size={14} />
            {t('skills.import')}
          </button>
          <button onClick={loadData} className="flex items-center gap-2 px-3 py-2 bg-dark-surface border border-dark-border rounded-lg">
            <RefreshCw size={14} />
            {t('skills.refresh')}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-[320px_minmax(0,1fr)] gap-4 flex-1 min-h-0">
        <div className="border border-dark-border rounded-lg overflow-hidden bg-dark-surface flex flex-col min-h-0">
          <div className="px-3 py-2 border-b border-dark-border text-sm text-dark-muted flex items-center justify-between">
            <span>{t('skills.list')}</span>
            <span>{filteredSkills.length}</span>
          </div>
          <div className="overflow-y-auto">
            {filteredSkills.map(skill => (
              <button
                key={skill.id}
                onClick={() => setSelectedId(skill.id)}
                className={`w-full text-left px-3 py-3 border-b border-dark-border/60 hover:bg-dark-hover ${selectedId === skill.id ? 'bg-dark-hover' : ''}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-lg">{skill.icon || '⭐'}</span>
                      <div className="min-w-0">
                        <div className="font-medium truncate">{skill.name}</div>
                        <div className="text-xs text-dark-muted truncate">{skill.id}</div>
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={e => {
                      e.stopPropagation();
                      handleToggle(skill);
                    }}
                    className={`flex items-center gap-1 px-2 py-1 rounded text-xs font-medium transition-colors ${
                      skill.enabled
                        ? 'bg-accent-green/20 text-accent-green hover:bg-accent-green/30'
                        : 'bg-accent-red/20 text-accent-red hover:bg-accent-red/30'
                    }`}
                    title={skill.enabled ? t('skills.disable') : t('skills.enable')}
                  >
                    {skill.enabled ? <ToggleRight size={16} /> : <ToggleLeft size={16} />}
                    {skill.enabled ? t('skills.enabled') : t('skills.disabled')}
                  </button>
                </div>
              </button>
            ))}
            {filteredSkills.length === 0 && <div className="p-4 text-sm text-dark-muted">{t('skills.noSkills')}</div>}
          </div>
        </div>

        <div className="border border-dark-border rounded-lg bg-dark-surface min-h-0 overflow-hidden flex flex-col">
          {selectedSkill ? (
            <>
              <div className="px-4 py-3 border-b border-dark-border flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-2xl">{selectedSkill.icon || '⭐'}</span>
                    <div className="min-w-0">
                      <div className="font-semibold truncate">{selectedSkill.name}</div>
                      <div className="text-xs text-dark-muted">{selectedSkill.id} · {selectedSkill.category}</div>
                    </div>
                  </div>
                  <p className="mt-2 text-sm text-dark-muted whitespace-pre-wrap">{selectedSkill.description}</p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleToggle(selectedSkill)}
                    className={`flex items-center gap-1 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                      selectedSkill.enabled
                        ? 'bg-accent-green/20 text-accent-green hover:bg-accent-green/30'
                        : 'bg-accent-red/20 text-accent-red hover:bg-accent-red/30'
                    }`}
                  >
                    {selectedSkill.enabled ? <ToggleRight size={14} /> : <ToggleLeft size={14} />}
                    {selectedSkill.enabled ? t('skills.enabled') : t('skills.disabled')}
                  </button>
                  <button onClick={() => openEdit(selectedSkill)} className="px-3 py-2 bg-accent-blue text-white rounded-lg text-sm flex items-center gap-2">
                    <Save size={14} />
                    {t('skills.edit')}
                  </button>
                </div>
              </div>
              <div className="p-4 overflow-y-auto text-sm space-y-3">
                <div>
                  <div className="text-dark-muted mb-1">{t('skills.tags')}</div>
                  <div className="flex flex-wrap gap-2">
                    {(selectedSkill.tags || []).length > 0 ? selectedSkill.tags!.map(tag => (
                      <span key={tag} className="px-2 py-1 rounded bg-dark-bg border border-dark-border">{tag}</span>
                    )) : <span className="text-dark-muted">{t('skills.none')}</span>}
                  </div>
                </div>
                <div>
                  <div className="text-dark-muted mb-1">{t('skills.source')}</div>
                  <div className="break-all">{selectedSkill.source || t('skills.localSkill')}</div>
                </div>
                <div>
                  <div className="text-dark-muted mb-1">{t('skills.systemPrompt')}</div>
                  <pre className="whitespace-pre-wrap text-xs p-3 rounded border border-dark-border bg-dark-bg overflow-auto">{selectedSkill.systemPrompt || t('skills.none')}</pre>
                </div>
              </div>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center text-dark-muted">{t('skills.selectSkill')}</div>
          )}
        </div>
      </div>

      <Modal isOpen={showEditor} onClose={() => setShowEditor(false)} title={editingId ? t('skills.edit') : t('skills.new')} size="xl">
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('skills.fieldId')} value={form.id} onChange={v => setForm(prev => ({ ...prev, id: v }))} />
          <Field label={t('skills.fieldName')} value={form.name} onChange={v => setForm(prev => ({ ...prev, name: v }))} />
          <Field label={t('skills.fieldCategory')} value={form.category} onChange={v => setForm(prev => ({ ...prev, category: v }))} />
          <Field label={t('skills.fieldTrigger')} value={form.trigger} onChange={v => setForm(prev => ({ ...prev, trigger: v }))} />
          <Field label={t('skills.tags')} value={form.tags} onChange={v => setForm(prev => ({ ...prev, tags: v }))} />
          <Field label={t('skills.fieldTools')} value={form.requiredTools} onChange={v => setForm(prev => ({ ...prev, requiredTools: v }))} />
          <div className="col-span-2">
            <label className="block text-sm mb-1 text-dark-muted">{t('skills.description')}</label>
            <textarea
              value={form.description}
              onChange={e => setForm(prev => ({ ...prev, description: e.target.value }))}
              className="w-full h-20 px-3 py-2 rounded-lg bg-dark-bg border border-dark-border outline-none resize-none"
            />
          </div>
          <div className="col-span-2">
            <label className="block text-sm mb-1 text-dark-muted">{t('skills.systemPrompt')}</label>
            <textarea
              value={form.systemPrompt}
              onChange={e => setForm(prev => ({ ...prev, systemPrompt: e.target.value }))}
              className="w-full h-40 px-3 py-2 rounded-lg bg-dark-bg border border-dark-border outline-none resize-none font-mono text-sm"
            />
          </div>
          {formError && <div className="col-span-2 text-sm text-accent-red">{formError}</div>}
          <div className="col-span-2 flex justify-end gap-2">
            <button onClick={() => setShowEditor(false)} className="px-4 py-2 border border-dark-border rounded-lg">{t('skills.cancel')}</button>
            <button disabled={saving} onClick={handleSave} className="px-4 py-2 bg-accent-blue text-white rounded-lg flex items-center gap-2">
              <Save size={14} />
              {saving ? t('skills.saving') : t('skills.save')}
            </button>
          </div>
        </div>
      </Modal>

      <Modal isOpen={showImport} onClose={() => setShowImport(false)} title={t('skills.importTitle')} size="md">
        <div className="space-y-3">
          <input
            ref={fileRef}
            type="file"
            accept=".md,.skill.md,.txt"
            className="hidden"
            onChange={e => {
              const file = e.target.files?.[0];
              if (file) void handleImportFile(file);
            }}
          />
          <button
            onClick={() => fileRef.current?.click()}
            disabled={importing}
            className="w-full py-6 border border-dashed border-dark-border rounded-lg flex items-center justify-center gap-2"
          >
            <FileUp size={16} />
            {t('skills.chooseFile')}
          </button>
          {importError && <div className="text-sm text-accent-red">{importError}</div>}
          <div className="text-xs text-dark-muted">{t('skills.importHint')}</div>
        </div>
      </Modal>
    </div>
  );
}

function splitList(value: string) {
  return value.split(',').map(item => item.trim()).filter(Boolean);
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <div>
      <label className="block text-sm mb-1 text-dark-muted">{label}</label>
      <input
        value={value}
        onChange={e => onChange(e.target.value)}
        className="w-full px-3 py-2 rounded-lg bg-dark-bg border border-dark-border outline-none focus:border-accent-blue"
      />
    </div>
  );
}
