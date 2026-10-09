/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TextTest {

	@Test
	@DisplayName( "string and number literals in SQL become question marks, the rest stays" )
	void maskSql() {
		assertThat( Text.maskSql( "SELECT * FROM users WHERE email = 'bob@example.com' AND age > 30 AND id = ?" ) )
		    .isEqualTo( "SELECT * FROM users WHERE email = ? AND age > ? AND id = ?" );
		assertThat( Text.maskSql( "select 'it''s' , 'a\\'b', 1.5e3, 0x1F from t1 where c2 = :name and d = $1" ) )
		    .isEqualTo( "select ? , ?, ?, ? from t1 where c2 = :name and d = $1" );
		assertThat( Text.maskSql( "SELECT \"col1\", `x2` FROM [t3] WHERE a IN (1,2,3) -- note 5\n/* 6 */" ) )
		    .isEqualTo( "SELECT \"col1\", `x2` FROM [t3] WHERE a IN (?,?,?) -- note 5\n/* 6 */" );
		assertThat( Text.maskSql( "SELECT 'unterminated" ) ).isEqualTo( "SELECT ?" );
		assertThat( Text.maskSql( null ) ).isEmpty();
		assertThat( Text.maskSql( "" ) ).isEmpty();
	}

	@Test
	@DisplayName( "whitespace is collapsed and trimmed without a regular expression" )
	void spaces() {
		assertThat( Text.collapseSpaces( "  a \n\t b   c  " ) ).isEqualTo( "a b c" );
		String clean = "select a from b";
		assertThat( Text.collapseSpaces( clean ) ).isSameInstanceAs( clean );
		assertThat( Text.collapseSpaces( "   " ) ).isEmpty();
		assertThat( Text.collapseSpaces( null ) ).isEmpty();
		assertThat( Text.normalizeSql( " SELECT  *\nFROM T " ) ).isEqualTo( "select * from t" );
	}

	@Test
	@DisplayName( "numeric, UUID and long hex path segments collapse to :id" )
	void paths() {
		assertThat( Text.collapsePath( "/orders/42" ) ).isEqualTo( "/orders/:id" );
		assertThat( Text.collapsePath( "/orders/42/items/7/edit" ) ).isEqualTo( "/orders/:id/items/:id/edit" );
		assertThat( Text.collapsePath( "/u/123e4567-e89b-12d3-a456-426614174000/x" ) ).isEqualTo( "/u/:id/x" );
		assertThat( Text.collapsePath( "/f/0123456789abcdef0123/x" ) ).isEqualTo( "/f/:id/x" );
		assertThat( Text.collapsePath( "/v2/orders.bxm" ) ).isEqualTo( "/v2/orders.bxm" );
		assertThat( Text.collapsePath( "/abcdefabcdefabcdef" ) ).isEqualTo( "/abcdefabcdefabcdef" );
		assertThat( Text.collapsePath( "/" ) ).isEqualTo( "/" );
		assertThat( Text.collapsePath( "" ) ).isEmpty();
	}

	@Test
	@DisplayName( "error messages group by shape: quotes, numbers and ids removed" )
	void messages() {
		assertThat( Text.generalizeMessage( "Key 'abc' not found in row 17 of 123e4567-e89b-12d3-a456-426614174000" ) )
		    .isEqualTo( "key ? not found in row # of #" );
		assertThat( Text.generalizeMessage( "Boom \"x\"" ) ).isEqualTo( "boom ?" );
		assertThat( Text.generalizeMessage( "" ) ).isEmpty();
	}

}
