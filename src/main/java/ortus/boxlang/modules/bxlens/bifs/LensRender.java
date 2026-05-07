/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package ortus.boxlang.modules.bxlens.bifs;

import java.lang.management.ManagementFactory;

import ortus.boxlang.modules.bxlens.LensRequestData;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * LensRender() — Render the bx-lens debug bar HTML fragment.
 */
@BoxBIF
public class LensRender extends BaseLensBIF {

	private static final Key KEY_ALPINE_SOURCE = Key.of( "alpineSource" );

	public LensRender() {
		super();
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequestData data = getLensData( context );
		if ( !isEnabled( data ) )
			return "";

		long		now			= System.currentTimeMillis();
		long		totalTime	= ( data.endedAt > 0 ? data.endedAt : now ) - data.startedAt;

		IStruct		settings	= getModuleSettings();
		LensService	svc			= getLensService();

		// JVM snapshot
		IStruct		jvmData;
		try {
			jvmData = Struct.of(
			    "memory", svc != null ? svc.getMemoryInfo() : Struct.of(),
			    "threadDump", svc != null ? svc.getThreadDump() : "unavailable",
			    "threadCount", ManagementFactory.getThreadMXBean().getThreadCount()
			);
		} catch ( Exception e ) {
			jvmData = Struct.of(
			    "memory", Struct.of(),
			    "threadDump", "unavailable",
			    "threadCount", 0
			);
		}

		// BoxLang server info - best-effort
		IStruct bxData = Struct.of();
		try {
			IStruct serverScope = ( IStruct ) context.getScopeNearby( Key.of( "server" ) );
			if ( serverScope != null ) {
				IStruct	bxInfo		= serverScope.getAsStruct( Key.of( "boxlang" ) );
				IStruct	javaInfo	= serverScope.getAsStruct( Key.of( "java" ) );
				IStruct	osInfo		= serverScope.getAsStruct( Key.of( "os" ) );
				bxData = Struct.of(
				    "version", bxInfo != null ? bxInfo.getOrDefault( Key.of( "version" ), "" ) : "",
				    "javaVersion", javaInfo != null ? javaInfo.getOrDefault( Key.of( "version" ), "" ) : "",
				    "osName", osInfo != null ? osInfo.getOrDefault( Key.of( "name" ), "" ) : "",
				    "osArch", osInfo != null ? osInfo.getOrDefault( Key.of( "arch" ), "" ) : "",
				    "modules",
				    bxInfo != null ? bxInfo.getOrDefault( Key.of( "modules" ), new ortus.boxlang.runtime.types.Array() )
				        : new ortus.boxlang.runtime.types.Array(),
				    "extensions",
				    bxInfo != null ? bxInfo.getOrDefault( Key.of( "extensions" ), new ortus.boxlang.runtime.types.Array() )
				        : new ortus.boxlang.runtime.types.Array()
				);
			}
		} catch ( Exception ignored ) {
		}

		// Server-side settings surfaced to the client
		IStruct	serverSettings		= Struct.of(
		    "enabled", settings.getOrDefault( Key.of( "enabled" ), Boolean.TRUE ),
		    "maxQueries", settings.getOrDefault( Key.of( "maxQueries" ), 100 ),
		    "maxExceptions", settings.getOrDefault( Key.of( "maxExceptions" ), 50 ),
		    "maxTemplates", settings.getOrDefault( Key.of( "maxTemplates" ), 200 ),
		    "maxMessages", settings.getOrDefault( Key.of( "maxMessages" ), 200 ),
		    "maxTimings", settings.getOrDefault( Key.of( "maxTimings" ), 200 ),
		    "theme", settings.getOrDefault( Key.of( "theme" ), "dark" ),
		    "editorLinkPattern", settings.getOrDefault( Key.of( "editorLinkPattern" ), "" )
		);

		String	defaultTheme		= ( String ) settings.getOrDefault( Key.of( "theme" ), "dark" );
		String	alpineSource		= ( String ) settings.getOrDefault( KEY_ALPINE_SOURCE, "" );

		// Convert the POJO to an IStruct for JSON serialization
		IStruct	lensDataStruct		= data.toStruct();

		String	jsonData			= ej( toJson( context, lensDataStruct ) );
		String	jsonJvm				= ej( toJson( context, jvmData ) );
		String	jsonBx				= ej( toJson( context, bxData ) );
		String	jsonServerSettings	= ej( toJson( context, serverSettings ) );

		return buildHtml( jsonData, jsonJvm, jsonBx, jsonServerSettings, totalTime, defaultTheme, alpineSource );
	}

	// -------------------------------------------------------------------------
	// Private — JSON serialization
	// -------------------------------------------------------------------------

	private String toJson( IBoxContext context, Object value ) {
		return ( String ) context.invokeFunction( Key.of( "jsonSerialize" ), new Object[] { value } );
	}

	// -------------------------------------------------------------------------
	// Private — escape JSON for safe JS literal embedding
	// -------------------------------------------------------------------------

	private static String ej( String json ) {
		return json
		    .replace( "&", "&amp;" )
		    .replace( "<", "&lt;" )
		    .replace( ">", "&gt;" )
		    .replace( "'", "&#39;" );
	}

	// -------------------------------------------------------------------------
	// Private — HTML assembly
	// -------------------------------------------------------------------------

	private String buildHtml( String jsonData, String jsonJvm, String jsonBx, String jsonServerSettings,
	    long totalTime, String defaultTheme, String alpineSource ) {
		StringBuilder sb = new StringBuilder();

		sb.append( "<!-- bx-lens -->" );
		sb.append( "<style id=\"bxlens-styles\">" );
		sb.append( CSS );
		sb.append( "</style>" );

		sb.append( "<script id=\"bxlens-script\">" );
		if ( alpineSource != null && !alpineSource.isEmpty() ) {
			sb.append( alpineSource );
		}
		sb.append( buildJs( jsonData, jsonJvm, jsonBx, jsonServerSettings, totalTime, defaultTheme ) );
		sb.append( "</script>" );

		sb.append( HTML );

		return sb.toString();
	}

	private String buildJs( String jsonData, String jsonJvm, String jsonBx, String jsonServerSettings,
	    long totalTime, String defaultTheme ) {
		return JS_PART1
		    + defaultTheme + JS_PART2
		    + totalTime + JS_PART3
		    + jsonData + JS_PART4
		    + jsonJvm + JS_PART5
		    + jsonBx + JS_PART6
		    + jsonServerSettings + JS_PART7;
	}

	// -------------------------------------------------------------------------
	// Static content constants
	// -------------------------------------------------------------------------

	private static final String	CSS			= """

	                                          #bxlens-root {
	                                            all:initial;display:block;
	                                            position:fixed;bottom:0;left:0;width:100%;
	                                            z-index:2147483647;
	                                            font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;
	                                            font-size:13px;line-height:1.4;
	                                            --bxl-bg:#1a1a2e;--bxl-bg-panel:#16213e;--bxl-bg-tab:#0f3460;
	                                            --bxl-accent:#e94560;--bxl-accent2:#c73652;
	                                            --bxl-text:#eaeaea;--bxl-text-muted:#9ca3af;
	                                            --bxl-border:#2d3748;
	                                            --bxl-warn:#f59e0b;--bxl-err:#ef4444;--bxl-ok:#10b981;
	                                            --bxl-http:#38b2ac;--bxl-soap:#9f7aea;
	                                            --bxl-bar-height:40px;--bxl-panel-height:360px;
	                                            --bxl-font-mono:"Consolas","Monaco","Courier New",monospace;
	                                          }
	                                          #bxlens-root[data-bxl-theme="light"] {
	                                            --bxl-bg:#f1f5f9;--bxl-bg-panel:#ffffff;--bxl-bg-tab:#e2e8f0;
	                                            --bxl-text:#0f172a;--bxl-text-muted:#64748b;--bxl-border:#cbd5e1;
	                                            --bxl-accent:#e94560;--bxl-accent2:#c73652;
	                                          }
	                                          #bxlens-root *{box-sizing:border-box;margin:0;padding:0;}
	                                          .bxlens-bar{
	                                            display:flex;align-items:center;height:var(--bxl-bar-height);
	                                            background:var(--bxl-bg);color:var(--bxl-text);
	                                            padding:0 10px;gap:8px;cursor:pointer;
	                                            border-top:2px solid var(--bxl-accent);user-select:none;
	                                          }
	                                          .bxlens-logo{display:flex;align-items:center;gap:5px;color:var(--bxl-accent);font-weight:700;font-size:12px;flex-shrink:0;}
	                                          .bxlens-method{padding:2px 6px;border-radius:3px;background:var(--bxl-accent);color:#fff;font-size:11px;font-weight:700;flex-shrink:0;}
	                                          .bxlens-url{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--bxl-text-muted);font-size:12px;}
	                                          .bxlens-chip{
	                                            display:flex;align-items:center;gap:3px;padding:2px 7px;
	                                            border-radius:4px;background:var(--bxl-bg-tab);
	                                            font-size:11px;cursor:pointer;flex-shrink:0;color:var(--bxl-text);
	                                            transition:background .15s;
	                                          }
	                                          .bxlens-chip:hover{background:var(--bxl-border);}
	                                          .bxlens-chip.warn{color:var(--bxl-warn);}
	                                          .bxlens-chip.err{color:var(--bxl-err);}
	                                          .bxlens-chip.http{color:var(--bxl-http);}
	                                          .bxlens-chevron{margin-left:auto;transition:transform .2s;flex-shrink:0;color:var(--bxl-text-muted);}
	                                          .bxlens-chevron.open{transform:rotate(180deg);}
	                                          .bxlens-panel{
	                                            background:var(--bxl-bg-panel);
	                                            height:var(--bxl-panel-height);
	                                            display:flex;flex-direction:column;
	                                            border-top:1px solid var(--bxl-border);overflow:hidden;
	                                          }
	                                          .bxlens-tabs{
	                                            display:flex;background:var(--bxl-bg);
	                                            border-bottom:1px solid var(--bxl-border);overflow-x:auto;flex-shrink:0;
	                                            scrollbar-width:none;
	                                          }
	                                          .bxlens-tabs::-webkit-scrollbar{display:none;}
	                                          .bxlens-tab{
	                                            display:flex;align-items:center;gap:4px;
	                                            padding:7px 12px;border:none;background:transparent;
	                                            color:var(--bxl-text-muted);cursor:pointer;
	                                            font-size:12px;font-family:inherit;white-space:nowrap;
	                                            border-bottom:2px solid transparent;transition:color .15s;
	                                          }
	                                          .bxlens-tab:hover{color:var(--bxl-text);background:var(--bxl-bg-tab);}
	                                          .bxlens-tab.active{color:#fff;border-bottom-color:var(--bxl-accent);}
	                                          .bxlens-badge{
	                                            padding:1px 5px;border-radius:8px;background:var(--bxl-accent);
	                                            color:#fff;font-size:10px;font-weight:700;min-width:18px;text-align:center;
	                                          }
	                                          .bxlens-badge.warn{background:var(--bxl-warn);}
	                                          .bxlens-badge.ok{background:var(--bxl-ok);}
	                                          .bxlens-content{flex:1;overflow-y:auto;padding:10px 14px;color:var(--bxl-text);}
	                                          .bxlens-empty{color:var(--bxl-text-muted);text-align:center;padding:32px 0;font-size:13px;}
	                                          .bxlens-kv{width:100%;border-collapse:collapse;font-size:12px;}
	                                          .bxlens-kv th{color:var(--bxl-text-muted);font-weight:500;width:180px;padding:4px 8px 4px 0;vertical-align:top;text-align:left;}
	                                          .bxlens-kv td{padding:4px 0;word-break:break-all;}
	                                          .bxlens-kv tr{border-bottom:1px solid var(--bxl-border);}
	                                          .bxlens-section{margin-bottom:14px;}
	                                          .bxlens-section-title{font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:.05em;color:var(--bxl-text-muted);margin-bottom:6px;}
	                                          .bxlens-query{margin-bottom:10px;border:1px solid var(--bxl-border);border-radius:4px;overflow:hidden;}
	                                          .bxlens-query-head{display:flex;align-items:center;gap:8px;padding:5px 8px;background:var(--bxl-bg);font-size:11px;}
	                                          .bxlens-query-copy{margin-left:auto;cursor:pointer;opacity:.6;background:none;border:none;color:var(--bxl-text);font-size:11px;}
	                                          .bxlens-query-copy:hover{opacity:1;}
	                                          .bxlens-sql{
	                                            font-family:var(--bxl-font-mono);font-size:11px;
	                                            padding:8px;background:var(--bxl-bg-panel);
	                                            color:var(--bxl-text);white-space:pre-wrap;word-break:break-all;
	                                          }
	                                          .bxlens-badge-time{padding:1px 6px;border-radius:3px;font-size:11px;font-weight:600;}
	                                          .bxlens-badge-time.slow{background:var(--bxl-err);color:#fff;}
	                                          .bxlens-badge-time.med{background:var(--bxl-warn);color:#000;}
	                                          .bxlens-badge-time.fast{background:var(--bxl-bg-tab);color:var(--bxl-text);}
	                                          .bxlens-ex{border-left:3px solid var(--bxl-err);padding:8px 10px;margin-bottom:8px;background:rgba(239,68,68,.07);border-radius:0 4px 4px 0;}
	                                          .bxlens-ex-type{font-weight:700;color:var(--bxl-err);font-size:12px;}
	                                          .bxlens-ex-msg{color:var(--bxl-text);margin:3px 0;font-size:12px;}
	                                          .bxlens-ex-trace{font-family:var(--bxl-font-mono);font-size:10px;color:var(--bxl-text-muted);white-space:pre-wrap;max-height:200px;overflow-y:auto;margin-top:4px;}
	                                          .bxlens-ex-link{color:var(--bxl-accent);text-decoration:none;border-bottom:1px dashed var(--bxl-accent);}
	                                          .bxlens-tl{padding:6px 0;}
	                                          .bxlens-tl-ref{display:grid;grid-template-columns:180px 1fr 60px;gap:6px;align-items:center;margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid var(--bxl-border);}
	                                          .bxlens-tl-row{display:grid;grid-template-columns:180px 1fr 60px;gap:6px;align-items:center;margin-bottom:3px;}
	                                          .bxlens-tl-label{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;color:var(--bxl-text-muted);}
	                                          .bxlens-tl-wrap{background:var(--bxl-bg);border-radius:2px;height:10px;overflow:hidden;position:relative;}
	                                          .bxlens-tl-bar{height:100%;border-radius:2px;position:absolute;}
	                                          .bxlens-tl-time{text-align:right;font-size:10px;color:var(--bxl-text-muted);}
	                                          .bxlens-tl-filters{display:flex;gap:6px;margin-bottom:8px;flex-wrap:wrap;}
	                                          .bxlens-tl-filter{padding:2px 8px;border-radius:10px;font-size:11px;cursor:pointer;border:1px solid;transition:opacity .15s;}
	                                          .bxlens-tl-filter.off{opacity:.35;}
	                                          .bxlens-msg{display:flex;gap:8px;padding:5px 0;border-bottom:1px solid var(--bxl-border);font-size:12px;}
	                                          .bxlens-msg-icon{font-size:14px;flex-shrink:0;}
	                                          .bxlens-msg-text{flex:1;}
	                                          .bxlens-msg-offset{color:var(--bxl-text-muted);font-size:10px;flex-shrink:0;padding-top:1px;}
	                                          .bxlens-http{border:1px solid var(--bxl-border);border-radius:4px;margin-bottom:6px;overflow:hidden;font-size:12px;}
	                                          .bxlens-http-row{display:grid;grid-template-columns:50px 1fr 60px 70px 60px;gap:8px;padding:6px 8px;align-items:center;background:var(--bxl-bg);}
	                                          .bxlens-status-ok{color:var(--bxl-ok);}
	                                          .bxlens-status-warn{color:var(--bxl-warn);}
	                                          .bxlens-status-err{color:var(--bxl-err);}
	                                          .bxlens-mem-row{display:grid;grid-template-columns:90px 1fr 80px;gap:8px;align-items:center;margin-bottom:8px;font-size:12px;}
	                                          .bxlens-mem-bar{background:var(--bxl-bg);border-radius:3px;height:12px;overflow:hidden;}
	                                          .bxlens-mem-fill{height:100%;background:var(--bxl-accent);border-radius:3px;transition:width .5s;}
	                                          .bxlens-code{font-family:var(--bxl-font-mono);font-size:11px;padding:2px 5px;background:var(--bxl-bg);border-radius:3px;}
	                                          .bxlens-pre{font-family:var(--bxl-font-mono);font-size:11px;white-space:pre-wrap;word-break:break-all;color:var(--bxl-text);}
	                                          .bxlens-subNav{display:flex;gap:4px;flex-wrap:wrap;margin-bottom:8px;}
	                                          .bxlens-subBtn{padding:3px 10px;border-radius:3px;border:1px solid var(--bxl-border);background:transparent;color:var(--bxl-text-muted);cursor:pointer;font-size:11px;font-family:inherit;}
	                                          .bxlens-subBtn.active{background:var(--bxl-accent);color:#fff;border-color:var(--bxl-accent);}
	                                          .bxlens-btn{padding:5px 12px;border-radius:4px;border:1px solid var(--bxl-border);background:var(--bxl-bg-tab);color:var(--bxl-text);cursor:pointer;font-size:12px;font-family:inherit;}
	                                          .bxlens-btn:hover{background:var(--bxl-border);}
	                                          .bxlens-settings-row{display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid var(--bxl-border);font-size:13px;}
	                                          .bxlens-settings-row label{cursor:pointer;}
	                                          .bxlens-toggle-group{display:flex;gap:4px;}
	                                          .bxlens-toggle{padding:3px 10px;border-radius:3px;border:1px solid var(--bxl-border);background:transparent;color:var(--bxl-text-muted);cursor:pointer;font-size:11px;font-family:inherit;}
	                                          .bxlens-toggle.active{background:var(--bxl-accent);color:#fff;border-color:var(--bxl-accent);}
	                                          .bxlens-qviews{display:flex;gap:4px;margin-bottom:8px;}
	                                          .bxlens-view-btn{padding:2px 8px;border-radius:3px;border:1px solid var(--bxl-border);background:transparent;color:var(--bxl-text-muted);cursor:pointer;font-size:11px;font-family:inherit;}
	                                          .bxlens-view-btn.active{background:var(--bxl-bg-tab);color:var(--bxl-text);}
	                                          .bxlens-scope-note{font-size:11px;color:var(--bxl-text-muted);font-style:italic;padding:8px 0;}
	                                          .bxlens-bx-modules{font-size:11px;column-count:2;gap:10px;}
	                                          .bxlens-bx-module{padding:2px 0;color:var(--bxl-text-muted);}
	                                          """;

	// JS is split at injection points: theme, totalTime, data, jvm, bx, ss
	private static final String	JS_PART1	= """

	                                          (function(){
	                                          if(window.__bxLensInit__)return;
	                                          window.__bxLensInit__=true;

	                                          var _LS={
	                                            get:function(k,d){try{var v=localStorage.getItem("bxlens_"+k);return v!==null?JSON.parse(v):d;}catch(e){return d;}},
	                                            set:function(k,v){try{localStorage.setItem("bxlens_"+k,JSON.stringify(v));}catch(e){}}
	                                          };

	                                          function bxLensBar(){
	                                            return {
	                                              open:false,
	                                              activeTab:"request",
	                                              varScope:"form",
	                                              qView:"list",
	                                              tView:"bars",
	                                              showThreadDump:false,
	                                              hideEmptyTabs:_LS.get("hideEmptyTabs",false),
	                                              panelHeight:_LS.get("panelHeight",360),
	                                              theme:_LS.get("theme",\"""";

	private static final String	JS_PART2	= """
	                                          "),
	                                              editorMode:_LS.get("editorMode","vscode"),
	                                              tlFilter:{T:true,Q:true,H:true,S:true,C:true},
	                                              totalTime:\"""";

	private static final String	JS_PART3	= """
	                                          ,
	                                              data:\"""";

	private static final String	JS_PART4	= """
	                                          ,
	                                              jvm:\"""";

	private static final String	JS_PART5	= """
	                                          ,
	                                              bx:\"""";

	private static final String	JS_PART6	= """
	                                          ,
	                                              ss:\"""";

	private static final String	JS_PART7	= """
	                                          ,

	                                              init:function(){
	                                                var r=this.$el;
	                                                r.style.setProperty("--bxl-panel-height",this.panelHeight+"px");
	                                                this._applyTheme(this.theme);
	                                                var self=this;
	                                                if(window.matchMedia){
	                                                  window.matchMedia("(prefers-color-scheme:dark)").addEventListener("change",function(){
	                                                    if(self.theme==="auto")self._applyTheme("auto");
	                                                  });
	                                                }
	                                              },

	                                              toggle:function(){this.open=!this.open;},
	                                              setTab:function(t){this.activeTab=t;this.open=true;},
	                                              openSettings:function(){this.activeTab="settings";this.open=true;},

	                                              _applyTheme:function(t){
	                                                var r=this.$el;
	                                                this.theme=t;
	                                                _LS.set("theme",t);
	                                                var applied=t;
	                                                if(t==="auto"){
	                                                  applied=window.matchMedia&&window.matchMedia("(prefers-color-scheme:dark)").matches?"dark":"light";
	                                                }
	                                                r.setAttribute("data-bxl-theme",applied);
	                                              },
	                                              setTheme:function(t){this._applyTheme(t);},

	                                              setPanelHeight:function(h){
	                                                this.panelHeight=h;_LS.set("panelHeight",h);
	                                                this.$el.style.setProperty("--bxl-panel-height",h+"px");
	                                              },

	                                              toggleHideEmpty:function(){
	                                                this.hideEmptyTabs=!this.hideEmptyTabs;_LS.set("hideEmptyTabs",this.hideEmptyTabs);
	                                                if(this.hideEmptyTabs&&!this.visibleTabs().includes(this.activeTab)){this.activeTab="request";}
	                                              },

	                                              setEditorMode:function(m){this.editorMode=m;_LS.set("editorMode",m);},

	                                              resetSettings:function(){
	                                                this.hideEmptyTabs=false;this.panelHeight=360;this.theme=this.ss.theme||"dark";
	                                                _LS.set("hideEmptyTabs",false);_LS.set("panelHeight",360);_LS.set("theme",this.ss.theme||"dark");
	                                                this.$el.style.setProperty("--bxl-panel-height","360px");
	                                                this._applyTheme(this.ss.theme||"dark");
	                                              },

	                                              counts:function(){
	                                                var d=this.data;
	                                                return {
	                                                  request:null,exceptions:d.exceptions.length,queries:d.queries.length,
	                                                  http:d.httpCalls.length,soap:d.soapCalls.length,
	                                                  messages:d.messages.length,timings:d.timings.length,
	                                                  timeline:d.templates.length+d.queries.length+d.httpCalls.length+(d.timings||[]).length,
	                                                  templates:d.templates.length,variables:null,boxlang:null,jvm:null,settings:null
	                                                };
	                                              },

	                                              visibleTabs:function(){
	                                                var all=["request","exceptions","queries","http","soap","messages","timings","timeline","templates","variables","boxlang","jvm","settings"];
	                                                if(!this.hideEmptyTabs)return all;
	                                                var c=this.counts();
	                                                return all.filter(function(t){return c[t]===null||c[t]>0;});
	                                              },

	                                              editorLink:function(path,line){
	                                                if(this.editorMode==="off"||!path)return null;
	                                                var pat=this.editorMode==="phpstorm"
	                                                  ?"phpstorm://open?file={path}&line={line}"
	                                                  :"vscode://file/{path}:{line}";
	                                                return pat.replace("{path}",path).replace("{line}",line||1);
	                                              },

	                                              fmtMs:function(ms){return ms>=1000?(ms/1000).toFixed(2)+"s":ms+"ms";},
	                                              fmtBytes:function(b){if(!b)return"0 B";var k=1024,s=["B","KB","MB","GB"],i=Math.floor(Math.log(b)/Math.log(k));return(b/Math.pow(k,i)).toFixed(1)+" "+s[i];},
	                                              pct:function(part,tot){return tot>0?Math.min(100,(part/tot)*100).toFixed(1):0;},
	                                              timeClass:function(ms){return ms>=1000?"slow":ms>=100?"med":"fast";},
	                                              statusClass:function(s){return s>=500?"err":s>=400?"warn":"ok";},

	                                              msgIcon:function(t){
	                                                var icons={info:"ℹ",warn:"⚠",error:"✕",debug:"◉"};
	                                                return icons[t]||"·";
	                                              },
	                                              msgColor:function(t){
	                                                return t==="error"?"color:var(--bxl-err)":t==="warn"?"color:var(--bxl-warn)":t==="debug"?"color:var(--bxl-text-muted)":"color:var(--bxl-http)";
	                                              },

	                                              sqlHash:function(sql){
	                                                return sql.replace(/\\x27[^\\x27]*\\x27/g,"?").replace(/\\b\\d+\\b/g,"?").replace(/\\s+/g," ").trim().toLowerCase();
	                                              },
	                                              groupedQueries:function(){
	                                                var g={};var qs=this.data.queries;
	                                                for(var i=0;i<qs.length;i++){
	                                                  var q=qs[i],h=this.sqlHash(q.sql);
	                                                  if(!g[h])g[h]={sql:q.sql,hash:h,count:0,totalTime:0,items:[]};
	                                                  g[h].count++;g[h].totalTime+=q.executionTime;g[h].items.push(q);
	                                                }
	                                                return Object.values(g);
	                                              },
	                                              sortedQueries:function(){
	                                                return this.data.queries.slice().sort(function(a,b){return b.executionTime-a.executionTime;});
	                                              },

	                                              tlEvents:function(){
	                                                var ev=[],d=this.data,f=this.tlFilter;
	                                                if(f.T)d.templates.forEach(function(t){ev.push({label:t.templatePath.split("/").pop()||t.templatePath,fullLabel:t.templatePath,dur:t.executionTime,off:t.offset,depth:t.depth||0,type:"T",color:"var(--bxl-accent)"});});
	                                                if(f.Q)d.queries.forEach(function(q){ev.push({label:q.sql.substring(0,60),fullLabel:q.sql,dur:q.executionTime,off:q.offset,depth:0,type:"Q",color:"#f6ad55"});});
	                                                if(f.H)(d.httpCalls||[]).forEach(function(h){ev.push({label:h.method+" "+h.url.substring(0,50),fullLabel:h.url,dur:h.executionTime,off:h.offset,depth:0,type:"H",color:"var(--bxl-http)"});});
	                                                if(f.S)(d.soapCalls||[]).forEach(function(s){ev.push({label:s.action||s.endpoint,fullLabel:s.endpoint,dur:s.executionTime,off:s.offset,depth:0,type:"S",color:"var(--bxl-soap)"});});
	                                                if(f.C)(d.timings||[]).forEach(function(c){ev.push({label:c.label,fullLabel:c.label,dur:c.executionTime,off:c.offset,depth:0,type:"C",color:"var(--bxl-ok)"});});
	                                                ev.sort(function(a,b){return a.off-b.off;});
	                                                return ev;
	                                              },

	                                              memPct:function(used,max){return max>0?Math.round((used/max)*100):0;},

	                                              copySQL:function(sql){
	                                                if(navigator.clipboard){navigator.clipboard.writeText(sql).catch(function(){});}
	                                              },

	                                              scopeEnabled:function(name){
	                                                return this.ss.scopes&&this.ss.scopes[name]!==false;
	                                              }
	                                            };
	                                          }

	                                          if(window.Alpine&&parseInt(window.Alpine.version)>=3){
	                                            window.Alpine.data("bxLensBar",bxLensBar);
	                                          }else{
	                                            window.Alpine&&window.Alpine.data?window.Alpine.data("bxLensBar",bxLensBar):0;
	                                            document.addEventListener("DOMContentLoaded",function(){
	                                              if(window.Alpine&&window.Alpine.start)Alpine.data("bxLensBar",bxLensBar),Alpine.start();
	                                            });
	                                          }
	                                          })();
	                                          """;

	private static final String	HTML		= """

	                                          <div id="bxlens-root" x-data="bxLensBar()" x-cloak>

	                                          <!-- SUMMARY BAR -->
	                                          <div class="bxlens-bar" @click="toggle()">
	                                            <span class="bxlens-logo">
	                                              <svg width="18" height="18" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
	                                                <path d="M60 5 L20 55 L45 55 L40 95 L80 45 L55 45 Z" fill="#e94560"/>
	                                              </svg>
	                                              BoxLang
	                                            </span>
	                                            <span class="bxlens-method" x-text="data.method||'GET'"></span>
	                                            <span class="bxlens-url" x-text="(data.url||'').substring(0,90)" :title="data.url"></span>

	                                            <!-- timing -->
	                                            <span class="bxlens-chip" @click.stop="setTab('timeline')">
	                                              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"/></svg>
	                                              <span x-text="fmtMs(totalTime)"></span>
	                                            </span>

	                                            <!-- queries -->
	                                            <span class="bxlens-chip" :class="{warn:data.queries.length>10}" @click.stop="setTab('queries')">
	                                              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14c0 1.66 4.03 3 9 3s9-1.34 9-3V5"/><path d="M3 12c0 1.66 4.03 3 9 3s9-1.34 9-3"/></svg>
	                                              <span x-text="data.queries.length+' Q'"></span>
	                                            </span>

	                                            <!-- exceptions -->
	                                            <span class="bxlens-chip" :class="{err:data.exceptions.length>0}" @click.stop="setTab('exceptions')">
	                                              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z"/></svg>
	                                              <span x-text="data.exceptions.length+' Ex'"></span>
	                                            </span>

	                                            <!-- HTTP -->
	                                            <span class="bxlens-chip http" @click.stop="setTab('http')" x-show="data.httpCalls.length>0">
	                                              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z"/><path d="M3.6 9h16.8M3.6 15h16.8M11.5 3a17 17 0 0 0 0 18M12.5 3a17 17 0 0 1 0 18"/></svg>
	                                              <span x-text="data.httpCalls.length+' H'"></span>
	                                            </span>

	                                            <!-- messages -->
	                                            <span class="bxlens-chip" @click.stop="setTab('messages')" x-show="data.messages.length>0">
	                                              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2.25 12.76c0 1.6 1.123 2.994 2.707 3.227 1.087.16 2.185.283 3.293.369V21l4.076-4.076a1.526 1.526 0 0 1 1.037-.443 48.282 48.282 0 0 0 5.68-.494c1.584-.233 2.707-1.626 2.707-3.228V6.741c0-1.602-1.123-2.995-2.707-3.228A48.394 48.394 0 0 0 12 3c-2.392 0-4.744.175-7.043.513C3.373 3.746 2.25 5.14 2.25 6.741v6.018Z"/></svg>
	                                              <span x-text="data.messages.length+' M'"></span>
	                                            </span>

	                                            <!-- settings gear -->
	                                            <span class="bxlens-chip" @click.stop="openSettings()">
	                                              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.325.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 0 1 1.37.49l1.296 2.247a1.125 1.125 0 0 1-.26 1.431l-1.003.827c-.293.241-.438.613-.43.992a7.723 7.723 0 0 1 0 .255c-.008.378.137.75.43.991l1.004.827c.424.35.534.955.26 1.43l-1.298 2.247a1.125 1.125 0 0 1-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 0 1-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.543-.56.94-1.11.94h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 0 1-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 0 1-1.369-.49l-1.297-2.247a1.125 1.125 0 0 1 .26-1.431l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 0 1 0-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 0 1-.26-1.43l1.297-2.247a1.125 1.125 0 0 1 1.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28Z"/><path d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"/></svg>
	                                            </span>

	                                            <!-- chevron -->
	                                            <span class="bxlens-chevron" :class="{open:open}">
	                                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m4.5 15.75 7.5-7.5 7.5 7.5"/></svg>
	                                            </span>
	                                          </div><!-- end bar -->

	                                          <!-- PANEL -->
	                                          <div class="bxlens-panel" x-show="open"
	                                            x-transition:enter="transition ease-out duration-150"
	                                            x-transition:enter-start="opacity-0 transform translate-y-2"
	                                            x-transition:enter-end="opacity-100 transform translate-y-0">

	                                            <!-- TAB NAV -->
	                                            <div class="bxlens-tabs">
	                                              <template x-for="tab in visibleTabs()" :key="tab">
	                                                <button class="bxlens-tab" :class="{active:activeTab===tab}"
	                                                        @click="setTab(tab)">
	                                                  <span x-text="tab.charAt(0).toUpperCase()+tab.slice(1)"></span>
	                                                  <template x-if="counts()[tab]!==null&&counts()[tab]>0">
	                                                    <span class="bxlens-badge"
	                                                          :class="{warn:tab==='queries'&&counts()[tab]>10,ok:tab==='timings'}"
	                                                          x-text="counts()[tab]"></span>
	                                                  </template>
	                                                </button>
	                                              </template>
	                                            </div>

	                                            <!-- CONTENT -->
	                                            <div class="bxlens-content">

	                                              <!-- ===== REQUEST ===== -->
	                                              <div x-show="activeTab==='request'">
	                                                <div class="bxlens-section">
	                                                  <div class="bxlens-section-title">This Request</div>
	                                                  <table class="bxlens-kv">
	                                                    <tr><th>Request ID</th><td><span class="bxlens-code" x-text="data.requestId"></span></td></tr>
	                                                    <tr><th>Method</th><td x-text="data.method"></td></tr>
	                                                    <tr><th>URL</th><td style="word-break:break-all" x-text="data.url"></td></tr>
	                                                    <tr><th>Status</th><td x-text="data.statusCode"></td></tr>
	                                                    <tr><th>Application</th><td x-text="data.applicationName||'—'"></td></tr>
	                                                    <tr><th>Total Time</th><td x-text="fmtMs(totalTime)"></td></tr>
	                                                    <tr><th>Queries</th><td x-text="data.queries.length"></td></tr>
	                                                    <tr><th>HTTP Calls</th><td x-text="(data.httpCalls||[]).length"></td></tr>
	                                                    <tr><th>SOAP Calls</th><td x-text="(data.soapCalls||[]).length"></td></tr>
	                                                    <tr><th>Exceptions</th><td x-text="data.exceptions.length"></td></tr>
	                                                    <tr><th>Templates</th><td x-text="data.templates.length"></td></tr>
	                                                    <tr><th>Messages</th><td x-text="(data.messages||[]).length"></td></tr>
	                                                    <tr><th>Custom Timings</th><td x-text="(data.timings||[]).length"></td></tr>
	                                                  </table>
	                                                </div>
	                                                <div class="bxlens-section">
	                                                  <div class="bxlens-section-title">Global Stats</div>
	                                                  <table class="bxlens-kv">
	                                                    <tr><th>Total Requests</th><td x-text="data.globalStats.totalRequests"></td></tr>
	                                                    <tr><th>Total Queries</th><td x-text="data.globalStats.totalQueries"></td></tr>
	                                                    <tr><th>Total Exceptions</th><td x-text="data.globalStats.totalExceptions"></td></tr>
	                                                    <tr><th>Total HTTP Calls</th><td x-text="data.globalStats.totalHttpCalls"></td></tr>
	                                                    <tr><th>Active Sessions</th><td x-text="data.globalStats.activeSessions"></td></tr>
	                                                    <tr><th>Uptime</th><td x-text="fmtMs(data.globalStats.uptimeMs||0)"></td></tr>
	                                                    <tr><th>Avg Request Time</th><td x-text="fmtMs(data.globalStats.avgRequestTimeMs||0)"></td></tr>
	                                                    <tr><th>Slowest Request</th><td x-text="fmtMs(data.globalStats.slowestRequestMs||0)"></td></tr>
	                                                    <tr><th>Fastest Request</th><td x-text="fmtMs(data.globalStats.fastestRequestMs||0)"></td></tr>
	                                                  </table>
	                                                </div>
	                                              </div>

	                                              <!-- ===== EXCEPTIONS ===== -->
	                                              <div x-show="activeTab==='exceptions'">
	                                                <div class="bxlens-empty" x-show="data.exceptions.length===0">No exceptions — great job!</div>
	                                                <template x-for="(ex,i) in data.exceptions" :key="i">
	                                                  <div class="bxlens-ex">
	                                                    <div class="bxlens-ex-type" x-text="ex.type"></div>
	                                                    <div class="bxlens-ex-msg" x-text="ex.message"></div>
	                                                    <template x-if="ex.detail">
	                                                      <div style="font-size:11px;color:var(--bxl-text-muted);margin-top:3px;" x-text="ex.detail"></div>
	                                                    </template>
	                                                    <pre class="bxlens-ex-trace" x-text="ex.stackTrace"></pre>
	                                                  </div>
	                                                </template>
	                                              </div>

	                                              <!-- ===== QUERIES ===== -->
	                                              <div x-show="activeTab==='queries'">
	                                                <div class="bxlens-qviews">
	                                                  <button class="bxlens-view-btn" :class="{active:qView==='list'}"   @click="qView='list'">List</button>
	                                                  <button class="bxlens-view-btn" :class="{active:qView==='grouped'}" @click="qView='grouped'">Grouped</button>
	                                                  <button class="bxlens-view-btn" :class="{active:qView==='slowest'}" @click="qView='slowest'">Slowest First</button>
	                                                </div>

	                                                <div class="bxlens-empty" x-show="data.queries.length===0">No queries recorded.</div>

	                                                <!-- list view -->
	                                                <template x-if="qView==='list'">
	                                                  <div>
	                                                    <template x-for="(q,i) in data.queries" :key="i">
	                                                      <div class="bxlens-query">
	                                                        <div class="bxlens-query-head">
	                                                          <span class="bxlens-badge-time" :class="timeClass(q.executionTime)" x-text="fmtMs(q.executionTime)"></span>
	                                                          <span style="color:var(--bxl-text-muted)" x-text="q.recordCount+' rows'"></span>
	                                                          <span style="color:var(--bxl-text-muted);font-size:10px" x-text="''+q.offset+'ms'"></span>
	                                                          <button class="bxlens-query-copy" title="Copy SQL" @click="copySQL(q.sql)">⎘ copy</button>
	                                                        </div>
	                                                        <pre class="bxlens-sql" x-text="q.sql"></pre>
	                                                      </div>
	                                                    </template>
	                                                  </div>
	                                                </template>

	                                                <!-- grouped view -->
	                                                <template x-if="qView==='grouped'">
	                                                  <div>
	                                                    <template x-for="(g,i) in groupedQueries()" :key="i">
	                                                      <div class="bxlens-query">
	                                                        <div class="bxlens-query-head">
	                                                          <span class="bxlens-badge" x-text="g.count+'×'"></span>
	                                                          <span style="color:var(--bxl-text-muted)" x-text="'Total: '+fmtMs(g.totalTime)+' · Avg: '+fmtMs(Math.round(g.totalTime/g.count))"></span>
	                                                          <button class="bxlens-query-copy" @click="copySQL(g.sql)">⎘ copy</button>
	                                                        </div>
	                                                        <pre class="bxlens-sql" x-text="g.sql"></pre>
	                                                      </div>
	                                                    </template>
	                                                  </div>
	                                                </template>

	                                                <!-- slowest view -->
	                                                <template x-if="qView==='slowest'">
	                                                  <div>
	                                                    <template x-for="(q,i) in sortedQueries()" :key="i">
	                                                      <div class="bxlens-query">
	                                                        <div class="bxlens-query-head">
	                                                          <span class="bxlens-badge-time" :class="timeClass(q.executionTime)" x-text="fmtMs(q.executionTime)"></span>
	                                                          <span style="color:var(--bxl-text-muted)" x-text="q.recordCount+' rows'"></span>
	                                                          <button class="bxlens-query-copy" @click="copySQL(q.sql)">⎘ copy</button>
	                                                        </div>
	                                                        <pre class="bxlens-sql" x-text="q.sql"></pre>
	                                                      </div>
	                                                    </template>
	                                                  </div>
	                                                </template>
	                                              </div>

	                                              <!-- ===== HTTP ===== -->
	                                              <div x-show="activeTab==='http'">
	                                                <div class="bxlens-empty" x-show="(data.httpCalls||[]).length===0">No outgoing HTTP calls recorded.</div>
	                                                <template x-for="(h,i) in (data.httpCalls||[])" :key="i">
	                                                  <div class="bxlens-http">
	                                                    <div class="bxlens-http-row">
	                                                      <span style="font-weight:700;font-size:11px" x-text="h.method"></span>
	                                                      <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px" x-text="h.url" :title="h.url"></span>
	                                                      <span :class="'bxlens-status-'+statusClass(h.statusCode)" x-text="h.statusCode||'—'"></span>
	                                                      <span style="color:var(--bxl-text-muted)" x-text="fmtBytes(h.responseSize)"></span>
	                                                      <span class="bxlens-badge-time" :class="timeClass(h.executionTime)" x-text="fmtMs(h.executionTime)"></span>
	                                                    </div>
	                                                  </div>
	                                                </template>
	                                              </div>

	                                              <!-- ===== SOAP ===== -->
	                                              <div x-show="activeTab==='soap'">
	                                                <div class="bxlens-empty" x-show="(data.soapCalls||[]).length===0">No SOAP calls recorded.</div>
	                                                <template x-for="(s,i) in (data.soapCalls||[])" :key="i">
	                                                  <div class="bxlens-http">
	                                                    <div class="bxlens-http-row">
	                                                      <span style="font-weight:700;font-size:11px">SOAP</span>
	                                                      <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px" x-text="s.action||s.endpoint" :title="s.endpoint"></span>
	                                                      <span :class="'bxlens-status-'+statusClass(s.statusCode)" x-text="s.statusCode||'—'"></span>
	                                                      <span style="color:var(--bxl-text-muted)" x-text="fmtBytes(s.responseSize)"></span>
	                                                      <span class="bxlens-badge-time" :class="timeClass(s.executionTime)" x-text="fmtMs(s.executionTime)"></span>
	                                                    </div>
	                                                  </div>
	                                                </template>
	                                              </div>

	                                              <!-- ===== MESSAGES ===== -->
	                                              <div x-show="activeTab==='messages'">
	                                                <div class="bxlens-empty" x-show="(data.messages||[]).length===0">
	                                                  No messages. Use <span class="bxlens-code">LensMessage('hello')</span> to add one.
	                                                </div>
	                                                <template x-for="(m,i) in (data.messages||[])" :key="i">
	                                                  <div class="bxlens-msg">
	                                                    <span class="bxlens-msg-icon" :style="msgColor(m.type)" x-text="msgIcon(m.type)"></span>
	                                                    <span class="bxlens-msg-text" x-text="m.message"></span>
	                                                    <span class="bxlens-msg-offset" x-text="m.offset+'ms'"></span>
	                                                  </div>
	                                                </template>
	                                              </div>

	                                              <!-- ===== TIMINGS ===== -->
	                                              <div x-show="activeTab==='timings'">
	                                                <div class="bxlens-empty" x-show="(data.timings||[]).length===0">
	                                                  No custom timings. Use <span class="bxlens-code">LensStart('label')</span> / <span class="bxlens-code">LensStop('label')</span>.
	                                                </div>
	                                                <div style="display:flex;gap:4px;margin-bottom:8px;">
	                                                  <button class="bxlens-view-btn" :class="{active:tView==='bars'}"  @click="tView='bars'">Bars</button>
	                                                  <button class="bxlens-view-btn" :class="{active:tView==='table'}" @click="tView='table'">Table</button>
	                                                </div>
	                                                <template x-if="tView==='bars'">
	                                                  <div class="bxlens-tl">
	                                                    <template x-for="(t,i) in (data.timings||[])" :key="i">
	                                                      <div class="bxlens-tl-row">
	                                                        <span class="bxlens-tl-label" x-text="t.label"></span>
	                                                        <div class="bxlens-tl-wrap">
	                                                          <div class="bxlens-tl-bar"
	                                                               :style="'background:var(--bxl-ok);left:'+pct(t.offset,totalTime)+'%;width:'+Math.max(0.5,pct(t.executionTime,totalTime))+'%'"></div>
	                                                        </div>
	                                                        <span class="bxlens-tl-time" x-text="fmtMs(t.executionTime)"></span>
	                                                      </div>
	                                                    </template>
	                                                  </div>
	                                                </template>
	                                                <template x-if="tView==='table'">
	                                                  <table class="bxlens-kv">
	                                                    <thead><tr><th>Label</th><th>Duration</th><th>Offset</th></tr></thead>
	                                                    <tbody>
	                                                      <template x-for="(t,i) in (data.timings||[])" :key="i">
	                                                        <tr>
	                                                          <td x-text="t.label"></td>
	                                                          <td x-text="fmtMs(t.executionTime)"></td>
	                                                          <td x-text="t.offset+'ms'"></td>
	                                                        </tr>
	                                                      </template>
	                                                    </tbody>
	                                                  </table>
	                                                </template>
	                                              </div>

	                                              <!-- ===== TIMELINE ===== -->
	                                              <div x-show="activeTab==='timeline'">
	                                                <div class="bxlens-tl-filters">
	                                                  <span class="bxlens-tl-filter" :class="{off:!tlFilter.T}"
	                                                        style="color:var(--bxl-accent);border-color:var(--bxl-accent);"
	                                                        @click="tlFilter.T=!tlFilter.T">Templates</span>
	                                                  <span class="bxlens-tl-filter" :class="{off:!tlFilter.Q}"
	                                                        style="color:#f6ad55;border-color:#f6ad55;"
	                                                        @click="tlFilter.Q=!tlFilter.Q">Queries</span>
	                                                  <span class="bxlens-tl-filter" :class="{off:!tlFilter.H}"
	                                                        style="color:var(--bxl-http);border-color:var(--bxl-http);"
	                                                        @click="tlFilter.H=!tlFilter.H">HTTP</span>
	                                                  <span class="bxlens-tl-filter" :class="{off:!tlFilter.S}"
	                                                        style="color:var(--bxl-soap);border-color:var(--bxl-soap);"
	                                                        @click="tlFilter.S=!tlFilter.S">SOAP</span>
	                                                  <span class="bxlens-tl-filter" :class="{off:!tlFilter.C}"
	                                                        style="color:var(--bxl-ok);border-color:var(--bxl-ok);"
	                                                        @click="tlFilter.C=!tlFilter.C">Timings</span>
	                                                </div>
	                                                <div class="bxlens-tl">
	                                                  <!-- Reference bar -->
	                                                  <div class="bxlens-tl-ref">
	                                                    <span style="color:var(--bxl-text-muted);font-size:11px">Request total</span>
	                                                    <div class="bxlens-tl-wrap">
	                                                      <div class="bxlens-tl-bar" style="background:var(--bxl-border);width:100%"></div>
	                                                    </div>
	                                                    <span class="bxlens-tl-time" x-text="fmtMs(totalTime)"></span>
	                                                  </div>
	                                                  <!-- Events -->
	                                                  <template x-for="(ev,i) in tlEvents()" :key="i">
	                                                    <div class="bxlens-tl-row" :style="'padding-left:'+(ev.depth*14)+'px'">
	                                                      <span class="bxlens-tl-label" :title="ev.fullLabel" x-text="ev.label"></span>
	                                                      <div class="bxlens-tl-wrap" :title="fmtMs(ev.dur)+' @ '+ev.off+'ms'">
	                                                        <div class="bxlens-tl-bar"
	                                                             :style="'background:'+ev.color+';left:'+pct(ev.off,totalTime)+'%;width:'+Math.max(0.5,pct(ev.dur,totalTime))+'%'"></div>
	                                                      </div>
	                                                      <span class="bxlens-tl-time" x-text="fmtMs(ev.dur)"></span>
	                                                    </div>
	                                                  </template>
	                                                </div>
	                                              </div>

	                                              <!-- ===== TEMPLATES ===== -->
	                                              <div x-show="activeTab==='templates'">
	                                                <div class="bxlens-empty" x-show="data.templates.length===0">No templates recorded.</div>
	                                                <table class="bxlens-kv" x-show="data.templates.length>0">
	                                                  <thead><tr><th>#</th><th>Template</th><th>Offset</th><th>Duration</th></tr></thead>
	                                                  <tbody>
	                                                    <template x-for="(t,i) in data.templates" :key="i">
	                                                      <tr>
	                                                        <td style="color:var(--bxl-text-muted);width:24px" x-text="i+1"></td>
	                                                        <td :style="'padding-left:'+(t.depth*12)+'px'" style="word-break:break-all;font-size:11px;font-family:var(--bxl-font-mono)">
	                                                          <span x-text="(t.depth>0?'└ ':'')+t.templatePath"></span>
	                                                        </td>
	                                                        <td style="color:var(--bxl-text-muted);font-size:11px" x-text="t.offset+'ms'"></td>
	                                                        <td><span class="bxlens-badge-time" :class="timeClass(t.executionTime)" x-text="fmtMs(t.executionTime)"></span></td>
	                                                      </tr>
	                                                    </template>
	                                                  </tbody>
	                                                </table>
	                                              </div>

	                                              <!-- ===== VARIABLES ===== -->
	                                              <div x-show="activeTab==='variables'">
	                                                <div class="bxlens-subNav">
	                                                  <template x-for="scope in ['form','url','cgi','request','session','application']" :key="scope">
	                                                    <button class="bxlens-subBtn" :class="{active:varScope===scope}" @click="varScope=scope"
	                                                            x-text="scope.charAt(0).toUpperCase()+scope.slice(1)"></button>
	                                                  </template>
	                                                </div>
	                                                <template x-if="scopeEnabled(varScope)">
	                                                  <pre class="bxlens-pre" x-text="JSON.stringify(data.variables[varScope]||{},null,2)"></pre>
	                                                </template>
	                                                <template x-if="!scopeEnabled(varScope)">
	                                                  <div class="bxlens-scope-note">
	                                                    Scope capture is disabled. Enable in ModuleConfig: <span class="bxlens-code" x-text="'settings.scopes.'+varScope+' = true'"></span>
	                                                  </div>
	                                                </template>
	                                              </div>

	                                              <!-- ===== BOXLANG ===== -->
	                                              <div x-show="activeTab==='boxlang'">
	                                                <div class="bxlens-section">
	                                                  <div class="bxlens-section-title">Runtime</div>
	                                                  <table class="bxlens-kv">
	                                                    <tr><th>BoxLang Version</th><td x-text="bx.version||'—'"></td></tr>
	                                                    <tr><th>Java Version</th><td x-text="bx.javaVersion||'—'"></td></tr>
	                                                    <tr><th>OS</th><td x-text="(bx.osName||'—')+(bx.osArch?' ('+bx.osArch+')':'')"></td></tr>
	                                                  </table>
	                                                </div>
	                                                <div class="bxlens-section" x-show="(bx.modules||[]).length>0">
	                                                  <div class="bxlens-section-title">Installed Modules (<span x-text="(bx.modules||[]).length"></span>)</div>
	                                                  <div class="bxlens-bx-modules">
	                                                    <template x-for="(m,i) in (bx.modules||[])" :key="i">
	                                                      <div class="bxlens-bx-module" x-text="m"></div>
	                                                    </template>
	                                                  </div>
	                                                </div>
	                                                <div class="bxlens-section" x-show="(bx.extensions||[]).length>0">
	                                                  <div class="bxlens-section-title">Extensions</div>
	                                                  <div class="bxlens-bx-modules">
	                                                    <template x-for="(e,i) in (bx.extensions||[])" :key="i">
	                                                      <div class="bxlens-bx-module" x-text="e"></div>
	                                                    </template>
	                                                  </div>
	                                                </div>
	                                              </div>

	                                              <!-- ===== JVM ===== -->
	                                              <div x-show="activeTab==='jvm'">
	                                                <div class="bxlens-section">
	                                                  <div class="bxlens-section-title">Memory</div>
	                                                  <div x-show="jvm.memory">
	                                                    <div class="bxlens-mem-row">
	                                                      <span style="color:var(--bxl-text-muted)">Heap Used</span>
	                                                      <div class="bxlens-mem-bar">
	                                                        <div class="bxlens-mem-fill" :style="'width:'+memPct(jvm.memory.heapUsed||0,jvm.memory.heapMax||1)+'%'"></div>
	                                                      </div>
	                                                      <span x-text="fmtBytes(jvm.memory.heapUsed||0)+' / '+fmtBytes(jvm.memory.heapMax||0)"></span>
	                                                    </div>
	                                                    <div class="bxlens-mem-row">
	                                                      <span style="color:var(--bxl-text-muted)">Heap Committed</span>
	                                                      <div class="bxlens-mem-bar">
	                                                        <div class="bxlens-mem-fill" style="background:var(--bxl-http);"
	                                                             :style="'width:'+memPct(jvm.memory.heapCommitted||0,jvm.memory.heapMax||1)+'%'"></div>
	                                                      </div>
	                                                      <span x-text="fmtBytes(jvm.memory.heapCommitted||0)"></span>
	                                                    </div>
	                                                    <div class="bxlens-mem-row">
	                                                      <span style="color:var(--bxl-text-muted)">Non-Heap</span>
	                                                      <div class="bxlens-mem-bar">
	                                                        <div class="bxlens-mem-fill" style="background:var(--bxl-ok);width:30%"></div>
	                                                      </div>
	                                                      <span x-text="fmtBytes(jvm.memory.nonHeapUsed||0)"></span>
	                                                    </div>
	                                                  </div>
	                                                </div>
	                                                <div class="bxlens-section">
	                                                  <div class="bxlens-section-title">Threads</div>
	                                                  <div style="display:flex;align-items:center;gap:12px;margin-bottom:8px;">
	                                                    <span>Active Threads: <strong x-text="jvm.threadCount||'—'"></strong></span>
	                                                    <button class="bxlens-btn" @click="showThreadDump=!showThreadDump"
	                                                            x-text="showThreadDump?'Hide Thread Dump ▲':'Show Thread Dump ▼'"></button>
	                                                  </div>
	                                                  <pre class="bxlens-pre" x-show="showThreadDump"
	                                                       style="max-height:200px;overflow-y:auto;font-size:10px;padding:8px;background:var(--bxl-bg);border-radius:4px;"
	                                                       x-text="jvm.threadDump"></pre>
	                                                </div>
	                                                <div class="bxlens-section">
	                                                  <div class="bxlens-section-title" style="color:var(--bxl-warn)">⚠ Heap Dump</div>
	                                                  <p style="font-size:12px;color:var(--bxl-text-muted);margin-bottom:8px;">Heap dumps can be several GB. HotSpot JVMs only. Call from your application code:</p>
	                                                  <pre class="bxlens-pre" style="background:var(--bxl-bg);padding:8px;border-radius:4px;">var path = LensDumpHeap();
	                                          var path = LensDumpHeap( "/tmp/my.hprof" );</pre>
	                                                </div>
	                                              </div>

	                                              <!-- ===== SETTINGS ===== -->
	                                              <div x-show="activeTab==='settings'">
	                                                <div class="bxlens-section">
	                                                  <div class="bxlens-section-title">UI Preferences</div>

	                                                  <div class="bxlens-settings-row">
	                                                    <input type="checkbox" id="bxl-hide-empty" :checked="hideEmptyTabs" @change="toggleHideEmpty()">
	                                                    <label for="bxl-hide-empty">Hide empty tabs</label>
	                                                  </div>

	                                                  <div class="bxlens-settings-row">
	                                                    <span style="color:var(--bxl-text-muted);min-width:90px">Theme</span>
	                                                    <div class="bxlens-toggle-group">
	                                                      <button class="bxlens-toggle" :class="{active:theme==='dark'}"  @click="setTheme('dark')">Dark</button>
	                                                      <button class="bxlens-toggle" :class="{active:theme==='light'}" @click="setTheme('light')">Light</button>
	                                                      <button class="bxlens-toggle" :class="{active:theme==='auto'}"  @click="setTheme('auto')">Auto</button>
	                                                    </div>
	                                                  </div>

	                                                  <div class="bxlens-settings-row">
	                                                    <span style="color:var(--bxl-text-muted);min-width:90px">Panel Height</span>
	                                                    <div class="bxlens-toggle-group">
	                                                      <button class="bxlens-toggle" :class="{active:panelHeight===280}" @click="setPanelHeight(280)">280px</button>
	                                                      <button class="bxlens-toggle" :class="{active:panelHeight===360}" @click="setPanelHeight(360)">360px</button>
	                                                      <button class="bxlens-toggle" :class="{active:panelHeight===480}" @click="setPanelHeight(480)">480px</button>
	                                                    </div>
	                                                  </div>

	                                                  <div class="bxlens-settings-row">
	                                                    <span style="color:var(--bxl-text-muted);min-width:90px">Editor Links</span>
	                                                    <div class="bxlens-toggle-group">
	                                                      <button class="bxlens-toggle" :class="{active:editorMode==='vscode'}"    @click="setEditorMode('vscode')">VS Code</button>
	                                                      <button class="bxlens-toggle" :class="{active:editorMode==='phpstorm'}"  @click="setEditorMode('phpstorm')">PhpStorm</button>
	                                                      <button class="bxlens-toggle" :class="{active:editorMode==='off'}"       @click="setEditorMode('off')">Off</button>
	                                                    </div>
	                                                  </div>

	                                                  <div class="bxlens-settings-row">
	                                                    <button class="bxlens-btn" @click="resetSettings()">Reset to defaults</button>
	                                                  </div>
	                                                </div>

	                                                <div class="bxlens-section">
	                                                  <div class="bxlens-section-title">Server-side Module Settings (read-only)</div>
	                                                  <table class="bxlens-kv">
	                                                    <tr><th>Enabled</th><td x-text="ss.enabled"></td></tr>
	                                                    <tr><th>Max Queries</th><td x-text="ss.maxQueries"></td></tr>
	                                                    <tr><th>Max Exceptions</th><td x-text="ss.maxExceptions"></td></tr>
	                                                    <tr><th>Max Templates</th><td x-text="ss.maxTemplates"></td></tr>
	                                                    <tr><th>Max Messages</th><td x-text="ss.maxMessages"></td></tr>
	                                                    <tr><th>Max Timings</th><td x-text="ss.maxTimings"></td></tr>
	                                                    <tr><th>Theme Default</th><td x-text="ss.theme"></td></tr>
	                                                    <tr><th>Editor Pattern</th><td x-text="ss.editorLinkPattern||'(none)'"></td></tr>
	                                                    <tr><th>Scopes</th>
	                                                      <td>
	                                                        <template x-for="(v,k) in (ss.scopes||{})" :key="k">
	                                                          <span :style="v?'color:var(--bxl-ok)':'color:var(--bxl-text-muted)'">
	                                                            <span x-text="k"></span><span x-text="v?' ✓ ':' ✗ '"></span>
	                                                          </span>
	                                                        </template>
	                                                      </td>
	                                                    </tr>
	                                                  </table>
	                                                </div>
	                                              </div>

	                                            </div><!-- end .bxlens-content -->
	                                          </div><!-- end .bxlens-panel -->

	                                          </div><!-- end #bxlens-root -->
	                                          """;

}
