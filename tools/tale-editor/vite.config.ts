import path from "node:path";
import { defineConfig } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";

import { filesPlugin, setVocabularyPaths } from "./server/filesPlugin.ts";

// The editor runs through Vite's dev server (start-editor.cmd), with a small local file API so it
// can open and save .tale files in any browser. Extra folders can be added with TALE_DIRS
// (separated by ";"). `npm run build` still produces one self-contained HTML file as a fallback.
const repo = path.resolve(import.meta.dirname, "../..");
const mod = path.join(repo, "cim-1.21.1");

const roots = [
  { label: "Mod tales", dir: path.join(mod, "src/main/resources") },
  { label: "Dev world datapacks", dir: path.join(mod, "run/saves") },
  ...(process.env.TALE_DIRS ?? "").split(";").filter(Boolean).map((dir) => ({ label: path.basename(dir), dir })),
];
setVocabularyPaths([path.join(mod, "run/cim-vocabulary.json")]);

export default defineConfig({
  plugins: [filesPlugin(roots), viteSingleFile()],
  server: { open: true, port: 5180 },
  build: { target: "es2022" },
});
