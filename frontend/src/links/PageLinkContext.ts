import { createContext } from "react";

/**
 * Where the link popover looks up pages. Provided by Editor from data-suggest-url on #editor-root;
 * without it the popover is a plain URL form. A context because BlockNote renders the toolbar
 * components itself, so there is no place to pass the URL as a prop.
 */
export const PageLinkContext = createContext<{ suggestUrl?: string }>({});
