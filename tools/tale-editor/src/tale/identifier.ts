// Minecraft's namespaced IDs ("cim:chatter/grief"), with the same rules as the game's
// Identifier.tryParse: namespace [a-z0-9_.-], path [a-z0-9/._-], "minecraft" when none is written.

const NAMESPACE = /^[a-z0-9_.-]+$/;
const PATH = /^[a-z0-9/._-]*$/;

export interface Identifier {
  namespace: string;
  path: string;
}

export function tryParse(text: string): Identifier | null {
  const colon = text.indexOf(":");
  const namespace = colon < 0 ? "minecraft" : text.substring(0, colon);
  const path = colon < 0 ? text : text.substring(colon + 1);
  if (!NAMESPACE.test(namespace) || !PATH.test(path)) {
    return null;
  }
  return { namespace, path };
}

export function of(namespace: string, path: string): Identifier {
  return { namespace, path };
}

export function idString(id: Identifier): string {
  return `${id.namespace}:${id.path}`;
}

export function idEquals(a: Identifier, b: Identifier): boolean {
  return a.namespace === b.namespace && a.path === b.path;
}
