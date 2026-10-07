import { useEffect, useState } from "react";
import { formatKeyboardShortcut } from "@blocknote/core";
import { FormattingToolbarExtension, ShowSelectionExtension } from "@blocknote/core/extensions";
import {
  useBlockNoteEditor,
  useComponentsContext,
  useDictionary,
  useEditorDOMElement,
  useEditorState,
  useExtension,
} from "@blocknote/react";
import { LinkIcon } from "./LinkIcon";
import { PageLinkForm } from "./PageLinkForm";
import { selectLinkTarget } from "./linkSelection";

/**
 * The link button of the formatting toolbar: a port of BlockNote's CreateLinkButton with our
 * PageLinkForm inside (spec U-06). Same behaviour otherwise: Ctrl/Meta+K opens it, the selection
 * stays highlighted while the popover is open, and it closes once the document changes.
 */
export function PageLinkButton() {
  const editor = useBlockNoteEditor();
  const editorDOMElement = useEditorDOMElement();
  const Components = useComponentsContext()!;
  const dict = useDictionary();

  const formattingToolbar = useExtension(FormattingToolbarExtension);
  // eslint-disable-next-line @typescript-eslint/unbound-method -- plain object method, as in BlockNote's own button
  const { showSelection } = useExtension(ShowSelectionExtension);

  const [showPopover, setShowPopover] = useState(false);
  useEffect(() => {
    showSelection(showPopover, "pageLinkButton");
    return () => showSelection(false, "pageLinkButton");
  }, [showPopover, showSelection]);

  const state = useEditorState({ editor, selector: ({ editor }) => selectLinkTarget(editor) });
  useEffect(() => {
    setShowPopover(false);
  }, [state]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key === "k") {
        setShowPopover(true);
        event.preventDefault();
      }
    };
    editorDOMElement?.addEventListener("keydown", onKeyDown);
    return () => {
      editorDOMElement?.removeEventListener("keydown", onKeyDown);
    };
  }, [editorDOMElement]);

  if (state === undefined) {
    return null;
  }

  return (
    <Components.Generic.Popover.Root open={showPopover} onOpenChange={setShowPopover}>
      <Components.Generic.Popover.Trigger>
        <Components.FormattingToolbar.Button
          className="bn-button"
          data-test="createLink"
          label={dict.formatting_toolbar.link.tooltip}
          mainTooltip={dict.formatting_toolbar.link.tooltip}
          secondaryTooltip={formatKeyboardShortcut(dict.formatting_toolbar.link.secondary_tooltip, dict.generic.ctrl_shortcut)}
          icon={<LinkIcon />}
          onClick={() => setShowPopover((open) => !open)}
        />
      </Components.Generic.Popover.Trigger>
      <Components.Generic.Popover.Content className="bn-popover-content bn-form-popover" variant="form-popover">
        <PageLinkForm url={state.url} text={state.text} range={state.range} onDone={() => formattingToolbar.store.setState(false)} />
      </Components.Generic.Popover.Content>
    </Components.Generic.Popover.Root>
  );
}
