import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  optimizeDeps: {
    // MapLibre v6 resolves its ESM worker beside the package entry. Vite's
    // dependency pre-bundler otherwise separates that worker from its sibling.
    exclude: ["maplibre-gl"],
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
  },
});
