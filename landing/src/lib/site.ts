export type SearchEngine = {
  id: string;
  name: string;
  mark: string;
  color: string;
  url: string;
  custom?: boolean;
};

export const DOWNLOAD_URL = 'https://github.com/Supscott/Prosto/releases';
export const PLAY_STORE_URL = 'https://play.google.com/store/apps/details?id=com.jestk.browser';
export const REPO_URL = 'https://github.com/Supscott/Prosto';
export const LICENSE_URL = 'https://github.com/Supscott/Prosto/blob/main/LICENSE';

export const BUILT_IN_ENGINES: SearchEngine[] = [
  { id: 'google', name: 'Google', mark: 'G', color: 'var(--color-search-google)', url: 'https://www.google.com/search?q=%s' },
  { id: 'duckduckgo', name: 'DuckDuckGo', mark: 'D', color: 'var(--color-search-duckduckgo)', url: 'https://duckduckgo.com/?q=%s' },
  { id: 'brave', name: 'Brave', mark: 'B', color: 'var(--color-search-brave)', url: 'https://search.brave.com/search?q=%s' },
  { id: 'kagi', name: 'Kagi', mark: 'K', color: 'var(--color-search-kagi)', url: 'https://kagi.com/search?q=%s' },
  { id: 'ecosia', name: 'Ecosia', mark: 'E', color: 'var(--color-search-ecosia)', url: 'https://www.ecosia.org/search?q=%s' },
  { id: 'startpage', name: 'Startpage', mark: 'S', color: 'var(--color-search-startpage)', url: 'https://www.startpage.com/sp/search?query=%s' },
];

export const BLOCKED_REQUESTS = [
  'doubleclick.net / pagead',
  'facebook.com / tr',
  'googletagmanager.com',
  'scorecardresearch.com',
  'hotjar.com / cdn',
  'criteo.net / deliver',
];

export const TICKER_ITEMS = [
  'Floating tab dock',
  'Swipe-up search',
  'Custom engines',
  'EasyList shield',
  'No telemetry',
  '4 MB APK',
  'Open source',
  'Android 8+',
];
