# Lantra - Android

Lantra is an Android application that allows you to discover devices (“speakers”) on your local
network and stream audio from your device to them in real-time. It integrates WebSocket-based device
discovery and TCP-based audio streaming.

## Features

#### Speaker Discovery

- Automatically detects devices on the same Wi-Fi network using mDNS/Bonjour.

- Live list of available speakers in the local network (coming soon).

#### Audio Streaming

- Streams PCM audio using AudioPlaybackCapture or microphone fallback.

- Supports stereo, 44.1kHz PCM streaming over TCP.
