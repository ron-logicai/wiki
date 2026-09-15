import { test, expect } from "@playwright/test";

test("application reports healthy", async ({ request }) => {
  const response = await request.get("/actuator/health");
  expect(response.ok()).toBeTruthy();
  expect(await response.json()).toMatchObject({ status: "UP" });
});

test("editor bundle is served by Spring Boot", async ({ request }) => {
  const response = await request.get("/editor/editor.js");
  expect(response.ok()).toBeTruthy();
});
