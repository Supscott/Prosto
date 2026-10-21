import type { CSSProperties } from 'react';
import { ArrowUpRight, Box, Fingerprint, Layers, Search, ShieldCheck, Terminal, Zap } from 'lucide-react';
import { BUILT_IN_ENGINES, REPO_URL } from '../lib/site';
import { AnimatedNumber } from './AnimatedNumber';
import { SpotlightCard } from './SpotlightCard';

export function BentoSection() {
  return (
    <section id="specs" className="section border-t border-line">
      <div className="shell">
        <div className="flex flex-col gap-6 md:flex-row md:items-end md:justify-between" data-reveal>
          <div>
            <span className="eyebrow">04 / Under the hood</span>
            <h2 className="display mt-5 max-w-3xl text-[clamp(2.5rem,6.5vw,4.8rem)] text-paper">
              Small by design.
              <span className="block">
                Fast by <em className="accent text-brand">default.</em>
              </span>
            </h2>
          </div>
          <p className="max-w-sm text-[0.88rem] leading-6 text-muted">
            Prosto uses the WebView already on your phone instead of shipping another Chromium. The result is a browser
            that opens instantly and stays out of your RAM.
          </p>
        </div>

        <div className="mt-10 grid auto-rows-[minmax(13rem,auto)] grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <SpotlightCard className="flex flex-col justify-between p-5 transition-transform duration-300 hover:-translate-y-1 sm:col-span-2 sm:p-7">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="font-mono text-[0.55rem] tracking-[0.13em] text-brand uppercase">Tiny footprint</p>
                <p className="mt-3 text-[clamp(4rem,12vw,7.5rem)] leading-none font-extrabold tracking-[-0.08em] text-paper">
                  <AnimatedNumber value={4} decimals={1} />
                  <span className="ml-1 text-[0.28em] font-bold tracking-[-0.03em] text-muted">MB</span>
                </p>
                <p className="mt-1 text-[0.78rem] text-muted">Uncompressed APK. Entirely self-contained.</p>
              </div>
              <span className="grid size-10 flex-none place-items-center rounded-full border border-line text-brand">
                <Box size={17} />
              </span>
            </div>

            <div className="mt-8 space-y-3">
              <div>
                <div className="mb-1.5 flex items-center justify-between font-mono text-[0.48rem] tracking-[0.08em] uppercase">
                  <span className="text-paper">Prosto</span>
                  <span className="text-brand">4 MB</span>
                </div>
                <div className="compare-bar h-2">
                  <span className="w-[5%] bg-brand" />
                </div>
              </div>
              <div>
                <div className="mb-1.5 flex items-center justify-between font-mono text-[0.48rem] tracking-[0.08em] uppercase">
                  <span className="text-muted">Typical Chromium browser</span>
                  <span className="text-muted">~220 MB</span>
                </div>
                <div className="compare-bar h-2">
                  <span className="w-full bg-white/16" />
                </div>
              </div>
            </div>
          </SpotlightCard>

          <SpotlightCard className="flex flex-col justify-between p-5 transition-transform duration-300 hover:-translate-y-1 sm:p-7">
            <div className="flex items-start justify-between">
              <span className="grid size-10 place-items-center rounded-full border border-line text-brand">
                <Zap size={17} />
              </span>
              <span className="font-mono text-[0.44rem] tracking-[0.1em] text-muted uppercase">0 ms lag</span>
            </div>
            <div>
              <h3 className="text-[1.1rem] font-extrabold tracking-[-0.04em] text-paper">A dock that disappears.</h3>
              <p className="mt-2 text-[0.76rem] leading-5 text-muted">
                Scroll down and the dock recedes. Scroll up and it is back before you notice.
              </p>
              <div className="mini-dock mt-4">
                <span className="mini-tab" data-active="true">
                  <span className="mini-tab-dot" />
                  Field notes
                </span>
                <span className="mini-tab">
                  <span className="mini-tab-dot" style={{ '--dot': '#A5F2E9' } as CSSProperties} />
                </span>
                <span className="mini-tab">
                  <span className="mini-tab-dot" style={{ '--dot': '#7FD8CB' } as CSSProperties} />
                </span>
              </div>
            </div>
          </SpotlightCard>

          <SpotlightCard className="flex flex-col justify-between p-5 transition-transform duration-300 hover:-translate-y-1 sm:p-7">
            <div className="flex items-start justify-between">
              <span className="grid size-10 place-items-center rounded-full border border-line text-mint">
                <Search size={17} />
              </span>
              <span className="font-mono text-[0.44rem] tracking-[0.1em] text-muted uppercase">6 built-in</span>
            </div>
            <div>
              <h3 className="text-[1.1rem] font-extrabold tracking-[-0.04em] text-paper">Your engines, your order.</h3>
              <p className="mt-2 text-[0.76rem] leading-5 text-muted">
                Keep the providers you trust. Hide the rest. Add your own with a single URL pattern.
              </p>
              <div className="mt-4 flex flex-wrap gap-1.5">
                {BUILT_IN_ENGINES.slice(0, 4).map((engine) => (
                  <span
                    key={engine.id}
                    className="inline-flex items-center gap-1.5 rounded-full border border-line px-2 py-1 text-[0.56rem] font-bold text-muted"
                  >
                    <span className="size-1.5 rounded-full" style={{ background: engine.color }} />
                    {engine.name}
                  </span>
                ))}
                <span className="inline-flex items-center rounded-full border border-dashed border-brand/30 px-2 py-1 font-mono text-[0.52rem] text-brand">
                  + you
                </span>
              </div>
            </div>
          </SpotlightCard>

          <SpotlightCard className="flex flex-col justify-between p-5 transition-transform duration-300 hover:-translate-y-1 sm:p-7">
            <div className="flex items-start justify-between">
              <span className="grid size-10 place-items-center rounded-full border border-line text-teal">
                <Fingerprint size={17} />
              </span>
              <span className="font-mono text-[0.44rem] tracking-[0.1em] text-muted uppercase">Local only</span>
            </div>
            <div>
              <h3 className="text-[1.1rem] font-extrabold tracking-[-0.04em] text-paper">Zero telemetry. Zero accounts.</h3>
              <p className="mt-2 text-[0.76rem] leading-5 text-muted">
                History, favorites, tabs, and downloads live in a local Room database. Nothing follows you out.
              </p>
              <div className="mt-4 flex items-center gap-2 rounded-xl border border-line bg-white/[0.025] px-3 py-2">
                <ShieldCheck size={14} className="text-brand" />
                <span className="font-mono text-[0.48rem] tracking-[0.1em] text-muted uppercase">Nothing to opt out of</span>
              </div>
            </div>
          </SpotlightCard>

          <SpotlightCard className="flex flex-col justify-between p-5 transition-transform duration-300 hover:-translate-y-1 sm:p-7">
            <div className="flex items-start justify-between">
              <span className="grid size-10 place-items-center rounded-full border border-line text-warm">
                <Layers size={17} />
              </span>
              <span className="font-mono text-[0.44rem] tracking-[0.1em] text-muted uppercase">Android 8+</span>
            </div>
            <div>
              <h3 className="text-[1.1rem] font-extrabold tracking-[-0.04em] text-paper">Your WebView, supercharged.</h3>
              <p className="mt-2 text-[0.76rem] leading-5 text-muted">
                No bundled engine. Prosto reuses the WebView on your phone with pooled tabs and crash recovery.
              </p>
              <div className="mt-4 flex flex-wrap gap-1.5">
                <span className="rounded-full border border-line px-2 py-1 font-mono text-[0.46rem] tracking-[0.06em] text-muted uppercase">
                  WebView pool
                </span>
                <span className="rounded-full border border-line px-2 py-1 font-mono text-[0.46rem] tracking-[0.06em] text-muted uppercase">
                  Crash-safe
                </span>
              </div>
            </div>
          </SpotlightCard>

          <SpotlightCard className="flex flex-col justify-between gap-6 p-5 transition-transform duration-300 hover:-translate-y-1 sm:col-span-2 sm:p-7 lg:col-span-3">
            <div className="flex items-start justify-between">
              <div>
                <p className="font-mono text-[0.55rem] tracking-[0.13em] text-brand uppercase">Open source · GPL-3.0</p>
                <h3 className="mt-3 text-[1.4rem] font-extrabold tracking-[-0.05em] text-paper">
                  Audit it. Fork it. Make it yours.
                </h3>
                <p className="mt-2 max-w-md text-[0.78rem] leading-5 text-muted">
                  Every line is on GitHub. The build is reproducible, the issue tracker is public, and the whole thing
                  belongs to the people who use it.
                </p>
              </div>
              <span className="grid size-10 flex-none place-items-center rounded-full border border-line text-paper">
                <Terminal size={17} />
              </span>
            </div>

            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <div className="min-w-0 flex-1 overflow-x-auto rounded-xl border border-line bg-black/25 px-3.5 py-3 font-mono text-[0.58rem] text-muted">
                <span className="text-brand">$</span> git clone https://github.com/Supscott/browser.git
              </div>
              <a
                href={REPO_URL}
                target="_blank"
                rel="noreferrer"
                className="btn btn-ghost min-h-10 shrink-0 px-4 text-[0.66rem]"
              >
                View source
                <ArrowUpRight size={13} />
              </a>
            </div>
          </SpotlightCard>
        </div>
      </div>
    </section>
  );
}
