import { useEffect, useRef, useState } from 'react';
import { ArrowUpRight, Download, Menu, X } from 'lucide-react';
import { DIRECT_APK_URL, REPO_URL } from '../lib/site';
import { Logo } from './Logo';

const NAV_LINKS = [
  { label: 'The dock', href: '#dock' },
  { label: 'Search', href: '#search' },
  { label: 'Privacy', href: '#privacy' },
  { label: 'Specs', href: '#specs' },
];

export function SiteHeader() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (menuOpen && !dialog.open) {
      dialog.showModal();
    } else if (!menuOpen && dialog.open) {
      dialog.close();
    }
  }, [menuOpen]);

  // Fallback for browsers without native closedby support
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (!('closedBy' in HTMLDialogElement.prototype)) {
      const outside = (e: PointerEvent, d: HTMLDialogElement) => {
        const r = d.getBoundingClientRect();
        return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
      };
      let pressedOutside = false;
      const onPointerDown = (e: PointerEvent) => {
        pressedOutside = outside(e, dialog);
      };
      const onPointerUp = (e: PointerEvent) => {
        if (pressedOutside && outside(e, dialog)) {
          dialog.close();
        }
      };
      dialog.addEventListener('pointerdown', onPointerDown);
      dialog.addEventListener('pointerup', onPointerUp);
      return () => {
        dialog.removeEventListener('pointerdown', onPointerDown);
        dialog.removeEventListener('pointerup', onPointerUp);
      };
    }
  }, []);

  return (
    <>
      <header className="fixed inset-x-0 top-0 z-[75] pt-2.5 sm:pt-3.5">
        <div className="shell">
          <div
            className="nav-shell flex items-center justify-between rounded-full py-2 pl-2 pr-2 sm:pl-3 sm:pr-2.5"
            data-scrolled={scrolled}
          >
            <a href="#top" className="flex shrink-0 items-center" onClick={() => setMenuOpen(false)}>
              <Logo />
            </a>

            <nav className="hidden items-center md:flex" aria-label="Main navigation">
              {NAV_LINKS.map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  className="rounded-full px-3 py-2 text-[0.7rem] font-bold text-muted transition-colors hover:bg-white/5 hover:text-paper"
                >
                  {link.label}
                </a>
              ))}
            </nav>

            <div className="flex items-center gap-1.5">
              <span className="hidden sm:block">
                <a
                  href={DIRECT_APK_URL}
                  className="btn btn-primary min-h-10 px-4 text-[0.68rem]"
                >
                  <Download size={14} strokeWidth={2.4} />
                  Get Prosto (APK)
                </a>
              </span>
              <a
                href={DIRECT_APK_URL}
                className="grid size-10 place-items-center rounded-full bg-brand text-ink sm:hidden"
                aria-label="Download the Prosto APK"
              >
                <Download size={16} strokeWidth={2.4} />
              </a>
              <button
                type="button"
                className="grid size-10 place-items-center rounded-full border border-line bg-white/[0.03] text-paper md:hidden"
                onClick={() => setMenuOpen((open) => !open)}
                aria-label={menuOpen ? 'Close menu' : 'Open menu'}
                aria-expanded={menuOpen}
              >
                {menuOpen ? <X size={18} /> : <Menu size={18} />}
              </button>
            </div>
          </div>
        </div>
      </header>

      <dialog
        ref={dialogRef}
        {...({ closedby: 'any' } as any)}
        onClose={() => setMenuOpen(false)}
        className="nav-menu md:hidden"
        aria-label="Navigation"
      >
        <div className="flex items-center justify-between pb-6">
          <Logo />
          <button
            type="button"
            className="grid size-10 place-items-center rounded-full border border-line bg-white/[0.03] text-paper"
            onClick={() => setMenuOpen(false)}
            aria-label="Close menu"
          >
            <X size={18} />
          </button>
        </div>

        <nav className="flex flex-col">
          {NAV_LINKS.map((link, index) => (
            <a key={link.href} href={link.href} onClick={() => setMenuOpen(false)}>
              <span className="accent not-italic">0{index + 1}</span>
              {link.label}
            </a>
          ))}
        </nav>

        <div className="mt-auto">
          <a
            href={DIRECT_APK_URL}
            className="btn btn-primary w-full"
            onClick={() => setMenuOpen(false)}
          >
            <Download size={16} />
            Download APK (4 MB)
          </a>
          <div className="mt-4 flex items-center justify-between">
            <span className="status-line">Open source · GPL-3.0</span>
            <a
              href={REPO_URL}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 text-[0.7rem] font-bold text-muted"
            >
              GitHub <ArrowUpRight size={13} />
            </a>
          </div>
        </div>
      </dialog>
    </>
  );
}
