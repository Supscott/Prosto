import { Play } from 'lucide-react';
import { MediaPlaceholder } from './MediaPlaceholder';

const STORY = [
  {
    time: '00:00',
    title: 'The dock waits',
    body: 'A page is open. The floating dock rests above the gesture bar with three live tabs.',
  },
  {
    time: '00:02',
    title: 'Thumb moves up',
    body: 'A short swipe opens quick search in place. The keyboard rises, the cursor is already waiting.',
  },
  {
    time: '00:04',
    title: 'One sideways flick',
    body: 'The engine rail moves from Google to Kagi without retyping the query. The search runs instantly.',
  },
];

export function VideoSection() {
  return (
    <section className="section relative overflow-hidden border-t border-line">
      <div className="shell">
        <div className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between" data-reveal>
          <div>
            <h2 className="display mt-5 text-[clamp(2.5rem,6.5vw,4.8rem)] text-paper">
              See the thumb flow.
            </h2>
          </div>
          <p className="max-w-sm text-[0.88rem] leading-6 text-muted">
            The whole interaction model is one continuous movement. No menus, no reach, no pause to find the controls.
          </p>
        </div>

        <div className="relative mt-9" data-scale-in>
          <div className="absolute -inset-x-3 -top-3 h-6 rounded-t-[2rem] border-x border-t border-line/50" aria-hidden="true" />
          <div className="relative overflow-hidden rounded-[1.7rem] border border-line bg-panel p-2 sm:p-3">
            <MediaPlaceholder
              type="video"
              title="Dock → swipe up → search → switch engine"
              description="Replace with a 16:9 screen recording: a thumb drags the dock upward, quick search snaps open, then the engine rail scrolls from Google to Kagi while the query stays intact."
              className="aspect-[16/10] sm:aspect-[16/9]"
              wide
            />

            <div className="pointer-events-none absolute top-5 left-5 flex items-center gap-2 rounded-full border border-line bg-ink/75 px-2.5 py-1.5 sm:top-7 sm:left-7">
              <span className="grid size-5 place-items-center rounded-full bg-brand text-ink">
                <Play size={9} fill="currentColor" />
              </span>
              <span className="text-[0.6875rem] text-muted">Product film · 0:12</span>
            </div>

            <div className="pointer-events-none absolute right-5 bottom-5 hidden items-center gap-2 rounded-full border border-line bg-ink/75 px-2.5 py-1.5 sm:right-7 sm:bottom-7 sm:flex">
              <span className="size-1.5 rounded-full bg-warm" />
              <span className="text-[0.6875rem] text-muted">Replace placeholder</span>
            </div>
          </div>
        </div>

        <div className="mt-8 grid gap-0 border-t border-line sm:grid-cols-3">
          {STORY.map((item, index) => (
            <div
              key={item.time}
              className={`py-5 sm:px-5 ${index > 0 ? 'border-t border-line sm:border-t-0 sm:border-l' : ''}`}
            >
              <div className="flex items-center justify-between">
                <span className="step-number">0{index + 1}</span>
                <span className="text-[0.6875rem] text-muted">{item.time}</span>
              </div>
              <h3 className="mt-4 text-[0.92rem] font-extrabold tracking-[-0.025em] text-paper">{item.title}</h3>
              <p className="mt-1.5 text-[0.77rem] leading-6 text-muted">{item.body}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
