// Renders the launcher icon layers from the SVGs: node branding/render-icons.js (from the repo root).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const res = path.join(root, 'app/src/main/res');
const densities = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  async function shot(svgFile, px, out) {
    const svg = fs.readFileSync(path.join(__dirname, svgFile), 'utf8');
    await page.setViewportSize({ width: px, height: px });
    await page.setContent(`<html><body style="margin:0;background:transparent">${svg.replace(/width="512" height="512"/, `width="${px}" height="${px}"`)}</body></html>`);
    fs.mkdirSync(path.dirname(out), { recursive: true });
    await page.screenshot({ path: out, omitBackground: true, clip: { x: 0, y: 0, width: px, height: px } });
  }
  for (const [d, k] of Object.entries(densities)) {
    const px = Math.round(108 * k);
    await shot('layer-background.svg', px, `${res}/mipmap-${d}/ic_launcher_background.png`);
    await shot('layer-foreground.svg', px, `${res}/mipmap-${d}/ic_launcher_foreground.png`);
  }
  await shot('portix.svg', 512, path.join(__dirname, 'portix-512.png'));
  await browser.close();
})();
