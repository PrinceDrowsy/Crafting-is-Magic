// The conversation tree. A file opens as a map: one card per conversation (a starting point and
// everything it leads to) plus a card for each piece several conversations share. Open a card - or
// just move the cursor into it - to see that conversation's own tree, start to END, with shared
// bubbles drawn in softly. Kept in step with the script both ways.

import dagre from "@dagrejs/dagre";

import { type Node, type TaleFile, jumpIsConditional } from "../tale/ast";
import { idString } from "../tale/identifier";
import type { Problem } from "../tale/problem";
import { resolveTarget } from "../tale/validator";
import type { Vocabulary } from "../tale/vocabulary";
import { DragController, type Payload } from "../blocks/drag";
import { bodyStart, insertLines, layoutNodes, moveLines, removeLines } from "../blocks/lines";
import { Palette } from "../blocks/palette";
import { addCondition, makeOnce, renderStack } from "../blocks/stack";
import { type Structure, analyze, localNode, nextNodes } from "./conversations";

export interface TreeCallbacks {
  /** Move the script to this line. */
  select(line: number): void;
  /** The script's current text. */
  getText(): string;
  /** Replace the script's text (applied as one undoable change). */
  setText(text: string): void;
}

type EdgeKind = "jump" | "conditional" | "owns" | "success" | "failure" | "missing";
type View = { kind: "map" } | { kind: "conversation" | "piece"; name: string };

interface GraphNode {
  key: string;
  element: HTMLElement;
  line: number;
}

interface Edge {
  from: string;
  to: string;
  kind: EdgeKind;
  label?: string;
}


export class TreeView {
  private readonly viewport: HTMLDivElement;
  private readonly canvas: HTMLDivElement;
  private readonly edges: SVGSVGElement;
  private readonly crumbs: HTMLDivElement;
  private readonly focusButton: HTMLButtonElement;
  private nodes: GraphNode[] = [];
  private scale = 1;
  private offsetX = 20;
  private offsetY = 20;

  private file: TaleFile | null = null;
  private structure: Structure | null = null;
  private broken = new Set<number>();
  private problems: Problem[] = [];
  private known: (id: string) => boolean = () => false;

  private view: View = { kind: "map" };
  private focus = false;
  private highlighted = -1;
  private visible = new Set<string>();
  private signature = "";
  private shown = "";
  private vocab: Vocabulary | null = null;
  private readonly drag: DragController;
  private readonly palette: Palette;

  constructor(private readonly host: HTMLElement, private readonly callbacks: TreeCallbacks) {
    host.innerHTML = `
      <div class="tree-main">
        <div class="tree-stage">
          <div class="tree-crumbs"></div>
          <div class="tree-tools">
            <button class="bubble small" data-act="blocks" title="Show or hide the block palette">🧩 Blocks</button>
            <button class="bubble small" data-act="focus" title="Show only the node at the cursor and its neighbors">Focus</button>
            <button class="bubble small" data-act="fit">Fit</button>
          </div>
          <div class="tree-viewport" data-fits="node"><div class="tree-canvas"><svg class="tree-edges"></svg></div></div>
        </div>
      </div>`;
    this.drag = new DragController(host, (payload, target) => this.drop(payload, target));
    this.palette = new Palette(this.drag);
    host.querySelector(".tree-main")!.prepend(this.palette.element);
    const blocksButton = host.querySelector<HTMLButtonElement>("[data-act=blocks]")!;
    const showPalette = (show: boolean) => {
      this.palette.element.hidden = !show;
      blocksButton.classList.toggle("on", show);
      try {
        localStorage.setItem("tale-editor.palette.hidden", show ? "" : "1");
      } catch {
        // remembering is a nicety
      }
    };
    let paletteShown = true;
    try {
      paletteShown = localStorage.getItem("tale-editor.palette.hidden") !== "1";
    } catch {
      // fine: shown by default
    }
    showPalette(paletteShown);
    blocksButton.onclick = () => showPalette(this.palette.element.hidden !== false);
    this.viewport = host.querySelector(".tree-viewport")!;
    this.canvas = host.querySelector(".tree-canvas")!;
    this.edges = host.querySelector(".tree-edges")!;
    this.crumbs = host.querySelector(".tree-crumbs")!;
    host.querySelector<HTMLButtonElement>("[data-act=fit]")!.onclick = () => this.fit();
    this.focusButton = host.querySelector<HTMLButtonElement>("[data-act=focus]")!;
    this.focusButton.onclick = () => {
      this.focus = !this.focus;
      this.focusButton.classList.toggle("on", this.focus);
      this.draw();
    };
    this.enablePanZoom();
  }

  /**
   * Shows {@code file}. {@code broken} holds the header lines of nodes the game leaves out for having
   * errors, {@code problems} are the file's own, {@code known} says whether a node id exists anywhere
   * in the library.
   */
  render(file: TaleFile | null, broken: Set<number>, problems: Problem[], known: (id: string) => boolean): void {
    if (file?.source !== this.file?.source) {
      this.view = { kind: "map" };
    }
    this.file = file;
    this.structure = file ? analyze(file) : null;
    this.broken = broken;
    this.problems = problems;
    this.known = known;
    if (this.structure && this.view.kind === "map" && this.structure.conversations.length === 1 && this.structure.pieces.length === 0) {
      this.view = { kind: "conversation", name: this.structure.conversations[0].root.name };
    }
    this.draw();
  }

  /** Lights up whatever holds {@code line}; outside the map, the view follows the cursor. */
  highlight(line: number): void {
    this.highlighted = line;
    const node = this.file ? nodeAt(this.file, line) : null;
    if (node && this.view.kind !== "map" && !this.visible.has(node.name)) {
      const next = this.viewFor(node.name);
      if (JSON.stringify(next) !== JSON.stringify(this.view)) {
        this.view = next;
        this.draw(); // calls back in here once the new view is drawn
        return;
      }
    }
    if (node && this.focus && this.view.kind !== "map" && this.focusName() !== this.shownFocus()) {
      this.draw();
      return;
    }
    const activeKey = !node ? "" : this.view.kind === "map" ? this.cardKeyFor(node.name) : `n:${node.name}`;
    this.nodes.forEach((graphNode) => graphNode.element.classList.toggle("active", graphNode.key === activeKey));
    this.canvas.querySelectorAll<HTMLElement>(".blk[data-line]").forEach((el) => el.classList.toggle("current", Number(el.dataset.line) === line));
  }

  // ---- Views ----

  private open(view: View, line: number): void {
    this.view = view;
    this.draw();
    this.callbacks.select(line);
  }

  /** The view that best shows {@code name}: its own conversation, or the shared piece it's part of. */
  private viewFor(name: string): View {
    const structure = this.structure!;
    if (structure.conversations.some((c) => c.root.name === name)) {
      return { kind: "conversation", name };
    }
    const owners = structure.owners.get(name) ?? [];
    if (this.view.kind === "conversation" && owners.includes(this.view.name)) {
      return this.view;
    }
    const piece = structure.pieceOf.get(name);
    if (piece) {
      return { kind: "piece", name: piece.head.name };
    }
    return owners.length > 0 ? { kind: "conversation", name: owners[0] } : { kind: "map" };
  }

  private cardKeyFor(name: string): string {
    const piece = this.structure?.pieceOf.get(name);
    return piece ? `p:${piece.head.name}` : `c:${this.structure?.owners.get(name)?.[0] ?? ""}`;
  }

  /** The nodes the current conversation or piece is made of. */
  private membersOfView(): Set<string> | null {
    const structure = this.structure!;
    if (this.view.kind === "conversation") {
      const name = this.view.name;
      return structure.conversations.find((c) => c.root.name === name)?.members ?? null;
    }
    if (this.view.kind === "piece") {
      const name = this.view.name;
      return structure.pieces.find((p) => p.head.name === name)?.members ?? null;
    }
    return null;
  }

  private focusName(): string | null {
    if (!this.focus || !this.file) return null;
    const node = nodeAt(this.file, this.highlighted);
    return node && this.membersOfView()?.has(node.name) ? node.name : null;
  }

  private shownFocus(): string | null {
    return JSON.parse(this.shown || "[null]")[2] ?? null;
  }

  // ---- Drawing ----

  /** Redraws the tree; skipped when nothing that shows has changed. */
  private draw(): void {
    const file = this.file;
    if (file && this.view.kind !== "map" && !this.membersOfView()) {
      this.view = { kind: "map" }; // its conversation was renamed or removed
    }
    const focusName = this.view.kind === "map" ? null : this.focusName();
    const shown = JSON.stringify([file?.source ?? "", this.view, focusName]);
    const signature = file ? JSON.stringify([file.nodes, [...this.broken], this.problems.length, shown]) : "";
    if (signature === this.signature) {
      return;
    }
    const refit = shown !== this.shown;
    this.signature = signature;
    this.shown = shown;
    this.canvas.replaceChildren(this.edges);
    this.edges.innerHTML = `<defs>${["jump", "conditional", "success", "failure", "missing"].map((kind) =>
      `<marker id="arrow-${kind}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
        <path d="M0,0 L10,5 L0,10 z" class="arrowhead ${kind}"/></marker>`).join("")}</defs>`;
    this.nodes = [];
    this.visible = new Set();
    this.renderCrumbs();
    this.focusButton.hidden = this.view.kind === "map";
    if (!file || file.nodes.length === 0) {
      return;
    }

    const graph = newGraph();
    const edges: Edge[] = [];
    const add = (key: string, element: HTMLElement, line: number) => {
      this.canvas.append(element);
      graph.setNode(key, { width: element.offsetWidth, height: element.offsetHeight });
      this.nodes.push({ key, element, line });
    };
    if (this.view.kind === "map") {
      this.buildMap(add, edges);
    } else {
      let members = this.membersOfView()!;
      if (focusName) {
        const near = neighborhood(file, focusName);
        members = new Set([...members].filter((name) => near.has(name)));
      }
      this.visible = members;
      this.buildTree(file, members, graph, add, edges);
    }
    this.layout(graph, edges);
    if (refit) {
      this.fit();
    }
    if (this.highlighted > 0) {
      this.highlight(this.highlighted);
    }
  }

  private renderCrumbs(): void {
    const structure = this.structure;
    this.crumbs.innerHTML = "";
    if (!structure || !this.file) return;
    const map = document.createElement("button");
    map.className = `crumb${this.view.kind === "map" ? " here" : ""}`;
    map.textContent = "✿ Map";
    map.onclick = () => {
      this.view = { kind: "map" };
      this.draw();
    };
    this.crumbs.append(map);
    if (this.view.kind === "map") {
      const count = document.createElement("span");
      count.className = "crumb-note";
      const conversations = structure.conversations.length;
      const pieces = structure.pieces.length;
      count.textContent = `${conversations} conversation${conversations === 1 ? "" : "s"}${pieces ? ` · ${pieces} shared` : ""}`;
      this.crumbs.append(count);
    } else {
      this.crumbs.insertAdjacentHTML("beforeend", `<span class="crumb-sep">›</span><span class="crumb here">${
        this.view.kind === "piece" ? "⇄ " : ""}${escape(this.view.name)}</span>`);
    }
  }

  /** One card per conversation and shared piece; arrows only where conversations lead into each other. */
  private buildMap(add: (key: string, element: HTMLElement, line: number) => void, edges: Edge[]): void {
    const structure = this.structure!;
    const counts = this.problemCounts();
    for (const conversation of structure.conversations) {
      const own = [...conversation.members].filter((name) => !structure.pieceOf.has(name));
      const uses = [...new Set([...conversation.members].map((name) => structure.pieceOf.get(name)?.head.name).filter(Boolean))] as string[];
      const card = this.card(conversation.root, own, counts, uses.length > 0 ? `uses ${uses.join(", ")}` : "");
      card.onclick = () => this.open({ kind: "conversation", name: conversation.root.name }, conversation.root.line);
      add(`c:${conversation.root.name}`, card, conversation.root.line);
    }
    for (const piece of structure.pieces) {
      const card = this.card(piece.head, [...piece.members], counts, `shared by ${piece.usedBy.length}`);
      card.classList.add("shared");
      card.onclick = () => this.open({ kind: "piece", name: piece.head.name }, piece.head.line);
      add(`p:${piece.head.name}`, card, piece.head.line);
    }
    for (const conversation of structure.conversations) {
      conversation.leadsTo.forEach((to) => edges.push({ from: `c:${conversation.root.name}`, to: `c:${to}`, kind: "jump" }));
    }
  }

  private card(head: Node, members: string[], counts: Map<string, { errors: number; warnings: number }>, note: string): HTMLElement {
    const file = this.file!;
    const nodes = file.nodes.filter((node) => members.includes(node.name));
    const replies = nodes.flatMap((node) => node.body).filter((statement) => statement.kind === "reply").length;
    const errors = members.reduce((sum, name) => sum + (counts.get(name)?.errors ?? 0), 0);
    const warnings = members.reduce((sum, name) => sum + (counts.get(name)?.warnings ?? 0), 0);
    const first = head.body.find((statement) => statement.kind === "spoken" || statement.kind === "interjection");
    const card = document.createElement("div");
    card.className = `tcard${nodes.some((node) => this.broken.has(node.line)) ? " broken" : ""}`;
    card.innerHTML = `
      <div class="thead">${escape(head.name)}${head.start.length > 0
        ? ` <span class="tstart">★ ${escape(head.start.join(", "))}${head.weight !== 1 ? ` ×${head.weight}` : ""}</span>` : ""}
        ${errors ? `<span class="badge error">${errors}</span>` : ""}${warnings ? `<span class="badge warning">${warnings}</span>` : ""}</div>
      ${first ? `<div class="tcard-first">“${escape(clip(first.text, 60))}”</div>` : ""}
      <div class="tcard-stats">${nodes.length} node${nodes.length === 1 ? "" : "s"} · ${replies} repl${replies === 1 ? "y" : "ies"}${
        note ? ` · ${escape(note)}` : ""}</div>`;
    return card;
  }

  /** Errors and warnings per node of the current file. */
  private problemCounts(): Map<string, { errors: number; warnings: number }> {
    const counts = new Map<string, { errors: number; warnings: number }>();
    for (const problem of this.problems) {
      const node = nodeAt(this.file!, problem.line);
      if (!node) continue;
      const count = counts.get(node.name) ?? { errors: 0, warnings: 0 };
      if (problem.severity === "error") count.errors++;
      else count.warnings++;
      counts.set(node.name, count);
    }
    return counts;
  }

  /** The bubbles of {@code members}, their replies, and arrows; jumps leaving the view end in pills. */
  private buildTree(file: TaleFile, members: Set<string>, graph: Graph,
                    add: (key: string, element: HTMLElement, line: number) => void, edges: Edge[]): void {
    const structure = this.structure!;
    const target = (from: string, raw: string, kind: EdgeKind, label?: string) => {
      if (raw === "END") {
        if (!graph.hasNode("end")) {
          add("end", this.pill("tend", "★ END", -1), -1);
        }
        edges.push({ from, to: "end", kind, label });
        return;
      }
      const local = localNode(file, raw);
      if (local && members.has(local.name)) {
        edges.push({ from, to: `n:${local.name}`, kind, label });
      } else if (local) {
        // Out of view: a little pill that hops the script (and so the view) there.
        const key = `h:${local.name}`;
        if (!graph.hasNode(key)) {
          add(key, this.pill("text-target hidden-node", `→ ${local.name}`, local.line), local.line);
        }
        edges.push({ from, to: key, kind, label });
      } else {
        const id = resolveTarget(file, raw);
        const key = `x:${raw}`;
        const elsewhere = id !== null && this.known(idString(id));
        if (!graph.hasNode(key)) {
          add(key, this.pill(`text-target${elsewhere ? "" : " missing"}`, elsewhere ? `↗ ${raw}` : `✕ ${raw}`, -1), -1);
        }
        edges.push({ from, to: key, kind: elsewhere ? kind : "missing", label });
      }
    };
    const conditionLabel = (statement: { conditions: { source: string }[] }) =>
      statement.conditions.length > 0 ? `if ${statement.conditions.map((c) => c.source).join(", ")}` : undefined;

    const shown = file.nodes.filter((node) => members.has(node.name));
    const lines = this.callbacks.getText().split("\n");
    const layouts = new Map(layoutNodes(lines.join("\n")).map((layout) => [layout.header, layout]));
    for (const node of shown) {
      const bubble = this.nodeBubble(node, lines, layouts.get(node.line), file);
      if (this.broken.has(node.line)) {
        bubble.classList.add("broken");
        bubble.title = "Left out of the game until its errors are fixed";
      }
      const piece = structure.pieceOf.get(node.name);
      if (piece && this.view.kind === "conversation") {
        bubble.classList.add("shared");
        bubble.querySelector(".thead")!.insertAdjacentHTML("beforeend",
          ` <span class="tshared" title="Changing it changes every conversation that passes through">shared by ${piece.usedBy.length}</span>`);
      }
      add(`n:${node.name}`, bubble, node.line);
    }
    for (const node of shown) {
      const from = `n:${node.name}`;
      for (const statement of node.body) {
        if (statement.kind === "jump") {
          target(from, statement.target, jumpIsConditional(statement) ? "conditional" : "jump", conditionLabel(statement));
        } else if (statement.kind === "reply") {
          // Replies live inside the bubble now; their arrows leave from it, labeled with the reply.
          const label = statement.voice ?? statement.tone ?? "reply";
          if (statement.voice !== null) {
            if (statement.successTarget) target(from, statement.successTarget, "success", label);
            if (statement.failureTarget) target(from, statement.failureTarget, "failure", label);
          } else {
            for (const inner of statement.body) {
              if (inner.kind === "jump") {
                const condition = conditionLabel(inner);
                target(from, inner.target, jumpIsConditional(inner) ? "conditional" : "jump", condition ? `${label} ${condition}` : label);
              }
            }
          }
        }
      }
    }
  }

  private layout(graph: Graph, edges: Edge[]): void {
    edges.forEach((edge, i) => graph.setEdge(edge.from, edge.to, { label: edge.label }, `e${i}`));
    wrapSiblings(graph, this.nodes.map((node) => node.key), edges);
    dagre.layout(graph);

    for (const { key, element } of this.nodes) {
      const box = graph.node(key);
      element.style.left = `${box.x - box.width / 2}px`;
      element.style.top = `${box.y - box.height / 2}px`;
    }
    const size = graph.graph();
    this.edges.setAttribute("width", String((size.width ?? 0) + 40));
    this.edges.setAttribute("height", String((size.height ?? 0) + 40));
    edges.forEach((edge, i) => {
      const points = graph.edge({ v: edge.from, w: edge.to, name: `e${i}` }).points as { x: number; y: number }[];
      const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
      path.setAttribute("d", smoothPath(points));
      path.setAttribute("class", `tedge ${edge.kind}`);
      if (edge.kind !== "owns") {
        path.setAttribute("marker-end", `url(#arrow-${edge.kind})`);
      }
      this.edges.append(path);
      if (edge.label) {
        const middle = points[Math.floor(points.length / 2)];
        const label = document.createElement("div");
        label.className = "tedge-label";
        label.textContent = edge.label;
        label.style.left = `${middle.x}px`;
        label.style.top = `${middle.y}px`;
        this.canvas.append(label);
      }
    });
  }

  // ---- Building bubbles ----

  /** A node as a stack of blocks under its name - the name takes conditions as a "when:". */
  private nodeBubble(node: Node, lines: string[], layout: ReturnType<typeof layoutNodes>[number] | undefined, file: TaleFile): HTMLElement {
    const bubble = document.createElement("div");
    bubble.className = "tnode";
    const head = document.createElement("div");
    head.className = "thead";
    head.textContent = node.name;
    head.dataset.fits = "condition";
    head.dataset.header = String(node.line);
    head.title = "Drop a condition here to only offer this node when it's true";
    head.onclick = () => this.callbacks.select(node.line);
    bubble.append(head);
    if (layout && this.vocab) {
      bubble.append(...renderStack(layout, {
        vocab: this.vocab,
        targets: file.nodes.map((other) => other.name).filter((name) => name !== node.name),
        lines,
        edit: (change) => this.callbacks.setText(change(this.callbacks.getText().split("\n")).join("\n")),
        select: (line) => this.callbacks.select(line),
        drag: this.drag,
      }));
    }
    return bubble;
  }

  /** Something was dropped: turn it into a script edit. */
  private drop(payload: Payload, target: HTMLElement): void {
    const lines = this.callbacks.getText().split("\n");
    const fits = (target.dataset.fits ?? "").split(" ");
    let next: string[] | null = null;
    if (fits.includes("trash")) {
      if (payload.type === "block") next = removeLines(lines, payload.block.line, payload.block.last);
    } else if (target.dataset.after !== undefined) {
      const after = Number(target.dataset.after);
      const indent = target.dataset.indent ?? "";
      next = payload.type === "template"
        ? insertLines(lines, after, payload.template.lines, indent)
        : moveLines(lines, payload.block.line, payload.block.last, after, indent);
    } else if (payload.type === "template" && payload.template.fits.includes("condition")) {
      const condition = payload.template.lines[0];
      if (target.dataset.header !== undefined) {
        const layout = layoutNodes(lines.join("\n")).find((node) => node.header === Number(target.dataset.header));
        const when = layout?.hats.find((hat) => hat.kind === "when");
        next = when ? addCondition(lines, when.line, condition)
          : layout ? insertLines(lines, bodyStart(layout), [`when: ${condition}`], "") : null;
      } else if (target.dataset.line) {
        next = addCondition(lines, Number(target.dataset.line), condition);
      }
    } else if (payload.type === "template" && fits.includes("once") && target.dataset.line) {
      next = makeOnce(lines, Number(target.dataset.line));
    } else if (payload.type === "template" && fits.includes("node")) {
      const taken = new Set(layoutNodes(lines.join("\n")).map((node) => node.name));
      let name = "new_node";
      for (let i = 2; taken.has(name); i++) name = `new_node_${i}`;
      while (lines.length > 0 && lines[lines.length - 1].trim() === "") lines.pop();
      next = [...lines, "", `=== ${name}`, ...payload.template.lines.slice(1), ""];
      this.callbacks.setText(next.join("\n"));
      this.callbacks.select(lines.length + 2);
      return;
    }
    if (next && next !== lines) {
      this.callbacks.setText(next.join("\n"));
    }
  }

  /** Called when the vocabulary changes - it decides the palette and every dropdown. */
  setVocabulary(vocab: Vocabulary): void {
    this.vocab = vocab;
    this.palette.setVocabulary(vocab);
    this.signature = ""; // dropdowns change: redraw
    this.draw();
  }

  private pill(className: string, text: string, line: number): HTMLElement {
    const element = document.createElement("div");
    element.className = className;
    element.textContent = text;
    if (line > 0) {
      element.onclick = () => this.callbacks.select(line);
    }
    return element;
  }

  // ---- Pan and zoom ----

  private enablePanZoom(): void {
    let dragging = false;
    let lastX = 0;
    let lastY = 0;
    this.viewport.onpointerdown = (event) => {
      if ((event.target as HTMLElement).closest(".tnode, .treply, .tedit, .text-target, .tcard")) {
        return;
      }
      dragging = true;
      lastX = event.clientX;
      lastY = event.clientY;
      this.viewport.setPointerCapture(event.pointerId);
    };
    this.viewport.onpointermove = (event) => {
      if (!dragging) return;
      this.offsetX += event.clientX - lastX;
      this.offsetY += event.clientY - lastY;
      lastX = event.clientX;
      lastY = event.clientY;
      this.apply();
    };
    this.viewport.onpointerup = () => (dragging = false);
    this.viewport.onwheel = (event) => {
      event.preventDefault();
      const rect = this.viewport.getBoundingClientRect();
      const mx = event.clientX - rect.left;
      const my = event.clientY - rect.top;
      const factor = Math.exp(-event.deltaY * 0.0015);
      const next = Math.min(2.5, Math.max(0.2, this.scale * factor));
      this.offsetX = mx - ((mx - this.offsetX) * next) / this.scale;
      this.offsetY = my - ((my - this.offsetY) * next) / this.scale;
      this.scale = next;
      this.apply();
    };
  }

  /** Zooms and centers so the whole tree fits. */
  fit(): void {
    const width = Number(this.edges.getAttribute("width") ?? 1);
    const height = Number(this.edges.getAttribute("height") ?? 1);
    const rect = this.viewport.getBoundingClientRect();
    this.scale = Math.min(1.2, Math.max(0.2, Math.min(rect.width / width, (rect.height - 40) / height) * 0.95));
    this.offsetX = (rect.width - width * this.scale) / 2;
    this.offsetY = 44;
    this.apply();
  }

  private apply(): void {
    this.canvas.style.transform = `translate(${this.offsetX}px, ${this.offsetY}px) scale(${this.scale})`;
  }
}

function newGraph() {
  const graph = new dagre.graphlib.Graph({ multigraph: true });
  graph.setGraph({ rankdir: "TB", nodesep: 28, ranksep: 46, marginx: 10, marginy: 10 });
  graph.setDefaultEdgeLabel(() => ({}));
  return graph;
}

type Graph = ReturnType<typeof newGraph>;

/** The node whose header is at or above {@code line} - the one the cursor is in. */
function nodeAt(file: TaleFile, line: number): Node | null {
  let best: Node | null = null;
  for (const node of file.nodes) {
    if (node.line <= line && (!best || node.line > best.line)) {
      best = node;
    }
  }
  return best ?? file.nodes[0] ?? null;
}

const FOCUS_BEFORE = 1;
const FOCUS_AFTER = 2;

/** The focused node plus everything a step before it and two steps after. */
function neighborhood(file: TaleFile, focus: string): Set<string> {
  const after = new Map<string, Node[]>();
  const before = new Map<string, Node[]>();
  for (const node of file.nodes) {
    for (const next of nextNodes(file, node)) {
      after.set(node.name, [...(after.get(node.name) ?? []), next]);
      before.set(next.name, [...(before.get(next.name) ?? []), node]);
    }
  }
  const visible = new Set([focus]);
  const walk = (links: Map<string, Node[]>, steps: number) => {
    let frontier = [focus];
    for (let step = 0; step < steps; step++) {
      frontier = frontier.flatMap((name) => links.get(name) ?? []).map((node) => node.name).filter((name) => !visible.has(name));
      frontier.forEach((name) => visible.add(name));
    }
  };
  walk(before, FOCUS_BEFORE);
  walk(after, FOCUS_AFTER);
  return visible;
}

const WRAP = 4;

/**
 * Bubbles that share exactly the same parents (or have none, like a map of conversations) would all sit
 * in one long row. Break each such group into rows of {@link WRAP} by tying every row to the one above
 * with invisible edges - dagre then stacks them instead of spreading them out.
 */
function wrapSiblings(graph: Graph, keys: string[], edges: { from: string; to: string }[]): void {
  const parents = new Map<string, Set<string>>();
  for (const edge of edges) {
    if (edge.from !== edge.to) {
      parents.set(edge.to, (parents.get(edge.to) ?? new Set()).add(edge.from));
    }
  }
  const groups = new Map<string, string[]>();
  for (const key of keys) {
    if (key === "end") continue;
    const group = [...(parents.get(key) ?? [])].sort().join("|");
    groups.set(group, [...(groups.get(group) ?? []), key]);
  }
  let tie = 0;
  for (const members of groups.values()) {
    for (let i = WRAP; i < members.length; i++) {
      graph.setEdge(members[i - WRAP], members[i], { minlen: 1, weight: 2 }, `wrap${tie++}`);
    }
  }
}

function smoothPath(points: { x: number; y: number }[]): string {
  if (points.length < 3) {
    return `M${points.map((p) => `${p.x},${p.y}`).join(" L")}`;
  }
  let d = `M${points[0].x},${points[0].y}`;
  for (let i = 1; i < points.length - 1; i++) {
    const mid = { x: (points[i].x + points[i + 1].x) / 2, y: (points[i].y + points[i + 1].y) / 2 };
    d += ` Q${points[i].x},${points[i].y} ${mid.x},${mid.y}`;
  }
  const last = points[points.length - 1];
  return `${d} L${last.x},${last.y}`;
}

function clip(text: string, max = 70): string {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

function escape(text: string): string {
  return text.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]!);
}
