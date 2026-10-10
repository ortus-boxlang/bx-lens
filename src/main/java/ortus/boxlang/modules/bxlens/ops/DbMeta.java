/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the agent may know about a database: the tables, their columns and an approximate row count, all from the JDBC metadata of the
 * driver. Nothing here builds or runs SQL text, and no tool of the agent takes any. Row counts come from the table statistics a driver
 * reports with the index information (<code>CARDINALITY</code> of the statistic row). They are estimates, and a driver may report none.
 */
public final class DbMeta {

	public static final int MAX_TABLES = 200;

	private DbMeta() {
	}

	/**
	 * The tables and views, optionally those whose name contains a text, each with an approximate row count where the driver has one.
	 */
	public static Map<String, Object> tables( Connection c, String filter ) throws SQLException {
		DatabaseMetaData			md		= c.getMetaData();
		String						schema	= schemaOf( c );
		String						needle	= filter == null ? "" : filter.toLowerCase( Locale.ROOT );
		List<Map<String, Object>>	out		= new ArrayList<>();
		int							total	= 0;
		try ( ResultSet rs = md.getTables( c.getCatalog(), schema, "%", new String[] { "TABLE", "VIEW" } ) ) {
			while ( rs.next() ) {
				String name = rs.getString( "TABLE_NAME" );
				if ( name == null || !needle.isEmpty() && !name.toLowerCase( Locale.ROOT ).contains( needle ) ) {
					continue;
				}
				total++;
				if ( out.size() >= MAX_TABLES ) {
					continue;
				}
				Map<String, Object> t = new LinkedHashMap<>();
				t.put( "name", name );
				t.put( "schema", rs.getString( "TABLE_SCHEM" ) );
				t.put( "type", rs.getString( "TABLE_TYPE" ) );
				out.add( t );
			}
		}
		for ( Map<String, Object> t : out ) {
			if ( "TABLE".equalsIgnoreCase( String.valueOf( t.get( "type" ) ) ) ) {
				t.put( "approxRows", approxRows( md, c.getCatalog(), String.valueOf( t.get( "schema" ) ), String.valueOf( t.get( "name" ) ) ) );
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "product", md.getDatabaseProductName() );
		m.put( "schema", schema );
		m.put( "tables", out );
		m.put( "count", total );
		if ( total > out.size() ) {
			m.put( "note", "Showing the first " + out.size() + " of " + total + ". Use filter to narrow it." );
		}
		m.put( "approxRowsNote",
		    "approxRows comes from the table statistics of the driver. It is an estimate and null when the driver reports none. No SQL is run." );
		return m;
	}

	/**
	 * The columns of one table.
	 */
	public static Map<String, Object> columns( Connection c, String table ) throws SQLException {
		if ( !validName( table ) ) {
			throw new IllegalArgumentException( "That is not a table name" );
		}
		DatabaseMetaData			md		= c.getMetaData();
		String						schema	= schemaOf( c );
		List<Map<String, Object>>	out		= new ArrayList<>();
		String						found	= null;
		try ( ResultSet rs = md.getColumns( c.getCatalog(), schema, table, "%" ) ) {
			while ( rs.next() && out.size() < 300 ) {
				found = rs.getString( "TABLE_NAME" );
				Map<String, Object> col = new LinkedHashMap<>();
				col.put( "name", rs.getString( "COLUMN_NAME" ) );
				col.put( "type", rs.getString( "TYPE_NAME" ) );
				col.put( "size", rs.getInt( "COLUMN_SIZE" ) );
				col.put( "nullable", "YES".equalsIgnoreCase( rs.getString( "IS_NULLABLE" ) ) );
				out.add( col );
			}
		}
		List<String> pk = new ArrayList<>();
		if ( found != null ) {
			try ( ResultSet rs = md.getPrimaryKeys( c.getCatalog(), schema, found ) ) {
				while ( rs.next() ) {
					pk.add( rs.getString( "COLUMN_NAME" ) );
				}
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "table", found == null ? table : found );
		m.put( "columns", out );
		m.put( "primaryKey", pk );
		if ( out.isEmpty() ) {
			m.put( "note", "No such table in the default schema (" + schema + "). Names are case sensitive in some databases: use dbTables to see them." );
		}
		return m;
	}

	/**
	 * A table name the metadata calls accept: letters, digits and a few separators. It is a parameter of a metadata call, never part of SQL.
	 */
	public static boolean validName( String name ) {
		if ( name == null || name.isBlank() || name.length() > 128 ) {
			return false;
		}
		for ( int i = 0; i < name.length(); i++ ) {
			char ch = name.charAt( i );
			if ( !Character.isLetterOrDigit( ch ) && ch != '_' && ch != '$' && ch != '#' && ch != '-' && ch != '.' ) {
				return false;
			}
		}
		return true;
	}

	private static Object approxRows( DatabaseMetaData md, String catalog, String schema, String table ) {
		try ( ResultSet rs = md.getIndexInfo( catalog, "null".equals( schema ) ? null : schema, table, false, true ) ) {
			while ( rs.next() ) {
				if ( rs.getShort( "TYPE" ) == DatabaseMetaData.tableIndexStatistic ) {
					long n = rs.getLong( "CARDINALITY" );
					return rs.wasNull() ? null : n;
				}
			}
		} catch ( Throwable t ) {
			// The driver has no statistics for it
		}
		return null;
	}

	private static String schemaOf( Connection c ) {
		try {
			return c.getSchema();
		} catch ( Throwable t ) {
			return null;
		}
	}

}
