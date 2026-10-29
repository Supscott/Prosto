import { useEffect, useState } from 'react';
import homeImage from '../assets/home.png';
import searchImage from '../assets/search.png';
import { ArrowLeftRight, ArrowUp } from 'lucide-react';
import { useInView } from '../lib/useInView';

type AnimationPhase = 'dock-resting' | 'search-expanded' | 'switching-engines';

const DURATION_RESTING_MS = 2500;
const DURATION_SEARCH_MS = 5500;
const DURATION_TOTAL_CYCLE_MS = 8500;

export function GestureAnimationDemo() {
  const [step, setStep] = useState<AnimationPhase>('dock-resting');
  const { ref, inView } = useInView<HTMLDivElement>(0.2);

  useEffect(() => {
    if (!inView) {
      setStep('dock-resting');
      return;
    }

    let timeouts: number[] = [];

    const schedulePhaseCycle = () => {
      timeouts.forEach((id) => window.clearTimeout(id));
      setStep('dock-resting');
      timeouts = [
        window.setTimeout(() => setStep('search-expanded'), DURATION_RESTING_MS),
        window.setTimeout(() => setStep('switching-engines'), DURATION_SEARCH_MS),
      ];
    };

    schedulePhaseCycle();
    const interval = window.setInterval(schedulePhaseCycle, DURATION_TOTAL_CYCLE_MS);

    return () => {
      timeouts.forEach((id) => window.clearTimeout(id));
      window.clearInterval(interval);
    };
  }, [inView]);

  return (
    <div ref={ref} className="relative mx-auto w-full max-w-[19rem] sm:max-w-[21rem]">
      {/* Phone chassis - Pixel 11 styling */}
      <div className="relative overflow-hidden rounded-[1.65rem] sm:rounded-[1.85rem] border border-white/25 bg-panel-2 p-2 sm:p-2.5">
        {/* Pixel physical buttons on right edge */}
        <div className="absolute -right-[3px] top-[22%] h-5 w-[3px] rounded-r-xs bg-white/35 shadow-sm" aria-hidden="true" />
        <div className="absolute -right-[3px] top-[32%] h-11 w-[3px] rounded-r-xs bg-white/25 shadow-sm" aria-hidden="true" />

        <div className="relative aspect-[9/19.5] overflow-hidden rounded-[1.35rem] sm:rounded-[1.55rem] border border-black/50 bg-black">
          {/* Top earpiece speaker slit */}
          <div className="absolute top-0.5 left-1/2 z-40 h-[2px] w-8 -translate-x-1/2 rounded-full bg-white/20" aria-hidden="true" />

          {/* Pixel centered circular punch-hole camera */}
          <div
            className="absolute top-2.5 left-1/2 z-40 flex size-3 -translate-x-1/2 items-center justify-center rounded-full bg-ink ring-1 ring-white/10 shadow-inner"
            aria-hidden="true"
          >
            <div className="size-1 rounded-full bg-panel" />
          </div>

          {/* Screen 1: Home View (Dock resting) */}
          <div
            className={`absolute inset-0 transition-opacity transition-transform duration-700 ease-out ${
              step === 'dock-resting' ? 'opacity-100 scale-100' : 'opacity-0 scale-95 pointer-events-none'
            }`}
          >
            <img
              src={homeImage}
              alt="Home screen with floating dock"
              className="h-full w-full object-cover select-none"
              loading="eager" fetchPriority="high"
            />
          </div>

          {/* Screen 2: Search View (Dock expanded) */}
          <div
            className={`absolute inset-0 transition-opacity transition-transform duration-700 ease-out ${
              step !== 'dock-resting' ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-105 translate-y-8 pointer-events-none'
            }`}
          >
            <img
              src={searchImage}
              alt="Search interface with engine rail"
              className="h-full w-full object-cover select-none"
              loading="eager" fetchPriority="high"
            />
          </div>

          {/* Simulated Animated Thumb Pointer & Trail */}
          <div className="pointer-events-none absolute inset-0 z-30">
            {step === 'dock-resting' && (
              <div
                className="absolute bottom-16 left-1/2 -translate-x-1/2 flex flex-col items-center"
                style={{ animation: 'thumbSwipeUp 2.4s ease-in-out infinite' }}
              >
                <div className="size-11 rounded-full border-2 border-brand/80 bg-brand/30 shadow-md animate-ping opacity-75" />
                <div className="absolute top-1.5 size-8 rounded-full border border-white/60 bg-white/40 shadow-lg" />
                <span className="mt-2 inline-flex items-center gap-1 rounded-full bg-black/90 px-2 py-0.5 text-[0.6875rem] font-bold text-brand shadow">
                  <ArrowUp size={10} /> Swipe Up
                </span>
              </div>
            )}

            {step === 'search-expanded' && (
              <div
                className="absolute bottom-28 left-1/2 -translate-x-1/2 flex flex-col items-center"
                style={{ animation: 'thumbFlick 2.8s ease-in-out infinite' }}
              >
                <div className="size-10 rounded-full border-2 border-mint/80 bg-mint/30 shadow-md" />
                <span className="mt-2 inline-flex items-center gap-1 rounded-full bg-black/90 px-2 py-0.5 text-[0.6875rem] font-bold text-mint shadow">
                  <ArrowLeftRight size={10} /> Flick Engine
                </span>
              </div>
            )}

            {step === 'switching-engines' && (
              <div className="absolute top-12 right-6 rounded-full bg-black/90 border border-brand/40 px-3 py-1 text-[0.6875rem] text-brand animate-pulse">
                ✓ Query kept intact
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Caption pill below preview */}
      <div className="mt-4 flex items-center justify-between rounded-xl border border-line bg-panel/90 px-3 py-2 text-xs">
        <span className="inline-flex items-center gap-2 text-[0.6875rem] text-muted">
          <span className="size-1.5 rounded-full bg-brand animate-pulse" />
          {step === 'dock-resting' && '01 / Dock sits under thumb'}
          {step === 'search-expanded' && '02 / Swipe up opens search'}
          {step === 'switching-engines' && '03 / Flick switches engines'}
        </span>
        <span className="text-[0.6875rem] text-muted">Auto-looping GIF demo</span>
      </div>
    </div>
  );
}
