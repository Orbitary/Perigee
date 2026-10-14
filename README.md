<div align="center">

# 🛰️ Perigee

*- Discord Rich Presence Client for Java -*

[![](https://img.shields.io/github/license/Orbitary/Perigee?style=flat-square)](LICENSE)
[![](https://img.shields.io/github/actions/workflow/status/Orbitary/Perigee/publish.yml?style=flat-square)](https://github.com/Orbitary/Perigee/actions/workflows/publish.yml)
[![](https://img.shields.io/badge/java-21%2B-orange?style=flat-square)](#-requirements)
[![](https://img.shields.io/badge/dependencies-0-brightgreen?style=flat-square)](#-installation)

</div>

* * *

# ✨ Introduction

**Perigee** is a pure-Java client for [Discord Rich Presence](https://docs.discord.com/developers/rich-presence/overview)

- **Lean:** Zero dependencies, built-in parsing, no native libraries
- **Async by default:** Every call returns a `CompletableFuture`
- **Self-healing:** Reconnects with backoff and resends your presence when Discord restarts
- **Modern:** Uses Discord's [Social SDK](https://docs.discord.com/developers/discord-social-sdk/overview) protocol, unlike similar clients


## 🔌 Requirements

- **Java 21** or newer
- The **Discord desktop client** running on the same machine
- A Discord **application ID** from the [Developer Portal](https://discord.com/developers/applications)


## ⚙️ Installation

<details open>
<summary>Gradle (Kotlin)</summary>

```kotlin
repositories {
    maven("https://repo.bitsquidd.xyz/repository/bit/")
}

dependencies {
    implementation("xyz.orbitary:perigee:0.0.1")
}
```

</details>

<details>
<summary>Gradle (Groovy)</summary>

```groovy
repositories {
    maven { url 'https://repo.bitsquidd.xyz/repository/bit/' }
}

dependencies {
    implementation 'xyz.orbitary:perigee:0.0.1'
}
```

</details>

<details>
<summary>Maven</summary>

```xml

<repositories>
    <repository>
        <id>repo.bitsquidd.xyz</id>
        <url>https://repo.bitsquidd.xyz/repository/bit/</url>
    </repository>
</repositories>

<dependencies>
<dependency>
    <groupId>xyz.orbitary</groupId>
    <artifactId>perigee</artifactId>
    <version>0.0.1</version>
</dependency>
</dependencies>
```

</details>


# 📖 Examples

## Quick start

```java
PerigeeClient client = PerigeeClient.builder("123456789012345678").build();

client.on(Ready.class, ready -> Logger.info("Connected as " + ready.user().username()));

client.connect();

client.setActivity(activity -> activity
    .details("Exploring the lobby")
    .state("Solo")
    .startedAt(Instant.now())
    .largeImage("logo", "My Game")
    .button("Website", "https://example.com"));
```

- `connect()` can be called before Discord is running.
- With auto-reconnect on, it stays pending until Discord appears, and any activity you set in the meantime is sent as soon as the handshake finishes.
- Perigee's threads are daemon threads, so keep your application alive for as long as you want presence shown.


## Activities

### Building an Activity

```java
Activity activity = Activity.of(a -> a
    .type(ActivityType.PLAYING)
    .details("Ranked - Capture the Flag")
    .state("In a party")
    .timestamps(Timestamps.since(Instant.now()))
    .party("lobby-42", 2, 4)
    .largeImage("map_dunes", "Dunes")
    .smallImage("rank_gold", "Gold II")
    .joinSecret("join-token")
    .button("Watch live", "https://example.com/live"));

client.setActivity(activity);
```

- Unset fields are left out of what Discord receives.
- Discord documents are validated before sending.


### Updating and clearing

```java
client.setActivity(a -> a
  .details("In the menu")
);

client.clearActivity();
```


## Events

```java
client.on(Ready.class, event -> Logger.info("Hello " + event.user().username()));
client.on(Disconnected.class, event -> Logger.info("Lost Discord, reconnecting: " + event.reconnecting()));

client.on(Join.class, event -> teleportToLobby(event.secret()));
client.on(Spectate.class, event -> spectate(event.secret()));

client.on(JoinRequest.class, event -> {
    if (lobbyHasSpace()) client.acceptJoinRequest(event.user().id());
    else client.rejectJoinRequest(event.user().id());
});
```


## Configuration

```java
PerigeeClient client = PerigeeClient.builder("123456789012345678")
    .autoReconnect(true)
    .reconnectDelay(Duration.ofSeconds(1), Duration.ofSeconds(30))
    .searchDirectory("/custom/ipc/dir")
    .build();
```

---

Made with 🪐 by [Orbitary](https://github.com/Orbitary)
