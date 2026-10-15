// Parity check: the editor must report exactly what the game reports. Reads every .tale file the
// dev client loads (the mod's own + the dev world's datapacks), validates them with the editor's
// port, and compares against the problems the game logged on its last load (runclient_out.log).
// Skipped when there's no log to compare with.

import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

import { idForPath } from "../files";
import { build } from "./library";
import type { Vocabulary } from "./vocabulary";

const repo = path.resolve(__dirname, "../../../..");
const log = path.join(repo, "runclient_out.log");
const vocabulary = JSON.parse(readFileSync(path.join(__dirname, "../vocabulary.json"), "utf8")) as Vocabulary;

function taleFiles(dir: string, prefix = ""): string[] {
  if (!existsSync(dir)) {
    return [];
  }
  return readdirSync(dir).flatMap((name) => {
    const full = path.join(dir, name);
    const rel = prefix ? `${prefix}/${name}` : name;
    return statSync(full).isDirectory() ? taleFiles(full, rel) : name.endsWith(".tale") ? [rel] : [];
  });
}

describe("parity with the game", () => {
  it.skipIf(!existsSync(log))("reports the same problems as the game's last load", () => {
    const roots = [
      path.join(repo, "cim-1.21.1/src/main/resources"),
      path.join(repo, "cim-1.21.1/run/saves/New World/datapacks/cim_tale_test"),
    ];
    const sources = roots.flatMap((root) => taleFiles(root).map((rel) => {
      const tales = rel.split("/").indexOf("tales");
      // The game names a source "data/<ns>/tales/<file> (<pack>)"; compare on the path part only.
      return { id: idForPath(rel), source: rel.split("/").slice(tales - 2).join("/"), text: readFileSync(path.join(root, rel), "utf8") };
    }));
    const ours = build(sources, vocabulary).problems.map((p) => `${p.source}:${p.line}: ${p.severity}: ${p.message}`).sort();

    // The game's lines: "(cim/narrative) data/x/tales/y.tale (pack):12: error: message"
    const lines = readFileSync(log, "utf8").split(/\r?\n/);
    const loadIndex = lines.map((line, i) => (line.includes("(cim/narrative) Loaded") ? i : -1)).filter((i) => i >= 0).pop() ?? -1;
    const theirs = [];
    for (let i = loadIndex + 1; i < lines.length; i++) {
      const match = /\(cim\/narrative\) (data\/\S+) \([^)]*\):(\d+): (error|warning): (.*)$/.exec(lines[i]);
      if (!match) {
        if (theirs.length > 0) break; // the problem lines are one block right after the summary
        continue;
      }
      theirs.push(`${match[1]}:${match[2]}: ${match[3]}: ${match[4]}`);
    }
    expect(ours).toEqual(theirs.sort());
  });
});
