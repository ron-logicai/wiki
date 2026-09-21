/**
 * Save path per spec section 8: send the document, the title and the version they are based on.
 * The server answers 200 with the new version, 409 on a stale base version,
 * 401 when the session expired, 403 when the CSRF token is stale or the role is insufficient,
 * 400 for invalid content. The editor keeps its content in every failure case.
 */
export type SaveState =
  | { status: "clean" }
  | { status: "dirty" }
  | { status: "saving" }
  | { status: "saved"; version: number; revision: number; savedAt: string }
  | { status: "conflict"; currentVersion?: number }
  | { status: "error"; message: string };

export interface SaveRequest {
  baseVersion: number;
  title: string;
  document: unknown;
}

/** Saves to the page endpoint by default; templates pass their own endpoint (spec F-15). */
export async function savePage(pageId: string, request: SaveRequest, saveUrl?: string): Promise<SaveState> {
  try {
    const response = await fetch(saveUrl ?? `/api/pages/${encodeURIComponent(pageId)}/content`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        ...csrfHeader(),
      },
      credentials: "same-origin",
      body: JSON.stringify(request),
    });

    if (response.status === 409) {
      const body = await safeJson(response);
      return { status: "conflict", currentVersion: body?.currentVersion };
    }
    if (response.status === 401) {
      return { status: "error", message: "Je sessie is verlopen. Kopieer je tekst en log opnieuw in." };
    }
    if (response.status === 400) {
      const body = await safeJson(response);
      return { status: "error", message: body?.error ?? "De inhoud is ongeldig." };
    }
    if (response.status === 403) {
      // CSRF token stale or no rights (spec N-02); the server sends the reason as JSON.
      const body = await safeJson(response);
      return { status: "error", message: body?.error ?? "Opslaan geweigerd. Herlaad de pagina en probeer het opnieuw." };
    }
    if (!response.ok) {
      return { status: "error", message: `Server antwoordde met ${response.status}` };
    }

    const body = await response.json();
    return { status: "saved", version: body.version, revision: body.revision, savedAt: body.savedAt };
  } catch {
    return { status: "error", message: "Geen verbinding met de server. Je tekst staat nog in de editor." };
  }
}

/** Spring Security CSRF token rendered by Thymeleaf as <meta name="_csrf" ...>. */
export function csrfHeader(): Record<string, string> {
  const token = document.querySelector<HTMLMetaElement>('meta[name="_csrf"]')?.content;
  const header = document.querySelector<HTMLMetaElement>('meta[name="_csrf_header"]')?.content;
  return token && header ? { [header]: token } : {};
}

async function safeJson(response: Response): Promise<{ currentVersion?: number; error?: string } | undefined> {
  try {
    return await response.json();
  } catch {
    return undefined;
  }
}
