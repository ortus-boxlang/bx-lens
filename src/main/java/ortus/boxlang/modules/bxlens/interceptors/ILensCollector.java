/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors;

import ortus.boxlang.modules.bxlens.model.LensRequest;

/**
 * A Lens collector. One class per kind of data. Collectors that listen to BoxLang events extend {@link BaseCollector},
 * which makes them interceptors that the service registers when the collector is enabled in the settings.
 * <p>
 * Collectors are shared across threads: keep per-request state in the {@link LensRequest}, never in fields.
 */
public interface ILensCollector {

	/**
	 * Unique id, also the key under <code>collectors</code> in the settings.
	 */
	String id();

	/**
	 * Is this collector on when the settings do not mention it? Expensive collectors return false.
	 */
	default boolean enabledByDefault() {
		return true;
	}

	/**
	 * Is this collector too heavy or too revealing for the light collect level? Heavy collectors are not registered at that level.
	 */
	default boolean heavy() {
		return false;
	}

	/**
	 * Does this collector only add data to the page (the request snapshot)? Such a collector is skipped at the end of a request that is not kept
	 * in the history and shows no bar, because nobody would ever see what it gathers.
	 */
	default boolean snapshotOnly() {
		return false;
	}

	/**
	 * Called when a tracked request starts.
	 */
	default void onRequestStart( LensRequest request ) {
	}

	/**
	 * Called when a tracked request finishes, before the snapshot is built. Collectors that gather data at the end of a request do it here.
	 */
	default void onRequestFinish( LensRequest request ) {
	}

}
