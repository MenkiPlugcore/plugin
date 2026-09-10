# MENKIAFK Public API v1

MENKIAFK v1.5.0 exposes a small read-only Bukkit API for plugins that want to consume AFK state without parsing commands, placeholders, or `stats.yml`.

## Runtime dependency

Add MENKIAFK as a dependency or soft dependency in the consuming plugin. If your plugin requires the API to function, use:

```yaml
depend: [MENKIAFK]
```

If the integration is optional, use:

```yaml
softdepend: [MENKIAFK]
```

The consuming project must compile against the MENKIAFK JAR that contains the `store.menkiestes.menkiafk.api` package. Do not shade or relocate MENKIAFK API classes into the consuming plugin.

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

`AfkStatisticsSnapshot` follows the same live projection behavior as MENKIAFK commands/placeholders: an active session is reflected once it satisfies the configured minimum statistics duration. The snapshot is immutable.

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

Lifecycle events are informational and are intentionally not cancellable. MENKIAFK does not emit leave events during plugin/server disable; shutdown only finalizes persistence and clears runtime state.

## Public types

Stable API surface introduced in v1.5.0:

- `store.menkiestes.menkiafk.api.MenkiAfkAPI`
- `store.menkiestes.menkiafk.api.AfkSessionSnapshot`
- `store.menkiestes.menkiafk.api.AfkStatisticsSnapshot`
- `store.menkiestes.menkiafk.api.AfkSessionType`
- `store.menkiestes.menkiafk.api.event.PlayerEnterAfkEvent`
- `store.menkiestes.menkiafk.api.event.PlayerLeaveAfkEvent`

Classes under `store.menkiestes.menkiafk.api.internal` are implementation details and are not part of the compatibility contract.

## Design contract

The v1 API is intentionally read-only. It does not expose force-AFK, force-return, raw mutable session objects, YAML objects, or internal managers. This keeps integrations isolated from MENKIAFK persistence/lifecycle internals and leaves the core standalone and lightweight.
