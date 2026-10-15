// Splits a .tale file into the pieces a writer thinks in: conversations (a starting node and everything
// it leads to) and shared pieces (nodes several conversations pass through, like a common set of
// replies). The tree view shows the file as a map of these, then one at a time.

import type { Node, TaleFile } from "../tale/ast";
import { idString } from "../tale/identifier";
import { resolveTarget } from "../tale/validator";

export interface Conversation {
  root: Node;
  /** Every node this conversation can pass through, shared ones included. */
  members: Set<string>;
  /** Other conversations it jumps into, by root name. */
  leadsTo: Set<string>;
}

export interface SharedPiece {
  head: Node;
  members: Set<string>;
  /** The conversations passing through it, by root name. */
  usedBy: string[];
}

export interface Structure {
  conversations: Conversation[];
  pieces: SharedPiece[];
  /** Node name -> the conversations it belongs to. */
  owners: Map<string, string[]>;
  /** Node name -> the shared piece it's part of, if any. */
  pieceOf: Map<string, SharedPiece>;
}

/** The node in {@code file} a jump to {@code raw} lands on, if it's in this file. */
export function localNode(file: TaleFile, raw: string): Node | null {
  const id = resolveTarget(file, raw);
  return (id && file.nodes.find((node) => idString(node.id) === idString(id))) || null;
}

/** Every node a node can lead to directly - its jumps, its replies' jumps, and its voice checks. */
export function nextNodes(file: TaleFile, node: Node): Node[] {
  const raws: string[] = [];
  for (const statement of node.body) {
    if (statement.kind === "jump") {
      raws.push(statement.target);
    } else if (statement.kind === "reply") {
      if (statement.successTarget) raws.push(statement.successTarget);
      if (statement.failureTarget) raws.push(statement.failureTarget);
      statement.body.forEach((inner) => inner.kind === "jump" && raws.push(inner.target));
    }
  }
  return raws.map((raw) => localNode(file, raw)).filter((next): next is Node => next !== null);
}

export function analyze(file: TaleFile): Structure {
  const next = new Map(file.nodes.map((node) => [node.name, nextNodes(file, node)]));
  const reached = new Set<string>();
  next.forEach((targets, from) => targets.forEach((target) => target.name !== from && reached.add(target.name)));

  // A conversation starts wherever the game can start one, or wherever nothing in this file leads.
  const roots = new Set(file.nodes.filter((node) => node.start.length > 0 || !reached.has(node.name)).map((node) => node.name));
  const conversations: Conversation[] = [];
  const owners = new Map<string, string[]>();
  const walk = (root: Node) => {
    const members = new Set([root.name]);
    const leadsTo = new Set<string>();
    const queue = [root.name];
    while (queue.length > 0) {
      for (const target of next.get(queue.shift()!) ?? []) {
        if (target.name === root.name || members.has(target.name)) continue;
        if (roots.has(target.name)) {
          leadsTo.add(target.name); // another conversation - linked, not swallowed
          continue;
        }
        members.add(target.name);
        queue.push(target.name);
      }
    }
    members.forEach((name) => owners.set(name, [...(owners.get(name) ?? []), root.name]));
    conversations.push({ root, members, leadsTo });
  };
  file.nodes.filter((node) => roots.has(node.name)).forEach(walk);
  // Loops nothing starts (a -> b -> a) still deserve a home: the earliest node of each becomes a root.
  for (let orphan = file.nodes.find((node) => !owners.has(node.name)); orphan; orphan = file.nodes.find((node) => !owners.has(node.name))) {
    roots.add(orphan.name);
    walk(orphan);
  }

  // Shared nodes that lead into each other form one piece, headed by the one nothing else in it leads to.
  const shared = file.nodes.filter((node) => (owners.get(node.name)?.length ?? 0) > 1);
  const sharedNames = new Set(shared.map((node) => node.name));
  const pieces: SharedPiece[] = [];
  const pieceOf = new Map<string, SharedPiece>();
  for (const start of shared) {
    if (pieceOf.has(start.name)) continue;
    const members = new Set([start.name]);
    const queue = [start.name];
    while (queue.length > 0) {
      const name = queue.shift()!;
      const neighbors = [
        ...(next.get(name) ?? []).map((node) => node.name),
        ...shared.filter((node) => next.get(node.name)!.some((target) => target.name === name)).map((node) => node.name),
      ];
      for (const neighbor of neighbors) {
        if (sharedNames.has(neighbor) && !members.has(neighbor)) {
          members.add(neighbor);
          queue.push(neighbor);
        }
      }
    }
    const inPiece = shared.filter((node) => members.has(node.name));
    const head = inPiece.find((node) => !inPiece.some((other) => other !== node && next.get(other.name)!.some((t) => t.name === node.name))) ?? inPiece[0];
    const usedBy = [...new Set([...members].flatMap((name) => owners.get(name)!))];
    const piece = { head, members, usedBy };
    pieces.push(piece);
    members.forEach((name) => pieceOf.set(name, piece));
  }
  return { conversations, pieces, owners, pieceOf };
}
