// Conditions and effects as editable pieces: read one from script text, change its arguments, write
// it back - in exactly the shapes the parser accepts.

import { splitTopLevel } from "../tale/parser";
import type { CallSpec, FormSpec, TokenSpec, Vocabulary } from "../tale/vocabulary";

export interface Call {
  negated: boolean;
  name: string;
  args: string[];
  comparison: string | null;
}

const COMPARISON = /^([A-Za-z0-9_.:]+)\s*(>=|<=|==|!=|>|<)\s*(\S+)$/;
const CALL = /^([A-Za-z0-9_.:]+)\s*\((.*)\)$/;
export const COMPARISONS = [">=", "<=", "==", "!=", ">", "<"];

export function parseCondition(text: string): Call {
  let body = text.trim();
  let negated = false;
  if (body.startsWith("not ")) {
    negated = true;
    body = body.substring(4).trim();
  }
  const comparison = COMPARISON.exec(body);
  if (comparison) {
    return { negated, name: comparison[1], args: [comparison[3]], comparison: comparison[2] };
  }
  const call = CALL.exec(body);
  if (call) {
    return { negated, name: call[1], args: splitTopLevel(call[2]).map((arg) => arg.trim()), comparison: null };
  }
  return { negated, name: body, args: [], comparison: null };
}

export function formatCondition(call: Call): string {
  const prefix = call.negated ? "not " : "";
  if (call.comparison !== null) {
    return `${prefix}${call.name} ${call.comparison} ${call.args[0] ?? ""}`.trimEnd();
  }
  return call.args.length === 0 ? `${prefix}${call.name}` : `${prefix}${call.name}(${call.args.join(", ")})`;
}

/** "fondness +5" or "memory cim:gift, joy clear" - the part after "~". */
export function parseEffect(body: string): Call {
  const text = body.trim();
  const space = text.indexOf(" ");
  const name = space < 0 ? text : text.substring(0, space);
  const rest = space < 0 ? "" : text.substring(space + 1).trim();
  return { negated: false, name, args: rest.length === 0 ? [] : splitTopLevel(rest).map((arg) => arg.trim()), comparison: null };
}

export function formatEffect(call: Call): string {
  return call.args.length === 0 ? call.name : `${call.name} ${call.args.join(", ")}`;
}

/** Splits "-> target if a, b" style lines into the part before "if" and its conditions. */
export function splitIf(text: string): { body: string; conditions: string[] } {
  const match = /^(.*?)\s+if\s+(.+)$/.exec(text);
  return match ? { body: match[1].trim(), conditions: splitTopLevel(match[2]).map((c) => c.trim()) } : { body: text.trim(), conditions: [] };
}

export function joinIf(body: string, conditions: string[]): string {
  return conditions.length === 0 ? body : `${body} if ${conditions.join(", ")}`;
}

export function specFor(specs: Record<string, CallSpec>, name: string): CallSpec | undefined {
  return specs[name.includes(":") ? name : `cim:${name}`];
}

/** The shortest name a call can be written with - "cim:" is assumed. */
export function shortName(id: string): string {
  return id.startsWith("cim:") ? id.substring(4) : id;
}

/** The form {@code args} are written in; falls back to the first. */
export function pickForm(spec: CallSpec, args: string[]): FormSpec | null {
  const forms = spec.forms ?? [];
  const firstWord = (args[0] ?? "").split(/\s+/)[0]?.toLowerCase();
  return forms.find((form) => {
    const token = form.args[0]?.tokens[0];
    return token?.kind === "literal" && token.values?.includes(firstWord);
  }) ?? forms[0] ?? null;
}

export function defaultToken(token: TokenSpec): string {
  switch (token.kind) {
    case "word":
    case "literal":
      return token.values?.[0] ?? token.label;
    case "scoped_id":
      return `${token.values?.[0] ?? ""}something`;
    case "signed_number":
      return "+1";
    case "name":
      return "stage";
    default:
      return "something";
  }
}

/** Arguments that make a valid call in {@code form}: every required token filled with its first choice. */
export function defaultArgs(form: FormSpec | null): string[] {
  if (!form) return [];
  return form.args.filter((arg) => !arg.optional)
    .map((arg) => arg.tokens.filter((token) => !token.optional).map(defaultToken).join(" "));
}

export function defaultCondition(vocab: Vocabulary, id: string): string {
  const spec = vocab.conditions[id];
  const name = shortName(id);
  const form = spec?.forms?.[0] ?? null;
  const args = defaultArgs(form);
  return formatCondition({ negated: false, name, args, comparison: spec?.comparison ? ">=" : null });
}

export function defaultEffect(vocab: Vocabulary, id: string): string {
  return formatEffect({ negated: false, name: shortName(id), args: defaultArgs(vocab.effects[id]?.forms?.[0] ?? null), comparison: null });
}
