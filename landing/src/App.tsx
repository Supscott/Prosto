import { useSiteMotion } from './lib/useSiteMotion';
import { TICKER_ITEMS } from './lib/site';
import { ScrollProgress } from './components/ScrollProgress';
import { CursorGlow } from './components/CursorGlow';
import { SiteHeader } from './components/SiteHeader';
import { Hero } from './components/Hero';
import { Ticker } from './components/Ticker';
import { DockSection } from './components/DockSection';
import { SearchSection } from './components/SearchSection';
import { PrivacySection } from './components/PrivacySection';
import { BentoSection } from './components/BentoSection';
import { VideoSection } from './components/VideoSection';
import { DownloadSection } from './components/DownloadSection';
import { SiteFooter } from './components/SiteFooter';

export function App() {
  useSiteMotion();

  return (
    <div className="relative min-h-screen overflow-clip bg-ink text-paper">
      <a
        href="#main"
        className="sr-only z-[100] rounded-full bg-brand px-4 py-2 text-sm font-bold text-ink focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>

      <div className="grain" aria-hidden="true" />
      <ScrollProgress />
      <CursorGlow />
      <SiteHeader />

      <main id="main">
        <Hero />
        <Ticker items={TICKER_ITEMS} />
        <DockSection />
        <SearchSection />
        <PrivacySection />
        <BentoSection />
        <VideoSection />
        <DownloadSection />
      </main>

      <SiteFooter />
    </div>
  );
}
