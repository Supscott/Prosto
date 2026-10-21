import { useEffect, useState } from 'react';
import { useInView } from '../lib/useInView';

type AnimatedNumberProps = {
  value: number;
  decimals?: number;
  suffix?: string;
  duration?: number;
};

export function AnimatedNumber({ value, decimals = 0, suffix = '', duration = 1400 }: AnimatedNumberProps) {
  const { ref, inView } = useInView<HTMLSpanElement>(0.5);
  const reducedMotion =
    typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const [display, setDisplay] = useState(reducedMotion ? value : 0);

  useEffect(() => {
    if (reducedMotion) {
      setDisplay(value);
      return;
    }

    if (!inView) return;

    let frame = 0;
    const start = performance.now();

    const animate = (now: number) => {
      const progress = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - progress, 3);
      setDisplay(value * eased);

      if (progress < 1) {
        frame = window.requestAnimationFrame(animate);
      }
    };

    frame = window.requestAnimationFrame(animate);
    return () => window.cancelAnimationFrame(frame);
  }, [inView, value, duration, reducedMotion]);

  return (
    <span ref={ref}>
      {display.toFixed(decimals)}
      {suffix}
    </span>
  );
}
