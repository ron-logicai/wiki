import { test, expect, type Page } from "@playwright/test";

/**
 * Export menu on the read view (spec F-17 plus the PDF extra): the download icon opens a menu with
 * "Downloaden als .md" and "Downloaden als .pdf"; both links deliver a file for a viewer too.
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

test("download icon opens a menu with Markdown and PDF", async ({ page }) => {
  await login(page, "editor");
  const id = await createPage(page, `Export ${Date.now()}`);

  const menu = page.locator("details.menu");
  const items = menu.locator(".menu__item");
  await expect(items.first()).toBeHidden();

  await menu.locator("summary").click();
  await expect(items).toHaveCount(2);
  await expect(items.nth(0)).toHaveText(/Downloaden als \.md/);
  await expect(items.nth(0)).toHaveAttribute("href", `/pages/${id}/export.md`);
  await expect(items.nth(1)).toHaveText(/Downloaden als \.pdf/);
  await expect(items.nth(1)).toHaveAttribute("href", `/pages/${id}/export.pdf`);

  // A click outside closes it; Escape closes it too.
  await page.locator("h1").click();
  await expect(items.first()).toBeHidden();
  await menu.locator("summary").click();
  await expect(items.first()).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(items.first()).toBeHidden();

  // Choosing PDF downloads a real PDF file.
  await menu.locator("summary").click();
  const [download] = await Promise.all([page.waitForEvent("download"), items.nth(1).click()]);
  expect(download.suggestedFilename()).toMatch(/^export-\d+\.pdf$/);
  await expect(items.first()).toBeHidden();
});

test("viewer may download both formats", async ({ page, browser, baseURL }) => {
  await login(page, "editor");
  const id = await createPage(page, `Alleen lezen ${Date.now()}`);

  // A separate session for the viewer; its request client shares the session cookie.
  const viewer = await browser.newContext({ baseURL });
  const viewerPage = await viewer.newPage();
  await login(viewerPage, "viewer");

  const md = await viewer.request.get(`/pages/${id}/export.md`);
  expect(md.ok()).toBeTruthy();
  expect(md.headers()["content-type"]).toContain("text/markdown");

  const pdf = await viewer.request.get(`/pages/${id}/export.pdf`);
  expect(pdf.ok()).toBeTruthy();
  expect(pdf.headers()["content-type"]).toContain("application/pdf");
  expect((await pdf.body()).subarray(0, 5).toString()).toBe("%PDF-");
  await viewer.close();
});
