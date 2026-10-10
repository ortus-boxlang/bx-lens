#!/usr/bin/env python3
"""A stand-in for a local Ollama server, so the AI features can be tried and tested without a model: python3 mock-ai.py (listens on 11434,
or the port in MOCK_AI_PORT).

It answers the three calls Lens makes:

  POST /api/chat    chat, streamed (NDJSON) when "stream" is true, else one JSON object. Deterministic, driven by keywords in the last user
                    message (see decide()). When a keyword names a tool that the request offers, the answer is a tool call. When the last message
                    is a tool result, the answer lists the fields of that result, so a test can tell the model saw it. Everything else is echoed.
  POST /api/embed   embeddings. A bag of words hashed into 64 dimensions, so texts that share words are close.
  GET  /api/tags    the models it pretends to have (llama3.2 and nomic-embed-text).

GET /mock/log returns the requests it saw (a list of {path, tools, last, messages}) so a test can check what reached the model.
DELETE /mock/log clears it. POST /mock/config {"embed": false} (or MOCK_AI_NO_EMBED=1 at start) makes /api/embed answer 404, to test the
keyword fallback of the documentation search.
"""
import hashlib
import json
import math
import os
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = []
LOCK = threading.Lock()
# POST /mock/config {"embed": false} makes /api/embed answer 404, to test the keyword fallback of the documentation search
CONFIG = {"embed": not os.environ.get("MOCK_AI_NO_EMBED")}

# keyword in the last user message -> tool to call, in order. A tool is called only when the request offers it.
RULES = [
	("force:", None),
	("blocked thread", "blockedThreads"),
	("deadlock", "blockedThreads"),
	("thread", "threadsSummary"),
	("executor", "executors"),
	("healthy", "diagnose"),
	("slow right now", "diagnose"),
	("diagnose", "diagnose"),
	("error rate", "overview"),
	("how many requests", "overview"),
	("garbage", "runGc"),
	("run gc", "runGc"),
	("pause task", "taskAction"),
	("clear cache", "cacheAction"),
	("environment", "environment"),
	("how do i", "searchDocs"),
	("documentation", "searchDocs"),
	("what is the", "searchDocs"),
	("tables", "dbTables"),
	("sql", "queryStats"),
]

ARGS = {
	"runGc": {},
	"taskAction": {"scheduler": "demo", "task": "demo-task", "action": "pause"},
	"cacheAction": {"cache": "default", "action": "clear"},
	"searchDocs": None,  # the question itself
	"dbTables": {"datasource": "derby"},
	"queryStats": {"limit": 5},
	"executors": {},
}


def summarize(content):
	"""A readable answer built from a tool result: a title and one bullet per field of the result."""
	try:
		d = json.loads(content)
	except ValueError:
		return "Here is what the tool returned (shortened): " + str(content)[:400]
	res = d.get("result", d) if isinstance(d, dict) else d
	lines = ["Here is what the **%s** tool returned:" % (d.get("tool", "?") if isinstance(d, dict) else "?"), ""]
	if isinstance(res, dict):
		for k, v in list(res.items())[:12]:
			if isinstance(v, dict):
				vs = json.dumps(v)[:100]
			elif isinstance(v, list):
				vs = "%d items" % len(v)
			else:
				vs = (json.dumps(v) if isinstance(v, bool) else str(v))[:140]
			lines.append("- %s: %s" % (k, vs))
	else:
		lines.append(str(res)[:300])
	return "\n".join(lines)


def vector(text):
	v = [0.0] * 64
	for w in re.findall(r"[a-z0-9]+", text.lower()):
		h = int(hashlib.md5(w.encode()).hexdigest(), 16)
		v[h % 64] += 1.0
	n = math.sqrt(sum(x * x for x in v)) or 1.0
	return [x / n for x in v]


def decide(body):
	"""Return ("tool", name, args) or ("text", content)."""
	msgs = body.get("messages", [])
	tools = [t.get("function", {}).get("name") for t in body.get("tools", []) or []]
	last = msgs[-1] if msgs else {"role": "user", "content": ""}
	if last.get("role") == "tool":
		return ("text", summarize(str(last.get("content", ""))))
	text = str(last.get("content", ""))
	low = text.lower()
	if low.startswith("force:"):
		name = text[6:].split()[0] if text[6:].split() else ""
		return ("tool", name, {})
	if "what did i just ask" in low or "what did i ask" in low:
		users = [m for m in msgs[:-1] if m.get("role") == "user"]
		if users:
			return ("text", "You asked: " + str(users[-1].get("content", "")))
		return ("text", "I do not remember any earlier question.")
	if "slow-answer" in low:
		time.sleep(3)
		return ("text", "MOCK AGENT: finally")
	for key, tool in RULES:
		if tool and key in low:
			if tool in tools:
				args = ARGS.get(tool, {})
				if args is None:
					args = {"question": text}
				return ("tool", tool, dict(args))
			return ("text", "MOCK AGENT: I have no tool called " + tool + " for that. Tools offered: " + ", ".join(sorted(t for t in tools if t)[:50]))
	return ("text", "MOCK AGENT: you said: " + text[:300])


class Handler(BaseHTTPRequestHandler):
	protocol_version = "HTTP/1.1"

	def _json(self, code, obj):
		out = json.dumps(obj).encode()
		self.send_response(code)
		self.send_header("Content-Type", "application/json")
		self.send_header("Content-Length", str(len(out)))
		self.end_headers()
		self.wfile.write(out)

	def do_GET(self):
		if self.path.startswith("/api/tags"):
			self._json(200, {"models": [{"name": "llama3.2:latest", "model": "llama3.2:latest"}, {"name": "nomic-embed-text:latest", "model": "nomic-embed-text:latest"}]})
		elif self.path.startswith("/mock/log"):
			with LOCK:
				self._json(200, list(LOG))
		else:
			self._json(404, {"error": "not found"})

	def do_DELETE(self):
		with LOCK:
			LOG.clear()
		self._json(200, {"ok": True})

	def do_POST(self):
		n = int(self.headers.get("Content-Length", 0))
		body = json.loads(self.rfile.read(n) or b"{}")
		if self.path.startswith("/mock/config"):
			CONFIG.update({k: v for k, v in body.items() if k in CONFIG})
			self._json(200, CONFIG)
			return
		if self.path.startswith("/api/embed"):
			if not CONFIG["embed"]:
				self._json(404, {"error": "model not found"})
				return
			inp = body.get("input", "")
			items = inp if isinstance(inp, list) else [inp]
			self._json(200, {"model": body.get("model", "nomic-embed-text"), "embeddings": [vector(str(x)) for x in items]})
			return
		if not self.path.startswith("/api/chat"):
			self._json(404, {"error": "not found"})
			return
		msgs = body.get("messages", [])
		with LOCK:
			LOG.append({"path": self.path, "tools": [t.get("function", {}).get("name") for t in body.get("tools", []) or []],
			            "last": msgs[-1] if msgs else None, "messages": len(msgs), "model": body.get("model")})
			del LOG[:-200]
		kind = decide(body)
		model = body.get("model", "llama3.2")
		stream = body.get("stream", True)
		if kind[0] == "tool":
			msg = {"role": "assistant", "content": "", "tool_calls": [{"function": {"name": kind[1], "arguments": kind[2]}}]}
			chunks = [msg]
			final = {"model": model, "created_at": "2026-01-01T00:00:00Z", "message": msg, "done": True, "done_reason": "stop", "prompt_eval_count": 10, "eval_count": 5}
			if not stream:
				self._json(200, final)
				return
			self._stream([{"model": model, "message": msg, "done": False}, {"model": model, "message": {"role": "assistant", "content": ""}, "done": True, "done_reason": "stop"}])
			return
		text = kind[1]
		if not stream:
			self._json(200, {"model": model, "created_at": "2026-01-01T00:00:00Z", "message": {"role": "assistant", "content": text}, "done": True, "done_reason": "stop",
			                 "prompt_eval_count": 10, "eval_count": 5})
			return
		parts = re.findall(r"\S+\s*", text) or [""]
		lines = [{"model": model, "message": {"role": "assistant", "content": p}, "done": False} for p in parts]
		lines.append({"model": model, "message": {"role": "assistant", "content": ""}, "done": True, "done_reason": "stop", "prompt_eval_count": 10, "eval_count": len(parts)})
		self._stream(lines)

	def _stream(self, lines):
		self.send_response(200)
		self.send_header("Content-Type", "application/x-ndjson")
		self.send_header("Transfer-Encoding", "chunked")
		self.end_headers()
		for obj in lines:
			data = (json.dumps(obj) + "\n").encode()
			self.wfile.write(("%x\r\n" % len(data)).encode() + data + b"\r\n")
			self.wfile.flush()
			time.sleep(0.01)
		self.wfile.write(b"0\r\n\r\n")
		self.wfile.flush()

	def log_message(self, *a):
		pass


if __name__ == "__main__":
	ThreadingHTTPServer(("127.0.0.1", int(os.environ.get("MOCK_AI_PORT", "11434"))), Handler).serve_forever()
