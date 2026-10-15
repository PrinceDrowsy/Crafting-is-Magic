// The editor's local helper: a small file API served alongside the editor, so it can open and save
// .tale files on disk in any browser (Firefox has no folder access for web pages). It only ever
// touches .tale files inside the folders it was given.

import { promises as fs } from "node:fs";
import path from "node:path";
import type { IncomingMessage, ServerResponse } from "node:http";
import type { Plugin } from "vite";

export interface Root {
  /** Shown in the editor: "Mod tales", "Dev world datapacks". */
  label: string;
  dir: string;
}

async function walk(dir: string, prefix: string, out: string[]): Promise<void> {
  let entries;
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const entry of entries) {
    const rel = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      if (entry.name !== "node_modules" && !entry.name.startsWith(".")) {
        await walk(path.join(dir, entry.name), rel, out);
      }
    } else if (entry.name.endsWith(".tale")) {
      out.push(rel);
    }
  }
}

/** Resolves a requested file inside a root, refusing anything outside it or not a .tale file. */
function safePath(roots: Root[], rootIndex: number, rel: string): string | null {
  const root = roots[rootIndex];
  if (!root || !rel.endsWith(".tale")) {
    return null;
  }
  const full = path.resolve(root.dir, rel);
  const base = path.resolve(root.dir) + path.sep;
  return full.startsWith(base) ? full : null;
}

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    let body = "";
    req.setEncoding("utf8");
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => resolve(body));
    req.on("error", reject);
  });
}

function send(res: ServerResponse, status: number, data: unknown): void {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.end(JSON.stringify(data));
}

export function filesPlugin(roots: Root[]): Plugin {
  return {
    name: "tale-files",
    configureServer(server) {
      server.middlewares.use("/api", async (req, res) => {
        try {
          const url = new URL(req.url ?? "/", "http://localhost");
          if (req.method === "GET" && url.pathname === "/files") {
            const result = [];
            for (let i = 0; i < roots.length; i++) {
              const files: string[] = [];
              await walk(roots[i].dir, "", files);
              result.push({ label: roots[i].label, dir: roots[i].dir, files: files.sort() });
            }
            return send(res, 200, result);
          }
          if (url.pathname === "/file") {
            const full = safePath(roots, Number(url.searchParams.get("root")), url.searchParams.get("path") ?? "");
            if (full === null) {
              return send(res, 400, { error: "Only .tale files inside the editor's folders" });
            }
            if (req.method === "GET") {
              return send(res, 200, { text: await fs.readFile(full, "utf8") });
            }
            if (req.method === "PUT") {
              const { text } = JSON.parse(await readBody(req)) as { text: string };
              await fs.mkdir(path.dirname(full), { recursive: true });
              await fs.writeFile(full, text, "utf8");
              return send(res, 200, { ok: true });
            }
          }
          if (req.method === "GET" && url.pathname === "/vocabulary") {
            for (const candidate of vocabularyPaths) {
              try {
                return send(res, 200, JSON.parse(await fs.readFile(candidate, "utf8")));
              } catch {
                // try the next place
              }
            }
            return send(res, 404, { error: "No cim-vocabulary.json yet - open a world in the dev client to export one" });
          }
          send(res, 404, { error: "Unknown request" });
        } catch (error) {
          send(res, 500, { error: String(error) });
        }
      });
    },
  };
}

/** Where the game writes its vocabulary export (DebugCommand#exportVocabulary). */
let vocabularyPaths: string[] = [];

export function setVocabularyPaths(paths: string[]): void {
  vocabularyPaths = paths;
}
