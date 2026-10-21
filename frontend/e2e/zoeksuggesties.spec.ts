import { test, expect, type Page } from "@playwright/test";

/**
 * Title suggestions on the search page: typing part of a title lists existing pages from
 * /api/pages/suggest (the same lookup as the link popover), picking one opens that page, and a term
 * no title contains shows no list at all. Enter without a pick still submits the ordinary search.
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

test("search box suggests an existing page while typing and opens it", async ({ page }) => {
  await login(page, "editor");
  const stamp = Date.now();
  const title = `Suggestiepagina ${stamp}`;
  const id = await createPage(page, title);

  await page.goto("/search");
  const field = page.locator("#q");
  await field.fill(`suggestiepagina ${stamp}`);

  const suggestion = page.locator(`#zoek-suggesties a.zoek__suggestie[href="/pages/${id}"]`);
  await expect(suggestion).toBeVisible();
  await expect(suggestion).toContainText(title);
  await expect(field).toHaveAttribute("aria-expanded", "true");

  await field.press("ArrowDown");
  await field.press("Enter");
  await expect(page).toHaveURL(new RegExp(`/pages/${id}$`));
  await expect(page.locator("h1")).toHaveText(title);
});

test("home page search box suggests an existing page while typing and opens it", async ({ page }) => {
  await login(page, "editor");
  const stamp = Date.now();
  const title = `Startsuggestie ${stamp}`;
  const id = await createPage(page, title);

  await page.goto("/");
  const field = page.locator("#home-q");
  await field.fill(`startsuggestie ${stamp}`);

  const suggestion = page.locator(`#home-suggesties a.zoek__suggestie[href="/pages/${id}"]`);
  await expect(suggestion).toBeVisible();
  await expect(suggestion).toContainText(title);

  await field.press("ArrowDown");
  await field.press("Enter");
  await expect(page).toHaveURL(new RegExp(`/pages/${id}$`));
  await expect(page.locator("h1")).toHaveText(title);
});

test("no suggestions for a term no title contains, Enter still searches", async ({ page }) => {
  await login(page, "editor");
  await page.goto("/search");
  const field = page.locator("#q");
  const term = `geentitel${Date.now()}`;
  await field.fill(term);

  // Give the debounced lookup time to answer; the list must stay hidden.
  await page.waitForTimeout(600);
  await expect(page.locator("#zoek-suggesties")).toBeHidden();

  await field.press("Enter");
  await expect(page).toHaveURL(new RegExp(`/search\\?q=${term}`));
});
