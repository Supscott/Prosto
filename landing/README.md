# Prosto Browser — Official Landing Page

This is the official landing page for **Prosto**, the thumb-first open-source Android web browser.

Built with [Astro](https://astro.build) and [Tailwind CSS v4](https://tailwindcss.com).

## ⚡ Features
- **Hero Device Simulator**: Interactive simulated mobile device showcasing 1-swipe new tab gesture, engine carousel, prefix shortcuts (`g`, `d`, `b`), and ad-block shield statistics.
- **Ergonomics Section**: Visual reachability comparison demonstrating natural thumb arcs on 6.7"+ mobile displays.
- **Bento Grid**: Visual breakdown of core capabilities (Dock, Omnisearch, Shortcuts, Shield, Serene Canvas).
- **Engineering Architecture**: Kotlin Compose, Room v8, Managed WebView pooling, resumable downloads.
- **Direct GitHub Releases APK Download**: Quick installation links for Android 8.0+.

## 🛠️ Development

```bash
cd landing
pnpm install
pnpm dev
```

Visit [http://localhost:4321](http://localhost:4321) in your browser.

## 📦 Production Build

```bash
pnpm build
```

Generates a static site inside `./dist/`, ready for zero-config deployment to GitHub Pages, Cloudflare Pages, or Vercel.
