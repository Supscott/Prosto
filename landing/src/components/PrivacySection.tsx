import { EyeOff, LockKeyhole, ShieldCheck } from 'lucide-react';
import { BLOCKED_REQUESTS } from '../lib/site';
import { useInView } from '../lib/useInView';

const PRIVACY_POINTS = [
  {
    title: 'EasyList, built in',
    body: 'Ads and trackers are matched against bundled EasyList rulesets before a request leaves the app.',
  },
  {
    title: 'Pages still work',
    body: 'Blocked images get a transparent pixel, beacons get a 204, and scripts receive benign stand-ins.',
  },
  {
    title: 'Nothing phones home',
    body: 'No analytics SDK. No telemetry. No account. Your tabs, history, and engines stay on your device.',
  },
];

export function PrivacySection() {
  const { ref, inView } = useInView<HTMLDivElement>(0.2);

  return (
    <section id="privacy" className="section relative overflow-hidden">
      <div
        className="pointer-events-none absolute top-1/3 -left-24 size-[28rem] rounded-full bg-brand/[0.025] blur-3xl"
        aria-hidden="true"
      />

      <div className="shell relative z-10">
        <div className="grid gap-12 lg:grid-cols-[0.9fr_1.1fr] lg:items-center lg:gap-16">
          <div data-reveal>
            <span className="eyebrow">03 / Quiet web</span>
            <h2 className="display mt-5 text-[clamp(2.5rem,6.5vw,4.8rem)] text-paper">
              The internet,
              <span className="block">
                with the noise turned <em className="accent text-brand">down.</em>
              </span>
            </h2>
            <p className="mt-5 max-w-md text-[0.95rem] leading-7 text-muted">
              Ad and tracker blocking is not an extension. It is the engine. Prosto filters requests inside the browser,
              keeps pages intact, and never asks you to trust a black box.
            </p>

            <div className="mt-8 border-t border-line">
              {PRIVACY_POINTS.map((point) => (
                <div key={point.title} className="border-b border-line py-4">
                  <h3 className="text-[0.86rem] font-extrabold tracking-[-0.02em] text-paper">{point.title}</h3>
                  <p className="mt-1 text-[0.8rem] leading-6 text-muted">{point.body}</p>
                </div>
              ))}
            </div>
          </div>

          <div ref={ref} className="relative" data-scale-in>
            <div className="absolute -inset-3 rounded-[2.2rem] border border-line/60" aria-hidden="true" />
            <div className="rounded-[1.8rem] border border-line bg-panel p-4 shadow-[0_50px_110px_-60px_rgba(0, 0, 0, 0.95)] sm:p-6">
              <div className="flex items-center justify-between">
                <span className="status-line">Shield active</span>
                <span className="rounded-full border border-brand/25 bg-brand/[0.08] px-2.5 py-1 font-mono text-[0.48rem] tracking-[0.12em] text-brand uppercase">
                  On-device
                </span>
              </div>

              <div className="mt-4 grid items-center gap-5 sm:grid-cols-[0.85fr_1.15fr]">
                <div className="radar mx-auto w-full max-w-[13rem]">
                  <span className="radar-sweep" />
                  <span className="radar-core">
                    <ShieldCheck size={30} strokeWidth={1.6} />
                  </span>
                  <span className="absolute top-[9%] left-1/2 -translate-x-1/2 font-mono text-[0.4rem] tracking-[0.12em] text-muted uppercase">
                    Tracker
                  </span>
                  <span className="absolute bottom-[12%] left-[6%] font-mono text-[0.4rem] tracking-[0.12em] text-muted uppercase">
                    Ad
                  </span>
                  <span className="absolute right-[5%] bottom-[22%] font-mono text-[0.4rem] tracking-[0.12em] text-muted uppercase">
                    Beacon
                  </span>
                </div>

                <div>
                  <div className="flex items-center justify-between border-b border-line pb-2 font-mono text-[0.46rem] tracking-[0.1em] text-muted uppercase">
                    <span>Request log</span>
                    <span className="text-brand">Blocked</span>
                  </div>
                  <div>
                    {BLOCKED_REQUESTS.map((request, index) => (
                      <div
                        key={request}
                        className="blocked-row"
                        style={{
                          animationDelay: `${index * 130}ms`,
                          animationPlayState: inView ? 'running' : 'paused',
                        }}
                      >
                        <span className="blocked-row-dot" />
                        <span className="truncate">{request}</span>
                        <span className="blocked-row-status">Stopped</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              <div className="mt-5 grid grid-cols-3 divide-x divide-line border-t border-line pt-4">
                <div className="pr-3">
                  <p className="text-[1.25rem] font-extrabold tracking-[-0.05em] text-paper">0</p>
                  <p className="mt-1 font-mono text-[0.43rem] tracking-[0.08em] text-muted uppercase">SDKs</p>
                </div>
                <div className="px-3">
                  <p className="text-[1.25rem] font-extrabold tracking-[-0.05em] text-paper">1×1</p>
                  <p className="mt-1 font-mono text-[0.43rem] tracking-[0.08em] text-muted uppercase">PNG swap</p>
                </div>
                <div className="pl-3">
                  <p className="text-[1.25rem] font-extrabold tracking-[-0.05em] text-paper">204</p>
                  <p className="mt-1 font-mono text-[0.43rem] tracking-[0.08em] text-muted uppercase">No content</p>
                </div>
              </div>
            </div>

            <div className="mt-4 flex items-start gap-2 px-1 text-[0.58rem] leading-4 text-muted/70">
              <LockKeyhole size={11} className="mt-0.5 flex-none" />
              <p>Example requests only. Prosto does not collect or report what it blocks.</p>
              <EyeOff size={11} className="mt-0.5 flex-none" />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
