import { describe, expect, it } from "vitest";

import { tryParse } from "../tale/identifier";
import { build } from "../tale/library";
import type { Vocabulary } from "../tale/vocabulary";
import bundled from "../vocabulary.json";
import { defaultCondition, defaultEffect, formatCondition, parseCondition, parseEffect, splitIf } from "./calls";
import { layoutNodes, moveLines, replaceContent } from "./lines";

const vocab = bundled as Vocabulary;

const SAMPLE = `=== gossip
start: chatter
weight: +3 if night
{pony}: Did you hear? // a comment
    or: Between you and me...
~ fondness +1
* Friendly: Oh?
    ~ trust +1
    -> bye
* [Kindness | Trust] Tell me more.
    success -> bye
    failure -> END
-> bye if night

=== bye
{pony}: See you!
-> END`;

describe("lines as blocks", () => {
  it("nests or-lines and reply children", () => {
    const [gossip, bye] = layoutNodes(SAMPLE);
    expect(gossip.hats.map((b) => b.kind)).toEqual(["start", "weight"]);
    expect(gossip.body.map((b) => b.kind)).toEqual(["spoken", "effect", "reply", "reply", "jump"]);
    expect(gossip.body[0].children.map((b) => b.kind)).toEqual(["or"]);
    expect(gossip.body[0].content).toBe("{pony}: Did you hear?");
    expect(gossip.body[2].children.map((b) => b.kind)).toEqual(["effect", "jump"]);
    expect(gossip.body[3].children.map((b) => b.kind)).toEqual(["branch", "branch"]);
    expect(gossip.body[3].last).toBe(12);
    expect(bye.body).toHaveLength(2);
  });

  it("keeps comments and indentation when a line is rewritten", () => {
    const lines = replaceContent(SAMPLE.split("\n"), 4, "{pony}: Guess what?");
    expect(lines[3]).toBe("{pony}: Guess what? // a comment");
    expect(replaceContent(SAMPLE.split("\n"), 8, "~ trust +2")[7]).toBe("    ~ trust +2");
  });

  it("moves a block with its children and re-indents it", () => {
    const lines = SAMPLE.split("\n");
    // Move the effect (line 6) into the friendly reply, after its "~ trust +1".
    const moved = moveLines(lines, 6, 6, 8, "    ");
    expect(moved.slice(5, 9)).toEqual(["* Friendly: Oh?", "    ~ trust +1", "    ~ fondness +1", "    -> bye"]);
    // Move the spoken line and its "or:" to the end of the node.
    const spoken = moveLines(lines, 4, 5, 13, "");
    expect(spoken.slice(11, 13)).toEqual(["{pony}: Did you hear? // a comment", "    or: Between you and me..."]);
    expect(moveLines(lines, 4, 5, 4, "")).toBe(lines); // into itself
  });
});

describe("calls", () => {
  it("round-trips conditions and effects", () => {
    for (const text of ["night", "not species(pegasus)", "trust >= medium", "mood(grief, strong)", "biome(#is_forest, swamp)"]) {
      expect(formatCondition(parseCondition(text))).toBe(text);
    }
    expect(parseEffect("memory cim:gift, joy clear").args).toEqual(["cim:gift", "joy clear"]);
    expect(splitIf("-> bye if night, species(unicorn)")).toEqual({ body: "-> bye", conditions: ["night", "species(unicorn)"] });
  });

  it("makes defaults the game accepts for every condition and effect", () => {
    const conditions = Object.keys(vocab.conditions).map((id) => `-> END if ${defaultCondition(vocab, id)}`);
    const effects = Object.keys(vocab.effects).map((id) => `~ ${defaultEffect(vocab, id)}`);
    const text = `=== all\n${[...conditions, ...effects].join("\n")}`;
    const problems = build([{ id: tryParse("test:all")!, source: "all.tale", text }], vocab).problems
      .filter((problem) => problem.severity === "error");
    expect(problems).toEqual([]);
  });
});
