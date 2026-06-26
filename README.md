# PlayitProxyProtocol

PlayitProxyProtocol is a server-side Minecraft Forge mod for **Minecraft 1.21.1** / **Forge 52.x** that adds HAProxy Proxy Protocol support for servers running behind TCP proxies such as [playit.gg](https://playit.gg/).

It reads Proxy Protocol v1 and v2 before Minecraft's normal Netty packet decoder, extracts the real client IP/port, and updates the remote address used by the Minecraft server connection.

## Compatibility

- Minecraft: **1.21.1**
- Loader: **Forge 52.x**
- Java: **21**
- Side: **server only**
- Clients do not need to install this mod.

## Features

- Supports HAProxy Proxy Protocol v1 and v2.
- Compatible with playit.gg TCP tunnels that send Proxy Protocol headers.
- Rejects direct non-proxy connections when configured to do so.
- Supports proxy source allowlisting with IP and CIDR entries.
- Logs detected Proxy Protocol connections with the proxy address and real player address.
- Rate-limits repeated rejection logs so direct pings do not spam the console.

## Configuration

Forge creates the config file at:

```text
config/playitproxyprotocol.toml
```

Default configuration:

```toml
enabled=true
blockRegularConnections=true
onlyAllowFrom=["*"]
protocolVersion="both"
```

Options:

- `enabled`: enables or disables Proxy Protocol handling.
- `blockRegularConnections`: when `true`, connections without a Proxy Protocol header are rejected.
- `onlyAllowFrom`: `["*"]` allows any proxy source, or use a list of IP/CIDR entries such as `["127.0.0.1", "10.0.0.0/8"]`.
- `protocolVersion`: accepted values are `"both"`, `"v1"`, or `"v2"`.

## Installation

1. Build the jar:

```bash
./gradlew build
```

2. Copy the generated jar from `build/libs/` to the server's `mods/` directory.
3. Start the Forge 1.21.1 server once to generate `config/playitproxyprotocol.toml`.
4. Configure your proxy or playit.gg tunnel to send Proxy Protocol headers.

## Important Notes

When `blockRegularConnections=true`, direct connections that do not include a Proxy Protocol header will be rejected. This is expected and helps prevent spoofing when the server is meant to be reached only through the trusted proxy.

If you want to allow both proxied and direct local connections, set:

```toml
blockRegularConnections=false
```

## Building

This project uses the Forge MDK Gradle setup and targets Java 21:

```bash
./gradlew build
```

The compiled mod jar is written to `build/libs/`.
