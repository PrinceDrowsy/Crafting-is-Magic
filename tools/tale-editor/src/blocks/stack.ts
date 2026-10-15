// A node drawn as a stack of blocks, Scratch style: its start/weight/when "hats" on top, then one
// block per line, replies as C-shaped blocks holding their effects and jumps. Every blank is a slot,
// every block has a grip to drag it by, and thin gaps between blocks take drops. All changes are
// line edits on the script.

import type { Vocabulary } from "../tale/vocabulary";
import { voiceTakesFeeling } from "../tale/vocabulary";
import { type Call, formatCondition, formatEffect, joinIf, parseCondition, parseEffect, specFor, splitIf } from "./calls";
import { type Category, type Fits, capitalize } from "./catalog";
import type { DragController } from "./drag";
import { BRANCH, type Block, CHILD_INDENT, INTERJECTION, type NodeLayout, SPEAKER_LINE, bodyStart, removeLines, replaceContent } from "./lines";
import { callSlots, choice, text } from "./slots";

export interface StackContext {
  vocab: Vocabulary;
  /** Node names a jump can go to. */
  targets: string[];
  lines: string[];
  edit(change: (lines: string[]) => string[]): void;
  select(line: number): void;
  drag: DragController;
}

const CATEGORY_OF: Record<Block["kind"], Category | "unknown"> = {
  spoken: "talk", or: "talk", interjection: "talk",
  reply: "choices", branch: "choices",
  jump: "flow", effect: "effects",
  start: "starting", weight: "starting", when: "starting", tone: "starting",
  unknown: "unknown",
};

/** The hats and body of {@code node}, ready to go under its header. */
export function renderStack(node: NodeLayout, ctx: StackContext): HTMLElement[] {
  const hats = document.createElement("div");
  hats.className = "stack hats";
  hats.append(gap(node.header, "", ["hat"]));
  node.hats.forEach((hat) => hats.append(blockRow(hat, ctx), gap(hat.last, "", ["hat"])));

  const body = document.createElement("div");
  body.className = "stack body";
  body.append(gap(bodyStart(node), "", ["body"]));
  node.body.forEach((block) => body.append(blockRow(block, ctx), gap(block.last, "", block.kind === "spoken" ? ["body", "or"] : ["body"])));
  if (node.body.length === 0) {
    body.insertAdjacentHTML("beforeend", `<div class="stack-empty">drop blocks here</div>`);
  }
  return [hats, body];
}

function gap(after: number, indent: string, fits: Fits[]): HTMLElement {
  const element = document.createElement("div");
  element.className = "gap";
  element.dataset.after = String(after);
  element.dataset.indent = indent;
  element.dataset.fits = fits.join(" ");
  return element;
}

function word(textContent: string, className = "blk-word"): HTMLElement {
  const element = document.createElement("span");
  element.className = className;
  element.textContent = textContent;
  return element;
}

function blockRow(block: Block, ctx: StackContext): HTMLElement {
  const row = document.createElement("div");
  row.className = `blk cat-${CATEGORY_OF[block.kind]} kind-${block.kind}`;
  row.dataset.line = String(block.line);
  row.dataset.kind = block.kind;
  const line = document.createElement("div");
  line.className = "blk-line";
  row.append(line);

  if (block.kind !== "branch") {
    const grip = word("⠿", "grip");
    grip.draggable = true;
    grip.title = "Drag to move · drop on the palette to delete";
    grip.ondragstart = (event) => {
      event.stopPropagation();
      ctx.drag.start(event, { type: "block", block }, ctx.lines.slice(block.line - 1, block.last).join("\n"), row);
    };
    line.append(grip);
  }
  const parts = document.createElement("div");
  parts.className = "blk-parts";
  parts.append(...contents(block, ctx, row));
  line.append(parts);
  if (block.kind !== "branch") {
    const remove = document.createElement("button");
    remove.className = "blk-remove";
    remove.textContent = "×";
    remove.title = "Delete this block";
    remove.onclick = (event) => {
      event.stopPropagation();
      ctx.edit((lines) => removeLines(lines, block.line, block.last));
    };
    line.append(remove);
  }
  line.onclick = (event) => {
    if (!(event.target as HTMLElement).closest(".slot, button")) ctx.select(block.line);
  };

  if (block.kind === "reply") {
    const voice = block.content.substring(1).trim().startsWith("[");
    const fits: Fits[] = [voice ? "voice-child" : "child"];
    const indent = block.indent + CHILD_INDENT;
    const mouth = document.createElement("div");
    mouth.className = "mouth";
    mouth.append(gap(block.line, indent, fits));
    block.children.forEach((child) => mouth.append(blockRow(child, ctx), gap(child.last, indent, fits)));
    row.append(mouth);
    row.dataset.fits = "once";
  } else if (block.kind === "spoken" && block.children.length > 0) {
    const alternatives = document.createElement("div");
    alternatives.className = "alternatives";
    block.children.forEach((child) => alternatives.append(blockRow(child, ctx)));
    row.append(alternatives);
  }
  return row;
}

/** The words and slots of one block. */
function contents(block: Block, ctx: StackContext, row: HTMLElement): (HTMLElement | string)[] {
  const vocab = ctx.vocab;
  const write = (content: string) => ctx.edit((lines) => replaceContent(lines, block.line, content));
  const content = block.content;
  const target = (current: string, onChange: (value: string) => void) =>
    choice(["END", ...ctx.targets], current, onChange, { labels: (value) => (value === "END" ? "★ END" : value), title: "where to go" });

  switch (block.kind) {
    case "spoken": {
      const [, speaker, said] = SPEAKER_LINE.exec(content)!;
      return [
        speaker === "{pony}" ? word("pony", "blk-word strong")
          : text(speaker, (next) => write(`${next}: ${said}`), { placeholder: "speaker", className: "slot-speaker" }),
        word("says"),
        text(said, (next) => write(`${speaker}: ${next}`), { placeholder: "what they say", className: "slot-wide" }),
      ];
    }
    case "or":
      return [word("or"), text(content.substring(3).trim(), (next) => write(`or: ${next}`), { className: "slot-wide" })];
    case "interjection": {
      const [, voice, tier, said] = INTERJECTION.exec(content)!;
      const format = (v: string, t: string, s: string) => `(${v}${t && t !== "faint" ? `: ${t}` : ""}) ${s}`;
      return [
        choice(vocab.voices.map(capitalize), capitalize(voice), (v) => write(format(v, tier ?? "", said)), { title: "voice" }),
        choice(vocab.tiers, (tier ?? "faint").toLowerCase(), (t) => write(format(voice, t, said)), { title: "how strong it has to be" }),
        text(said, (s) => write(format(voice, tier ?? "", s)), { className: "slot-wide" }),
      ];
    }
    case "effect": {
      row.dataset.fits = "condition";
      const { body, conditions } = splitIf(content.substring(1).trim());
      const call = parseEffect(body);
      const rewrite = (c: Call, conds: string[]) => write(`~ ${joinIf(formatEffect(c), conds)}`);
      return [
        word(call.name, "blk-word strong"),
        ...callSlots(specFor(vocab.effects, call.name), call, (next) => rewrite(next, conditions)),
        ...conditionArea(conditions, ctx, (conds) => rewrite(call, conds)),
      ];
    }
    case "jump": {
      row.dataset.fits = "condition";
      const { body, conditions } = splitIf(content.substring(2).trim());
      return [
        word(body === "END" ? "end" : "go to"),
        target(body, (next) => write(`-> ${joinIf(next, conditions)}`)),
        ...conditionArea(conditions, ctx, (conds) => write(`-> ${joinIf(body, conds)}`)),
      ];
    }
    case "reply":
      return replyContents(block, ctx, write);
    case "branch": {
      const [, which, to] = BRANCH.exec(content)!;
      return [
        word(which === "success" ? "✔ success" : "✘ failure", `blk-word ${which}`),
        target(to, (next) => write(`${which} -> ${next}`)),
      ];
    }
    case "start": {
      const contexts = content.substring(6).split(",").map((c) => c.trim()).filter(Boolean);
      return [word("starts in"), ...contexts.map((context, i) => choice(vocab.startContexts, context, (next) => {
        const updated = [...contexts];
        updated[i] = next;
        write(`start: ${updated.join(", ")}`);
      }, { title: "when the game can open this conversation" }))];
    }
    case "weight": {
      const rest = content.substring(7).trim();
      const bonus = /^\+(\d+)\s+if\s+(.+)$/.exec(rest);
      if (!bonus) {
        return [word("weight"), text(rest, (next) => write(`weight: ${next.replace(/\D/g, "") || "1"}`), { className: "slot-number" }),
          word("(how often it's picked)", "blk-note")];
      }
      row.dataset.fits = "condition";
      const conditions = splitIf(`x if ${bonus[2]}`).conditions;
      return [word("+"), text(bonus[1], (next) => write(`weight: +${next.replace(/\D/g, "") || "1"} if ${conditions.join(", ")}`), { className: "slot-number" }),
        word("more likely"), ...conditionArea(conditions, ctx, (conds) =>
          conds.length > 0 ? write(`weight: +${bonus[1]} if ${conds.join(", ")}`) : ctx.edit((lines) => removeLines(lines, block.line, block.line)))];
    }
    case "tone":
      return [word("feels"), text(content.substring(5).trim(), (next) => write(`tone: ${next.toLowerCase().replace(/[^a-z0-9_]+/g, "_")}`),
        { placeholder: "gentle", title: "Greetings only open conversations of the same tone" }), word("(greetings match it)", "blk-note")];
    case "when": {
      row.dataset.fits = "condition";
      const conditions = splitIf(`x if ${content.substring(5).trim()}`).conditions;
      return [word("only when"), ...conditionArea(conditions, ctx, (conds) =>
        conds.length > 0 ? write(`when: ${conds.join(", ")}`) : ctx.edit((lines) => removeLines(lines, block.line, block.line)), false)];
    }
    default:
      return [word("?", "blk-word unknown"), text(content, (next) => write(next), { className: "slot-wide", title: "A line the editor can't read yet" })];
  }
}

function replyContents(block: Block, ctx: StackContext, write: (content: string) => void): (HTMLElement | string)[] {
  const vocab = ctx.vocab;
  let header = block.content.substring(1).trim();
  const once = header.endsWith("!once");
  if (once) header = header.substring(0, header.length - 5).trim();
  const onceTag = (isOnce: boolean) => (isOnce ? " !once" : "");
  const parts: HTMLElement[] = [];

  if (header.startsWith("[") && header.includes("]")) {
    const close = header.indexOf("]");
    const [voicePart, feelingPart] = header.substring(1, close).split("|");
    const [voiceName, tierName] = voicePart.split(":").map((s) => s.trim());
    const feeling = feelingPart?.trim() ?? "";
    const said = header.substring(close + 1).trim();
    const format = (v: string, t: string, f: string, s: string, o: boolean) =>
      `* [${v}${t ? `: ${t}` : ""}${f ? ` | ${f}` : ""}] ${s}${onceTag(o)}`;
    parts.push(
      word("try", "blk-word strong"),
      choice(vocab.voices.filter((v) => v !== "whisper" || voiceName.toLowerCase() === "whisper").map(capitalize), capitalize(voiceName),
        (v) => write(format(v, tierName ?? "", voiceTakesFeeling(vocab, v) ? feeling : "", said, once)), { title: "voice" }),
      choice(vocab.tiers, (tierName ?? "").toLowerCase(), (t) => write(format(voiceName, t, feeling, said, once)),
        { optional: true, title: "how strong the voice has to be to offer this" }),
    );
    if (voiceTakesFeeling(vocab, voiceName)) {
      parts.push(word("·"), choice(vocab.feelings.map(capitalize), feeling ? capitalize(feeling) : "", (f) => write(format(voiceName, tierName ?? "", f, said, once)),
        { optional: true, title: "the feeling it builds on success" }));
    }
    parts.push(text(said, (s) => write(format(voiceName, tierName ?? "", feeling, s, once)), { className: "slot-wide", placeholder: "what you try" }));
    if (once) parts.push(onceBadge(() => write(format(voiceName, tierName ?? "", feeling, said, false))));
    return parts;
  }
  const match = /^([A-Za-z]+):\s*(.*)$/.exec(header);
  const tone = match?.[1] ?? "";
  const said = match?.[2] ?? header;
  const format = (t: string, s: string, o: boolean) => `* ${t}: ${s}${onceTag(o)}`;
  parts.push(
    word("reply", "blk-word strong"),
    choice(vocab.tones.map(capitalize), capitalize(tone), (t) => write(format(t, said, once)), { title: "tone" }),
    text(said, (s) => write(format(tone, s, once)), { className: "slot-wide", placeholder: "what you say" }),
  );
  if (once) parts.push(onceBadge(() => write(format(tone, said, false))));
  return parts;
}

function onceBadge(remove: () => void): HTMLElement {
  const badge = document.createElement("button");
  badge.className = "once-badge";
  badge.textContent = "once ×";
  badge.title = "Only offered once - click to offer it every time";
  badge.onclick = (event) => {
    event.stopPropagation();
    remove();
  };
  return badge;
}

/** The "if ..." chips after a jump or effect (or the body of a when:/weight bonus). */
function conditionArea(conditions: string[], ctx: StackContext, onChange: (conditions: string[]) => void, showIf = true): HTMLElement[] {
  if (conditions.length === 0) return [];
  const parts: HTMLElement[] = showIf ? [word("if")] : [];
  conditions.forEach((condition, i) => {
    const call = parseCondition(condition);
    const replace = (next: string | null) => {
      const updated = [...conditions];
      if (next === null) updated.splice(i, 1);
      else updated[i] = next;
      onChange(updated);
    };
    const chip = document.createElement("span");
    chip.className = "cond";
    const not = document.createElement("button");
    not.className = `cond-not${call.negated ? " on" : ""}`;
    not.textContent = "not";
    not.title = call.negated ? "Click to drop the \"not\"" : "Click to turn this around";
    not.onclick = (event) => {
      event.stopPropagation();
      replace(formatCondition({ ...call, negated: !call.negated }));
    };
    const remove = document.createElement("button");
    remove.className = "cond-remove";
    remove.textContent = "×";
    remove.title = "Remove this condition";
    remove.onclick = (event) => {
      event.stopPropagation();
      replace(null);
    };
    chip.append(not, word(call.name, "cond-name"),
      ...callSlots(specFor(ctx.vocab.conditions, call.name), call, (next) => replace(formatCondition(next))), remove);
    parts.push(chip);
  });
  return parts;
}

/** Adds a condition to a jump, effect, weight bonus or when: line. */
export function addCondition(lines: string[], line: number, condition: string): string[] {
  const raw = lines[line - 1];
  const content = raw.replace(/\/\/.*$/, "").trim();
  if (content.startsWith("when:") || /^weight:\s*\+\d+\s+if\s/.test(content)) {
    return replaceContent(lines, line, `${content}, ${condition}`);
  }
  const { body, conditions } = splitIf(content);
  return replaceContent(lines, line, joinIf(body, [...conditions, condition]));
}

/** Marks a reply as offered only once. */
export function makeOnce(lines: string[], line: number): string[] {
  const content = lines[line - 1].replace(/\/\/.*$/, "").trim();
  return content.endsWith("!once") ? lines : replaceContent(lines, line, `${content} !once`);
}
