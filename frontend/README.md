# GT2.0 frontend

This folder contains the Angular 22 application, Capacitor configuration, encrypted native SQLite integration, and Android project. See the repository [README](../README.md) for setup, enrollment, build, branding, and scope instructions.

Common commands:

```bash
npm ci
npm test
npm run build
npm run cap:sync
npm run android:build
```

The functional Patient and authentication flows are native-only by design. `npm start` is useful for layout work but is not a substitute for Android SQLite testing.
