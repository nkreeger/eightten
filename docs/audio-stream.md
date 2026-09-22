# Sports Radio 810 Audio Stream

The AmperWave player page for Sports Radio 810 WHB is:

```text
https://player.amperwave.net/8008
```

The page loads its station configuration from:

```text
https://player.amperwave.net/station?uid=8008
```

In that JSON response, the audio URLs are under `data`:

- `audioHls` contains the preferred AAC HLS stream.
- `audioHtml` contains an MP3 playlist alternative.

## Preferred stream

Use this stable HLS manifest URL for Android playback:

```text
https://live.amperwave.net/manifest/unionbroadcasting-whbamaac-hlsc2.m3u8?source=v7player
```

## MP3 alternative

```text
https://live.amperwave.net/playlist/unionbroadcasting-whbammp3-ibc2.m3u?source=v6player
```

The MP3 URL returns an M3U playlist containing one or more active stream
servers. The HLS URL similarly redirects to an active server and returns an
HLS manifest.

Always start playback with one of the stable `live.amperwave.net` URLs above.
Do not save the resolved `prod-*` server URLs: they are load-balanced and may
contain temporary session identifiers.

## Inspect the current configuration

```shell
curl -fsSL 'https://player.amperwave.net/station?uid=8008'
```

If `jq` is installed, extract only the audio fields with:

```shell
curl -fsSL 'https://player.amperwave.net/station?uid=8008' \
  | jq '.data | {audioHls, audioHtml}'
```
