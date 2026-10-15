// The play-test panel: run a conversation right in the editor, the way the game would - set up the pony,
// the player, how they feel about each other, the world and the dice, then click through it. Effects
// change the setup as they happen (a "+5 fondness" moves the slider), so you see the consequences.

import type { Node } from "../tale/ast";
import type { Vocabulary } from "../tale/vocabulary";
import { type Beat, Conversation, type Library, pickOpener } from "./runtime";
import { type Happening, ODDS_PHRASES, type Sim, bandName, oddsBand, ponyTimeOfDay, voiceTier } from "./sim";
import { type PlayState, type Tier, defaultState, loadState, saveState } from "./state";

export interface PlayCallbacks {
  /** The node the script's cursor is in, for "the node at the cursor". */
  cursorNode(): Node | null;
  /** Move the script to a node (by full ID) - from the "nodes played" trail. */
  showNode(id: string): void;
}

const VOICE_SYMBOLS: Record<string, string> = {
  honesty: "●", kindness: "✿", laughter: "☺", generosity: "♦", loyalty: "⚡", magic: "✦", whisper: "…",
};
const VOICE_COLORS: Record<string, string> = {
  honesty: "#f9c468", kindness: "#fff7a8", laughter: "#f7a8d8", generosity: "#f5f5f5",
  loyalty: "#9ee6fa", magic: "#cc8cf2", whisper: "#c9b3c0",
};

export class PlayPanel {
  private state: PlayState = loadState();
  private vocab: Vocabulary | null = null;
  private library: Library = new Map();
  private conversation: Conversation | null = null;
  private readonly tried = new Set<string>();
  private readonly happened: Happening[] = [];
  private readonly unknown = new Set<string>();
  private readonly setup: HTMLElement;
  private readonly log: HTMLElement;
  private readonly choices: HTMLElement;
  private readonly trail: HTMLElement;
  private readonly happenings: HTMLElement;
  private readonly startSelect: HTMLSelectElement;

  constructor(private readonly host: HTMLElement, private readonly callbacks: PlayCallbacks) {
    host.innerHTML = `
      <div class="play">
        <div class="play-tools">
          <select class="play-start" title="Where the conversation starts"></select>
          <button class="bubble small primary" data-act="play">▶ Play</button>
          <label class="play-dice">🎲 <select>
            <option value="fair">fair dice</option><option value="success">always succeed</option><option value="failure">always fail</option>
          </select></label>
          <button class="bubble small" data-act="reset" title="Back to the default setup">Reset setup</button>
        </div>
        <div class="play-main">
          <div class="play-setup"></div>
          <div class="play-stage">
            <div class="play-log"><div class="placeholder">Set things up on the left, then ▶ Play.</div></div>
            <div class="play-choices"></div>
            <details class="play-happened" open><summary>What happened</summary><div class="play-trail"></div><ul></ul></details>
          </div>
        </div>
      </div>`;
    this.setup = host.querySelector(".play-setup")!;
    this.log = host.querySelector(".play-log")!;
    this.choices = host.querySelector(".play-choices")!;
    this.trail = host.querySelector(".play-trail")!;
    this.happenings = host.querySelector(".play-happened ul")!;
    this.startSelect = host.querySelector(".play-start")!;
    host.querySelector<HTMLButtonElement>("[data-act=play]")!.onclick = () => this.play(this.startSelect.value);
    host.querySelector<HTMLButtonElement>("[data-act=reset]")!.onclick = () => {
      this.state = defaultState();
      this.changed();
    };
    const dice = host.querySelector<HTMLSelectElement>(".play-dice select")!;
    dice.value = this.state.dice;
    dice.onchange = () => {
      this.state.dice = dice.value as PlayState["dice"];
      this.changed(false);
    };
  }

  /** The current library and vocabulary - refreshed whenever the scripts change. */
  update(library: Library, vocab: Vocabulary): void {
    const vocabChanged = this.vocab !== vocab;
    this.library = library;
    this.vocab = vocab;
    if (vocabChanged) {
      this.renderStarts();
      this.renderSetup();
    }
  }

  // ---- Playing ----

  private sim(): Sim {
    return {
      state: this.state,
      vocab: this.vocab!,
      unknown: this.unknown,
      happened: (entry) => this.happened.push(entry),
    };
  }

  /** Starts a conversation: "chatter" is a right-click (a greeting of the topic's tone, then the topic). */
  private play(start: string): void {
    if (!this.vocab) return;
    this.log.innerHTML = "";
    this.happened.length = 0;
    const sim = this.sim();
    let conversation: Conversation | null = null;
    if (start === "@cursor") {
      const node = this.callbacks.cursorNode();
      if (node) conversation = new Conversation(this.library, sim, node, null, false, this.tried);
      else this.note("Put the script's cursor inside a node first.");
    } else if (start === "farewell" || (this.vocab.leadInContexts ?? []).includes(start) && start !== "greeting") {
      const node = pickOpener(start, this.library, sim);
      if (node) conversation = new Conversation(this.library, sim, node, null, true, this.tried);
      else this.note(`Nothing answers "start: ${start}" with this setup.`);
    } else if (start === "chatter") {
      const opener = pickOpener("chatter", this.library, sim);
      if (opener) {
        const greeting = pickOpener("greeting", this.library, sim, opener.tone);
        conversation = new Conversation(this.library, sim, opener, greeting, false, this.tried);
      } else {
        this.note("No chatter opener fits this setup.");
      }
    } else {
      const node = pickOpener(start, this.library, sim);
      if (node) conversation = new Conversation(this.library, sim, node, null, false, this.tried);
      else this.note(`Nothing answers "start: ${start}" with this setup.`);
    }
    this.conversation = conversation;
    if (conversation) this.show(conversation.beat);
    this.changed(false);
  }

  private choose(index: number, label: string): void {
    if (!this.conversation) return;
    this.yourLine(label);
    this.show(this.conversation.choose(index));
    this.changed(false);
  }

  /** The lines of a beat, then its replies - or, at the end, what you could bring up next. */
  private show(beat: Beat): void {
    for (const line of beat.lines) {
      if (line.kind === "spoken") {
        this.entry(line.partner ? this.state.pony.name : line.speaker || "…", "#f9d9c4", this.spoken(line.text), "spoken");
      } else if (line.kind === "voice") {
        this.entry(`${VOICE_SYMBOLS[line.voice] ?? "?"} ${capitalize(line.voice)}`, VOICE_COLORS[line.voice] ?? "#ddd",
          escapeHtml(line.text), "voice");
      } else {
        const success = line.kind === "success";
        this.badge(`${success ? "★ " : ""}${VOICE_SYMBOLS[line.voice] ?? ""} ${capitalize(line.voice)} - ${success ? "it landed." : "it didn't land."}${success ? " ★" : ""}`,
          VOICE_COLORS[line.voice] ?? "#ddd", success);
      }
    }
    this.choices.innerHTML = "";
    if (!beat.ended) {
      beat.options.forEach((option, i) => {
        const label = option.voice
          ? `${VOICE_SYMBOLS[option.voice] ?? "?"} ${option.text}`
          : option.text || capitalize(option.tone ?? "...");
        const button = this.choice(i + 1, label, option.voice ? VOICE_COLORS[option.voice] : undefined);
        if (option.odds >= 0) {
          button.insertAdjacentHTML("beforeend", `<span class="play-odds">${ODDS_PHRASES[oddsBand(option.odds)]}</span>`);
          button.title = `${option.odds}% (players only ever see the phrase)`;
        }
        button.onclick = () => this.choose(i, option.voice ? `${VOICE_SYMBOLS[option.voice] ?? ""} ${option.text}` : label);
      });
      return;
    }
    // The conversation has run its course: bring something up, or say goodbye - as in game.
    this.choices.insertAdjacentHTML("beforeend", `<div class="play-ended">✿ the topic has run its course ✿</div>`);
    let n = 1;
    for (const context of this.vocab?.startContexts ?? []) {
      if (context === "chatter" || (this.vocab?.leadInContexts ?? []).includes(context)) continue;
      const button = this.choice(n++, `Bring up: ${context}`);
      button.onclick = () => {
        this.yourLine(`(brings up ${context})`);
        this.continueWith(context, false);
      };
    }
    const goodbye = this.choice(n, "See you later.");
    goodbye.onclick = () => {
      this.yourLine("See you later.");
      this.continueWith("farewell", true);
    };
  }

  /** Keeps the log and starts the next part of the conversation (a request, or the farewell). */
  private continueWith(context: string, speakOnly: boolean): void {
    const sim = this.sim();
    const node = pickOpener(context, this.library, sim);
    if (!node) {
      this.note(`Nothing answers "start: ${context}" with this setup.`);
      this.choices.innerHTML = "";
      this.changed(false);
      return;
    }
    this.conversation = new Conversation(this.library, sim, node, null, speakOnly, this.tried);
    this.show(this.conversation.beat);
    if (speakOnly) {
      this.choices.innerHTML = `<div class="play-ended">✿ the conversation closes ✿</div>`;
    }
    this.changed(false);
  }

  private spoken(text: string): string {
    const action = /^\*([^*]+)\*\s*(.*)$/.exec(text);
    if (!action) return `“${escapeHtml(text)}”`;
    const words = action[2].trim();
    return `<i class="play-action">*${escapeHtml(action[1].trim())}*</i>${words ? ` “${escapeHtml(words)}”` : ""}`;
  }

  private entry(tag: string, color: string, html: string, kind: string): void {
    this.log.insertAdjacentHTML("beforeend",
      `<div class="play-entry ${kind}"><span class="play-tag" style="--tag:${color}">${escapeHtml(tag)}</span><div class="play-text">${html}</div></div>`);
    this.log.scrollTop = this.log.scrollHeight;
  }

  private yourLine(text: string): void {
    this.entry("You", "#d8c4f5", escapeHtml(text), "you");
  }

  private badge(text: string, color: string, success: boolean): void {
    this.log.insertAdjacentHTML("beforeend",
      `<div class="play-badge${success ? " success" : ""}" style="--tag:${color}">${escapeHtml(text)}</div>`);
  }

  private note(text: string): void {
    this.log.insertAdjacentHTML("beforeend", `<div class="play-note">${escapeHtml(text)}</div>`);
  }

  private choice(number: number, label: string, color?: string): HTMLButtonElement {
    const button = document.createElement("button");
    button.className = "play-choice";
    if (color) button.style.setProperty("--voice", color);
    button.innerHTML = `<span class="play-number">${number}</span><span class="play-label">${escapeHtml(label)}</span>`;
    this.choices.append(button);
    return button;
  }

  // ---- Setup and what happened ----

  /** Remembers the setup, redraws it (effects move its values), and lists what happened. */
  private changed(redrawSetup = true): void {
    saveState(this.state);
    if (redrawSetup || this.happened.length > 0) this.renderSetup();
    this.renderHappened();
  }

  private renderHappened(): void {
    this.happenings.innerHTML = this.happened.length === 0 ? `<li class="quiet">Nothing yet.</li>`
      : this.happened.map((h) => `<li class="${h.kind}">${escapeHtml(h.text)}</li>`).join("");
    this.trail.innerHTML = "";
    for (const id of this.conversation?.visited ?? []) {
      const crumb = document.createElement("button");
      crumb.className = "play-crumb";
      crumb.textContent = id.substring(id.lastIndexOf("/") + 1);
      crumb.title = `${id} - click to see it in the script`;
      crumb.onclick = () => this.callbacks.showNode(id);
      this.trail.append(crumb);
    }
  }

  private renderStarts(): void {
    const vocab = this.vocab!;
    const previous = this.startSelect.value;
    const options: [string, string][] = [["chatter", "Right-click a pony (greeting + topic)"]];
    for (const context of vocab.startContexts) {
      if (context === "chatter" || context === "greeting") continue;
      options.push([context, context === "farewell" ? "Farewell (\"See you later.\")" : `Bring up: ${context}`]);
    }
    options.push(["@cursor", "The node at the script's cursor"]);
    this.startSelect.innerHTML = options.map(([value, label]) => `<option value="${value}">${escapeHtml(label)}</option>`).join("");
    if (options.some(([value]) => value === previous)) this.startSelect.value = previous;
  }

  private renderSetup(): void {
    if (!this.vocab) return;
    const v = this.vocab;
    const s = this.state;
    const values = (condition: string, arg = 0) => v.conditions[condition]?.forms?.[0]?.args?.[arg]?.tokens?.[0]?.values ?? [];
    const emotions = values("cim:mood");
    const open = new Set([...this.setup.querySelectorAll("details[open]")].map((el) => (el as HTMLElement).dataset.section));
    this.setup.innerHTML = "";

    const section = (key: string, title: string, summary: string, build: (body: HTMLElement) => void) => {
      const details = document.createElement("details");
      details.dataset.section = key;
      details.open = open.size === 0 ? key === "pony" : open.has(key);
      details.innerHTML = `<summary><b>${title}</b> <span class="play-summary">${escapeHtml(summary)}</span></summary>`;
      const body = document.createElement("div");
      body.className = "play-fields";
      build(body);
      details.append(body);
      this.setup.append(details);
    };

    section("pony", "🐴 Pony", `${s.pony.name}, ${s.pony.personality} ${s.pony.species.replace(/_/g, " ")}`, (b) => {
      b.append(
        text("Name", s.pony.name, (x) => (s.pony.name = x), this),
        select("Species", values("cim:species"), s.pony.species, (x) => (s.pony.species = x), this),
        select("Personality", values("cim:personality"), s.pony.personality, (x) => (s.pony.personality = x), this),
        multi("Quirks", values("cim:quirk"), s.pony.quirks, (x) => (s.pony.quirks = x), this),
        select("Life stage", values("cim:life_stage"), s.pony.lifeStage, (x) => (s.pony.lifeStage = x), this),
        text("Job", s.pony.profession, (x) => (s.pony.profession = x || "none"), this, "none, farmer, librarian…"),
        moods("Mood", emotions, v.tiers, s.pony.mood, this),
        check("Has family records", s.pony.familyRecorded, (x) => (s.pony.familyRecorded = x), this),
        check("Orphan", s.pony.orphan, (x) => (s.pony.orphan = x), this),
        text("Parent", s.pony.parentName, (x) => (s.pony.parentName = x), this, "a name, or empty"),
        check("Parent nearby", s.pony.parentPresent, (x) => (s.pony.parentPresent = x), this),
        text("Partner", s.pony.partnerName, (x) => (s.pony.partnerName = x), this, "a name, or empty"),
        check("Partner nearby", s.pony.partnerPresent, (x) => (s.pony.partnerPresent = x), this),
        text("Favorite gift", s.pony.favoriteGift, (x) => (s.pony.favoriteGift = x), this),
      );
    });

    const voiceSummary = v.voices.map((voice) => {
      const tier = voiceTier(s, voice);
      return tier < 0 ? null : `${voice} ${["faint", "clear", "strong"][tier]}`;
    }).filter(Boolean).join(", ");
    section("player", "🧑 Player", `${s.player.name} - ${voiceSummary}`, (b) => {
      b.append(
        text("Name", s.player.name, (x) => (s.player.name = x), this),
        select("Species", values("cim:player.species"), s.player.species, (x) => (s.player.species = x), this),
        text("Heritage", s.player.heritage, (x) => (s.player.heritage = x), this, "(not in game yet)"),
        text("Upbringing", s.player.upbringing, (x) => (s.player.upbringing = x), this, "(not in game yet)"),
        moods("Mood", emotions, v.tiers, s.player.mood, this),
        text("Mourning", s.player.mourning, (x) => (s.player.mourning = x), this, "who {mourning} names"),
      );
      const voices = document.createElement("div");
      voices.className = "play-voices";
      voices.innerHTML = `<div class="play-field-label">Inner voices <span class="quiet">(faint 0 · clear 25 · strong 75; the Whisper is silent at 0)</span></div>`;
      for (const voice of v.voices) {
        voices.append(number(`${VOICE_SYMBOLS[voice] ?? ""} ${capitalize(voice)}`, s.player.voices[voice] ?? 0,
          (x) => (s.player.voices[voice] = x), this, 0, 200));
      }
      b.append(voices);
    });

    const r = s.relationship;
    section("relationship", "💞 How they feel about you",
      `fondness ${bandName(r.fondness)}, trust ${bandName(r.trust)}, respect ${bandName(r.respect)}`, (b) => {
        for (const feeling of ["fondness", "trust", "respect"] as const) {
          b.append(number(`${capitalize(feeling)} (${bandName(r[feeling])})`, r[feeling], (x) => (r[feeling] = x), this, -50, 300,
            "bands: low 10 · medium 40 · high 100 · devoted 200"));
        }
        b.append(select("Last talked", values("cim:last_talked"), r.lastTalked, (x) => (r.lastTalked = x), this));
        b.append(list("Memories of you", r.memories.map((m) => `${m.id}${m.emotion ? ` (${m.emotion})` : ""}`),
          (items) => (r.memories = items.map((item) => {
            const match = /^(\S+)(?:\s*\((\w+)\))?$/.exec(item.trim());
            return { id: match?.[1] ?? item.trim(), emotion: match?.[2] ?? "" };
          })), this, "cim:shared_grief (grief)"));
      });

    const w = s.world;
    section("world", "🌍 World", `${w.time}${s.pony.species === "bat_pony" ? ` (the pony's clock: ${ponyTimeOfDay(s)})` : ""}${w.thundering ? ", storm" : w.raining ? ", rain" : ""}`, (b) => {
      b.append(
        select("Time of day", ["morning", "afternoon", "evening", "night"], w.time, (x) => (w.time = x as PlayState["world"]["time"]), this),
        check("Raining", w.raining, (x) => (w.raining = x), this),
        check("Thundering", w.thundering, (x) => (w.thundering = x), this),
        text("Biome", w.biome, (x) => (w.biome = x), this, "minecraft:plains"),
        text("Holiday", w.holiday, (x) => (w.holiday = x), this, "cim:nightmare_night (not in game yet)"),
        select("Season", ["", ...values("cim:season")], w.season, (x) => (w.season = x), this),
        list("Facts", s.facts, (items) => (s.facts = items), this, "cim:world/crystal_empire_restored"),
        check("News to break", s.news.available, (x) => (s.news.available = x), this),
        text("News type", s.news.type, (x) => (s.news.type = x), this, "cim:loss"),
        text("News is about", s.news.subject, (x) => (s.news.subject = x), this, "{news_subject}"),
      );
    });

    section("gift", "🎁 Gift being offered", `${s.gift.item}${s.gift.favorite ? " (favorite)" : ""}`, (b) => {
      b.append(
        text("Item", s.gift.item, (x) => (s.gift.item = x), this, "minecraft:poppy"),
        check("It's their favorite", s.gift.favorite, (x) => (s.gift.favorite = x), this),
        check("They'll take a gift today", s.gift.canTake, (x) => (s.gift.canTake = x), this),
      );
    });

    if (this.unknown.size > 0) {
      section("other", "❔ Other mods' conditions", `${this.unknown.size} set by hand`, (b) => {
        b.insertAdjacentHTML("beforeend", `<div class="quiet">The editor can't simulate these - choose whether they hold.</div>`);
        for (const source of this.unknown) {
          b.append(check(source, s.overrides[source] ?? false, (x) => (s.overrides[source] = x), this));
        }
      });
    }
  }

  /** A setup field changed: remember it and refresh the summaries. */
  edited(): void {
    saveState(this.state);
    this.renderSetup();
  }
}

// ---- Small form helpers ----

function field(label: string, control: HTMLElement, hint?: string): HTMLElement {
  const wrap = document.createElement("label");
  wrap.className = "play-field";
  wrap.innerHTML = `<span class="play-field-label">${escapeHtml(label)}</span>`;
  wrap.append(control);
  if (hint) wrap.insertAdjacentHTML("beforeend", `<span class="quiet">${escapeHtml(hint)}</span>`);
  return wrap;
}

function text(label: string, value: string, set: (value: string) => void, panel: PlayPanel, placeholder = ""): HTMLElement {
  const input = document.createElement("input");
  input.value = value;
  input.placeholder = placeholder;
  input.onchange = () => {
    set(input.value.trim());
    panel.edited();
  };
  input.onkeydown = (event) => event.stopPropagation();
  return field(label, input);
}

function number(label: string, value: number, set: (value: number) => void, panel: PlayPanel, min: number, max: number, hint?: string): HTMLElement {
  const input = document.createElement("input");
  input.type = "number";
  input.min = String(min);
  input.max = String(max);
  input.value = String(value);
  input.onchange = () => {
    set(parseInt(input.value, 10) || 0);
    panel.edited();
  };
  input.onkeydown = (event) => event.stopPropagation();
  return field(label, input, hint);
}

function select(label: string, values: string[], value: string, set: (value: string) => void, panel: PlayPanel): HTMLElement {
  const control = document.createElement("select");
  const all = values.includes(value) ? values : [value, ...values];
  control.innerHTML = all.map((option) => `<option value="${escapeHtml(option)}"${option === value ? " selected" : ""}>${escapeHtml(option || "—")}</option>`).join("");
  control.onchange = () => {
    set(control.value);
    panel.edited();
  };
  return field(label, control);
}

function check(label: string, value: boolean, set: (value: boolean) => void, panel: PlayPanel): HTMLElement {
  const wrap = document.createElement("label");
  wrap.className = "play-check";
  const box = document.createElement("input");
  box.type = "checkbox";
  box.checked = value;
  box.onchange = () => {
    set(box.checked);
    panel.edited();
  };
  wrap.append(box, document.createTextNode(` ${label}`));
  return wrap;
}

function multi(label: string, values: string[], chosen: string[], set: (value: string[]) => void, panel: PlayPanel): HTMLElement {
  const wrap = document.createElement("div");
  wrap.className = "play-multi";
  for (const value of values) {
    const chip = document.createElement("button");
    chip.className = `play-chip${chosen.includes(value) ? " on" : ""}`;
    chip.textContent = value.replace(/_/g, " ");
    chip.onclick = (event) => {
      event.preventDefault();
      set(chosen.includes(value) ? chosen.filter((c) => c !== value) : [...chosen, value]);
      panel.edited();
    };
    wrap.append(chip);
  }
  return field(label, wrap);
}

function moods(label: string, emotions: string[], tiers: string[], mood: Record<string, Tier>, panel: PlayPanel): HTMLElement {
  const wrap = document.createElement("div");
  wrap.className = "play-moods";
  for (const emotion of emotions) {
    const control = document.createElement("select");
    control.innerHTML = ["", ...tiers].map((tier) => `<option value="${tier}"${(mood[emotion] ?? "") === tier ? " selected" : ""}>${tier || "—"}</option>`).join("");
    control.onchange = () => {
      if (control.value) mood[emotion] = control.value as Tier;
      else delete mood[emotion];
      panel.edited();
    };
    const item = document.createElement("label");
    item.className = "play-mood";
    item.append(document.createTextNode(emotion), control);
    wrap.append(item);
  }
  return field(label, wrap);
}

function list(label: string, items: string[], set: (items: string[]) => void, panel: PlayPanel, placeholder: string): HTMLElement {
  const area = document.createElement("textarea");
  area.rows = Math.max(2, items.length + 1);
  area.value = items.join("\n");
  area.placeholder = `one per line, e.g. ${placeholder}`;
  area.onchange = () => {
    set(area.value.split("\n").map((line) => line.trim()).filter(Boolean));
    panel.edited();
  };
  area.onkeydown = (event) => event.stopPropagation();
  return field(label, area);
}

function capitalize(word: string): string {
  return word.charAt(0).toUpperCase() + word.substring(1);
}

function escapeHtml(text: string): string {
  return text.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]!);
}
