# ImageFrame Client (NeoForge 26.2)

Community port of [ImageFrame Client](https://github.com/LOOHP/ImageFrameClient) to **NeoForge** for **Minecraft 26.2**.

Complementary client mod for servers with [ImageFrame](https://github.com/LOOHP/ImageFrame) to display HD and full color images!

## Status

This is an unofficial port. The original mod is built against Fabric; this branch is the same feature set rebuilt for NeoForge.

| | |
|---|---|
| Minecraft | 26.2 |
| NeoForge | 26.2.0.88+ |
| Java | 25 |
| Side | Client only |

## Differences from the Fabric version

* Built with [ModDevGradle](https://docs.neoforged.net/docs/gettingstarted/moddevgradle/) instead of Fabric Loom.
* **MidnightLib is a separate dependency** instead of being bundled (jar-in-jar). Install [MidnightLib](https://modrinth.com/mod/midnightlib) for NeoForge alongside this mod.
* The ModMenu config screen entry is replaced by NeoForge's own config screen: **Mods -> ImageFrame Client -> Config**.
* All payload types are registered as `optional`, so the client can still join servers that do not run NeoForge (vanilla or Fabric with ImageFrame).

Everything else is unchanged: the same payloads, the same configuration options (`imageframeclient.json`), the same tooltip previews, and the same two mixins (`ItemMixin`, `MapRendererMixin`).

## Installing

1. Install [NeoForge 26.2](https://neoforged.net/).
2. Drop the mod jar into `mods/`.
3. Install [MidnightLib](https://modrinth.com/mod/midnightlib) (NeoForge) into `mods/` as well.
4. Join a server that runs [ImageFrame](https://github.com/LOOHP/ImageFrame). A toast appears when the server confirms support.

## Building from source

```bash
git clone -b neoforge-26.2 https://github.com/not-oshi/ImageFrameClient.git
cd ImageFrameClient
./gradlew build
```

The jar is written to `build/libs/`.

To run a development client:

```bash
./gradlew runClient
```

## Credits

Original mod by [LOOHP](https://github.com/LOOHP) - please support the original project.
