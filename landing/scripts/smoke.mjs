import { chromium } from 'playwright';

const baseUrl = process.env.SMOKE_URL ?? 'http://127.0.0.1:4173/';
const channel = process.env.PLAYWRIGHT_CHANNEL ?? 'chrome';
let failures = 0;

function check(name, condition, detail = '') {
  const status = condition ? 'PASS' : 'FAIL';
  console.log(`${status} ${name}${detail ? ` — ${detail}` : ''}`);
  if (!condition) failures += 1;
}

async function waitForPreview() {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    try {
      const response = await fetch(baseUrl);
      if (response.ok) return;
    } catch {
      // Preview server is still starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Preview server is not reachable at ${baseUrl}. Run "npm run preview -- --host 127.0.0.1 --port 4173" first.`);
}

await waitForPreview();
const browser = await chromium.launch({ channel });

try {
  // 1. Mobile viewport test (390x844 iPhone 14)
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
    deviceScaleFactor: 2,
  });
  const page = await context.newPage();
  await page.goto(baseUrl, { waitUntil: 'networkidle' });

  // Hero phone fully visible check on load
  const phoneImg = await page.locator('#top img[alt*="Prosto Browser"]').boundingBox();
  if (!phoneImg) throw new Error('Hero phone image is missing.');
  check('hero phone starts below header', phoneImg.y >= 50, `y=${phoneImg.y}`);
  check('hero phone bottom visible without scrolling', phoneImg.y + phoneImg.height <= 850, `bottom=${phoneImg.y + phoneImg.height}`);

  // Mobile menu dialog test
  await page.locator('button[aria-label="Open menu"]').click();
  await page.waitForTimeout(300);
  const isMenuOpen = await page.evaluate(() => {
    const d = document.querySelector('dialog.nav-menu');
    return d ? d.open : false;
  });
  check('mobile dialog opens', isMenuOpen);

  await page.locator('dialog.nav-menu button[aria-label="Close menu"]').click();
  await page.waitForTimeout(300);
  const isMenuClosed = await page.evaluate(() => {
    const d = document.querySelector('dialog.nav-menu');
    return d ? !d.open : true;
  });
  check('mobile dialog closes on button', isMenuClosed);

  // Dock section: Gesture demo
  await page.evaluate(() => document.querySelector('#dock')?.scrollIntoView({ behavior: 'instant' }));
  await page.waitForTimeout(500);
  const gestureImgCount = await page.locator('#dock img[alt*="Home screen"]').count();
  check('gesture animation demo renders', gestureImgCount === 1);

  // Direct APK links
  const apkLinks = await page.locator('a[href*="/releases/latest/download/prosto.apk"]').count();
  check('direct apk download links present', apkLinks >= 2, `count=${apkLinks}`);

  await context.close();

  // 2. Desktop viewport test (1440x900)
  const desktopContext = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
  });
  const desktopPage = await desktopContext.newPage();
  await desktopPage.goto(baseUrl, { waitUntil: 'networkidle' });

  const desktopPhone = await desktopPage.locator('#top img[alt*="Prosto Browser"]').boundingBox();
  if (!desktopPhone) throw new Error('Desktop hero phone is missing.');
  check('desktop hero phone completely fits in viewport', desktopPhone.y + desktopPhone.height <= 900, `bottom=${desktopPhone.y + desktopPhone.height}`);

  const qrInHero = await desktopPage.locator('#top img[alt="QR Code"]').count();
  check('desktop QR badge visible in hero', qrInHero === 1);

  await desktopContext.close();

  // 3. Reduced motion & overflow check
  const reducedContext = await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    reducedMotion: 'reduce',
    deviceScaleFactor: 1,
  });
  const reducedPage = await reducedContext.newPage();
  await reducedPage.goto(baseUrl, { waitUntil: 'networkidle' });
  await reducedPage.waitForTimeout(400);

  const reduced = await reducedPage.evaluate(() => ({
    overflow: document.documentElement.scrollWidth - window.innerWidth,
    footprint: document.querySelector('#specs p[class*="text-[clamp(4rem"]')?.textContent?.trim(),
  }));
  check('reduced motion shows final footprint', reduced.footprint?.includes('4.0') === true, reduced.footprint ?? 'missing');
  check('no horizontal overflow', reduced.overflow === 0, String(reduced.overflow));
  await reducedContext.close();
} finally {
  await browser.close();
}

if (failures > 0) {
  console.error(`\n${failures} smoke check(s) failed.`);
  process.exitCode = 1;
} else {
  console.log('\nAll smoke checks passed.');
}
