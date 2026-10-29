import type { CSSProperties } from 'react';
import { ArrowUpRight, Download, ShieldCheck, Smartphone } from 'lucide-react';
import { DIRECT_APK_URL, DOWNLOAD_URL } from '../lib/site';

export function DownloadSection() {
  return (
    <section id="download" className="px-3 pt-3 pb-3 sm:px-4 sm:pt-4 sm:pb-4">
      <div className="download-plane mx-auto max-w-[86rem] px-5 py-10 sm:px-10 sm:py-14 lg:px-14 lg:py-16">
        <span className="download-word display" aria-hidden="true">
          PROSTO
        </span>

        <div className="relative z-10 grid items-center gap-12 lg:grid-cols-[1.15fr_0.85fr]">
          <div data-reveal>
            <span className="inline-flex items-center gap-2 rounded-full border border-ink/20 px-3 py-1.5 font-mono text-[0.52rem] font-semibold tracking-[0.12em] text-ink/70 uppercase">
              <span className="size-1.5 rounded-full bg-ink" />
              Latest release · v1.0
            </span>

            <h2 className="display mt-6 max-w-[14ch] text-[clamp(2.9rem,7.5vw,6.3rem)] leading-[0.9] text-ink">
              Put the web back under your <em className="accent">thumb.</em>
            </h2>

            <p className="mt-5 max-w-md text-[0.94rem] leading-7 text-ink/70">
              One 4 MB APK. No account. No onboarding maze. Download Prosto, set it as your browser, and feel the difference in
              the first swipe.
            </p>

            <div className="mt-7 flex flex-col gap-2.5 sm:flex-row sm:items-center">
              <a href={DIRECT_APK_URL} className="btn btn-dark">
                <Download size={16} strokeWidth={2.4} />
                Download APK (4 MB)
                <ArrowUpRight size={15} strokeWidth={2.4} />
              </a>
              <a
                href={DOWNLOAD_URL}
                target="_blank"
                rel="noreferrer"
                className="btn border border-ink/20 bg-transparent text-ink hover:border-ink/40 hover:bg-ink/5"
              >
                Releases & Changelog
              </a>
            </div>

            <div className="mt-6 hidden items-center gap-3.5 rounded-2xl border border-ink/15 bg-ink/[0.04] p-3 sm:flex">
              <img
                src="/qr-download.svg"
                alt="QR Code to download APK"
                className="size-14 rounded-xl bg-ink p-1.5 shadow-sm"
                width={56}
                height={56}
              />
              <div>
                <p className="text-[0.8rem] font-bold text-ink">Scan from your phone</p>
                <p className="text-[0.66rem] text-ink/70">Open camera on your Android phone to start instant download.</p>
              </div>
            </div>

            {/* 3-step installation guide */}
            <div className="mt-8 border-t border-ink/15 pt-5">
              <p className="font-mono text-[0.52rem] font-bold tracking-[0.1em] text-ink/70 uppercase">
                Quick install (3 steps)
              </p>
              <div className="mt-3 grid gap-2.5 sm:grid-cols-3">
                <div className="rounded-xl border border-ink/15 bg-ink/[0.03] p-3">
                  <span className="font-mono text-[0.65rem] font-extrabold text-ink">01</span>
                  <p className="mt-1 text-[0.74rem] font-bold text-ink">Download APK</p>
                  <p className="mt-0.5 text-[0.62rem] leading-snug text-ink/70">
                    Direct 4 MB file, completes in a few seconds.
                  </p>
                </div>
                <div className="rounded-xl border border-ink/15 bg-ink/[0.03] p-3">
                  <span className="font-mono text-[0.65rem] font-extrabold text-ink">02</span>
                  <p className="mt-1 text-[0.74rem] font-bold text-ink">Open file</p>
                  <p className="mt-0.5 text-[0.62rem] leading-snug text-ink/70">
                    Tap the download notification or check Downloads folder.
                  </p>
                </div>
                <div className="rounded-xl border border-ink/15 bg-ink/[0.03] p-3">
                  <span className="font-mono text-[0.65rem] font-extrabold text-ink">03</span>
                  <p className="mt-1 text-[0.74rem] font-bold text-ink">Confirm install</p>
                  <p className="mt-0.5 text-[0.62rem] leading-snug text-ink/70">
                    Allow "Install unknown apps" from browser if prompted.
                  </p>
                </div>
              </div>
            </div>

            <div className="mt-7 flex flex-wrap gap-x-5 gap-y-2 font-mono text-[0.52rem] font-semibold tracking-[0.1em] text-ink/60 uppercase">
              <span className="inline-flex items-center gap-1.5">
                <Smartphone size={12} /> Android 8.0+
              </span>
              <span>4 MB APK</span>
              <span>GPL-3.0</span>
              <span className="inline-flex items-center gap-1.5">
                <ShieldCheck size={12} /> No trackers
              </span>
            </div>
          </div>

          <div className="relative mx-auto w-full max-w-[18rem] lg:max-w-[20rem]" data-parallax="-0.055">
            <div className="absolute -top-4 -left-4 z-20 rotate-[-8deg] rounded-full border border-ink/15 bg-paper px-3 py-1.5 font-mono text-[0.5rem] font-bold tracking-[0.1em] text-ink uppercase shadow-lg">
              No top bar
            </div>
            <div className="absolute -right-3 bottom-10 z-20 rotate-[7deg] rounded-full bg-ink px-3 py-1.5 font-mono text-[0.5rem] font-bold tracking-[0.1em] text-brand uppercase shadow-xl">
              Zero telemetry
            </div>

            <div className="rotate-[5deg] rounded-[1.75rem] border border-ink/25 bg-ink p-1.5 shadow-[0_55px_90px_-38px_rgba(0, 0, 0, 0.65)]">
              <div className="device-screen !rounded-[1.45rem]">
                <div className="device-status !text-paper/60">
                  <span>10:08</span>
                  <span className="size-2 rounded-full bg-black/90 ring-1 ring-white/10" aria-hidden="true" />
                  <span>5G</span>
                </div>
                <div className="flex flex-1 flex-col gap-2 p-3 pb-24">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-mono text-[0.4rem] tracking-[0.1em] text-muted">PROSTO.APP</p>
                      <p className="mt-1 text-[0.68rem] font-extrabold text-paper">A quieter internet</p>
                    </div>
                    <span className="rounded-full border border-line px-1.5 py-0.5 font-mono text-[0.38rem] text-muted">
                      LIVE
                    </span>
                  </div>
                  <div className="grid flex-1 grid-cols-2 gap-1.5">
                    <div className="rounded-xl border border-dashed border-white/15 bg-white/[0.04]" />
                    <div className="flex flex-col gap-1.5">
                      <div className="flex-1 rounded-xl border border-dashed border-white/15 bg-white/[0.04]" />
                      <div className="h-1/3 rounded-xl bg-brand/10 p-2">
                        <span className="font-mono text-[0.36rem] tracking-[0.1em] text-brand uppercase">Shield on</span>
                      </div>
                    </div>
                  </div>
                </div>
                <div className="absolute right-3 bottom-8 left-3">
                  <div className="mb-1.5 flex items-center justify-between rounded-full border border-brand/25 bg-[#141C19] py-1.5 pr-1.5 pl-2">
                    <span className="font-mono text-[0.4rem] text-muted">Search without the noise</span>
                    <span className="grid size-4 place-items-center rounded-full bg-brand text-[0.5rem] text-ink">→</span>
                  </div>
                  <div className="mini-dock !border-white/10 !bg-[#0E1512]">
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
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
