import type { CSSProperties } from 'react';
import { ArrowLeftRight, ArrowUpRight, Plus, Search } from 'lucide-react';
import { BUILT_IN_ENGINES } from '../lib/site';

const engineRow = [...BUILT_IN_ENGINES, ...BUILT_IN_ENGINES];

export function SearchSection() {
  return (
    <section id="search" className="section relative overflow-hidden border-y border-line bg-ink-soft">
      <div
        className="pointer-events-none absolute -top-16 right-0 font-extrabold tracking-[-0.1em] text-white/[0.025] select-none"
        aria-hidden="true"
        data-parallax="-0.06"
      >
        <span className="display text-[clamp(8rem,28vw,26rem)] leading-none">SEARCH</span>
      </div>

      <div className="shell relative z-10">
        <div className="grid gap-12 lg:grid-cols-[0.8fr_1.2fr] lg:items-center lg:gap-16">
          <div data-reveal>
            <span className="eyebrow">02 / Quick search</span>
            <h2 className="display mt-5 text-[clamp(2.5rem,6.5vw,4.8rem)] text-paper">
              One swipe.
              <span className="block">
                Every <em className="accent text-mint">engine.</em>
              </span>
            </h2>
            <p className="mt-5 max-w-md text-[0.95rem] leading-7 text-muted">
              The search bar is a carousel, not a dropdown. Flick left or right to move between Google, DuckDuckGo,
              Brave, Kagi, Ecosia, Startpage, and any provider you add yourself.
            </p>

            <div className="mt-7 flex flex-wrap gap-2">
              <span className="pill">
                <ArrowLeftRight size={11} />
                Swipe to switch
              </span>
              <span className="pill">
                <Plus size={11} />
                Add any engine
              </span>
              <span className="pill">
                <Search size={11} />
                Query stays intact
              </span>
            </div>

            <a href="#dock" className="btn btn-ghost mt-8">
              Try it in the dock
              <ArrowUpRight size={15} />
            </a>
          </div>

          <div className="relative min-w-0" data-scale-in>
            <div className="rounded-[1.8rem] border border-line bg-panel p-3.5 shadow-[0_50px_100px_-55px_rgba(0, 0, 0, 0.9)] sm:p-5">
              <div className="flex items-center gap-2.5 rounded-2xl border border-brand/35 bg-[#121916] p-2 shadow-[0_0_0_4px_rgba(86, 219, 207, 0.05)]">
                <span className="grid size-9 flex-none place-items-center rounded-xl bg-[#f6c945] font-mono text-sm font-extrabold text-ink">
                  G
                </span>
                <span className="min-w-0 flex-1 text-[0.82rem] font-semibold text-muted">Search without the noise</span>
                <span className="hidden items-center gap-1 rounded-full border border-line px-2 py-1 font-mono text-[0.46rem] tracking-[0.08em] text-muted uppercase sm:flex">
                  Swipe
                  <ArrowLeftRight size={9} />
                </span>
                <span className="grid size-8 flex-none place-items-center rounded-full bg-brand text-ink">
                  <ArrowUpRight size={15} strokeWidth={2.4} />
                </span>
              </div>

              <div className="relative mt-6 min-w-0 overflow-hidden [mask-image:linear-gradient(90deg,transparent,black_7%,black_93%,transparent)]">
                <div className="engine-marquee">
                  {engineRow.map((engine, index) => (
                    <div className="engine-tile" key={`a-${engine.id}-${index}`}>
                      <span
                        className="engine-tile-mark"
                        style={{ '--engine-color': engine.color } as CSSProperties}
                      >
                        {engine.mark}
                      </span>
                      <span>
                        <span className="engine-tile-name block">{engine.name}</span>
                        <span className="engine-tile-meta">Search</span>
                      </span>
                    </div>
                  ))}
                </div>
                <div className="engine-marquee engine-marquee--reverse mt-2">
                  {engineRow.map((engine, index) => (
                    <div className="engine-tile" key={`b-${engine.id}-${index}`}>
                      <span
                        className="engine-tile-mark"
                        style={{ '--engine-color': engine.color } as CSSProperties}
                      >
                        {engine.mark}
                      </span>
                      <span>
                        <span className="engine-tile-name block">{engine.name}</span>
                        <span className="engine-tile-meta">Search</span>
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="mt-6 grid gap-2 sm:grid-cols-[1.3fr_0.7fr]">
                <div className="rounded-2xl border border-line bg-ink/40 p-3.5">
                  <p className="font-mono text-[0.48rem] tracking-[0.12em] text-brand uppercase">Bring your own</p>
                  <p className="mt-2 text-[0.74rem] font-bold text-paper">Any provider. Any URL.</p>
                  <p className="mt-1 text-[0.62rem] leading-4 text-muted">
                    Drop in a search URL with <span className="font-mono text-mint">%s</span> where the query goes.
                  </p>
                  <div className="mt-3 rounded-lg border border-line bg-black/20 px-2.5 py-2 font-mono text-[0.52rem] text-muted">
                    https://your-engine.com/?q=<span className="text-brand">%s</span>
                  </div>
                </div>
                <div className="flex flex-col justify-between rounded-2xl border border-line bg-[#0C110F] p-3.5">
                  <span className="grid size-7 place-items-center rounded-full border border-mint/30 text-mint">
                    <Plus size={13} />
                  </span>
                  <div>
                    <p className="text-[0.72rem] font-extrabold text-paper">Saved locally.</p>
                    <p className="mt-1 text-[0.6rem] leading-4 text-muted">Never synced. Never tracked.</p>
                  </div>
                </div>
              </div>
            </div>

            <span className="absolute -top-3 right-5 rounded-full border border-brand/30 bg-brand px-2.5 py-1 font-mono text-[0.5rem] font-bold tracking-[0.1em] text-ink uppercase">
              6 built-in + yours
            </span>
          </div>
        </div>
      </div>
    </section>
  );
}
