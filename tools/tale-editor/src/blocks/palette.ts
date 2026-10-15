// The block palette: a drawer of blocks in color-coded categories to drag into the tree. Dragging a
// block from the tree back onto it throws that block away.

import type { Vocabulary } from "../tale/vocabulary";
import { CATEGORIES, type Category, type Template, catalog } from "./catalog";
import type { DragController } from "./drag";

const OPEN_KEY = "tale-editor.palette.closed";

export class Palette {
  readonly element: HTMLElement;
  private readonly list: HTMLElement;
  private readonly search: HTMLInputElement;
  private templates: Template[] = [];
  private closed = new Set<string>(readClosed());

  constructor(private readonly drag: DragController) {
    this.element = document.createElement("aside");
    this.element.className = "palette";
    this.element.dataset.fits = "trash";
    this.element.innerHTML = `
      <div class="palette-head">🧩 Blocks</div>
      <input class="palette-search" type="search" placeholder="Find a block…">
      <div class="palette-list"></div>
      <div class="palette-trash">🗑 Drop here to delete</div>`;
    this.list = this.element.querySelector(".palette-list")!;
    this.search = this.element.querySelector(".palette-search")!;
    this.search.oninput = () => this.draw();
    this.search.onkeydown = (event) => event.stopPropagation();
  }

  setVocabulary(vocab: Vocabulary): void {
    this.templates = catalog(vocab);
    this.draw();
  }

  private draw(): void {
    const query = this.search.value.trim().toLowerCase();
    this.list.innerHTML = "";
    for (const category of CATEGORIES) {
      const matching = this.templates.filter((template) => template.category === category.id
        && (!query || template.label.toLowerCase().includes(query) || (template.group ?? "").toLowerCase().includes(query)));
      if (matching.length === 0) continue;
      const section = document.createElement("section");
      const isClosed = !query && this.closed.has(category.id);
      section.className = `palette-cat cat-${category.id}${isClosed ? " closed" : ""}`;
      const head = document.createElement("button");
      head.className = "palette-cat-head";
      head.innerHTML = `<span>${category.icon} ${category.label}</span><span class="chev">${isClosed ? "▸" : "▾"}</span>`;
      head.onclick = () => this.toggle(category.id);
      section.append(head);
      if (!isClosed) {
        let group: string | undefined;
        for (const template of matching) {
          if (template.group && template.group !== group) {
            group = template.group;
            section.insertAdjacentHTML("beforeend", `<div class="palette-group">${group}</div>`);
          }
          section.append(this.item(template, category.id));
        }
      }
      this.list.append(section);
    }
  }

  private item(template: Template, category: Category): HTMLElement {
    const item = document.createElement("div");
    item.className = `blk palette-blk cat-${category}${template.fits.includes("condition") ? " is-condition" : ""}`;
    item.draggable = true;
    item.textContent = template.label;
    item.title = [template.hint, template.lines.join("\n")].filter(Boolean).join("\n\n");
    item.ondragstart = (event) => this.drag.start(event, { type: "template", template }, template.lines.join("\n"), item);
    return item;
  }

  private toggle(category: string): void {
    if (!this.closed.delete(category)) this.closed.add(category);
    try {
      localStorage.setItem(OPEN_KEY, JSON.stringify([...this.closed]));
    } catch {
      // remembering is a nicety
    }
    this.draw();
  }
}

function readClosed(): string[] {
  try {
    return JSON.parse(localStorage.getItem(OPEN_KEY) ?? "[]");
  } catch {
    return [];
  }
}
