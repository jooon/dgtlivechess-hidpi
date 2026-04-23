# dgtlivechess-hidpi

This repo patches DGT LiveChess so it can run scaled on HiDPI displays.

## Files

- [build.sh](build.sh)
  Downloads the runtime dependencies and compiles the compatibility classes into `./build/`.
- [dgtlivechess](dgtlivechess)
  Runtime wrapper. Uses the files produced by `./build.sh`.
- [compat-src/com/novotea/entity/ProxyUtil.java](compat-src/com/novotea/entity/ProxyUtil.java)
  Fixes Java 9+ default-method proxy dispatch.
- [compat-src/com/novotea/entity/ProxyEntityAccess.java](compat-src/com/novotea/entity/ProxyEntityAccess.java)
  Replaces the old proxy bootstrap path so it links cleanly on Java 11+.
- [compat-src/com/novotea/ui/core/AbstractColumnUI.java](compat-src/com/novotea/ui/core/AbstractColumnUI.java)
  Replaces the removed JavaFX 8 internal `TableColumn.impl_setReorderable(...)` call.

## Prerequisites

- Installed DGT LiveChess at `/opt/DGTLiveChess`
- JDK 11+ on `PATH` (`java`, `javac`)
- `python3`
- internet access when running `./build.sh`

The generated `build/` directory is the only place that needs downloaded dependencies. Once `./build.sh` has completed, `./dgtlivechess` does not need internet.

Runtime note:

- `./dgtlivechess` prefers a local Java 11 runtime if one is installed
- newer JDKs can still be forced with `JAVA=/path/to/java ./dgtlivechess`, but Java 11 is the safest tested path

## Build

Run:

```bash
./build.sh
```

That downloads OpenJFX and JAXB jars into `./build/`, extracts the app's embedded `application.jar`, and compiles the compatibility patch classes.

## Run

Launch the app with:

```bash
./dgtlivechess
```

What the wrapper does at runtime:

1. Verifies that `./build.sh` has already produced the needed jars and class files
2. Builds a patched cached copy of `/opt/DGTLiveChess/app/package.jar` under `./build/cache/`
3. Patches any active `~/.dgt_livechess/boot/*.jar` application image
4. Launches DGT LiveChess on Java 11+ with the downloaded OpenJFX runtime and UI scaling enabled

## Patched Breakages

The current compatibility layer patches these Java 8 to Java 11+/OpenJFX incompatibilities:

- `UndeclaredThrowableException` / `IllegalAccessException` in `com.novotea.entity.ProxyUtil`
  Cause: old default-method proxy dispatch using pre-Java-9 `MethodHandles.Lookup` behavior
- `NoSuchMethodError` for `javafx.scene.control.TableColumn.impl_setReorderable(boolean)`
  Cause: JavaFX 8 internal API removed in JavaFX 11+

## Known Risk

This app was built against Java 8 and JavaFX 8. More breakages may still exist in less-used windows or controls that rely on removed JavaFX internals.

If another window crashes, the normal workflow is:

1. run `./dgtlivechess`
2. trigger the failing UI path
3. capture the stderr stack trace
4. add another focused compatibility patch under `compat-src/`
5. rerun `./build.sh`
