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
```

- Set `world-name` explicitly on every server, especially with Multiverse.
- Plugin state is stored in `plugins/ForeverWorld/seasons.yml`.
- `update-world-spawn` defaults to `false` so ForeverWorld does not compete with spawn-management plugins.
- `placement-policy: abort` cancels the reset if the spawn platform or archive pods would overwrite occupied space.
- If WorldGuard is installed and `check-worldguard-protection` is enabled, protected regions block the reset during dry-run and confirm.

## Notes

- Archive pods are placed near the old season spawn using the configured offsets.
- Player archive layout is controlled by `archive-player-spacing` and `archive-row-width`.
- Offline resets persist until the affected player logs in.
- Only players currently in the managed world are reset during a season rollover.
