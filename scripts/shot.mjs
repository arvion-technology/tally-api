#!/usr/bin/env node
// Screenshot the demo with headless Chromium.
// Usage: node scripts/shot.mjs [width] [height] [output]
import { spawn, spawnSync } from "node:child_process";
import { existsSync, mkdirSync } from "node:fs";

const width = Number(process.argv[2] || 1440);
const height = Number(process.argv[3] || 900);
const out = process.argv[4] || "docs/screenshot.png";
const hash = process.argv[5] || "";
const port = 4199;
mkdirSync("docs", { recursive: true });

const chromium = ["chromium", "chromium-browser", "google-chrome"].find((bin) =>
  spawnSync("which", [bin], { stdio: "ignore" }).status === 0);
if (!chromium) { console.error("chromium not found"); process.exit(1); }

const server = spawn(process.execPath, ["scripts/serve.mjs", ".", String(port)], { stdio: "ignore" });
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

await wait(600);
const args = [
  "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
  "--force-device-scale-factor=1", `--window-size=${width},${height}`,
  `--screenshot=${out}`, "--virtual-time-budget=6000",
  `--user-data-dir=/tmp/arvion-chrome-${Date.now()}`,
  `http://localhost:${port}/demo/index.html${hash}`,
];
const code = spawnSync(chromium, args, { stdio: "inherit" }).status;
server.kill();
if (code !== 0) { console.error("screenshot failed"); process.exit(1); }
if (!existsSync(out)) { console.error("no screenshot written"); process.exit(1); }
console.log("wrote " + out);
