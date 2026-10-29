export type SearchEngine = {
  id: string;
  name: string;
  mark: string;
  color: string;
  url: string;
  custom?: boolean;
};

export const DOWNLOAD_URL = 'https://github.com/Supscott/browser/releases';
export const DIRECT_APK_URL = 'https://github.com/Supscott/browser/releases/latest/download/app-release.apk';
export const REPO_URL = 'https://github.com/Supscott/browser';
export const LICENSE_URL = 'https://github.com/Supscott/browser/blob/main/LICENSE';

export const BUILT_IN_ENGINES: SearchEngine[] = [
  { id: 'google', name: 'Google', mark: 'G', color: '#f6c945', url: 'https://www.google.com/search?q=%s' },
  { id: 'duckduckgo', name: 'DuckDuckGo', mark: 'D', color: '#de5833', url: 'https://duckduckgo.com/?q=%s' },
  { id: 'brave', name: 'Brave', mark: 'B', color: '#fb542b', url: 'https://search.brave.com/search?q=%s' },
  { id: 'kagi', name: 'Kagi', mark: 'K', color: '#ffb319', url: 'https://kagi.com/search?q=%s' },
  { id: 'ecosia', name: 'Ecosia', mark: 'E', color: '#55b878', url: 'https://www.ecosia.org/search?q=%s' },
  { id: 'startpage', name: 'Startpage', mark: 'S', color: '#7dd3fc', url: 'https://www.startpage.com/sp/search?query=%s' },
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
  'FLOATING TAB DOCK',
  'SWIPE-UP SEARCH',
  'CUSTOM ENGINES',
  'EASYLIST SHIELD',
  'ZERO TELEMETRY',
  '4 MB APK',
  'OPEN SOURCE',
  'ANDROID 8+',
];
