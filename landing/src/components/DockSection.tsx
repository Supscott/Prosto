import { Check, MoveUpRight } from 'lucide-react';
import { DockDemo } from './DockDemo';

const DOCK_FEATURES = [
  {
    title: 'Swipe up. Start searching.',
    body: 'A short upward flick from anywhere on the dock opens a new tab and puts the cursor in the search field.',
  },
  {
    title: 'Tabs, not tab hunts.',
    body: 'Every open page stays visible in the dock. Tap to switch, drag to reorder, swipe to dismiss.',
  },
  {
    title: 'It gets out of the way.',
    body: 'The dock recedes as you read and returns the instant you scroll back up. No top bars, ever.',
  },
];

export function DockSection() {
  return (
    <section id="dock" className="section overflow-hidden">
      <div className="shell">
        <div className="grid gap-14 lg:grid-cols-[0.86fr_1.14fr] lg:items-center lg:gap-8">
          <div data-reveal>
            <span className="eyebrow">01 / The dock</span>
            <h2 className="display mt-5 text-[clamp(2.5rem,6.5vw,4.8rem)] text-paper">
              Tabs live where your <em className="accent text-brand">thumb</em> already is.
            </h2>
            <p className="mt-5 max-w-md text-[0.95rem] leading-7 text-muted">
              The floating dock is the whole browser. It sits in the bottom third of the screen, inside the natural arc
              of your thumb, and keeps every frequent action one gesture away.
            </p>

            <ul className="mt-8 border-t border-line">
              {DOCK_FEATURES.map((feature, index) => (
                <li key={feature.title} className="grid grid-cols-[1.6rem_1fr] gap-3 border-b border-line py-4">
                  <span className="mt-0.5 grid size-5 place-items-center rounded-full border border-brand/30 text-brand">
                    <Check size={11} strokeWidth={2.6} />
                  </span>
                  <div>
                    <div className="flex items-baseline gap-2">
                      <h3 className="text-[0.88rem] font-extrabold tracking-[-0.02em] text-paper">{feature.title}</h3>
                      <span className="font-mono text-[0.48rem] tracking-[0.08em] text-muted">0{index + 1}</span>
                    </div>
                    <p className="mt-1 text-[0.8rem] leading-6 text-muted">{feature.body}</p>
                  </div>
                </li>
              ))}
            </ul>

            <div className="mt-6 flex items-start gap-3 rounded-2xl border border-brand/20 bg-brand/[0.045] p-4">
              <span className="mt-0.5 grid size-7 flex-none place-items-center rounded-full bg-brand text-ink">
                <MoveUpRight size={14} strokeWidth={2.4} />
              </span>
              <p className="text-[0.78rem] leading-6 text-muted">
                <span className="font-bold text-paper">Try the live demo.</span> Drag the dock upward, switch tabs, flick
                the engine rail sideways, or add your own search engine. Everything works in this little phone.
              </p>
            </div>
          </div>

          <div className="relative flex flex-col items-center" data-parallax="0.05">
            <div
              className="pointer-events-none absolute top-1/2 left-1/2 size-[28rem] max-w-[120%] -translate-x-1/2 -translate-y-1/2 rounded-full border border-line/60 sm:size-[34rem]"
              aria-hidden="true"
            >
              <span className="absolute inset-[12%] rounded-full border border-dashed border-line/70" />
              <span className="absolute inset-[26%] rounded-full border border-brand/10" />
            </div>

            <span className="mb-5 inline-flex items-center gap-2 rounded-full border border-line bg-panel px-3 py-1.5 font-mono text-[0.54rem] tracking-[0.12em] text-muted uppercase">
              <span className="size-1.5 rounded-full bg-brand" />
              Interactive · no install
            </span>

            <div className="relative z-10">
              <DockDemo />
            </div>

            <div className="mt-5 flex items-center gap-3 text-[0.58rem] text-muted">
              <span className="font-mono tracking-[0.1em] uppercase">Tap + to add an engine</span>
              <span className="h-px w-8 bg-line" />
              <span className="font-mono tracking-[0.1em] uppercase">Swipe up for search</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
