import { test, expect, type Page } from "@playwright/test";

/**
 * Uploading a video from the file picker: the editor's "Video" block sends the file to the wiki,
 * the saved document refers to /attachments/{id}, and the read view plays it from there.
 */

const PASSWORD = "wiki";

/** A minimal MP4 header ("ftyp" box) plus filler; enough for the server's type check. */
const MP4 = Buffer.concat([
  Buffer.from([0, 0, 0, 0x18]),
  Buffer.from("ftypisom", "ascii"),
  Buffer.from([0, 0, 2, 0]),
  Buffer.alloc(48),
]);

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

test("editor uploads a video from the file picker and the page plays it", async ({ page }) => {
  await login(page, "editor");
  const title = `Video ${Date.now()}`;
  const id = await createPage(page, title);

  await page.goto(`/pages/${id}/edit`);
  const editor = page.locator('.bn-editor[contenteditable="true"]');
  await editor.click();

  // Insert a video block from the block menu and upload through its file panel.
  await page.keyboard.type("/video");
  await page.getByRole("option", { name: /video/i }).first().click();
  // The block opens its file panel (tab "Upload") right away; the button opens the OS file chooser.
  const chooser = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: /upload video/i }).click();
  await (await chooser).setFiles({ name: "demo.mp4", mimeType: "video/mp4", buffer: MP4 });

  const player = editor.locator("video");
  await expect(player).toHaveAttribute("src", /^\/attachments\/[0-9a-f-]{36}$/);
  const src = await player.getAttribute("src");

  await page.click('button:has-text("Opslaan")');
  await expect(page.locator("[data-save-status]")).toHaveAttribute("data-save-status", "saved");

  // The read view serves the player from the wiki itself, with a session.
  await page.goto(`/pages/${id}`);
  await expect(page.locator("article video")).toHaveAttribute("src", src!);
  const response = await page.request.get(src!);
  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain("video/mp4");
});
