# Gieligotchi for RuneLite

Gieligotchi is a local cosmetic companion game inspired by early-2000s virtual
pets. Raise eggs through ordinary Old School RuneScape play, discover companions
and colours, build affection and collect scenes and toys.

## Features

- 71 companions and 781 collectible colour variants;
- progression from skilling, combat, quests and recognised activities;
- animated egg hatching and persistent hatch history;
- companion levels, wishes, affection, personalities and memories;
- a cosmetic shop with Gielinor-inspired backdrops and familiar toys;
- local per-profile progress with no account credentials or remote service;
- a Tamagotchi-style sidebar and optional movable in-game overlay;
- reduced-motion and overlay presentation settings;
- special recognition for max-level and deeply bonded companions.

## Development

```powershell
.\gradlew.bat clean test
.\gradlew.bat run
```

The development client uses isolated in-memory preferences. Jagex-account login
setup follows RuneLite's standard external-plugin workflow.

## Safety and scope

Gieligotchi observes RuneLite events but never injects input, changes game
actions, represents the companion as an in-world NPC, or affects RuneScape XP,
combat, drops or pet ownership. Progress stays on the local client. The Discord
button only opens the community invite in the user's browser.

BSD-2-Clause licensed.

## Artwork note

Companion and backdrop artwork was created specifically for Gieligotchi with
AI-assisted tools. We welcome collaboration with Old School RuneScape artists.
