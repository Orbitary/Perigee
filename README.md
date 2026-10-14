<div align="center">

# 🛰️ Perigee

*- Discord Rich Presence Client for Java -*

[![](https://img.shields.io/github/license/Orbitary/Perigee?style=flat-square)](LICENSE)
[![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo.bitsquidd.xyz%2Frepository%2Fbit%2Fxyz%2Forbitary%2Fperigee%2Fmaven-metadata.xml&label=version&style=flat-square)](#-installation)
[![](https://img.shields.io/github/actions/workflow/status/Orbitary/Perigee/publish.yml?style=flat-square)](https://github.com/Orbitary/Perigee/actions/workflows/publish.yml)
[![](https://img.shields.io/badge/java-21%2B-orange?style=flat-square)](#-requirements)

</div>

* * *

# ✨ Introduction

**Perigee** is a pure-Java library for [Discord Rich Presence](https://docs.discord.com/developers/rich-presence/overview).

- **Lean:** Zero dependencies, built-in parsing, no native libraries
- **Async by default:** Every call returns a `CompletableFuture`
- **Self-healing:** Reconnects with backoff and resends your presence when Discord restarts
- **Modern:** Uses Discord's [Social SDK](https://docs.discord.com/developers/discord-social-sdk/overview) protocol, unlike similar clients
- **Cross-platform:** Windows named pipes, Unix sockets, and Flatpak and Snap Discord installs

> Discord's own [Social SDK](https://docs.discord.com/developers/discord-social-sdk/overview) is recommended for new native games, but it ships only for C++, Unity and Unreal.


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

client.on(Ready.class, ready -> System.out.println("Connected as " + ready.user().username()));

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
- The limits Discord documents (two buttons, button label and URL lengths, party sizes) are validated before sending.


### Updating and clearing

```java
client.setActivity(a -> a
  .details("In the menu")
);

client.clearActivity();
```

- Only the newest update is sent when you exceed Discord's rate limit; every update it replaced completes together with it.


## Events

```java
client.on(Ready.class,        event -> System.out.println("Hello " + event.user().username()));
client.on(Disconnected.class, event -> System.out.println("Lost Discord, reconnecting: " + event.reconnecting()));

client.on(Join.class,         event -> teleportToLobby(event.secret()));
client.on(Spectate.class,     event -> spectate(event.secret()));

client.on(JoinRequest.class,  event -> {
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
