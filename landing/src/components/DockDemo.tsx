import { useEffect, useRef, useState } from 'react';
import type { CSSProperties, FormEvent, PointerEvent as ReactPointerEvent } from 'react';
import { ArrowRight, ChevronLeft, ChevronRight, ChevronUp, Plus, ShieldCheck, X } from 'lucide-react';
import { BUILT_IN_ENGINES, type SearchEngine } from '../lib/site';
import { MediaPlaceholder } from './MediaPlaceholder';

type Tab = {
  id: string;
  name: string;
  mark: string;
  color: string;
  title: string;
  meta: string;
  type: 'photo' | 'video';
  description: string;
};

const TABS: Tab[] = [
  {
    id: 'field-notes',
    name: 'Field notes',
    mark: 'F',
    color: '#56DBCF',
    title: 'A quieter internet',
    meta: 'PROSTO.APP',
    type: 'photo',
    description: 'A full-bleed photo of a misty mountain ridge at dawn. Calm, spacious, no interface chrome.',
  },
  {
    id: 'recipe',
    name: 'Recipe',
    mark: 'R',
    color: '#A5F2E9',
    title: 'Miso butter noodles',
    meta: 'KITCHEN.LOCAL',
    type: 'photo',
    description: 'A top-down photo of noodles in a dark bowl with greens and sesame. The page is clean and readable.',
  },
  {
    id: 'read',
    name: 'Read later',
    mark: 'L',
    color: '#7FD8CB',
    title: 'The shape of calm software',
    meta: 'LONGFORM.READ',
    type: 'video',
    description: 'A short looping video of a slow pan across a quiet workspace. Captions sit above the dock.',
  },
];

const STORAGE_KEY = 'prosto.custom-engines';

function getSavedEngines() {
  if (typeof window === 'undefined') return BUILT_IN_ENGINES;

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return BUILT_IN_ENGINES;

    const parsed = JSON.parse(raw) as SearchEngine[];
    if (!Array.isArray(parsed)) return BUILT_IN_ENGINES;

    const valid = parsed.filter(
      (engine) => engine && typeof engine.name === 'string' && typeof engine.url === 'string',
    );

    return [...BUILT_IN_ENGINES, ...valid.map((engine) => ({ ...engine, custom: true }))];
  } catch {
    return BUILT_IN_ENGINES;
  }
}

export function DockDemo() {
  const [activeTabId, setActiveTabId] = useState(TABS[0].id);
  const [searchOpen, setSearchOpen] = useState(false);
  const [engines, setEngines] = useState<SearchEngine[]>(getSavedEngines);
  const [activeEngineId, setActiveEngineId] = useState('google');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<{ query: string; engine: string } | null>(null);
  const [showCustomForm, setShowCustomForm] = useState(false);
  const [customName, setCustomName] = useState('');
  const [customUrl, setCustomUrl] = useState('');

  const dragStartY = useRef<number | null>(null);
  const dragCleanup = useRef<(() => void) | null>(null);
  const programmaticScroll = useRef<number | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const railRef = useRef<HTMLDivElement>(null);

  const activeTab = TABS.find((tab) => tab.id === activeTabId) ?? TABS[0];
  const activeEngine = engines.find((engine) => engine.id === activeEngineId) ?? engines[0];

  useEffect(() => {
    try {
      const custom = engines.filter((engine) => engine.custom);
      if (custom.length > 0) {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(custom));
      } else {
        window.localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // Storage can be blocked; the demo still works in memory.
    }
  }, [engines]);

  useEffect(() => {
    if (!searchOpen) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setSearchOpen(false);
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [searchOpen]);

  useEffect(() => {
    return () => {
      dragCleanup.current?.();
      if (programmaticScroll.current !== null) window.clearTimeout(programmaticScroll.current);
    };
  }, []);

  const openSearch = () => {
    setResults(null);
    setSearchOpen(true);
    window.setTimeout(() => inputRef.current?.focus({ preventScroll: true }), 420);
  };

  const closeSearch = () => {
    setSearchOpen(false);
    setShowCustomForm(false);
  };

  const holdProgrammaticSelection = () => {
    if (programmaticScroll.current !== null) window.clearTimeout(programmaticScroll.current);
    programmaticScroll.current = window.setTimeout(() => {
      programmaticScroll.current = null;
    }, 650);
  };

  const scrollToEngine = (id: string) => {
    const rail = railRef.current;
    const node = rail?.querySelector<HTMLElement>(`[data-engine-id="${id}"]`);
    if (!rail || !node) return;

    holdProgrammaticSelection();
    rail.scrollTo({
      left: node.offsetLeft - (rail.clientWidth - node.offsetWidth) / 2,
      behavior: 'smooth',
    });
  };

  const selectEngine = (id: string) => {
    setActiveEngineId(id);
    scrollToEngine(id);
  };

  const cycleEngine = (direction: -1 | 1) => {
    const currentIndex = Math.max(
      0,
      engines.findIndex((engine) => engine.id === activeEngineId),
    );
    const nextIndex = (currentIndex + direction + engines.length) % engines.length;
    selectEngine(engines[nextIndex].id);
  };

  const handleRailScroll = () => {
    if (programmaticScroll.current !== null) return;

    const rail = railRef.current;
    if (!rail) return;

    const center = rail.scrollLeft + rail.clientWidth / 2;
    let nearestId = activeEngineId;
    let nearestDistance = Number.POSITIVE_INFINITY;

    rail.querySelectorAll<HTMLElement>('[data-engine-id]').forEach((node) => {
      const nodeCenter = node.offsetLeft + node.offsetWidth / 2;
      const distance = Math.abs(nodeCenter - center);
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearestId = node.dataset.engineId ?? nearestId;
      }
    });

    if (nearestId !== activeEngineId) setActiveEngineId(nearestId);
  };

  const runSearch = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) {
      inputRef.current?.focus();
      return;
    }

    setResults({ query: trimmed, engine: activeEngine.name });
    inputRef.current?.blur();
  };

  const addCustomEngine = (event: FormEvent) => {
    event.preventDefault();
    const name = customName.trim();
    const rawUrl = customUrl.trim();
    if (!name || !rawUrl) return;

    const url = rawUrl.includes('%s')
      ? rawUrl
      : `${rawUrl}${rawUrl.includes('?') ? '&' : '?'}q=%s`;

    const engine: SearchEngine = {
      id: `custom-${Date.now()}`,
      name,
      mark: name.slice(0, 1).toUpperCase(),
      color: '#56DBCF',
      url,
      custom: true,
    };

    setEngines((current) => [...current, engine]);
    setActiveEngineId(engine.id);
    setCustomName('');
    setCustomUrl('');
    setShowCustomForm(false);
    holdProgrammaticSelection();
    window.setTimeout(() => scrollToEngine(engine.id), 80);
  };

  const onDockPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    dragCleanup.current?.();
    dragStartY.current = event.clientY;

    const finish = (upEvent: PointerEvent) => {
      const start = dragStartY.current;
      dragStartY.current = null;
      dragCleanup.current = null;
      window.removeEventListener('pointercancel', cancel);
      if (start !== null && start - upEvent.clientY > 26) {
        openSearch();
      }
    };

    const cancel = () => {
      dragStartY.current = null;
      dragCleanup.current = null;
      window.removeEventListener('pointerup', finish);
    };

    window.addEventListener('pointerup', finish, { once: true });
    window.addEventListener('pointercancel', cancel, { once: true });

    dragCleanup.current = () => {
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', cancel);
    };
  };

  const customEngineForm = (
    <form className="custom-engine-form" onSubmit={addCustomEngine}>
      <input
        value={customName}
        onChange={(event) => setCustomName(event.target.value)}
        placeholder="Engine name"
        aria-label="Custom engine name"
      />
      <input
        value={customUrl}
        onChange={(event) => setCustomUrl(event.target.value)}
        placeholder="https://example.com/?q=%s"
        aria-label="Custom engine search URL"
        inputMode="url"
      />
      <button type="submit">Add engine</button>
    </form>
  );

  return (
    <div className="device-shell" data-scale-in>
      <div className="device-screen">
        <div className="device-status">
          <span>9:41</span>
          <span className="device-notch" aria-hidden="true" />
          <span className="inline-flex items-center gap-1">
            <ShieldCheck size={10} />
            LTE
          </span>
        </div>

        <div className="device-page">
          <div className="device-page-head">
            <div>
              <p className="device-page-meta">{activeTab.meta}</p>
              <p className="device-page-title">{activeTab.title}</p>
            </div>
            <span className="rounded-full border border-line px-1.5 py-0.5 font-mono text-[0.42rem] tracking-[0.1em] text-muted uppercase">
              Live
            </span>
          </div>

          <MediaPlaceholder
            type={activeTab.type}
            title={activeTab.title}
            description={activeTab.description}
            className="flex-1"
          />

          <div className="grid grid-cols-[1.4fr_1fr] gap-1.5">
            <div className="rounded-xl border border-line bg-white/[0.025] px-2 py-1.5">
              <div className="h-1 w-3/4 rounded-full bg-white/15" />
              <div className="mt-1.5 h-1 w-full rounded-full bg-white/8" />
              <div className="mt-1 h-1 w-2/3 rounded-full bg-white/8" />
            </div>
            <div className="rounded-xl border border-line bg-brand/[0.06] px-2 py-1.5">
              <p className="font-mono text-[0.4rem] tracking-[0.1em] text-brand uppercase">Shield on</p>
              <p className="mt-1 text-[0.45rem] leading-3 text-muted">3 requests blocked on this page.</p>
            </div>
          </div>

          <div
            className="device-dock-wrap"
            onPointerDown={onDockPointerDown}
          >
            <button type="button" className="device-swipe-hint w-full" onClick={openSearch}>
              <ChevronUp size={11} strokeWidth={2.4} />
              Swipe up to search
            </button>
            <div className="device-dock">
              {TABS.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  className="device-tab"
                  data-active={tab.id === activeTabId}
                  onClick={() => setActiveTabId(tab.id)}
                  aria-label={`Open ${tab.name} tab`}
                >
                  <span className="device-tab-mark" style={{ '--tab-color': tab.color } as CSSProperties}>
                    {tab.mark}
                  </span>
                  <span className="device-tab-name">{tab.name}</span>
                </button>
              ))}
              <button type="button" className="device-add" onClick={openSearch} aria-label="Open quick search">
                <Plus size={14} strokeWidth={2.5} />
              </button>
            </div>
          </div>
        </div>

        <div className="device-search-layer" data-open={searchOpen} aria-hidden={!searchOpen} inert={!searchOpen}>
          <div className="device-search-head">
            <div>
              <p className="device-search-label">Quick search</p>
              <p className="mt-1 text-[0.68rem] font-bold text-paper">Where to?</p>
            </div>
            <button type="button" className="device-close" onClick={closeSearch} aria-label="Close quick search">
              <X size={14} />
            </button>
          </div>

          <div className="device-search-body">
            <form className="device-search-field" onSubmit={runSearch}>
              <span
                className="device-search-engine-mark"
                style={{ '--engine-color': activeEngine.color } as CSSProperties}
              >
                {activeEngine.mark}
              </span>
              <input
                ref={inputRef}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={`Search ${activeEngine.name}`}
                aria-label={`Search with ${activeEngine.name}`}
              />
              <button type="submit" className="device-search-submit" aria-label="Search">
                <ArrowRight size={14} strokeWidth={2.4} />
              </button>
            </form>

            <div className="device-engine-strip">
              <div className="device-engine-strip-head">
                <span>Engine · swipe to switch</span>
                <span className="flex items-center gap-0.5">
                  <button
                    type="button"
                    className="grid size-4 place-items-center text-muted"
                    onClick={() => cycleEngine(-1)}
                    aria-label="Previous search engine"
                  >
                    <ChevronLeft size={11} />
                  </button>
                  <button
                    type="button"
                    className="grid size-4 place-items-center text-muted"
                    onClick={() => cycleEngine(1)}
                    aria-label="Next search engine"
                  >
                    <ChevronRight size={11} />
                  </button>
                </span>
              </div>

              <div
                ref={railRef}
                className="device-engine-rail"
                onScroll={handleRailScroll}
                data-lenis-prevent
              >
                {engines.map((engine) => (
                  <button
                    type="button"
                    key={engine.id}
                    className="device-engine-option"
                    data-active={engine.id === activeEngineId}
                    data-engine-id={engine.id}
                    onClick={() => selectEngine(engine.id)}
                  >
                    <span
                      className="device-engine-option-mark"
                      style={{ '--engine-color': engine.color } as CSSProperties}
                    />
                    <span className="device-engine-option-name">{engine.name}</span>
                  </button>
                ))}
                <button
                  type="button"
                  className="device-engine-option device-engine-add"
                  onClick={() => setShowCustomForm((open) => !open)}
                >
                  <Plus size={10} />
                  <span className="device-engine-option-name">Add</span>
                </button>
              </div>

              {showCustomForm && customEngineForm}
            </div>

            {results ? (
              <div className="device-results" aria-live="polite">
                <div className="device-result-query">
                  “{results.query}” <span>on {results.engine}</span>
                </div>
                <MediaPlaceholder
                  type="video"
                  title="Search results"
                  description={`The results page for “${results.query}” would load here. Replace with a real screen recording of ${results.engine} results.`}
                  className="min-h-[8rem] flex-1"
                />
                <button
                  type="button"
                  className="rounded-lg border border-line py-1.5 text-[0.5rem] font-bold text-muted"
                  onClick={() => setResults(null)}
                >
                  Back to search
                </button>
              </div>
            ) : (
              <p className="device-search-tip">
                Scroll the engine rail sideways.
                <br />
                Tap + to add any engine that uses %s.
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
