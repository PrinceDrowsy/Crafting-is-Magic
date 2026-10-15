// Turns the text of a .tale file into a TaleFile - a line-for-line port of the game's TaleParser.java,
// messages included, so the editor reports exactly what the game would. Never throws on bad input:
// every problem is recorded with its line and parsing carries on.

import type {
  After, ConditionCall, EffectCall, EventCall, Node, Reply, Stage, Statement, TaleFile, Thread, Trigger, WeightBonus,
} from "./ast";
import { type Identifier, of, tryParse } from "./identifier";
import type { Problem } from "./problem";

const NAME = /^[a-z0-9_]+$/;
const SPEAKER_LINE = /^(\{[A-Za-z0-9_.]+\}|[A-Za-z][A-Za-z0-9_' -]*):\s*(.*)$/;
const INTERJECTION = /^\(\s*([A-Za-z]+)\s*(?::\s*([A-Za-z]+)\s*)?\)\s*(.*)$/;
const TONE_REPLY = /^([A-Za-z]+):\s*(.*)$/;
const COMPARISON = /^([A-Za-z0-9_.:]+)\s*(>=|<=|==|!=|>|<)\s*(\S+)$/;
const CALL = /^([A-Za-z0-9_.:]+)\s*\((.*)\)$/;
const BARE = /^[A-Za-z0-9_.:]+$/;
const CHANCE = /^chance\s+(\d+)\s*%$/;
const AFTER = /^after\s+(\d+)\s+times?\s*->\s*(\S+)$/;
const BRANCH = /^(success|failure)\s*->\s*(\S+)$/;
const WEIGHT_BASE = /^(\d+)$/;
const WEIGHT_BONUS = /^\+(\d+)\s+if\s+(.+)$/;
const IF_SUFFIX = /^(.*?)\s+if\s+(.+)$/;

interface ReplyBuilder extends Reply {}

interface StageBuilder {
  name: string;
  line: number;
  triggers: Trigger[];
  effects: EffectCall[];
  after: After | null;
}

/** Java's String.trim: strips characters up to and including the space. */
function jtrim(text: string): string {
  return text.replace(/^[\u0000- ]+|[\u0000- ]+$/g, "");
}

function isBlank(text: string): boolean {
  return text.trim().length === 0;
}

class Parser {
  readonly nodes: Node[] = [];
  readonly threads: Thread[] = [];

  private nodeName: string | null = null;
  private nodeLine = 0;
  private nodeStart: string[] = [];
  private nodeWeight = 1;
  private nodeBonuses: WeightBonus[] = [];
  private nodeWhen: ConditionCall[] = [];
  private nodeBody: Statement[] = [];
  private nodeTone: string | null = null;
  private reply: ReplyBuilder | null = null;

  private threadId: Identifier | null = null;
  private threadLine = 0;
  private threadOnCue = false;
  private threadRequires: ConditionCall[] = [];
  private threadStages: Stage[] = [];
  private stage: StageBuilder | null = null;

  constructor(private readonly fileId: Identifier, private readonly source: string, private readonly problems: Problem[]) {}

  line(lineNumber: number, raw: string): void {
    const withoutComment = stripComment(raw);
    const content = jtrim(withoutComment);
    if (content.length === 0) {
      return;
    }
    const indented = /\s/.test(withoutComment.charAt(0));
    if (content.startsWith("===")) {
      this.header(lineNumber, jtrim(content.substring(3)));
    } else if (this.nodeName !== null) {
      this.nodeLineIn(lineNumber, content, indented);
    } else if (this.threadId !== null) {
      this.threadLineIn(lineNumber, content, indented);
    } else {
      this.error(lineNumber, "This line is outside any node. Start one with \"=== name\".");
    }
  }

  // ---- Headers ----

  private header(lineNumber: number, rest: string): void {
    this.finishNode();
    this.finishThread();
    if (rest.startsWith("thread ") || rest === "thread") {
      const idText = jtrim(rest.substring("thread".length));
      let id = tryParse(idText.includes(":") ? idText : `${this.fileId.namespace}:${idText}`);
      if (idText.length === 0 || id === null) {
        this.error(lineNumber, "A thread needs a valid ID, like \"=== thread cim:crystal/hints_of_the_north\".");
        id = of(this.fileId.namespace, `invalid_thread_${lineNumber}`);
      }
      this.threadId = id;
      this.threadLine = lineNumber;
      this.threadOnCue = false;
      this.threadRequires = [];
      this.threadStages = [];
      return;
    }
    if (!NAME.test(rest)) {
      this.error(lineNumber, `Node names use lowercase letters, numbers, and underscores - got "${rest}".`);
    }
    this.nodeName = rest;
    this.nodeLine = lineNumber;
    this.nodeStart = [];
    this.nodeWeight = 1;
    this.nodeBonuses = [];
    this.nodeWhen = [];
    this.nodeBody = [];
    this.nodeTone = null;
  }

  // ---- Conversation nodes ----

  private nodeLineIn(lineNumber: number, content: string, indented: boolean): void {
    if (content.startsWith("when:") || content.startsWith("start:") || content.startsWith("weight:") || content.startsWith("tone:")) {
      const colon = content.indexOf(":");
      if (this.nodeBody.length > 0) {
        this.error(lineNumber, `"${content.substring(0, colon + 1)}" has to come right after the node's "===" line.`);
      }
      const rest = jtrim(content.substring(colon + 1));
      switch (content.substring(0, colon)) {
        case "when": this.nodeWhen.push(...this.conditions(lineNumber, rest)); break;
        case "start": this.startLine(lineNumber, rest); break;
        case "tone": this.toneLine(lineNumber, rest); break;
        default: this.weightLine(lineNumber, rest);
      }
      return;
    }
    if (content.startsWith("*")) {
      this.finishReply();
      this.reply = this.replyHeader(lineNumber, jtrim(content.substring(1)));
      return;
    }
    if (indented && this.reply !== null) {
      this.replyChild(lineNumber, content);
      return;
    }
    if (content.startsWith("or:")) {
      this.alternative(lineNumber, jtrim(content.substring("or:".length)));
      return;
    }
    this.finishReply();
    const statement = this.statement(lineNumber, content);
    if (statement !== null) {
      this.nodeBody.push(statement);
    }
  }

  private startLine(lineNumber: number, rest: string): void {
    for (const part of splitTopLevel(rest)) {
      const context = jtrim(part);
      if (!NAME.test(context)) {
        this.error(lineNumber, `"start:" takes lowercase names like "chatter" - got "${context}".`);
      } else {
        this.nodeStart.push(context);
      }
    }
    if (this.nodeStart.length === 0) {
      this.error(lineNumber, "\"start:\" needs at least one name, like \"start: chatter\".");
    }
  }

  private toneLine(lineNumber: number, rest: string): void {
    if (!NAME.test(rest)) {
      this.error(lineNumber, `"tone:" takes one lowercase name, like "tone: gentle" - got "${rest}".`);
    } else if (this.nodeTone !== null) {
      this.error(lineNumber, `A node has one tone - it already says "tone: ${this.nodeTone}".`);
    } else {
      this.nodeTone = rest;
    }
  }

  private weightLine(lineNumber: number, rest: string): void {
    const base = WEIGHT_BASE.exec(rest);
    const bonus = WEIGHT_BONUS.exec(rest);
    if (base) {
      this.nodeWeight = parseInt(base[1], 10);
    } else if (bonus) {
      this.nodeBonuses.push({ line: lineNumber, amount: parseInt(bonus[1], 10), conditions: this.conditions(lineNumber, bonus[2]) });
    } else {
      this.error(lineNumber, "Write it as \"weight: 2\" or \"weight: +3 if <conditions>\".");
    }
  }

  private alternative(lineNumber: number, text: string): void {
    const last = this.nodeBody[this.nodeBody.length - 1];
    if (last && last.kind === "spoken") {
      last.alternatives = [...last.alternatives, text];
    } else {
      this.error(lineNumber, "\"or:\" goes right under a spoken line, giving another way it might come out.");
    }
  }

  private statement(lineNumber: number, content: string): Statement | null {
    if (content.startsWith("~") || content.startsWith("->")) {
      const isEffect = content.startsWith("~");
      let body = jtrim(content.substring(isEffect ? 1 : 2));
      let conditions: ConditionCall[] = [];
      const suffix = IF_SUFFIX.exec(body);
      if (suffix) {
        body = jtrim(suffix[1]);
        conditions = this.conditions(lineNumber, suffix[2]);
      }
      return isEffect
        ? { kind: "effect", line: lineNumber, effect: this.effect(lineNumber, body), conditions }
        : this.jump(lineNumber, body, conditions);
    }
    const interjection = INTERJECTION.exec(content);
    if (interjection) {
      return { kind: "interjection", line: lineNumber, voice: interjection[1], tier: interjection[2] ?? "faint", text: interjection[3] };
    }
    const spoken = SPEAKER_LINE.exec(content);
    if (spoken) {
      return { kind: "spoken", line: lineNumber, speaker: jtrim(spoken[1]), text: spoken[2], alternatives: [] };
    }
    this.error(lineNumber, "I don't recognize this line. Spoken lines look like \"{pony}: text\"; "
      + "voices like \"(Kindness) text\"; replies start with \"*\"; effects with \"~\"; jumps with \"->\".");
    return null;
  }

  private jump(lineNumber: number, target: string, conditions: ConditionCall[]): Statement {
    if (target.length === 0) {
      this.error(lineNumber, "\"->\" needs somewhere to go - a node name, or END.");
    }
    return { kind: "jump", line: lineNumber, target, conditions };
  }

  private replyHeader(lineNumber: number, content: string): ReplyBuilder {
    const builder: ReplyBuilder = {
      kind: "reply", line: lineNumber, tone: null, voice: null, voiceTier: null, feeling: null, text: "", once: false,
      body: [], successTarget: null, failureTarget: null,
    };
    if (content.endsWith("!once")) {
      builder.once = true;
      content = jtrim(content.substring(0, content.length - "!once".length));
    }
    if (content.startsWith("[")) {
      const close = content.indexOf("]");
      if (close < 0) {
        this.error(lineNumber, "This voice reply is missing its closing \"]\".");
        builder.text = content;
        return builder;
      }
      const inside = content.substring(1, close);
      builder.text = jtrim(content.substring(close + 1));
      const parts = inside.split("|");
      if (parts.length > 2) {
        this.error(lineNumber, "A voice reply takes \"[Voice | Feeling]\" - only one \"|\".");
      }
      const voiceAndTier = parts[0].split(":");
      builder.voice = jtrim(voiceAndTier[0]);
      if (voiceAndTier.length > 1) {
        builder.voiceTier = jtrim(voiceAndTier[1]);
      }
      if (parts.length > 1 && !isBlank(parts[1])) {
        builder.feeling = jtrim(parts[1]);
      }
      if (builder.voice.length === 0) {
        this.error(lineNumber, "This voice reply doesn't name a voice.");
      }
      return builder;
    }
    const tone = TONE_REPLY.exec(content);
    if (tone) {
      builder.tone = tone[1];
      builder.text = tone[2];
    } else {
      this.error(lineNumber, "A reply is either a tone (\"* Friendly: text\") or a voice (\"* [Kindness | Trust] text\").");
      builder.text = content;
    }
    return builder;
  }

  private replyChild(lineNumber: number, content: string): void {
    const reply = this.reply!;
    const branch = BRANCH.exec(content);
    if (branch) {
      if (reply.tone !== null) {
        this.error(lineNumber, "Only voice replies roll, so only they take success/failure. Use \"-> target\" here.");
      }
      if (branch[1] === "success") {
        reply.successTarget = branch[2];
      } else {
        reply.failureTarget = branch[2];
      }
      return;
    }
    if (content.startsWith("->") && reply.voice !== null) {
      this.error(lineNumber, "A voice reply rolls, so it needs \"success -> target\" and \"failure -> target\" instead of a plain jump.");
      return;
    }
    if (content.startsWith("->") || content.startsWith("~")) {
      const statement = this.statement(lineNumber, content);
      if (statement !== null) {
        reply.body.push(statement);
      }
      return;
    }
    this.error(lineNumber, "Under a reply, use \"-> target\", \"success -> target\", \"failure -> target\", or an effect (\"~\").");
  }

  private finishReply(): void {
    if (this.reply !== null) {
      this.nodeBody.push(this.reply);
      this.reply = null;
    }
  }

  finishNode(): void {
    if (this.nodeName === null) {
      return;
    }
    this.finishReply();
    const id = tryParse(`${this.fileId.namespace}:${this.fileId.path}/${this.nodeName}`)
      ?? of(this.fileId.namespace, `${this.fileId.path}/invalid_node_${this.nodeLine}`);
    this.nodes.push({
      name: this.nodeName, id, line: this.nodeLine, start: this.nodeStart, weight: this.nodeWeight, bonuses: this.nodeBonuses,
      when: this.nodeWhen, body: this.nodeBody, tone: this.nodeTone,
    });
    this.nodeName = null;
  }

  // ---- Threads ----

  private threadLineIn(lineNumber: number, content: string, indented: boolean): void {
    if (content.startsWith("requires:")) {
      if (this.threadStages.length > 0) {
        this.error(lineNumber, "\"requires:\" has to come before the thread's first stage.");
      }
      this.threadRequires.push(...this.conditions(lineNumber, content.substring("requires:".length)));
      return;
    }
    if (content.startsWith("starts:")) {
      if (this.stage !== null || this.threadStages.length > 0) {
        this.error(lineNumber, "\"starts:\" has to come before the thread's first stage.");
      }
      const when = jtrim(content.substring("starts:".length)).replace(/\s+/g, " ");
      if (when === "on cue") {
        this.threadOnCue = true;
      } else if (when === "automatically") {
        this.threadOnCue = false;
      } else {
        this.error(lineNumber, "\"starts:\" is either \"on cue\" (only ~ thread start begins it) or \"automatically\" (the default).");
      }
      return;
    }
    if (!indented && content.startsWith("stage")) {
      this.finishStage();
      const name = jtrim(content.substring("stage".length));
      if (!NAME.test(name)) {
        this.error(lineNumber, `Stage names use lowercase letters, numbers, and underscores - got "${name}".`);
      }
      this.stage = { name, line: lineNumber, triggers: [], effects: [], after: null };
      return;
    }
    if (this.stage === null) {
      this.error(lineNumber, "Thread lines go inside a stage. Start one with \"stage name\".");
      return;
    }
    if (content.startsWith("on ")) {
      this.stage.triggers.push(this.trigger(lineNumber, jtrim(content.substring(3))));
    } else if (content.startsWith("after")) {
      const after = AFTER.exec(content);
      if (after) {
        if (this.stage.after !== null) {
          this.error(lineNumber, "A stage can only have one \"after N times\".");
        }
        this.stage.after = { line: lineNumber, times: parseInt(after[1], 10), target: after[2] };
      } else {
        this.error(lineNumber, "Write it as \"after N times -> stage\".");
      }
    } else if (content.startsWith("~")) {
      this.stage.effects.push(this.effect(lineNumber, jtrim(content.substring(1))));
    } else {
      this.error(lineNumber, "Inside a stage, use \"on <event>\", \"after N times -> stage\", or an effect (\"~\").");
    }
  }

  private trigger(lineNumber: number, content: string): Trigger {
    let target: string | null = null;
    const arrow = content.indexOf("->");
    if (arrow >= 0) {
      target = jtrim(content.substring(arrow + 2));
      content = jtrim(content.substring(0, arrow));
    }
    const parts = splitTopLevel(content);
    let event = parts.length === 0 ? null : this.event(lineNumber, parts[0]);
    let chance = 100;
    for (const extra of parts.slice(Math.min(1, parts.length))) {
      const chanceMatch = CHANCE.exec(jtrim(extra));
      if (chanceMatch) {
        chance = parseInt(chanceMatch[1], 10);
      } else {
        this.error(lineNumber, `After the event, only "chance N%" is allowed - got "${jtrim(extra)}".`);
      }
    }
    if (event === null) {
      this.error(lineNumber, "\"on\" needs an event, like \"on sleep\".");
      event = { line: lineNumber, type: of("cim", "missing"), args: [], source: "" };
    }
    return { line: lineNumber, event, chancePercent: chance, target };
  }

  private finishStage(): void {
    if (this.stage !== null) {
      this.threadStages.push({ ...this.stage });
      this.stage = null;
    }
  }

  finishThread(): void {
    if (this.threadId === null) {
      return;
    }
    this.finishStage();
    if (this.threadStages.length === 0) {
      this.error(this.threadLine, "This thread has no stages.");
    }
    this.threads.push({
      id: this.threadId, line: this.threadLine, onCue: this.threadOnCue, requires: this.threadRequires, stages: this.threadStages,
    });
    this.threadId = null;
  }

  // ---- Calls ----

  private conditions(lineNumber: number, text: string): ConditionCall[] {
    const result: ConditionCall[] = [];
    for (const part of splitTopLevel(text)) {
      const condition = this.condition(lineNumber, jtrim(part));
      if (condition !== null) {
        result.push(condition);
      }
    }
    if (result.length === 0) {
      this.error(lineNumber, "This line expects at least one condition.");
    }
    return result;
  }

  private condition(lineNumber: number, text: string): ConditionCall | null {
    let negated = false;
    let body = text;
    if (body.startsWith("not ")) {
      negated = true;
      body = jtrim(body.substring(4));
    }
    const comparison = COMPARISON.exec(body);
    if (comparison) {
      const type = this.typeId(lineNumber, comparison[1]);
      return type === null ? null : { line: lineNumber, type, args: [comparison[3]], comparison: comparison[2], negated, source: text };
    }
    const call = CALL.exec(body);
    if (call) {
      const type = this.typeId(lineNumber, call[1]);
      return type === null ? null : { line: lineNumber, type, args: args(call[2]), comparison: null, negated, source: text };
    }
    if (BARE.test(body)) {
      const type = this.typeId(lineNumber, body);
      return type === null ? null : { line: lineNumber, type, args: [], comparison: null, negated, source: text };
    }
    this.error(lineNumber, `I can't read the condition "${text}". Conditions look like "night", "mood(grief)", or "trust >= medium".`);
    return null;
  }

  private effect(lineNumber: number, text: string): EffectCall {
    const space = text.indexOf(" ");
    const name = space < 0 ? text : text.substring(0, space);
    const rest = space < 0 ? "" : jtrim(text.substring(space + 1));
    const type = this.typeId(lineNumber, name) ?? of("cim", "invalid");
    return { line: lineNumber, type, args: rest.length === 0 ? [] : args(rest), source: text };
  }

  private event(lineNumber: number, text: string): EventCall | null {
    text = jtrim(text);
    const call = CALL.exec(text);
    if (call) {
      const type = this.typeId(lineNumber, call[1]);
      return type === null ? null : { line: lineNumber, type, args: args(call[2]), source: text };
    }
    if (BARE.test(text)) {
      const type = this.typeId(lineNumber, text);
      return type === null ? null : { line: lineNumber, type, args: [], source: text };
    }
    this.error(lineNumber, `I can't read the event "${text}". Events look like "sleep" or "enter_biome(snowy)".`);
    return null;
  }

  private typeId(lineNumber: number, name: string): Identifier | null {
    const id = tryParse(name.includes(":") ? name : `cim:${name}`);
    if (id === null) {
      this.error(lineNumber, `"${name}" isn't a valid name - use lowercase letters, numbers, "_", ".", and one ":".`);
    }
    return id;
  }

  private error(lineNumber: number, message: string): void {
    this.problems.push({ severity: "error", source: this.source, line: lineNumber, message });
  }
}

function args(text: string): string[] {
  return splitTopLevel(text).map(jtrim);
}

/** Splits on commas that aren't inside parentheses. */
export function splitTopLevel(text: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let start = 0;
  for (let i = 0; i < text.length; i++) {
    const c = text.charAt(i);
    if (c === "(") {
      depth++;
    } else if (c === ")") {
      depth = Math.max(0, depth - 1);
    } else if (c === "," && depth === 0) {
      parts.push(text.substring(start, i));
      start = i + 1;
    }
  }
  const last = text.substring(start);
  if (!isBlank(last) || parts.length > 0) {
    parts.push(last);
  }
  return parts.filter((part) => !isBlank(part));
}

function stripComment(line: string): string {
  const comment = line.indexOf("//");
  return comment < 0 ? line : line.substring(0, comment);
}

/** Parses one file's text; problems are appended to {@code problems}. */
export function parse(fileId: Identifier, source: string, text: string, problems: Problem[]): TaleFile {
  const parser = new Parser(fileId, source, problems);
  const lines = text.split(/\r?\n/);
  lines.forEach((line, i) => parser.line(i + 1, line));
  parser.finishNode();
  parser.finishThread();
  return { id: fileId, source, nodes: parser.nodes, threads: parser.threads };
}
