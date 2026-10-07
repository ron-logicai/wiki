import { test, expect, type Page } from "@playwright/test";

/**
 * Page suggestions in the link popover (spec U-06): typing part of a title offers existing pages,
 * picking one inserts the fixed page URL (/pages/{id}), and the read view renders a plain anchor
 * that leads to that page. An ordinary address still becomes an external https link.
 */

const PASSWORD = "wiki";

async function login(page: Page, username: string) {
  await page.goto("/login");
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', PASSWORD);
  await page.click('button[type="submit"]');
  await expect(page).not.toHaveURL(/\/login/);
}

async function createPage(page: Page, title: string): Promise<string> {
  await page.goto("/pages/new");
  const form = page.locator('form:has(input[name="title"])');
  await form.locator('input[name="title"]').fill(title);
  await form.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/pages\/[0-9a-f-]{36}$/);
  return page.url().split("/pages/")[1];
}

/** Types a sentence and selects it, retrying until the formatting toolbar confirms the selection. */
async function typeAndSelect(page: Page, sentence: string) {
  const editor = page.locator('.bn-editor[contenteditable="true"]');
  await editor.click();
  await page.keyboard.type(sentence);
  await expect(editor).toContainText(sentence);
  // The formatting toolbar only appears for a non-empty selection; a selection made right after
  // typing is occasionally lost, so select again when the toolbar does not show up.
  const linkButton = page.locator('[data-test="createLink"]');
  for (let attempt = 0; attempt < 3 && !(await linkButton.isVisible()); attempt++) {
    await page.keyboard.press("End");
    await page.keyboard.press("Shift+Home");
    await linkButton.waitFor({ state: "visible", timeout: 2000 }).catch(() => undefined);
  }
  await expect(linkButton).toBeVisible();
  return editor;
}

/** Types a sentence, selects it and opens the link popover from the formatting toolbar. */
async function openLinkPopover(page: Page, sentence: string) {
  const editor = await typeAndSelect(page, sentence);
  await page.locator('[data-test="createLink"]').click();
  const input = page.getByPlaceholder("URL of zoek een pagina…");
  await expect(input).toBeVisible();
  return { editor, input };
}

test("link popover suggests an existing page and inserts its fixed URL", async ({ page }) => {
  await login(page, "editor");
  const stamp = Date.now();
  const targetTitle = `Doelpagina ${stamp}`;
  const targetId = await createPage(page, targetTitle);
  const sourceId = await createPage(page, `Bronpagina ${stamp}`);

  await page.goto(`/pages/${sourceId}/edit`);
  const { editor, input } = await openLinkPopover(page, "Zie de doelpagina");
  await input.fill(`doelpagina ${stamp}`);
  await page.getByRole("option", { name: targetTitle }).click();

  await expect(editor.locator(`a[href="/pages/${targetId}"]`)).toHaveText("Zie de doelpagina");

  await page.click('button:has-text("Opslaan")');
  await page.waitForURL(new RegExp(`/pages/${sourceId}$`));
  const link = page.locator(`main a[href="/pages/${targetId}"]`);
  await expect(link).toHaveText("Zie de doelpagina");
  await link.click();
  await expect(page.locator("h1")).toHaveText(targetTitle);
});

test("a plain address still becomes an https link", async ({ page }) => {
  await login(page, "editor");
  const sourceId = await createPage(page, `Externe link ${Date.now()}`);

  await page.goto(`/pages/${sourceId}/edit`);
  const { editor, input } = await openLinkPopover(page, "Voorbeeld");
  await input.fill("example.com");
  await input.press("Enter");

  await expect(editor.locator('a[href="https://example.com"]')).toHaveText("Voorbeeld");
});

test("right-click on selected text offers Link toevoegen and inserts a page link", async ({ page }) => {
  await login(page, "editor");
  const stamp = Date.now();
  const targetTitle = `Rechtsklikdoel ${stamp}`;
  const targetId = await createPage(page, targetTitle);
  const sourceId = await createPage(page, `Rechtsklikbron ${stamp}`);

  await page.goto(`/pages/${sourceId}/edit`);
  const editor = await typeAndSelect(page, "Rechtsklik hier");
  // The whole line is selected, so the centre of the paragraph lies inside the selection.
  await editor.locator("p", { hasText: "Rechtsklik hier" }).click({ button: "right" });
  await page.getByRole("menuitem", { name: "Link toevoegen" }).click();

  const input = page.getByPlaceholder("URL of zoek een pagina…");
  await expect(input).toBeVisible();
  await input.fill(`rechtsklikdoel ${stamp}`);
  await page.getByRole("option", { name: targetTitle }).click();

  await expect(editor.locator(`a[href="/pages/${targetId}"]`)).toHaveText("Rechtsklik hier");
  await expect(page.getByRole("menuitem")).toHaveCount(0);
});

test("right-click without a selection leaves the browser menu alone", async ({ page }) => {
  await login(page, "editor");
  const sourceId = await createPage(page, `Rechtsklik leeg ${Date.now()}`);

  await page.goto(`/pages/${sourceId}/edit`);
  const editor = page.locator('.bn-editor[contenteditable="true"]');
  await editor.click();
  await page.keyboard.type("Geen selectie");
  await expect(editor).toContainText("Geen selectie");
  await editor.locator("p", { hasText: "Geen selectie" }).click({ button: "right" });

  await expect(page.getByRole("menuitem")).toHaveCount(0);
  await expect(page.locator('[data-test="linkContextMenu"]')).toHaveCount(0);
});
