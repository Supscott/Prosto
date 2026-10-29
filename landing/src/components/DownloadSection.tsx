import { ArrowUpRight, Download, ShieldCheck, Smartphone } from 'lucide-react';
import { DOWNLOAD_URL, PLAY_STORE_URL } from '../lib/site';
import homeImage from '../assets/home.png';

export function DownloadSection() {
  return (
    <section id="download" className="px-3 pt-3 pb-3 sm:px-4 sm:pt-4 sm:pb-4">
      <div className="download-plane mx-auto max-w-[86rem] px-5 py-10 sm:px-10 sm:py-14 lg:px-14 lg:py-16">
        <span className="download-word display" aria-hidden="true">
          PROSTO
        </span>

        <div className="relative z-10 grid items-center gap-12 lg:grid-cols-[1.15fr_0.85fr]">
          <div data-reveal>
            <span className="inline-flex items-center gap-2 rounded-full border border-ink/20 px-3 py-1.5 text-[0.6875rem] font-semibold text-ink/70">
              <span className="size-1.5 rounded-full bg-ink" />
              Latest release · v1.0
            </span>

            <h2 className="display mt-6 max-w-[14ch] text-[clamp(2.9rem,7.5vw,6.3rem)] leading-[0.9] text-ink">
              Put the web back under your thumb.
            </h2>

            <p className="mt-5 max-w-md text-[0.94rem] leading-7 text-ink/70">
              Install Prosto from Google Play, set it as your browser, and feel the difference in the first swipe.
            </p>

            <div className="mt-7 flex flex-col gap-2.5 sm:flex-row sm:items-center">
              <a href={PLAY_STORE_URL} className="btn btn-dark">
                <Download size={16} strokeWidth={2.4} />
                Get it on Google Play
                <ArrowUpRight size={15} strokeWidth={2.4} />
              </a>
              <a
                href={DOWNLOAD_URL}
                target="_blank"
                rel="noreferrer"
                className="btn border border-ink/20 bg-transparent text-ink hover:border-ink/40 hover:bg-ink/5"
              >
                Source & Changelog
              </a>
            </div>

            <div className="mt-8 border-t border-ink/15 pt-5">
              <p className="text-[0.6875rem] font-bold text-ink/70">Install safely through Google Play</p>
              <p className="mt-2 max-w-md text-[0.8rem] leading-6 text-ink/70">
                Google Play handles app installation and updates. No APK sideloading or unknown-app permission needed.
              </p>
            </div>

            <div className="mt-7 flex flex-wrap gap-x-5 gap-y-2 text-[0.6875rem] font-semibold text-ink/60">
              <span className="inline-flex items-center gap-1.5">
                <Smartphone size={12} /> Android 8.0+
              </span>
              <span>Google Play</span>
              <span>GPL-3.0</span>
              <span className="inline-flex items-center gap-1.5">
                <ShieldCheck size={12} /> No trackers
              </span>
            </div>
          </div>

          <div className="relative mx-auto w-full max-w-[18rem] lg:max-w-[20rem]" data-parallax="-0.055">
            <div className="absolute -top-4 -left-4 z-20 rotate-[-8deg] rounded-full border border-ink/15 bg-paper px-3 py-1.5 text-[0.6875rem] font-bold text-ink shadow-lg">
              No top bar
            </div>
            <div className="absolute -right-3 bottom-10 z-20 rotate-[7deg] rounded-full bg-ink px-3 py-1.5 text-[0.6875rem] font-bold text-brand shadow-md">
              Zero telemetry
            </div>

            <figure className="overflow-hidden rounded-[1.75rem] shadow-md">
              <img
                src={homeImage}
                alt="Prosto browser home screen with its floating tab dock"
                width={1220}
                height={2712}
                loading="lazy"
                className="block h-auto w-full"
              />
            </figure>
          </div>
        </div>
      </div>
    </section>
  );
}
