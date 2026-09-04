import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";
import { once } from "node:events";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoDir = resolve(scriptDir, "../../..");
const chromePath = process.env.CHROME_PATH ?? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const baseUrl = process.env.DASHBOARD_URL ?? "http://127.0.0.1:3000/";
const debuggingPort = Number(process.env.CHROME_DEBUGGING_PORT ?? "9224");
const profileDir = await mkdtemp(join(tmpdir(), "digital-delta-dashboard-"));
const browser = spawn(chromePath, [
  "--headless=new",
  `--remote-debugging-port=${debuggingPort}`,
  `--user-data-dir=${profileDir}`,
  "--hide-scrollbars",
  "--no-first-run",
  "--no-default-browser-check",
  "--use-angle=swiftshader-webgl",
  "about:blank",
], { stdio: "ignore" });

try {
  await waitForBrowser();
  for (const viewport of [{ width: 1366, height: 768 }, { width: 1920, height: 1080 }]) {
    await capture(viewport, "bn");
    await capture(viewport, "en");
  }
} finally {
  browser.kill("SIGTERM");
  await Promise.race([once(browser, "exit"), wait(2_000)]);
  await rm(profileDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}

async function capture({ width, height }, language) {
  const page = await fetch(`http://127.0.0.1:${debuggingPort}/json/new?${encodeURIComponent(baseUrl)}`, { method: "PUT" }).then(assertJson);
  const cdp = connectCdp(page.webSocketDebuggerUrl);
  await cdp.open;
  await cdp.call("Page.enable");
  await cdp.call("Runtime.enable");
  await cdp.call("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile: false });
  await cdp.call("Page.navigate", { url: baseUrl });
  await wait(3_000);
  await cdp.call("Runtime.evaluate", { expression: "document.fonts.ready", awaitPromise: true, returnByValue: true });
  if (language === "en") {
    await cdp.call("Runtime.evaluate", {
      expression: `(() => { const button = document.querySelector("button.language"); if (!button) throw new Error("language button missing"); button.click(); })()`,
      awaitPromise: true,
    });
    await wait(300);
  }
  const result = await cdp.call("Page.captureScreenshot", { format: "png", fromSurface: true, captureBeyondViewport: false });
  const index = width === 1366 ? "01" : "02";
  const path = join(repoDir, `artifacts/screenshots/command-${language}/${index}-command-${language}-live-overview-${width}x${height}.png`);
  await writeFile(path, Buffer.from(result.data, "base64"));
  cdp.close();
  await fetch(`http://127.0.0.1:${debuggingPort}/json/close/${page.id}`);
  console.log(path);
}

function connectCdp(url) {
  const socket = new WebSocket(url);
  let nextId = 0;
  const pending = new Map();
  socket.addEventListener("message", ({ data }) => {
    const message = JSON.parse(data);
    if (!message.id) return;
    const request = pending.get(message.id);
    if (!request) return;
    pending.delete(message.id);
    if (message.error) request.reject(new Error(message.error.message));
    else request.resolve(message.result);
  });
  const open = new Promise((resolveOpen, rejectOpen) => {
    socket.addEventListener("open", resolveOpen, { once: true });
    socket.addEventListener("error", rejectOpen, { once: true });
  });
  return {
    open,
    call(method, params = {}) {
      const id = ++nextId;
      return new Promise((resolveCall, rejectCall) => {
        pending.set(id, { resolve: resolveCall, reject: rejectCall });
        socket.send(JSON.stringify({ id, method, params }));
      });
    },
    close() {
      socket.close();
    },
  };
}

async function waitForBrowser() {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${debuggingPort}/json/version`);
      if (response.ok) return;
    } catch {
      // Chrome is still starting.
    }
    await wait(100);
  }
  throw new Error("Chrome DevTools endpoint did not become ready");
}

async function assertJson(response) {
  if (!response.ok) throw new Error(`Chrome DevTools request failed: ${response.status}`);
  return response.json();
}

function wait(milliseconds) {
  return new Promise((resolveWait) => setTimeout(resolveWait, milliseconds));
}
