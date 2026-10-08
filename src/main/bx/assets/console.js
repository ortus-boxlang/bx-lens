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
				overview: null, requests: [], capacity: 0, settings: null,
				rf: "all", rq: "", rsel: null, detail: null, detailErr: "",
				executors: [], execCounts: {}, esel: null, hist: {},
				tasks: null, ksel: null, kf: "all", kq: "", confirm: "", runResult: null, running: "",
				system: null, sys: { cpu: [], heap: [], thr: [] },
				bar: null, design: [], dirty: false, dragFrom: null, dragOver: null,
				threads: null, tsel: null, tf: "ALL", tq: "", tpool: "", threadTimer: null,
				icon: icon,

				init: async function () {
					var self = this;
					await this.load();
					var h = location.hash.replace("#", ""), deep = h.split("/");
					h = deep[0];
					if (this.state.tabs.some(function (t) { return t.id === h; })) { this.tab = h; }
					if (h === "requests" && deep[1]) { this.pick(decodeURIComponent(deep[1])); }
					window.addEventListener("hashchange", function () { var t = location.hash.replace("#", ""); if (t) { self.tab = t; self.onTab(); } });
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
					var topics = ["requests"].concat(["executors", "tasks", "datasources", "system"].filter(function (t) { return self.has(t); })).join(",");
					this.es = new EventSource(base() + "/stream?topics=" + topics, { withCredentials: true });
					this.es.addEventListener("tick", function (e) { self.streamOk = true; self.onTick(JSON.parse(e.data)); });
					this.es.onerror = function () { self.streamOk = false; };
				},
				disconnect: function () { if (this.es) { this.es.close(); this.es = null; } this.streamOk = false; },
				onTick: function (d) {
					if (d.overview) { this.overview = d.overview; this.badges(); }
					if (d.requests) { this.requests = d.requests; if (this.rsel === null && d.requests.length) { this.pick(d.requests[0].id); } }
					if (d.executors) { this.setExecutors(d.executors); }
					if (d.tasks) { this.tasks = d.tasks; if (!this.ksel && this.allTasks().length) { this.ksel = this.allTasks()[0].scheduler + "/" + this.allTasks()[0].name; } }
					if (d.system) { this.setSystem(d.system); }
					if (d.datasources) { this.dsl = d.datasources; }
				},
				load: async function () {
					this.state = await (await this.api("state")).json();
					await this.refresh();
					this.settings = await (await this.api("settings")).json();
					if (this.has("executors")) { this.setExecutors(await (await this.api("executors")).json()); }
					if (this.has("tasks")) { this.tasks = await (await this.api("tasks")).json(); var a = this.allTasks(); if (a.length) { this.ksel = a[0].scheduler + "/" + a[0].name; } }
					if (this.has("system")) { this.setSystem(await (await this.api("system")).json()); }
					if (this.has("datasources")) { this.dsl = await (await this.api("datasources")).json(); }
				},
				refresh: async function () {
					try {
						var o = await (await this.api("overview")).json();
						var r = await (await this.api("requests")).json();
						this.overview = o; this.requests = r.requests; this.capacity = r.capacity;
						if (this.rsel === null && this.requests.length) { this.pick(this.requests[0].id); }
						if (this.has("executors")) { this.setExecutors(await (await this.api("executors")).json()); }
						if (this.has("tasks")) { this.tasks = await (await this.api("tasks")).json(); }
						if (this.has("system")) { this.setSystem(await (await this.api("system")).json()); }
					} catch (e) { /* signed out or offline: the next tick tries again */ }
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
					if (this.tab === "caches") { this.loadCaches(); this.cacheTimer = setInterval(function () { if (self.tab === "caches" && !document.hidden) { self.loadCaches(); } }, 5000); } else { clearInterval(this.cacheTimer); }
					if (this.tab === "designer" && !this.bar) { this.loadBar(); }
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
						return { key: s.id, type: s.type, label: s.label.indexOf("/") > -1 ? s.label.split("/").pop() : s.label, left: (s.start / total) * 100, width: Math.max(0.6, (s.dur / total) * 100), top: i * 16 };
					});
				},
				copyJson: async function () { try { await navigator.clipboard.writeText(JSON.stringify(this.detail, null, 2)); this.toast("Request JSON copied"); } catch (e) { this.toast("Could not copy"); } },
				downloadJson: function () {
					var a = document.createElement("a");
					a.href = URL.createObjectURL(new Blob([JSON.stringify(this.detail, null, 2)], { type: "application/json" }));
					a.download = "lens-request-" + this.detail.request.id + ".json"; document.body.appendChild(a); a.click(); a.remove();
				},
				copyCurl: async function () {
					var r = this.detail.request, h = this.detail.headers || {}, parts = ["curl -X " + r.method + " '" + r.url + (r.query ? "?" + r.query : "") + "'"];
					Object.keys(h).filter(function (k) { return ["host", "content-length", "connection"].indexOf(k.toLowerCase()) < 0 && h[k] !== "[redacted]"; }).forEach(function (k) { parts.push("-H '" + k + ": " + String(h[k]).replace(/'/g, "'\\''") + "'"); });
					try { await navigator.clipboard.writeText(parts.join(" \\\n  ")); this.toast("cURL copied. Redacted headers are left out."); } catch (e) { this.toast("Could not copy"); }
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
				qcap: function (e) { return e.queueCapacity > 1e6 || e.queueCapacity < 0 ? "unbounded" : e.queueCapacity; },
				qmax: function (e) { return e.queueCapacity > 1e6 || e.queueCapacity <= 0 ? 10 : Math.max(10, e.queueCapacity); },
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
