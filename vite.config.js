import { defineConfig } from 'vite';

// base './' keeps asset paths relative so the same build works on
// GitHub Pages (served from /<repo>/) and inside the Capacitor Android app.
export default defineConfig({
  base: './',
  worker: { format: 'es' },
  build: { target: 'es2022' },
});
