import { useEffect, useState } from "react";
import { useCreateBlockNote } from "@blocknote/react";
import { BlockNoteView } from "@blocknote/mantine";
import { BlockNoteSchema, defaultBlockSpecs, type PartialBlock } from "@blocknote/core";
import { savePage, type SaveState } from "./save";
import { uploadFile } from "./upload";

interface EditorProps {
  pageId: string;
  baseVersion: number;
  initialDocument: unknown;
  /** Endpoint for PUT; defaults to the page endpoint. Templates use /api/templates/{id}/content. */
  saveUrl?: string;
  /** Where "open the newest version" points after a conflict. */
  viewUrl?: string;
  /** Endpoint for file uploads (video blocks). Without it the editor offers no upload. */
  uploadUrl?: string;
}

const TITLE_INPUT_ID = "page-title";

/**
 * Exactly the blocks the server accepts (DocumentValidator.BLOCK_TYPES), so the block menu never
 * offers something that would be refused on save. "video" is an uploaded MP4/WebM file.
 */
const schema = BlockNoteSchema.create({
  blockSpecs: {
    paragraph: defaultBlockSpecs.paragraph,
    heading: defaultBlockSpecs.heading,
    bulletListItem: defaultBlockSpecs.bulletListItem,
    numberedListItem: defaultBlockSpecs.numberedListItem,
    checkListItem: defaultBlockSpecs.checkListItem,
    codeBlock: defaultBlockSpecs.codeBlock,
    quote: defaultBlockSpecs.quote,
    divider: defaultBlockSpecs.divider,
    video: defaultBlockSpecs.video,
  },
});

type EditorBlock = PartialBlock<(typeof schema)["blockSchema"]>;

const STATUS_LABEL: Record<SaveState["status"], string> = {
  clean: "Geen wijzigingen",
  dirty: "Niet opgeslagen",
  saving: "Bezig met opslaan…",
  saved: "Opgeslagen",
  error: "Opslaan mislukt",
  conflict: "Conflict: de pagina is intussen door iemand anders gewijzigd. Kopieer je tekst en open de nieuwste versie.",
};

export function Editor({ pageId, baseVersion, initialDocument, saveUrl, viewUrl, uploadUrl }: EditorProps) {
  const [version, setVersion] = useState(baseVersion);
  const [state, setState] = useState<SaveState>({ status: "clean" });

  const editor = useCreateBlockNote({
    schema,
    initialContent: toInitialContent(initialDocument),
    uploadFile: uploadUrl ? (file: File) => uploadFile(uploadUrl, file) : undefined,
  });

  // The title lives in a plain input rendered by Thymeleaf; typing there also marks the page dirty.
  useEffect(() => {
    const input = titleInput();
    if (!input) {
      return;
    }
    const onInput = () => setState({ status: "dirty" });
    input.addEventListener("input", onInput);
    return () => input.removeEventListener("input", onInput);
  }, []);

  // Warn before leaving with unsaved changes (spec section 8, where the browser supports it).
  useEffect(() => {
    const dirty = state.status === "dirty" || state.status === "error" || state.status === "conflict";
    if (!dirty) {
      return;
    }
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [state.status]);

  async function handleSave() {
    setState({ status: "saving" });
    const result = await savePage(pageId, {
      baseVersion: version,
      title: titleInput()?.value ?? "",
      document: editor.document,
    }, saveUrl);
    if (result.status === "saved") {
      setVersion(result.version);
    }
    setState(result);
  }

  return (
    <div className="wiki-editor">
      <div className="wiki-editor__toolbar">
        <button type="button" onClick={handleSave} disabled={state.status === "saving"}>
          Opslaan
        </button>
        <span role="status" aria-live="polite" data-save-status={state.status}>
          {STATUS_LABEL[state.status]}
          {state.status === "error" ? ` (${state.message})` : null}
          {state.status === "saved" ? ` om ${formatTime(state.savedAt)}` : null}
        </span>
        {state.status === "conflict" ? (
          <a href={viewUrl ?? `/pages/${encodeURIComponent(pageId)}`} target="_blank" rel="noopener">
            Nieuwste versie openen
          </a>
        ) : null}
      </div>
      <BlockNoteView editor={editor} onChange={() => setState({ status: "dirty" })} />
    </div>
  );
}

function titleInput(): HTMLInputElement | null {
  return document.getElementById(TITLE_INPUT_ID) as HTMLInputElement | null;
}

/** Only a non-empty array of blocks is a valid initial document; otherwise start empty. */
function toInitialContent(value: unknown): EditorBlock[] | undefined {
  return Array.isArray(value) && value.length > 0 ? (value as EditorBlock[]) : undefined;
}

function formatTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleTimeString("nl-NL", { hour: "2-digit", minute: "2-digit" });
}
