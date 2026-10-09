/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToLongFunction;

/**
 * Keeps a map under its cap without scanning it for every new key. Nothing happens until the map is over the cap by a margin (10%, at least
 * 8 entries). Then one thread removes the least recently seen entries in a single pass, down to the cap, and the others carry on. So a store
 * may briefly hold up to a margin more than its cap, and a new key costs a size check, not a scan.
 */
public final class Bounded {

	private final AtomicBoolean busy = new AtomicBoolean();

	/**
	 * Evict the least recently seen entries when the map is over cap plus margin.
	 *
	 * @param lastSeen the time an entry was last seen
	 *
	 * @return how many entries were removed
	 */
	public <K, V> int trim( Map<K, V> map, int cap, ToLongFunction<V> lastSeen ) {
		int margin = Math.max( 8, cap / 10 );
		if ( map.size() <= cap + margin || !busy.compareAndSet( false, true ) ) {
			return 0;
		}
		try {
			List<Map.Entry<K, V>> all = new ArrayList<>( map.entrySet() );
			all.sort( Comparator.comparingLong( e -> lastSeen.applyAsLong( e.getValue() ) ) );
			int	remove	= Math.max( 0, all.size() - cap );
			int	removed	= 0;
			for ( int i = 0; i < remove; i++ ) {
				if ( map.remove( all.get( i ).getKey() ) != null ) {
					removed++;
				}
			}
			return removed;
		} finally {
			busy.set( false );
		}
	}

}
