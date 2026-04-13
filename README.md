# ForeverWorld

ForeverWorld is a Paper plugin for season-style overworld resets. A reset moves spawn forward, archives player inventories into physical chests near the old season area, and sends players into the new season.

## What It Does

- Tracks a current season plus archived seasons in `seasons.yml`
- Moves each new season spawn forward by a configurable distance
- Builds a stone-brick spawn platform at the new season location
- Archives online player inventories and ender chest contents into labeled chest pods
- Queues offline players so they are archived and moved on next join
- Requires an explicit confirmation step before a reset runs
- Scopes resets to one configured managed overworld
- Lets admins dry-run placement and compatibility checks before confirming a reset

## Commands

- `/season status` shows the current season and queued resets
- `/season reset <season-name>` arms a reset
- `/season confirm` runs the armed reset
- `/season dryrun [season-name]` previews affected players, integrations, and placement issues

Permission: `foreverworld.admin` (default: `op`)

## Reset Walkthrough

1. An admin runs `/season reset <season-name>`.
2. The reset stays armed for the configured confirmation window.
3. `/season confirm` begins the season rollover.
4. The current season is marked as archived.
5. A new spawn is chosen farther along the X axis.
6. A fresh spawn platform is created at that location.
7. Online players have their inventory and ender chest archived into physical chests near the old season area.
8. Online players are reset and teleported to the new spawn.
9. Offline players are queued and processed automatically the next time they join.

## Config

```yml
world-name: ""
season-distance-blocks: 50000
archive-anchor-offset-x: 0
archive-anchor-offset-z: 24
archive-player-spacing: 6
archive-row-width: 8
spawn-platform-radius: 2
confirmation-seconds: 60
update-world-spawn: false
only-reset-managed-world-players: true
placement-policy: abort
check-worldguard-protection: true

integrations:
  placeholderapi:
    register-expansion: true
  essentialsx:
    warn-on-spawn-conflict: true
    player-reset-commands: []
  auraskills:
    player-reset-commands: []
  mcmmo:
    player-reset-commands: []
  dynmap:
    update-markers: true
    marker-set-id: foreverworld
    marker-set-label: ForeverWorld
    current-marker-id: foreverworld-current
    current-marker-label: CurrentSeasonSpawn
    archive-marker-id-prefix: foreverworld-archive-
    archive-marker-label-prefix: SeasonArchive
    marker-icon: default
  bluemap:
    season-reset-commands: []
  minecraft:
    reset-advancements: true
```

- Set `world-name` explicitly on every server, especially with Multiverse.
- Plugin state is stored in `plugins/ForeverWorld/seasons.yml`.
- `update-world-spawn` defaults to `false` so ForeverWorld does not compete with spawn-management plugins.
- `placement-policy: abort` cancels the reset if the spawn platform or archive pods would overwrite occupied space.
- If WorldGuard is installed and `check-worldguard-protection` is enabled, protected regions block the reset during dry-run and confirm.

## PlaceholderAPI

When [PlaceholderAPI](https://www.spigotmc.org/resources/6245/) is installed, ForeverWorld registers an internal expansion (`%foreverworld_*%`):

| Placeholder | Description |
|---|---|
| `%foreverworld_current_season%` | Current season name |
| `%foreverworld_current_index%` | Current season index |
| `%foreverworld_managed_world%` | Configured managed world name |
| `%foreverworld_pending_resets%` | Number of pending offline resets |
| `%foreverworld_archived_seasons%` | Count of archived seasons |
| `%foreverworld_next_spawn_x%` | Next spawn X coordinate |
| `%foreverworld_next_spawn_y%` | Next spawn Y coordinate |
| `%foreverworld_next_spawn_z%` | Next spawn Z coordinate |
| `%foreverworld_current_spawn_x%` | Current spawn X coordinate |
| `%foreverworld_current_spawn_y%` | Current spawn Y coordinate |
| `%foreverworld_current_spawn_z%` | Current spawn Z coordinate |
| `%foreverworld_player_pending_reset%` | `true` if the querying player has a pending reset |

Registration is controlled by `integrations.placeholderapi.register-expansion`.

## Integration Hooks

ForeverWorld can dispatch console commands when players are reset or when a season completes. Commands are templates with placeholders that are resolved at runtime:

**Per-player reset** (`integrations.<plugin>.player-reset-commands`):
- `{player}`, `{uuid}` — player identity
- `{new_season}`, `{new_season_index}` — destination season
- `{archived_season}`, `{archived_season_index}` — archived season
- `{new_spawn_x}`, `{new_spawn_y}`, `{new_spawn_z}` — new spawn coordinates
- `{archive_x}`, `{archive_y}`, `{archive_z}` — archive chest coordinates

**Season reset** (`integrations.bluemap.season-reset-commands`):
- Same placeholders as above, without `{player}`.

Example EssentialsX hook:
```yaml
integrations:
  essentialsx:
    player-reset-commands:
      - "essentials:delhome {player} season_{archived_season_index}"
      - "essentials:sethome {player} season_{new_season_index}"
```

### dynmap Markers

When dynmap is installed and `integrations.dynmap.update-markers` is `true`, ForeverWorld automatically:
- Creates a marker set `foreverworld` / `ForeverWorld`
- Places a `default` icon marker at the current season spawn
- Places an archive marker at each archived season's anchor location

Configure the marker IDs, labels, icon, and label prefix under `integrations.dynmap.*`.

### Compatibility Warnings

`/season dryrun` reports warnings when:
- EssentialsX is installed with `update-world-spawn: true`
- AuraSkills/mcMMO are installed but no `player-reset-commands` are configured (their progression will persist between seasons)
- dynmap is installed but `update-markers` is disabled
- BlueMap is installed but no `season-reset-commands` are configured

## Notes

- Archive pods are placed near the old season spawn using the configured offsets.
- Player archive layout is controlled by `archive-player-spacing` and `archive-row-width`.
- Offline resets persist until the affected player logs in.
- Only players currently in the managed world are reset during a season rollover.
