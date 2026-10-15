// The script seen as blocks: every line inside a node is one block, with "or:" lines nested under
// their spoken line and a reply's effects and jumps nested inside it. Blocks only ever change the
// script by editing these lines, so the script stays the single source of truth.
//
// Classification mirrors the parser (tale/parser.ts) rule for rule.

export type BlockKind =
  | "start" | "weight" | "when" | "tone"
  | "spoken" | "or" | "interjection" | "effect" | "jump" | "reply" | "branch" | "unknown";

export interface Block {
  kind: BlockKind;
  /** 1-based line number. */
  line: number;
  indent: string;
  /** The line without its indentation or trailing comment. */
  content: string;
  children: Block[];
  /** The last line this block covers, children included. */
  last: number;
}

export interface NodeLayout {
  name: string;
  header: number;
  /** start:, weight:, when: and tone: lines - they must sit right under the header. */
  hats: Block[];
  body: Block[];
}

export const SPEAKER_LINE = /^(\{[A-Za-z0-9_.]+\}|[A-Za-z][A-Za-z0-9_' -]*):\s*(.*)$/;
export const INTERJECTION = /^\(\s*([A-Za-z]+)\s*(?::\s*([A-Za-z]+)\s*)?\)\s*(.*)$/;
export const BRANCH = /^(success|failure)\s*->\s*(\S+)$/;
export const CHILD_INDENT = "    ";

export function splitComment(raw: string): { code: string; comment: string } {
  const at = raw.indexOf("//");
  return at < 0 ? { code: raw, comment: "" } : { code: raw.substring(0, at), comment: raw.substring(at) };
}

function classify(content: string): BlockKind {
  if (content.startsWith("~")) return "effect";
  if (content.startsWith("->")) return "jump";
  if (INTERJECTION.test(content)) return "interjection";
  if (SPEAKER_LINE.test(content)) return "spoken";
  return "unknown";
}

/** Every conversation node in {@code text}, as blocks. Threads are skipped. */
export function layoutNodes(text: string): NodeLayout[] {
  const nodes: NodeLayout[] = [];
  let node: NodeLayout | null = null;
  let reply: Block | null = null;
  const lines = text.split("\n");
  lines.forEach((raw, i) => {
    const n = i + 1;
    const { code } = splitComment(raw);
    const content = code.trim();
    if (content.length === 0) return;
    const indent = code.substring(0, code.length - code.trimStart().length);
    const indented = /\s/.test(code.charAt(0));
    if (content.startsWith("===")) {
      const rest = content.substring(3).trim();
      node = rest === "thread" || rest.startsWith("thread ") ? null : { name: rest, header: n, hats: [], body: [] };
      if (node) nodes.push(node);
      reply = null;
      return;
    }
    if (!node) return;
    const block = (kind: BlockKind): Block => ({ kind, line: n, indent, content, children: [], last: n });
    if (/^(when|start|weight|tone):/.test(content)) {
      node.hats.push(block(content.substring(0, content.indexOf(":")) as BlockKind));
      return;
    }
    if (content.startsWith("*")) {
      reply = block("reply");
      node.body.push(reply);
      return;
    }
    if (indented && reply) {
      const child = block(BRANCH.test(content) ? "branch" : classify(content));
      reply.children.push(child);
      reply.last = n;
      return;
    }
    reply = null;
    const previous = node.body[node.body.length - 1];
    if (content.startsWith("or:") && previous?.kind === "spoken") {
      previous.children.push(block("or"));
      previous.last = n;
      return;
    }
    node.body.push(block(content.startsWith("or:") ? "unknown" : classify(content)));
  });
  return nodes;
}

/** The line a new body block goes after when dropped at the very top of the node. */
export function bodyStart(node: NodeLayout): number {
  return Math.max(node.header, ...node.hats.map((hat) => hat.last));
}

/** Every block in the node, nested ones included. */
export function allBlocks(node: NodeLayout): Block[] {
  const out: Block[] = [];
  const walk = (blocks: Block[]) => blocks.forEach((block) => {
    out.push(block);
    walk(block.children);
  });
  walk(node.hats);
  walk(node.body);
  return out;
}

// ---- Text edits (pure: lines in, lines out) ----

/** Inserts {@code added} after 1-based line {@code after} (0 = top of file), indented by {@code indent}. */
export function insertLines(lines: string[], after: number, added: string[], indent: string): string[] {
  return [...lines.slice(0, after), ...added.map((line) => indent + line), ...lines.slice(after)];
}

/** Replaces one line's content, keeping its indentation and trailing comment. */
export function replaceContent(lines: string[], line: number, content: string): string[] {
  const raw = lines[line - 1];
  const { code, comment } = splitComment(raw);
  const indent = code.substring(0, code.length - code.trimStart().length);
  const next = [...lines];
  next[line - 1] = indent + content + (comment ? ` ${comment}` : "");
  return next;
}

export function removeLines(lines: string[], from: number, to: number): string[] {
  return [...lines.slice(0, from - 1), ...lines.slice(to)];
}

/**
 * Moves lines {@code from}..{@code to} to just after line {@code after}, re-indenting the first line
 * to {@code indent} (nested lines keep their extra indentation). Moving a block into itself does nothing.
 */
export function moveLines(lines: string[], from: number, to: number, after: number, indent: string): string[] {
  if (after >= from - 1 && after <= to) {
    return lines;
  }
  const moved = lines.slice(from - 1, to);
  const base = moved[0].length - moved[0].trimStart().length;
  const reindented = moved.map((line) => {
    if (line.trim().length === 0) return line;
    const own = line.length - line.trimStart().length;
    return indent + " ".repeat(Math.max(0, own - base)) + line.trimStart();
  });
  const without = removeLines(lines, from, to);
  const at = after > to ? after - moved.length : after;
  return insertLines(without, at, reindented, "");
}
