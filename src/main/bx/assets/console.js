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
				state: null, tab: "overview", live: true, timer: null, toastMsg: "",
				overview: null, requests: [], capacity: 0, settings: null,
				rf: "all", rq: "", rsel: null, detail: null, detailErr: "",
				icon: icon,

				init: async function () {
					var self = this;
					await this.load();
					var h = location.hash.replace("#", "");
					if (this.state.tabs.some(function (t) { return t.id === h; })) { this.tab = h; }
					window.addEventListener("hashchange", function () { var t = location.hash.replace("#", ""); if (t) { self.tab = t; } });
					this.timer = setInterval(function () { if (self.live && !document.hidden) { self.refresh(); } }, 3000);
				},

				api: async function (path, opts) {
					opts = opts || {};
					opts.credentials = "same-origin";
					opts.headers = Object.assign({ "X-Lens-CSRF": body().csrf }, opts.headers || {});
					var r = await fetch(base() + "/api/" + path, opts);
					if (r.status === 401) { location.reload(); throw new Error("signed out"); }
					return r;
				},

				load: async function () {
					this.state = await (await this.api("state")).json();
					await this.refresh();
					this.settings = await (await this.api("settings")).json();
				},

				refresh: async function () {
					try {
						var o = await (await this.api("overview")).json();
						var r = await (await this.api("requests")).json();
						this.overview = o; this.requests = r.requests; this.capacity = r.capacity;
						if (this.rsel === null && this.requests.length) { this.pick(this.requests[0].id); }
					} catch (e) { /* signed out or offline: the next tick tries again */ }
				},

				go: function (t) { this.tab = t; location.hash = t; },
				groups: function () {
					var out = [], seen = {};
					(this.state ? this.state.tabs : []).forEach(function (t) { if (!seen[t.group]) { seen[t.group] = { name: t.group, tabs: [] }; out.push(seen[t.group]); } seen[t.group].tabs.push(t); });
					return out;
				},

				toast: function (m) { var self = this; this.toastMsg = m; setTimeout(function () { self.toastMsg = ""; }, 1800); },
				logout: async function () {
					await fetch(base() + "/logout", { method: "POST", credentials: "same-origin", headers: { "X-Lens-CSRF": body().csrf } });
					location.reload();
				},

				// requests
				filtered: function () {
					var f = this.rf, q = this.rq.toLowerCase(), slow = this.state ? 400 : 400;
					return this.requests.filter(function (r) {
						return (f === "all" || (f === "err" && r.status >= 400) || (f === "slow" && r.ms > slow) || (f === "json" && r.type === "json")) && (!q || r.url.toLowerCase().indexOf(q) > -1);
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
				time: function (ms) { var d = new Date(ms); return d.toTimeString().slice(0, 8); },
				fmt: function (ms) { return ms >= 1000 ? (ms / 1000).toFixed(2) + " s" : (Math.round(ms * 10) / 10) + " ms"; },
				statusClass: function (s) { return s >= 500 ? "crit" : s >= 400 ? "warn" : "ok"; },
				bars: function () {
					if (!this.detail) { return []; }
					var spans = this.detail.spans.slice(0, 40), total = spans.reduce(function (m, s) { return Math.max(m, s.start + s.dur); }, Math.max(this.detail.request.durationMs, 0.001));
					return spans.map(function (s, i) {
						return { key: s.id, type: s.type, label: s.label.indexOf("/") > -1 ? s.label.split("/").pop() : s.label, left: (s.start / total) * 100, width: Math.max(0.6, (s.dur / total) * 100), top: i * 16 };
					});
				},
				wfHeight: function () { return Math.min(40, this.bars().length) * 16 + 8; },

				// overview
				spark: function () {
					if (!this.overview) { return ""; }
					var s = this.overview.series, w = 240, h = 46, mx = Math.max(1, Math.max.apply(null, s));
					return "M" + s.map(function (v, i) { return (i / (s.length - 1) * w).toFixed(1) + "," + (h - 3 - (v / mx) * (h - 8)).toFixed(1); }).join("L");
				},

				licenseClass: function () { return this.state ? this.state.license.state : "none"; },
				showBanner: function () { return this.state && (this.state.license.state === "trial"); }
			};
		});
	});
})();
