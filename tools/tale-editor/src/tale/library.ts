// Parses and validates a set of .tale files together - a port of TaleLibrary.build, including which
// nodes and threads are left out of the game for having errors.

import type { Node, TaleFile, Thread } from "./ast";
import { type Identifier, idString } from "./identifier";
import { parse } from "./parser";
import type { Problem } from "./problem";
import { validate } from "./validator";
import type { Vocabulary } from "./vocabulary";

export interface SourceFile {
  id: Identifier;
  source: string;
  text: string;
}

export interface Snapshot {
  files: TaleFile[];
  nodes: Map<string, Node>;
  threads: Map<string, Thread>;
  problems: Problem[];
  /** Header lines of nodes/threads left out of the game for having errors, by source. */
  leftOut: Map<string, Set<number>>;
}

export function build(sources: SourceFile[], vocab: Vocabulary): Snapshot {
  const problems: Problem[] = [];
  const files = sources.map((file) => parse(file.id, file.source, file.text, problems));
  validate(files, vocab, problems);

  const nodes = new Map<string, Node>();
  const threads = new Map<string, Thread>();
  const leftOut = new Map<string, Set<number>>();
  for (const file of files) {
    const broken = brokenHeaders(file, problems);
    leftOut.set(file.source, broken);
    for (const node of file.nodes) {
      if (!broken.has(node.line) && !nodes.has(idString(node.id))) {
        nodes.set(idString(node.id), node);
      }
    }
    for (const thread of file.threads) {
      if (!broken.has(thread.line) && !threads.has(idString(thread.id))) {
        threads.set(idString(thread.id), thread);
      }
    }
  }
  problems.sort((a, b) => (a.source < b.source ? -1 : a.source > b.source ? 1 : a.line - b.line));
  return { files, nodes, threads, problems, leftOut };
}

/** The "===" lines of every node and thread in {@code file} that an error falls inside. */
function brokenHeaders(file: TaleFile, problems: Problem[]): Set<number> {
  const headers = [...file.nodes.map((node) => node.line), ...file.threads.map((thread) => thread.line)].sort((a, b) => a - b);
  const broken = new Set<number>();
  for (const problem of problems) {
    if (problem.severity !== "error" || problem.source !== file.source) {
      continue;
    }
    let owner: number | null = null;
    for (const header of headers) {
      if (header <= problem.line) {
        owner = header;
      } else {
        break;
      }
    }
    if (owner !== null) {
      broken.add(owner);
    }
  }
  return broken;
}
