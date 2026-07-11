import { cpSync, existsSync, mkdirSync, rmSync } from "node:fs";

rmSync("dist", { recursive: true, force: true });
mkdirSync("dist", { recursive: true });
cpSync(".next/standalone", "dist", { recursive: true });
mkdirSync("dist/.next", { recursive: true });
cpSync(".next/static", "dist/.next/static", { recursive: true });
if (existsSync("public")) {
  cpSync("public", "dist/public", { recursive: true });
}
