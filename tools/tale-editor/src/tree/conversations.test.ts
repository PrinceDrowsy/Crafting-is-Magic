import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

import { tryParse } from "../tale/identifier";
import { parse } from "../tale/parser";
import { analyze } from "./conversations";

function file(text: string) {
  return parse(tryParse("test:sample")!, "sample.tale", text, []);
}

describe("conversations", () => {
  it("gives each starting point its own conversation and pulls out what they share", () => {
    const structure = analyze(file(`
=== weather
start: idle_chatter
{pony}: "Nice day."
-> chat

=== gossip
start: idle_chatter
{pony}: "Did you hear?"
-> chat

=== chat
* Friendly:
    -> bye

=== bye
{pony}: "See you!"
-> END
`));
    expect(structure.conversations.map((c) => c.root.name)).toEqual(["weather", "gossip"]);
    expect([...structure.conversations[0].members]).toEqual(["weather", "chat", "bye"]);
    expect(structure.pieces).toHaveLength(1);
    expect(structure.pieces[0].head.name).toBe("chat");
    expect([...structure.pieces[0].members].sort()).toEqual(["bye", "chat"]);
    expect(structure.pieces[0].usedBy).toEqual(["weather", "gossip"]);
  });

  it("links conversations that jump into each other instead of merging them", () => {
    const structure = analyze(file(`
=== first
start: idle_chatter
-> second

=== second
start: idle_chatter
{pony}: "Hi."
`));
    expect(structure.conversations).toHaveLength(2);
    expect([...structure.conversations[0].leadsTo]).toEqual(["second"]);
    expect(structure.pieces).toHaveLength(0);
  });

  it("finds a home for loops nothing starts", () => {
    const structure = analyze(file(`
=== a
-> b

=== b
-> a
`));
    expect(structure.conversations.map((c) => c.root.name)).toEqual(["a"]);
    expect(structure.owners.get("b")).toEqual(["a"]);
  });

  it("splits idle chatter into its topics", () => {
    const text = readFileSync(path.join(__dirname, "../../../../cim-1.21.1/src/main/resources/data/cim/tales/chatter/idle_chatter.tale"), "utf8");
    const structure = analyze(parse(tryParse("cim:chatter/idle_chatter")!, "idle_chatter.tale", text, []));
    expect(structure.conversations.map((c) => c.root.name)).toContain("village_gossip");
    expect(structure.conversations.find((c) => c.root.name === "weather")!.members).toContain("weather_storm");
    expect(structure.pieces.map((p) => p.head.name)).toEqual(expect.arrayContaining(["replies", "gentle_replies"]));
    expect(structure.pieceOf.get("goodbye")!.head.name).toBe("replies");
  });
});
