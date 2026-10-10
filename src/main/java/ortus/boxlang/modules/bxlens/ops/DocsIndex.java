/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The chunks of the bundled documentation, and the keyword search over them that is used when no embedding model is available. The chunks
 * are cut by the bx-ai <code>MarkdownLoader</code> in <code>DocsRag.bx</code>, which also builds the vector index; this class keeps the same
 * chunks in Java so the fallback search does not depend on a model. The index is rebuilt only when the checksum of the documents changes. It
 * lives in memory and is never written to disk.
 */
public final class DocsIndex {

	/** The most characters of a chunk a search result carries. */
	public static final int				MAX_CHUNK	= 900;

	/** Words too common to tell one chunk from another. */
	private static final Set<String>	STOP		= Set.of( "the", "a", "an", "and", "or", "of", "to", "in", "on", "is", "are", "it", "for", "with", "as",
	    "by", "at",
	    "be", "this", "that", "how", "do", "does", "i", "we", "you", "can", "what", "which", "my", "me", "not", "from", "if", "when", "should", "would",
	    "about" );

	/**
	 * One piece of a page.
	 *
	 * @param page    the page, for example <code>console/executors.md</code>
	 * @param heading the section heading, or an empty string
	 * @param text    the text
	 */
	public record Chunk( String page, String heading, String text ) {
	}

	private volatile List<Chunk>	chunks		= List.of();
	private volatile String			checksum	= "";
	private volatile String			mode		= "none";
	private volatile String			note		= "";
	private volatile String			signature	= "";

	/**
	 * A checksum over the names and the content of the Markdown files under a folder. It changes when a page is added, removed or edited.
	 */
	public static String checksum( Path dir ) {
		try ( Stream<Path> walk = Files.walk( dir, 4 ) ) {
			MessageDigest	md		= MessageDigest.getInstance( "SHA-256" );
			List<Path>		files	= walk.filter( p -> Files.isRegularFile( p ) && p.getFileName().toString().endsWith( ".md" ) ).sorted().toList();
			for ( Path f : files ) {
				md.update( dir.relativize( f ).toString().replace( '\\', '/' ).getBytes( StandardCharsets.UTF_8 ) );
				md.update( ( byte ) 0 );
				md.update( Files.readAllBytes( f ) );
				md.update( ( byte ) 0 );
			}
			StringBuilder sb = new StringBuilder();
			for ( byte b : md.digest() ) {
				sb.append( Character.forDigit( b >> 4 & 15, 16 ) ).append( Character.forDigit( b & 15, 16 ) );
			}
			return sb.toString();
		} catch ( IOException | java.security.NoSuchAlgorithmException e ) {
			return "";
		}
	}

	/**
	 * The Markdown files under a folder, relative names, sorted.
	 */
	public static List<String> pages( Path dir ) {
		try ( Stream<Path> walk = Files.walk( dir, 4 ) ) {
			return walk.filter( p -> Files.isRegularFile( p ) && p.getFileName().toString().endsWith( ".md" ) )
			    .map( p -> dir.relativize( p ).toString().replace( '\\', '/' ) )
			    .sorted().toList();
		} catch ( IOException e ) {
			return List.of();
		}
	}

	/**
	 * Replace the chunks. {@code mode} says how a search is answered: keywords, or embeddings once the vector index is built.
	 */
	public void set( List<Chunk> chunks, String checksum, String signature ) {
		this.chunks		= List.copyOf( chunks );
		this.checksum	= checksum;
		this.signature	= signature;
		this.mode		= "keywords";
		this.note		= "";
	}

	public void mode( String mode, String note ) {
		this.mode	= mode;
		this.note	= note == null ? "" : note;
	}

	public boolean current( String checksum, String signature ) {
		return !this.chunks.isEmpty() && this.checksum.equals( checksum ) && this.signature.equals( signature );
	}

	public int size() {
		return this.chunks.size();
	}

	public List<Chunk> chunks() {
		return this.chunks;
	}

	public String mode() {
		return this.mode;
	}

	public String note() {
		return this.note;
	}

	public String checksum() {
		return this.checksum;
	}

	/**
	 * The words of a text, lower case, without the common ones and without one letter words. Scanned by hand, no regular expression.
	 */
	public static List<String> tokens( String text ) {
		List<String>	out	= new ArrayList<>();
		StringBuilder	sb	= new StringBuilder();
		String			s	= text == null ? "" : text.toLowerCase( Locale.ROOT );
		for ( int i = 0; i <= s.length(); i++ ) {
			char c = i < s.length() ? s.charAt( i ) : ' ';
			if ( Character.isLetterOrDigit( c ) ) {
				sb.append( c );
			} else if ( sb.length() > 0 ) {
				String w = sb.toString();
				sb.setLength( 0 );
				if ( w.length() > 1 && !STOP.contains( w ) ) {
					out.add( stem( w ) );
				}
			}
		}
		return out;
	}

	/** Very small stemmer: plural and -ing/-ed endings, so "executors" finds "executor". */
	private static String stem( String w ) {
		if ( w.length() > 4 && w.endsWith( "ies" ) ) {
			return w.substring( 0, w.length() - 3 ) + "y";
		}
		if ( w.length() > 4 && w.endsWith( "ing" ) ) {
			return w.substring( 0, w.length() - 3 );
		}
		if ( w.length() > 3 && w.endsWith( "es" ) && ( w.endsWith( "ses" ) || w.endsWith( "xes" ) || w.endsWith( "ches" ) || w.endsWith( "shes" ) ) ) {
			return w.substring( 0, w.length() - 2 );
		}
		if ( w.length() > 3 && w.endsWith( "s" ) && !w.endsWith( "ss" ) ) {
			return w.substring( 0, w.length() - 1 );
		}
		if ( w.length() > 4 && w.endsWith( "ed" ) ) {
			return w.substring( 0, w.length() - 2 );
		}
		return w;
	}

	/**
	 * Score the chunks against a question: every word of the question that appears in a chunk adds to its score, more when it is in the
	 * heading or the page name, and rare words count more than common ones. The top chunks are returned, best first.
	 */
	public List<Map<String, Object>> keywordSearch( String question, int limit ) {
		List<String> q = tokens( question );
		if ( q.isEmpty() || this.chunks.isEmpty() ) {
			return List.of();
		}
		List<Chunk>					all		= this.chunks;
		Map<String, Integer>		docFreq	= new HashMap<>();
		List<Map<String, Integer>>	counts	= new ArrayList<>();
		for ( Chunk c : all ) {
			Map<String, Integer> tf = new HashMap<>();
			for ( String t : tokens( c.text() ) ) {
				tf.merge( t, 1, Integer::sum );
			}
			counts.add( tf );
			for ( String t : tf.keySet() ) {
				docFreq.merge( t, 1, Integer::sum );
			}
		}
		record Scored( int index, double score ) {
		}
		List<Scored> scored = new ArrayList<>();
		for ( int i = 0; i < all.size(); i++ ) {
			Chunk					c		= all.get( i );
			Map<String, Integer>	tf		= counts.get( i );
			List<String>			head	= tokens( c.heading() + " " + c.page().replace( '/', ' ' ).replace( '-', ' ' ) );
			double					score	= 0;
			for ( String t : q.stream().distinct().toList() ) {
				int n = tf.getOrDefault( t, 0 );
				if ( n > 0 ) {
					double idf = Math.log( 1 + ( double ) all.size() / ( 1 + docFreq.getOrDefault( t, 0 ) ) );
					score += idf * ( 1 + Math.log( n ) );
				}
				if ( head.contains( t ) ) {
					score += 2.5;
				}
			}
			if ( score > 0 ) {
				scored.add( new Scored( i, score ) );
			}
		}
		scored.sort( Comparator.comparingDouble( Scored::score ).reversed() );
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Scored s : scored ) {
			out.add( result( all.get( s.index() ), Math.round( s.score() * 100.0 ) / 100.0 ) );
			if ( out.size() >= limit ) {
				break;
			}
		}
		return out;
	}

	/**
	 * A search result as the agent gets it: the page name, the heading and the text, cut to {@link #MAX_CHUNK} characters.
	 */
	public static Map<String, Object> result( Chunk c, double score ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "page", c.page() );
		m.put( "heading", c.heading() );
		m.put( "score", score );
		m.put( "text", c.text().length() > MAX_CHUNK ? c.text().substring( 0, MAX_CHUNK ) + "..." : c.text() );
		return m;
	}

}
