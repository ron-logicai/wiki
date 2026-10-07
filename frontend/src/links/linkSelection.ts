import { isTableCellSelection, type BlockNoteEditor } from "@blocknote/core";

/** What a link popover needs to know about the current selection. */
export interface LinkTarget {
  /** Href of the link under the selection, or "" when there is none. */
  url: string;
  /** Selected text, or the text of the existing link. */
  text: string;
  range: { from: number; to: number };
}

/**
 * The selection rules of BlockNote's CreateLinkButton, shared by the toolbar button and the context
 * menu (spec U-06): a link can only be set in an editable editor whose schema has links, never in a
 * table-cell selection, and only when a selected block has inline content. Returns undefined
 * when no link can be set.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any -- the editor's schema is irrelevant here, as in BlockNote's own hooks
export function selectLinkTarget(editor: BlockNoteEditor<any, any, any>): LinkTarget | undefined {
  if (
    !editor.isEditable ||
    !("link" in editor.schema.inlineContentSchema) ||
    isTableCellSelection(editor.prosemirrorState.selection) ||
    !(editor.getSelection()?.blocks || [editor.getTextCursorPosition().block]).find((block) => block.content !== undefined)
  ) {
    return undefined;
  }
  return {
    url: editor.getSelectedLinkUrl() || "",
    text: editor.getSelectedText(),
    range: {
      from: editor.prosemirrorState.selection.from,
      to: editor.prosemirrorState.selection.to,
    },
  };
}
