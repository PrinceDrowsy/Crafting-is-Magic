// The blanks on a block: dropdowns for anything with a fixed set of choices, inline text for the rest.
// Conditions and effects build theirs from the game's argument specs, so every call gets the right
// fields without the editor knowing about it.

import type { CallSpec, TokenSpec } from "../tale/vocabulary";
import { COMPARISONS, type Call, defaultArgs, pickForm } from "./calls";

/** A dropdown. The current value stays selectable even when it isn't one of the choices. */
export function choice(values: string[], current: string, onChange: (value: string) => void,
                       options: { optional?: boolean; labels?: (value: string) => string; title?: string } = {}): HTMLSelectElement {
  const select = document.createElement("select");
  select.className = "slot";
  if (options.title) select.title = options.title;
  const all = [...(options.optional ? [""] : []), ...values];
  if (!all.includes(current)) all.push(current);
  for (const value of all) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = value === "" ? "—" : options.labels?.(value) ?? value;
    option.selected = value === current;
    select.append(option);
  }
  select.onchange = () => onChange(select.value);
  return select;
}

/** Inline text that grows as you type; commits on Enter or when you click away, Escape undoes. */
export function text(value: string, onCommit: (value: string) => void,
                     options: { placeholder?: string; className?: string; title?: string } = {}): HTMLElement {
  const field = document.createElement("span");
  field.className = `slot slot-text${options.className ? ` ${options.className}` : ""}`;
  field.contentEditable = "plaintext-only";
  field.spellcheck = true;
  field.textContent = value;
  field.dataset.placeholder = options.placeholder ?? "";
  if (options.title) field.title = options.title;
  field.onkeydown = (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      field.blur();
    } else if (event.key === "Escape") {
      field.textContent = value;
      field.blur();
    }
    event.stopPropagation(); // keep typing out of the editor's shortcuts
  };
  field.onblur = () => {
    const next = (field.textContent ?? "").replace(/\s*\n\s*/g, " ").trim();
    if (next !== value.trim()) onCommit(next);
  };
  return field;
}

function tokenSlot(token: TokenSpec, word: string, optional: boolean, onChange: (word: string) => void): HTMLElement {
  if ((token.kind === "word" || token.kind === "literal") && token.values && token.values.length > 0) {
    if (token.kind === "literal" && token.values.length === 1) {
      const fixed = document.createElement("span");
      fixed.className = "slot-fixed";
      fixed.textContent = token.values[0];
      return fixed;
    }
    return choice(token.values, word, onChange, { optional, title: token.label });
  }
  const placeholder = token.kind === "scoped_id" ? `${token.values?.[0] ?? ""}${token.label}` : token.label;
  return text(word, (next) => {
    if (token.kind === "signed_number" && /^\d+$/.test(next)) next = `+${next}`;
    onChange(next.replace(/\s+/g, "_"));
  }, { placeholder: optional ? `${placeholder}?` : placeholder, className: token.kind === "signed_number" ? "slot-number" : "", title: token.label });
}

/** The fields for one condition or effect call; {@code onChange} gets the edited call. */
export function callSlots(spec: CallSpec | undefined, call: Call, onChange: (call: Call) => void): HTMLElement[] {
  const slots: HTMLElement[] = [];
  const emit = (patch: Partial<Call>) => onChange({ ...call, ...patch });
  if (!spec || spec.custom || !spec.forms || spec.forms.length === 0) {
    // No spec to go by: the arguments as one free text field.
    slots.push(text(call.args.join(", "), (next) => emit({ args: next ? next.split(/\s*,\s*/) : [] }), { placeholder: "arguments" }));
    return slots;
  }
  if (spec.comparison) {
    slots.push(choice(COMPARISONS, call.comparison ?? ">=", (op) => emit({ comparison: op }), { title: "comparison" }));
  }
  const form = pickForm(spec, call.args)!;
  if (spec.forms.length > 1) {
    // Forms are told apart by their first word ("thread start" vs "thread advance").
    const firsts = spec.forms.map((f) => f.args[0]?.tokens[0]?.values?.[0] ?? "");
    const current = form.args[0]?.tokens[0]?.values?.[0] ?? "";
    slots.push(choice(firsts, current, (word) => {
      const next = spec.forms!.find((f) => f.args[0]?.tokens[0]?.values?.[0] === word) ?? null;
      emit({ args: defaultArgs(next) });
    }, { title: "kind" }));
  }
  const count = form.repeatLast ? Math.max(form.args.length, call.args.length) : form.args.length;
  const required = form.args.filter((arg) => !arg.optional).length;
  const args = [...call.args];
  for (let i = 0; i < count; i++) {
    const argSpec = form.args[Math.min(i, form.args.length - 1)];
    if (!argSpec) break;
    const words = (args[i] ?? "").split(/\s+/).filter(Boolean);
    argSpec.tokens.forEach((token, t) => {
      if (spec.forms!.length > 1 && i === 0 && t === 0) return; // already shown as the form choice
      slots.push(tokenSlot(token, words[t] ?? "", argSpec.optional || token.optional, (word) => {
        const nextWords = [...words];
        nextWords[t] = word;
        const nextArgs = [...args];
        nextArgs[i] = nextWords.filter(Boolean).join(" ");
        while (nextArgs.length > 0 && nextArgs[nextArgs.length - 1] === "") nextArgs.pop(); // drop emptied optional ends
        emit({ args: nextArgs });
      }));
    });
    if (form.repeatLast && i >= form.args.length - 1 && count > Math.max(1, required)) {
      // repeated arguments can be taken away again, down to what's required
      const remove = document.createElement("button");
      remove.className = "slot-remove";
      remove.textContent = "×";
      remove.title = "Remove this one";
      remove.onclick = () => emit({ args: args.filter((_, index) => index !== i) });
      slots.push(remove);
    }
  }
  if (form.repeatLast) {
    const add = document.createElement("button");
    add.className = "slot-add";
    add.textContent = "+";
    add.title = "Add another";
    const last = form.args[form.args.length - 1];
    add.onclick = () => emit({ args: [...args, last ? defaultArgs({ repeatLast: false, args: [{ ...last, optional: false }] })[0] ?? "" : ""] });
    slots.push(add);
  }
  return slots;
}
