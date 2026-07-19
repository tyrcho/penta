// Renders the #art element (a <svg id="art" viewBox="0 0 400 400"> by convention — either
// a standalone .svg file or one embedded in an .html wrapper both work, Chromium displays
// raw SVG files directly) to a transparent PNG using headless Chromium. This project has no
// image-gen tool and no local npm install of Playwright, but Chromium and a global playwright
// module are preinstalled — see "Render the SVG" in ../SKILL.md for the exact invocation
// (requires NODE_PATH=/opt/node22/lib/node_modules).
//
// Usage: node render.js <input.svg|input.html> <output.png> [sizePx=800]
const { chromium } = require('playwright');
const path = require('path');

async function main() {
  const htmlPath = process.argv[2];
  const outPath = process.argv[3];
  const size = parseInt(process.argv[4] || '800', 10);

  if (!htmlPath || !outPath) {
    console.error('Usage: node render.js <input.html> <output.png> [sizePx=800]');
    process.exit(1);
  }

  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const page = await browser.newPage({ viewport: { width: size, height: size }, deviceScaleFactor: 1 });
  await page.goto('file://' + path.resolve(htmlPath));
  const el = await page.$('#art');
  if (!el) {
    console.error('No element with id="art" found in ' + htmlPath);
    process.exit(1);
  }
  await el.screenshot({ path: outPath, omitBackground: true });
  await browser.close();
}

main().catch((e) => { console.error(e); process.exit(1); });
