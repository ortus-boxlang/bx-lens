#!/usr/bin/env python3
"""A stand-in for a local Ollama server, so the AI features can be tried without a model: python3 mock-ai.py (listens on 11434).
Every chat request is answered with a canned reply that echoes the first 200 characters of the prompt."""
import json
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):

	def do_POST(self):
		body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
		msgs = body.get("messages", [])
		prompt = msgs[-1].get("content", "") if msgs else ""
		reply = {"model": body.get("model", "mock"), "created_at": "2026-01-01T00:00:00Z", "done": True, "done_reason": "stop",
		         "message": {"role": "assistant", "content": "MOCK ANSWER. The prompt began: " + prompt[:200]}}
		out = json.dumps(reply).encode()
		self.send_response(200)
		self.send_header("Content-Type", "application/json")
		self.send_header("Content-Length", str(len(out)))
		self.end_headers()
		self.wfile.write(out)

	def log_message(self, *a):
		pass


HTTPServer(("127.0.0.1", 11434), Handler).serve_forever()
