// Checks parsed .tale files against each other and the vocabulary - a line-for-line port of the
// game's TaleValidator.java, messages included.

import { type ConditionCall, type EffectCall, type Node, type Reply, type Statement, type TaleFile, type Thread, type Trigger, jumpIsConditional, texts } from "./ast";
import { type Identifier, idString, tryParse } from "./identifier";
import type { Problem } from "./problem";
import { type Vocabulary, checkCall, isFeeling, isTone, isVoice, normalize, voiceTakesFeeling } from "./vocabulary";

export const END = "END";
const VARIABLE = /\{([A-Za-z0-9_.]+)\}/g;

/** A jump target written in {@code file}: a local node name, or a full node ID. */
export function resolveTarget(file: TaleFile, target: string): Identifier | null {
  if (target.includes(":")) {
    return tryParse(target);
  }
  return tryParse(`${file.id.namespace}:${file.id.path}/${target}`);
}

function threadKey(written: string): string {
  return written.includes(":") ? written : `cim:${written}`;
}

class Validator {
  private readonly nodesById = new Map<string, Node>();
  private readonly threadsById = new Map<string, Thread>();
  private readonly jumpedTo = new Set<string>();
  private readonly advancedTo = new Set<string>();
  private readonly started = new Set<string>();

  constructor(private readonly files: TaleFile[], private readonly vocab: Vocabulary, private readonly problems: Problem[]) {}

  run(): void {
    for (const file of this.files) {
      for (const node of file.nodes) {
        const key = idString(node.id);
        const previous = this.nodesById.get(key);
        if (previous) {
          this.error(file, node.line, `There's already a node called "${node.name}" in this file (line ${previous.line}).`);
        } else {
          this.nodesById.set(key, node);
        }
      }
      for (const thread of file.threads) {
        const key = idString(thread.id);
        if (this.threadsById.has(key)) {
          this.error(file, thread.line, `Another file already defines the thread ${key}.`);
        } else {
          this.threadsById.set(key, thread);
        }
      }
    }
    for (const file of this.files) {
      file.nodes.forEach((node) => this.checkNode(file, node));
      file.threads.forEach((thread) => this.checkThread(file, thread));
    }
    this.files.forEach((file) => this.warnUnreached(file));
  }

  // ---- Nodes ----

  private checkNode(file: TaleFile, node: Node): void {
    for (const context of node.start) {
      if (!this.vocab.startContexts.includes(normalize(context))) {
        this.error(file, node.line, `Nothing opens conversations at "start: ${context}". Known: ${this.vocab.startContexts.join(", ")}.`);
      }
    }
    const leadIn = node.start.find((context) => (this.vocab.leadInContexts ?? []).includes(normalize(context)));
    if (leadIn !== undefined) {
      for (const statement of node.body) {
        if (statement.kind === "reply" || statement.kind === "jump") {
          this.warning(file, statement.line, `A "start: ${leadIn}" node only speaks - replies and jumps here are skipped.`);
        }
      }
    }
    if (node.start.length === 0 && (node.weight !== 1 || node.bonuses.length > 0)) {
      this.warning(file, node.line, "\"weight:\" only matters on a node with \"start:\".");
    }
    if (node.start.length === 0 && node.tone !== null) {
      this.warning(file, node.line, "\"tone:\" only matters on a node with \"start:\".");
    }
    node.bonuses.forEach((bonus) => bonus.conditions.forEach((condition) => this.checkCondition(file, condition)));
    node.when.forEach((condition) => this.checkCondition(file, condition));
    node.body.forEach((statement) => this.checkStatement(file, statement));
    for (let i = 0; i < node.body.length - 1; i++) {
      const statement = node.body[i];
      if (statement.kind === "jump" && !jumpIsConditional(statement)) {
        this.warning(file, node.body[i + 1].line, `Nothing after the jump on line ${statement.line} will ever play.`);
        break;
      }
    }
  }

  private checkStatement(file: TaleFile, statement: Statement): void {
    switch (statement.kind) {
      case "jump":
        this.checkJump(file, statement.line, statement.target);
        statement.conditions.forEach((condition) => this.checkCondition(file, condition));
        break;
      case "effect":
        this.checkEffect(file, statement.effect);
        statement.conditions.forEach((condition) => this.checkCondition(file, condition));
        break;
      case "interjection":
        this.checkVoice(file, statement.line, statement.voice);
        this.checkTier(file, statement.line, statement.tier);
        this.checkVariables(file, statement.line, statement.text);
        break;
      case "spoken":
        this.checkVariables(file, statement.line, statement.speaker);
        texts(statement).forEach((text) => this.checkVariables(file, statement.line, text));
        break;
      case "reply":
        this.checkReply(file, statement);
        break;
    }
  }

  private checkReply(file: TaleFile, reply: Reply): void {
    if (reply.tone !== null && !isTone(this.vocab, reply.tone)) {
      this.error(file, reply.line, `Unknown tone "${reply.tone}". Tones: ${this.vocab.tones.join(", ")}.`);
    }
    reply.body.forEach((statement) => this.checkStatement(file, statement));
    this.checkVariables(file, reply.line, reply.text);
    if (reply.text.trim().length === 0 && reply.voice !== null) {
      this.error(file, reply.line, "A voice reply needs text - only tone replies can fall back to showing the tone.");
    }
    if (reply.voice !== null) {
      const voice = reply.voice;
      this.checkVoice(file, reply.line, voice);
      if (reply.voiceTier !== null) {
        this.checkTier(file, reply.line, reply.voiceTier);
      }
      if (reply.feeling !== null) {
        if (!voiceTakesFeeling(this.vocab, voice)) {
          this.error(file, reply.line, `A ${voice} reply has no feeling - it doesn't rely on the pony's warmth toward you.`);
        } else if (!isFeeling(this.vocab, reply.feeling)) {
          this.error(file, reply.line, `Unknown feeling "${reply.feeling}". Feelings: ${this.vocab.feelings.join(", ")}.`);
        }
      }
      if (reply.successTarget === null) {
        this.error(file, reply.line, "This voice reply needs a \"success -> target\" line under it.");
      }
      if (reply.failureTarget === null) {
        this.error(file, reply.line, "This voice reply needs a \"failure -> target\" line under it. Failure moves the story forward.");
      }
      if (reply.successTarget !== null) {
        this.checkJump(file, reply.line, reply.successTarget);
      }
      if (reply.failureTarget !== null) {
        this.checkJump(file, reply.line, reply.failureTarget);
      }
    } else if (!reply.body.some((statement) => statement.kind === "jump" && !jumpIsConditional(statement))) {
      this.warning(file, reply.line, "This reply has no \"-> target\", so choosing it ends the conversation. Add \"-> END\" if that's intended.");
    }
  }

  private checkJump(file: TaleFile, line: number, target: string): void {
    if (target === END) {
      return;
    }
    const id = resolveTarget(file, target);
    if (id === null || !this.nodesById.has(idString(id))) {
      this.error(file, line, `There's no node "${target}" to jump to.${target.toUpperCase() === END ? " (END is written in capitals.)" : ""}`);
      return;
    }
    this.jumpedTo.add(idString(id));
  }

  private checkVoice(file: TaleFile, line: number, voice: string): void {
    if (!isVoice(this.vocab, voice)) {
      this.error(file, line, `Unknown voice "${voice}". Voices: ${this.vocab.voices.join(", ")}.`);
    }
  }

  private checkTier(file: TaleFile, line: number, tier: string): void {
    if (!this.vocab.tiers.includes(normalize(tier))) {
      this.error(file, line, `Unknown tier "${tier}". Tiers: ${this.vocab.tiers.join(", ")}.`);
    }
  }

  // ---- Calls ----

  private checkCondition(file: TaleFile, condition: ConditionCall): void {
    const spec = this.vocab.conditions[idString(condition.type)];
    if (!spec) {
      this.error(file, condition.line, `No mod or datapack has registered a condition called ${idString(condition.type)}.`);
      return;
    }
    condition.args.forEach((arg) => this.checkVariables(file, condition.line, arg));
    this.report(file, condition.line, `"${condition.source}"`, checkCall(spec, condition.args, condition.comparison));
  }

  private checkEffect(file: TaleFile, effect: EffectCall): void {
    const spec = this.vocab.effects[idString(effect.type)];
    if (!spec) {
      this.error(file, effect.line, `No mod or datapack has registered an effect called ${idString(effect.type)}.`);
      return;
    }
    effect.args.forEach((arg) => this.checkVariables(file, effect.line, arg));
    this.report(file, effect.line, `"~ ${effect.source}"`, checkCall(spec, effect.args, null));
    if (idString(effect.type) === "cim:thread" && effect.args.length > 0) {
      const words = effect.args[0].trim().split(/\s+/);
      if (words.length === 3 && words[0] === "advance") {
        this.advancedTo.add(`${threadKey(words[1])} ${words[2]}`);
      } else if (words.length === 2 && words[0] === "start") {
        this.started.add(threadKey(words[1]));
      }
    }
  }

  private checkVariables(file: TaleFile, line: number, text: string): void {
    for (const match of text.matchAll(VARIABLE)) {
      if (!this.vocab.variables.includes(normalize(match[1]))) {
        this.error(file, line, `Unknown variable {${match[1]}}. Variables: ${this.vocab.variables.map((name) => `{${name}}`).join(", ")}.`);
      }
    }
  }

  private report(file: TaleFile, line: number, what: string, messages: string[]): void {
    messages.forEach((message) => this.error(file, line, `${what}: ${message}`));
  }

  // ---- Threads ----

  private checkThread(file: TaleFile, thread: Thread): void {
    thread.requires.forEach((condition) => this.checkCondition(file, condition));
    const stageNames = new Set<string>();
    for (const stage of thread.stages) {
      if (stageNames.has(stage.name)) {
        this.error(file, stage.line, `This thread already has a stage called "${stage.name}".`);
      }
      stageNames.add(stage.name);
    }
    for (const stage of thread.stages) {
      if (stage.triggers.length === 0) {
        this.warning(file, stage.line, `Stage "${stage.name}" has no "on" lines, so nothing in it will ever happen on its own.`);
      }
      for (const trigger of stage.triggers) {
        this.checkEvent(file, trigger);
        if (trigger.chancePercent < 1 || trigger.chancePercent > 100) {
          this.error(file, trigger.line, "Chance has to be between 1% and 100%.");
        }
        if (trigger.target !== null) {
          this.checkStage(file, thread, stageNames, trigger.line, trigger.target);
        }
      }
      stage.effects.forEach((effect) => this.checkEffect(file, effect));
      if (stage.after !== null) {
        if (stage.after.times < 1) {
          this.error(file, stage.after.line, "\"after\" needs at least 1 time.");
        }
        this.checkStage(file, thread, stageNames, stage.after.line, stage.after.target);
      }
    }
  }

  private checkEvent(file: TaleFile, trigger: Trigger): void {
    const spec = this.vocab.events[idString(trigger.event.type)];
    if (!spec) {
      this.error(file, trigger.line, `No mod or datapack has registered an event called ${idString(trigger.event.type)}.`);
      return;
    }
    this.report(file, trigger.line, `"on ${trigger.event.source}"`, checkCall(spec, trigger.event.args, null));
  }

  private checkStage(file: TaleFile, thread: Thread, stageNames: Set<string>, line: number, target: string): void {
    if (!stageNames.has(target) && target !== END) {
      this.error(file, line, `Thread ${idString(thread.id)} has no stage "${target}".${target.toUpperCase() === END ? " (END is written in capitals.)" : ""}`);
    }
  }

  // ---- Reachability ----

  private warnUnreached(file: TaleFile): void {
    for (const node of file.nodes) {
      if (node.start.length === 0 && !this.jumpedTo.has(idString(node.id))) {
        this.warning(file, node.line, `Nothing jumps to "${node.name}" and it has no "start:", so it will only play from /cim debug dialogue test.`);
      }
    }
    for (const thread of file.threads) {
      const reached = new Set<string>();
      for (const stage of thread.stages) {
        stage.triggers.forEach((trigger) => trigger.target !== null && reached.add(trigger.target));
        if (stage.after !== null) {
          reached.add(stage.after.target);
        }
      }
      const id = idString(thread.id);
      if (thread.onCue && !this.started.has(id)) {
        this.warning(file, thread.line, `Thread ${id} starts on cue, but nothing says "~ thread start ${id}" - only /cim debug thread start will begin it.`);
      }
      for (let i = 1; i < thread.stages.length; i++) {
        const stage = thread.stages[i];
        if (!reached.has(stage.name) && !this.advancedTo.has(`${id} ${stage.name}`)) {
          this.warning(file, stage.line, `Nothing moves thread ${id} to stage "${stage.name}".`);
        }
      }
    }
  }

  private error(file: TaleFile, line: number, message: string): void {
    this.problems.push({ severity: "error", source: file.source, line, message });
  }

  private warning(file: TaleFile, line: number, message: string): void {
    this.problems.push({ severity: "warning", source: file.source, line, message });
  }
}

/** Validates all files together; problems are appended to {@code problems}. */
export function validate(files: TaleFile[], vocab: Vocabulary, problems: Problem[]): void {
  new Validator(files, vocab, problems).run();
}
