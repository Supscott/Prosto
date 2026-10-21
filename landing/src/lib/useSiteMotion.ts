import { useEffect } from 'react';
import Lenis from 'lenis';
import gsap from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

gsap.registerPlugin(ScrollTrigger);

export function useSiteMotion() {
  useEffect(() => {
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (reducedMotion) {
      return;
    }

    const lenis = new Lenis({
      lerp: 0.085,
      wheelMultiplier: 0.95,
      touchMultiplier: 1.15,
      smoothWheel: true,
    });

    const updateScrollTrigger = () => ScrollTrigger.update();
    lenis.on('scroll', updateScrollTrigger);

    const tick = (time: number) => {
      lenis.raf(time * 1000);
    };

    gsap.ticker.add(tick);
    gsap.ticker.lagSmoothing(0);

    const onAnchorClick = (event: MouseEvent) => {
      const target = event.target as HTMLElement | null;
      const anchor = target?.closest<HTMLAnchorElement>('a[href^="#"]');
      if (!anchor) return;

      const href = anchor.getAttribute('href');
      if (!href || href === '#') return;

      const destination = document.querySelector<HTMLElement>(href);
      if (!destination) return;

      event.preventDefault();
      lenis.scrollTo(destination, { offset: -84, duration: 1.25 });
    };

    document.addEventListener('click', onAnchorClick);

    const context = gsap.context(() => {
      gsap.utils.toArray<HTMLElement>('[data-reveal]').forEach((element) => {
        const delay = Number(element.dataset.revealDelay ?? 0);
        gsap.from(element, {
          y: 34,
          opacity: 0,
          duration: 0.9,
          delay,
          ease: 'power3.out',
          scrollTrigger: {
            trigger: element,
            start: 'top 90%',
            once: true,
          },
        });
      });

      gsap.utils.toArray<HTMLElement>('[data-parallax]').forEach((element) => {
        const distance = Number(element.dataset.parallax ?? 0.12);
        const section = element.closest('section') ?? element;
        gsap.to(element, {
          yPercent: distance * 100,
          ease: 'none',
          scrollTrigger: {
            trigger: section,
            start: 'top bottom',
            end: 'bottom top',
            scrub: 0.5,
          },
        });
      });

      gsap.utils.toArray<HTMLElement>('[data-scale-in]').forEach((element) => {
        gsap.from(element, {
          scale: 0.94,
          opacity: 0,
          duration: 1.1,
          ease: 'power3.out',
          scrollTrigger: {
            trigger: element,
            start: 'top 88%',
            once: true,
          },
        });
      });
    });

    const refresh = () => ScrollTrigger.refresh();

    if (document.fonts?.ready) {
      void document.fonts.ready.then(refresh);
    }
    window.addEventListener('load', refresh, { once: true });

    return () => {
      document.removeEventListener('click', onAnchorClick);
      window.removeEventListener('load', refresh);
      gsap.ticker.remove(tick);
      context.revert();
      lenis.destroy();
    };
  }, []);
}
