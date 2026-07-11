import { cpSync, mkdirSync, rmSync, writeFileSync } from "node:fs";

rmSync("dist", { recursive: true, force: true });
mkdirSync("dist/server", { recursive: true });
cpSync("backend/contentlens-api/src/server.js", "dist/server/app.js");
writeFileSync(
  "dist/server/index.js",
  [
    'import { createServer } from "./app.js";',
    'const port = Number.parseInt(process.env.PORT || "8787", 10);',
    'createServer().listen(port, () => {',
    '  console.log(`ContentLens API listening on :${port}`);',
    '});',
    ""
  ].join("\n")
);
mkdirSync("dist/.openai", { recursive: true });
cpSync(".openai/hosting.json", "dist/.openai/hosting.json");
writeFileSync("dist/package.json", JSON.stringify({ type: "module" }, null, 2));
