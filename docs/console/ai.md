---
title: The AI page and Lensy
order: 9
description: A chat that looks at your server with the tools of Lens, on a local model or a provider you choose. BoxLang+ only.
icon: lucide:bot
---

# The AI page and Lensy

Lensy is a chat inside the console. You ask in plain words ("Do we have any blocked threads?", "How healthy are the executors and how do I improve them?", "What is slow right now?") and it answers by looking at the server through a set of [tools](../reference/ai-tools.md): requests, errors, queries, executors, threads, memory, datasources and the Lens documentation. It never gets more than the user who is signed in. It is a **BoxLang+** feature. Copy prompt and the ChatGPT and Claude buttons of [Ask Lens](ask-lens-and-ai.md) stay free.

![The assistant with a tool call and an answer](../assets/screenshots/console-agent.png)

## What you need

| Requirement | Where it shows |
|---|---|
| The `bx-ai` module | It ships inside Lens. The AI page says if it is missing. |
| BoxLang+ or a trial | Without it the AI page is locked and the assistant is hidden. |
| `ai.enabled` | Turn it on from the AI page (admin) or in `boxlang.json`. |
| A model that answers | The default is [Ollama on this machine](../guides/ollama.md): `ollama pull llama3.2` and `ollama pull nomic-embed-text`. Press **Test connection** to check. |

The floating button at the bottom right of every console page, and the Ask link of the bar, appear only when all of that is true.

## Using it

- Press the round button, or **Alt+K**, to open the drawer. **Escape** closes it. The Ask Lens page shows the same chat as a full page.
- Answers stream in as the model writes them. Each tool the assistant uses shows as a chip with its name and a one line result. Click the chip to see the arguments.
- **Reset conversation** forgets everything. The memory is per console session: it holds the last `ai.memoryMessages` (20) messages in the memory of the JVM, and is gone at logout, when the session times out, on reset and when the module stops. Nothing is written to disk.
- The status line shows the provider, the model, how the documentation search works (`embeddings`, `keywords` or `off`) and how many tools you have.
- The bar has an **Ask** link (and an entry in its More menu) that opens the console with the drawer open. It shows only when the console is reachable for the caller and the assistant works.

## Roles

An admin gets every tool. A **viewer** gets only the tools that show what the viewer already sees in the console (overview, requests, errors, executors, tasks, datasources, caches, modules, `diagnose` and the documentation search). The assistant is not even offered the thread, JVM, log, environment or database tools for a viewer, and the server refuses them if the model asks anyway. A viewer cannot change the AI settings.

## Actions need your approval

Some tools change something: run, pause, resume or reload a task, evict, reap or clear a cache, run garbage collection, turn an integration on or off, change a live Lens setting. For each one the chat shows an **approval card** with the exact action and its arguments, and the answer waits. **Approve** runs it, **Deny** does nothing and tells the assistant it was not approved. The request is held on the server for five minutes, can be decided once, and only by the same console session with its CSRF token. The assistant cannot approve for you, and nothing it says changes that.

![An approval card](../assets/screenshots/console-agent-approval.png)

The actions need the admin role, BoxLang+, a console that is not [read only](settings.md), `console.actions` and `ai.actions`. There is no tool to restart or stop the server, to take a heap dump or to read a file, and no tool runs SQL. The database tools read table and column metadata only. Settings about the AI, the console, access and the disk store cannot be changed by the assistant.

## Ask about the documentation

`searchDocs` searches the pages of this documentation that help an operator (features, configuration, the console pages, troubleshooting, security, the reference and the production guide). On first use Lens cuts the pages into sections with the bx-ai Markdown loader and embeds them with the embedding model into an in-memory index. If the embedding model does not answer it searches by keywords over the same sections, and the status line says which mode is active. The index is rebuilt only when the pages or the embedding settings change, and is never saved.

![The AI page](../assets/screenshots/console-ai.png)

## The settings

The AI page changes these live. They apply at once and are saved with the other [console overrides](settings.md). See [`ai`](../configuration.md#ai) for the defaults.

| Setting | What it does |
|---|---|
| `ai.enabled` | The master switch. |
| `ai.provider` | `ollama` (default), `openai`, `claude`, `gemini`, `mistral`, `groq`, `grok`, `deepseek`, `openrouter`, `openai-compatible`, `cohere` or `docker`. |
| `ai.model`, `ai.baseUrl`, `ai.embeddingModel` | The model, the address of the model server and the embedding model. |
| `ai.temperature`, `ai.timeoutSeconds`, `ai.maxToolCalls`, `ai.memoryMessages`, `ai.maxConcurrentChats` | How the model is called and the limits of a chat. |
| `ai.actions`, `ai.rag` | Allow proposed actions, allow the documentation search. |
| `ai.apiKeyEnv` | The name of an environment variable that holds the API key. |

### The API key

A key is never typed into the console. Either set `ai.apiKeyEnv` to the **name** of an environment variable (the page refuses anything that is not a variable name, so a pasted key is rejected), or keep it in `boxlang.json` as an encrypted value, which the page cannot change:

```json title="boxlang.json"
{ "modules": { "bxLens": { "settings": { "ai": { "apiKey": "bxsecret:..." } } } } }
```

The page shows where the key is read from and whether it was found, never the key. A local Ollama needs no key.

On Free the page is locked:

![The AI page on Free](../assets/screenshots/console-ai-locked.png)

## What leaves the server

Every question and every tool result goes to the provider. Lens hides secrets and caps every result at 12,000 characters first, but the results can still hold request paths, error messages, thread names, log lines and settings. **A local model keeps all of it inside your network. A hosted provider receives it.** Pick the provider with that in mind. See the [threat model](../security.md#ops-assistant-threat-model).

## Limits

- At most `ai.maxConcurrentChats` (3) chats run at once on the server. The next one is told to wait (429).
- One answer may use `ai.maxToolCalls` (8) tool calls and `ai.timeoutSeconds` (120) seconds. The time you take to approve an action does not count.
- A session may start 20 questions and make 120 tool calls a minute. A question is up to 2000 characters.
- Closing the page cancels the answer.
- Every tool call, approval, denial and action is in the [audit log](../security.md#audit-log).
