// Plays conversations - a port of the game's TaleRuntime.java, so the play-test panel shows what the game
// would: spoken lines pile up, effects happen, jumps are followed, until the node's replies (a pause) or
// the end. Interjections and voice replies show only when the player's voice is strong enough; voice
// replies roll (with the panel's dice) and follow success or failure. Greetings and farewells only speak.

import type { Node, Reply, Spoken, Statement } from "../tale/ast";
import { idString, tryParse } from "../tale/identifier";
import { apply, fill, holds, odds, voiceTier, type Sim } from "./sim";
import type { ConditionCall } from "../tale/ast";

export const MAX_STEPS = 256;

export type LineKind = "spoken" | "voice" | "success" | "failure";

export interface Line {
  kind: LineKind;
  speaker: string;
  partner: boolean;
  text: string;
  voice: string;
}

export interface Option {
  tone: string | null;
  voice: string | null;
  text: string;
  /** For a voice reply, its chance in percent; -1 for a tone reply. */
  odds: number;
}

export interface Beat {
  lines: Line[];
  options: Option[];
  ended: boolean;
}

/** Every node in the library, by full ID - the editor's snapshot of what the game would load. */
export type Library = Map<string, Node>;

export function allHold(conditions: ConditionCall[], sim: Sim): boolean {
  return conditions.every((condition) => holds(condition, condition.args.map((arg) => fill(arg, sim.state, true)), sim));
}

/**
 * Where a conversation opens: among nodes marked {@code start: <context>} whose when: holds, weighted
 * (bonuses included); {@code tone} narrows it to nodes of exactly that tone ({@code undefined} = any).
 */
export function pickOpener(context: string, library: Library, sim: Sim, tone?: string | null): Node | null {
  const candidates: Node[] = [];
  const weights: number[] = [];
  let total = 0;
  for (const node of library.values()) {
    if (!node.start.includes(context) || (tone !== undefined && node.tone !== tone) || !allHold(node.when, sim)) continue;
    let weight = node.weight;
    for (const bonus of node.bonuses) {
      if (allHold(bonus.conditions, sim)) weight += bonus.amount;
    }
    if (weight > 0) {
      candidates.push(node);
      weights.push(weight);
      total += weight;
    }
  }
  if (candidates.length === 0) return null;
  let roll = Math.floor(Math.random() * total);
  for (let i = 0; i < candidates.length; i++) {
    roll -= weights[i];
    if (roll < 0) return candidates[i];
  }
  return candidates[candidates.length - 1];
}

/** A jump target written in {@code from}'s file: a local node name, or a full node ID. */
export function resolve(from: Node, target: string): string | null {
  if (target.includes(":")) {
    const id = tryParse(target);
    return id ? idString(id) : null;
  }
  const path = from.id.path;
  return `${from.id.namespace}:${path.substring(0, path.lastIndexOf("/") + 1)}${target}`;
}

export class Conversation {
  node: Node;
  private offered: Reply[] = [];
  beat: Beat;
  /** Every node played, in order - shown in the panel. */
  readonly visited: string[] = [];
  /** !once replies tried (in game, a relationship fact). */
  private readonly tried: Set<string>;

  constructor(private readonly library: Library, private readonly sim: Sim, node: Node, leadIn: Node | null,
              speakOnly = false, tried = new Set<string>()) {
    this.node = node;
    this.tried = tried;
    const lines: Line[] = [];
    if (speakOnly) {
      this.leadIn(node, lines);
      this.beat = { lines, options: [], ended: true };
      return;
    }
    if (leadIn) this.leadIn(leadIn, lines);
    this.beat = this.run(node, lines, { count: 0 });
  }

  /** A lead-in (or farewell) only speaks and runs effects; replies and jumps are skipped. */
  private leadIn(from: Node, lines: Line[]): void {
    this.visited.push(idString(from.id));
    for (const statement of from.body) {
      if (statement.kind === "spoken") lines.push(this.line(statement));
      else if (statement.kind === "effect" && allHold(statement.conditions, this.sim)) this.apply(statement);
      else if (statement.kind === "interjection" && this.hears(statement.voice, statement.tier)) {
        lines.push({ kind: "voice", speaker: "", partner: false, text: fill(statement.text, this.sim.state, false), voice: statement.voice.toLowerCase() });
      }
    }
  }

  choose(index: number): Beat {
    if (this.beat.ended || index < 0 || index >= this.offered.length) return this.beat;
    const reply = this.offered[index];
    const from = this.node;
    this.offered = [];
    const lines: Line[] = [];
    const steps = { count: 0 };
    let rolledTarget: string | null = null;
    if (reply.voice !== null) {
      const voice = reply.voice.toLowerCase();
      const chance = odds(reply, this.sim);
      const dice = this.sim.state.dice;
      const roll = dice === "success" ? 0 : dice === "failure" ? 99 : Math.floor(Math.random() * 100);
      const success = roll < chance;
      this.sim.happened({
        text: `${voice} at ${chance}%, rolled ${roll} - ${success ? "success" : "failure"}${dice !== "fair" ? " (forced)" : ""}`,
        kind: "roll",
      });
      // attempted: the voice grows a little with use, and a !once reply is remembered as tried
      this.sim.state.player.voices[voice] = (this.sim.state.player.voices[voice] ?? 0) + 3;
      if (reply.once) this.tried.add(`${idString(from.id)}#${reply.line}`);
      lines.push({ kind: success ? "success" : "failure", speaker: "", partner: false, text: "", voice });
      rolledTarget = success ? reply.successTarget : reply.failureTarget;
    }
    for (const statement of reply.body) {
      if (++steps.count > MAX_STEPS) return (this.beat = this.loop());
      if (statement.kind === "effect" && allHold(statement.conditions, this.sim)) this.apply(statement);
      else if (statement.kind === "jump" && allHold(statement.conditions, this.sim)) return (this.beat = this.follow(statement.target, lines, steps));
    }
    if (rolledTarget !== null) return (this.beat = this.follow(rolledTarget, lines, steps));
    return (this.beat = { lines, options: [], ended: true });
  }

  private run(from: Node, lines: Line[], steps: { count: number }): Beat {
    this.node = from;
    this.visited.push(idString(from.id));
    this.offered = [];
    for (const statement of from.body as Statement[]) {
      if (++steps.count > MAX_STEPS) return this.loop();
      if (this.offered.length > 0 && statement.kind !== "reply") continue; // once replies start, the rest is replies
      switch (statement.kind) {
        case "spoken": lines.push(this.line(statement)); break;
        case "effect": if (allHold(statement.conditions, this.sim)) this.apply(statement); break;
        case "jump": if (allHold(statement.conditions, this.sim)) return this.follow(statement.target, lines, steps); break;
        case "interjection":
          if (this.hears(statement.voice, statement.tier)) {
            lines.push({ kind: "voice", speaker: "", partner: false, text: fill(statement.text, this.sim.state, false), voice: statement.voice.toLowerCase() });
          }
          break;
        case "reply":
          if (statement.voice === null || this.offersVoiceReply(from, statement)) this.offered.push(statement);
          break;
      }
    }
    if (this.offered.length === 0) return { lines, options: [], ended: true };
    const options: Option[] = this.offered.map((reply) => reply.voice !== null
      ? { tone: null, voice: reply.voice.toLowerCase(), text: fill(reply.text, this.sim.state, false), odds: odds(reply, this.sim) }
      : { tone: reply.tone, voice: null, text: fill(reply.text, this.sim.state, false), odds: -1 });
    return { lines, options, ended: false };
  }

  private hears(voice: string, tier: string): boolean {
    return voiceTier(this.sim.state, voice) >= Math.max(0, ["faint", "clear", "strong"].indexOf(tier.toLowerCase()));
  }

  private offersVoiceReply(from: Node, reply: Reply): boolean {
    if (!this.hears(reply.voice!, reply.voiceTier ?? "faint")) return false;
    return !reply.once || !this.tried.has(`${idString(from.id)}#${reply.line}`);
  }

  private follow(target: string, lines: Line[], steps: { count: number }): Beat {
    if (target === "END") {
      this.offered = [];
      return { lines, options: [], ended: true };
    }
    const id = resolve(this.node, target);
    const next = id ? this.library.get(id) : undefined;
    if (!next) {
      this.sim.happened({ text: `no node "${target}" to jump to - the conversation ends (in game this is logged as an error)`, kind: "note" });
      this.offered = [];
      return { lines, options: [], ended: true };
    }
    return this.run(next, lines, steps);
  }

  private loop(): Beat {
    this.sim.happened({ text: `more than ${MAX_STEPS} steps without a reply - probably a jump loop; the conversation ends`, kind: "note" });
    this.offered = [];
    return { lines: [], options: [], ended: true };
  }

  private line(spoken: Spoken): Line {
    const texts = [spoken.text, ...spoken.alternatives];
    const text = texts[Math.floor(Math.random() * texts.length)];
    const partner = spoken.speaker === "{pony}";
    return { kind: "spoken", speaker: fill(spoken.speaker, this.sim.state, false), partner, text: fill(text, this.sim.state, false), voice: "" };
  }

  private apply(statement: Extract<Statement, { kind: "effect" }>): void {
    apply(statement.effect, statement.effect.args.map((arg) => fill(arg, this.sim.state, true)), this.sim);
  }
}
