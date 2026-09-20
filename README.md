# Verstile Client

Verstile is a modular Fabric client for Minecraft Java Edition 1.21.11. It includes a compact searchable ClickGUI, configurable combat and movement assists, a draggable HUD editor, profiles, keybinds, and 3D render tools.

> Use the client only where the server rules permit it. You are responsible for following the rules of every server you join.

## Download

Download `verstile-client-1.0.31.jar` from the [latest release](https://github.com/merthileyapiyor-lab/Verstile-Client/releases/latest). Do not download builds re-uploaded by unknown third parties.

## Gameplay

[Watch/download the gameplay video](https://github.com/merthileyapiyor-lab/Verstile-Client/releases/download/v1.0.31/Game.PLay.verstile.mp4)

## Requirements

- Minecraft Java Edition 1.21.11
- Fabric Loader 0.18.1 or newer
- Fabric API 0.141.4+1.21.11
- Java 21

## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Place Fabric API and `verstile-client-1.0.31.jar` in your Minecraft `mods` folder.
3. Start Minecraft with the Fabric profile.
4. Open the ClickGUI with Right Shift.

## Highlights

- Searchable category-based ClickGUI with saved module settings
- Toggle and hold keybinds, including mouse buttons
- Combat utilities including Aim Assist, Triggerbot, AutoMace, and crystal tools
- Movement utilities including Scaffold, MLG, Clutch, SafeWalk, FreeCam, and NoFall
- 3D ESP, Storage ESP, Tracers, projectile prediction, explosions, and radar
- Draggable HUD editor with target, performance, coordinates, potion, ping, and friends widgets
- Friends and BedWars auto-teammate support
- Client and external Injection GUI modes where supported

## Privacy and public build

The public source and release do not contain private Telegram credentials. Remote reporting and private update infrastructure are disabled unless a maintainer explicitly configures them locally. The public build also excludes server-disruption functionality.

## Building

```powershell
.\gradlew.bat build
```

The remapped JAR will be created under `build/libs/`.

## Authors

- mertk2134
- avcibora

## License

This project is distributed under the CC0 1.0 Universal license included in [LICENSE](LICENSE).
