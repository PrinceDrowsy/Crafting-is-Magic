// Everything a .tale file may say, as exported by the game (`/cim debug tale export`, written to
// cim-vocabulary.json in the game folder). Argument checks run the same declarative specs the game
// uses (ArgSpec.java), so the editor and the game agree on what's valid.

export type TokenKind = "word" | "literal" | "id" | "scoped_id" | "id_or_tag" | "signed_number" | "name";

export interface TokenSpec {
  kind: TokenKind;
  label: string;
  optional: boolean;
  values?: string[];
}

export interface ArgSpecArg {
  optional: boolean;
  tokens: TokenSpec[];
}

export interface FormSpec {
  repeatLast: boolean;
  args: ArgSpecArg[];
}

export interface CallSpec {
  custom?: boolean;
  usage?: string;
  comparison?: boolean;
  forms?: FormSpec[];
}

export interface Vocabulary {
  format: number;
  conditions: Record<string, CallSpec>;
  effects: Record<string, CallSpec>;
  events: Record<string, CallSpec>;
  variables: string[];
  voices: string[];
  feelinglessVoices: string[];
  tones: string[];
  feelings: string[];
  tiers: string[];
  startContexts: string[];
  /** Start contexts that only lead in to another conversation, like greetings (older exports lack it). */
  leadInContexts?: string[];
}

export function normalize(word: string): string {
  return word.trim().toLowerCase();
}

export function isVoice(vocab: Vocabulary, voice: string): boolean {
  return vocab.voices.includes(normalize(voice));
}

export function voiceTakesFeeling(vocab: Vocabulary, voice: string): boolean {
  return !vocab.feelinglessVoices.includes(normalize(voice));
}

export function isTone(vocab: Vocabulary, tone: string): boolean {
  return vocab.tones.includes(normalize(tone));
}

export function isFeeling(vocab: Vocabulary, feeling: string): boolean {
  return vocab.feelings.includes(normalize(feeling));
}

export function tierIndex(vocab: Vocabulary, tier: string): number {
  return vocab.tiers.indexOf(normalize(tier));
}

// ---- Argument checks: a port of ArgSpec.check, messages included ----

/** IDs may contain {variables}, filled in at run time. */
function withoutVariables(text: string): string {
  return text.trim().replace(/\{[A-Za-z0-9_.]+\}/g, "x");
}

const NAMESPACE = /^[a-z0-9_.-]+$/;
const PATH = /^[a-z0-9/._-]*$/;

function validId(text: string): boolean {
  const filled = withoutVariables(text);
  if (filled.length === 0) {
    return false;
  }
  const full = filled.includes(":") ? filled : `cim:${filled}`;
  const colon = full.indexOf(":");
  return NAMESPACE.test(full.substring(0, colon)) && PATH.test(full.substring(colon + 1));
}

function badId(text: string): string {
  return `"${text.trim()}" isn't a valid ID - lowercase letters, numbers, "_", "/", ".", and one ":"`;
}

function checkToken(token: TokenSpec, word: string): string | null {
  const lower = word.toLowerCase();
  const values = token.values ?? [];
  switch (token.kind) {
    case "word":
    case "literal":
      return values.includes(lower) ? null : `"${word}" isn't a ${token.label} - one of: ${values.join(", ")}`;
    case "id":
      return validId(word) ? null : badId(word);
    case "id_or_tag":
      return validId(word.startsWith("#") ? word.substring(1) : word) ? null : badId(word);
    case "scoped_id": {
      if (!validId(word)) {
        return badId(word);
      }
      const filled = withoutVariables(word);
      const path = filled.includes(":") ? filled.substring(filled.indexOf(":") + 1) : filled;
      return values.some((scope) => path.startsWith(scope)) ? null
        : `a ${token.label}'s path starts with ${values.join(", ")} - that's where it's kept`;
    }
    case "signed_number":
      return /^[+-]\d+$/.test(word) ? null : `write the change as +N or -N, not "${word}"`;
    case "name":
      return /^[a-z0-9_]+$/.test(lower) ? null : `"${word}" should be a single lowercase word`;
  }
}

function checkArg(spec: ArgSpecArg, text: string): string[] {
  const trimmed = text.trim();
  const words = trimmed.length === 0 ? [] : trimmed.split(/\s+/);
  const required = spec.tokens.filter((token) => !token.optional).length;
  if (words.length < required || words.length > spec.tokens.length) {
    const shape = spec.tokens.map((token) => token.optional ? `[${token.label}]` : `<${token.label}>`).join(" ");
    return [`"${trimmed}" should be ${shape}`];
  }
  const problems: string[] = [];
  words.forEach((word, i) => {
    const problem = checkToken(spec.tokens[i], word);
    if (problem !== null) {
      problems.push(problem);
    }
  });
  return problems;
}

function countMessage(required: number, total: number, repeats: boolean): string {
  if (total === 0) {
    return "takes no arguments";
  }
  if (repeats) {
    return `takes at least ${required} argument(s)`;
  }
  return required === total ? `takes ${total} argument(s)` : `takes ${required} to ${total} arguments`;
}

function checkForm(form: FormSpec, args: string[]): string[] {
  const specs = form.args;
  const required = specs.filter((arg) => !arg.optional).length;
  const repeats = form.repeatLast && specs.length > 0;
  if (args.length < required || (!repeats && args.length > specs.length)) {
    return [countMessage(required, specs.length, repeats)];
  }
  const problems: string[] = [];
  args.forEach((arg, i) => problems.push(...checkArg(specs[Math.min(i, specs.length - 1)], arg)));
  return problems;
}

/** Checks one call's arguments against its spec; a message per problem, empty when fine. */
export function checkCall(spec: CallSpec, args: string[], comparison: string | null): string[] {
  if (spec.custom || !spec.forms) {
    return []; // registered without a spec: the game checks it, the editor can't
  }
  const usage = spec.usage ?? "";
  if (spec.comparison && comparison === null) {
    return [`write it as a comparison: ${usage}`];
  }
  if (!spec.comparison && comparison !== null) {
    return [`can't be compared with ${comparison} - write it as ${usage}`];
  }
  let first: string[] | null = null;
  for (const form of spec.forms) {
    const problems = checkForm(form, args);
    if (problems.length === 0) {
      return [];
    }
    first ??= problems;
  }
  if (spec.forms.length > 1) {
    return [`write it as ${usage}`];
  }
  return first ?? [];
}
