import { ArrowUpRight, Download, MoveDown } from 'lucide-react';
import { DIRECT_APK_URL } from '../lib/site';
import { PhoneHeroMockup } from './PhoneHeroMockup';

export function Hero() {
  return (
    <section
      id="top"
      className="relative flex min-h-[100dvh] flex-col justify-center overflow-hidden pt-16 pb-6 sm:pt-20 sm:pb-8 lg:h-[100dvh] lg:max-h-[100dvh] lg:py-0"
    >
      <div className="site-grid pointer-events-none absolute inset-0 opacity-70" aria-hidden="true" />
      <div className="hero-glow pointer-events-none absolute inset-0" aria-hidden="true" />
      <div
        className="pointer-events-none absolute -top-40 left-1/2 h-[28rem] w-[28rem] -translate-x-1/2 rounded-full border border-brand/10"
        aria-hidden="true"
        data-parallax="-0.04"
      />

      <div className="shell relative z-10 my-auto w-full">
        <div className="grid items-center gap-5 sm:gap-6 lg:grid-cols-[1.1fr_0.9fr] lg:gap-8">
          <div data-reveal className="flex flex-col justify-center">
            <span className="pill self-start text-[0.66rem] sm:text-xs">
              <span className="pill-dot" />
              Open source Android browser
            </span>

            <h1 className="display mt-3 sm:mt-5 text-[clamp(2.4rem,4.6vw,5.4rem)] leading-[0.95] text-paper">
              The web,
              <span className="block">
                within <em className="accent text-brand">reach.</em>
              </span>
            </h1>

            <p className="mt-3 max-w-[28rem] text-[0.88rem] leading-relaxed text-muted sm:mt-4 sm:text-base sm:leading-7">
              Prosto puts every control under your thumb. Slide the dock, flick through search engines, and leave the
              trackers behind. No stretching. No clutter. No compromise.
            </p>

            <div className="mt-5 flex flex-wrap items-center gap-2.5 sm:mt-6 sm:gap-3">
              <a href={DIRECT_APK_URL} className="btn btn-primary min-h-10 text-xs sm:text-sm">
                <Download size={15} strokeWidth={2.4} />
                Download APK (4 MB)
                <ArrowUpRight size={14} strokeWidth={2.4} />
              </a>
              <a href="#dock" className="btn btn-ghost min-h-10 text-xs sm:text-sm">
                See the gestures
                <MoveDown size={14} strokeWidth={2.2} />
              </a>
              <a
                href={DIRECT_APK_URL}
                className="hidden items-center gap-2 rounded-2xl border border-line bg-panel/70 px-2.5 py-1.5 text-[0.64rem] text-muted transition hover:border-brand/40 hover:text-paper sm:flex"
                title="Scan with Android phone to download APK"
              >
                <img
                  src="/qr-download.svg"
                  alt="QR Code"
                  className="size-6 rounded bg-black/40 p-0.5"
                  width={24}
                  height={24}
                />
                <span className="leading-tight">
                  <span className="block font-bold text-paper">Scan with phone</span>
                  <span className="font-mono text-[0.52rem] text-muted">Android 8.0+</span>
                </span>
              </a>
            </div>

            <div className="mt-6 grid max-w-[25rem] grid-cols-3 border-t border-line pt-3 sm:pt-4">
              <div>
                <p className="text-[1.25rem] font-extrabold tracking-[-0.05em] text-paper sm:text-[1.45rem]">4.0 MB</p>
                <p className="mt-0.5 font-mono text-[0.48rem] tracking-[0.08em] text-muted uppercase sm:text-[0.52rem]">
                  APK footprint
                </p>
              </div>
              <div className="border-l border-line pl-3 sm:pl-4">
                <p className="text-[1.25rem] font-extrabold tracking-[-0.05em] text-paper sm:text-[1.45rem]">0</p>
                <p className="mt-0.5 font-mono text-[0.48rem] tracking-[0.08em] text-muted uppercase sm:text-[0.52rem]">
                  trackers baked in
                </p>
              </div>
              <div className="border-l border-line pl-3 sm:pl-4">
                <p className="text-[1.25rem] font-extrabold tracking-[-0.05em] text-paper sm:text-[1.45rem]">1</p>
                <p className="mt-0.5 font-mono text-[0.48rem] tracking-[0.08em] text-muted uppercase sm:text-[0.52rem]">
                  hand required
                </p>
              </div>
            </div>
          </div>

          <div
            className="scene-frame relative flex h-full max-h-[46vh] sm:max-h-[52vh] lg:max-h-[min(36rem,calc(100dvh-6rem))] w-full items-center justify-center py-2"
            data-parallax="0.04"
          >
            <div className="scene-halo" aria-hidden="true" />
            <div className="scene-orbit" aria-hidden="true" />

            <div className="relative z-10 flex h-full w-full items-center justify-center">
              <PhoneHeroMockup />
            </div>

            <span className="scene-chip top-[10%] left-0 sm:left-[2%]">Tab dock</span>
            <span className="scene-chip top-[5%] right-0 sm:right-[4%]">Swipe up</span>
            <span className="scene-chip right-[2%] bottom-[12%] hidden sm:inline-flex">Quick search</span>
            <span className="scene-chip bottom-[18%] left-0 hidden md:inline-flex">No trackers</span>
          </div>
        </div>
      </div>
    </section>
  );
}
