import { cloudflareTest } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [cloudflareTest({ wrangler: { configPath: "./wrangler.jsonc" }, miniflare: {
    bindings: { PUBLISHER_KEYS: JSON.stringify({ N4: "test-only-publisher-token-not-a-deployed-secret" }) },
  } })],
});
