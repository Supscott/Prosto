import { lazy, Suspense } from 'react';
import { ArrowUpRight, Download, MoveDown } from 'lucide-react';
import { DOWNLOAD_URL } from '../lib/site';

const BrowserScene = lazy(() => import('./BrowserScene'));

function SceneFallback() {
  return (
    <div className="scene-canvas">
      <div className="scene-fallback" />
    </div>
  );
}

export function Hero() {
  return (
    <section id="top" className="relative overflow-hidden pt-28 pb-8 sm:pt-36 lg:pt-40">
      <div className="site-grid pointer-events-none absolute inset-0 opacity-70" aria-hidden="true" />
      <div className="hero-glow pointer-events-none absolute inset-0" aria-hidden="true" />
      <div
        className="pointer-events-none absolute -top-40 left-1/2 h-[28rem] w-[28rem] -translate-x-1/2 rounded-full border border-brand/10"
        aria-hidden="true"
        data-parallax="-0.04"
      />

      <div className="shell relative z-10">
        <div className="grid items-center gap-4 lg:grid-cols-[1.05fr_0.95fr] lg:gap-2">
          <div data-reveal>
            <span className="pill">
              <span className="pill-dot" />
              Open source Android browser
            </span>

            <h1 className="display mt-6 text-[clamp(3.4rem,12vw,7.7rem)] text-paper">
              The web,
              <span className="block">
                within <em className="accent text-brand">reach.</em>
              </span>
            </h1>

            <p className="mt-6 max-w-[28rem] text-[0.98rem] leading-7 text-muted sm:mt-7 sm:text-base">
              Prosto puts every control under your thumb. Slide the dock, flick through search engines, and leave the
              trackers behind. No stretching. No clutter. No compromise.
            </p>

            <div className="mt-7 flex flex-col gap-2.5 sm:flex-row sm:items-center">
              <a href={DOWNLOAD_URL} target="_blank" rel="noreferrer" className="btn btn-primary">
                <Download size={16} strokeWidth={2.4} />
                Download the APK
                <ArrowUpRight size={15} strokeWidth={2.4} />
              </a>
              <a href="#dock" className="btn btn-ghost">
                See the gestures
                <MoveDown size={15} strokeWidth={2.2} />
              </a>
            </div>

            <div className="mt-9 grid max-w-[26rem] grid-cols-3 border-t border-line pt-5">
              <div>
                <p className="text-[1.45rem] font-extrabold tracking-[-0.05em] text-paper">4.0 MB</p>
                <p className="mt-1 font-mono text-[0.52rem] tracking-[0.08em] text-muted uppercase">APK footprint</p>
              </div>
              <div className="border-l border-line pl-4">
                <p className="text-[1.45rem] font-extrabold tracking-[-0.05em] text-paper">0</p>
                <p className="mt-1 font-mono text-[0.52rem] tracking-[0.08em] text-muted uppercase">trackers baked in</p>
              </div>
              <div className="border-l border-line pl-4">
                <p className="text-[1.45rem] font-extrabold tracking-[-0.05em] text-paper">1</p>
                <p className="mt-1 font-mono text-[0.52rem] tracking-[0.08em] text-muted uppercase">hand required</p>
              </div>
            </div>
          </div>

          <div className="scene-frame relative h-[23rem] w-full sm:h-[31rem] lg:h-[37rem]" data-parallax="0.07">
            <div className="scene-halo" aria-hidden="true" />
            <div className="scene-orbit" aria-hidden="true" />
            <Suspense fallback={<SceneFallback />}>
              <div className="scene-canvas">
                <BrowserScene />
              </div>
            </Suspense>

            <span className="scene-chip top-[16%] left-0 sm:left-[2%]">Tab dock</span>
            <span className="scene-chip top-[9%] right-0 sm:right-[4%]">Swipe up</span>
            <span className="scene-chip right-[8%] bottom-[17%] hidden sm:inline-flex">Quick search</span>
            <span className="scene-chip bottom-[28%] left-0 hidden md:inline-flex">No trackers</span>
          </div>
        </div>
      </div>
    </section>
  );
}
