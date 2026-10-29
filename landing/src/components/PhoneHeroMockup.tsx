import homeImage from '../assets/home.png';
import { ShieldCheck, Layers, Sparkles } from 'lucide-react';

export function PhoneHeroMockup() {
  return (
    <div className="relative mx-auto flex h-full w-full items-center justify-center [perspective:1400px]">
      {/* 3D Phone Chassis with CSS floating animation */}
      <div className="phone-3d-wrap relative flex items-center justify-center transition-transform duration-700 ease-out [transform-style:preserve-3d]">
        {/* Subtle glow behind phone */}
        <div
          className="pointer-events-none absolute -inset-4 rounded-[3rem] bg-brand/15 opacity-50 blur-xl transition-opacity [transform:translateZ(-30px)]"
          aria-hidden="true"
        />

        {/* Pixel 11 Phone Body - realistic subtle curvature and uniform bezels */}
        <div className="relative mx-auto h-[38vh] max-h-[19rem] sm:h-[46vh] sm:max-h-[26rem] lg:h-[min(34rem,64vh)] aspect-[1220/2712] rounded-[1.35rem] sm:rounded-[1.6rem] lg:rounded-[1.85rem] border border-white/25 bg-panel-2 p-1.5 sm:p-2 lg:p-2">
          {/* Pixel physical buttons on right edge */}
          <div className="absolute -right-[3px] top-[22%] h-4 sm:h-5 w-[3px] rounded-r-xs bg-white/35 shadow-sm" aria-hidden="true" />
          <div className="absolute -right-[3px] top-[32%] h-9 sm:h-11 w-[3px] rounded-r-xs bg-white/25 shadow-sm" aria-hidden="true" />

          {/* Inner bezel with display */}
          <div className="relative h-full w-full overflow-hidden rounded-[1.1rem] sm:rounded-[1.35rem] lg:rounded-[1.55rem] border border-black/50 bg-black">
            {/* Top earpiece speaker slit */}
            <div className="absolute top-0.5 left-1/2 z-30 h-[2px] w-7 sm:w-9 -translate-x-1/2 rounded-full bg-white/20" aria-hidden="true" />

            {/* Pixel centered circular punch-hole camera */}
            <div
              className="absolute top-2 sm:top-2.5 left-1/2 z-30 flex size-2.5 sm:size-3 -translate-x-1/2 items-center justify-center rounded-full bg-ink ring-1 ring-white/10 shadow-inner"
              aria-hidden="true"
            >
              <div className="size-1 rounded-full bg-panel" />
            </div>

            {/* Screen Screenshot */}
            <img
              src={homeImage}
              alt="Prosto Browser Interface"
              fetchPriority="high"
              loading="eager"
              decoding="async"
              className="block h-full w-full object-cover select-none"
            />

            {/* Glass sheen / reflection */}
            <div
              className="pointer-events-none absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.04] to-transparent"
              aria-hidden="true"
            />
          </div>
        </div>

        {/* Floating Card 1: Trackers blocked (3D space) */}
        <div
          className="absolute -top-2 -right-3 z-20 hidden rounded-2xl border border-brand/35 bg-panel p-2.5 shadow-md sm:block [transform:translateZ(45px)]"
          style={{ animation: 'floatCard 6s ease-in-out infinite' }}
        >
          <div className="flex items-center gap-2">
            <span className="grid size-5 place-items-center rounded-lg bg-brand/20 text-brand">
              <ShieldCheck size={13} />
            </span>
            <div>
              <p className="text-[0.6875rem] text-muted">Shield active</p>
              <p className="text-[0.6875rem] font-extrabold text-paper">0 Trackers passed</p>
            </div>
          </div>
        </div>

        {/* Floating Card 2: 1-hand thumb zone (3D space) */}
        <div
          className="absolute -bottom-3 -left-4 z-20 hidden rounded-2xl border border-mint/35 bg-panel p-2.5 shadow-md sm:block [transform:translateZ(50px)]"
          style={{ animation: 'floatCard 6s ease-in-out 1.5s infinite' }}
        >
          <div className="flex items-center gap-2">
            <span className="grid size-5 place-items-center rounded-lg bg-mint/20 text-mint">
              <Layers size={13} />
            </span>
            <div>
              <p className="text-[0.6875rem] text-muted">Thumb reach</p>
              <p className="text-[0.6875rem] font-extrabold text-paper">Dock at bottom ⅓</p>
            </div>
          </div>
        </div>

        {/* Floating Card 3: Lightweight (3D space) */}
        <div
          className="absolute top-1/2 -left-6 z-20 hidden -translate-y-1/2 rounded-2xl border border-white/20 bg-panel px-2.5 py-1.5 shadow-md md:block [transform:translateZ(35px)]"
          style={{ animation: 'floatCard 6s ease-in-out 3s infinite' }}
        >
          <div className="flex items-center gap-1.5">
            <Sparkles size={11} className="text-brand" />
            <span className="text-[0.6875rem] font-bold text-paper">4.0 MB APK</span>
          </div>
        </div>
      </div>
    </div>
  );
}
