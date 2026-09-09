# NMEA application stories

The NMEA app opens on the current flow of real data. Its connection, output and service settings remain the existing marine engine's complete screens. Both the upper-left Back control and Shell Back return from those screens to the flow first. All new copy follows the Shell's Chinese / English language setting.

## Connect the boat

The sailor sees their saved TCP / UDP endpoint, its actual connection phase, the age of the latest received packet, and the engine's valid / unparsed sentence counts. Connection settings open the existing profile and reconnect controls. Returning shows the live result. An open connection waiting for data does not appear as fresh reception.

## Understand where data goes

The flow links boat input to the data shared by Chart, instruments and recording. The count of fresh common fields comes from `VesselDataSnapshot` values and freshness, with a direct link to boat data / sources. The two outbound paths are shown separately: selected Phone/App data to the boat network, and selected Phone/App data to clients of the local TCP service. The screen does not claim that every Boat input sentence is forwarded to either output.

Boat output uses `phonePositionOutputStatus.connectionState`, `writtenSentences` and `lastWriteElapsed`. Local service uses `nmeaSharing.state`, `clientCount`, `sentSentences` and `lastOutputElapsed`. These are runtime transport and completed-write observations; neither `desiredEnabled` nor a configured/requested switch is treated as proof of sending. A completed socket write is described as written, without claiming receipt or use by an instrument.

## Read the wire

The traffic Pivot shows the engine's bounded raw RX buffer, actual boat TX buffer or actual client-write buffer. The newest sentence is first. A case-insensitive text filter finds sentence type, talker ID or contents. Pause freezes only the three displayed buffers; the existing connections, sharing and recordings continue. Resume returns to current buffers. Long press uses native text selection and copy. No synthetic sentences or per-line timestamps are invented.

## Connect another device

The clients Pivot shows listening status, actual local addresses and TCP port, connected client addresses and completed-write count per client. Zero clients is explicitly listening with no clients. Stopping the local service uses the existing service command and leaves boat input / boat output ownership unchanged. Configuration and selected shared fields remain in the existing output settings.

## Manual review

- With the TCP fixture receiving, open NMEA and compare endpoint, valid count and freshness with the existing connection details.
- Swipe to traffic, filter `RMC`, pause, verify the visible lines stay frozen while flow counts keep advancing, then resume.
- Open connection settings and a nested settings page; virtual Back closes the nested page, then returns to NMEA flow, then follows the Shell's normal app history.
- Start the existing local service with no receiver: the new home must show listening / zero clients / no completed writes. Connect a receiver and compare actual client/write observations. Stop only that service and verify input remains connected.
- Review narrow-screen Chinese and English layout, including long hostnames and raw sentence selection.

This screen creates no socket, publisher, GPS request or background service. It subscribes to the process-wide marine engine already used by the other applications.
