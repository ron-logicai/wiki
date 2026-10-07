import {
  DeleteLinkButton,
  FormattingToolbar,
  getFormattingToolbarItems,
  LinkToolbar,
  OpenLinkButton,
  type LinkToolbarProps,
} from "@blocknote/react";
import { EditPageLinkButton } from "./EditPageLinkButton";
import { PageLinkButton } from "./PageLinkButton";

/**
 * BlockNote's default toolbars with only the link parts swapped for ours (spec U-06). Defined at
 * module level so the controllers get stable components and do not remount the toolbar on every
 * Editor render.
 */
export function WikiFormattingToolbar() {
  return (
    <FormattingToolbar>
      {getFormattingToolbarItems().map((item) => (item.key === "createLinkButton" ? <PageLinkButton key="createLinkButton" /> : item))}
    </FormattingToolbar>
  );
}

export function WikiLinkToolbar(props: LinkToolbarProps) {
  return (
    <LinkToolbar {...props}>
      <EditPageLinkButton
        url={props.url}
        text={props.text}
        range={props.range}
        setToolbarOpen={props.setToolbarOpen}
        setToolbarPositionFrozen={props.setToolbarPositionFrozen}
      />
      <OpenLinkButton url={props.url} />
      <DeleteLinkButton range={props.range} setToolbarOpen={props.setToolbarOpen} />
    </LinkToolbar>
  );
}
