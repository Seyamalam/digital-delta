// Creates only machine-local publisher credentials, never prints them, and never
// overwrites existing secrets. The drill uses a source-bound token, not browser auth.
import { randomBytes } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";

const root = new URL("../", import.meta.url);
const secretFile = new URL("services/headquarters-archive/.dev.vars", root);
const mode = process.argv[2] ?? "setup";
try {
  await writeFile(secretFile, `PUBLISHER_KEYS='${JSON.stringify({ "simulator-local-drill": randomBytes(32).toString("hex") })}'\n`, { flag: "wx", mode: 0o600 });
  console.log("Created local observer publisher credentials in ignored .dev.vars (not displayed).");
} catch (error) { if (error.code !== "EEXIST") throw error; }
if (mode === "seed") {
  const contents = await readFile(secretFile, "utf8");
  const value = contents.match(/^PUBLISHER_KEYS='(.*)'$/m)?.[1];
  if (!value) throw new Error("Local .dev.vars must define PUBLISHER_KEYS as a single-quoted JSON object.");
  const token = JSON.parse(value)["simulator-local-drill"];
  if (typeof token !== "string" || token.length < 32) throw new Error("Enroll simulator-local-drill before seeding.");
  const child = spawn("go", ["run", "./cmd/delta-drill", "--seed", process.argv[3] ?? "fair-pass-01", "--interval", "120ms"], {
    cwd: fileURLToPath(new URL("services/node/", root)), stdio: "inherit", env: { ...process.env, DELTA_OBSERVER_PUBLISHER_TOKEN: token },
  });
  child.on("error", (error) => { console.error(error.message); process.exitCode = 1; });
  child.on("exit", (code) => { process.exitCode = code ?? 1; });
} else if (mode !== "setup") throw new Error("Use setup or seed.");
