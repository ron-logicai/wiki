import { useComponentsContext, useDictionary, type LinkToolbarProps } from "@blocknote/react";
import { PageLinkForm } from "./PageLinkForm";

/**
 * "Edit link" in the toolbar that appears over an existing link: BlockNote's EditLinkButton with our
 * PageLinkForm inside, so an existing link can be pointed at a wiki page as well (spec U-06).
 */
export function EditPageLinkButton(props: Pick<LinkToolbarProps, "url" | "text" | "range" | "setToolbarOpen" | "setToolbarPositionFrozen">) {
  const Components = useComponentsContext()!;
  const dict = useDictionary();

  return (
    <Components.Generic.Popover.Root onOpenChange={props.setToolbarPositionFrozen}>
      <Components.Generic.Popover.Trigger>
        <Components.LinkToolbar.Button className="bn-button" mainTooltip={dict.link_toolbar.edit.tooltip} isSelected={false}>
          {dict.link_toolbar.edit.text}
        </Components.LinkToolbar.Button>
      </Components.Generic.Popover.Trigger>
      <Components.Generic.Popover.Content className="bn-popover-content bn-form-popover" variant="form-popover">
        <PageLinkForm
          url={props.url}
          text={props.text}
          range={props.range}
          onDone={() => {
            props.setToolbarOpen?.(false);
            props.setToolbarPositionFrozen?.(false);
          }}
        />
      </Components.Generic.Popover.Content>
    </Components.Generic.Popover.Root>
  );
}
