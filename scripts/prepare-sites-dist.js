import { cpSync, existsSync, mkdirSync, rmSync } from "node:fs";

rmSync("dist", { recursive: true, force: true });
mkdirSync("dist/server", { recursive: true });
cpSync(".next/standalone", "dist/server", { recursive: true });
cpSync("dist/server/server.js", "dist/server/index.js");
mkdirSync("dist/server/.next", { recursive: true });
cpSync(".next/static", "dist/server/.next/static", { recursive: true });
mkdirSync("dist/.openai", { recursive: true });
cpSync(".openai/hosting.json", "dist/.openai/hosting.json");
if (existsSync("public")) {
  cpSync("public", "dist/server/public", { recursive: true });
}
