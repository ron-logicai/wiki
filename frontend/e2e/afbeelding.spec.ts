import { test, expect, type Page } from "@playwright/test";

/**
 * Uploading a photo from the file picker (spec U-01): the editor's "Image" block sends the PNG to the
 * wiki, the saved document refers to /attachments/{id}, and the read view shows it from there.
 * A file that is not PNG/JPEG/PDF is refused by the server and the reason is shown in the editor.
 */

const PASSWORD = "wiki";

/** A real 1x1 PNG, so the browser renders it. */
const PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);

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

async function insertImageBlock(page: Page) {
  const editor = page.locator('.bn-editor[contenteditable="true"]');
  await editor.click();
  await page.keyboard.type("/image");
  await page.getByRole("option", { name: /image/i }).first().click();
  return editor;
}

test("editor uploads a PNG from the file picker and the page shows it", async ({ page }) => {
  await login(page, "editor");
  const title = `Afbeelding ${Date.now()}`;
  const id = await createPage(page, title);

  await page.goto(`/pages/${id}/edit`);
  const editor = await insertImageBlock(page);
  const chooser = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: /upload image/i }).click();
  await (await chooser).setFiles({ name: "punt.png", mimeType: "image/png", buffer: PNG });

  const image = editor.locator("img.bn-visual-media");
  await expect(image).toHaveAttribute("src", /^\/attachments\/[0-9a-f-]{36}$/);
  const src = await image.getAttribute("src");

  await page.click('button:has-text("Opslaan")');
  await expect(page.locator("[data-save-status]")).toHaveAttribute("data-save-status", "saved");

  await page.goto(`/pages/${id}`);
  await expect(page.locator("article figure.afbeelding img")).toHaveAttribute("src", src!);
  const response = await page.request.get(src!);
  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain("image/png");
});

test("a file that is not PNG, JPEG or PDF is refused with a visible reason", async ({ page }) => {
  await login(page, "editor");
  const id = await createPage(page, `Geen svg ${Date.now()}`);

  await page.goto(`/pages/${id}/edit`);
  const editor = await insertImageBlock(page);
  const chooser = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: /upload image/i }).click();
  await (await chooser).setFiles({
    name: "plaatje.png",
    mimeType: "image/png",
    buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'),
  });

  await expect(page.locator("[data-upload-error]")).toContainText("PNG, JPEG en PDF");
  await expect(editor.locator("img.bn-visual-media")).toHaveCount(0);
});
