/**
 * Website screenshot script using Playwright headless browser.
 * Called by ScreenshotService.java via ProcessBuilder.
 * Usage: node screenshot.mjs <url> <width> <height> [output.png]
 * Outputs base64-encoded PNG to stdout, or saves to file if path provided.
 * Uses createRequire to support NODE_PATH for global playwright installs.
 */
import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');

const url = process.argv[2];
const width = parseInt(process.argv[3]) || 1280;
const height = parseInt(process.argv[4]) || 800;
const outputFile = process.argv[5] || null;

if (!url) {
  console.error('Usage: node screenshot.mjs <url> <width> <height> [output.png]');
  process.exit(1);
}

let browser;
try {
  browser = await chromium.launch({
    headless: true,
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH || process.env.CHROMIUM_PATH || undefined,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu']
  });

  const context = await browser.newContext({
    viewport: { width, height },
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
  });

  const page = await context.newPage();

  await page.goto(url, {
    waitUntil: 'networkidle',
    timeout: 25000
  });

  await page.waitForTimeout(1500);

  if (outputFile) {
    await page.screenshot({ path: outputFile, fullPage: false, type: 'png' });
    console.log('OK:' + outputFile);
  } else {
    const buffer = await page.screenshot({ fullPage: false, type: 'png' });
    process.stdout.write(buffer);
  }

  await browser.close();
  process.exit(0);
} catch (err) {
  if (browser) {
    try { await browser.close(); } catch (_) {}
  }
  console.error('SCREENSHOT_ERROR:' + err.message);
  process.exit(1);
}
