import { cpSync, mkdirSync, rmSync, writeFileSync } from "node:fs";

rmSync("dist", { recursive: true, force: true });
mkdirSync("dist/server", { recursive: true });
cpSync("backend/contentlens-api/src/server.js", "dist/server/index.js");
mkdirSync("dist/.openai", { recursive: true });
cpSync(".openai/hosting.json", "dist/.openai/hosting.json");
writeFileSync("dist/package.json", JSON.stringify({ type: "module" }, null, 2));
