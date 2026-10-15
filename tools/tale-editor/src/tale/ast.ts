// What a parsed .tale file looks like - a direct mirror of the game's TaleAst.java, so the editor
// and the game always mean the same thing by a line.

import type { Identifier } from "./identifier";

export interface TaleFile {
  id: Identifier;
  source: string;
  nodes: Node[];
  threads: Thread[];
}

export interface Node {
  name: string;
  id: Identifier;
  line: number;
  start: string[];
  weight: number;
  bonuses: WeightBonus[];
  when: ConditionCall[];
  body: Statement[];
  /** "tone: gentle" - a greeting only opens a conversation of the same tone. */
  tone: string | null;
}

export interface WeightBonus {
  line: number;
  amount: number;
  conditions: ConditionCall[];
}

export type Statement = Spoken | Interjection | EffectStatement | Jump | Reply;

export interface Spoken {
  kind: "spoken";
  line: number;
  speaker: string;
  text: string;
  alternatives: string[];
}

export interface Interjection {
  kind: "interjection";
  line: number;
  voice: string;
  tier: string;
  text: string;
}

export interface EffectStatement {
  kind: "effect";
  line: number;
  effect: EffectCall;
  conditions: ConditionCall[];
}

export interface Jump {
  kind: "jump";
  line: number;
  target: string;
  conditions: ConditionCall[];
}

export interface Reply {
  kind: "reply";
  line: number;
  tone: string | null;
  voice: string | null;
  voiceTier: string | null;
  feeling: string | null;
  text: string;
  once: boolean;
  body: Statement[];
  successTarget: string | null;
  failureTarget: string | null;
}

export interface ConditionCall {
  line: number;
  type: Identifier;
  args: string[];
  comparison: string | null;
  negated: boolean;
  source: string;
}

export interface EffectCall {
  line: number;
  type: Identifier;
  args: string[];
  source: string;
}

export interface Thread {
  id: Identifier;
  line: number;
  onCue: boolean;
  requires: ConditionCall[];
  stages: Stage[];
}

export interface Stage {
  name: string;
  line: number;
  triggers: Trigger[];
  effects: EffectCall[];
  after: After | null;
}

export interface Trigger {
  line: number;
  event: EventCall;
  chancePercent: number;
  target: string | null;
}

export interface EventCall {
  line: number;
  type: Identifier;
  args: string[];
  source: string;
}

export interface After {
  line: number;
  times: number;
  target: string;
}

export function texts(spoken: Spoken): string[] {
  return [spoken.text, ...spoken.alternatives];
}

export function jumpIsConditional(jump: Jump): boolean {
  return jump.conditions.length > 0;
}
