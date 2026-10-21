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
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
    deviceScaleFactor: 1,
  });
  const page = await context.newPage();
  await page.addInitScript(() => {
    if (window.sessionStorage.getItem('prosto-smoke-initialized')) return;
    window.localStorage.removeItem('prosto.custom-engines');
    window.sessionStorage.setItem('prosto-smoke-initialized', 'true');
  });
  await page.goto(baseUrl, { waitUntil: 'networkidle' });
  await page.evaluate(() => document.querySelector('.device-shell').scrollIntoView({ block: 'center' }));
  await page.waitForTimeout(1500);

  const dock = await page.locator('.device-dock-wrap').boundingBox();
  if (!dock) throw new Error('Dock demo is missing.');

  await page.mouse.move(dock.x + dock.width / 2, dock.y + dock.height - 12);
  await page.mouse.down();
  await page.mouse.move(dock.x + dock.width / 2, dock.y + dock.height - 105, { steps: 10 });
  await page.mouse.up();
  await page.waitForTimeout(700);
  check('drag opens quick search', (await page.locator('.device-search-layer').getAttribute('data-open')) === 'true');

  await page.getByLabel('Close quick search').click();
  await page.waitForTimeout(600);
  check('close quick search', (await page.locator('.device-search-layer').getAttribute('data-open')) === 'false');

  await page.getByLabel('Open Recipe tab').click();
  await page.waitForTimeout(300);
  check('tab switches', (await page.getByLabel('Open Recipe tab').getAttribute('data-active')) === 'true');
  check('tab content updates', (await page.locator('.device-page-title').innerText()).includes('Miso'));

  await page.locator('.device-swipe-hint').click();
  await page.waitForTimeout(600);
  await page.locator('.device-engine-add').click();
  await page.getByLabel('Custom engine name').fill('Smoke Engine');
  await page.getByLabel('Custom engine search URL').fill('https://example.com/?q=%s');
  await page.getByRole('button', { name: 'Add engine' }).click();
  await page.waitForTimeout(900);
  check('custom engine becomes active', (await page.locator('input[aria-label="Search with Smoke Engine"]').count()) === 1);

  await page.locator('input[aria-label="Search with Smoke Engine"]').fill('calm software');
  await page.locator('button[aria-label="Search"]').click();
  await page.waitForTimeout(400);
  check('search results render', (await page.locator('.device-results').innerText()).includes('calm software'));
  check('search uses custom engine', (await page.locator('.device-results').innerText()).includes('Smoke Engine'));

  await page.reload({ waitUntil: 'networkidle' });
  await page.evaluate(() => document.querySelector('.device-shell').scrollIntoView({ block: 'center' }));
  await page.waitForTimeout(1500);
  await page.locator('.device-swipe-hint').click();
  await page.waitForTimeout(600);
  check('custom engine persists', (await page.locator('.device-engine-option-name', { hasText: 'Smoke Engine' }).count()) === 1);
  await context.close();

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
    lenis: document.documentElement.classList.contains('lenis'),
    overflow: document.documentElement.scrollWidth - window.innerWidth,
    footprint: document.querySelector('#specs p[class*="text-[clamp(4rem"]')?.textContent?.trim(),
  }));
  check('reduced motion skips smooth scrolling', reduced.lenis === false);
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
