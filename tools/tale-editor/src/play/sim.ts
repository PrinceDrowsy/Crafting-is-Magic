// The game's own words, simulated: every built-in condition, effect and variable read from (and written
// to) the play-test state - a mirror of CimTaleVocabulary and CimVoices, so a conversation plays out
// here the way it would in game. Words from other mods can't be simulated; their conditions are set by
// hand in the panel, and their effects are listed as "not simulated".

import type { ConditionCall, EffectCall, Reply } from "../tale/ast";
import { idString } from "../tale/identifier";
import type { Vocabulary } from "../tale/vocabulary";
import type { PlayState, Tier } from "./state";

/** One thing that happened, for the "what happened" list. */
export interface Happening {
  text: string;
  kind: "effect" | "roll" | "note" | "unsimulated";
}

export interface Sim {
  state: PlayState;
  vocab: Vocabulary;
  happened(entry: Happening): void;
  /** Conditions met that the editor can't evaluate - collected so the panel can offer toggles. */
  unknown: Set<string>;
}

const BANDS = ["none", "low", "medium", "high", "devoted"];
const BAND_AT = [0, 10, 40, 100, 200];
const TIERS: Tier[] = ["faint", "clear", "strong"];
const TONE_AFFINITY: Record<string, string> = {
  peppy: "friendly", steadfast: "friendly", gentle: "friendly", bookish: "curious", refined: "curious",
  daring: "bold", cranky: "bold",
};
const VALUED: Record<string, string[]> = {
  steadfast: ["loyalty", "honesty"], peppy: ["laughter"], gentle: ["kindness"], refined: ["generosity"],
  bookish: ["magic"], daring: ["loyalty"], cranky: ["honesty"], lazy: [],
};
/** Representative world ticks for each time of day (0 is 6:00), to place a bat pony's flipped clock. */
const TIME_TICKS = { morning: 1000, afternoon: 8000, evening: 12500, night: 18000 };

export function bandIndex(value: number): number {
  let band = 0;
  BAND_AT.forEach((at, i) => {
    if (value >= at) band = i;
  });
  return band;
}

export function bandName(value: number): string {
  return BANDS[bandIndex(value)];
}

function tierIndex(tier: string): number {
  return TIERS.indexOf((tier || "faint").toLowerCase() as Tier);
}

/** -1 silent, 0 faint, 1 clear, 2 strong - as VoiceLeanings.tier. */
export function voiceTier(state: PlayState, voice: string): number {
  const points = state.player.voices[voice.toLowerCase()] ?? 0;
  if (voice.toLowerCase() === "whisper" && points <= 0) return -1;
  return points >= 75 ? 2 : points >= 25 ? 1 : 0;
}

function feels(mood: Record<string, Tier>, emotion: string, least: string): boolean {
  const has = mood[emotion.toLowerCase()];
  return !!has && tierIndex(has) >= Math.max(0, tierIndex(least));
}

function compare(actual: number, op: string, target: number): boolean {
  switch (op) {
    case ">=": return actual >= target;
    case "<=": return actual <= target;
    case ">": return actual > target;
    case "<": return actual < target;
    case "!=": return actual !== target;
    default: return actual === target;
  }
}

function id(text: string, namespace = "cim"): string {
  const trimmed = text.trim().toLowerCase();
  return trimmed.includes(":") ? trimmed : `${namespace}:${trimmed}`;
}

/** The pony's own time of day (bat ponies live by night, so theirs is flipped), as CimTaleVocabulary.timeOfDay. */
export function ponyTimeOfDay(state: PlayState): string {
  let time = TIME_TICKS[state.world.time];
  if (state.pony.species === "bat_pony") time = (time + 12000) % 24000;
  if (time >= 23000 || time < 5000) return "morning";
  return time < 11000 ? "afternoon" : time < 14000 ? "evening" : "night";
}

/** Whether {@code condition} holds; {@code args} have variables already filled in. */
export function holds(condition: ConditionCall, args: string[], sim: Sim): boolean {
  const result = test(condition, args, sim);
  return result !== condition.negated;
}

function test(condition: ConditionCall, args: string[], sim: Sim): boolean {
  const s = sim.state;
  const arg = (i: number) => (args[i] ?? "").trim().toLowerCase();
  switch (idString(condition.type)) {
    case "cim:fondness":
    case "cim:trust":
    case "cim:respect": {
      const feeling = condition.type.path as "fondness" | "trust" | "respect";
      return compare(bandIndex(s.relationship[feeling]), condition.comparison ?? "==", BANDS.indexOf(arg(0)));
    }
    case "cim:mood": return feels(s.pony.mood, arg(0), arg(1));
    case "cim:player.mood": return feels(s.player.mood, arg(0), arg(1));
    case "cim:fact": return s.facts.includes(id(arg(0)));
    case "cim:remembers": return s.relationship.memories.some((memory) => id(memory.id) === id(arg(0)));
    case "cim:voice": return voiceTier(s, arg(0)) >= Math.max(0, tierIndex(arg(1)));
    case "cim:species": return s.pony.species === arg(0);
    case "cim:personality": return s.pony.personality === arg(0);
    case "cim:quirk": return s.pony.quirks.includes(arg(0));
    case "cim:likes_tone": return TONE_AFFINITY[s.pony.personality] === arg(0);
    case "cim:life_stage": return s.pony.lifeStage === arg(0);
    case "cim:profession": return id(s.pony.profession || "none", "minecraft") === id(arg(0), "minecraft");
    case "cim:family":
      return arg(0) === "recorded" ? s.pony.familyRecorded
        : arg(0) === "orphan" ? s.pony.familyRecorded && s.pony.orphan
        : arg(0) === "parent" ? s.pony.familyRecorded && !!s.pony.parentName
        : arg(0) === "partner" ? s.pony.familyRecorded && !!s.pony.partnerName : false;
    case "cim:present":
      return arg(0) === "parent" ? s.pony.parentPresent && !!s.pony.parentName : s.pony.partnerPresent && !!s.pony.partnerName;
    case "cim:player.species": return s.player.species === arg(0);
    case "cim:player.heritage": return s.player.heritage === arg(0);
    case "cim:player.upbringing": return s.player.upbringing === arg(0);
    case "cim:news": return s.news.available && (args.length === 0 || id(s.news.type) === id(arg(0)));
    case "cim:night": return s.world.time === "night";
    case "cim:raining": return s.world.raining || s.world.thundering;
    case "cim:thundering": return s.world.thundering;
    case "cim:biome": return args.some((a) => id(a.replace(/^#/, ""), "minecraft") === id(s.world.biome.replace(/^#/, ""), "minecraft"));
    case "cim:holiday": return !!s.world.holiday && id(s.world.holiday) === id(arg(0));
    case "cim:season": return s.world.season === arg(0);
    case "cim:time_of_day": return ponyTimeOfDay(s) === arg(0);
    case "cim:last_talked": return s.relationship.lastTalked === arg(0);
    case "cim:gift":
      return args.some((a) => a.trim().toLowerCase() === "favorite" ? s.gift.favorite : id(a, "minecraft") === id(s.gift.item, "minecraft"));
    case "cim:can_take_gift": return s.gift.canTake;
    default:
      sim.unknown.add(condition.source);
      return s.overrides[condition.source] ?? false;
  }
}

function readable(text: string): string {
  return text.replace(/^[a-z0-9_.-]+:/, "").replace(/_/g, " ");
}

/** {@code {name}} for a variable, or null to leave the text as written - as CimTaleVocabulary's variables. */
export function variable(name: string, state: PlayState): string | null {
  switch (name) {
    case "pony": return state.pony.name;
    case "player": return state.player.name;
    case "species": return readable(state.pony.species);
    case "player.species": return state.player.species ? readable(state.player.species) : null;
    case "profession": return state.pony.profession && state.pony.profession !== "none" ? readable(state.pony.profession) : null;
    case "favorite_gift": return state.pony.favoriteGift || "a surprise";
    case "mourning": return state.player.mourning || null;
    case "news_subject": return state.news.available && state.news.subject ? state.news.subject : null;
    case "parent": return state.pony.parentPresent && state.pony.parentName ? state.pony.parentName : null;
    case "partner": return state.pony.partnerPresent && state.pony.partnerName ? state.pony.partnerName : null;
    case "gift": return state.gift.item ? readable(state.gift.item).toLowerCase() : null;
    default: return null;
  }
}

/** Fills {variables} in; in arguments ({@code asId}) the value is made ID-safe, as TaleRuntime.fill. */
export function fill(text: string, state: PlayState, asId: boolean): string {
  return text.replace(/\{([A-Za-z0-9_.]+)\}/g, (whole, name: string) => {
    const value = variable(name.toLowerCase(), state);
    if (value === null) return whole;
    return asId ? value.toLowerCase().replace(/[^a-z0-9_.-]+/g, "_") : value;
  });
}

/** Carries an effect out on the state, and says what happened. */
export function apply(effect: EffectCall, args: string[], sim: Sim): void {
  const s = sim.state;
  const words = (args[0] ?? "").trim().split(/\s+/);
  const say = (text: string) => sim.happened({ text, kind: "effect" });
  switch (idString(effect.type)) {
    case "cim:fondness":
    case "cim:trust":
    case "cim:respect": {
      const feeling = effect.type.path as "fondness" | "trust" | "respect";
      const before = bandName(s.relationship[feeling]);
      s.relationship[feeling] += parseInt(words[0], 10) || 0;
      const after = bandName(s.relationship[feeling]);
      say(`${feeling} ${words[0]} → ${s.relationship[feeling]}${before !== after ? ` (now ${after})` : ""}`);
      return;
    }
    case "cim:memory": {
      const emotion = (args[1] ?? "").trim().split(/\s+/)[0] ?? "";
      s.relationship.memories.push({ id: id(args[0] ?? ""), emotion });
      say(`the pony remembers ${id(args[0] ?? "")}${emotion ? ` (${args[1].trim()})` : ""}`);
      return;
    }
    case "cim:fact": {
      const fact = id(words[1] ?? "");
      s.facts = s.facts.filter((f) => f !== fact);
      if (words[0] === "set") s.facts.push(fact);
      say(`fact ${words[0]} ${fact}`);
      return;
    }
    case "cim:leaning": {
      const voice = (words[0] ?? "").toLowerCase();
      s.player.voices[voice] = (s.player.voices[voice] ?? 0) + (parseInt(words[1], 10) || 0);
      say(`${voice} ${words[1]} → ${s.player.voices[voice]} points`);
      return;
    }
    case "cim:tell_news":
      if (s.news.available) {
        s.news.available = false;
        say(`the player learns the news${s.news.subject ? ` about ${s.news.subject}` : ""} - grief and its moment arrive`);
      } else {
        say("tell_news: no news to tell");
      }
      return;
    case "cim:take_gift":
      if (!s.gift.canTake) {
        say("take_gift: past today's limit - the player keeps it");
        return;
      }
      s.relationship.fondness += s.gift.favorite ? 20 : 5;
      if (s.gift.favorite) s.relationship.trust += 3;
      say(`the pony takes the ${readable(s.gift.item)} - fondness +${s.gift.favorite ? "20, trust +3 (their favorite!)" : "5"}`);
      return;
    case "cim:open_trade": say("the trade screen opens (after these lines are read)"); return;
    case "cim:moment": say(`moment: ${args[0]}`); return;
    case "cim:thread": say(`thread ${args[0]}`); return;
    default:
      sim.happened({ text: `~ ${effect.source} (not simulated here)`, kind: "unsimulated" });
  }
}

/** A voice reply's chance, as CimVoices.odds: the voice's strength, what the pony values, warmth, mood, memories. */
export function odds(reply: Reply, sim: Sim): number {
  const s = sim.state;
  const voice = (reply.voice ?? "").toLowerCase();
  let chance = 30 + Math.round((Math.min(s.player.voices[voice] ?? 0, 150) / 150) * 30);
  if ((VALUED[s.pony.personality] ?? []).includes(voice)) chance += 15;
  const feeling = reply.feeling?.toLowerCase();
  if (feeling && ["fondness", "trust", "respect"].includes(feeling) && !sim.vocab.feelinglessVoices.includes(voice)) {
    chance += [-10, 0, 8, 15, 22][bandIndex(s.relationship[feeling as "fondness" | "trust" | "respect"])];
  }
  const clear = (emotion: string) => feels(s.pony.mood, emotion, "clear");
  const hurting = clear("grief") || clear("fear");
  switch (voice) {
    case "kindness": case "generosity": case "whisper": chance += hurting ? 10 : 0; break;
    case "laughter": chance += (clear("joy") ? 10 : 0) - (clear("grief") || clear("anger") ? 10 : 0); break;
    case "honesty": chance += clear("anger") ? -10 : 0; break;
    case "loyalty": chance += clear("fear") ? 10 : 0; break;
    case "magic": chance += clear("joy") || clear("calm") ? 5 : 0; break;
  }
  let memoryShift = 0;
  for (const memory of s.relationship.memories) {
    if (["joy", "love", "calm"].includes(memory.emotion)) memoryShift += 4;
    else if (["anger", "fear", "grief"].includes(memory.emotion)) memoryShift -= 4;
  }
  chance += Math.max(-12, Math.min(12, memoryShift));
  return Math.max(5, Math.min(95, chance));
}

/** The phrase band shown for odds - never the number, as in game. */
export const ODDS_PHRASES = ["a long shot", "worth a try", "could go either way", "likely", "almost a sure thing"];

export function oddsBand(chance: number): number {
  return chance < 25 ? 0 : chance < 45 ? 1 : chance < 60 ? 2 : chance < 80 ? 3 : 4;
}
