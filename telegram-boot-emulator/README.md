# telegram-boot-emulator

A local Telegram for telegram-boot applications. The emulator runs inside your application and serves the
Telegram Bot API that the library uses, so all bots of the application work without Telegram, Docker or any
other external service. You play the user in a small web UI or through an HTTP API. Tests use the same API.

- Every bot the application starts is connected automatically: several bots at once, and bots enabled or
  disabled at runtime.
- Turned on and off by Spring Boot configuration only: no code changes in the application.
- Everything is kept in memory and lost on restart.

> **For development and tests only.** While the emulator is enabled, bots are not connected to Telegram.
> The emulator has no authentication, so it listens on `127.0.0.1` by default.

**Contents:**
[Quick start](#quick-start) ·
[Configuration](#configuration) ·
[How it works](#how-it-works) ·
[Web UI](#web-ui) ·
[User API](#user-api) ·
[Bot API](#bot-api) ·
[Testing](#testing-with-the-emulator) ·
[Limitations](#limitations)

## Quick start

**1. Add the dependency** next to the telegram-boot starter. Use `developmentOnly` so that the emulator is available
to `bootRun` but never packaged into the application jar, and `testImplementation` for tests:

```kotlin
dependencies {
    implementation("io.github.pm665:telegram-boot-spring-boot-starter:<version>")
    developmentOnly("io.github.pm665:telegram-boot-emulator:<version>")
    testImplementation("io.github.pm665:telegram-boot-emulator:<version>")
}
```

**2. Enable it**, typically in a Spring profile. Bot tokens only have to look like Telegram tokens; the username of
each bot comes from your `BotsService` (see [How bots are identified](#how-bots-are-identified)):

```yaml
---
spring:
  config:
    activate:
      on-profile: emulator
telegram-boot:
  emulator:
    enabled: true
```

**3. Run the application** with the profile and open the UI at <http://localhost:18790>:

```shell
gradle bootRun --args='--spring.profiles.active=emulator'
```

At startup the emulator logs where it listens:

```
WARN ... Telegram emulator is enabled: bots are NOT connected to Telegram. Bot API and UI: http://127.0.0.1:18790
INFO ... Telegram emulator registered bot @sample_bot
```

The [sample application](../examples/telegram-boot-sample-app) is set up this way: run it with the `emulator`
profile to chat with its two bots.

## Configuration

| Property | Default | Description |
|---|---|---|
| `telegram-boot.emulator.enabled` | `false` | Starts the emulator and connects all bots to it. |
| `telegram-boot.emulator.host` | `127.0.0.1` | Interface to listen on. Keep it local: the emulator has no authentication. |
| `telegram-boot.emulator.port` | `18790` | Port of the Bot API, the user API and the UI. `0` picks a free port. |
| `telegram-boot.emulator.bots[].token` | | Token of a bot known to the emulator in addition to the application's `BotsService`. |
| `telegram-boot.emulator.bots[].username` | | Username of that bot. |

- The port the emulator actually listens on is published as the `local.telegram-boot.emulator.port` property, like
  `local.server.port` of Spring Boot web applications. Use it with `port: 0` in tests.
- While the emulator is enabled, `telegram-boot.telegram.api-url` is ignored, and a warning is logged if it is set.
- The emulator replaces the `TelegramUrl` bean of the Telegram module. Nothing else in the application changes.

## How it works

The emulator is an HTTP server on its own port, started together with the Spring context, before the bots.
Bots reach it through the regular telegrambots client. The server offers three things:

| Path | What |
|---|---|
| `/bot<token>/<method>` | [Bot API](#bot-api) used by the bots of the application. |
| `/api/...` | [User API](#user-api) for the user side: chats, messages, button presses, history. |
| `/` | [Web UI](#web-ui) built on the user API. |

It runs on its own port, so it works in applications without a web server. It is also not affected by the
application's web security or web configuration.

### How bots are identified

A bot is identified by the token in the request path. On start, every bot calls `getMe`, and that request
registers it in the emulator. Telegram knows the username of a token from BotFather. The emulator looks it up:

1. in the application's `BotsService`: the active Telegram bot with this token. This also covers bots enabled at
   runtime;
2. in `telegram-boot.emulator.bots`. This is useful for tokens the application validates before it stores the bot;
3. otherwise the request is rejected with `401 Unauthorized`, as Telegram does for an invalid token.

Every other Bot API request needs a registered token and gets `401` otherwise. The bot id is the number before
`:` in the token, as in Telegram (`1001:sample` is bot `1001`).

### Chats, users and messages

- **Only a user starts a chat.** Starting a chat sends `/start`, like the *Start* button in Telegram. A bot writing
  to a chat nobody started gets `403 Forbidden: bot can't initiate conversation with a user`.
- **Chats are private chats of one user with one bot.** The chat id is the user id, as in Telegram. Users are
  identified by `userId`. Only the user who started the chat can write in it, press its buttons or read its
  history. Anyone else gets `403 Forbidden: chat <id> belongs to another user`.
- **The user profile** (`username`, `firstName`, `languageCode`) is sent to the bot with every update. Details
  missing in a request are kept from earlier requests of the same user.
- **Message ids** are unique within a chat. The user and the bot share one counter, as in Telegram.
- **The chat history** contains every entry received from the user and from the bot, in the order the emulator
  received them. This holds for concurrent requests too. Each entry has a `seq` number.
- **Buttons:** a user can press only an inline button that is currently on a bot message that is not deleted.
  As in Telegram, `editMessageText` removes the buttons unless it passes them again.
- **Updates** are queued per bot and delivered with `getUpdates` long polling. Updates sent while a bot is stopped
  are delivered when it starts again.

## Web UI

Open <http://localhost:18790> (or the configured host and port).

1. Enter a **User ID** and optionally a **Username**. Both are remembered by the browser.
2. Select a **Bot**. The dot next to it shows whether the bot is polling right now.
3. Press **Start** to start the chat, then type messages or press the buttons under the bot's messages.

| What | How it is shown |
|---|---|
| User messages | Blue bubbles on the right. |
| Bot text and rich messages | White bubbles on the left, with their inline buttons. Text is shown as sent: Markdown is not rendered. |
| Other activities | A short line with the action and brief info, on the side of the sender: `▶ Pressed "hello"`, `🔔 Callback answer: "Saved" (alert)`, `📷 Photo: caption (cat.jpg, 2048 bytes)`, `📍 Location 52.52, 13.405`, `✏️ Edited message #2: "new text"`, `🗑 Deleted message #4`, `📌 Pinned message #2`. |

Bubbles keep the text as it was sent, and later edits are listed in the history. Buttons always show the current
ones, so replaced buttons are not pressable anymore. Deleted messages are struck through. Each message shows its
`#messageId` and time. The history refreshes every second, and errors appear in a red bar at the bottom.

## User API

JSON over HTTP at `/api`. The user is identified by `userId` in every request. `{bot}` is the bot username without
`@`. `{chatId}` is the id of the private chat, which is the user id.

| Request | Body / query | Response |
|---|---|---|
| `GET /api/bots` | | Registered bots. |
| `GET /api/bots/{bot}/chats` | `?userId=` | The user's chat with the bot, if started (a list with zero or one chat). |
| `POST /api/bots/{bot}/chats` | `{"userId", "username"?, "firstName"?, "languageCode"?}` | Starts the chat and sends `/start`: `201`. For a started chat, nothing is sent: `200`. |
| `POST /api/bots/{bot}/chats/{chatId}/messages` | `{"userId", "text"}` | Sends a text message. |
| `POST /api/bots/{bot}/chats/{chatId}/callbacks` | `{"userId", "messageId", "data"}` | Presses the inline button with this callback data. |
| `GET /api/bots/{bot}/chats/{chatId}/history` | `?userId=&after=` | Entries with `seq` greater than `after` (default `0`), in order. |

Example: start a chat with `sample_bot`, send `/hello`, and read the history:

```shell
curl -s -X POST localhost:18790/api/bots/sample_bot/chats \
  -H 'Content-Type: application/json' -d '{"userId": 4242, "username": "alice", "languageCode": "en"}'
curl -s -X POST localhost:18790/api/bots/sample_bot/chats/4242/messages \
  -H 'Content-Type: application/json' -d '{"userId": 4242, "text": "/hello"}'
curl -s 'localhost:18790/api/bots/sample_bot/chats/4242/history?userId=4242'
```

### Responses

`GET /api/bots`:

```json
[{"id": 1001, "username": "sample_bot", "online": true, "registeredAt": "2025-01-01T10:00:00Z"}]
```

A bot is `online` while it waits in `getUpdates` and for 3 seconds after.

`POST /api/bots/{bot}/chats`:

```json
{
  "created": true,
  "chat": {"id": 4242, "userId": 4242, "username": "alice", "languageCode": "en", "startedAt": "2025-01-01T10:00:01Z"},
  "start": {"chatId": 4242, "seq": 1, "updateId": 1, "messageId": 1}
}
```

`POST .../messages` and `POST .../callbacks` return the recorded action. `callbackQueryId` is returned for button
presses only:

```json
{"chatId": 4242, "seq": 3, "updateId": 2, "messageId": 2, "callbackQueryId": "cbq-1001-1"}
```

`GET .../history` returns a list of entries. Fields that do not apply are left out:

```json
[
  {"seq": 1, "date": "2025-01-01T10:00:01Z", "from": "user", "action": "text", "kind": "message",
   "messageId": 1, "text": "/start", "buttons": []},
  {"seq": 2, "date": "2025-01-01T10:00:01Z", "from": "bot", "action": "rich_message", "kind": "message",
   "messageId": 2, "text": "Commands", "buttons": [[{"text": "hello", "callbackData": "/hello"}]]},
  {"seq": 3, "date": "2025-01-01T10:00:02Z", "from": "user", "action": "callback", "kind": "activity",
   "messageId": 2, "text": "hello", "buttons": [], "info": "/hello"}
]
```

| Field | Meaning |
|---|---|
| `seq` | Position in the chat history, in the order the emulator received the entries. |
| `from` | `user` or `bot`. |
| `kind` | `message` for text messages (chat bubbles), `activity` for everything else. |
| `action` | `text`, `rich_message`, `photo`, `location`, `callback`, `callback_answer`, `edit_text`, `edit_markup`, `delete`, `pin`, `unpin`. |
| `messageId` | The message created by the entry, or the one it refers to (pressed, answered, edited, deleted, pinned). |
| `text` | Message text or photo caption, button text of a `callback`, notification text of a `callback_answer`, new text of an `edit_text`. |
| `buttons` | Inline keyboard rows: sent with a message, or the new buttons of an edit. |
| `info` | Brief details: parse mode of a bot text (`parse mode Markdown`), callback data of a `callback`, `alert` of a `callback_answer`, coordinates of a `location`, file of a `photo`. |

### Errors

Errors have a status and a message:

```json
{"status": 403, "message": "Forbidden: chat 4242 belongs to another user"}
```

| Status | When |
|---|---|
| `400` | Invalid input: missing `userId`, empty text, a button that is not on the message, a deleted message. |
| `403` | The chat belongs to another user. |
| `404` | Unknown bot, chat not started yet, unknown path. |
| `405` | Wrong HTTP method. |

## Bot API

The emulator implements the part of the Telegram Bot API that telegram-boot uses, for long polling bots. Requests
are accepted as JSON, form, multipart form data or query parameters. Responses use the Bot API format
`{"ok": true, "result": ...}`, and errors `{"ok": false, "error_code": ..., "description": ...}`, as Telegram does.

| Method | Notes |
|---|---|
| `getMe` | Registers the bot, see [How bots are identified](#how-bots-are-identified). |
| `deleteWebhook` | Always succeeds: only long polling is emulated. |
| `getUpdates` | Long polling with `offset`, `limit` (up to 100) and `timeout` (up to 50 seconds). A new request releases the waiting one, so a restarted bot takes over right away. |
| `sendMessage` | Text, parse mode and inline keyboard. Reply keyboards are accepted and ignored. |
| `sendRichMessage` | The rich HTML message of the library's `/start` menu: paragraphs and `<tg-button>` rows. |
| `sendPhoto` | Upload (also with `attach://`) or a `file_id` returned for an earlier photo of the same bot. The image itself is not kept. |
| `sendLocation` | |
| `editMessageText`, `editMessageReplyMarkup` | Messages of the bot only. |
| `deleteMessage`, `pinChatMessage`, `unpinChatMessage` | |
| `answerCallbackQuery` | Once per callback query, by the bot that received it. |
| `setMyCommands`, `getMyCommands` | Stored per scope and language. |

Other methods get `404` and a warning in the log, naming the method.

## Testing with the emulator

Enable the emulator on a random port, read the port from `local.telegram-boot.emulator.port`, and act as a user
through the user API. Bots process updates asynchronously, so wait for the reply in the history:

```kotlin
@SpringBootTest(properties = ["telegram-boot.emulator.enabled=true", "telegram-boot.emulator.port=0"])
class HelloCommandE2eTest {
    @Value("\${local.telegram-boot.emulator.port}")
    private var port: Int = 0

    private val http = HttpClient.newHttpClient()

    @Test
    fun `hello command greets the user`() {
        post("/api/bots/sample_bot/chats", """{"userId": 4242, "username": "alice"}""")
        post("/api/bots/sample_bot/chats/4242/messages", """{"userId": 4242, "text": "/hello"}""")

        awaitHistory("/api/bots/sample_bot/chats/4242/history?userId=4242") { it.contains("\"text\":\"Hello @alice\"") }
    }

    private fun post(path: String, json: String) =
        http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun awaitHistory(path: String, condition: (String) -> Boolean) {
        val deadline = Instant.now().plusSeconds(10)
        while (true) {
            val history = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).build(),
                HttpResponse.BodyHandlers.ofString()).body()
            if (condition(history)) return
            check(Instant.now().isBefore(deadline)) { "Unexpected history: $history" }
            Thread.sleep(100)
        }
    }
}
```

Some tips:

- Use a different `userId` in each test. The emulator is shared by all tests of a cached Spring context, and the
  chats of different users are separate.
- Test the configuration you run locally by activating its profile, e.g. `@ActiveProfiles("emulator")`, and
  override only the port.
- The [end-to-end tests](../telegram-boot-e2e-tests) of the sample application show a complete setup, with a
  small `EmulatorUser` client for the user API.

## Limitations

- Only long polling bots: webhooks are not supported.
- Only private chats: no groups, channels, inline queries, payments or other Telegram features.
- Reply keyboards are ignored, and only callback buttons of inline keyboards can be pressed.
- Photos are recorded by name and size. Their content is not stored or shown.
- State is in memory: restarting the application clears all chats.
- When the application stops, bots that are still polling may log a failed `getUpdates` request.
