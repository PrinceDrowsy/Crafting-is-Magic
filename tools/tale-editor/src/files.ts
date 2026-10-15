// Opening and saving .tale files through the editor's local helper (server/filesPlugin.ts), which
// works in any browser, Firefox included. Without the helper (the single-file build opened from
// disk), files are opened one at a time and saved by downloading.

import { type Identifier, of } from "./tale/identifier";

export interface WorkFile {
  /** Which helper folder it's in (-1 when opened by hand). */
  root: number;
  rootLabel: string;
  /** Path inside that folder: "data/cim/tales/chatter/idle_chatter.tale". */
  path: string;
  /** The file's .tale ID: data/cim/tales/chatter/idle_chatter.tale is cim:chatter/idle_chatter. */
  id: Identifier;
  text: string;
  saved: string;
}

/** The ID a file at {@code path} gets in game: data/<namespace>/tales/<path>.tale. */
export function idForPath(path: string): Identifier {
  const parts = path.split("/");
  const tales = parts.indexOf("tales");
  if (tales >= 2 && parts[tales - 2] === "data") {
    return of(parts[tales - 1], parts.slice(tales + 1).join("/").replace(/\.tale$/, ""));
  }
  return of("cim", parts[parts.length - 1].replace(/\.tale$/, ""));
}

/** Whether the local helper is running (start-editor.cmd). */
export async function helperAvailable(): Promise<boolean> {
  try {
    return (await fetch("api/files")).ok;
  } catch {
    return false;
  }
}

export async function loadAll(): Promise<WorkFile[]> {
  const roots = (await (await fetch("api/files")).json()) as { label: string; files: string[] }[];
  const files: WorkFile[] = [];
  for (let root = 0; root < roots.length; root++) {
    for (const path of roots[root].files) {
      const response = await fetch(`api/file?root=${root}&path=${encodeURIComponent(path)}`);
      const { text } = (await response.json()) as { text: string };
      files.push({ root, rootLabel: roots[root].label, path, id: idForPath(path), text, saved: text });
    }
  }
  return files;
}

/** Opens single files by hand - for the single-file build without the helper. */
export function openFiles(): Promise<WorkFile[]> {
  return new Promise((resolve) => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = ".tale";
    input.multiple = true;
    input.onchange = async () => {
      const files: WorkFile[] = [];
      for (const file of Array.from(input.files ?? [])) {
        const text = await file.text();
        files.push({ root: -1, rootLabel: "Opened files", path: file.name, id: idForPath(file.name), text, saved: text });
      }
      resolve(files);
    };
    input.click();
  });
}

/** Saves through the helper when the file came from it; otherwise downloads it. */
export async function save(file: WorkFile): Promise<void> {
  if (file.root >= 0) {
    const response = await fetch(`api/file?root=${file.root}&path=${encodeURIComponent(file.path)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text: file.text }),
    });
    if (!response.ok) {
      throw new Error((await response.json()).error ?? "Save failed");
    }
  } else {
    const link = document.createElement("a");
    link.href = URL.createObjectURL(new Blob([file.text], { type: "text/plain" }));
    link.download = file.path.split("/").pop() ?? "untitled.tale";
    link.click();
    URL.revokeObjectURL(link.href);
  }
  file.saved = file.text;
}

/** The game's vocabulary export, through the helper; null if it hasn't been exported yet. */
export async function fetchVocabulary(): Promise<unknown | null> {
  try {
    const response = await fetch("api/vocabulary");
    return response.ok ? await response.json() : null;
  } catch {
    return null;
  }
}
