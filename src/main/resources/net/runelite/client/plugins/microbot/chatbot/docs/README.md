# Chatbot

Short in-game replies using OpenAI, Google Gemini, or a custom OpenAI-compatible service. Version 1.1.2 includes request pacing, automatic error recovery, comma-separated keyword filters, previews that clear themselves, and a compact status overlay.

## Get ready for a test

1. Open **Chatbot → AI Connection** and choose the provider that issued your API key.
2. Paste the key into **API Key**. Existing OpenAI keys and model settings survive the upgrade.
3. For Gemini, leave **Custom Model** empty and select **Gemini 3.5 Flash-Lite** or **Gemini 3.8 Flash**. For OpenAI, an empty Custom Model uses your saved OpenAI model, or `gpt-4o-mini`.
4. For a custom service, fill in both **Custom Model** and **Custom Endpoint**. The endpoint must be the full chat-completions URL, including the path. The key is sent to this address.
5. Choose the channels in **Chat Sources**. Use **Only Respond To (names)** or **Trigger Keywords** for a controlled first test.
6. Turn **Send Replies Automatically** off for a preview test. The bot types into an empty chatbox, then erases its completed preview without pressing Enter. If you edit the preview, your changed input is preserved.
7. Enable the plugin and watch the overlay's status, active model, API errors, and time until the next request.

Create keys in the [OpenAI API dashboard](https://platform.openai.com/api-keys) or [Google AI Studio](https://aistudio.google.com/apikey). ChatGPT subscriptions and API access are separate services. Keep keys private.

## Fewer requests, better recovery

Every API attempt is paced, including failed requests. The normal pause is randomized between **Min Request Pause** and **Max Request Pause**, initially 5–15 seconds. HTTP 429 and 503 responses start a longer pause of at least 30 seconds, then progressively increase the wait for consecutive failures, up to roughly 15 minutes. A server's `Retry-After` hint can extend the wait. There is no immediate retry loop.

Only the newest eligible message waits for a reply. It expires after **Message Expiry**, initially 60 seconds. Chat received during a long API pause therefore cannot turn into a burst of old replies.

A Gemini daily-quota error pauses requests until the next midnight in Google's Pacific quota timezone, with a small reset margin; a longer server hint is respected. With **Gemini Lite Fallback** enabled, a limited or temporarily unavailable curated Flash model can switch to Flash-Lite after the required pause. Custom model overrides do not use this fallback. Limits and model availability depend on your provider, project and billing tier; the plugin does not guarantee a daily allowance.

Invalid credentials, billing exhaustion, invalid models and other permanent request errors pause requests until you change the connection settings or restart the plugin after resolving the problem. The overlay explains the pause.

Existing saved cooldowns migrate into the new request-pause settings once. New explicit minimum and maximum settings take priority on subsequent starts.

## Chat behavior

| Incoming channel | Reply channel |
| --- | --- |
| Public or moderator public chat | Public, when **Public Replies** is enabled |
| Clan or group ironman clan | Clan |
| Guest clan | Guest clan |
| Friends chat | Friends chat |
| Private or game messages | Ignored |

Public replies use plain text, without a `/p` prefix.

The plugin checks channel settings, player-name lists, whole-word keywords, public-chat distance, and common trade/gambling spam before requesting a reply. **Public Replies** off rejects public inputs before they consume API usage. Trigger Keywords accepts comma-separated alternatives such as `hi, hello, good luck`; any one can trigger a reply. A keyword such as `he` matches `he`, not `feathers` or `ashes`. Special characters in keywords are treated literally.

Public replies require the sender to be visible and within **Public Chat Distance**, initially 10 tiles. Clan and friends chat are not distance-limited. **Ignore Trade Spam** uses conservative patterns; it can be disabled when ordinary trading messages are part of the conversation.

Your existing chatbox input is preserved. The plugin waits or skips when you are already typing instead of appending and submitting a combined message. It also checks the plugin and login session before typing, sending, and clearing a preview, so an old completion cannot act after logout or shutdown. Completed previews are erased only when the chatbox still contains exactly what Chatbot typed. A preview is not counted as a sent reply.

## Settings

| Setting | Default | Purpose |
| --- | --- | --- |
| Provider | OpenAI | Keeps an existing OpenAI setup working |
| Gemini Model | Gemini 3.5 Flash-Lite | Curated Gemini model; used when Custom Model is empty |
| Custom Model | Empty | Exact model ID; required for Custom, overrides the provider's model otherwise |
| Custom Endpoint | Empty | Full OpenAI-compatible chat-completions URL for Custom |
| Public / Clan / Friends Chat | On / Off / Off | Channels that can trigger a reply |
| Public Replies | On | Allow public requests and replies |
| Send Replies Automatically | On | Turn off to type and clear previews without Enter |
| Reply Length | 80 | Limit of 20–80 characters, including routing prefix |
| Conversation Memory | 10 | Recent context messages, 0–40; 0 disables memory |
| Reply Tokens | 60 | Generated token limit, 16–512 |
| Creativity | 0.7 | Temperature, bounded to 0.0–2.0 |
| Min / Max Typing Delay | 2000 / 5000 ms | Wait before typing; 0 is allowed |
| Min / Max Request Pause | 5 / 15 seconds | Random spacing between API attempts |
| Message Expiry | 60 seconds | Maximum age of the one pending message |
| Public Chat Distance | 10 tiles | Maximum distance to a visible public speaker |
| Ignore Trade Spam / Own Messages | On / On | Filter spam and prevent self-reply loops |

Numeric settings are checked again at runtime, including settings saved outside the panel. Conversation context is kept only in memory and resets when the speaker or channel changes. Context and pending chat are discarded on shutdown or when logout is observed.

## Troubleshooting

- **Rate limited / server busy:** let the overlay's countdown finish. Increasing request-pause values or narrowing the keyword/name filters reduces requests. Restarting repeatedly does not resolve a provider quota.
- **Quota or billing pause:** check the selected provider's dashboard. Gemini daily quota may require waiting for the displayed reset; OpenAI billing exhaustion requires resolving API billing.
- **Configuration / authentication pause:** verify provider, key, model and endpoint. Change the connection settings after correcting them.
- **Waiting for empty chatbox:** send or erase your draft. Completed, unedited previews clear automatically; edited or interrupted previews are left for you to handle.
- **No public replies:** check both Public Chat and Public Replies, your filters, and whether the sender is visible and close enough.
- **Wrong model:** clear Custom Model to use the Gemini dropdown or your saved OpenAI model. The overlay shows the model actually used, including fallback.
