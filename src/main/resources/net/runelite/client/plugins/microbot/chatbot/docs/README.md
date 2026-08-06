# Chatbot Plugin

AI-powered in-game chatbot for Microbot Hub using OpenAI Chat Completions.

## What it does
- Listens to selected in-game chat channels.
- Sends messages to OpenAI and generates short OSRS-style replies.
- Replies in the matching channel behavior currently supported:
  - Public input -> Public reply (if enabled)
  - Clan input -> Clan reply
  - Friends Chat input -> Public reply (current behavior)
  - Private Messages -> Disabled (not listened to / working)

## OpenAI Access Token Setup
1. Sign in to OpenAI and create an API key in your API dashboard: https://platform.openai.com/api-keys
2. Copy the key (it starts with `sk-...`).
3. In RuneLite -> Chatbot plugin settings -> **OpenAI Settings** -> **API Key**.
4. Paste your key into **API Key**.
5. Keep it private. Do not share screenshots/logs containing your key.

If the key is empty or invalid, the plugin will not respond.

## Configuration Reference

### OpenAI Settings
- **API Key** (`openAiApiKey`)
  - Your OpenAI API key.
  - Required for responses.

- **Model** (`openAiModel`)
  - Model name sent to OpenAI.
  - Default: `gpt-4o-mini`

- **System Prompt** (`systemPrompt`)
  - Defines personality and response style.
  - Default is OSRS-friendly short-chat behavior.

- **Max Tokens** (`maxTokens`)
  - Maximum tokens requested from OpenAI per reply.
  - Lower = shorter/cheaper responses.
  - Default: `60`

- **Temperature** (`temperature`)
  - Creativity/randomness as text value (e.g. `0.7`).
  - Lower = more deterministic, higher = more varied.
  - Default: `0.7`

### Chat Sources
- **Public Chat** (`listenPublicChat`)
  - Listen/respond to public chat messages.
  - Default: `true`

- **Clan Chat** (`listenClanChat`)
  - Listen/respond to clan chat messages.
  - Default: `false`

- **Friends Chat** (`listenFriendsChat`)
  - Listen/respond to friends chat messages.
  - Default: `false`

### Response Settings
- **Min Response Delay (ms)** (`responseDelayMin`)
  - Minimum human-like delay before typing.
  - Default: `2000`

- **Max Response Delay (ms)** (`responseDelayMax`)
  - Maximum human-like delay before typing.
  - Default: `5000`

- **Respond in Public** (`respondViaPublic`)
  - Enables public chat output for non-clan channels.
  - If disabled, public/friends-chat inputs won’t send responses.
  - Clan responses still use clan channel routing.
  - Default: `true`

- **Max Response Length** (`maxResponseLength`)
  - Hard character cap after AI response.
  - OSRS chatbox friendly.
  - Default: `80`

- **Conversation Memory** (`conversationMemory`)
  - Number of recent user/assistant turns kept as context.
  - Default: `10`

### Filtering
- **Only Respond To (names)** (`onlyRespondToNames`)
  - Comma-separated allowlist. Empty = everyone.
  - Example: `Name1,Name2`

- **Ignore Names** (`ignoreNames`)
  - Comma-separated blocklist.
  - Example: `Spammer1,Spammer2`

- **Trigger Keyword** (`triggerKeyword`)
  - Only respond if message contains this keyword.
  - Empty = no keyword filter.

- **Ignore Own Messages** (`ignoreSelf`)
  - Prevents responding to your own character’s messages.
  - Default: `true`

- **Cooldown (seconds)** (`cooldownSeconds`)
  - Minimum time between responses.
  - Default: `5`

## Quick Start
1. Enable plugin.
2. Paste API key.
3. Choose channels in **Chat Sources**.
4. Adjust delay/cooldown to reduce spam-like behavior.
5. Test in chat with short prompts.

## Mass Worlds + Conversation Management
- In busy/mass worlds, many messages can arrive quickly and be queued.
- The plugin processes one queued message at a time on its loop.
- If cooldown is active, the current message is put back into the queue and retried later.
- Conversation context is shared (not per-player): recent user/assistant turns are kept in one rolling memory list.
- `conversationMemory` controls how many recent turns are kept; older turns are dropped from the front.
- Because memory is shared, heavy public chat can mix multiple speakers into context and reduce reply relevance.

### Storage details
- Message queue is in-memory only (runtime queue, not saved to disk).
- Conversation history is in-memory only (runtime context, not persisted across restarts).
- Queue/history are reset when the script starts and cleared on shutdown.

### Recommended settings for mass worlds
- Use **Trigger Keyword** so random public lines do not trigger responses.
- Use **Only Respond To (names)** for controlled conversations.
- Increase **Cooldown (seconds)** to reduce spam-like reply frequency.
- Reduce **Conversation Memory** to keep context tighter and less polluted.

## Troubleshooting
- **No replies**
  - Check API key, internet, and that desired chat source is enabled.
  - Ensure cooldown is not active.

- **Replies too long/too weird**
  - Lower **Max Tokens** and/or **Temperature**.
  - Tighten **System Prompt**.

- **Replies in wrong place**
  - Clan messages route to clan chat.
  - Public messages route to public chat (when enabled).
  - Private messages are disabled.
