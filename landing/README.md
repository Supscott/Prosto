# Prosto landing page

Mobile-first marketing site for Prosto, the thumb-first Android browser.

## Stack

- Vite + React 19 + TypeScript
- Tailwind CSS v4
- React Three Fiber / drei for the procedural 3D browser scene
- GSAP ScrollTrigger for parallax and reveals
- Lenis for smooth scrolling

## Brand

The site follows the app's design tokens (`new-design.md`):

- **Canvas** `#0E1513` (deep teal-black) with `#101614` panels and `#DEE4E2` text.
- **Primary** `#56DBCF` (jade) for actions and accents, `#A5F2E9` (mint) and `#7FD8CB` (teal) as secondary accents.
- **Fonts**: Manrope for titles (`--font-display`), Inter for body copy (`--font-sans`), JetBrains Mono for labels (`--font-mono`).
- **Logo**: the app icon from `app/src/main/assets/icon/app-icon.svg` (mint tile + teal swirl), also served as `public/favicon.svg`.

Tokens live in `src/styles/global.css` under `@theme`; the `brand` / `mint` / `teal` names replace the previous `acid` / `aqua` / `lilac` ones.

## Development

```bash
npm install
npm run dev
```

## Production

```bash
npm run build
npm run preview
```

The static build is written to `dist/`.

## Smoke test

Build and serve the production output first:

```bash
npm run build
npm run preview -- --host 127.0.0.1 --port 4173
```

Then run the browser smoke test in another terminal:

```bash
npm run smoke
```

The script uses installed Google Chrome by default. Set `PLAYWRIGHT_CHANNEL=chromium` after `npx playwright install chromium` to use Playwright's bundled browser instead, or set `SMOKE_URL` to test another origin.

## Demo interactions

The dock section is a working in-page demo:

- Drag the dock upward to open quick search.
- Tap tabs to switch the simulated page.
- Swipe or scroll the engine rail sideways.
- Add a custom search engine with a URL containing `%s`.

Custom engines are stored in `localStorage` under `prosto.custom-engines`.

## Media placeholders

All screenshots and recordings are intentionally represented by labeled gray placeholders. Replace them with real 9:16 or 16:9 media when available.

## Hero 3D scene content

The hero scene uses the real home screen capture `src/assets/home.png` (1220×2712). The phone display shows the full capture; the cards floating around it show crops of it (recently closed tabs, blocked-ads stats, a favorite tile). Crop rectangles live in `src/components/BrowserScene.tsx` and are cut at runtime with a canvas, so no extra image files are needed.
