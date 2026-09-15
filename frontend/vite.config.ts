import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The editor is a local React island mounted on /pages/{id}/edit.
// Vite is a build tool only; Spring Boot serves the output from /editor/*.
export default defineConfig({
  plugins: [react()],
  base: "/editor/",
  build: {
    outDir: "../src/main/resources/static/editor",
    emptyOutDir: true,
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      input: "src/main.tsx",
      output: {
        // Stable file names so the Thymeleaf template can reference them directly.
        entryFileNames: "editor.js",
        chunkFileNames: "chunks/[name].js",
        assetFileNames: (asset) =>
          asset.names?.some((n) => n.endsWith(".css")) ? "editor.css" : "assets/[name][extname]",
      },
    },
  },
});
