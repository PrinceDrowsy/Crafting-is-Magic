// Everything the play-test panel lets you set: the pony, the player, how they feel about each other,
// the world, a gift being offered, and the dice. Conversations read it through conditions and
// variables, and their effects change it - so you see consequences, not just lines.

export type Tier = "" | "faint" | "clear" | "strong";
export type Dice = "fair" | "success" | "failure";

export interface Memory {
  id: string;
  emotion: string;
}

export interface PlayState {
  pony: {
    name: string;
    species: string;
    personality: string;
    quirks: string[];
    lifeStage: string;
    profession: string;
    /** Has family records at all; orphaned. A parent or partner is "there" when they have a name. */
    familyRecorded: boolean;
    orphan: boolean;
    parentName: string;
    partnerName: string;
    parentPresent: boolean;
    partnerPresent: boolean;
    favoriteGift: string;
    mood: Record<string, Tier>;
  };
  player: {
    name: string;
    species: string;
    heritage: string;
    upbringing: string;
    mood: Record<string, Tier>;
    /** Inner voice points: faint from 0, clear from 25, strong from 75 (the Whisper is silent at 0). */
    voices: Record<string, number>;
    mourning: string;
  };
  relationship: {
    fondness: number;
    trust: number;
    respect: number;
    lastTalked: string;
    memories: Memory[];
  };
  world: {
    /** The world's clock; a bat pony's own clock is flipped from it, as in game. */
    time: "morning" | "afternoon" | "evening" | "night";
    raining: boolean;
    thundering: boolean;
    biome: string;
    holiday: string;
    season: string;
  };
  news: { available: boolean; type: string; subject: string };
  gift: { item: string; favorite: boolean; canTake: boolean };
  /** Story facts at every scope, by full ID ("cim:world/...", "cim:player/...", "cim:relationship/..."). */
  facts: string[];
  dice: Dice;
  /** Conditions the editor can't simulate (another mod's), set by hand: source text -> holds. */
  overrides: Record<string, boolean>;
}

export function defaultState(): PlayState {
  return {
    pony: {
      name: "Mint Leaf", species: "earth_pony", personality: "peppy", quirks: [], lifeStage: "adult", profession: "farmer",
      familyRecorded: true, orphan: false, parentName: "Clover Patch", partnerName: "", parentPresent: false, partnerPresent: false,
      favoriteGift: "Poppy", mood: {},
    },
    player: { name: "Player", species: "unicorn", heritage: "", upbringing: "", mood: {}, voices: {}, mourning: "" },
    relationship: { fondness: 0, trust: 0, respect: 0, lastTalked: "never", memories: [] },
    world: { time: "morning", raining: false, thundering: false, biome: "minecraft:plains", holiday: "", season: "" },
    news: { available: false, type: "cim:loss", subject: "" },
    gift: { item: "minecraft:poppy", favorite: false, canTake: true },
    facts: [],
    dice: "fair",
    overrides: {},
  };
}

const STORAGE_KEY = "tale-editor.play-state";

/** The setup is remembered between visits - a convenience; it falls back to defaults if storage is unavailable. */
export function loadState(): PlayState {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      const base = defaultState();
      const parsed = JSON.parse(saved) as Partial<PlayState>;
      return {
        ...base, ...parsed,
        pony: { ...base.pony, ...parsed.pony }, player: { ...base.player, ...parsed.player },
        relationship: { ...base.relationship, ...parsed.relationship }, world: { ...base.world, ...parsed.world },
        news: { ...base.news, ...parsed.news }, gift: { ...base.gift, ...parsed.gift },
      };
    }
  } catch {
    // fall through to defaults
  }
  return defaultState();
}

export function saveState(state: PlayState): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // remembering is a nicety
  }
}

export function cloneState(state: PlayState): PlayState {
  return JSON.parse(JSON.stringify(state)) as PlayState;
}
