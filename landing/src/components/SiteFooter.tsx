import { ArrowUpRight } from 'lucide-react';
import { DOWNLOAD_URL, LICENSE_URL, REPO_URL } from '../lib/site';
import { Logo } from './Logo';

const FOOTER_LINKS = [
  { label: 'Releases', href: DOWNLOAD_URL },
  { label: 'Source', href: REPO_URL },
  { label: 'License', href: LICENSE_URL },
  { label: 'Issues', href: `${REPO_URL}/issues` },
];

export function SiteFooter() {
  return (
    <footer className="relative overflow-hidden border-t border-line pt-14 pb-8 sm:pt-20">
      <div className="shell">
        <div className="grid gap-10 md:grid-cols-[1.4fr_1fr]">
          <div>
            <Logo />
            <p className="mt-5 max-w-sm text-sm leading-6 text-muted">
              A calm, open-source Android browser built for the way thumbs actually move. No top bars. No noise. Just the web.
            </p>
            <a
              href={`${REPO_URL}/stargazers`}
              target="_blank"
              rel="noreferrer"
              className="mt-6 inline-flex items-center gap-1.5 font-mono text-[0.62rem] font-semibold tracking-[0.12em] text-paper uppercase"
            >
              Star the project <ArrowUpRight size={14} />
            </a>
          </div>

          <div className="grid grid-cols-2 gap-8 sm:grid-cols-4 md:grid-cols-2">
            <div>
              <p className="eyebrow">Explore</p>
              <ul className="mt-4 space-y-2.5">
                {FOOTER_LINKS.slice(0, 2).map((link) => (
                  <li key={link.href}>
                    <a href={link.href} className="text-sm text-muted transition-colors hover:text-paper">
                      {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <p className="eyebrow">Project</p>
              <ul className="mt-4 space-y-2.5">
                {FOOTER_LINKS.slice(2).map((link) => (
                  <li key={link.href}>
                    <a href={link.href} className="text-sm text-muted transition-colors hover:text-paper">
                      {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>

        <div className="mt-16 overflow-hidden sm:mt-24">
          <p className="display text-outline whitespace-nowrap text-[clamp(5rem,20vw,17rem)] leading-[0.72]">
            PROSTO
          </p>
        </div>

        <div className="mt-8 flex flex-col gap-3 border-t border-line pt-6 text-[0.65rem] text-muted sm:flex-row sm:items-center sm:justify-between">
          <p>© {new Date().getFullYear()} Prosto. Independent and open source.</p>
          <p className="font-mono tracking-[0.1em] uppercase">Designed for one hand · Built with care</p>
        </div>
      </div>
    </footer>
  );
}
