// The .tale editor: files on the left, the script in the middle, the conversation tree on the right,
// problems along the bottom - all checked live with the same rules the game uses.

import { EditorState } from "@codemirror/state";
import { EditorView, drawSelection, highlightActiveLine, keymap, lineNumbers } from "@codemirror/view";
import { defaultKeymap, history, historyKeymap, indentWithTab } from "@codemirror/commands";
import { type Diagnostic, forceLinting, lintGutter, linter } from "@codemirror/lint";

import "./style.css";
import { taleHighlight, taleLanguage } from "./editor/taleLanguage";
import { type WorkFile, fetchVocabulary, helperAvailable, loadAll, openFiles, save } from "./files";
import { type Snapshot, build } from "./tale/library";
import type { Problem } from "./tale/problem";
import type { Vocabulary } from "./tale/vocabulary";
import { PlayPanel } from "./play/panel";
import { TreeView } from "./tree/treeView";
import bundledVocabulary from "./vocabulary.json";

// ---- State ----

let files: WorkFile[] = [];
let current: WorkFile | null = null;
let vocab: Vocabulary = bundledVocabulary as Vocabulary;
let vocabSource = "bundled copy";
let snapshot: Snapshot | null = null;
const states = new Map<WorkFile, EditorState>();

function sourceOf(file: WorkFile): string {
  return `${file.rootLabel}/${file.path}`;
}

/** Re-checks every open file together - jumps can cross files, as in game. */
function recheck(): Snapshot {
  snapshot = build(files.map((file) => ({ id: file.id, source: sourceOf(file), text: file.text })), vocab);
  renderFiles();
  renderProblems();
  renderTree();
  return snapshot;
}

// ---- Layout ----

const app = document.querySelector<HTMLDivElement>("#app")!;
app.innerHTML = `
  <header class="topbar">
    <div class="brand"><span class="sparkle">✦</span> Tale Editor</div>
    <div class="status" id="status"></div>
    <div class="actions">
      <button id="open" class="bubble" hidden>Open files…</button>
      <button id="reload" class="bubble">Reload vocabulary</button>
      <button id="save" class="bubble primary">Save <kbd>Ctrl S</kbd></button>
    </div>
  </header>
  <aside class="panel files column" data-panel="files">${panelBar("Files")}<div class="panel-body" id="files"></div></aside>
  <main class="panel script column" data-panel="script">${panelBar("Script")}<div class="panel-body" id="editor"></div></main>
  <section class="panel tree column" data-panel="tree">${panelBar("Tree")}<div class="panel-body">
    <div class="tab-host" id="tree"></div><div class="tab-host" id="play" hidden></div>
  </div></section>
  <footer class="panel problems" data-panel="problems">${panelBar("Problems")}<div class="panel-body" id="problems"></div></footer>
`;

function panelBar(title: string): string {
  return `<div class="panel-bar" title="Double-click to minimize or restore"><span class="panel-title">${title}</span>`
    + `<button class="panel-toggle" title="Minimize">–</button></div>`;
}

// ---- Minimizing panels (remembered between visits) ----

const minimized = new Set<string>(readMinimized());

function readMinimized(): string[] {
  try {
    return JSON.parse(localStorage.getItem("tale-editor.minimized") ?? "[]");
  } catch {
    return [];
  }
}

function applyPanels(): void {
  const strip = "34px";
  app.style.gridTemplateColumns = [
    minimized.has("files") ? strip : "240px",
    minimized.has("script") ? strip : "minmax(0, 1fr)",
    minimized.has("tree") ? strip : "minmax(0, 1fr)",
  ].join(" ");
  app.style.gridTemplateRows = `auto minmax(0, 1fr) ${minimized.has("problems") ? strip : "180px"}`;
  app.querySelectorAll<HTMLElement>("[data-panel]").forEach((panel) => {
    const isMin = minimized.has(panel.dataset.panel!);
    panel.classList.toggle("minimized", isMin);
    const toggle = panel.querySelector<HTMLButtonElement>(".panel-toggle")!;
    toggle.textContent = isMin ? "+" : "–";
    toggle.title = isMin ? "Restore" : "Minimize";
  });
  try {
    localStorage.setItem("tale-editor.minimized", JSON.stringify([...minimized]));
  } catch {
    // remembering is a nicety
  }
}

function togglePanel(name: string): void {
  if (!minimized.delete(name)) {
    minimized.add(name);
  }
  applyPanels();
  requestAnimationFrame(() => {
    view.requestMeasure();
    if (name === "tree" && !minimized.has("tree")) {
      tree.fit();
    }
  });
}

app.querySelectorAll<HTMLElement>("[data-panel]").forEach((panel) => {
  const name = panel.dataset.panel!;
  const bar = panel.querySelector<HTMLElement>(".panel-bar")!;
  panel.querySelector<HTMLButtonElement>(".panel-toggle")!.onclick = (event) => {
    event.stopPropagation();
    togglePanel(name);
  };
  bar.ondblclick = () => togglePanel(name);
  bar.onclick = () => minimized.has(name) && togglePanel(name); // a minimized strip restores on any click
});

const status = document.getElementById("status")!;
const view = new EditorView({ parent: document.getElementById("editor")! });

const tree = new TreeView(document.getElementById("tree")!, {
  select(line) {
    if (line >= 1 && line <= view.state.doc.lines) {
      const at = view.state.doc.line(line);
      const indent = at.text.length - at.text.trimStart().length;
      view.dispatch({ selection: { anchor: at.from + indent }, scrollIntoView: true });
      view.focus();
    }
  },
  getText: () => view.state.doc.toString(),
  setText(text) {
    // Only the part that changed is replaced, so the cursor and undo history stay sensible.
    const old = view.state.doc.toString();
    if (text === old) return;
    let start = 0;
    while (start < old.length && start < text.length && old[start] === text[start]) start++;
    let end = 0;
    while (end < old.length - start && end < text.length - start && old[old.length - 1 - end] === text[text.length - 1 - end]) end++;
    view.dispatch({ changes: { from: start, to: old.length - end, insert: text.substring(start, text.length - end) } });
  },
});

function renderTree(): void {
  const file = current && snapshot?.files.find((parsed) => parsed.source === sourceOf(current!));
  tree.render(file ?? null, (file && snapshot?.leftOut.get(file.source)) || new Set(),
    file ? snapshot!.problems.filter((problem) => problem.source === file.source) : [], (id) => snapshot?.nodes.has(id) ?? false);
  if (snapshot) play.update(snapshot.nodes, vocab);
}

// ---- Play test: a tab beside the tree ----

const play = new PlayPanel(document.getElementById("play")!, {
  cursorNode() {
    const file = current && snapshot?.files.find((parsed) => parsed.source === sourceOf(current!));
    if (!file) return null;
    const line = view.state.doc.lineAt(view.state.selection.main.head).number;
    let best = null;
    for (const node of file.nodes) {
      if (node.line <= line && (!best || node.line > best.line)) best = node;
    }
    return best;
  },
  showNode(id) {
    const parsed = snapshot?.files.find((candidate) => candidate.nodes.some((node) => `${node.id.namespace}:${node.id.path}` === id));
    const node = parsed?.nodes.find((candidate) => `${candidate.id.namespace}:${candidate.id.path}` === id);
    const file = parsed && files.find((candidate) => sourceOf(candidate) === parsed.source);
    if (file && node) open(file, node.line);
  },
});

/** The tree panel's bar becomes two tabs: the conversation tree, and the play test. */
function setUpTabs(): void {
  const title = document.querySelector<HTMLElement>('[data-panel="tree"] .panel-title')!;
  title.innerHTML = `<button class="panel-tab on" data-tab="tree">Tree</button><button class="panel-tab" data-tab="play">▶ Play test</button>`;
  title.querySelectorAll<HTMLButtonElement>(".panel-tab").forEach((tab) => {
    tab.onclick = (event) => {
      event.stopPropagation();
      title.querySelectorAll(".panel-tab").forEach((other) => other.classList.toggle("on", other === tab));
      document.getElementById("tree")!.hidden = tab.dataset.tab !== "tree";
      document.getElementById("play")!.hidden = tab.dataset.tab !== "play";
      if (tab.dataset.tab === "tree") requestAnimationFrame(() => tree.fit());
    };
  });
}
setUpTabs();

function newState(file: WorkFile): EditorState {
  return EditorState.create({
    doc: file.text,
    extensions: [
      lineNumbers(),
      history(),
      drawSelection(),
      highlightActiveLine(),
      taleLanguage,
      taleHighlight,
      lintGutter(),
      linter(lintCurrent, { delay: 250 }),
      keymap.of([...defaultKeymap, ...historyKeymap, indentWithTab, { key: "Mod-s", run: () => (saveCurrent(), true) }]),
      EditorView.updateListener.of((update) => {
        if (update.docChanged && current) {
          current.text = update.state.doc.toString();
          renderFiles();
        }
        if (update.selectionSet || update.docChanged) {
          tree.highlight(update.state.doc.lineAt(update.state.selection.main.head).number);
        }
      }),
      EditorView.lineWrapping,
    ],
  });
}

/** The current file's problems as editor squiggles. */
function lintCurrent(editor: EditorView): Diagnostic[] {
  if (!current) {
    return [];
  }
  const result = recheck();
  const source = sourceOf(current);
  const doc = editor.state.doc;
  return result.problems
    .filter((problem) => problem.source === source && problem.line >= 1 && problem.line <= doc.lines)
    .map((problem) => {
      const line = doc.line(problem.line);
      const indent = line.text.length - line.text.trimStart().length;
      return { from: line.from + indent, to: Math.max(line.from + indent, line.to), severity: problem.severity, message: problem.message };
    });
}

function open(file: WorkFile, line?: number): void {
  if (current) {
    states.set(current, view.state);
  }
  current = file;
  view.setState(states.get(file) ?? newState(file));
  if (line !== undefined && line >= 1 && line <= view.state.doc.lines) {
    const pos = view.state.doc.line(line).from;
    view.dispatch({ selection: { anchor: pos }, scrollIntoView: true });
  }
  view.focus();
  forceLinting(view);
  renderFiles();
}

async function saveCurrent(): Promise<void> {
  if (!current) {
    return;
  }
  try {
    await save(current);
    setStatus(`Saved ${current.path}`);
  } catch (error) {
    setStatus(`Couldn't save: ${error}`, true);
  }
  renderFiles();
}

// ---- Panels ----

function problemsFor(file: WorkFile): Problem[] {
  return snapshot?.problems.filter((problem) => problem.source === sourceOf(file)) ?? [];
}

function renderFiles(): void {
  const panel = document.getElementById("files")!;
  const groups = new Map<string, WorkFile[]>();
  files.forEach((file) => groups.set(file.rootLabel, [...(groups.get(file.rootLabel) ?? []), file]));
  panel.innerHTML = files.length === 0 ? `<div class="placeholder">No .tale files yet</div>` : "";
  for (const [label, group] of groups) {
    const heading = document.createElement("h3");
    heading.textContent = label;
    panel.append(heading);
    for (const file of group) {
      const problems = problemsFor(file);
      const errors = problems.filter((problem) => problem.severity === "error").length;
      const warnings = problems.length - errors;
      const item = document.createElement("button");
      item.className = `file${file === current ? " active" : ""}`;
      item.title = file.path;
      item.innerHTML = `<span class="name">${escape(file.path.replace(/^.*?tales\//, ""))}</span>`
        + (file.text !== file.saved ? `<span class="dirty" title="Unsaved">●</span>` : "")
        + (errors ? `<span class="badge error">${errors}</span>` : "")
        + (warnings ? `<span class="badge warning">${warnings}</span>` : "");
      item.onclick = () => open(file);
      panel.append(item);
    }
  }
}

function renderProblems(): void {
  const panel = document.getElementById("problems")!;
  const problems = snapshot?.problems ?? [];
  const errors = problems.filter((problem) => problem.severity === "error").length;
  // The count lives in the bar, so it still shows while the panel is minimized.
  document.querySelector('[data-panel="problems"] .panel-title')!.innerHTML = `Problems `
    + (errors ? `<span class="badge error">${errors}</span>` : "")
    + (problems.length - errors ? `<span class="badge warning">${problems.length - errors}</span>` : "")
    + (problems.length === 0 ? `<span class="all-clear">✿ all clear</span>` : "");
  panel.innerHTML = "";
  const list = document.createElement("ul");
  for (const problem of problems) {
    const item = document.createElement("li");
    item.className = problem.severity;
    const name = problem.source.replace(/^.*?tales\//, "");
    item.innerHTML = `<span class="where">${escape(name)}:${problem.line}</span> ${escape(problem.message)}`;
    item.onclick = () => {
      const file = files.find((candidate) => sourceOf(candidate) === problem.source);
      if (file) {
        open(file, problem.line);
      }
    };
    list.append(item);
  }
  panel.append(list);
}

function setStatus(text: string, bad = false): void {
  status.textContent = text;
  status.classList.toggle("bad", bad);
}

function escape(text: string): string {
  return text.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]!);
}

async function loadVocabulary(): Promise<void> {
  const exported = await fetchVocabulary();
  if (exported) {
    vocab = exported as Vocabulary;
    vocabSource = "the game's latest export";
  }
  tree.setVocabulary(vocab);
  setStatus(Object.keys(vocab.conditions ?? {}).length === 0
    ? "No vocabulary yet - open a world in the dev client once to export it"
    : `Vocabulary: ${vocabSource}`, Object.keys(vocab.conditions ?? {}).length === 0);
}

// ---- Start ----

applyPanels();
document.getElementById("save")!.onclick = () => saveCurrent();
document.getElementById("reload")!.onclick = async () => {
  await loadVocabulary();
  if (current) {
    forceLinting(view);
  } else {
    recheck();
  }
};
window.addEventListener("beforeunload", (event) => {
  if (files.some((file) => file.text !== file.saved)) {
    event.preventDefault();
  }
});

(async () => {
  await loadVocabulary();
  if (await helperAvailable()) {
    files = await loadAll();
  } else {
    const openButton = document.getElementById("open")!;
    openButton.hidden = false;
    openButton.onclick = async () => {
      files = [...files, ...(await openFiles())];
      recheck();
      if (!current && files.length > 0) {
        open(files[0]);
      }
    };
    setStatus("Working without the helper - open files by hand; saving downloads them");
  }
  recheck();
  if (files.length > 0) {
    open(files[0]);
  }
})();
