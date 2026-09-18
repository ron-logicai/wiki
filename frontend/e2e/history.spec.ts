import { test, expect, type Page } from "@playwright/test";

/**
 * Version history in the browser (spec F-11, acceptance A-05): every save is a revision with its own
 * URL, restoring an earlier revision makes a new one, and the revisions in between still exist.
 * Runs against a live app with the local profile (users viewer/editor/admin, password "wiki").
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
  const form = page.locator('form:has(input[name="title"])'); // not the sidebar's logout form
  await form.locator('input[name="title"]').fill(title);
  await form.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/pages\/[0-9a-f-]{36}$/);
  return page.url().split("/pages/")[1];
}

/** Saves through the editor's JSON endpoint, with the browser's session and CSRF token. */
async function save(page: Page, id: string, baseVersion: number, title: string, text: string) {
  await page.goto(`/pages/${id}`);
  const token = await page.locator('meta[name="_csrf"]').getAttribute("content");
  const header = await page.locator('meta[name="_csrf_header"]').getAttribute("content");
  const response = await page.request.put(`/api/pages/${id}/content`, {
    headers: { [header!]: token! },
    data: {
      baseVersion,
      title,
      document: [{ type: "paragraph", props: {}, content: [{ type: "text", text, styles: {} }], children: [] }],
    },
  });
  expect(response.status()).toBe(200);
}

test("editor restores an earlier revision and the history stays complete", async ({ page }) => {
  await login(page, "editor");
  const title = `Historie ${Date.now()}`;
  const id = await createPage(page, title);           // revision 1
  await save(page, id, 0, title, "Eerste tekst");      // revision 2
  await save(page, id, 1, "Andere titel", "Tweede tekst"); // revision 3

  // The history is a normal server page with a link per revision.
  await page.goto(`/pages/${id}/history`);
  const rows = page.locator("table.tabel tr").filter({ has: page.locator("td") });
  await expect(rows).toHaveCount(3);
  await expect(rows.first()).toContainText("actueel");

  // Restore revision 2 from its own URL; the confirm dialog is accepted.
  await rows.filter({ has: page.locator("td:first-child a", { hasText: /^2$/ }) }).locator("a").click();
  await expect(page).toHaveURL(new RegExp(`/pages/${id}/history/[0-9a-f-]{36}$`));
  await expect(page.locator("h1")).toHaveText(title);
  await expect(page.locator("article")).toContainText("Eerste tekst");
  page.once("dialog", (dialog) => dialog.accept());
  await page.click('button:has-text("Deze versie terugzetten")');

  // The earlier content is current again (A-05) and the flash message names both versions.
  await expect(page).toHaveURL(`/pages/${id}`);
  await expect(page.locator("h1")).toHaveText(title);
  await expect(page.locator("article")).toContainText("Eerste tekst");
  await expect(page.locator("article")).not.toContainText("Tweede tekst");
  await expect(page.locator(".melding--info")).toContainText("Versie 2 is teruggezet als versie 4");

  // Revisions 1 to 3 still exist; revision 3 still shows what was replaced.
  await page.goto(`/pages/${id}/history`);
  await expect(rows).toHaveCount(4);
  await rows.filter({ has: page.locator("td:first-child a", { hasText: /^3$/ }) }).locator("a").click();
  await expect(page.locator("h1")).toHaveText("Andere titel");
  await expect(page.locator("article")).toContainText("Tweede tekst");
});

test("viewer can read a revision at its own URL but cannot restore it", async ({ browser, page }) => {
  await login(page, "editor");
  const title = `Alleen lezen ${Date.now()}`;
  const id = await createPage(page, title);
  await save(page, id, 0, title, "Vroeger");
  await save(page, id, 1, title, "Nu");
  await page.goto(`/pages/${id}/history`);
  const revisionUrl = await page
    .locator("table.tabel tr")
    .filter({ has: page.locator("td:first-child a", { hasText: /^2$/ }) })
    .locator("td:first-child a")
    .getAttribute("href");

  const viewerContext = await browser.newContext();
  const viewer = await viewerContext.newPage();
  await login(viewer, "viewer");
  await viewer.goto(revisionUrl!);
  await expect(viewer.locator("h1")).toHaveText(title);
  await expect(viewer.locator("article")).toContainText("Vroeger");
  await expect(viewer.locator('button:has-text("terugzetten")')).toHaveCount(0);
  await viewer.goto(`/pages/${id}/history`);
  await expect(viewer.locator('button:has-text("Terugzetten")')).toHaveCount(0);
  await viewerContext.close();
});
