---
title: Set up Ollama for the assistant
order: 4
description: Run a local model so Lensy keeps everything inside your network.
icon: lucide:cpu
---

# Set up Ollama for the assistant

The [Lensy](../console/ai.md) uses a model on this machine by default. [Ollama](https://ollama.com) runs it. Nothing leaves your network.

## 1. Install Ollama and pull the two models

```bash
# install from https://ollama.com, then:
ollama pull llama3.2          # the chat model
ollama pull nomic-embed-text  # the embedding model for the documentation search
ollama serve                  # if it is not already running as a service
```

Ollama listens on `http://localhost:11434`, which is what Lens expects.

## 2. Turn the assistant on

On the console **AI** page (admin) switch on **Enable the assistant** and press **Apply and save**, then **Test connection**. Or set it in `boxlang.json`:

```json title="boxlang.json"
{ "modules": { "bxLens": { "settings": { "ai": { "enabled": true } } } } }
```

The assistant also needs BoxLang+ or a trial. The defaults are `ai.provider: "ollama"`, `ai.model: "llama3.2"`, `ai.baseUrl: "http://localhost:11434"` and `ai.embeddingModel: "nomic-embed-text"`.

## Ollama on another machine or in a container

Set `ai.baseUrl` to its address, for example `http://ollama.internal:11434`. Keep it on a network you control: the tool results go there.

## A bigger model, or a hosted one

A small model such as `llama3.2` handles simple questions. For harder ones choose a larger model (`ai.model`) or a hosted provider (`ai.provider`: `openai`, `claude`, `gemini` and others). Give the key through an environment variable and set `ai.apiKeyEnv` to its name. A hosted provider receives what the assistant reads.

## If it does not work

| Test connection says | Try |
|---|---|
| Connection refused | Is Ollama running? `curl http://localhost:11434/api/tags` |
| model not found | `ollama pull llama3.2` (or the model you set) |
| The embedding test fails | `ollama pull nomic-embed-text`. The assistant still works: the documentation search falls back to keywords |
| No answer in 40 seconds | The first request loads the model into memory. Try again, or use a smaller model |
