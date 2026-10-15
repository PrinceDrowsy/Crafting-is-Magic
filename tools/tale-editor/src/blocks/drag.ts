// Dragging blocks around: from the palette into a bubble, between bubbles, or back to the palette to
// throw away. Anything that can take a drop carries data-fits="..."; while a drag is on, the ones that
// fit light up and only they accept it.

import type { Block, BlockKind } from "./lines";
import type { Fits, Template } from "./catalog";

export type Payload =
  | { type: "template"; template: Template }
  | { type: "block"; block: Block };

type Target = Fits;

export function payloadFits(payload: Payload): Target[] {
  return payload.type === "template" ? payload.template.fits : [...blockFits(payload.block.kind), "trash"];
}

export function blockFits(kind: BlockKind): Target[] {
  switch (kind) {
    case "effect": return ["body", "child", "voice-child"];
    case "jump": return ["body", "child"];
    case "or": return ["or"];
    case "start": case "weight": case "when": case "tone": return ["hat"];
    case "branch": return [];
    default: return ["body"];
  }
}

export class DragController {
  private current: Payload | null = null;
  private hovered: HTMLElement | null = null;

  constructor(private readonly root: HTMLElement, private readonly onDrop: (payload: Payload, target: HTMLElement) => void) {
    root.addEventListener("dragover", (event) => {
      const target = this.targetOf(event);
      this.hover(target);
      if (target) {
        event.preventDefault();
        event.dataTransfer!.dropEffect = this.current?.type === "template" ? "copy" : "move";
      }
    });
    root.addEventListener("dragleave", (event) => {
      if (!root.contains(event.relatedTarget as globalThis.Node | null)) this.hover(null);
    });
    root.addEventListener("drop", (event) => {
      const target = this.targetOf(event);
      const payload = this.current;
      this.end();
      if (target && payload) {
        event.preventDefault();
        this.onDrop(payload, target);
      }
    });
    root.addEventListener("dragend", () => this.end());
  }

  /** Starts dragging {@code payload}; {@code text} is what lands if it's dropped somewhere else, like the script. */
  start(event: DragEvent, payload: Payload, text: string, image?: HTMLElement): void {
    this.current = payload;
    event.dataTransfer!.setData("text/plain", text);
    event.dataTransfer!.effectAllowed = "copyMove";
    if (image) event.dataTransfer!.setDragImage(image, 16, 14);
    const fits = new Set(payloadFits(payload));
    // Mark targets on the next frame - changing the page during dragstart can cancel the drag.
    requestAnimationFrame(() => {
      if (this.current !== payload) return;
      this.root.classList.add("is-dragging");
      this.root.querySelectorAll<HTMLElement>("[data-fits]").forEach((element) => {
        const accepts = element.dataset.fits!.split(" ");
        element.classList.toggle("can-drop", accepts.some((fit) => fits.has(fit as Target)) && !this.isInsideDragged(element, payload));
      });
    });
  }

  /** A block can't be dropped into a gap among its own lines (inside its own reply, say). */
  private isInsideDragged(element: HTMLElement, payload: Payload): boolean {
    if (payload.type !== "block" || element.dataset.after === undefined) return false;
    const after = Number(element.dataset.after);
    return after >= payload.block.line && after < payload.block.last;
  }

  private targetOf(event: DragEvent): HTMLElement | null {
    if (!this.current) return null;
    let element = event.target as HTMLElement | null;
    while (element && element !== this.root.parentElement) {
      if (element.classList?.contains("can-drop")) return element;
      element = element.parentElement;
    }
    return null;
  }

  private hover(target: HTMLElement | null): void {
    if (target === this.hovered) return;
    this.hovered?.classList.remove("drop-here");
    target?.classList.add("drop-here");
    this.hovered = target;
  }

  private end(): void {
    this.current = null;
    this.hover(null);
    this.root.classList.remove("is-dragging");
    this.root.querySelectorAll(".can-drop").forEach((element) => element.classList.remove("can-drop"));
  }
}
