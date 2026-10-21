type LogoProps = {
  compact?: boolean;
};

/**
 * Brand mark: the Prosto app icon (mint tile + teal swirl) from
 * app/src/main/assets/icon/app-icon.svg.
 */
export function Logo({ compact = false }: LogoProps) {
  return (
    <span className="inline-flex items-center gap-2.5" aria-label="Prosto home">
      <span className="logo-mark" aria-hidden="true">
        <svg viewBox="0 0 80 80" fill="none">
          <rect width="80" height="80" rx="20" fill="#E9F6F2" />
          <path
            d="M59.6874 25.125C51.3749 15.9375 36.2812 18.125 26.6562 26.2188C16.1562 34.9688 13.7499 47.2188 19.6562 55.3125C25.7812 63.8438 39.1249 63.1875 49.6249 56.4063C55.0937 52.9063 59.6874 47.6563 62.3124 41.9688L53.3437 37.5938C51.1562 41.9688 47.8749 45.6875 43.9374 48.3125C37.3749 52.6875 30.5937 53.125 27.9687 49.625C25.1249 45.9063 27.7499 39.5625 33.4374 34.75C39.5624 29.5 47.8749 27.9688 52.4687 32.5625L59.6874 25.125Z"
            fill="#087E78"
          />
        </svg>
      </span>
      {!compact && (
        <span className="text-[0.95rem] font-extrabold tracking-[0.18em] text-paper">
          PROSTO
        </span>
      )}
    </span>
  );
}
