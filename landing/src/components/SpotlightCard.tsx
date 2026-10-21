import type { PointerEvent, ReactNode } from 'react';
import { useRef } from 'react';

type SpotlightCardProps = {
  children: ReactNode;
  className?: string;
};

export function SpotlightCard({ children, className = '' }: SpotlightCardProps) {
  const cardRef = useRef<HTMLDivElement>(null);

  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const card = cardRef.current;
    if (!card || event.pointerType !== 'mouse') return;

    const bounds = card.getBoundingClientRect();
    card.style.setProperty('--mx', `${event.clientX - bounds.left}px`);
    card.style.setProperty('--my', `${event.clientY - bounds.top}px`);
  };

  return (
    <div ref={cardRef} className={`spotlight-card ${className}`} onPointerMove={onPointerMove}>
      {children}
    </div>
  );
}
