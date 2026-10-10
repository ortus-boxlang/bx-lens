---
title: The AI page and Lensy
order: 9
description: A chat that looks at your server with the tools of Lens, on a local model or a provider you choose. BoxLang+ only.
icon: lucide:bot
---

# The AI page and Lensy

**Lensy is the Box Agent for your BoxLang server.** It is a chat inside the console. You ask in plain words ("Do we have any blocked threads?", "How healthy are the executors and how do I improve them?", "What is slow right now?") and it answers by looking at the server through a set of [tools](../reference/ai-tools.md): requests, errors, queries, executors, threads, memory, datasources and the Lens documentation. It never gets more than the user who is signed in. It is a **BoxLang+** feature. Copy prompt and the ChatGPT and Claude buttons of [Ask Lens](ask-lens-and-ai.md) stay free.

![The assistant with a tool call and an answer](../assets/screenshots/console-agent.png)

Lensy has four faces: idle, thinking (a slow blink and a small wiggle while an answer is written, still when your system asks for reduced motion), happy (after an answer) and worried (after an error). The face is in the floating button, the drawer header, next to each answer, on the AI page and in the bar's Ask link.

![Lensy idle, thinking, happy and worried](../assets/screenshots/lensy-states.png)

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

## Servers Lensy can ask (MCP)

An MCP server gives Lensy more tools. Lens lists the eleven Ortus documentation servers (BoxLang, ColdBox, CommandBox, TestBox, WireBox, LogBox, CacheBox, ContentBox, qb, Quick and cbauth). They are built in, **all off**, and cannot be removed or have their address changed. The admin can also add servers of their own. The list is on the AI page, under **Servers Lensy can ask (MCP)**, and is saved with the other console settings in `config/bxlens-settings.json` under `mcp.servers`. A saved entry that is not valid is skipped and shown on the page; it never stops the server from starting.

| Column | Meaning |
|---|---|
| On | Lensy may use the server. Turning one on asks first and shows the address: **the words Lensy puts in the tool arguments, usually your question, are sent to that server.** |
| Status | `ok`, `unreachable` with the reason (could not connect, timed out, HTTP 503...), `refused` when the address is no longer allowed, or `off`. A server that does not answer never stops Lensy: it carries on with the other tools. |
| Tools | How many of the server's tools Lensy may use. **Tools** opens the list and lets the admin tick which ones. A built-in server allows all of its tools, except `sendFeedback` (it sends text to the people who write the documentation), which has to be ticked by name and always asks for your click. A custom server allows none until the admin ticks some. |
| Trusted, read only | Custom servers only. A custom server's calls ask for your Approve click every time. Mark it trusted to say its tools only read, and they run without a click. |
| Test | Connects and lists the tools again (ten tests a minute for each session). |

![The MCP servers table on the AI page](../assets/screenshots/console-ai-mcp.png)

Rules for a custom server:

- The name is 1 to 40 characters. At most 20 custom servers.
- The address must be **https**. Plain `http` is accepted only for `localhost`, `127.x.x.x` and `[::1]`.
- No user name or password in the address, no `#` fragment, at most 500 characters.
- Every address the host name resolves to must be public. Private (10.x, 172.16 to 31.x, 192.168.x), loopback, link-local (169.254.x.x, which holds the cloud metadata address), unique local (fc00::/7), carrier-grade NAT and multicast addresses are refused, when the server is added and again before every connection. Redirects are never followed.
- **There is no way to add headers or credentials in this version.** Lens sends no API key and no cookie to an MCP server, so a server that needs authentication cannot be used yet.

Names: Lensy shows a tool as `server.tool` (for example `boxlang.searchDocumentation`) in chips, approvals and the audit log. The model is given `server__tool`, because model providers do not accept a dot in a tool name. A tool description is shown to the model with the server's name in front of it: `[BoxLang docs] Search across the documentation...`.

Who can use them: the admin can use every enabled server. A viewer can use the tools of enabled **built-in** servers, and nothing else, and cannot see or change the list. All changes are admin only, need BoxLang+, are refused when the console is read only, and are in the audit log as `ai.mcp.change`. Every call is in the audit log as `ai.mcp` with the server, the tool, `ok` or `denied`, and the milliseconds (never the arguments).

What comes back from a server is **untrusted content from the internet**. Lens wraps it as data, hides secrets in it, cuts it to 12,000 characters, and the system prompt tells Lensy not to follow instructions found in it. Read the [threat model](../security.md#mcp-servers) before turning a server on.

![Lensy answering with a tool of a documentation server, shown with the server's name](../assets/screenshots/console-agent-mcp.png)

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

Every question and every tool result goes to the provider. When Lensy uses an [MCP server](#servers-lensy-can-ask-mcp), the words in the tool arguments also go to that server. Lens hides secrets and caps every result at 12,000 characters first, but the results can still hold request paths, error messages, thread names, log lines and settings. **A local model keeps all of it inside your network. A hosted provider receives it.** Pick the provider with that in mind. See the [threat model](../security.md#lensy-threat-model).

## Limits

- At most `ai.maxConcurrentChats` (3) chats run at once on the server. The next one is told to wait (429).
- One answer may use `ai.maxToolCalls` (8) tool calls and `ai.timeoutSeconds` (120) seconds. The time you take to approve an action does not count.
- A session may start 20 questions and make 120 tool calls a minute. A question is up to 2000 characters.
- Closing the page cancels the answer.
- Every tool call, approval, denial and action is in the [audit log](../security.md#audit-log).
