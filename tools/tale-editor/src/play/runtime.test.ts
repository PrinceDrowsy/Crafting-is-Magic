// The play-test runtime against the mod's real .tale files: it should play them the way the game does.

import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

import { idForPath } from "../files";
import { tryParse } from "../tale/identifier";
import { build } from "../tale/library";
import type { Vocabulary } from "../tale/vocabulary";
import { Conversation, pickOpener } from "./runtime";
import { type Happening, type Sim } from "./sim";
import { defaultState } from "./state";

const vocab = JSON.parse(readFileSync(path.join(__dirname, "../vocabulary.json"), "utf8")) as Vocabulary;
const root = path.resolve(__dirname, "../../../../cim-1.21.1/src/main/resources");

function taleFiles(dir: string, prefix = ""): string[] {
  if (!existsSync(dir)) return [];
  return readdirSync(dir).flatMap((name) => {
    const full = path.join(dir, name);
    const rel = prefix ? `${prefix}/${name}` : name;
    return statSync(full).isDirectory() ? taleFiles(full, rel) : name.endsWith(".tale") ? [rel] : [];
  });
}

function library() {
  const sources = taleFiles(root).map((rel) => ({ id: idForPath(rel), source: rel, text: readFileSync(path.join(root, rel), "utf8") }));
  return build(sources, vocab).nodes;
}

function sim(happened: Happening[] = []): Sim {
  return { state: defaultState(), vocab, unknown: new Set(), happened: (entry) => happened.push(entry) };
}

describe("play-test runtime", () => {
  const nodes = library();

  it("opens chatter with a greeting, then a topic with replies", () => {
    const s = sim();
    s.state.relationship.lastTalked = "today";
    const opener = pickOpener("chatter", nodes, s)!;
    const greeting = pickOpener("greeting", nodes, s, opener.tone);
    const conversation = new Conversation(nodes, s, opener, greeting);
    expect(conversation.beat.lines.length).toBeGreaterThan(1);
    expect(conversation.beat.lines[0].partner).toBe(true);
    expect(conversation.beat.ended || conversation.beat.options.length > 0).toBe(true);
  });

  it("takes a gift, and the feelings move", () => {
    const happened: Happening[] = [];
    const s = sim(happened);
    s.state.gift.favorite = true;
    const node = pickOpener("gift", nodes, s)!;
    new Conversation(nodes, s, node, null);
    expect(s.state.relationship.fondness).toBe(20);
    expect(s.state.relationship.trust).toBe(3);
    expect(happened.some((h) => h.text.includes("favorite"))).toBe(true);
  });

  it("says no to a gift past today's limit", () => {
    const s = sim();
    s.state.gift.canTake = false;
    const node = pickOpener("gift", nodes, s)!;
    expect(node.name).toBe("too_many_today");
    new Conversation(nodes, s, node, null);
    expect(s.state.relationship.fondness).toBe(0);
  });

  it("follows forced dice to the success branch", () => {
    const happened: Happening[] = [];
    const s = sim(happened);
    s.state.dice = "success";
    const text = `=== ask
{pony}: Well?
* [Kindness | Trust] It's alright.
    success -> yes
    failure -> no

=== yes
{pony}: Thank you.

=== no
{pony}: ...No.`;
    const own = build([{ id: tryParse("test:dice")!, source: "dice.tale", text }], vocab).nodes;
    const conversation = new Conversation(own, s, own.get("test:dice/ask")!, null);
    expect(conversation.beat.options[0].voice).toBe("kindness");
    const after = conversation.choose(0);
    expect(after.lines[0].kind).toBe("success");
    expect(after.lines[1].text).toBe("Thank you.");
    expect(happened.some((h) => h.kind === "roll" && h.text.includes("forced"))).toBe(true);
  });
});
