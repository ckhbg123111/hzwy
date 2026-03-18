# Realtime Protocol

The prototype server exposes a raw WebSocket endpoint:

```text
ws://localhost:8080/ws/realtime?token=<auth-token>
```

## Client Messages

### Subscribe

```json
{
  "type": "subscribe",
  "topic": "games/<matchId>"
}
```

Supported topic patterns:

- `users/<userId>`
- `parties/<partyId>`
- `rooms/<roomId>`
- `games/<matchId>`

### Unsubscribe

```json
{
  "type": "unsubscribe",
  "topic": "games/<matchId>"
}
```

### Game Action

```json
{
  "type": "game.action",
  "payload": {
    "gameId": "match_xxx",
    "actionType": "TAKE_TILES",
    "clientSeq": "client-001",
    "payload": {
      "sourceType": "FACTORY",
      "sourceIndex": 0,
      "color": "BLUE",
      "targetLine": 1
    }
  }
}
```

## Server Events

- `system.notice`: connection and subscription notices
- `party.invite`: target user was added to a party
- `party.updated`: party membership or state changed
- `queue.status`: queue waiting or matched updates
- `room.created`: room created
- `room.updated`: room roster/status changed
- `match.found`: a queue or room start produced a match
- `game.snapshot`: latest public game state
- `game.actionAck`: action accepted
- `game.actionRejected`: action rejected with reason
- `game.result`: final match result

### `game.snapshot`

```json
{
  "type": "game.snapshot",
  "payload": {
    "gameId": "match_xxx",
    "gameType": "AZUL",
    "phase": "SELECT_TILES",
    "stateVersion": 4,
    "currentPlayerId": "user_xxx",
    "deadlineAt": "2026-03-18T12:00:00Z",
    "state": {
      "roundNumber": 2,
      "factories": [["BLUE", "BLUE", "RED", "WHITE"]],
      "centerTiles": ["BLACK", "YELLOW"],
      "firstPlayerMarkerAvailable": true,
      "bagCount": 68,
      "discardCount": 12,
      "players": [
        {
          "userId": "user_xxx",
          "score": 8,
          "patternLines": [
            { "capacity": 1, "color": null, "count": 0 }
          ],
          "wall": [[false, false, false, false, false]],
          "floorTiles": [],
          "hasFirstPlayerMarker": false
        }
      ]
    }
  }
}
```

### `game.actionAck`

```json
{
  "type": "game.actionAck",
  "payload": {
    "gameId": "match_xxx",
    "clientSeq": "client-001",
    "stateVersion": 5,
    "automatic": false
  }
}
```

### `game.actionRejected`

```json
{
  "type": "game.actionRejected",
  "payload": {
    "gameId": "match_xxx",
    "clientSeq": "client-001",
    "reason": "It is not this player's turn."
  }
}
```

### `game.result`

```json
{
  "type": "game.result",
  "payload": {
    "matchId": "match_xxx",
    "roomId": "room_xxx",
    "gameType": "AZUL",
    "finishedAt": "2026-03-18T12:30:00Z",
    "summary": "Azul match finished.",
    "placements": [
      {
        "userId": "user_a",
        "displayName": "Alice",
        "place": 1,
        "score": 68,
        "completedRows": 2
      }
    ],
    "finalState": {
      "roundNumber": 6,
      "factories": [],
      "centerTiles": [],
      "firstPlayerMarkerAvailable": false,
      "bagCount": 0,
      "discardCount": 0,
      "players": []
    }
  }
}
```

## Snapshot Notes

- The server never sends the hidden Azul bag order.
- Public snapshots include factory tiles, center tiles, pattern lines, wall occupancy, floor tiles, current player, and deadline metadata.
- `game.result.finalState` reuses the same Azul public snapshot structure so clients can render the exact final board without reconstructing it from logs.
- Timeout handling is server-side. When a turn expires, the server can auto-submit a legal default action and broadcast the resulting snapshot.
