// Syntax highlighting for .tale files - a line-oriented tokenizer, since the format itself is
// line-oriented (docs/narrative-reference.md). Colors come from the Mane Six palette.

import { HighlightStyle, StreamLanguage, type StringStream, syntaxHighlighting } from "@codemirror/language";
import { tags } from "@lezer/highlight";

interface State {
  /** What kind of line we're in, decided at its first token. */
  line: "none" | "spoken" | "voice" | "reply" | "header" | "keyword" | "effect" | "jump";
}

const KEYWORD_LINE = /^(when|start|weight|requires|starts|or):/;
const STAGE_WORDS = /^(stage|on|after|success|failure)\b/;

export const taleLanguage = StreamLanguage.define<State>({
  name: "tale",
  startState: () => ({ line: "none" }),
  token(stream: StringStream, state: State): string | null {
    if (stream.sol()) {
      state.line = "none";
    }
    if (stream.eatSpace()) {
      return null;
    }
    if (stream.match("//")) {
      stream.skipToEnd();
      return "comment";
    }
    if (stream.match(/^\{[A-Za-z0-9_.]+\}/)) {
      return "variableName";
    }
    if (state.line === "none") {
      if (stream.match("===")) {
        state.line = "header";
        return "heading";
      }
      if (stream.match(KEYWORD_LINE)) {
        state.line = "keyword";
        return "keyword";
      }
      if (stream.match(STAGE_WORDS)) {
        state.line = "keyword";
        return "keyword";
      }
      if (stream.match("*")) {
        state.line = "reply";
        return "operator";
      }
      if (stream.match("~")) {
        state.line = "effect";
        return "operator";
      }
      if (stream.match("->")) {
        state.line = "jump";
        return "operator";
      }
      if (stream.match(/^\(\s*[A-Za-z]+\s*(:\s*[A-Za-z]+\s*)?\)/)) {
        state.line = "voice";
        return "atom";
      }
      if (stream.match(/^[A-Za-z][A-Za-z0-9_' -]*:/)) {
        state.line = "spoken";
        return "labelName";
      }
    }
    if (state.line === "header") {
      stream.skipToEnd();
      return "heading";
    }
    if (state.line === "reply") {
      if (stream.match(/^\[[^\]]*\]/)) {
        return "atom";
      }
      if (stream.match(/^[A-Za-z]+:/)) {
        return "labelName";
      }
      if (stream.match("!once")) {
        return "keyword";
      }
    }
    if (state.line === "effect" || state.line === "jump" || state.line === "keyword") {
      if (stream.match(/^(if|not)\b/)) {
        return "keyword";
      }
      if (stream.match(/^(>=|<=|==|!=|>|<|->)/)) {
        return "operator";
      }
      if (stream.match(/^END\b/)) {
        return "keyword";
      }
      if (stream.match(/^[a-z0-9_.]+:[a-z0-9_./-]+/)) {
        return "string";
      }
    }
    stream.next();
    return state.line === "spoken" ? "content" : state.line === "voice" ? "emphasis" : null;
  },
});

/** Mane Six palette, darkened enough to read on the pale editor background. */
export const taleHighlight = syntaxHighlighting(HighlightStyle.define([
  { tag: tags.heading, color: "#8a3fb5", fontWeight: "800" },            // Twilight
  { tag: tags.comment, color: "#9a8fae", fontStyle: "italic" },
  { tag: tags.keyword, color: "#c2410c", fontWeight: "700" },            // Applejack
  { tag: tags.operator, color: "#c0399a", fontWeight: "700" },           // Pinkie
  { tag: tags.variableName, color: "#0e7aa8", fontWeight: "700" },       // Rainbow Dash
  { tag: tags.atom, color: "#9a7b00", fontWeight: "700" },               // Fluttershy
  { tag: tags.labelName, color: "#5b4c7a", fontWeight: "700" },
  { tag: tags.string, color: "#3f6e9e" },
  { tag: tags.emphasis, color: "#8a6f00", fontStyle: "italic" },
  { tag: tags.content, color: "#2d2440" },
]));
