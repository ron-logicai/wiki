/**
 * Page suggestions for the link popover (spec U-06): the editor asks the wiki which pages match what
 * the user types and inserts the fixed page URL (/pages/{id}), so the link survives renames and moves
 * (spec F-16). Plain GET with the same-origin session cookie; a read needs no CSRF token.
 */

export interface PageSuggestion {
  id: string;
  title: string;
  /** Ancestor titles joined with " / ", empty for a root page. */
  path: string;
  /** The fixed page URL, ready to use as href. */
  url: string;
}

export async function fetchPageSuggestions(suggestUrl: string, q: string, signal: AbortSignal): Promise<PageSuggestion[]> {
  const separator = suggestUrl.includes("?") ? "&" : "?";
  const response = await fetch(`${suggestUrl}${separator}q=${encodeURIComponent(q)}`, {
    headers: { Accept: "application/json" },
    credentials: "same-origin",
    signal,
  });
  if (response.status === 401) {
    throw new Error("Je sessie is verlopen. Log opnieuw in om pagina's te zoeken.");
  }
  if (!response.ok) {
    throw new Error("Suggesties laden mislukt.");
  }
  const body: unknown = await response.json();
  if (!Array.isArray(body)) {
    throw new Error("Suggesties laden mislukt.");
  }
  return body as PageSuggestion[];
}

const KNOWN_PREFIXES = ["http://", "https://", "mailto:", "#"];

/** True when the value is a destination in its own right, so there is nothing to search for. */
export function looksLikeUrl(value: string): boolean {
  const trimmed = value.trim().toLowerCase();
  return KNOWN_PREFIXES.some((prefix) => trimmed.startsWith(prefix)) || trimmed.startsWith("/") || trimmed.startsWith("www.");
}

/**
 * What BlockNote's own popover does for external links (prepend https:// when no protocol is given),
 * minus the mistake of doing that to an app-relative path such as /pages/{id}. A protocol-relative
 * "//host" is passed on for the server to refuse (LinkPolicy). Returns "" for a blank value.
 */
export function normalizeLinkUrl(value: string): string {
  const trimmed = value.trim();
  if (trimmed === "") {
    return "";
  }
  const lower = trimmed.toLowerCase();
  if (KNOWN_PREFIXES.some((prefix) => lower.startsWith(prefix)) || trimmed.startsWith("/")) {
    return trimmed;
  }
  return `https://${trimmed}`;
}
