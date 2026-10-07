import { useContext, useEffect, useId, useState, type ChangeEvent, type KeyboardEvent } from "react";
import { useComponentsContext, useExtension } from "@blocknote/react";
import { LinkToolbarExtension } from "@blocknote/core/extensions";
import { PageLinkContext } from "./PageLinkContext";
import { LinkIcon } from "./LinkIcon";
import { fetchPageSuggestions, looksLikeUrl, normalizeLinkUrl, type PageSuggestion } from "./suggest";
import "./links.css";

interface PageLinkFormProps {
  /** Current href (empty for a new link). */
  url: string;
  /** Selected text, or the text of the existing link. */
  text: string;
  range: { from: number; to: number };
  /** Closes whichever toolbar owns the popover once the link is set. */
  onDone: () => void;
}

const DEBOUNCE_MS = 200;

/**
 * Replacement for BlockNote's EditLinkMenuItems (spec U-06). Same URL field, but while the value is
 * not a URL it is treated as a search for a wiki page, and picking a page inserts /pages/{id}.
 * BlockNote's own form cannot do that: it prepends https:// to any value without a protocol, which
 * would turn /pages/{id} into https:///pages/{id}.
 */
export function PageLinkForm({ url, text, range, onDone }: PageLinkFormProps) {
  const Components = useComponentsContext()!;
  const { suggestUrl } = useContext(PageLinkContext);
  // eslint-disable-next-line @typescript-eslint/unbound-method -- plain object method, as in BlockNote's own form
  const { editLink } = useExtension(LinkToolbarExtension);
  const listId = useId();

  const [value, setValue] = useState(url);
  const [items, setItems] = useState<PageSuggestion[]>([]);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [status, setStatus] = useState<"idle" | "loading" | "error">("idle");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setValue(url);
  }, [url]);

  const searching = suggestUrl !== undefined && !looksLikeUrl(value);

  useEffect(() => {
    if (!suggestUrl || looksLikeUrl(value)) {
      setItems([]);
      setActiveIndex(-1);
      setStatus("idle");
      return;
    }
    // Debounced and abortable: a stale response never overwrites a newer one (also under StrictMode's
    // double effect run, where the first controller is aborted right away).
    const controller = new AbortController();
    setStatus("loading");
    const timer = setTimeout(async () => {
      try {
        const found = await fetchPageSuggestions(suggestUrl, value, controller.signal);
        if (controller.signal.aborted) {
          return;
        }
        setItems(found);
        setActiveIndex(found.length > 0 ? 0 : -1);
        setStatus("idle");
      } catch (caught) {
        if (controller.signal.aborted) {
          return;
        }
        setItems([]);
        setActiveIndex(-1);
        setError(caught instanceof Error ? caught.message : "Suggesties laden mislukt.");
        setStatus("error");
      }
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [suggestUrl, value]);

  function pick(item: PageSuggestion) {
    editLink(item.url, text || item.title, range.from);
    onDone();
  }

  function submit() {
    const href = normalizeLinkUrl(value);
    if (href === "") {
      return;
    }
    editLink(href, text, range.from);
    onDone();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown" && searching) {
      event.preventDefault();
      setActiveIndex((index) => Math.min(index + 1, items.length - 1));
    } else if (event.key === "ArrowUp" && searching) {
      event.preventDefault();
      setActiveIndex((index) => Math.max(index - 1, -1));
    } else if (event.key === "Enter" && !event.nativeEvent.isComposing) {
      event.preventDefault();
      const active = searching && activeIndex >= 0 ? items[activeIndex] : undefined;
      if (active) {
        pick(active);
      } else {
        submit();
      }
    }
  }

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    setValue(event.currentTarget.value);
  }

  const activeId = searching && activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined;

  return (
    <Components.Generic.Form.Root>
      <Components.Generic.Form.TextInput
        className="bn-text-input"
        name="url"
        icon={<LinkIcon />}
        autoFocus={true}
        autoComplete="off"
        placeholder="URL of zoek een pagina…"
        value={value}
        onKeyDown={handleKeyDown}
        onChange={handleChange}
        aria-activedescendant={activeId}
      />
      {searching ? (
        <div className="wiki-paginalinks" data-paginasuggesties>
          {status === "error" ? (
            <p role="alert" className="wiki-paginalinks__status">
              {error}
            </p>
          ) : null}
          {status === "idle" && items.length === 0 ? <p className="wiki-paginalinks__status">Geen pagina&apos;s gevonden</p> : null}
          {status === "loading" && items.length === 0 ? <p className="wiki-paginalinks__status">Bezig met zoeken…</p> : null}
          {items.length > 0 ? (
            <ul role="listbox" id={listId} aria-label="Paginasuggesties" className="wiki-paginalinks__lijst">
              {items.map((item, index) => (
                <li
                  key={item.id}
                  id={`${listId}-${index}`}
                  role="option"
                  aria-selected={index === activeIndex}
                  className="wiki-paginalinks__item"
                  // Keep focus (and the editor selection) in the input until editLink has run.
                  onMouseDown={(event) => event.preventDefault()}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => pick(item)}
                >
                  <span className="wiki-paginalinks__titel">{item.title}</span>
                  {item.path ? <span className="wiki-paginalinks__pad">{item.path}</span> : null}
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </Components.Generic.Form.Root>
  );
}
