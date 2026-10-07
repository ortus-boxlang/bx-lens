---
title: Contributing
order: 2
description: Build, test and contribute to BX Lens.
icon: lucide:git-pull-request
---

# Contributing

Read the [contributing guide](https://github.com/ortus-boxlang/bx-lens/blob/main/CONTRIBUTING.md) first. Report issues at [github.com/ortus-boxlang/bx-lens/issues](https://github.com/ortus-boxlang/bx-lens/issues).

## Build and test

Use the Gradle wrapper from the repository root.

```bash
./gradlew test            # unit tests
./gradlew spotlessCheck   # check formatting
./gradlew spotlessApply   # fix formatting
./gradlew build           # compile, test and package the module
```

Java code follows the Ortus Java formatter. Run `spotlessApply` before you commit.

## Harness and end to end tests

The `harness/` folder holds a small app that exercises every panel. Playwright drives it from the browser. Run the suite from the folder that holds `package.json`:

```bash
npm install
npm run e2e
```

The same run generates the documentation screenshots in `docs/assets/screenshots/`.

## Documentation

The docs are a [bx-sites](https://github.com/ortus-boxlang/bx-sites) project. The config is `bxsites.yaml` at the repository root, and the pages live in `docs/`.

```bash
install-bx-module bx-sites
bxSites serve    # live preview
bxSites build    # writes site/
```

Do not commit `site/` or other build output.

## Pull requests

- Add tests for features and fixes.
- Keep collectors free of shared state. Use the per-request slot.
- Collectors must never throw into a request.
- Update [Changelog](changelog.md).
