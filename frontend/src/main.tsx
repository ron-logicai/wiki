import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { Editor } from "./Editor";
import "@blocknote/core/fonts/inter.css";
import "@blocknote/mantine/style.css";

/**
 * Mounts the BlockNote editor on the element Thymeleaf renders on /pages/{id}/edit:
 *
 *   <div id="editor-root" data-page-id="..." data-base-version="7"></div>
 *   <script id="editor-document" type="application/json">[...blocks]</script>
 *
 * Routing stays with Spring MVC. This script only manages the editor component
 * and saves through the JSON endpoint (PUT /api/pages/{id}/content).
 */
function mount() {
  const root = document.getElementById("editor-root");
  if (!root) {
    return;
  }

  const pageId = root.dataset.pageId;
  const baseVersion = Number(root.dataset.baseVersion ?? "0");
  const documentElement = document.getElementById("editor-document");

  if (!pageId || !documentElement) {
    showInitError(root, "De editor kon niet worden gestart: pagina-informatie ontbreekt.");
    return;
  }

  let initialDocument: unknown;
  try {
    initialDocument = JSON.parse(documentElement.textContent || "[]");
  } catch {
    showInitError(root, "De editor kon de bestaande inhoud niet lezen. Er is niets overschreven.");
    return;
  }

  createRoot(root).render(
    <StrictMode>
      <Editor pageId={pageId} baseVersion={baseVersion} initialDocument={initialDocument} />
    </StrictMode>,
  );
}

/** F-06 / spec: an empty or broken editor must never overwrite existing content. */
function showInitError(root: HTMLElement, message: string) {
  root.setAttribute("role", "alert");
  root.textContent = message;
}

try {
  mount();
} catch (error) {
  const root = document.getElementById("editor-root");
  if (root) {
    showInitError(root, "De editor kon niet worden gestart. Bestaande inhoud is niet gewijzigd.");
  }
  console.error(error);
}
