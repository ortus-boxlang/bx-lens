/* BX Lens UI. An Alpine.js component. The request data is read from <script id="bxlens-data">. Nothing here calls the network. */
( function () {
	"use strict";

	var TYPES = [
		{ id: "template", label: "Template" },
		{ id: "func", label: "Function" },
		{ id: "query", label: "Query" },
		{ id: "http", label: "HTTP" },
		{ id: "tx", label: "Transaction" },
		{ id: "timer", label: "Timer" },
		{ id: "custom", label: "Custom" }
	];
	var STORE_KEY = "bxlens.state";

	function component() {
		return {
			// ---- data
			d: null, r: null, ui: {}, history: [], declared: [],
			// ---- ui state
			tab: "timeline", collapsed: true, detached: false, menu: false, menuStyle: "", height: 360, theme: "auto",
			v0: 0, v1: 0, total: 0, sel: null, q: "", filters: {}, toastText: "", base: "",
			_y0: null, _h0: 0, _toastTimer: null,

			init: function () {
				var node = document.getElementById( "bxlens-data" );
				if ( !node ) { return; }
				var page;
				try { page = JSON.parse( node.textContent ); } catch ( e ) { return; }
				this.ui = page.ui || {};
				this.history = page.history || [];
				this.declared = page.declared || [];
				this.d = page.data;
				this.r = this.d.request;
				// Spans contributed by panels may run past the request clock, so the axis covers them too
				this.total = this.d.spans.reduce( function ( m, s ) { return Math.max( m, s.start + s.dur ); }, Math.max( 1, this.r.durationMs ) );
				this.v0 = 0; this.v1 = this.total;
				TYPES.forEach( function ( t ) { this.filters[ t.id ] = true; }, this );
				this.height = this.ui.height || 360;
				this.tab = this.ui.defaultTab || "timeline";
				this.collapsed = !this.ui.startOpen;
				this.theme = this.ui.theme || "auto";
				this.base = commonBase( this.d );
				var saved = load();
				if ( saved.h ) { this.height = saved.h; }
				if ( saved.tab ) { this.tab = saved.tab; }
				if ( typeof saved.collapsed === "boolean" ) { this.collapsed = saved.collapsed; }
				if ( saved.theme ) { this.theme = saved.theme; }
				if ( saved.detached && this.ui.allowDetach ) { this.detached = true; }
				// A problem opens the panel on Issues, whatever was saved
				if ( this.ui.autoOpenOnException && this.d.exceptions.length ) {
					this.collapsed = false;
					this.tab = this.d.issues.length ? "issues" : "exceptions";
				}
				if ( !this.tabIds().includes( this.tab ) ) { this.tab = this.tabIds()[ 0 ] || "timeline"; }
				this.applyTheme();
				var self = this;
				document.addEventListener( "keydown", function ( e ) { self.onKey( e ); } );
				if ( window.matchMedia ) {
					window.matchMedia( "(prefers-color-scheme: dark)" ).addEventListener( "change", function () { self.applyTheme(); } );
				}
			},

			// ---- derived
			get sev() { return this.r ? this.r.severity : "none"; },
			get statusClass() { var s = this.r.status; return s >= 500 ? "crit" : ( s >= 400 ? "warn" : "ok" ); },
			get hotkeyLabel() { return this.ui.hotkey || "Ctrl+`"; },
			get spanTypes() {
				var present = {};
				this.d.spans.forEach( function ( s ) { present[ s.type ] = true; } );
				return TYPES.filter( function ( t ) { return present[ t.id ]; } );
			},
			get rows() {
				var self = this, q = this.q.toLowerCase(), out = [];
				for ( var i = 0; i < this.d.spans.length && out.length < 600; i++ ) {
					var s = this.d.spans[ i ];
					if ( self.filters[ s.type ] === false ) { continue; }
					if ( s.start + s.dur < self.v0 || s.start > self.v1 ) { continue; }
					if ( q && ( s.label + " " + ( s.file || "" ) + " " + ( s.detail && s.detail.sql || "" ) ).toLowerCase().indexOf( q ) < 0 ) { continue; }
					out.push( s );
				}
				return out;
			},
			get ticks() {
				var span = this.v1 - this.v0, out = [];
				for ( var i = 0; i <= 4; i++ ) {
					var v = this.v0 + span * i / 4;
					out.push( span < 20 ? Math.round( v * 10 ) / 10 : Math.round( v ) );
				}
				return out;
			},
			get selSpan() {
				var id = this.sel;
				return id === null ? null : ( this.d.spans.filter( function ( s ) { return s.id === id; } )[ 0 ] || null );
			},
			get sortedIssues() {
				var rank = { crit: 0, warn: 1, info: 2 };
				return this.d.issues.slice().sort( function ( a, b ) { return ( rank[ a.severity ] === undefined ? 1 : rank[ a.severity ] ) - ( rank[ b.severity ] === undefined ? 1 : rank[ b.severity ] ); } );
			},
			get treeSpans() {
				return this.d.spans.filter( function ( s ) { return s.type === "template" || s.type === "func"; } );
			},
			get customPanels() {
				var known = {}, declared = {};
				this.declared.forEach( function ( p ) { declared[ p.id ] = p; } );
				// A declaration (onLensRegister) sets how the tab looks, the request data fills it
				var out = ( this.d.panels || [] ).map( function ( p ) {
					known[ p.id ] = true;
					var d = declared[ p.id ];
					return d ? Object.assign( {}, p, { label: d.label, icon: d.icon, order: d.order } ) : p;
				} );
				this.declared.forEach( function ( p ) {
					if ( !known[ p.id ] ) { out.push( { id: p.id, label: p.label, icon: p.icon, order: p.order, renderer: p.renderer, badge: { count: -1, severity: "none" }, content: {} } ); }
				} );
				return out.sort( function ( a, b ) { return a.order - b.order; } );
			},
			get tabs() {
				var d = this.d, c = d.counts, sevOf = function ( t ) { return d.issues.some( function ( i ) { return i.tab === t && i.severity === "crit"; } ) ? "crit" : ( d.issues.some( function ( i ) { return i.tab === t; } ) ? "warn" : "" ); };
				var list = [
					{ id: "issues", label: "Issues", badge: c.issues || null, sev: this.sev === "none" ? "" : this.sev },
					{ id: "timeline", label: "Timeline", badge: null, sev: "" },
					{ id: "queries", label: "Queries", badge: c.queries || null, sev: sevOf( "queries" ) },
					{ id: "templates", label: "Templates", badge: c.templates || null, sev: "" },
					{ id: "http", label: "HTTP", badge: c.http || null, sev: "" },
					{ id: "exceptions", label: "Exceptions", badge: c.exceptions || null, sev: c.exceptions ? "crit" : "" },
					{ id: "messages", label: "Messages", badge: ( c.messages + ( c.logs || 0 ) ) || null, sev: "" },
					{ id: "timers", label: "Timers", badge: c.timers || null, sev: "" },
					{ id: "cache", label: "Cache", badge: d.cache ? d.cache.length : null, sev: "" },
					{ id: "modules", label: "Modules", badge: d.modules ? d.modules.length : null, sev: "" },
					{ id: "bifs", label: "BIFs", badge: d.bifs ? d.bifs.length : null, sev: "" },
					{ id: "request", label: "Request", badge: null, sev: "" },
					{ id: "scopes", label: "Scopes", badge: null, sev: "" },
					{ id: "jvm", label: "Runtime", badge: null, sev: "" },
					{ id: "history", label: "History", badge: this.history.length || null, sev: "" }
				];
				// The BIFs tab only exists when the bifs collector measured something
				if ( !d.bifs ) { list = list.filter( function ( t ) { return t.id !== "bifs"; } ); }
				this.customPanels.forEach( function ( p ) {
					var b = p.badge && p.badge.count >= 0 ? p.badge.count : null;
					list.push( { id: p.id, label: p.label, badge: b, sev: p.badge && p.badge.severity !== "none" ? p.badge.severity : "" } );
				} );
				// Tabs hidden in the settings never show. The layout saved in the console's bar designer orders and hides the rest
				var hidden = this.ui.hiddenTabs || [], layout = this.ui.layout || [];
				list = list.filter( function ( t ) { return hidden.indexOf( t.id ) < 0; } );
				if ( layout.length ) {
					var pos = {}, vis = {};
					layout.forEach( function ( l, i ) { pos[ l.id ] = i; vis[ l.id ] = l.visible !== false; } );
					list = list.filter( function ( t ) { return vis[ t.id ] !== false; } );
					list = list.map( function ( t, i ) { return { t: t, k: pos[ t.id ] === undefined ? 1000 + i : pos[ t.id ] }; } ).sort( function ( a, b ) { return a.k - b.k; } ).map( function ( x ) { return x.t; } );
				}
				return list;
			},
			tabIds: function () { return this.tabs.map( function ( t ) { return t.id; } ); },

			// ---- actions
			open: function ( tab ) { this.tab = tab; this.collapsed = false; this.persist(); },
			toggle: function () { this.collapsed = !this.collapsed; this.persist(); },
			toggleDetach: function () { this.detached = !this.detached; this.persist(); },
			toggleTheme: function () {
				var dark = this.$root.getAttribute( "data-theme" ) === "dark";
				this.theme = dark ? "light" : "dark";
				this.applyTheme(); this.persist();
			},
			bytesLabel: function ( n ) {
				if ( n === null || n === undefined ) { return "n/a"; }
				var u = [ "B", "KB", "MB", "GB" ], i = 0;
				while ( n >= 1024 && i < 3 ) { n /= 1024; i++; }
				return ( i ? n.toFixed( n >= 100 ? 0 : 1 ) : n ) + " " + u[ i ];
			},
			toggleMenu: function ( e ) {
				var r = e.currentTarget.getBoundingClientRect();
				this.menuStyle = "right:" + Math.max( 4, window.innerWidth - r.right ) + "px;bottom:" + ( window.innerHeight - r.top + 4 ) + "px;";
				this.menu = !this.menu;
			},
			applyTheme: function () {
				var t = this.theme;
				if ( t === "auto" ) { t = window.matchMedia && window.matchMedia( "(prefers-color-scheme: dark)" ).matches ? "dark" : "light"; }
				this.$root.setAttribute( "data-theme", t );
			},
			goIssue: function ( i ) {
				if ( i.span ) { this.sel = i.span; }
				this.tab = i.tab === "timeline" || i.span ? "timeline" : i.tab;
				if ( !this.tabIds().includes( this.tab ) ) { this.tab = this.tabIds()[ 0 ] || "timeline"; }
				this.persist();
			},
			zoom: function ( how ) {
				var span = this.v1 - this.v0, mid = ( this.v0 + this.v1 ) / 2;
				if ( how === "in" ) { span = Math.max( this.total / 50, span / 2 ); this.v0 = Math.max( 0, mid - span / 2 ); this.v1 = this.v0 + span; }
				if ( how === "out" ) { span = Math.min( this.total, span * 2 ); this.v0 = Math.max( 0, Math.min( this.total - span, mid - span / 2 ) ); this.v1 = this.v0 + span; }
				if ( how === "l" ) { var a = Math.min( span / 3, this.v0 ); this.v0 -= a; this.v1 -= a; }
				if ( how === "r" ) { var b = Math.min( span / 3, this.total - this.v1 ); this.v0 += b; this.v1 += b; }
				if ( how === "reset" ) { this.v0 = 0; this.v1 = this.total; }
			},
			startResize: function ( e ) { this._y0 = e.clientY; this._h0 = this.height; e.target.setPointerCapture( e.pointerId ); },
			doResize: function ( e ) {
				if ( this._y0 === null ) { return; }
				this.height = Math.max( 160, Math.min( window.innerHeight - 140, this._h0 + ( this._y0 - e.clientY ) ) );
			},
			endResize: function () { this._y0 = null; this.persist(); },
			persist: function () {
				try { localStorage.setItem( STORE_KEY, JSON.stringify( { h: this.height, tab: this.tab, collapsed: this.collapsed, theme: this.theme, detached: this.detached } ) ); } catch ( e ) { /* storage blocked */ }
			},
			onKey: function ( e ) {
				if ( matches( e, this.ui.hotkey || "Ctrl+`" ) ) { e.preventDefault(); this.toggle(); return; }
				var t = e.target, typing = t && /^(INPUT|TEXTAREA|SELECT)$/.test( t.tagName ) || ( t && t.isContentEditable );
				if ( typing || e.ctrlKey || e.metaKey || e.altKey || this.collapsed ) { return; }
				if ( /^[1-9]$/.test( e.key ) && this.tabs[ +e.key - 1 ] ) { this.tab = this.tabs[ +e.key - 1 ].id; this.persist(); }
				if ( e.key === "/" ) {
					e.preventDefault(); this.tab = "timeline";
					var self = this;
					setTimeout( function () { var el = document.getElementById( "bxlens-q" ); if ( el ) { el.focus(); } }, 30 );
					self.persist();
				}
			},
			copy: function ( text, label ) {
				var self = this;
				var done = function ( ok ) { self.toast( ok ? label + " copied" : "Copy blocked. Select the text instead." ); };
				try { navigator.clipboard.writeText( text ).then( function () { done( true ); }, function () { done( false ); } ); } catch ( e ) { done( false ); }
			},
			toast: function ( t ) {
				var self = this;
				this.toastText = t; clearTimeout( this._toastTimer );
				this._toastTimer = setTimeout( function () { self.toastText = ""; }, 1600 );
			},

			// ---- helpers used by the template
			color: function ( type ) { return "var(--t-" + ( type || "custom" ) + ")"; },
			typeLabel: function ( type ) { var t = TYPES.filter( function ( x ) { return x.id === type; } )[ 0 ]; return t ? t.label : type; },
			segStyle: function ( s ) {
				var span = this.v1 - this.v0;
				var l = Math.max( 0, ( s.start - this.v0 ) / span * 100 ), r = Math.min( 100, ( s.start + s.dur - this.v0 ) / span * 100 );
				return "left:" + l.toFixed( 2 ) + "%;width:" + Math.max( 0.4, r - l ).toFixed( 2 ) + "%;background:" + this.color( s.type );
			},
			fmt: function ( ms ) {
				if ( ms === undefined || ms === null ) { return ""; }
				return ms >= 1000 ? ( ms / 1000 ).toFixed( 2 ) + " s" : ( ms >= 100 ? Math.round( ms ) : Math.round( ms * 10 ) / 10 ) + " ms";
			},
			bytes: function ( n ) { return n >= 1048576 ? ( n / 1048576 ).toFixed( 1 ) + " MB" : ( n >= 1024 ? ( n / 1024 ).toFixed( 1 ) + " KB" : n + " B" ); },
			uptime: function ( ms ) {
				var s = Math.floor( ms / 1000 ), h = Math.floor( s / 3600 ), m = Math.floor( s % 3600 / 60 );
				return h ? h + "h " + m + "m" : m + "m " + ( s % 60 ) + "s";
			},
			provides: function ( m ) {
				var out = [];
				if ( m.bifs ) { out.push( m.bifs + " BIFs" ); }
				if ( m.components ) { out.push( m.components + " components" ); }
				if ( m.interceptors ) { out.push( m.interceptors + ( m.interceptors === 1 ? " interceptor" : " interceptors" ) ); }
				return out.length ? out.join( " \u00b7 " ) : "\u2014";
			},
			sum: function ( list, key ) { return list.reduce( function ( a, x ) { return a + ( x[ key ] || 0 ); }, 0 ); },
			shortPath: function ( p ) {
				if ( !p ) { return ""; }
				if ( this.base && p.indexOf( this.base ) === 0 ) { return p.slice( this.base.length ).replace( /^[\\/]+/, "" ) || p; }
				var parts = p.split( /[\\/]/ );
				return parts.slice( -3 ).join( "/" );
			},
			editorUrl: function ( file, line ) {
				var pattern = this.ui.editorLink;
				if ( !pattern || !file ) { return "#"; }
				var f = file;
				if ( this.ui.remoteBase && f.indexOf( this.ui.remoteBase ) === 0 ) { f = this.ui.localBase + f.slice( this.ui.remoteBase.length ); }
				f = f.replace( /\\/g, "/" );
				return pattern.replace( "{path}", f ).replace( "{line}", line || 1 );
			},
			sqlWithParams: function ( q ) { return q.sql + ( q.params && q.params.length ? "\n-- params " + JSON.stringify( q.params ) : "" ); },
			cellsOf: function ( row, columns ) {
				if ( Array.isArray( row ) ) { return row; }
				if ( row && typeof row === "object" ) { return ( columns && columns.length ? columns : Object.keys( row ) ).map( function ( c ) { return row[ c ]; } ); }
				return [ row ];
			},
			cellText: function ( v ) { return v !== null && typeof v === "object" ? JSON.stringify( v ) : ( v === undefined || v === null ? "" : String( v ) ); },
			flatTree: function ( nodes ) {
				var out = [];
				( function walk( list, depth ) {
					( list || [] ).forEach( function ( n ) { out.push( { label: n.label, ms: n.ms, depth: depth } ); walk( n.children, depth + 1 ); } );
				} )( nodes, 0 );
				return out;
			}
		};
	}

	function load() {
		try { return JSON.parse( localStorage.getItem( STORE_KEY ) || "{}" ) || {}; } catch ( e ) { return {}; }
	}

	// Longest shared folder prefix of every source path, so labels can drop it
	function commonBase( d ) {
		var paths = [];
		d.spans.forEach( function ( s ) { if ( s.type === "template" && s.file ) { paths.push( s.file ); } } );
		if ( paths.length < 2 ) { return ""; }
		var prefix = paths[ 0 ];
		paths.forEach( function ( p ) { while ( p.indexOf( prefix ) !== 0 && prefix ) { prefix = prefix.slice( 0, -1 ); } } );
		var cut = Math.max( prefix.lastIndexOf( "/" ), prefix.lastIndexOf( "\\" ) );
		return cut > 0 ? prefix.slice( 0, cut + 1 ) : "";
	}

	function matches( e, combo ) {
		var parts = combo.split( "+" ), key = parts.pop();
		var want = { ctrl: false, alt: false, shift: false, meta: false };
		parts.forEach( function ( p ) { want[ p.toLowerCase() ] = true; } );
		return e.ctrlKey === want.ctrl && e.altKey === want.alt && e.shiftKey === want.shift && e.metaKey === want.meta && e.key.toLowerCase() === key.toLowerCase();
	}

	function register( Alpine ) {
		Alpine.data( "bxLens", component );
	}

	// Works whether Lens brings its own Alpine or the host page already runs one
	if ( window.Alpine && window.Alpine.version ) {
		register( window.Alpine );
		var root = document.getElementById( "bxlens" );
		if ( root && !root._x_dataStack ) { window.Alpine.initTree( root ); }
	} else {
		document.addEventListener( "alpine:init", function () { register( window.Alpine ); } );
	}
}() );
