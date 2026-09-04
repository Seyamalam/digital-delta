import { copyFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const projectDirectory = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const sourceDirectory = resolve(projectDirectory, "node_modules/maplibre-gl/dist");
const outputDirectory = resolve(projectDirectory, "public/vendor/maplibre");

await mkdir(outputDirectory, { recursive: true });
await Promise.all([
  "maplibre-gl-worker.mjs",
  "maplibre-gl-shared.mjs",
].map((file) => copyFile(resolve(sourceDirectory, file), resolve(outputDirectory, file))));
