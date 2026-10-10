/* BX Lens console. Alpine components for the login page and the app. Same-origin only: no external requests. */
(function () {
	var body = function () { return document.body.dataset; };
	var base = function () { return body().base; };

	function icon(name) { return "#ph-" + name; }

	document.addEventListener("alpine:init", function () {

		Alpine.data("lensLogin", function () {
			return {
				pw: "", err: "", busy: false, locked: 0, timer: null,
				insecure: location.protocol !== "https:" && ["localhost", "127.0.0.1", "[::1]"].indexOf(location.hostname) < 0,
				submit: async function () {
					if (this.busy || !this.pw) { return; }
					this.busy = true; this.err = "";
					try {
						var r = await fetch(base() + "/login", { method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/x-www-form-urlencoded", "X-Lens-Login": "1" }, body: new URLSearchParams({ password: this.pw }).toString() });
						var j = await r.json().catch(function () { return {}; });
						if (r.ok && j.ok) { location.reload(); return; }
						if (r.status === 429) { this.lock(j.lockedSeconds || 60); }
						else { this.err = j.error || "Sign in failed."; if (j.attemptsLeft !== undefined) { this.err += " " + j.attemptsLeft + " attempts left."; } }
					} catch (e) { this.err = "Could not reach the server."; }
					this.busy = false; this.pw = "";
				},
				lock: function (s) {
					var self = this; this.locked = s;
					clearInterval(this.timer);
					this.timer = setInterval(function () {
						self.locked--;
						if (self.locked <= 0) { clearInterval(self.timer); self.err = ""; }
						else { self.err = "Too many attempts from this address. Try again in " + Math.floor(self.locked / 60) + ":" + String(self.locked % 60).padStart(2, "0") + "."; }
					}, 1000);
					this.err = "Too many attempts from this address. Try again in " + Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0") + ".";
				}
			};
		});

		Alpine.data("lensApp", function () {
			return {
				state: null, tab: "overview", live: true, es: null, streamOk: false, timer: null, toastMsg: "", now: Date.now(),
				overview: null, requests: [], capacity: 0, servers: 1, settings: null,
				rf: "all", rq: "", rsel: null, detail: null, detailErr: "",
				executors: [], execCounts: {}, esel: null, hist: {},
				tasks: null, ksel: null, kf: "all", kq: "", confirm: "", runResult: null, running: "",
				system: null, sys: { cpu: [], heap: [], thr: [] },
				bar: null, design: [], dirty: false, dragFrom: null, dragOver: null,
				threads: null, tsel: null, tf: "ALL", tq: "", tpool: "", threadTimer: null,
				icon: icon,

				init: async function () {
					// Alpine calls init() by itself and x-init calls it again: set everything up once
					if (this.started) { return; }
					this.started = true;
					var self = this;
					await this.load();
					var h = location.hash.replace("#", ""), deep = h.split("/");
					h = deep[0];
					if (this.state.tabs.some(function (t) { return t.id === h; })) { this.tab = h; }
					if (h === "requests" && deep[1]) { this.pick(decodeURIComponent(deep[1])); }
					if (h === "agent") { this.openAgentWhenReady(); }
					window.addEventListener("hashchange", function () { var t = location.hash.replace("#", ""); if (t === "agent") { self.openAgent(); } else if (t) { self.tab = t; self.onTab(); } });
					document.addEventListener("keydown", function (e) {
						if (e.altKey && (e.key === "k" || e.key === "K")) { e.preventDefault(); self.toggleAgent(); }
						else if (e.key === "Escape" && self.agent.open && self.tab !== "ask") { self.closeAgent(); }
					});
					setInterval(function () { self.now = Date.now(); }, 1000);
					this.timer = setInterval(function () { if (self.live && !self.streamOk && !document.hidden) { self.refresh(); } }, 3000);
					this.$watch("live", function (v) { if (v) { self.connect(); } else { self.disconnect(); } });
					this.onTab();
					this.connect();
				},

				// ---- transport ----
				api: async function (path, opts) {
					opts = opts || {};
					opts.credentials = "same-origin";
					opts.headers = Object.assign({ "X-Lens-CSRF": body().csrf }, opts.headers || {});
					var r = await fetch(base() + "/api/" + path, opts);
					if (r.status === 401) { location.reload(); throw new Error("signed out"); }
					return r;
				},
				sdraft: {}, settingsError: "",
				sval: function (d) { return Object.prototype.hasOwnProperty.call(this.sdraft, d.key) ? this.sdraft[d.key] : d.value; },
				sedit: function (d, v) {
					if (d.type === "int") { v = v === "" ? "" : Number(v); }
					if (String(v) === String(Array.isArray(d.value) ? d.value.join(", ") : d.value)) { delete this.sdraft[d.key]; } else { this.sdraft[d.key] = v; }
					this.sdraft = Object.assign({}, this.sdraft);
				},
				dirtyCount: function () { return Object.keys(this.sdraft).length; },
				discardSettings: function () { this.sdraft = {}; this.settingsError = ""; },
				saveSettings: async function () {
					var body = new URLSearchParams({ changes: JSON.stringify(this.sdraft) }).toString();
					var r = await this.api("settings", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: body });
					var j = await r.json();
					if (r.ok) { this.settings = j; this.sdraft = {}; this.settingsError = ""; this.toast("Settings applied and saved"); this.state = await (await this.api("state")).json(); }
					else { this.settingsError = j.error || "Could not save"; }
				},
				resetSetting: async function (key) {
					if (!key && !confirm("Reset every setting you changed in the console?")) { return; }
					var r = await this.api("settings/reset", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ key: key }).toString() });
					var j = await r.json();
					if (r.ok) { this.settings = j; this.sdraft = {}; this.settingsError = ""; this.toast(key ? "Reset" : "All settings reset"); this.state = await (await this.api("state")).json(); }
					else { this.settingsError = j.error || "Could not reset"; }
				},
				has: function (id) { return !!(this.state && this.state.tabs.some(function (t) { return t.id === id; })); },
				connect: function () {
					var self = this;
					this.disconnect();
					if (!window.EventSource) { return; }
					var topics = ["requests"].concat(["executors", "tasks", "datasources", "inflight", "queries", "system"].filter(function (t) { return self.has(t); })).join(",");
					var extra = "";
					if (this.tab === "logfiles" && this.lfile && this.llive) { topics += ",log"; extra = "&logfile=" + encodeURIComponent(this.lfile) + "&logoffset=" + this.loffset; }
					this.es = new EventSource(base() + "/stream?topics=" + topics + extra, { withCredentials: true });
					this.es.addEventListener("tick", function (e) { self.streamOk = true; self.onTick(JSON.parse(e.data)); });
					this.es.onerror = function () { self.streamOk = false; };
				},
				disconnect: function () { if (this.es) { this.es.close(); this.es = null; } this.streamOk = false; },
				onTick: function (d) {
					if (d.overview) { this.overview = d.overview; this.badges(); }
					if (d.servers !== undefined) { this.servers = d.servers; }
					if (d.requests) { this.requests = d.requests; if (this.rsel === null && d.requests.length) { this.pick(d.requests[0].id); } }
					if (d.executors) { this.setExecutors(d.executors); }
					if (d.tasks) { this.tasks = d.tasks; if (!this.ksel && this.allTasks().length) { this.ksel = this.allTasks()[0].scheduler + "/" + this.allTasks()[0].name; } }
					if (d.system) { this.setSystem(d.system); }
					if (d.datasources) { this.dsl = d.datasources; }
					if (d.inflight) { this.inf = d.inflight; if (this.isel && this.tab === "inflight") { this.pickInflight(this.isel); } }
					if (d.queries) { this.qs = d.queries; if (this.qpick) { var qp = this.qpick; this.qpick = this.qs.statements.find(function (s) { return s.sql === qp.sql && s.datasource === qp.datasource; }) || qp; } }
					if (d.log) { this.loffset = d.log.offset; this.onLogLines(d.log.lines); }
				},
				load: async function () {
					this.state = await (await this.api("state")).json();
					await this.refresh();
					this.settings = await (await this.api("settings")).json();
					this.loadAi();
					this.loadAgent();
					if (this.has("executors")) { this.setExecutors(await (await this.api("executors")).json()); }
					if (this.has("tasks")) { this.tasks = await (await this.api("tasks")).json(); var a = this.allTasks(); if (a.length) { this.ksel = a[0].scheduler + "/" + a[0].name; } }
					if (this.has("system")) { this.setSystem(await (await this.api("system")).json()); }
					if (this.has("datasources")) { this.dsl = await (await this.api("datasources")).json(); }
				},
				refresh: async function () {
					try {
						var o = await (await this.api("overview")).json();
						var r = await (await this.api("requests")).json();
						this.overview = o; this.requests = r.requests; this.capacity = r.capacity; this.servers = r.servers || 1;
						if (this.rsel === null && this.requests.length) { this.pick(this.requests[0].id); }
						if (this.has("executors")) { this.setExecutors(await (await this.api("executors")).json()); }
						if (this.has("tasks")) { this.tasks = await (await this.api("tasks")).json(); }
						if (this.has("system")) { this.setSystem(await (await this.api("system")).json()); }
					} catch (e) { /* signed out or offline: the next tick tries again */ }
				},
				aiInfo: null, aiBusy: false, aiOut: { open: false, text: "", error: "" }, askQ: "",
				loadAi: async function () { try { this.aiInfo = await (await this.api("ai")).json(); } catch (e) { this.aiInfo = null; } },
				promptUrl: function (kind, id, n) { return "ai/prompt?kind=" + kind + "&id=" + encodeURIComponent(id || "") + "&n=" + encodeURIComponent(n === undefined || n === null ? "" : n); },
				fetchPrompt: async function (kind, id, n) { var r = await this.api(this.promptUrl(kind, id, n)); if (!r.ok) { this.toast("Nothing to explain yet"); return null; } return (await r.json()).prompt; },
				copyText: async function (text) {
					try { await navigator.clipboard.writeText(text); return true; } catch (e) {
						var t = document.createElement("textarea"); t.value = text; t.style.position = "fixed"; t.style.opacity = "0"; document.body.appendChild(t); t.select();
						var ok = false; try { ok = document.execCommand("copy"); } catch (e2) { ok = false; } document.body.removeChild(t); return ok;
					}
				},
				copyPrompt: async function (kind, id, n) { var p = await this.fetchPrompt(kind, id, n); if (p && await this.copyText(p)) { this.toast("Prompt copied. Paste it into your chat."); } },
				sendPrompt: async function (site, kind, id, n) {
					var w = window.open("", "_blank"); if (w) { w.opener = null; }
					var p = await this.fetchPrompt(kind, id, n);
					if (!p) { if (w) { w.close(); } return; }
					var copied = await this.copyText(p);
					this.toast(copied ? "Prompt copied. Paste it into the chat." : "Could not copy. Use Copy prompt.");
					if (w) { w.location = site === "claude" ? "https://claude.ai/new" : "https://chatgpt.com/"; }
				},
				explain: async function (kind, id, n) {
					this.aiBusy = true; this.aiOut = { open: true, text: "", error: "" };
					try {
						var r = await this.api("ai/explain", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ kind: kind, id: id || "", n: n === undefined || n === null ? "" : n }).toString() });
						var j = await r.json(); if (r.ok) { this.aiOut.text = j.answer; } else { this.aiOut.error = j.error || "The AI call failed"; }
					} finally { this.aiBusy = false; }
				},
				ask: async function () {
					this.aiBusy = true; this.aiOut = { open: true, text: "", error: "" };
					try {
						var r = await this.api("ai/ask", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ question: this.askQ }).toString() });
						var j = await r.json(); if (r.ok) { this.aiOut.text = j.answer; } else { this.aiOut.error = j.error || "The AI call failed"; }
					} finally { this.aiBusy = false; }
				},
				openAgentWhenReady: function () { var self = this; this.loadAgent().then(function () { self.openAgent(); }); },
				// ---- Lensy: a floating drawer on every page, and the full page of Ask Lens ----
				agent: { st: null, open: false, msgs: [], busy: false, input: "", ctl: null, mood: "idle", moodTimer: null },
				// Lensy's face: idle, think (while a turn runs), happy (after an answer, for a few seconds), error. Reduced motion gets a still face.
				reducedMotion: function () { try { return window.matchMedia("(prefers-reduced-motion: reduce)").matches; } catch (e) { return false; } },
				lensyHref: function (kind) {
					var k = kind || this.agent.mood || "idle";
					if (k === "think") { return this.reducedMotion() ? "#lensy-think-still" : "#lensy-think"; }
					return "#lensy-" + k;
				},
				agentMsgMood: function (i) { return i === this.agent.msgs.length - 1 && this.agent.mood !== "think" ? this.agent.mood : "idle"; },
				setMood: function (mood, backAfterMs) {
					this.agent.mood = mood;
					if (this.agent.moodTimer) { clearTimeout(this.agent.moodTimer); this.agent.moodTimer = null; }
					if (backAfterMs) { var self = this; this.agent.moodTimer = setTimeout(function () { self.agent.mood = "idle"; }, backAfterMs); }
				},
				agentExamples: ["Do we have any blocked threads?", "How healthy are the executors and how do I improve them?", "How many requests did we serve and what is the error rate?", "What is slow right now?"],
				agentOn: function () { return !!(this.agent.st && this.agent.st.available); },
				loadAgent: async function () { try { this.agent.st = await (await this.api("agent/status")).json(); } catch (e) { this.agent.st = null; } },
				openAgent: function () {
					if (!this.agentOn()) { return; }
					this.agent.open = true; this.placeAgent();
					var self = this; this.$nextTick(function () { var i = document.getElementById("agent-input"); if (i) { i.focus(); } self.scrollAgent(); });
				},
				closeAgent: function () { this.agent.open = false; },
				toggleAgent: function () { if (!this.agent.st) { this.openAgentWhenReady(); return; } if (this.agent.open && this.tab !== "ask") { this.closeAgent(); } else { this.openAgent(); } },
				// One chat element. On the Ask Lens page it sits in the page, everywhere else in the floating drawer
				placeAgent: function () {
					var el = document.getElementById("agent"), host = document.getElementById(this.tab === "ask" ? "agent-page" : "agent-float");
					if (el && host && el.parentNode !== host) { host.appendChild(el); }
				},
				agentShown: function () { return this.agentOn() && (this.tab === "ask" || this.agent.open); },
				scrollAgent: function () { var l = document.getElementById("agent-list"); if (l) { l.scrollTop = l.scrollHeight; } },
				agentKey: function (e) {
					if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); this.sendAgent(); }
				},
				agentLeft: function (p) { return Math.max(0, Math.round((p.expiresAt - this.now) / 1000)); },
				sendAgent: async function (text) {
					var q = (text !== undefined ? text : this.agent.input).trim();
					if (!q || this.agent.busy) { return; }
					this.agent.input = ""; this.agent.busy = true; this.setMood("think");
					var self = this, m = { role: "assistant", parts: [], done: false };
					this.agent.msgs.push({ role: "user", text: q });
					this.agent.msgs.push(m);
					// Work on the reactive copy: changes to the raw object would not reach the page
					m = this.agent.msgs[this.agent.msgs.length - 1];
					this.agent.ctl = new AbortController();
					var last = function (type) { var p = m.parts[m.parts.length - 1]; return p && p.type === type ? p : null; };
					try {
						var r = await fetch(base() + "/api/agent/chat", { method: "POST", credentials: "same-origin", signal: this.agent.ctl.signal,
							headers: { "X-Lens-CSRF": body().csrf, "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ message: q }).toString() });
						if (r.status === 401) { location.reload(); return; }
						if (!r.ok) { var j = await r.json().catch(function () { return {}; }); m.parts.push({ type: "error", text: j.error || "The assistant is not available." }); return; }
						var reader = r.body.getReader(), dec = new TextDecoder(), buf = "";
						for (;;) {
							var chunk = await reader.read();
							if (chunk.done) { break; }
							buf += dec.decode(chunk.value, { stream: true });
							var cut;
							while ((cut = buf.indexOf("\n\n")) >= 0) {
								var block = buf.slice(0, cut); buf = buf.slice(cut + 2);
								var ev = "", data = "";
								block.split("\n").forEach(function (line) { if (line.indexOf("event:") === 0) { ev = line.slice(6).trim(); } else if (line.indexOf("data:") === 0) { data += line.slice(5).trim(); } });
								if (!ev || !data) { continue; }
								var d; try { d = JSON.parse(data); } catch (e) { continue; }
								if (ev === "token") { var t = last("text"); if (t) { t.text += d.text; } else { m.parts.push({ type: "text", text: d.text }); } }
								else if (ev === "tool_call") { m.parts.push({ type: "tool", name: d.name, args: d.args, readOnly: d.readOnly, ok: null, summary: "", open: false }); }
								else if (ev === "tool_result") { for (var i = m.parts.length - 1; i >= 0; i--) { var p = m.parts[i]; if (p.type === "tool" && p.name === d.name && p.ok === null) { p.ok = d.ok; p.summary = d.summary; break; } } }
								else if (ev === "approval_request") { m.parts.push({ type: "approval", id: d.id, tool: d.tool, args: d.args, summary: d.summary, expiresAt: d.expiresAt, state: "pending", error: "" }); }
								else if (ev === "error") { m.parts.push({ type: "error", text: d.message }); }
								else if (ev === "done") { m.done = true; }
								this.$nextTick(function () { self.scrollAgent(); });
							}
						}
					} catch (e) {
						if (e.name !== "AbortError") { m.parts.push({ type: "error", text: "The connection to the server was lost." }); }
					} finally {
						m.done = true; this.agent.busy = false; this.agent.ctl = null;
						this.setMood(m.parts.some(function (p) { return p.type === "error"; }) ? "error" : "happy", 8000);
						// A card nobody answered is no longer waiting
						m.parts.forEach(function (p) { if (p.type === "approval" && p.state === "pending") { p.state = "closed"; } });
						this.$nextTick(function () { self.scrollAgent(); });
						this.loadAgent();
					}
				},
				decideAgent: async function (p, approve) {
					if (p.state !== "pending") { return; }
					p.state = "sending";
					var r = await this.api("agent/approve", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ id: p.id, approve: approve ? "true" : "false" }).toString() });
					var j = await r.json().catch(function () { return {}; });
					if (r.ok) { p.state = approve ? "approved" : "denied"; } else { p.state = r.status === 410 ? "expired" : "closed"; p.error = j.error || ""; }
				},
				stopAgent: function () { if (this.agent.ctl) { this.agent.ctl.abort(); } },
				resetAgent: async function () {
					this.stopAgent();
					try { await this.api("agent/reset", { method: "POST" }); } catch (e) { /* the page keeps what it has */ }
					this.agent.msgs = []; this.agent.busy = false; this.setMood("idle"); this.toast("Conversation cleared");
				},
				agentRag: function () { var s = this.agent.st; return s && s.rag ? (s.rag.mode === "embeddings" ? "docs: embeddings" : s.rag.mode === "keywords" ? "docs: keywords" : s.rag.mode === "off" ? "docs: off" : "docs: on first use") : ""; },
				toolLabel: function (p) {
					var a = p.args || {}, keys = Object.keys(a);
					return keys.length ? keys.map(function (k) { return k + "=" + a[k]; }).join(", ") : "no arguments";
				},
				// A small safe Markdown: code blocks, lists, bold, inline code. It builds DOM nodes with textContent, never HTML
				mdTo: function (el, text) {
					while (el.firstChild) { el.removeChild(el.firstChild); }
					var inline = function (parent, s) {
						var i = 0, buf = "";
						var flush = function () { if (buf) { parent.appendChild(document.createTextNode(buf)); buf = ""; } };
						while (i < s.length) {
							if (s.startsWith("**", i) && s.indexOf("**", i + 2) > i + 2) { flush(); var b = document.createElement("strong"); b.textContent = s.slice(i + 2, s.indexOf("**", i + 2)); parent.appendChild(b); i = s.indexOf("**", i + 2) + 2; }
							else if (s[i] === "`" && s.indexOf("`", i + 1) > i + 1) { flush(); var c = document.createElement("code"); c.textContent = s.slice(i + 1, s.indexOf("`", i + 1)); parent.appendChild(c); i = s.indexOf("`", i + 1) + 1; }
							else { buf += s[i]; i++; }
						}
						flush();
					};
					var bulletOf = function (t) {
						if ((t[0] === "-" || t[0] === "*") && t[1] === " ") { return { ordered: false, text: t.slice(2).trim() }; }
						var n = 0; while (n < t.length && t[n] >= "0" && t[n] <= "9") { n++; }
						return n > 0 && (t[n] === "." || t[n] === ")") && t[n + 1] === " " ? { ordered: true, text: t.slice(n + 2).trim() } : null;
					};
					var headOf = function (t) { var n = 0; while (t[n] === "#") { n++; } return n > 0 && n <= 6 && t[n] === " " ? t.slice(n + 1) : null; };
					var lines = String(text || "").split("\n"), i = 0, list = null, para = null;
					var endPara = function () { para = null; };
					var endList = function () { list = null; };
					while (i < lines.length) {
						var line = lines[i];
						if (line.trim().indexOf("```") === 0) {
							endPara(); endList();
							var code = []; i++;
							while (i < lines.length && lines[i].trim().indexOf("```") !== 0) { code.push(lines[i]); i++; }
							var pre = document.createElement("pre"), cd = document.createElement("code"); cd.textContent = code.join("\n"); pre.appendChild(cd); el.appendChild(pre); i++; continue;
						}
						var t = line.trim(), bullet = bulletOf(t);
						if (bullet) {
							endPara();
							if (!list || list.ordered !== bullet.ordered) { list = { ordered: bullet.ordered, node: document.createElement(bullet.ordered ? "ol" : "ul") }; el.appendChild(list.node); }
							var li = document.createElement("li"); inline(li, bullet.text); list.node.appendChild(li);
						} else if (!t) { endPara(); endList(); }
						else {
							endList();
							var h = headOf(t);
							if (h !== null) { endPara(); var hd = document.createElement("strong"); hd.className = "md-h"; inline(hd, h); el.appendChild(hd); }
							else { if (!para) { para = document.createElement("p"); el.appendChild(para); } else { para.appendChild(document.createElement("br")); } inline(para, t); }
						}
						i++;
					}
				},
				// ---- the AI page ----
				// ---- MCP servers (admin) ----
				mcp: null, mcpError: "", mcpBusy: "", mcpConfirm: null, mcpOpen: "", mcpDraft: [], mcpAdd: { name: "", url: "", hint: "" },
				loadMcp: async function () {
					try { var r = await this.api("ai/mcp"); this.mcp = r.ok ? await r.json() : null; } catch (e) { this.mcp = null; }
				},
				mcpStatus: function (sv) {
					if (sv.status === "ok") { return sv.enabled ? "ok" : "reachable, off"; }
					if (sv.status === "pending") { return "not looked at yet"; }
					if (sv.status === "disabled") { return "off"; }
					return sv.status + (sv.reason ? ": " + sv.reason : "");
				},
				// One call to the server; the answer is the whole list again
				mcpCall: async function (id, path, opts, form) {
					this.mcpBusy = id; this.mcpError = "";
					try {
						var o = Object.assign({ method: "POST" }, opts || {});
						if (form) { o.headers = { "Content-Type": "application/x-www-form-urlencoded" }; o.body = new URLSearchParams(form).toString(); }
						var r = await this.api(path, o), j = await r.json().catch(function () { return {}; });
						if (r.ok) { this.mcp = j; await this.loadAgent(); return true; }
						this.mcpError = j.error || "The change was refused."; return false;
					} finally { this.mcpBusy = ""; }
				},
				mcpToggle: function (sv) {
					if (!this.mcp.canChange) { return; }
					if (sv.enabled) { this.mcpEnable(sv, false, false); } else { this.mcpConfirm = { id: sv.id, name: sv.name, url: sv.url }; }
				},
				mcpEnable: async function (sv, on, confirmed) {
					if (on && !confirmed) { return; }
					this.mcpConfirm = null;
					if (await this.mcpCall(sv.id, "ai/mcp/" + encodeURIComponent(sv.id) + "/enable", {}, { enabled: on ? "true" : "false" })) { this.toast(sv.name + (on ? " is on" : " is off")); }
				},
				mcpTrust: async function (sv) {
					if (!this.mcp.canChange) { return; }
					await this.mcpCall(sv.id, "ai/mcp", {}, { id: sv.id, trusted: sv.trusted ? "false" : "true" });
				},
				mcpTest: async function (sv) {
					if (await this.mcpCall(sv.id, "ai/mcp/" + encodeURIComponent(sv.id) + "/test")) { this.toast("Looked at " + sv.name); }
				},
				mcpRemove: async function (sv) {
					if (!confirm("Remove " + sv.name + "?")) { return; }
					await this.mcpCall(sv.id, "ai/mcp/" + encodeURIComponent(sv.id), { method: "DELETE" });
				},
				mcpPickTool: function (name, on) {
					var i = this.mcpDraft.indexOf(name);
					if (on && i < 0) { this.mcpDraft.push(name); } else if (!on && i >= 0) { this.mcpDraft.splice(i, 1); }
				},
				mcpSaveTools: async function (sv) {
					var draft = this.mcpDraft, all = sv.builtin && sv.tools.length > 0 && sv.tools.every(function (t) { return t.defaultOn === (draft.indexOf(t.name) >= 0); });
					if (await this.mcpCall(sv.id, "ai/mcp", {}, { id: sv.id, tools: JSON.stringify(all ? ["*"] : this.mcpDraft) })) { this.toast("Allowed tools saved"); }
				},
				// A hint as the address is typed. The server decides; this only saves a round trip.
				mcpUrlHint: function () {
					var u = this.mcpAdd.url.trim().toLowerCase(), h = "";
					if (u && u.indexOf("https://") !== 0) {
						var rest = u.slice(7), c1 = rest.indexOf(":"), c2 = rest.indexOf("/"), cut = c1 < 0 ? c2 : c2 < 0 ? c1 : Math.min(c1, c2), host = cut < 0 ? rest : rest.slice(0, cut);
						var loop = host === "localhost" || host === "[::1]" || host.indexOf("127.") === 0;
						if (u.indexOf("http://") === 0 && !loop) { h = "Only https:// is accepted. Plain http works for localhost only."; }
					}
					if (u.indexOf("@") > 0 && u.indexOf("://") > 0 && u.indexOf("@") < (u.indexOf("/", u.indexOf("://") + 3) < 0 ? u.length : u.indexOf("/", u.indexOf("://") + 3))) { h = "Leave the user name and password out of the address."; }
					this.mcpAdd.hint = h;
				},
				mcpAddServer: async function () {
					if (await this.mcpCall("+", "ai/mcp", {}, { name: this.mcpAdd.name.trim(), url: this.mcpAdd.url.trim() })) { this.mcpAdd = { name: "", url: "", hint: "" }; this.toast("Server added, switched off"); }
				},
				aic: null, aiForm: {}, aiSaving: false, aiTesting: false, aiTestOut: null, aiError: "",
				loadAiConfig: async function () {
					try { this.aic = await (await this.api("ai/config")).json(); this.aiForm = Object.assign({}, this.aic.values); } catch (e) { this.aic = null; }
				},
				aiDirty: function () { var self = this; return this.aic ? Object.keys(this.aiForm).filter(function (k) { return String(self.aiForm[k]) !== String(self.aic.values[k]); }) : []; },
				saveAiConfig: async function () {
					var self = this, changes = {};
					this.aiDirty().forEach(function (k) { changes[k] = self.aiForm[k]; });
					this.aiSaving = true; this.aiError = "";
					try {
						var r = await this.api("ai/config", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ changes: JSON.stringify(changes) }).toString() });
						var j = await r.json();
						if (r.ok) { this.aic = j; this.aiForm = Object.assign({}, j.values); this.toast("AI settings applied"); await this.loadAgent(); } else { this.aiError = j.error || "Could not save"; }
					} finally { this.aiSaving = false; }
				},
				testAi: async function () {
					this.aiTesting = true; this.aiTestOut = null;
					try {
						var r = await this.api("ai/test", { method: "POST" });
						var j = await r.json(); this.aiTestOut = r.ok ? j : { ok: false, message: j.error || "The test failed" };
						await this.loadAiConfig();
					} finally { this.aiTesting = false; }
				},
				errl: null, esel: "", egroup: null, esample: 0, rep: null, repTimer: null,
				ago: function (t) { var sec = Math.max(0, Math.round((Date.now() - t) / 1000)); return sec < 5 ? "just now" : this.dur(sec) + " ago"; },
				cur: function () { return this.egroup && this.egroup.samples.length ? this.egroup.samples[Math.min(this.esample, this.egroup.samples.length - 1)] : null; },
				loadErrors: async function () { this.errl = await (await this.api("errors")).json(); if (!this.esel && this.errl.groups.length) { this.pickError(this.errl.groups[0].id); } else if (this.esel) { this.pickError(this.esel, true); } },
				pickError: async function (id, keep) { this.esel = id; if (!keep) { this.esample = 0; } var r = await this.api("errors/" + encodeURIComponent(id)); this.egroup = r.ok ? await r.json() : null; if (this.egroup && this.esample >= this.egroup.samples.length) { this.esample = Math.max(0, this.egroup.samples.length - 1); } },
				resetErrors: async function () { if (!confirm("Forget every error group?")) { return; } this.errl = await (await this.api("errors/reset", { method: "POST" })).json(); this.esel = ""; this.egroup = null; },
				loadReports: async function () { this.rep = await (await this.api("reports")).json(); },
				resetReports: async function () { if (!confirm("Reset the counters for this run? Totals since first install are kept.")) { return; } this.rep = await (await this.api("reports/reset", { method: "POST" })).json(); },
				repPath: function (errors) {
					var s = this.rep ? this.rep.series.slice(-120) : [], max = 1, w = 400 / 120, d = "";
					s.forEach(function (p) { if (p.requests > max) { max = p.requests; } });
					s.forEach(function (p, i) {
						var v = errors ? p.errors : p.requests, h = v * 80 / max;
						if (h > 0) { d += "M" + (i * w).toFixed(1) + " " + (88 - h).toFixed(1) + "h" + Math.max(1, w - 1).toFixed(1) + "v" + h.toFixed(1) + "h-" + Math.max(1, w - 1).toFixed(1) + "z"; }
					});
					return d;
				},
				inf: null, isel: "", istack: null, qs: null, qsort: "max", qfilter: "", qpick: null,
				loadInflight: async function () { this.inf = await (await this.api("inflight")).json(); if (this.isel) { this.pickInflight(this.isel); } },
				pickInflight: async function (id) { this.isel = id; var r = await this.api("inflight/" + encodeURIComponent(id)); this.istack = await r.json(); },
				loadQueries: async function () { this.qs = await (await this.api("queries")).json(); },
				qrows: function () {
					var q = this.qfilter.toLowerCase(), key = { max: "maxMs", total: "totalMs", count: "count", fail: "failures" }[this.qsort];
					return this.qs.statements.filter(function (s) { return !q || s.sql.toLowerCase().indexOf(q) >= 0; }).sort(function (a, b) { return b[key] - a[key]; }).slice(0, 200);
				},
				resetQueries: async function () { this.qs = await (await this.api("queries/reset", { method: "POST" })).json(); this.qpick = null; },
				openRequest: function (id) { this.go("requests"); this.pick(id); },
				orml: null,
				loadOrm: async function () { this.loadIntegrations(); var r = await this.api("orm"); this.orml = r.ok ? await r.json() : null; },
				ormStatistics: async function (app, on) {
					var r = await this.api("orm/statistics", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ app: app, enabled: String(on) }).toString() });
					var j = await r.json();
					if (r.ok) { this.orml = j; this.toast(on ? "Statistics are on" : "Statistics are off"); } else { this.toast(j.error || "Could not change the statistics"); }
				},
				ints: null,
				loadIntegrations: async function () { var r = await this.api("integrations"); this.ints = r.ok ? await r.json() : null; },
				intOf: function (id) { return this.ints ? (this.ints.integrations.find(function (i) { return i.id === id; }) || null) : null; },
				intStatusText: function (i) { return { on: "On", available: "Available, off", notInstalled: "Not installed" }[i.status] || i.status; },
				intHint: function (i) { return i.status === "notInstalled" ? i.hint : (i.status === "available" ? "Installed. Turn on to listen." : "Listening."); },
				setIntegration: async function (i, on) {
					var r = await this.api("integrations/" + encodeURIComponent(i.id), { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ enabled: String(on) }).toString() });
					var j = await r.json();
					if (r.ok) { this.ints = j; this.toast(i.name + (on ? " is on" : " is off")); if (this.tab === "orm") { this.loadOrm(); } } else { this.toast(j.error || "Could not change the integration"); this.loadIntegrations(); }
				},
				modl: null, modsel: "", modq: "",
				loadModules: async function () { this.modl = await (await this.api("modules")).json(); if (!this.modsel && this.modl.modules.length) { this.modsel = this.modl.modules[0].name; } },
				modRows: function () { var q = this.modq.toLowerCase(); return this.modl.modules.filter(function (m) { return !q || m.name.toLowerCase().indexOf(q) >= 0; }); },
				modcur: function () { var s = this.modsel; return this.modl ? (this.modl.modules.find(function (m) { return m.name === s; }) || null) : null; },
				envd: null, envView: "modules", envq: "", base: base(),
				cfgd: null, cfgq: "",
				loadConfig: async function () { var r = await this.api("configuration"); this.cfgd = r.ok ? await r.json() : null; },
				cfgGroups: function () {
					var q = this.cfgq.toLowerCase();
					if (!this.cfgd) { return []; }
					return this.cfgd.groups.map(function (g) {
						return { name: g.name, admin: g.admin, entries: g.entries.filter(function (e) { return !q || e.key.toLowerCase().indexOf(q) >= 0 || JSON.stringify(e.value).toLowerCase().indexOf(q) >= 0 || g.name.toLowerCase().indexOf(q) >= 0; }) };
					}).filter(function (g) { return g.entries.length; });
				},
				cfgText: function (v) { return v !== null && typeof v === "object" ? JSON.stringify(v) : String(v); },
				loadEnv: async function () { this.envd = await (await this.api("environment")).json(); },
				envRows: function () {
					var rows = this.envView === "env" ? this.envd.env : this.envd.properties, q = this.envq.toLowerCase();
					return rows.filter(function (r) { return !q || r.name.toLowerCase().indexOf(q) >= 0; });
				},
				logl: null, lfile: "", llines: [], lq: "", llevel: "", lcount: "500", lcut: false, llive: true, loffset: 0, lastLevel: "",
				logHref: function () { return base() + "/api/logfiles/download?file=" + encodeURIComponent(this.lfile); },
				lineClass: function (l) { return /\[\s*ERROR\s*]/.test(l) ? "lerr" : /\[\s*WARN\s*]/.test(l) ? "lwarn" : /^\s+(at |\.\.\.|Caused by)/.test(l) ? "lmute" : ""; },
				loadLogs: async function () { this.logl = await (await this.api("logfiles")).json(); if (!this.lfile && this.logl.files.length) { this.pickLog(this.logl.files[0].name); } else if (this.lfile) { this.readLog(); } },
				pickLog: function (n) { this.lfile = n; this.lq = ""; this.llevel = ""; this.readLog(); },
				readLog: async function () {
					if (!this.lfile) { return; }
					var r = await this.api("logfiles/read?file=" + encodeURIComponent(this.lfile) + "&lines=" + this.lcount + "&q=" + encodeURIComponent(this.lq) + "&level=" + this.llevel);
					if (!r.ok) { return; }
					var j = await r.json();
					this.llines = j.lines; this.lcut = j.cut; this.loffset = j.offset;
					this.connect();
					this.scrollLog();
				},
				scrollLog: function () { var self = this; this.$nextTick(function () { var el = self.$refs.logview; if (el) { el.scrollTop = el.scrollHeight; } }); },
				onLogLines: function (lines) {
					var q = this.lq.toLowerCase(), order = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"], min = this.llevel ? order.indexOf(this.llevel) : 0, add = [];
					for (var i = 0; i < lines.length; i++) {
						var l = lines[i], m = /\[\s*(TRACE|DEBUG|INFO|WARN|ERROR)\s*]/.exec(l);
						if (m) { this.lastLevel = m[1]; }
						if (min > 0 && order.indexOf(this.lastLevel || "TRACE") < min) { continue; }
						if (q && l.toLowerCase().indexOf(q) < 0) { continue; }
						add.push(l);
					}
					if (add.length) { this.llines = this.llines.concat(add).slice(-3000); this.scrollLog(); }
				},
				cachel: null, csel: "", ckeys: null, cfilter: "", cvalue: null, cconfirm: false, cmsg: "",
				loadCaches: async function () { this.cachel = await (await this.api("caches")).json(); if (!this.csel && this.cachel.caches.length) { this.pickCache(this.cachel.caches[0].name); } },
				pickCache: function (n) { this.csel = n; this.cvalue = null; this.cmsg = ""; this.loadKeys(); },
				loadKeys: async function () {
					if (!this.csel) { return; }
					var r = await this.api("caches/" + encodeURIComponent(this.csel) + "/keys?filter=" + encodeURIComponent(this.cfilter));
					if (r.ok) { this.ckeys = await r.json(); }
				},
				viewValue: async function (k) {
					var r = await this.api("cachevalue/" + encodeURIComponent(this.csel) + "?key=" + encodeURIComponent(k));
					this.cvalue = await r.json();
				},
				cacheAct: async function (action, key) {
					var r = await this.api("caches/" + encodeURIComponent(this.csel) + "/" + action, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ key: key || "" }).toString() });
					var j = await r.json();
					this.cmsg = j.ok === false ? (j.message || j.error || "Failed") : (action === "evict" ? (j.evicted ? "Evicted" : "Key not found") : "Done, " + (j.removed || 0) + " object(s) removed");
					this.cvalue = null;
					await this.loadKeys(); await this.loadCaches();
				},
				dsl: null, dsRes: {}, dsBusy: "",
				dsClass: function (d) { return d.state === "ok" || d.state === "idle" ? (d.state === "ok" ? "on" : "hid") : "off"; },
				testDs: async function (d) {
					this.dsBusy = d.id;
					try { var r = await this.api("datasources/" + encodeURIComponent(d.id) + "/test", { method: "POST" }); this.dsRes[d.id] = await r.json(); this.dsRes = Object.assign({}, this.dsRes); } finally { this.dsBusy = ""; }
				},
				hd: null, hdConfirm: false, gcBusy: false, gcResult: "", hdTimer: null,
				hdHref: function () { return base() + "/api/heapdump/download"; },
				loadHd: async function () {
					try {
						this.hd = await (await this.api("heapdump")).json();
						var self = this;
						clearTimeout(this.hdTimer);
						if (this.hd.state === "running" || (this.hd.state === "ready" && this.tab === "system")) { this.hdTimer = setTimeout(function () { if (self.tab === "system") { self.loadHd(); } }, 2000); }
					} catch (e) { /* offline */ }
				},
				startHd: async function () {
					this.hdConfirm = false;
					var r = await this.api("heapdump", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: "confirm=true" });
					if (!r.ok) { this.toast((await r.json()).error || "Could not start"); }
					await this.loadHd();
				},
				discardHd: async function () { await this.api("heapdump/discard", { method: "POST" }); await this.loadHd(); },
				runGc: async function () {
					this.gcBusy = true;
					try {
						var r = await this.api("gc", { method: "POST" }); var j = await r.json();
						this.gcResult = r.ok ? "Freed " + this.bytes(j.freedBytes) + " in " + j.ms + " ms (" + this.bytes(j.beforeBytes) + " to " + this.bytes(j.afterBytes) + ")" : (j.error || "Could not run GC");
					} finally { this.gcBusy = false; }
				},
				onTab: function () {
					var self = this;
					clearInterval(this.threadTimer);
					if (this.tab === "system") { this.loadHd(); }
					if (this.tab === "logfiles") { this.loadLogs(); }
					if (this.tab === "inflight") { this.loadInflight(); }
					clearInterval(this.repTimer);
					if (this.tab === "errors" || this.tab === "reports") {
						var load = function () { if (self.tab === "errors") { self.loadErrors(); } else { self.loadReports(); } };
						load(); this.repTimer = setInterval(function () { if (!document.hidden) { load(); } }, 5000);
					}
					if (this.tab === "queries") { this.loadQueries(); }
					if (this.tab === "environment" && !this.envd) { this.loadEnv(); }
					if (this.tab === "configuration") { this.loadConfig(); }
					if (this.tab === "modules") { this.loadModules(); this.loadIntegrations(); }
					if (this.tab === "orm") { this.loadOrm(); this.ormTimer = setInterval(function () { if (self.tab === "orm" && !document.hidden) { self.loadOrm(); } }, 5000); } else { clearInterval(this.ormTimer); }
					if (this.tab !== "logfiles" && this.es) { this.connect(); }
					if (this.tab === "caches") { this.loadCaches(); this.cacheTimer = setInterval(function () { if (self.tab === "caches" && !document.hidden) { self.loadCaches(); } }, 5000); } else { clearInterval(this.cacheTimer); }
					if (this.tab === "designer" && !this.bar) { this.loadBar(); }
					if (this.tab === "ai") { this.loadAiConfig(); this.loadMcp(); }
					if (this.tab === "ask") { this.loadAgent(); }
					this.$nextTick(function () { self.placeAgent(); });
					if (this.tab === "threads") {
						this.loadThreads();
						this.threadTimer = setInterval(function () { if (self.live && self.tab === "threads" && !document.hidden) { self.loadThreads(); } }, 5000);
					}
				},
				go: function (t) { this.tab = t; location.hash = t; this.onTab(); },
				groups: function () {
					var out = [], seen = {};
					(this.state ? this.state.tabs : []).forEach(function (t) { if (!seen[t.group]) { seen[t.group] = { name: t.group, tabs: [] }; out.push(seen[t.group]); } seen[t.group].tabs.push(t); });
					return out;
				},
				badge: function (id) {
					if (id === "requests") { return { n: this.requests.length, c: "" }; }
					if (id === "executors") { var c = (this.execCounts.critical || 0), d = (this.execCounts.degraded || 0); return { n: c + d, c: c ? "crit" : "warn" }; }
					if (id === "tasks" && this.tasks) { return { n: this.tasks.failing, c: "crit" }; }
					if (id === "threads" && this.system) { return { n: this.system.threads.live, c: "" }; }
					return { n: 0, c: "" };
				},
				badges: function () { /* attention entries come from the server; nothing else to compute */ },
				attn: function () {
					var out = [], self = this;
					this.executors.forEach(function (e) { if (e.healthStatus === "critical" || e.healthStatus === "degraded") { out.push({ severity: e.healthStatus === "critical" ? "crit" : "warn", text: "Executor " + e.name + " is " + e.healthStatus + ": " + e.healthReport.summary, go: "executors" }); } });
					this.allTasks().forEach(function (t) { if (t.status === "failing" || t.status === "error") { out.push({ severity: "crit", text: "Task " + t.name + " is failing" + (t.outcome && t.outcome.message ? ": " + t.outcome.message : ""), go: "tasks" }); } });
					if (this.system && this.system.threads.deadlocked) { out.push({ severity: "crit", text: "Deadlocked threads detected", go: "threads" }); }
					return out.concat(this.overview ? this.overview.attention : []);
				},
				toast: function (m) { var self = this; this.toastMsg = m; setTimeout(function () { self.toastMsg = ""; }, 2400); },
				logout: async function () {
					this.disconnect();
					await fetch(base() + "/logout", { method: "POST", credentials: "same-origin", headers: { "X-Lens-CSRF": body().csrf } });
					location.reload();
				},
				fmt: function (ms) { return ms >= 1000 ? (ms / 1000).toFixed(2) + " s" : (Math.round(ms * 10) / 10) + " ms"; },
				bytes: function (n) { if (n === null || n === undefined || n < 0) { return "n/a"; } var u = ["B", "KB", "MB", "GB", "TB"], i = 0; while (n >= 1024 && i < 4) { n /= 1024; i++; } return (i ? n.toFixed(n >= 100 ? 0 : 1) : n) + " " + u[i]; },
				dur: function (sec) { sec = Math.max(0, Math.round(sec)); var d = Math.floor(sec / 86400), h = Math.floor(sec % 86400 / 3600), m = Math.floor(sec % 3600 / 60), s = sec % 60; return d ? d + "d " + h + "h" : h ? h + "h " + m + "m" : m ? m + "m " + s + "s" : s + "s"; },
				time: function (ms) { return new Date(ms).toTimeString().slice(0, 8); },
				statusClass: function (s) { return s >= 500 ? "crit" : s >= 400 ? "warn" : "ok"; },
				line: function (a, mx, w, h) {
					if (!a || a.length < 2) { return ""; }
					return "M" + a.map(function (v, i) { return (i / (a.length - 1) * w).toFixed(1) + "," + (h - 3 - Math.min(1, v / mx) * (h - 8)).toFixed(1); }).join("L");
				},
				push: function (arr, v, n) { arr.push(v); if (arr.length > (n || 60)) { arr.shift(); } },

				// ---- requests ----
				filtered: function () {
					var f = this.rf, q = this.rq.toLowerCase();
					return this.requests.filter(function (r) {
						return (f === "all" || (f === "err" && r.status >= 400) || (f === "slow" && r.ms > 400) || (f === "json" && r.type === "json")) && (!q || r.url.toLowerCase().indexOf(q) > -1);
					});
				},
				pick: async function (id) {
					this.rsel = id; this.detailErr = "";
					try {
						var r = await this.api("requests/" + encodeURIComponent(id));
						if (!r.ok) { this.detail = null; this.detailErr = "That request has been recycled."; return; }
						this.detail = await r.json();
					} catch (e) { this.detail = null; }
				},
				bars: function () {
					if (!this.detail) { return []; }
					var spans = this.detail.spans.slice(0, 40), total = spans.reduce(function (m, s) { return Math.max(m, s.start + s.dur); }, Math.max(this.detail.request.durationMs, 0.001));
					return spans.map(function (s, i) {
						return { key: s.id, type: s.type + (s.interrupted ? " cut" : ""), label: (s.interrupted ? "interrupted: " : "") + (s.label.indexOf("/") > -1 ? s.label.split("/").pop() : s.label), left: (s.start / total) * 100, width: Math.max(0.6, (s.dur / total) * 100), top: i * 16 };
					});
				},
				copyJson: async function () { try { await navigator.clipboard.writeText(JSON.stringify(this.detail, null, 2)); this.toast("Request JSON copied"); } catch (e) { this.toast("Could not copy"); } },
				downloadJson: function () {
					var a = document.createElement("a");
					a.href = URL.createObjectURL(new Blob([JSON.stringify(this.detail, null, 2)], { type: "application/json" }));
					a.download = "lens-request-" + this.detail.request.id + ".json"; document.body.appendChild(a); a.click(); a.remove();
				},
				copyCurl: async function () {
					var q = function (s) { return "'" + String(s).replace(/'/g, "'\\''") + "'"; };
					var r = this.detail.request, h = this.detail.headers || {}, parts = ["curl -X " + q(r.method) + " " + q(r.url + (r.query ? "?" + r.query : ""))];
					var note = r.serverId ? "# handled by server " + r.serverHost + " " + r.serverIp + " (" + r.serverId + ")\n" : "";
					Object.keys(h).filter(function (k) { return ["host", "content-length", "connection"].indexOf(k.toLowerCase()) < 0 && h[k] !== "[redacted]"; }).forEach(function (k) { parts.push("-H " + q(k + ": " + h[k])); });
					try { await navigator.clipboard.writeText(note + parts.join(" \\\n  ")); this.toast("cURL copied. Redacted headers are left out."); } catch (e) { this.toast("Could not copy"); }
				},
				wfHeight: function () { return Math.min(40, this.bars().length) * 16 + 8; },
				spark: function () { return this.overview ? this.line(this.overview.series, Math.max(1, Math.max.apply(null, this.overview.series)), 240, 46) : ""; },

				// ---- executors ----
				setExecutors: function (d) {
					var self = this;
					this.executors = d.executors; this.execCounts = d.counts;
					d.executors.forEach(function (e) {
						var h = self.hist[e.name] || (self.hist[e.name] = { a: [], q: [] });
						self.push(h.a, e.activeCount || 0); self.push(h.q, e.queueSize || 0);
					});
					if (!this.esel && d.executors.length) { this.esel = d.executors[0].name; }
				},
				exec: function () { var n = this.esel; return this.executors.find(function (e) { return e.name === n; }) || null; },
				execBarPct: function (e) { return e.maximumPoolSize ? Math.min(100, Math.round(e.activeCount / e.maximumPoolSize * 100)) : Math.min(100, (e.activeCount || 0) * 10); },
				execBarClass: function (e) { return e.healthStatus === "critical" ? "crit" : e.healthStatus === "degraded" ? "warn" : ""; },
				meterClass: function (v, deg, crit, invert) { return invert ? (v < crit ? "critical" : v < deg ? "degraded" : "") : (v > crit ? "critical" : v > deg ? "degraded" : ""); },
				meters: function (e) {
					var t = e.thresholds || {};
					function th(k, d, c) { var x = t[k] || {}; return [x.degraded === undefined ? d : x.degraded, x.critical === undefined ? c : x.critical]; }
					var p = th("poolUtilization", 75, 95), u = th("threadUtilization", 75, 95), q = th("queueUtilization", 70, 95), c = th("taskCompletionRate", 50, 25);
					var self = this;
					return [
						{ label: "Pool utilization", v: e.poolUtilization || 0, d: p[0], c: p[1], inv: false },
						{ label: "Thread utilization", v: e.threadsUtilization || 0, d: u[0], c: u[1], inv: false },
						{ label: "Queue utilization", v: e.queueUtilization || 0, d: q[0], c: q[1], inv: false },
						{ label: "Task completion rate", v: e.taskCompletionRate === undefined ? 100 : (e.taskCompletionRate || 100), d: c[0], c: c[1], inv: true }
					].map(function (m) { m.cls = self.meterClass(m.v, m.d, m.c, m.inv); m.key = m.label; return m; });
				},
				qcap: function (e) { return e.queueCapacity === null || e.queueCapacity === undefined || e.queueCapacity > 1e6 || e.queueCapacity < 0 ? "unbounded" : e.queueCapacity; },
				qmax: function (e) { return !e.queueCapacity || e.queueCapacity > 1e6 || e.queueCapacity <= 0 ? 10 : Math.max(10, e.queueCapacity); },
				execIssues: function () { return this.executors.filter(function (e) { return e.healthStatus === "critical" || e.healthStatus === "degraded"; }); },

				// ---- tasks ----
				allTasks: function () { var out = []; if (this.tasks) { this.tasks.schedulers.forEach(function (s) { s.tasks.forEach(function (t) { out.push(t); }); }); } return out; },
				ktask: function () { var k = this.ksel; return this.allTasks().find(function (t) { return t.scheduler + "/" + t.name === k; }) || null; },
				ktasks: function (s) {
					var f = this.kf, q = this.kq.toLowerCase();
					return s.tasks.filter(function (t) {
						return (f === "all" || (f === "failing" ? (t.status === "failing" || t.status === "error") : t.status === f)) && (!q || (t.name + t.group + t.scheduler).toLowerCase().indexOf(q) > -1);
					});
				},
				kcount: function (f) {
					return this.allTasks().filter(function (t) { return f === "all" || (f === "failing" ? (t.status === "failing" || t.status === "error") : t.status === f); }).length;
				},
				statusLabel: function (s) { return s === "never" ? "not run yet" : s; },
				next: function (t) {
					if (t.status === "paused") { return "paused"; }
					if (!t.nextRun) { return "n/a"; }
					var s = (Date.parse(t.nextRun) - this.now) / 1000;
					return s <= 0 ? "due" : this.dur(s);
				},
				ago: function (iso) { if (!iso) { return "never"; } var s = (this.now - Date.parse(iso)) / 1000; return s < 5 ? "just now" : this.dur(s) + " ago"; },
				act: async function (path, label) {
					try {
						var r = await this.api("tasks/" + path, { method: "POST" });
						var j = await r.json();
						this.toast(j.message || (j.ok ? label + " done" : label + " failed"));
						this.tasks = await (await this.api("tasks")).json();
						return j;
					} catch (e) { this.toast(label + " failed"); return null; }
				},
				pauseResume: function (t) { return this.act(encodeURIComponent(t.scheduler) + "/" + encodeURIComponent(t.name) + "/" + (t.status === "paused" ? "resume" : "pause"), t.status === "paused" ? "Resume" : "Pause"); },
				runNow: async function (t) {
					this.ksel = t.scheduler + "/" + t.name; this.running = this.ksel; this.runResult = null;
					var j = await this.act(encodeURIComponent(t.scheduler) + "/" + encodeURIComponent(t.name) + "/run", "Run");
					this.runResult = j; this.running = "";
				},
				schedAct: function (s, a) { return this.act(encodeURIComponent(s.name) + "/" + a, a); },
				reloadAll: async function () {
					var self = this;
					this.confirm = "";
					for (var i = 0; i < this.tasks.schedulers.length; i++) { await this.act(encodeURIComponent(this.tasks.schedulers[i].name) + "/reload", "Reload"); }
					self.toast("Reloaded all schedulers from tasks.json");
				},
				defRows: function (t) { var d = t.definition || {}; return Object.keys(d).map(function (k) { return { k: k, v: String(d[k]) }; }); },

				// ---- bar designer ----
				loadBar: async function () {
					this.bar = await (await this.api("bar")).json();
					var layout = this.bar.layout, pos = {}, vis = {};
					layout.forEach(function (l, i) { pos[l.id] = i; vis[l.id] = l.visible; });
					this.design = this.bar.catalog.map(function (t, i) { return { id: t.id, label: t.label, custom: t.custom, hidden: t.hidden, visible: vis[t.id] === undefined ? true : vis[t.id], k: pos[t.id] === undefined ? 1000 + i : pos[t.id] }; })
						.sort(function (a, b) { return a.k - b.k; });
					this.dirty = false;
				},
				move: function (i, d) { var j = i + d; if (j < 0 || j >= this.design.length) { return; } var t = this.design.splice(i, 1)[0]; this.design.splice(j, 0, t); this.dirty = true; },
				dropOn: function (i) { if (this.dragFrom === null || this.dragFrom === i) { this.dragOver = null; return; } var t = this.design.splice(this.dragFrom, 1)[0]; this.design.splice(i, 0, t); this.dragFrom = null; this.dragOver = null; this.dirty = true; },
				previewTabs: function () { return this.design.filter(function (t) { return t.visible && !t.hidden; }); },
				saveDesign: async function () {
					var body = new URLSearchParams({ layout: JSON.stringify(this.design.map(function (t) { return { id: t.id, visible: t.visible }; })) }).toString();
					var r = await this.api("bar/layout", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: body });
					if (r.ok) { this.toast("Layout saved. Reload a page to see it."); await this.loadBar(); } else { this.toast("Could not save the layout"); }
				},
				resetDesign: async function () { var r = await this.api("bar/reset", { method: "POST" }); if (r.ok) { this.toast("Back to the default layout"); await this.loadBar(); } },

				// ---- system ----
				setSystem: function (s) {
					this.system = s;
					this.push(this.sys.cpu, s.cpu.processCpu || 0); this.push(this.sys.heap, s.memory.heap.used); this.push(this.sys.thr, s.threads.live);
				},
				pctOf: function (used, max) { return max > 0 ? Math.min(100, Math.round(used / max * 100)) : 0; },
				poolClass: function (p) { var x = this.pctOf(p.used, p.max); return p.max > 0 && x > 90 ? "crit" : p.max > 0 && x > 75 ? "warn" : ""; },
				ramUsed: function () { var r = this.system && this.system.ram; return r ? r.total - r.free : 0; },
				propRows: function () { var p = this.system ? this.system.runtime.properties : {}; return Object.keys(p).map(function (k) { return { k: k, v: p[k] }; }); },

				// ---- threads ----
				loadThreads: async function () {
					try {
						var t = await (await this.api("threads")).json();
						this.threads = t;
						if (this.tsel === null || !t.threads.some(function (x) { return x.id === this.tsel; }, this)) { var me = t.threads.find(function (x) { return x.self; }); this.tsel = (me || t.threads[0] || {}).id; }
					} catch (e) { /* ignore */ }
				},
				tlist: function () {
					if (!this.threads) { return []; }
					var f = this.tf, q = this.tq.toLowerCase(), p = this.tpool;
					return this.threads.threads.filter(function (t) {
						return (f === "ALL" || t.state === f) && (!p || t.pool === p) && (!q || (t.name + " " + t.frames.map(function (x) { return x.text; }).join(" ")).toLowerCase().indexOf(q) > -1);
					});
				},
				pools: function () { var s = {}; (this.threads ? this.threads.threads : []).forEach(function (t) { s[t.pool] = 1; }); return Object.keys(s).sort(); },
				tcount: function (st) { return this.threads ? (st === "ALL" ? this.threads.threads.length : (this.threads.states[st] || 0)) : 0; },
				thread: function () { var id = this.tsel; return this.threads ? this.threads.threads.find(function (t) { return t.id === id; }) || null : null; },
				copyDump: async function () {
					try {
						var r = await fetch(base() + "/api/threads/dump", { credentials: "same-origin", headers: { "X-Lens-CSRF": body().csrf } });
						await navigator.clipboard.writeText(await r.text());
						this.toast("Thread dump copied");
					} catch (e) { this.toast("Could not copy. Use Download."); }
				},
				copyStack: async function () {
					var t = this.thread(); if (!t) { return; }
					try { await navigator.clipboard.writeText('"' + t.name + '"\n' + t.frames.map(function (f) { return "\tat " + f.text; }).join("\n")); this.toast("Stack copied"); } catch (e) { this.toast("Could not copy"); }
				},
				dumpUrl: function () { return base() + "/api/threads/dump"; },

				licenseClass: function () { return this.state ? this.state.license.state : "none"; },
				showBanner: function () { return this.state && (this.state.license.state === "trial" || this.state.license.state === "expired"); },
				bannerText: function () {
					if (!this.state) { return ""; }
					return this.state.license.state === "expired" ? "The BoxLang+ license is not valid. Plus features may stop working." : "You are on a trial. Plus features stop working when it ends and need a license.";
				}
			};
		});
	});
})();
