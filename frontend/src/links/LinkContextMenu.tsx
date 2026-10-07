import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { FormattingToolbarExtension, ShowSelectionExtension } from "@blocknote/core/extensions";
import { useBlockNoteEditor, useEditorDOMElement, useEditorState, useExtension } from "@blocknote/react";
import { LinkIcon } from "./LinkIcon";
import { PageLinkForm } from "./PageLinkForm";
import { selectLinkTarget, type LinkTarget } from "./linkSelection";
import "./links.css";

interface OpenMenu {
  x: number;
  y: number;
  target: LinkTarget;
  /** First the one-item menu, then the link form once "Link toevoegen" is chosen. */
  phase: "menu" | "form";
}

const VIEWPORT_MARGIN = 8;

/**
 * Right-click menu on selected text with a single item, "Link toevoegen", that opens the same
 * PageLinkForm as the toolbar button (spec U-06). It only replaces the browser menu when the
 * right-click lands on a non-empty selection; anywhere else the browser menu stays.
 */
export function LinkContextMenu() {
  const editor = useBlockNoteEditor();
  const editorDOMElement = useEditorDOMElement();
  const formattingToolbar = useExtension(FormattingToolbarExtension);
  // eslint-disable-next-line @typescript-eslint/unbound-method -- plain object method, as in BlockNote's own button
  const { showSelection } = useExtension(ShowSelectionExtension);

  const [menu, setMenu] = useState<OpenMenu | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const open = menu !== null;

  useEffect(() => {
    showSelection(open, "linkContextMenu");
    return () => showSelection(false, "linkContextMenu");
  }, [open, showSelection]);

  // Any change to the selection or the document closes the menu, as the toolbar popover does.
  const state = useEditorState({ editor, selector: ({ editor }) => selectLinkTarget(editor) });
  useEffect(() => {
    setMenu(null);
  }, [state]);

  useEffect(() => {
    if (!editorDOMElement) {
      return;
    }
    const onContextMenu = (event: MouseEvent) => {
      const target = selectLinkTarget(editor);
      if (!target || target.range.from === target.range.to) {
        return;
      }
      // Only a right-click on the selected text itself counts. Chrome already collapses the
      // selection when right-clicking elsewhere, Firefox does not; this makes both behave the same.
      const hit = editor.prosemirrorView.posAtCoords({ left: event.clientX, top: event.clientY });
      if (!hit || hit.pos < target.range.from || hit.pos > target.range.to) {
        return;
      }
      event.preventDefault();
      setMenu({ x: event.clientX, y: event.clientY, target, phase: "menu" });
    };
    editorDOMElement.addEventListener("contextmenu", onContextMenu);
    return () => editorDOMElement.removeEventListener("contextmenu", onContextMenu);
  }, [editor, editorDOMElement]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onMouseDown = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setMenu(null);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMenu(null);
        editor.focus();
      }
    };
    const close = () => setMenu(null);
    document.addEventListener("mousedown", onMouseDown);
    document.addEventListener("keydown", onKeyDown);
    // Element scrolls (the suggestion list) do not bubble, so this only reacts to page scrolling.
    window.addEventListener("scroll", close);
    window.addEventListener("resize", close);
    return () => {
      document.removeEventListener("mousedown", onMouseDown);
      document.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("scroll", close);
      window.removeEventListener("resize", close);
    };
  }, [open, editor]);

  // Keep the menu inside the viewport when the click was near an edge.
  useLayoutEffect(() => {
    const element = containerRef.current;
    if (!element || !menu) {
      return;
    }
    const rect = element.getBoundingClientRect();
    const x = Math.max(VIEWPORT_MARGIN, Math.min(menu.x, window.innerWidth - rect.width - VIEWPORT_MARGIN));
    const y = Math.max(VIEWPORT_MARGIN, Math.min(menu.y, window.innerHeight - rect.height - VIEWPORT_MARGIN));
    element.style.left = `${x}px`;
    element.style.top = `${y}px`;
  }, [menu]);

  if (!menu) {
    return null;
  }

  function done() {
    setMenu(null);
    formattingToolbar.store.setState(false);
  }

  return (
    <div
      ref={containerRef}
      className={menu.phase === "menu" ? "wiki-contextmenu" : "wiki-contextmenu bn-popover-content bn-form-popover"}
      style={{ left: menu.x, top: menu.y }}
      data-test="linkContextMenu"
    >
      {menu.phase === "menu" ? (
        <div role="menu" aria-label="Contextmenu">
          <button
            type="button"
            role="menuitem"
            className="wiki-contextmenu__item"
            // Keep focus (and the editor selection) in the editor until the form takes over.
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => setMenu({ ...menu, phase: "form" })}
          >
            <LinkIcon />
            <span>Link toevoegen</span>
          </button>
        </div>
      ) : (
        <PageLinkForm url={menu.target.url} text={menu.target.text} range={menu.target.range} onDone={done} />
      )}
    </div>
  );
}
