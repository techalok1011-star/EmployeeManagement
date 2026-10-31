// Turns Vite's built index.html into the Thymeleaf template Spring serves for /admin/collection-map.
import { readFileSync, writeFileSync, rmSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(here, "../../src/main/resources/static/js/map-app");
const built = resolve(out, "index.html");
const template = resolve(here, "../../src/main/resources/templates/admin/collection-map.html");

if (!existsSync(built)) { console.error("index.html not found in", out); process.exit(1); }
let html = readFileSync(built, "utf8");
// Thymeleaf treats [[...]] / [(...)] in inline scripts as expressions; make sure none slipped in.
if (/\[\[|\[\(/.test(html)) { console.error("Template contains a Thymeleaf inline-expression marker"); process.exit(1); }
writeFileSync(template, html, "utf8");
rmSync(built);
console.log("template written:", template);
