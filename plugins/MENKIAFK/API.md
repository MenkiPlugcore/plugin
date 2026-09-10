# MENKIAFK Public API v1

MENKIAFK v1.5.0 introduced the read-only Bukkit API, v1.5.1 added regression gates for that contract, and v1.6.0 promotes the same API surface to the Production Stable line.

## Runtime dependency

Add MENKIAFK as a dependency or soft dependency in the consuming plugin. If your plugin requires the API to function, use:

```yaml
depend: [MENKIAFK]
```

If the integration is optional, use:

```yaml
softdepend: [MENKIAFK]
```

Compile against the MENKIAFK JAR containing `store.menkiestes.menkiafk.api`. Do not shade or relocate MENKIAFK API classes into the consuming plugin.

## Obtain the API

```java
import store.menkiestes.menkiafk.api.MenkiAfkAPI;

MenkiAfkAPI afkApi = MenkiAfkAPI.get();
```

Equivalent ServicesManager lookup:

```java
MenkiAfkAPI afkApi = getServer().getServicesManager().load(MenkiAfkAPI.class);
if (afkApi == null) {
    // MENKIAFK is not enabled / service is unavailable.
}
```

## Read AFK state

```java
UUID uuid = player.getUniqueId();

boolean afk = afkApi.isAfk(uuid);
long totalMillis = afkApi.getTotalAfkTime(uuid);
int onlineAfk = afkApi.getAfkCount();

afkApi.getCurrentSession(uuid).ifPresent(session -> {
    String reason = session.reason();
    long startedAt = session.startedAt();
    long duration = session.durationMillis(System.currentTimeMillis());
    switch (session.type()) {
        case MANUAL -> { }
        case AUTO -> { }
    }
});
```

## Read statistics

```java
var stats = afkApi.getStatistics(player.getUniqueId());

long today = stats.todayMillis();
long week = stats.weekMillis();
long total = stats.totalMillis();
long longest = stats.longestMillis();
int sessions = stats.sessions();
int manual = stats.manualSessions();
int automatic = stats.autoSessions();
long lastAfkAt = stats.lastAfkAt();
```

`AfkStatisticsSnapshot` follows the same live projection behavior as MENKIAFK commands/placeholders: an active session is reflected once it satisfies the configured minimum statistics duration. Snapshots are immutable.

## Events

### PlayerEnterAfkEvent

Fired after MENKIAFK has committed the new AFK state.

```java
@EventHandler
public void onEnterAfk(PlayerEnterAfkEvent event) {
    Player player = event.getPlayer();
    AfkSessionSnapshot session = event.getSession();
}
```

### PlayerLeaveAfkEvent

Fired after MENKIAFK has removed the AFK state during normal runtime. It also fires when an AFK player quits or is kicked.

```java
@EventHandler
public void onLeaveAfk(PlayerLeaveAfkEvent event) {
    Player player = event.getPlayer();
    long duration = event.getDurationMillis();
    AfkSessionSnapshot session = event.getSession();
}
```

Lifecycle events are informational and intentionally not cancellable. MENKIAFK does not emit leave events during plugin/server disable; shutdown only finalizes persistence and clears runtime state.

## Public types

Stable API v1 compatibility surface:

- `store.menkiestes.menkiafk.api.MenkiAfkAPI`
- `store.menkiestes.menkiafk.api.AfkSessionSnapshot`
- `store.menkiestes.menkiafk.api.AfkStatisticsSnapshot`
- `store.menkiestes.menkiafk.api.AfkSessionType`
- `store.menkiestes.menkiafk.api.event.PlayerEnterAfkEvent`
- `store.menkiestes.menkiafk.api.event.PlayerLeaveAfkEvent`

Classes under `store.menkiestes.menkiafk.api.internal` are implementation details and are not part of the compatibility contract.

## v1 stable contract

The CI directly verifies that the v1 query methods remain available, `MANUAL`/`AUTO` enum values remain present, lifecycle events remain non-cancellable, and all required public API classes are packaged in the final release JAR.

The v1 API remains intentionally read-only. It does not expose force-AFK, force-return, mutable internal sessions, YAML objects, or internal managers. Breaking public API changes should be reserved for a future major version.
