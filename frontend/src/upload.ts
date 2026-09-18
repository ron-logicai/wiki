import { csrfHeader } from "./save";

/**
 * Sends a file the user picked in the editor to the wiki (POST multipart) and returns the URL the
 * document must use (/attachments/{id}). The server checks type and size; any refusal becomes an
 * error the editor shows, and nothing is inserted into the document.
 */
export async function uploadFile(uploadUrl: string, file: File): Promise<string> {
  const body = new FormData();
  body.append("file", file, file.name);
  let response: Response;
  try {
    response = await fetch(uploadUrl, {
      method: "POST",
      headers: { Accept: "application/json", ...csrfHeader() },
      credentials: "same-origin",
      body,
    });
  } catch {
    throw new Error("Geen verbinding met de server; het bestand is niet geüpload.");
  }
  if (response.status === 401) {
    throw new Error("Je sessie is verlopen. Log opnieuw in en probeer het nog eens.");
  }
  if (!response.ok) {
    const json = await response.json().catch(() => undefined);
    throw new Error(json?.error ?? `Uploaden mislukt (${response.status}).`);
  }
  const json = (await response.json()) as { url: string };
  return json.url;
}
