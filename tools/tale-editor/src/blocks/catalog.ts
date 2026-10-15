// The block palette: every kind of block a conversation can be built from, in categories, generated
// from the game's vocabulary - so new conditions and effects show up here without touching the editor.

import type { Vocabulary } from "../tale/vocabulary";
import { defaultCondition, defaultEffect, shortName } from "./calls";

/** Where a block may be dropped. */
export type Fits =
  | "body"       // a line in a node: talk, jumps, effects, replies
  | "hat"        // start:, weight:, when: - right under the node's header
  | "or"         // right under a spoken line
  | "child"      // inside a tone reply (effects and jumps)
  | "voice-child" // inside a voice reply (effects only - it branches on success/failure instead)
  | "trash"      // the palette: blocks dragged back there are thrown away
  | "condition"  // onto a jump, effect, weight bonus, when:, or a node's header
  | "once"       // onto a reply
  | "node";      // onto the empty canvas: a whole new node

export type Category = "talk" | "choices" | "flow" | "conditions" | "effects" | "starting";

export interface Template {
  id: string;
  category: Category;
  group?: string;
  label: string;
  /** Lines to insert (nested ones already indented relative to the first), or the condition text. */
  lines: string[];
  fits: Fits[];
  hint?: string;
}

export const CATEGORIES: { id: Category; label: string; icon: string }[] = [
  { id: "talk", label: "Talk", icon: "💬" },
  { id: "choices", label: "Choices", icon: "🔀" },
  { id: "flow", label: "Flow", icon: "➡" },
  { id: "conditions", label: "Conditions", icon: "❓" },
  { id: "effects", label: "Effects", icon: "✨" },
  { id: "starting", label: "Starting", icon: "★" },
];

const CONDITION_GROUPS: Record<string, string> = {
  fondness: "Relationship", trust: "Relationship", respect: "Relationship", remembers: "Relationship",
  likes_tone: "Relationship", family: "Relationship", present: "Relationship",
  species: "Pony", personality: "Pony", quirk: "Pony", profession: "Pony", life_stage: "Pony", mood: "Pony", voice: "Pony",
  biome: "World", season: "World", holiday: "World", night: "World", raining: "World", thundering: "World",
  fact: "World", news: "World",
};
const EFFECT_GROUPS: Record<string, string> = {
  fondness: "Relationship", trust: "Relationship", respect: "Relationship", memory: "Relationship",
  fact: "Story", thread: "Story", tell_news: "Story", moment: "Story",
  leaning: "Voices",
};

export function catalog(vocab: Vocabulary): Template[] {
  const voice = capitalize(vocab.voices[0] ?? "Kindness");
  const tone = capitalize(vocab.tones[0] ?? "Friendly");
  const feeling = capitalize(vocab.feelings.includes("trust") ? "trust" : vocab.feelings[0] ?? "Trust");
  const templates: Template[] = [
    { id: "pony_says", category: "talk", label: "pony says…", lines: ["{pony}: Something to say."], fits: ["body"] },
    { id: "or", category: "talk", label: "…or says", lines: ["or: Another way to say it."], fits: ["or"],
      hint: "Drop right under a spoken line - one of them is picked at random" },
    { id: "voice", category: "talk", label: "inner voice", lines: [`(${voice}) What the voice whispers.`], fits: ["body"] },

    { id: "reply", category: "choices", label: "reply in a tone", lines: [`* ${tone}: What you say.`, "    -> END"], fits: ["body"] },
    { id: "voice_reply", category: "choices", label: "voice check", lines: [
      `* [${voice} | ${feeling}] What you try.`, "    success -> END", "    failure -> END"], fits: ["body"],
      hint: "Rolls the dice - success and failure go different ways" },
    { id: "once", category: "choices", label: "only once", lines: ["!once"], fits: ["once"], hint: "Drop on a reply" },

    { id: "go_to", category: "flow", label: "go to…", lines: ["-> END"], fits: ["body", "child"] },
    { id: "end", category: "flow", label: "end", lines: ["-> END"], fits: ["body", "child"] },
    { id: "new_node", category: "flow", label: "new node", lines: ["=== new_node", "{pony}: Something to say."], fits: ["node"],
      hint: "Drop on empty space" },

    { id: "start", category: "starting", label: "starts in…", lines: [`start: ${vocab.startContexts[0] ?? "chatter"}`], fits: ["hat"] },
    { id: "weight", category: "starting", label: "weight", lines: ["weight: 2"], fits: ["hat"], hint: "How often it's picked" },
    { id: "weight_bonus", category: "starting", label: "more likely if…", lines: ["weight: +3 if night"], fits: ["hat"] },
    { id: "when", category: "starting", label: "only when…", lines: ["when: night"], fits: ["hat"] },
    { id: "tone", category: "starting", label: "tone", lines: ["tone: gentle"], fits: ["hat"],
      hint: "Heavy topics feel gentle - only greetings of the same tone open them" },
  ];
  for (const id of Object.keys(vocab.conditions).sort()) {
    const name = shortName(id);
    templates.push({
      id: `condition:${id}`, category: "conditions", group: name.startsWith("player.") ? "Player" : CONDITION_GROUPS[name] ?? "Other",
      label: name, lines: [defaultCondition(vocab, id)], fits: ["condition"], hint: vocab.conditions[id].usage,
    });
  }
  for (const id of Object.keys(vocab.effects).sort()) {
    const name = shortName(id);
    templates.push({
      id: `effect:${id}`, category: "effects", group: EFFECT_GROUPS[name] ?? "Other",
      label: name, lines: [`~ ${defaultEffect(vocab, id)}`], fits: ["body", "child", "voice-child"], hint: vocab.effects[id].usage,
    });
  }
  return templates;
}

export function capitalize(word: string): string {
  return word.charAt(0).toUpperCase() + word.substring(1);
}
