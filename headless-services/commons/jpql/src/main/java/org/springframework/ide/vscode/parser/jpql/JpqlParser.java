// Generated from Jpql.g4 by ANTLR 4.13.2
package org.springframework.ide.vscode.parser.jpql;

/**
 * JPQL per https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#bnf
 *
 * This is JPA BNF for JPQL. There are gaps and inconsistencies in the BNF itself, explained by other fragments of the spec.
 *
 * @see https://github.com/jakartaee/persistence/blob/master/spec/src/main/asciidoc/ch04-query-language.adoc#bnf
 * @author Greg Turnquist
 * @author Christoph Strobl
 * @since 3.1
 */

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class JpqlParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, WS=16, ABS=17, 
		ALL=18, AND=19, ANY=20, AS=21, ASC=22, AVG=23, BETWEEN=24, BOTH=25, BY=26, 
		CASE=27, CEILING=28, COALESCE=29, CONCAT=30, COUNT=31, CURRENT_DATE=32, 
		CURRENT_TIME=33, CURRENT_TIMESTAMP=34, DATE=35, DATETIME=36, DELETE=37, 
		DESC=38, DISTINCT=39, END=40, ELSE=41, EMPTY=42, ENTRY=43, ESCAPE=44, 
		EXISTS=45, EXP=46, EXTRACT=47, FALSE=48, FETCH=49, FLOOR=50, FROM=51, 
		FUNCTION=52, GROUP=53, HAVING=54, IN=55, INDEX=56, INNER=57, IS=58, JOIN=59, 
		KEY=60, LEADING=61, LEFT=62, LENGTH=63, LIKE=64, LN=65, LOCAL=66, LOCATE=67, 
		LOWER=68, MAX=69, MEMBER=70, MIN=71, MOD=72, NEW=73, NOT=74, NULL=75, 
		NULLIF=76, OBJECT=77, OF=78, ON=79, OR=80, ORDER=81, OUTER=82, POWER=83, 
		ROUND=84, SELECT=85, SET=86, SIGN=87, SIZE=88, SOME=89, SQRT=90, SUBSTRING=91, 
		SUM=92, THEN=93, TIME=94, TRAILING=95, TREAT=96, TRIM=97, TRUE=98, TYPE=99, 
		UPDATE=100, UPPER=101, VALUE=102, WHEN=103, WHERE=104, EQUAL=105, NOT_EQUAL=106, 
		CHARACTER=107, IDENTIFICATION_VARIABLE=108, STRINGLITERAL=109, JAVASTRINGLITERAL=110, 
		FLOATLITERAL=111, INTLITERAL=112, LONGLITERAL=113, SPEL=114;
	public static final int
		RULE_start = 0, RULE_ql_statement = 1, RULE_select_statement = 2, RULE_update_statement = 3, 
		RULE_delete_statement = 4, RULE_from_clause = 5, RULE_identificationVariableDeclarationOrCollectionMemberDeclaration = 6, 
		RULE_identification_variable_declaration = 7, RULE_range_variable_declaration = 8, 
		RULE_join = 9, RULE_fetch_join = 10, RULE_join_spec = 11, RULE_join_condition = 12, 
		RULE_join_association_path_expression = 13, RULE_join_collection_valued_path_expression = 14, 
		RULE_join_single_valued_path_expression = 15, RULE_collection_member_declaration = 16, 
		RULE_qualified_identification_variable = 17, RULE_map_field_identification_variable = 18, 
		RULE_single_valued_path_expression = 19, RULE_general_identification_variable = 20, 
		RULE_general_subpath = 21, RULE_simple_subpath = 22, RULE_treated_subpath = 23, 
		RULE_state_field_path_expression = 24, RULE_state_valued_path_expression = 25, 
		RULE_single_valued_object_path_expression = 26, RULE_collection_valued_path_expression = 27, 
		RULE_update_clause = 28, RULE_update_item = 29, RULE_new_value = 30, RULE_delete_clause = 31, 
		RULE_select_clause = 32, RULE_select_item = 33, RULE_select_expression = 34, 
		RULE_constructor_expression = 35, RULE_constructor_item = 36, RULE_aggregate_expression = 37, 
		RULE_where_clause = 38, RULE_groupby_clause = 39, RULE_groupby_item = 40, 
		RULE_having_clause = 41, RULE_orderby_clause = 42, RULE_orderby_item = 43, 
		RULE_subquery = 44, RULE_subquery_from_clause = 45, RULE_subselect_identification_variable_declaration = 46, 
		RULE_derived_path_expression = 47, RULE_general_derived_path = 48, RULE_simple_derived_path = 49, 
		RULE_treated_derived_path = 50, RULE_derived_collection_member_declaration = 51, 
		RULE_simple_select_clause = 52, RULE_simple_select_expression = 53, RULE_scalar_expression = 54, 
		RULE_conditional_expression = 55, RULE_conditional_term = 56, RULE_conditional_factor = 57, 
		RULE_conditional_primary = 58, RULE_simple_cond_expression = 59, RULE_between_expression = 60, 
		RULE_in_expression = 61, RULE_in_item = 62, RULE_like_expression = 63, 
		RULE_null_comparison_expression = 64, RULE_empty_collection_comparison_expression = 65, 
		RULE_collection_member_expression = 66, RULE_entity_or_value_expression = 67, 
		RULE_simple_entity_or_value_expression = 68, RULE_exists_expression = 69, 
		RULE_all_or_any_expression = 70, RULE_comparison_expression = 71, RULE_comparison_operator = 72, 
		RULE_arithmetic_expression = 73, RULE_arithmetic_term = 74, RULE_arithmetic_factor = 75, 
		RULE_arithmetic_primary = 76, RULE_string_expression = 77, RULE_datetime_expression = 78, 
		RULE_boolean_expression = 79, RULE_enum_expression = 80, RULE_entity_expression = 81, 
		RULE_simple_entity_expression = 82, RULE_entity_type_expression = 83, 
		RULE_type_discriminator = 84, RULE_functions_returning_numerics = 85, 
		RULE_functions_returning_datetime = 86, RULE_functions_returning_strings = 87, 
		RULE_trim_specification = 88, RULE_function_invocation = 89, RULE_extract_datetime_field = 90, 
		RULE_datetime_field = 91, RULE_extract_datetime_part = 92, RULE_datetime_part = 93, 
		RULE_function_arg = 94, RULE_case_expression = 95, RULE_general_case_expression = 96, 
		RULE_when_clause = 97, RULE_simple_case_expression = 98, RULE_case_operand = 99, 
		RULE_simple_when_clause = 100, RULE_coalesce_expression = 101, RULE_nullif_expression = 102, 
		RULE_trim_character = 103, RULE_identification_variable = 104, RULE_constructor_name = 105, 
		RULE_literal = 106, RULE_input_parameter = 107, RULE_pattern_value = 108, 
		RULE_date_time_timestamp_literal = 109, RULE_entity_type_literal = 110, 
		RULE_escape_character = 111, RULE_numeric_literal = 112, RULE_boolean_literal = 113, 
		RULE_enum_literal = 114, RULE_string_literal = 115, RULE_single_valued_embeddable_object_field = 116, 
		RULE_subtype = 117, RULE_collection_valued_field = 118, RULE_single_valued_object_field = 119, 
		RULE_state_field = 120, RULE_collection_value_field = 121, RULE_entity_name = 122, 
		RULE_result_variable = 123, RULE_superquery_identification_variable = 124, 
		RULE_collection_valued_input_parameter = 125, RULE_single_valued_input_parameter = 126, 
		RULE_function_name = 127, RULE_character_valued_input_parameter = 128;
	private static String[] makeRuleNames() {
		return new String[] {
			"start", "ql_statement", "select_statement", "update_statement", "delete_statement", 
			"from_clause", "identificationVariableDeclarationOrCollectionMemberDeclaration", 
			"identification_variable_declaration", "range_variable_declaration", 
			"join", "fetch_join", "join_spec", "join_condition", "join_association_path_expression", 
			"join_collection_valued_path_expression", "join_single_valued_path_expression", 
			"collection_member_declaration", "qualified_identification_variable", 
			"map_field_identification_variable", "single_valued_path_expression", 
			"general_identification_variable", "general_subpath", "simple_subpath", 
			"treated_subpath", "state_field_path_expression", "state_valued_path_expression", 
			"single_valued_object_path_expression", "collection_valued_path_expression", 
			"update_clause", "update_item", "new_value", "delete_clause", "select_clause", 
			"select_item", "select_expression", "constructor_expression", "constructor_item", 
			"aggregate_expression", "where_clause", "groupby_clause", "groupby_item", 
			"having_clause", "orderby_clause", "orderby_item", "subquery", "subquery_from_clause", 
			"subselect_identification_variable_declaration", "derived_path_expression", 
			"general_derived_path", "simple_derived_path", "treated_derived_path", 
			"derived_collection_member_declaration", "simple_select_clause", "simple_select_expression", 
			"scalar_expression", "conditional_expression", "conditional_term", "conditional_factor", 
			"conditional_primary", "simple_cond_expression", "between_expression", 
			"in_expression", "in_item", "like_expression", "null_comparison_expression", 
			"empty_collection_comparison_expression", "collection_member_expression", 
			"entity_or_value_expression", "simple_entity_or_value_expression", "exists_expression", 
			"all_or_any_expression", "comparison_expression", "comparison_operator", 
			"arithmetic_expression", "arithmetic_term", "arithmetic_factor", "arithmetic_primary", 
			"string_expression", "datetime_expression", "boolean_expression", "enum_expression", 
			"entity_expression", "simple_entity_expression", "entity_type_expression", 
			"type_discriminator", "functions_returning_numerics", "functions_returning_datetime", 
			"functions_returning_strings", "trim_specification", "function_invocation", 
			"extract_datetime_field", "datetime_field", "extract_datetime_part", 
			"datetime_part", "function_arg", "case_expression", "general_case_expression", 
			"when_clause", "simple_case_expression", "case_operand", "simple_when_clause", 
			"coalesce_expression", "nullif_expression", "trim_character", "identification_variable", 
			"constructor_name", "literal", "input_parameter", "pattern_value", "date_time_timestamp_literal", 
			"entity_type_literal", "escape_character", "numeric_literal", "boolean_literal", 
			"enum_literal", "string_literal", "single_valued_embeddable_object_field", 
			"subtype", "collection_valued_field", "single_valued_object_field", "state_field", 
			"collection_value_field", "entity_name", "result_variable", "superquery_identification_variable", 
			"collection_valued_input_parameter", "single_valued_input_parameter", 
			"function_name", "character_valued_input_parameter"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "','", "'('", "')'", "'.'", "'%'", "'>'", "'>='", "'<'", "'<='", 
			"'+'", "'-'", "'*'", "'/'", "':'", "'?'", null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			"'='"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, "WS", "ABS", "ALL", "AND", "ANY", "AS", "ASC", 
			"AVG", "BETWEEN", "BOTH", "BY", "CASE", "CEILING", "COALESCE", "CONCAT", 
			"COUNT", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "DATE", 
			"DATETIME", "DELETE", "DESC", "DISTINCT", "END", "ELSE", "EMPTY", "ENTRY", 
			"ESCAPE", "EXISTS", "EXP", "EXTRACT", "FALSE", "FETCH", "FLOOR", "FROM", 
			"FUNCTION", "GROUP", "HAVING", "IN", "INDEX", "INNER", "IS", "JOIN", 
			"KEY", "LEADING", "LEFT", "LENGTH", "LIKE", "LN", "LOCAL", "LOCATE", 
			"LOWER", "MAX", "MEMBER", "MIN", "MOD", "NEW", "NOT", "NULL", "NULLIF", 
			"OBJECT", "OF", "ON", "OR", "ORDER", "OUTER", "POWER", "ROUND", "SELECT", 
			"SET", "SIGN", "SIZE", "SOME", "SQRT", "SUBSTRING", "SUM", "THEN", "TIME", 
			"TRAILING", "TREAT", "TRIM", "TRUE", "TYPE", "UPDATE", "UPPER", "VALUE", 
			"WHEN", "WHERE", "EQUAL", "NOT_EQUAL", "CHARACTER", "IDENTIFICATION_VARIABLE", 
			"STRINGLITERAL", "JAVASTRINGLITERAL", "FLOATLITERAL", "INTLITERAL", "LONGLITERAL", 
			"SPEL"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "Jpql.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public JpqlParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StartContext extends ParserRuleContext {
		public Ql_statementContext ql_statement() {
			return getRuleContext(Ql_statementContext.class,0);
		}
		public TerminalNode EOF() { return getToken(JpqlParser.EOF, 0); }
		public StartContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_start; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterStart(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitStart(this);
		}
	}

	public final StartContext start() throws RecognitionException {
		StartContext _localctx = new StartContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_start);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(258);
			ql_statement();
			setState(259);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Ql_statementContext extends ParserRuleContext {
		public Select_statementContext select_statement() {
			return getRuleContext(Select_statementContext.class,0);
		}
		public Update_statementContext update_statement() {
			return getRuleContext(Update_statementContext.class,0);
		}
		public Delete_statementContext delete_statement() {
			return getRuleContext(Delete_statementContext.class,0);
		}
		public Ql_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ql_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterQl_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitQl_statement(this);
		}
	}

	public final Ql_statementContext ql_statement() throws RecognitionException {
		Ql_statementContext _localctx = new Ql_statementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_ql_statement);
		try {
			setState(264);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case SELECT:
				enterOuterAlt(_localctx, 1);
				{
				setState(261);
				select_statement();
				}
				break;
			case UPDATE:
				enterOuterAlt(_localctx, 2);
				{
				setState(262);
				update_statement();
				}
				break;
			case DELETE:
				enterOuterAlt(_localctx, 3);
				{
				setState(263);
				delete_statement();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Select_statementContext extends ParserRuleContext {
		public Select_clauseContext select_clause() {
			return getRuleContext(Select_clauseContext.class,0);
		}
		public From_clauseContext from_clause() {
			return getRuleContext(From_clauseContext.class,0);
		}
		public Where_clauseContext where_clause() {
			return getRuleContext(Where_clauseContext.class,0);
		}
		public Groupby_clauseContext groupby_clause() {
			return getRuleContext(Groupby_clauseContext.class,0);
		}
		public Having_clauseContext having_clause() {
			return getRuleContext(Having_clauseContext.class,0);
		}
		public Orderby_clauseContext orderby_clause() {
			return getRuleContext(Orderby_clauseContext.class,0);
		}
		public Select_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_select_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSelect_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSelect_statement(this);
		}
	}

	public final Select_statementContext select_statement() throws RecognitionException {
		Select_statementContext _localctx = new Select_statementContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_select_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(266);
			select_clause();
			setState(267);
			from_clause();
			setState(269);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(268);
				where_clause();
				}
			}

			setState(272);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==GROUP) {
				{
				setState(271);
				groupby_clause();
				}
			}

			setState(275);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==HAVING) {
				{
				setState(274);
				having_clause();
				}
			}

			setState(278);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ORDER) {
				{
				setState(277);
				orderby_clause();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Update_statementContext extends ParserRuleContext {
		public Update_clauseContext update_clause() {
			return getRuleContext(Update_clauseContext.class,0);
		}
		public Where_clauseContext where_clause() {
			return getRuleContext(Where_clauseContext.class,0);
		}
		public Update_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_update_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterUpdate_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitUpdate_statement(this);
		}
	}

	public final Update_statementContext update_statement() throws RecognitionException {
		Update_statementContext _localctx = new Update_statementContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_update_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(280);
			update_clause();
			setState(282);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(281);
				where_clause();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Delete_statementContext extends ParserRuleContext {
		public Delete_clauseContext delete_clause() {
			return getRuleContext(Delete_clauseContext.class,0);
		}
		public Where_clauseContext where_clause() {
			return getRuleContext(Where_clauseContext.class,0);
		}
		public Delete_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_delete_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterDelete_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitDelete_statement(this);
		}
	}

	public final Delete_statementContext delete_statement() throws RecognitionException {
		Delete_statementContext _localctx = new Delete_statementContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_delete_statement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(284);
			delete_clause();
			setState(286);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(285);
				where_clause();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class From_clauseContext extends ParserRuleContext {
		public TerminalNode FROM() { return getToken(JpqlParser.FROM, 0); }
		public Identification_variable_declarationContext identification_variable_declaration() {
			return getRuleContext(Identification_variable_declarationContext.class,0);
		}
		public List<IdentificationVariableDeclarationOrCollectionMemberDeclarationContext> identificationVariableDeclarationOrCollectionMemberDeclaration() {
			return getRuleContexts(IdentificationVariableDeclarationOrCollectionMemberDeclarationContext.class);
		}
		public IdentificationVariableDeclarationOrCollectionMemberDeclarationContext identificationVariableDeclarationOrCollectionMemberDeclaration(int i) {
			return getRuleContext(IdentificationVariableDeclarationOrCollectionMemberDeclarationContext.class,i);
		}
		public From_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_from_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterFrom_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitFrom_clause(this);
		}
	}

	public final From_clauseContext from_clause() throws RecognitionException {
		From_clauseContext _localctx = new From_clauseContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_from_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(288);
			match(FROM);
			setState(289);
			identification_variable_declaration();
			setState(294);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(290);
				match(T__0);
				setState(291);
				identificationVariableDeclarationOrCollectionMemberDeclaration();
				}
				}
				setState(296);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IdentificationVariableDeclarationOrCollectionMemberDeclarationContext extends ParserRuleContext {
		public Identification_variable_declarationContext identification_variable_declaration() {
			return getRuleContext(Identification_variable_declarationContext.class,0);
		}
		public Collection_member_declarationContext collection_member_declaration() {
			return getRuleContext(Collection_member_declarationContext.class,0);
		}
		public IdentificationVariableDeclarationOrCollectionMemberDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identificationVariableDeclarationOrCollectionMemberDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterIdentificationVariableDeclarationOrCollectionMemberDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitIdentificationVariableDeclarationOrCollectionMemberDeclaration(this);
		}
	}

	public final IdentificationVariableDeclarationOrCollectionMemberDeclarationContext identificationVariableDeclarationOrCollectionMemberDeclaration() throws RecognitionException {
		IdentificationVariableDeclarationOrCollectionMemberDeclarationContext _localctx = new IdentificationVariableDeclarationOrCollectionMemberDeclarationContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_identificationVariableDeclarationOrCollectionMemberDeclaration);
		try {
			setState(299);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AVG:
			case COUNT:
			case DATE:
			case FLOOR:
			case FROM:
			case INNER:
			case KEY:
			case LEFT:
			case MAX:
			case MIN:
			case NEW:
			case ORDER:
			case OUTER:
			case POWER:
			case SIGN:
			case SUM:
			case TIME:
			case TYPE:
			case VALUE:
			case IDENTIFICATION_VARIABLE:
				enterOuterAlt(_localctx, 1);
				{
				setState(297);
				identification_variable_declaration();
				}
				break;
			case IN:
				enterOuterAlt(_localctx, 2);
				{
				setState(298);
				collection_member_declaration();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Identification_variable_declarationContext extends ParserRuleContext {
		public Range_variable_declarationContext range_variable_declaration() {
			return getRuleContext(Range_variable_declarationContext.class,0);
		}
		public List<JoinContext> join() {
			return getRuleContexts(JoinContext.class);
		}
		public JoinContext join(int i) {
			return getRuleContext(JoinContext.class,i);
		}
		public List<Fetch_joinContext> fetch_join() {
			return getRuleContexts(Fetch_joinContext.class);
		}
		public Fetch_joinContext fetch_join(int i) {
			return getRuleContext(Fetch_joinContext.class,i);
		}
		public Identification_variable_declarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identification_variable_declaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterIdentification_variable_declaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitIdentification_variable_declaration(this);
		}
	}

	public final Identification_variable_declarationContext identification_variable_declaration() throws RecognitionException {
		Identification_variable_declarationContext _localctx = new Identification_variable_declarationContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_identification_variable_declaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(301);
			range_variable_declaration();
			setState(306);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 5332261958806667264L) != 0)) {
				{
				setState(304);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,9,_ctx) ) {
				case 1:
					{
					setState(302);
					join();
					}
					break;
				case 2:
					{
					setState(303);
					fetch_join();
					}
					break;
				}
				}
				setState(308);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Range_variable_declarationContext extends ParserRuleContext {
		public Entity_nameContext entity_name() {
			return getRuleContext(Entity_nameContext.class,0);
		}
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public TerminalNode AS() { return getToken(JpqlParser.AS, 0); }
		public Range_variable_declarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_range_variable_declaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterRange_variable_declaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitRange_variable_declaration(this);
		}
	}

	public final Range_variable_declarationContext range_variable_declaration() throws RecognitionException {
		Range_variable_declarationContext _localctx = new Range_variable_declarationContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_range_variable_declaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(309);
			entity_name();
			setState(311);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(310);
				match(AS);
				}
			}

			setState(313);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class JoinContext extends ParserRuleContext {
		public Join_specContext join_spec() {
			return getRuleContext(Join_specContext.class,0);
		}
		public Join_association_path_expressionContext join_association_path_expression() {
			return getRuleContext(Join_association_path_expressionContext.class,0);
		}
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public TerminalNode AS() { return getToken(JpqlParser.AS, 0); }
		public Join_conditionContext join_condition() {
			return getRuleContext(Join_conditionContext.class,0);
		}
		public JoinContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_join; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterJoin(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitJoin(this);
		}
	}

	public final JoinContext join() throws RecognitionException {
		JoinContext _localctx = new JoinContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_join);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(315);
			join_spec();
			setState(316);
			join_association_path_expression();
			setState(318);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(317);
				match(AS);
				}
			}

			setState(320);
			identification_variable();
			setState(322);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ON) {
				{
				setState(321);
				join_condition();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Fetch_joinContext extends ParserRuleContext {
		public Join_specContext join_spec() {
			return getRuleContext(Join_specContext.class,0);
		}
		public TerminalNode FETCH() { return getToken(JpqlParser.FETCH, 0); }
		public Join_association_path_expressionContext join_association_path_expression() {
			return getRuleContext(Join_association_path_expressionContext.class,0);
		}
		public Fetch_joinContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fetch_join; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterFetch_join(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitFetch_join(this);
		}
	}

	public final Fetch_joinContext fetch_join() throws RecognitionException {
		Fetch_joinContext _localctx = new Fetch_joinContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_fetch_join);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(324);
			join_spec();
			setState(325);
			match(FETCH);
			setState(326);
			join_association_path_expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Join_specContext extends ParserRuleContext {
		public TerminalNode JOIN() { return getToken(JpqlParser.JOIN, 0); }
		public TerminalNode INNER() { return getToken(JpqlParser.INNER, 0); }
		public TerminalNode LEFT() { return getToken(JpqlParser.LEFT, 0); }
		public TerminalNode OUTER() { return getToken(JpqlParser.OUTER, 0); }
		public Join_specContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_join_spec; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterJoin_spec(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitJoin_spec(this);
		}
	}

	public final Join_specContext join_spec() throws RecognitionException {
		Join_specContext _localctx = new Join_specContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_join_spec);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(333);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LEFT:
				{
				{
				setState(328);
				match(LEFT);
				setState(330);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==OUTER) {
					{
					setState(329);
					match(OUTER);
					}
				}

				}
				}
				break;
			case INNER:
				{
				setState(332);
				match(INNER);
				}
				break;
			case JOIN:
				break;
			default:
				break;
			}
			setState(335);
			match(JOIN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Join_conditionContext extends ParserRuleContext {
		public TerminalNode ON() { return getToken(JpqlParser.ON, 0); }
		public Conditional_expressionContext conditional_expression() {
			return getRuleContext(Conditional_expressionContext.class,0);
		}
		public Join_conditionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_join_condition; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterJoin_condition(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitJoin_condition(this);
		}
	}

	public final Join_conditionContext join_condition() throws RecognitionException {
		Join_conditionContext _localctx = new Join_conditionContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_join_condition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(337);
			match(ON);
			setState(338);
			conditional_expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Join_association_path_expressionContext extends ParserRuleContext {
		public Join_collection_valued_path_expressionContext join_collection_valued_path_expression() {
			return getRuleContext(Join_collection_valued_path_expressionContext.class,0);
		}
		public Join_single_valued_path_expressionContext join_single_valued_path_expression() {
			return getRuleContext(Join_single_valued_path_expressionContext.class,0);
		}
		public TerminalNode TREAT() { return getToken(JpqlParser.TREAT, 0); }
		public TerminalNode AS() { return getToken(JpqlParser.AS, 0); }
		public SubtypeContext subtype() {
			return getRuleContext(SubtypeContext.class,0);
		}
		public Join_association_path_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_join_association_path_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterJoin_association_path_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitJoin_association_path_expression(this);
		}
	}

	public final Join_association_path_expressionContext join_association_path_expression() throws RecognitionException {
		Join_association_path_expressionContext _localctx = new Join_association_path_expressionContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_join_association_path_expression);
		try {
			setState(356);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,16,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(340);
				join_collection_valued_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(341);
				join_single_valued_path_expression();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(342);
				match(TREAT);
				setState(343);
				match(T__1);
				setState(344);
				join_collection_valued_path_expression();
				setState(345);
				match(AS);
				setState(346);
				subtype();
				setState(347);
				match(T__2);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(349);
				match(TREAT);
				setState(350);
				match(T__1);
				setState(351);
				join_single_valued_path_expression();
				setState(352);
				match(AS);
				setState(353);
				subtype();
				setState(354);
				match(T__2);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Join_collection_valued_path_expressionContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Collection_valued_fieldContext collection_valued_field() {
			return getRuleContext(Collection_valued_fieldContext.class,0);
		}
		public List<Single_valued_embeddable_object_fieldContext> single_valued_embeddable_object_field() {
			return getRuleContexts(Single_valued_embeddable_object_fieldContext.class);
		}
		public Single_valued_embeddable_object_fieldContext single_valued_embeddable_object_field(int i) {
			return getRuleContext(Single_valued_embeddable_object_fieldContext.class,i);
		}
		public Join_collection_valued_path_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_join_collection_valued_path_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterJoin_collection_valued_path_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitJoin_collection_valued_path_expression(this);
		}
	}

	public final Join_collection_valued_path_expressionContext join_collection_valued_path_expression() throws RecognitionException {
		Join_collection_valued_path_expressionContext _localctx = new Join_collection_valued_path_expressionContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_join_collection_valued_path_expression);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(358);
			identification_variable();
			setState(359);
			match(T__3);
			setState(365);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,17,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(360);
					single_valued_embeddable_object_field();
					setState(361);
					match(T__3);
					}
					} 
				}
				setState(367);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,17,_ctx);
			}
			setState(368);
			collection_valued_field();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Join_single_valued_path_expressionContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Single_valued_object_fieldContext single_valued_object_field() {
			return getRuleContext(Single_valued_object_fieldContext.class,0);
		}
		public List<Single_valued_embeddable_object_fieldContext> single_valued_embeddable_object_field() {
			return getRuleContexts(Single_valued_embeddable_object_fieldContext.class);
		}
		public Single_valued_embeddable_object_fieldContext single_valued_embeddable_object_field(int i) {
			return getRuleContext(Single_valued_embeddable_object_fieldContext.class,i);
		}
		public Join_single_valued_path_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_join_single_valued_path_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterJoin_single_valued_path_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitJoin_single_valued_path_expression(this);
		}
	}

	public final Join_single_valued_path_expressionContext join_single_valued_path_expression() throws RecognitionException {
		Join_single_valued_path_expressionContext _localctx = new Join_single_valued_path_expressionContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_join_single_valued_path_expression);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(370);
			identification_variable();
			setState(371);
			match(T__3);
			setState(377);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,18,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(372);
					single_valued_embeddable_object_field();
					setState(373);
					match(T__3);
					}
					} 
				}
				setState(379);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,18,_ctx);
			}
			setState(380);
			single_valued_object_field();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Collection_member_declarationContext extends ParserRuleContext {
		public TerminalNode IN() { return getToken(JpqlParser.IN, 0); }
		public Collection_valued_path_expressionContext collection_valued_path_expression() {
			return getRuleContext(Collection_valued_path_expressionContext.class,0);
		}
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public TerminalNode AS() { return getToken(JpqlParser.AS, 0); }
		public Collection_member_declarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_collection_member_declaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterCollection_member_declaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitCollection_member_declaration(this);
		}
	}

	public final Collection_member_declarationContext collection_member_declaration() throws RecognitionException {
		Collection_member_declarationContext _localctx = new Collection_member_declarationContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_collection_member_declaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(382);
			match(IN);
			setState(383);
			match(T__1);
			setState(384);
			collection_valued_path_expression();
			setState(385);
			match(T__2);
			setState(387);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(386);
				match(AS);
				}
			}

			setState(389);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Qualified_identification_variableContext extends ParserRuleContext {
		public Map_field_identification_variableContext map_field_identification_variable() {
			return getRuleContext(Map_field_identification_variableContext.class,0);
		}
		public TerminalNode ENTRY() { return getToken(JpqlParser.ENTRY, 0); }
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Qualified_identification_variableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_qualified_identification_variable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterQualified_identification_variable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitQualified_identification_variable(this);
		}
	}

	public final Qualified_identification_variableContext qualified_identification_variable() throws RecognitionException {
		Qualified_identification_variableContext _localctx = new Qualified_identification_variableContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_qualified_identification_variable);
		try {
			setState(397);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case KEY:
			case VALUE:
				enterOuterAlt(_localctx, 1);
				{
				setState(391);
				map_field_identification_variable();
				}
				break;
			case ENTRY:
				enterOuterAlt(_localctx, 2);
				{
				setState(392);
				match(ENTRY);
				setState(393);
				match(T__1);
				setState(394);
				identification_variable();
				setState(395);
				match(T__2);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Map_field_identification_variableContext extends ParserRuleContext {
		public TerminalNode KEY() { return getToken(JpqlParser.KEY, 0); }
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public TerminalNode VALUE() { return getToken(JpqlParser.VALUE, 0); }
		public Map_field_identification_variableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_map_field_identification_variable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterMap_field_identification_variable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitMap_field_identification_variable(this);
		}
	}

	public final Map_field_identification_variableContext map_field_identification_variable() throws RecognitionException {
		Map_field_identification_variableContext _localctx = new Map_field_identification_variableContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_map_field_identification_variable);
		try {
			setState(409);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case KEY:
				enterOuterAlt(_localctx, 1);
				{
				setState(399);
				match(KEY);
				setState(400);
				match(T__1);
				setState(401);
				identification_variable();
				setState(402);
				match(T__2);
				}
				break;
			case VALUE:
				enterOuterAlt(_localctx, 2);
				{
				setState(404);
				match(VALUE);
				setState(405);
				match(T__1);
				setState(406);
				identification_variable();
				setState(407);
				match(T__2);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Single_valued_path_expressionContext extends ParserRuleContext {
		public Qualified_identification_variableContext qualified_identification_variable() {
			return getRuleContext(Qualified_identification_variableContext.class,0);
		}
		public TerminalNode TREAT() { return getToken(JpqlParser.TREAT, 0); }
		public TerminalNode AS() { return getToken(JpqlParser.AS, 0); }
		public SubtypeContext subtype() {
			return getRuleContext(SubtypeContext.class,0);
		}
		public State_field_path_expressionContext state_field_path_expression() {
			return getRuleContext(State_field_path_expressionContext.class,0);
		}
		public Single_valued_object_path_expressionContext single_valued_object_path_expression() {
			return getRuleContext(Single_valued_object_path_expressionContext.class,0);
		}
		public Single_valued_path_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_single_valued_path_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSingle_valued_path_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSingle_valued_path_expression(this);
		}
	}

	public final Single_valued_path_expressionContext single_valued_path_expression() throws RecognitionException {
		Single_valued_path_expressionContext _localctx = new Single_valued_path_expressionContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_single_valued_path_expression);
		try {
			setState(421);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,22,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(411);
				qualified_identification_variable();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(412);
				match(TREAT);
				setState(413);
				match(T__1);
				setState(414);
				qualified_identification_variable();
				setState(415);
				match(AS);
				setState(416);
				subtype();
				setState(417);
				match(T__2);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(419);
				state_field_path_expression();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(420);
				single_valued_object_path_expression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class General_identification_variableContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Map_field_identification_variableContext map_field_identification_variable() {
			return getRuleContext(Map_field_identification_variableContext.class,0);
		}
		public General_identification_variableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_general_identification_variable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterGeneral_identification_variable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitGeneral_identification_variable(this);
		}
	}

	public final General_identification_variableContext general_identification_variable() throws RecognitionException {
		General_identification_variableContext _localctx = new General_identification_variableContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_general_identification_variable);
		try {
			setState(425);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,23,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(423);
				identification_variable();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(424);
				map_field_identification_variable();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class General_subpathContext extends ParserRuleContext {
		public Simple_subpathContext simple_subpath() {
			return getRuleContext(Simple_subpathContext.class,0);
		}
		public Treated_subpathContext treated_subpath() {
			return getRuleContext(Treated_subpathContext.class,0);
		}
		public List<Single_valued_object_fieldContext> single_valued_object_field() {
			return getRuleContexts(Single_valued_object_fieldContext.class);
		}
		public Single_valued_object_fieldContext single_valued_object_field(int i) {
			return getRuleContext(Single_valued_object_fieldContext.class,i);
		}
		public General_subpathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_general_subpath; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterGeneral_subpath(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitGeneral_subpath(this);
		}
	}

	public final General_subpathContext general_subpath() throws RecognitionException {
		General_subpathContext _localctx = new General_subpathContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_general_subpath);
		try {
			int _alt;
			setState(436);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AVG:
			case COUNT:
			case DATE:
			case FLOOR:
			case FROM:
			case INNER:
			case KEY:
			case LEFT:
			case MAX:
			case MIN:
			case NEW:
			case ORDER:
			case OUTER:
			case POWER:
			case SIGN:
			case SUM:
			case TIME:
			case TYPE:
			case VALUE:
			case IDENTIFICATION_VARIABLE:
				enterOuterAlt(_localctx, 1);
				{
				setState(427);
				simple_subpath();
				}
				break;
			case TREAT:
				enterOuterAlt(_localctx, 2);
				{
				setState(428);
				treated_subpath();
				setState(433);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,24,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(429);
						match(T__3);
						setState(430);
						single_valued_object_field();
						}
						} 
					}
					setState(435);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,24,_ctx);
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Simple_subpathContext extends ParserRuleContext {
		public General_identification_variableContext general_identification_variable() {
			return getRuleContext(General_identification_variableContext.class,0);
		}
		public List<Single_valued_object_fieldContext> single_valued_object_field() {
			return getRuleContexts(Single_valued_object_fieldContext.class);
		}
		public Single_valued_object_fieldContext single_valued_object_field(int i) {
			return getRuleContext(Single_valued_object_fieldContext.class,i);
		}
		public Simple_subpathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simple_subpath; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSimple_subpath(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSimple_subpath(this);
		}
	}

	public final Simple_subpathContext simple_subpath() throws RecognitionException {
		Simple_subpathContext _localctx = new Simple_subpathContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_simple_subpath);
		try {
			int _alt;
			setState(447);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,27,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(438);
				general_identification_variable();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(439);
				general_identification_variable();
				setState(444);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,26,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(440);
						match(T__3);
						setState(441);
						single_valued_object_field();
						}
						} 
					}
					setState(446);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,26,_ctx);
				}
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Treated_subpathContext extends ParserRuleContext {
		public TerminalNode TREAT() { return getToken(JpqlParser.TREAT, 0); }
		public General_subpathContext general_subpath() {
			return getRuleContext(General_subpathContext.class,0);
		}
		public TerminalNode AS() { return getToken(JpqlParser.AS, 0); }
		public SubtypeContext subtype() {
			return getRuleContext(SubtypeContext.class,0);
		}
		public Treated_subpathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_treated_subpath; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterTreated_subpath(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitTreated_subpath(this);
		}
	}

	public final Treated_subpathContext treated_subpath() throws RecognitionException {
		Treated_subpathContext _localctx = new Treated_subpathContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_treated_subpath);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(449);
			match(TREAT);
			setState(450);
			match(T__1);
			setState(451);
			general_subpath();
			setState(452);
			match(AS);
			setState(453);
			subtype();
			setState(454);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class State_field_path_expressionContext extends ParserRuleContext {
		public General_subpathContext general_subpath() {
			return getRuleContext(General_subpathContext.class,0);
		}
		public State_fieldContext state_field() {
			return getRuleContext(State_fieldContext.class,0);
		}
		public State_field_path_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_state_field_path_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterState_field_path_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitState_field_path_expression(this);
		}
	}

	public final State_field_path_expressionContext state_field_path_expression() throws RecognitionException {
		State_field_path_expressionContext _localctx = new State_field_path_expressionContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_state_field_path_expression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(456);
			general_subpath();
			setState(457);
			match(T__3);
			setState(458);
			state_field();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class State_valued_path_expressionContext extends ParserRuleContext {
		public State_field_path_expressionContext state_field_path_expression() {
			return getRuleContext(State_field_path_expressionContext.class,0);
		}
		public General_identification_variableContext general_identification_variable() {
			return getRuleContext(General_identification_variableContext.class,0);
		}
		public State_valued_path_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_state_valued_path_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterState_valued_path_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitState_valued_path_expression(this);
		}
	}

	public final State_valued_path_expressionContext state_valued_path_expression() throws RecognitionException {
		State_valued_path_expressionContext _localctx = new State_valued_path_expressionContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_state_valued_path_expression);
		try {
			setState(462);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,28,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(460);
				state_field_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(461);
				general_identification_variable();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Single_valued_object_path_expressionContext extends ParserRuleContext {
		public General_subpathContext general_subpath() {
			return getRuleContext(General_subpathContext.class,0);
		}
		public Single_valued_object_fieldContext single_valued_object_field() {
			return getRuleContext(Single_valued_object_fieldContext.class,0);
		}
		public Single_valued_object_path_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_single_valued_object_path_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSingle_valued_object_path_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSingle_valued_object_path_expression(this);
		}
	}

	public final Single_valued_object_path_expressionContext single_valued_object_path_expression() throws RecognitionException {
		Single_valued_object_path_expressionContext _localctx = new Single_valued_object_path_expressionContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_single_valued_object_path_expression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(464);
			general_subpath();
			setState(465);
			match(T__3);
			setState(466);
			single_valued_object_field();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Collection_valued_path_expressionContext extends ParserRuleContext {
		public General_subpathContext general_subpath() {
			return getRuleContext(General_subpathContext.class,0);
		}
		public Collection_value_fieldContext collection_value_field() {
			return getRuleContext(Collection_value_fieldContext.class,0);
		}
		public Collection_valued_path_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_collection_valued_path_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterCollection_valued_path_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitCollection_valued_path_expression(this);
		}
	}

	public final Collection_valued_path_expressionContext collection_valued_path_expression() throws RecognitionException {
		Collection_valued_path_expressionContext _localctx = new Collection_valued_path_expressionContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_collection_valued_path_expression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(468);
			general_subpath();
			setState(469);
			match(T__3);
			setState(470);
			collection_value_field();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Update_clauseContext extends ParserRuleContext {
		public TerminalNode UPDATE() { return getToken(JpqlParser.UPDATE, 0); }
		public Entity_nameContext entity_name() {
			return getRuleContext(Entity_nameContext.class,0);
		}
		public TerminalNode SET() { return getToken(JpqlParser.SET, 0); }
		public List<Update_itemContext> update_item() {
			return getRuleContexts(Update_itemContext.class);
		}
		public Update_itemContext update_item(int i) {
			return getRuleContext(Update_itemContext.class,i);
		}
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public TerminalNode AS() { return getToken(JpqlParser.AS, 0); }
		public Update_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_update_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterUpdate_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitUpdate_clause(this);
		}
	}

	public final Update_clauseContext update_clause() throws RecognitionException {
		Update_clauseContext _localctx = new Update_clauseContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_update_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(472);
			match(UPDATE);
			setState(473);
			entity_name();
			setState(478);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 5912100447348326400L) != 0) || ((((_la - 69)) & ~0x3f) == 0 && ((1L << (_la - 69)) & 559461724181L) != 0)) {
				{
				setState(475);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==AS) {
					{
					setState(474);
					match(AS);
					}
				}

				setState(477);
				identification_variable();
				}
			}

			setState(480);
			match(SET);
			setState(481);
			update_item();
			setState(486);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(482);
				match(T__0);
				setState(483);
				update_item();
				}
				}
				setState(488);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Update_itemContext extends ParserRuleContext {
		public TerminalNode EQUAL() { return getToken(JpqlParser.EQUAL, 0); }
		public New_valueContext new_value() {
			return getRuleContext(New_valueContext.class,0);
		}
		public State_fieldContext state_field() {
			return getRuleContext(State_fieldContext.class,0);
		}
		public Single_valued_object_fieldContext single_valued_object_field() {
			return getRuleContext(Single_valued_object_fieldContext.class,0);
		}
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public List<Single_valued_embeddable_object_fieldContext> single_valued_embeddable_object_field() {
			return getRuleContexts(Single_valued_embeddable_object_fieldContext.class);
		}
		public Single_valued_embeddable_object_fieldContext single_valued_embeddable_object_field(int i) {
			return getRuleContext(Single_valued_embeddable_object_fieldContext.class,i);
		}
		public Update_itemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_update_item; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterUpdate_item(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitUpdate_item(this);
		}
	}

	public final Update_itemContext update_item() throws RecognitionException {
		Update_itemContext _localctx = new Update_itemContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_update_item);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(492);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,32,_ctx) ) {
			case 1:
				{
				setState(489);
				identification_variable();
				setState(490);
				match(T__3);
				}
				break;
			}
			setState(499);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,33,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(494);
					single_valued_embeddable_object_field();
					setState(495);
					match(T__3);
					}
					} 
				}
				setState(501);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,33,_ctx);
			}
			setState(504);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,34,_ctx) ) {
			case 1:
				{
				setState(502);
				state_field();
				}
				break;
			case 2:
				{
				setState(503);
				single_valued_object_field();
				}
				break;
			}
			setState(506);
			match(EQUAL);
			setState(507);
			new_value();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class New_valueContext extends ParserRuleContext {
		public Scalar_expressionContext scalar_expression() {
			return getRuleContext(Scalar_expressionContext.class,0);
		}
		public Simple_entity_expressionContext simple_entity_expression() {
			return getRuleContext(Simple_entity_expressionContext.class,0);
		}
		public TerminalNode NULL() { return getToken(JpqlParser.NULL, 0); }
		public New_valueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_new_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterNew_value(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitNew_value(this);
		}
	}

	public final New_valueContext new_value() throws RecognitionException {
		New_valueContext _localctx = new New_valueContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_new_value);
		try {
			setState(512);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,35,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(509);
				scalar_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(510);
				simple_entity_expression();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(511);
				match(NULL);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Delete_clauseContext extends ParserRuleContext {
		public TerminalNode DELETE() { return getToken(JpqlParser.DELETE, 0); }
		public TerminalNode FROM() { return getToken(JpqlParser.FROM, 0); }
		public Entity_nameContext entity_name() {
			return getRuleContext(Entity_nameContext.class,0);
		}
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public TerminalNode AS() { return getToken(JpqlParser.AS, 0); }
		public Delete_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_delete_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterDelete_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitDelete_clause(this);
		}
	}

	public final Delete_clauseContext delete_clause() throws RecognitionException {
		Delete_clauseContext _localctx = new Delete_clauseContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_delete_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(514);
			match(DELETE);
			setState(515);
			match(FROM);
			setState(516);
			entity_name();
			setState(521);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 5912100447348326400L) != 0) || ((((_la - 69)) & ~0x3f) == 0 && ((1L << (_la - 69)) & 559461724181L) != 0)) {
				{
				setState(518);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==AS) {
					{
					setState(517);
					match(AS);
					}
				}

				setState(520);
				identification_variable();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Select_clauseContext extends ParserRuleContext {
		public TerminalNode SELECT() { return getToken(JpqlParser.SELECT, 0); }
		public List<Select_itemContext> select_item() {
			return getRuleContexts(Select_itemContext.class);
		}
		public Select_itemContext select_item(int i) {
			return getRuleContext(Select_itemContext.class,i);
		}
		public TerminalNode DISTINCT() { return getToken(JpqlParser.DISTINCT, 0); }
		public Select_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_select_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSelect_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSelect_clause(this);
		}
	}

	public final Select_clauseContext select_clause() throws RecognitionException {
		Select_clauseContext _localctx = new Select_clauseContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_select_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(523);
			match(SELECT);
			setState(525);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DISTINCT) {
				{
				setState(524);
				match(DISTINCT);
				}
			}

			setState(527);
			select_item();
			setState(532);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(528);
				match(T__0);
				setState(529);
				select_item();
				}
				}
				setState(534);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Select_itemContext extends ParserRuleContext {
		public Select_expressionContext select_expression() {
			return getRuleContext(Select_expressionContext.class,0);
		}
		public Result_variableContext result_variable() {
			return getRuleContext(Result_variableContext.class,0);
		}
		public TerminalNode AS() { return getToken(JpqlParser.AS, 0); }
		public Select_itemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_select_item; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSelect_item(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSelect_item(this);
		}
	}

	public final Select_itemContext select_item() throws RecognitionException {
		Select_itemContext _localctx = new Select_itemContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_select_item);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(535);
			select_expression();
			setState(540);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,41,_ctx) ) {
			case 1:
				{
				setState(537);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==AS) {
					{
					setState(536);
					match(AS);
					}
				}

				setState(539);
				result_variable();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Select_expressionContext extends ParserRuleContext {
		public Single_valued_path_expressionContext single_valued_path_expression() {
			return getRuleContext(Single_valued_path_expressionContext.class,0);
		}
		public Scalar_expressionContext scalar_expression() {
			return getRuleContext(Scalar_expressionContext.class,0);
		}
		public Aggregate_expressionContext aggregate_expression() {
			return getRuleContext(Aggregate_expressionContext.class,0);
		}
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public TerminalNode OBJECT() { return getToken(JpqlParser.OBJECT, 0); }
		public Constructor_expressionContext constructor_expression() {
			return getRuleContext(Constructor_expressionContext.class,0);
		}
		public Select_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_select_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSelect_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSelect_expression(this);
		}
	}

	public final Select_expressionContext select_expression() throws RecognitionException {
		Select_expressionContext _localctx = new Select_expressionContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_select_expression);
		try {
			setState(552);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,42,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(542);
				single_valued_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(543);
				scalar_expression();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(544);
				aggregate_expression();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(545);
				identification_variable();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(546);
				match(OBJECT);
				setState(547);
				match(T__1);
				setState(548);
				identification_variable();
				setState(549);
				match(T__2);
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(551);
				constructor_expression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Constructor_expressionContext extends ParserRuleContext {
		public TerminalNode NEW() { return getToken(JpqlParser.NEW, 0); }
		public Constructor_nameContext constructor_name() {
			return getRuleContext(Constructor_nameContext.class,0);
		}
		public List<Constructor_itemContext> constructor_item() {
			return getRuleContexts(Constructor_itemContext.class);
		}
		public Constructor_itemContext constructor_item(int i) {
			return getRuleContext(Constructor_itemContext.class,i);
		}
		public Constructor_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constructor_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterConstructor_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitConstructor_expression(this);
		}
	}

	public final Constructor_expressionContext constructor_expression() throws RecognitionException {
		Constructor_expressionContext _localctx = new Constructor_expressionContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_constructor_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(554);
			match(NEW);
			setState(555);
			constructor_name();
			setState(556);
			match(T__1);
			setState(557);
			constructor_item();
			setState(562);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(558);
				match(T__0);
				setState(559);
				constructor_item();
				}
				}
				setState(564);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(565);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Constructor_itemContext extends ParserRuleContext {
		public Single_valued_path_expressionContext single_valued_path_expression() {
			return getRuleContext(Single_valued_path_expressionContext.class,0);
		}
		public Scalar_expressionContext scalar_expression() {
			return getRuleContext(Scalar_expressionContext.class,0);
		}
		public Aggregate_expressionContext aggregate_expression() {
			return getRuleContext(Aggregate_expressionContext.class,0);
		}
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public Constructor_itemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constructor_item; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterConstructor_item(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitConstructor_item(this);
		}
	}

	public final Constructor_itemContext constructor_item() throws RecognitionException {
		Constructor_itemContext _localctx = new Constructor_itemContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_constructor_item);
		try {
			setState(572);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,44,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(567);
				single_valued_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(568);
				scalar_expression();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(569);
				aggregate_expression();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(570);
				identification_variable();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(571);
				literal();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Aggregate_expressionContext extends ParserRuleContext {
		public State_valued_path_expressionContext state_valued_path_expression() {
			return getRuleContext(State_valued_path_expressionContext.class,0);
		}
		public TerminalNode AVG() { return getToken(JpqlParser.AVG, 0); }
		public TerminalNode MAX() { return getToken(JpqlParser.MAX, 0); }
		public TerminalNode MIN() { return getToken(JpqlParser.MIN, 0); }
		public TerminalNode SUM() { return getToken(JpqlParser.SUM, 0); }
		public TerminalNode DISTINCT() { return getToken(JpqlParser.DISTINCT, 0); }
		public TerminalNode COUNT() { return getToken(JpqlParser.COUNT, 0); }
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Single_valued_object_path_expressionContext single_valued_object_path_expression() {
			return getRuleContext(Single_valued_object_path_expressionContext.class,0);
		}
		public Function_invocationContext function_invocation() {
			return getRuleContext(Function_invocationContext.class,0);
		}
		public Aggregate_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_aggregate_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterAggregate_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitAggregate_expression(this);
		}
	}

	public final Aggregate_expressionContext aggregate_expression() throws RecognitionException {
		Aggregate_expressionContext _localctx = new Aggregate_expressionContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_aggregate_expression);
		int _la;
		try {
			setState(595);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AVG:
			case MAX:
			case MIN:
			case SUM:
				enterOuterAlt(_localctx, 1);
				{
				setState(574);
				_la = _input.LA(1);
				if ( !(_la==AVG || ((((_la - 69)) & ~0x3f) == 0 && ((1L << (_la - 69)) & 8388613L) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(575);
				match(T__1);
				setState(577);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==DISTINCT) {
					{
					setState(576);
					match(DISTINCT);
					}
				}

				setState(579);
				state_valued_path_expression();
				setState(580);
				match(T__2);
				}
				break;
			case COUNT:
				enterOuterAlt(_localctx, 2);
				{
				setState(582);
				match(COUNT);
				setState(583);
				match(T__1);
				setState(585);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==DISTINCT) {
					{
					setState(584);
					match(DISTINCT);
					}
				}

				setState(590);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,47,_ctx) ) {
				case 1:
					{
					setState(587);
					identification_variable();
					}
					break;
				case 2:
					{
					setState(588);
					state_valued_path_expression();
					}
					break;
				case 3:
					{
					setState(589);
					single_valued_object_path_expression();
					}
					break;
				}
				setState(592);
				match(T__2);
				}
				break;
			case FUNCTION:
				enterOuterAlt(_localctx, 3);
				{
				setState(594);
				function_invocation();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Where_clauseContext extends ParserRuleContext {
		public TerminalNode WHERE() { return getToken(JpqlParser.WHERE, 0); }
		public Conditional_expressionContext conditional_expression() {
			return getRuleContext(Conditional_expressionContext.class,0);
		}
		public Where_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_where_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterWhere_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitWhere_clause(this);
		}
	}

	public final Where_clauseContext where_clause() throws RecognitionException {
		Where_clauseContext _localctx = new Where_clauseContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_where_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(597);
			match(WHERE);
			setState(598);
			conditional_expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Groupby_clauseContext extends ParserRuleContext {
		public TerminalNode GROUP() { return getToken(JpqlParser.GROUP, 0); }
		public TerminalNode BY() { return getToken(JpqlParser.BY, 0); }
		public List<Groupby_itemContext> groupby_item() {
			return getRuleContexts(Groupby_itemContext.class);
		}
		public Groupby_itemContext groupby_item(int i) {
			return getRuleContext(Groupby_itemContext.class,i);
		}
		public Groupby_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_groupby_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterGroupby_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitGroupby_clause(this);
		}
	}

	public final Groupby_clauseContext groupby_clause() throws RecognitionException {
		Groupby_clauseContext _localctx = new Groupby_clauseContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_groupby_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(600);
			match(GROUP);
			setState(601);
			match(BY);
			setState(602);
			groupby_item();
			setState(607);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(603);
				match(T__0);
				setState(604);
				groupby_item();
				}
				}
				setState(609);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Groupby_itemContext extends ParserRuleContext {
		public Single_valued_path_expressionContext single_valued_path_expression() {
			return getRuleContext(Single_valued_path_expressionContext.class,0);
		}
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Groupby_itemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_groupby_item; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterGroupby_item(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitGroupby_item(this);
		}
	}

	public final Groupby_itemContext groupby_item() throws RecognitionException {
		Groupby_itemContext _localctx = new Groupby_itemContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_groupby_item);
		try {
			setState(612);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,50,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(610);
				single_valued_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(611);
				identification_variable();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Having_clauseContext extends ParserRuleContext {
		public TerminalNode HAVING() { return getToken(JpqlParser.HAVING, 0); }
		public Conditional_expressionContext conditional_expression() {
			return getRuleContext(Conditional_expressionContext.class,0);
		}
		public Having_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_having_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterHaving_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitHaving_clause(this);
		}
	}

	public final Having_clauseContext having_clause() throws RecognitionException {
		Having_clauseContext _localctx = new Having_clauseContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_having_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(614);
			match(HAVING);
			setState(615);
			conditional_expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Orderby_clauseContext extends ParserRuleContext {
		public TerminalNode ORDER() { return getToken(JpqlParser.ORDER, 0); }
		public TerminalNode BY() { return getToken(JpqlParser.BY, 0); }
		public List<Orderby_itemContext> orderby_item() {
			return getRuleContexts(Orderby_itemContext.class);
		}
		public Orderby_itemContext orderby_item(int i) {
			return getRuleContext(Orderby_itemContext.class,i);
		}
		public Orderby_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_orderby_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterOrderby_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitOrderby_clause(this);
		}
	}

	public final Orderby_clauseContext orderby_clause() throws RecognitionException {
		Orderby_clauseContext _localctx = new Orderby_clauseContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_orderby_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(617);
			match(ORDER);
			setState(618);
			match(BY);
			setState(619);
			orderby_item();
			setState(624);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(620);
				match(T__0);
				setState(621);
				orderby_item();
				}
				}
				setState(626);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Orderby_itemContext extends ParserRuleContext {
		public State_field_path_expressionContext state_field_path_expression() {
			return getRuleContext(State_field_path_expressionContext.class,0);
		}
		public General_identification_variableContext general_identification_variable() {
			return getRuleContext(General_identification_variableContext.class,0);
		}
		public Result_variableContext result_variable() {
			return getRuleContext(Result_variableContext.class,0);
		}
		public TerminalNode ASC() { return getToken(JpqlParser.ASC, 0); }
		public TerminalNode DESC() { return getToken(JpqlParser.DESC, 0); }
		public Orderby_itemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_orderby_item; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterOrderby_item(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitOrderby_item(this);
		}
	}

	public final Orderby_itemContext orderby_item() throws RecognitionException {
		Orderby_itemContext _localctx = new Orderby_itemContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_orderby_item);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(630);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,52,_ctx) ) {
			case 1:
				{
				setState(627);
				state_field_path_expression();
				}
				break;
			case 2:
				{
				setState(628);
				general_identification_variable();
				}
				break;
			case 3:
				{
				setState(629);
				result_variable();
				}
				break;
			}
			setState(633);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASC || _la==DESC) {
				{
				setState(632);
				_la = _input.LA(1);
				if ( !(_la==ASC || _la==DESC) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SubqueryContext extends ParserRuleContext {
		public Simple_select_clauseContext simple_select_clause() {
			return getRuleContext(Simple_select_clauseContext.class,0);
		}
		public Subquery_from_clauseContext subquery_from_clause() {
			return getRuleContext(Subquery_from_clauseContext.class,0);
		}
		public Where_clauseContext where_clause() {
			return getRuleContext(Where_clauseContext.class,0);
		}
		public Groupby_clauseContext groupby_clause() {
			return getRuleContext(Groupby_clauseContext.class,0);
		}
		public Having_clauseContext having_clause() {
			return getRuleContext(Having_clauseContext.class,0);
		}
		public SubqueryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_subquery; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSubquery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSubquery(this);
		}
	}

	public final SubqueryContext subquery() throws RecognitionException {
		SubqueryContext _localctx = new SubqueryContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_subquery);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(635);
			simple_select_clause();
			setState(636);
			subquery_from_clause();
			setState(638);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(637);
				where_clause();
				}
			}

			setState(641);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==GROUP) {
				{
				setState(640);
				groupby_clause();
				}
			}

			setState(644);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==HAVING) {
				{
				setState(643);
				having_clause();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Subquery_from_clauseContext extends ParserRuleContext {
		public TerminalNode FROM() { return getToken(JpqlParser.FROM, 0); }
		public List<Subselect_identification_variable_declarationContext> subselect_identification_variable_declaration() {
			return getRuleContexts(Subselect_identification_variable_declarationContext.class);
		}
		public Subselect_identification_variable_declarationContext subselect_identification_variable_declaration(int i) {
			return getRuleContext(Subselect_identification_variable_declarationContext.class,i);
		}
		public List<Collection_member_declarationContext> collection_member_declaration() {
			return getRuleContexts(Collection_member_declarationContext.class);
		}
		public Collection_member_declarationContext collection_member_declaration(int i) {
			return getRuleContext(Collection_member_declarationContext.class,i);
		}
		public Subquery_from_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_subquery_from_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSubquery_from_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSubquery_from_clause(this);
		}
	}

	public final Subquery_from_clauseContext subquery_from_clause() throws RecognitionException {
		Subquery_from_clauseContext _localctx = new Subquery_from_clauseContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_subquery_from_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(646);
			match(FROM);
			setState(647);
			subselect_identification_variable_declaration();
			setState(655);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(648);
				match(T__0);
				setState(651);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,57,_ctx) ) {
				case 1:
					{
					setState(649);
					subselect_identification_variable_declaration();
					}
					break;
				case 2:
					{
					setState(650);
					collection_member_declaration();
					}
					break;
				}
				}
				}
				setState(657);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Subselect_identification_variable_declarationContext extends ParserRuleContext {
		public Identification_variable_declarationContext identification_variable_declaration() {
			return getRuleContext(Identification_variable_declarationContext.class,0);
		}
		public Derived_path_expressionContext derived_path_expression() {
			return getRuleContext(Derived_path_expressionContext.class,0);
		}
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public TerminalNode AS() { return getToken(JpqlParser.AS, 0); }
		public List<JoinContext> join() {
			return getRuleContexts(JoinContext.class);
		}
		public JoinContext join(int i) {
			return getRuleContext(JoinContext.class,i);
		}
		public Derived_collection_member_declarationContext derived_collection_member_declaration() {
			return getRuleContext(Derived_collection_member_declarationContext.class,0);
		}
		public Subselect_identification_variable_declarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_subselect_identification_variable_declaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSubselect_identification_variable_declaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSubselect_identification_variable_declaration(this);
		}
	}

	public final Subselect_identification_variable_declarationContext subselect_identification_variable_declaration() throws RecognitionException {
		Subselect_identification_variable_declarationContext _localctx = new Subselect_identification_variable_declarationContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_subselect_identification_variable_declaration);
		int _la;
		try {
			setState(671);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,61,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(658);
				identification_variable_declaration();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(659);
				derived_path_expression();
				setState(661);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==AS) {
					{
					setState(660);
					match(AS);
					}
				}

				setState(663);
				identification_variable();
				setState(667);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 5332261958806667264L) != 0)) {
					{
					{
					setState(664);
					join();
					}
					}
					setState(669);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(670);
				derived_collection_member_declaration();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Derived_path_expressionContext extends ParserRuleContext {
		public General_derived_pathContext general_derived_path() {
			return getRuleContext(General_derived_pathContext.class,0);
		}
		public Single_valued_object_fieldContext single_valued_object_field() {
			return getRuleContext(Single_valued_object_fieldContext.class,0);
		}
		public Collection_valued_fieldContext collection_valued_field() {
			return getRuleContext(Collection_valued_fieldContext.class,0);
		}
		public Derived_path_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_derived_path_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterDerived_path_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitDerived_path_expression(this);
		}
	}

	public final Derived_path_expressionContext derived_path_expression() throws RecognitionException {
		Derived_path_expressionContext _localctx = new Derived_path_expressionContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_derived_path_expression);
		try {
			setState(681);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,62,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(673);
				general_derived_path();
				setState(674);
				match(T__3);
				setState(675);
				single_valued_object_field();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(677);
				general_derived_path();
				setState(678);
				match(T__3);
				setState(679);
				collection_valued_field();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class General_derived_pathContext extends ParserRuleContext {
		public Simple_derived_pathContext simple_derived_path() {
			return getRuleContext(Simple_derived_pathContext.class,0);
		}
		public Treated_derived_pathContext treated_derived_path() {
			return getRuleContext(Treated_derived_pathContext.class,0);
		}
		public List<Single_valued_object_fieldContext> single_valued_object_field() {
			return getRuleContexts(Single_valued_object_fieldContext.class);
		}
		public Single_valued_object_fieldContext single_valued_object_field(int i) {
			return getRuleContext(Single_valued_object_fieldContext.class,i);
		}
		public General_derived_pathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_general_derived_path; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterGeneral_derived_path(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitGeneral_derived_path(this);
		}
	}

	public final General_derived_pathContext general_derived_path() throws RecognitionException {
		General_derived_pathContext _localctx = new General_derived_pathContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_general_derived_path);
		try {
			int _alt;
			setState(692);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AVG:
			case COUNT:
			case DATE:
			case FLOOR:
			case FROM:
			case INNER:
			case KEY:
			case LEFT:
			case MAX:
			case MIN:
			case NEW:
			case ORDER:
			case OUTER:
			case POWER:
			case SIGN:
			case SUM:
			case TIME:
			case TYPE:
			case VALUE:
			case IDENTIFICATION_VARIABLE:
				enterOuterAlt(_localctx, 1);
				{
				setState(683);
				simple_derived_path();
				}
				break;
			case TREAT:
				enterOuterAlt(_localctx, 2);
				{
				setState(684);
				treated_derived_path();
				setState(689);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,63,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(685);
						match(T__3);
						setState(686);
						single_valued_object_field();
						}
						} 
					}
					setState(691);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,63,_ctx);
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Simple_derived_pathContext extends ParserRuleContext {
		public Superquery_identification_variableContext superquery_identification_variable() {
			return getRuleContext(Superquery_identification_variableContext.class,0);
		}
		public List<Single_valued_object_fieldContext> single_valued_object_field() {
			return getRuleContexts(Single_valued_object_fieldContext.class);
		}
		public Single_valued_object_fieldContext single_valued_object_field(int i) {
			return getRuleContext(Single_valued_object_fieldContext.class,i);
		}
		public Simple_derived_pathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simple_derived_path; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSimple_derived_path(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSimple_derived_path(this);
		}
	}

	public final Simple_derived_pathContext simple_derived_path() throws RecognitionException {
		Simple_derived_pathContext _localctx = new Simple_derived_pathContext(_ctx, getState());
		enterRule(_localctx, 98, RULE_simple_derived_path);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(694);
			superquery_identification_variable();
			setState(699);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,65,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(695);
					match(T__3);
					setState(696);
					single_valued_object_field();
					}
					} 
				}
				setState(701);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,65,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Treated_derived_pathContext extends ParserRuleContext {
		public TerminalNode TREAT() { return getToken(JpqlParser.TREAT, 0); }
		public General_derived_pathContext general_derived_path() {
			return getRuleContext(General_derived_pathContext.class,0);
		}
		public TerminalNode AS() { return getToken(JpqlParser.AS, 0); }
		public SubtypeContext subtype() {
			return getRuleContext(SubtypeContext.class,0);
		}
		public Treated_derived_pathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_treated_derived_path; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterTreated_derived_path(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitTreated_derived_path(this);
		}
	}

	public final Treated_derived_pathContext treated_derived_path() throws RecognitionException {
		Treated_derived_pathContext _localctx = new Treated_derived_pathContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_treated_derived_path);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(702);
			match(TREAT);
			setState(703);
			match(T__1);
			setState(704);
			general_derived_path();
			setState(705);
			match(AS);
			setState(706);
			subtype();
			setState(707);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Derived_collection_member_declarationContext extends ParserRuleContext {
		public TerminalNode IN() { return getToken(JpqlParser.IN, 0); }
		public Superquery_identification_variableContext superquery_identification_variable() {
			return getRuleContext(Superquery_identification_variableContext.class,0);
		}
		public Collection_valued_fieldContext collection_valued_field() {
			return getRuleContext(Collection_valued_fieldContext.class,0);
		}
		public List<Single_valued_object_fieldContext> single_valued_object_field() {
			return getRuleContexts(Single_valued_object_fieldContext.class);
		}
		public Single_valued_object_fieldContext single_valued_object_field(int i) {
			return getRuleContext(Single_valued_object_fieldContext.class,i);
		}
		public Derived_collection_member_declarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_derived_collection_member_declaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterDerived_collection_member_declaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitDerived_collection_member_declaration(this);
		}
	}

	public final Derived_collection_member_declarationContext derived_collection_member_declaration() throws RecognitionException {
		Derived_collection_member_declarationContext _localctx = new Derived_collection_member_declarationContext(_ctx, getState());
		enterRule(_localctx, 102, RULE_derived_collection_member_declaration);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(709);
			match(IN);
			setState(710);
			superquery_identification_variable();
			setState(711);
			match(T__3);
			setState(717);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,66,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(712);
					single_valued_object_field();
					setState(713);
					match(T__3);
					}
					} 
				}
				setState(719);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,66,_ctx);
			}
			setState(720);
			collection_valued_field();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Simple_select_clauseContext extends ParserRuleContext {
		public TerminalNode SELECT() { return getToken(JpqlParser.SELECT, 0); }
		public Simple_select_expressionContext simple_select_expression() {
			return getRuleContext(Simple_select_expressionContext.class,0);
		}
		public TerminalNode DISTINCT() { return getToken(JpqlParser.DISTINCT, 0); }
		public Simple_select_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simple_select_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSimple_select_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSimple_select_clause(this);
		}
	}

	public final Simple_select_clauseContext simple_select_clause() throws RecognitionException {
		Simple_select_clauseContext _localctx = new Simple_select_clauseContext(_ctx, getState());
		enterRule(_localctx, 104, RULE_simple_select_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(722);
			match(SELECT);
			setState(724);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DISTINCT) {
				{
				setState(723);
				match(DISTINCT);
				}
			}

			setState(726);
			simple_select_expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Simple_select_expressionContext extends ParserRuleContext {
		public Single_valued_path_expressionContext single_valued_path_expression() {
			return getRuleContext(Single_valued_path_expressionContext.class,0);
		}
		public Scalar_expressionContext scalar_expression() {
			return getRuleContext(Scalar_expressionContext.class,0);
		}
		public Aggregate_expressionContext aggregate_expression() {
			return getRuleContext(Aggregate_expressionContext.class,0);
		}
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Simple_select_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simple_select_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSimple_select_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSimple_select_expression(this);
		}
	}

	public final Simple_select_expressionContext simple_select_expression() throws RecognitionException {
		Simple_select_expressionContext _localctx = new Simple_select_expressionContext(_ctx, getState());
		enterRule(_localctx, 106, RULE_simple_select_expression);
		try {
			setState(732);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,68,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(728);
				single_valued_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(729);
				scalar_expression();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(730);
				aggregate_expression();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(731);
				identification_variable();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Scalar_expressionContext extends ParserRuleContext {
		public Arithmetic_expressionContext arithmetic_expression() {
			return getRuleContext(Arithmetic_expressionContext.class,0);
		}
		public String_expressionContext string_expression() {
			return getRuleContext(String_expressionContext.class,0);
		}
		public Enum_expressionContext enum_expression() {
			return getRuleContext(Enum_expressionContext.class,0);
		}
		public Datetime_expressionContext datetime_expression() {
			return getRuleContext(Datetime_expressionContext.class,0);
		}
		public Boolean_expressionContext boolean_expression() {
			return getRuleContext(Boolean_expressionContext.class,0);
		}
		public Case_expressionContext case_expression() {
			return getRuleContext(Case_expressionContext.class,0);
		}
		public Entity_type_expressionContext entity_type_expression() {
			return getRuleContext(Entity_type_expressionContext.class,0);
		}
		public Scalar_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_scalar_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterScalar_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitScalar_expression(this);
		}
	}

	public final Scalar_expressionContext scalar_expression() throws RecognitionException {
		Scalar_expressionContext _localctx = new Scalar_expressionContext(_ctx, getState());
		enterRule(_localctx, 108, RULE_scalar_expression);
		try {
			setState(741);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,69,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(734);
				arithmetic_expression(0);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(735);
				string_expression();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(736);
				enum_expression();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(737);
				datetime_expression();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(738);
				boolean_expression();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(739);
				case_expression();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(740);
				entity_type_expression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Conditional_expressionContext extends ParserRuleContext {
		public Conditional_termContext conditional_term() {
			return getRuleContext(Conditional_termContext.class,0);
		}
		public Conditional_expressionContext conditional_expression() {
			return getRuleContext(Conditional_expressionContext.class,0);
		}
		public TerminalNode OR() { return getToken(JpqlParser.OR, 0); }
		public Conditional_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_conditional_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterConditional_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitConditional_expression(this);
		}
	}

	public final Conditional_expressionContext conditional_expression() throws RecognitionException {
		return conditional_expression(0);
	}

	private Conditional_expressionContext conditional_expression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		Conditional_expressionContext _localctx = new Conditional_expressionContext(_ctx, _parentState);
		Conditional_expressionContext _prevctx = _localctx;
		int _startState = 110;
		enterRecursionRule(_localctx, 110, RULE_conditional_expression, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(744);
			conditional_term(0);
			}
			_ctx.stop = _input.LT(-1);
			setState(751);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,70,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new Conditional_expressionContext(_parentctx, _parentState);
					pushNewRecursionContext(_localctx, _startState, RULE_conditional_expression);
					setState(746);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(747);
					match(OR);
					setState(748);
					conditional_term(0);
					}
					} 
				}
				setState(753);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,70,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Conditional_termContext extends ParserRuleContext {
		public Conditional_factorContext conditional_factor() {
			return getRuleContext(Conditional_factorContext.class,0);
		}
		public Conditional_termContext conditional_term() {
			return getRuleContext(Conditional_termContext.class,0);
		}
		public TerminalNode AND() { return getToken(JpqlParser.AND, 0); }
		public Conditional_termContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_conditional_term; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterConditional_term(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitConditional_term(this);
		}
	}

	public final Conditional_termContext conditional_term() throws RecognitionException {
		return conditional_term(0);
	}

	private Conditional_termContext conditional_term(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		Conditional_termContext _localctx = new Conditional_termContext(_ctx, _parentState);
		Conditional_termContext _prevctx = _localctx;
		int _startState = 112;
		enterRecursionRule(_localctx, 112, RULE_conditional_term, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(755);
			conditional_factor();
			}
			_ctx.stop = _input.LT(-1);
			setState(762);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,71,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new Conditional_termContext(_parentctx, _parentState);
					pushNewRecursionContext(_localctx, _startState, RULE_conditional_term);
					setState(757);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(758);
					match(AND);
					setState(759);
					conditional_factor();
					}
					} 
				}
				setState(764);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,71,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Conditional_factorContext extends ParserRuleContext {
		public Conditional_primaryContext conditional_primary() {
			return getRuleContext(Conditional_primaryContext.class,0);
		}
		public TerminalNode NOT() { return getToken(JpqlParser.NOT, 0); }
		public Conditional_factorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_conditional_factor; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterConditional_factor(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitConditional_factor(this);
		}
	}

	public final Conditional_factorContext conditional_factor() throws RecognitionException {
		Conditional_factorContext _localctx = new Conditional_factorContext(_ctx, getState());
		enterRule(_localctx, 114, RULE_conditional_factor);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(766);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,72,_ctx) ) {
			case 1:
				{
				setState(765);
				match(NOT);
				}
				break;
			}
			setState(768);
			conditional_primary();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Conditional_primaryContext extends ParserRuleContext {
		public Simple_cond_expressionContext simple_cond_expression() {
			return getRuleContext(Simple_cond_expressionContext.class,0);
		}
		public Conditional_expressionContext conditional_expression() {
			return getRuleContext(Conditional_expressionContext.class,0);
		}
		public Conditional_primaryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_conditional_primary; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterConditional_primary(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitConditional_primary(this);
		}
	}

	public final Conditional_primaryContext conditional_primary() throws RecognitionException {
		Conditional_primaryContext _localctx = new Conditional_primaryContext(_ctx, getState());
		enterRule(_localctx, 116, RULE_conditional_primary);
		try {
			setState(775);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,73,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(770);
				simple_cond_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(771);
				match(T__1);
				setState(772);
				conditional_expression(0);
				setState(773);
				match(T__2);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Simple_cond_expressionContext extends ParserRuleContext {
		public Comparison_expressionContext comparison_expression() {
			return getRuleContext(Comparison_expressionContext.class,0);
		}
		public Between_expressionContext between_expression() {
			return getRuleContext(Between_expressionContext.class,0);
		}
		public In_expressionContext in_expression() {
			return getRuleContext(In_expressionContext.class,0);
		}
		public Like_expressionContext like_expression() {
			return getRuleContext(Like_expressionContext.class,0);
		}
		public Null_comparison_expressionContext null_comparison_expression() {
			return getRuleContext(Null_comparison_expressionContext.class,0);
		}
		public Empty_collection_comparison_expressionContext empty_collection_comparison_expression() {
			return getRuleContext(Empty_collection_comparison_expressionContext.class,0);
		}
		public Collection_member_expressionContext collection_member_expression() {
			return getRuleContext(Collection_member_expressionContext.class,0);
		}
		public Exists_expressionContext exists_expression() {
			return getRuleContext(Exists_expressionContext.class,0);
		}
		public Simple_cond_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simple_cond_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSimple_cond_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSimple_cond_expression(this);
		}
	}

	public final Simple_cond_expressionContext simple_cond_expression() throws RecognitionException {
		Simple_cond_expressionContext _localctx = new Simple_cond_expressionContext(_ctx, getState());
		enterRule(_localctx, 118, RULE_simple_cond_expression);
		try {
			setState(785);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,74,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(777);
				comparison_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(778);
				between_expression();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(779);
				in_expression();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(780);
				like_expression();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(781);
				null_comparison_expression();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(782);
				empty_collection_comparison_expression();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(783);
				collection_member_expression();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(784);
				exists_expression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Between_expressionContext extends ParserRuleContext {
		public List<Arithmetic_expressionContext> arithmetic_expression() {
			return getRuleContexts(Arithmetic_expressionContext.class);
		}
		public Arithmetic_expressionContext arithmetic_expression(int i) {
			return getRuleContext(Arithmetic_expressionContext.class,i);
		}
		public TerminalNode BETWEEN() { return getToken(JpqlParser.BETWEEN, 0); }
		public TerminalNode AND() { return getToken(JpqlParser.AND, 0); }
		public TerminalNode NOT() { return getToken(JpqlParser.NOT, 0); }
		public List<String_expressionContext> string_expression() {
			return getRuleContexts(String_expressionContext.class);
		}
		public String_expressionContext string_expression(int i) {
			return getRuleContext(String_expressionContext.class,i);
		}
		public List<Datetime_expressionContext> datetime_expression() {
			return getRuleContexts(Datetime_expressionContext.class);
		}
		public Datetime_expressionContext datetime_expression(int i) {
			return getRuleContext(Datetime_expressionContext.class,i);
		}
		public Between_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_between_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterBetween_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitBetween_expression(this);
		}
	}

	public final Between_expressionContext between_expression() throws RecognitionException {
		Between_expressionContext _localctx = new Between_expressionContext(_ctx, getState());
		enterRule(_localctx, 120, RULE_between_expression);
		int _la;
		try {
			setState(814);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,78,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(787);
				arithmetic_expression(0);
				setState(789);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(788);
					match(NOT);
					}
				}

				setState(791);
				match(BETWEEN);
				setState(792);
				arithmetic_expression(0);
				setState(793);
				match(AND);
				setState(794);
				arithmetic_expression(0);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(796);
				string_expression();
				setState(798);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(797);
					match(NOT);
					}
				}

				setState(800);
				match(BETWEEN);
				setState(801);
				string_expression();
				setState(802);
				match(AND);
				setState(803);
				string_expression();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(805);
				datetime_expression();
				setState(807);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(806);
					match(NOT);
					}
				}

				setState(809);
				match(BETWEEN);
				setState(810);
				datetime_expression();
				setState(811);
				match(AND);
				setState(812);
				datetime_expression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class In_expressionContext extends ParserRuleContext {
		public TerminalNode IN() { return getToken(JpqlParser.IN, 0); }
		public State_valued_path_expressionContext state_valued_path_expression() {
			return getRuleContext(State_valued_path_expressionContext.class,0);
		}
		public Type_discriminatorContext type_discriminator() {
			return getRuleContext(Type_discriminatorContext.class,0);
		}
		public Collection_valued_input_parameterContext collection_valued_input_parameter() {
			return getRuleContext(Collection_valued_input_parameterContext.class,0);
		}
		public TerminalNode NOT() { return getToken(JpqlParser.NOT, 0); }
		public List<In_itemContext> in_item() {
			return getRuleContexts(In_itemContext.class);
		}
		public In_itemContext in_item(int i) {
			return getRuleContext(In_itemContext.class,i);
		}
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public In_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_in_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterIn_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitIn_expression(this);
		}
	}

	public final In_expressionContext in_expression() throws RecognitionException {
		In_expressionContext _localctx = new In_expressionContext(_ctx, getState());
		enterRule(_localctx, 122, RULE_in_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(818);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,79,_ctx) ) {
			case 1:
				{
				setState(816);
				state_valued_path_expression();
				}
				break;
			case 2:
				{
				setState(817);
				type_discriminator();
				}
				break;
			}
			setState(821);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NOT) {
				{
				setState(820);
				match(NOT);
				}
			}

			setState(823);
			match(IN);
			setState(840);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,82,_ctx) ) {
			case 1:
				{
				{
				setState(824);
				match(T__1);
				setState(825);
				in_item();
				setState(830);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__0) {
					{
					{
					setState(826);
					match(T__0);
					setState(827);
					in_item();
					}
					}
					setState(832);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(833);
				match(T__2);
				}
				}
				break;
			case 2:
				{
				{
				setState(835);
				match(T__1);
				setState(836);
				subquery();
				setState(837);
				match(T__2);
				}
				}
				break;
			case 3:
				{
				setState(839);
				collection_valued_input_parameter();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class In_itemContext extends ParserRuleContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public Single_valued_input_parameterContext single_valued_input_parameter() {
			return getRuleContext(Single_valued_input_parameterContext.class,0);
		}
		public In_itemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_in_item; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterIn_item(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitIn_item(this);
		}
	}

	public final In_itemContext in_item() throws RecognitionException {
		In_itemContext _localctx = new In_itemContext(_ctx, getState());
		enterRule(_localctx, 124, RULE_in_item);
		try {
			setState(844);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AVG:
			case COUNT:
			case DATE:
			case FALSE:
			case FLOOR:
			case FROM:
			case INNER:
			case KEY:
			case LEFT:
			case MAX:
			case MIN:
			case NEW:
			case ORDER:
			case OUTER:
			case POWER:
			case SIGN:
			case SUM:
			case TIME:
			case TRUE:
			case TYPE:
			case VALUE:
			case IDENTIFICATION_VARIABLE:
			case STRINGLITERAL:
			case JAVASTRINGLITERAL:
			case FLOATLITERAL:
			case INTLITERAL:
			case LONGLITERAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(842);
				literal();
				}
				break;
			case T__13:
			case T__14:
				enterOuterAlt(_localctx, 2);
				{
				setState(843);
				single_valued_input_parameter();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Like_expressionContext extends ParserRuleContext {
		public String_expressionContext string_expression() {
			return getRuleContext(String_expressionContext.class,0);
		}
		public TerminalNode LIKE() { return getToken(JpqlParser.LIKE, 0); }
		public Pattern_valueContext pattern_value() {
			return getRuleContext(Pattern_valueContext.class,0);
		}
		public TerminalNode NOT() { return getToken(JpqlParser.NOT, 0); }
		public TerminalNode ESCAPE() { return getToken(JpqlParser.ESCAPE, 0); }
		public Escape_characterContext escape_character() {
			return getRuleContext(Escape_characterContext.class,0);
		}
		public Like_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_like_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterLike_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitLike_expression(this);
		}
	}

	public final Like_expressionContext like_expression() throws RecognitionException {
		Like_expressionContext _localctx = new Like_expressionContext(_ctx, getState());
		enterRule(_localctx, 126, RULE_like_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(846);
			string_expression();
			setState(848);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NOT) {
				{
				setState(847);
				match(NOT);
				}
			}

			setState(850);
			match(LIKE);
			setState(852);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(851);
				match(T__4);
				}
			}

			setState(854);
			pattern_value();
			setState(856);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,86,_ctx) ) {
			case 1:
				{
				setState(855);
				match(T__4);
				}
				break;
			}
			setState(860);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,87,_ctx) ) {
			case 1:
				{
				setState(858);
				match(ESCAPE);
				setState(859);
				escape_character();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Null_comparison_expressionContext extends ParserRuleContext {
		public TerminalNode IS() { return getToken(JpqlParser.IS, 0); }
		public TerminalNode NULL() { return getToken(JpqlParser.NULL, 0); }
		public Single_valued_path_expressionContext single_valued_path_expression() {
			return getRuleContext(Single_valued_path_expressionContext.class,0);
		}
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public TerminalNode NOT() { return getToken(JpqlParser.NOT, 0); }
		public Null_comparison_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_null_comparison_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterNull_comparison_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitNull_comparison_expression(this);
		}
	}

	public final Null_comparison_expressionContext null_comparison_expression() throws RecognitionException {
		Null_comparison_expressionContext _localctx = new Null_comparison_expressionContext(_ctx, getState());
		enterRule(_localctx, 128, RULE_null_comparison_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(864);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AVG:
			case COUNT:
			case DATE:
			case ENTRY:
			case FLOOR:
			case FROM:
			case INNER:
			case KEY:
			case LEFT:
			case MAX:
			case MIN:
			case NEW:
			case ORDER:
			case OUTER:
			case POWER:
			case SIGN:
			case SUM:
			case TIME:
			case TREAT:
			case TYPE:
			case VALUE:
			case IDENTIFICATION_VARIABLE:
				{
				setState(862);
				single_valued_path_expression();
				}
				break;
			case T__13:
			case T__14:
				{
				setState(863);
				input_parameter();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(866);
			match(IS);
			setState(868);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NOT) {
				{
				setState(867);
				match(NOT);
				}
			}

			setState(870);
			match(NULL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Empty_collection_comparison_expressionContext extends ParserRuleContext {
		public Collection_valued_path_expressionContext collection_valued_path_expression() {
			return getRuleContext(Collection_valued_path_expressionContext.class,0);
		}
		public TerminalNode IS() { return getToken(JpqlParser.IS, 0); }
		public TerminalNode EMPTY() { return getToken(JpqlParser.EMPTY, 0); }
		public TerminalNode NOT() { return getToken(JpqlParser.NOT, 0); }
		public Empty_collection_comparison_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_empty_collection_comparison_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterEmpty_collection_comparison_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitEmpty_collection_comparison_expression(this);
		}
	}

	public final Empty_collection_comparison_expressionContext empty_collection_comparison_expression() throws RecognitionException {
		Empty_collection_comparison_expressionContext _localctx = new Empty_collection_comparison_expressionContext(_ctx, getState());
		enterRule(_localctx, 130, RULE_empty_collection_comparison_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(872);
			collection_valued_path_expression();
			setState(873);
			match(IS);
			setState(875);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NOT) {
				{
				setState(874);
				match(NOT);
				}
			}

			setState(877);
			match(EMPTY);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Collection_member_expressionContext extends ParserRuleContext {
		public Entity_or_value_expressionContext entity_or_value_expression() {
			return getRuleContext(Entity_or_value_expressionContext.class,0);
		}
		public TerminalNode MEMBER() { return getToken(JpqlParser.MEMBER, 0); }
		public Collection_valued_path_expressionContext collection_valued_path_expression() {
			return getRuleContext(Collection_valued_path_expressionContext.class,0);
		}
		public TerminalNode NOT() { return getToken(JpqlParser.NOT, 0); }
		public TerminalNode OF() { return getToken(JpqlParser.OF, 0); }
		public Collection_member_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_collection_member_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterCollection_member_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitCollection_member_expression(this);
		}
	}

	public final Collection_member_expressionContext collection_member_expression() throws RecognitionException {
		Collection_member_expressionContext _localctx = new Collection_member_expressionContext(_ctx, getState());
		enterRule(_localctx, 132, RULE_collection_member_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(879);
			entity_or_value_expression();
			setState(881);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NOT) {
				{
				setState(880);
				match(NOT);
				}
			}

			setState(883);
			match(MEMBER);
			setState(885);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OF) {
				{
				setState(884);
				match(OF);
				}
			}

			setState(887);
			collection_valued_path_expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Entity_or_value_expressionContext extends ParserRuleContext {
		public Single_valued_object_path_expressionContext single_valued_object_path_expression() {
			return getRuleContext(Single_valued_object_path_expressionContext.class,0);
		}
		public State_field_path_expressionContext state_field_path_expression() {
			return getRuleContext(State_field_path_expressionContext.class,0);
		}
		public Simple_entity_or_value_expressionContext simple_entity_or_value_expression() {
			return getRuleContext(Simple_entity_or_value_expressionContext.class,0);
		}
		public Entity_or_value_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_entity_or_value_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterEntity_or_value_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitEntity_or_value_expression(this);
		}
	}

	public final Entity_or_value_expressionContext entity_or_value_expression() throws RecognitionException {
		Entity_or_value_expressionContext _localctx = new Entity_or_value_expressionContext(_ctx, getState());
		enterRule(_localctx, 134, RULE_entity_or_value_expression);
		try {
			setState(892);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,93,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(889);
				single_valued_object_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(890);
				state_field_path_expression();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(891);
				simple_entity_or_value_expression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Simple_entity_or_value_expressionContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public Simple_entity_or_value_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simple_entity_or_value_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSimple_entity_or_value_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSimple_entity_or_value_expression(this);
		}
	}

	public final Simple_entity_or_value_expressionContext simple_entity_or_value_expression() throws RecognitionException {
		Simple_entity_or_value_expressionContext _localctx = new Simple_entity_or_value_expressionContext(_ctx, getState());
		enterRule(_localctx, 136, RULE_simple_entity_or_value_expression);
		try {
			setState(897);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,94,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(894);
				identification_variable();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(895);
				input_parameter();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(896);
				literal();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Exists_expressionContext extends ParserRuleContext {
		public TerminalNode EXISTS() { return getToken(JpqlParser.EXISTS, 0); }
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public TerminalNode NOT() { return getToken(JpqlParser.NOT, 0); }
		public Exists_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exists_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterExists_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitExists_expression(this);
		}
	}

	public final Exists_expressionContext exists_expression() throws RecognitionException {
		Exists_expressionContext _localctx = new Exists_expressionContext(_ctx, getState());
		enterRule(_localctx, 138, RULE_exists_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(900);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NOT) {
				{
				setState(899);
				match(NOT);
				}
			}

			setState(902);
			match(EXISTS);
			setState(903);
			match(T__1);
			setState(904);
			subquery();
			setState(905);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class All_or_any_expressionContext extends ParserRuleContext {
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public TerminalNode ALL() { return getToken(JpqlParser.ALL, 0); }
		public TerminalNode ANY() { return getToken(JpqlParser.ANY, 0); }
		public TerminalNode SOME() { return getToken(JpqlParser.SOME, 0); }
		public All_or_any_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_all_or_any_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterAll_or_any_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitAll_or_any_expression(this);
		}
	}

	public final All_or_any_expressionContext all_or_any_expression() throws RecognitionException {
		All_or_any_expressionContext _localctx = new All_or_any_expressionContext(_ctx, getState());
		enterRule(_localctx, 140, RULE_all_or_any_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(907);
			_la = _input.LA(1);
			if ( !(_la==ALL || _la==ANY || _la==SOME) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(908);
			match(T__1);
			setState(909);
			subquery();
			setState(910);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Comparison_expressionContext extends ParserRuleContext {
		public Token op;
		public List<String_expressionContext> string_expression() {
			return getRuleContexts(String_expressionContext.class);
		}
		public String_expressionContext string_expression(int i) {
			return getRuleContext(String_expressionContext.class,i);
		}
		public Comparison_operatorContext comparison_operator() {
			return getRuleContext(Comparison_operatorContext.class,0);
		}
		public All_or_any_expressionContext all_or_any_expression() {
			return getRuleContext(All_or_any_expressionContext.class,0);
		}
		public List<Boolean_expressionContext> boolean_expression() {
			return getRuleContexts(Boolean_expressionContext.class);
		}
		public Boolean_expressionContext boolean_expression(int i) {
			return getRuleContext(Boolean_expressionContext.class,i);
		}
		public TerminalNode EQUAL() { return getToken(JpqlParser.EQUAL, 0); }
		public TerminalNode NOT_EQUAL() { return getToken(JpqlParser.NOT_EQUAL, 0); }
		public List<Enum_expressionContext> enum_expression() {
			return getRuleContexts(Enum_expressionContext.class);
		}
		public Enum_expressionContext enum_expression(int i) {
			return getRuleContext(Enum_expressionContext.class,i);
		}
		public List<Datetime_expressionContext> datetime_expression() {
			return getRuleContexts(Datetime_expressionContext.class);
		}
		public Datetime_expressionContext datetime_expression(int i) {
			return getRuleContext(Datetime_expressionContext.class,i);
		}
		public List<Entity_expressionContext> entity_expression() {
			return getRuleContexts(Entity_expressionContext.class);
		}
		public Entity_expressionContext entity_expression(int i) {
			return getRuleContext(Entity_expressionContext.class,i);
		}
		public List<Arithmetic_expressionContext> arithmetic_expression() {
			return getRuleContexts(Arithmetic_expressionContext.class);
		}
		public Arithmetic_expressionContext arithmetic_expression(int i) {
			return getRuleContext(Arithmetic_expressionContext.class,i);
		}
		public List<Entity_type_expressionContext> entity_type_expression() {
			return getRuleContexts(Entity_type_expressionContext.class);
		}
		public Entity_type_expressionContext entity_type_expression(int i) {
			return getRuleContext(Entity_type_expressionContext.class,i);
		}
		public Comparison_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparison_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterComparison_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitComparison_expression(this);
		}
	}

	public final Comparison_expressionContext comparison_expression() throws RecognitionException {
		Comparison_expressionContext _localctx = new Comparison_expressionContext(_ctx, getState());
		enterRule(_localctx, 142, RULE_comparison_expression);
		int _la;
		try {
			setState(952);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,102,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(912);
				string_expression();
				setState(913);
				comparison_operator();
				setState(916);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__1:
				case T__13:
				case T__14:
				case AVG:
				case CASE:
				case COALESCE:
				case CONCAT:
				case COUNT:
				case DATE:
				case FLOOR:
				case FROM:
				case FUNCTION:
				case INNER:
				case KEY:
				case LEFT:
				case LOWER:
				case MAX:
				case MIN:
				case NEW:
				case NULLIF:
				case ORDER:
				case OUTER:
				case POWER:
				case SIGN:
				case SUBSTRING:
				case SUM:
				case TIME:
				case TREAT:
				case TRIM:
				case TYPE:
				case UPPER:
				case VALUE:
				case CHARACTER:
				case IDENTIFICATION_VARIABLE:
				case STRINGLITERAL:
					{
					setState(914);
					string_expression();
					}
					break;
				case ALL:
				case ANY:
				case SOME:
					{
					setState(915);
					all_or_any_expression();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(918);
				boolean_expression();
				setState(919);
				((Comparison_expressionContext)_localctx).op = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==EQUAL || _la==NOT_EQUAL) ) {
					((Comparison_expressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(922);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__1:
				case T__13:
				case T__14:
				case AVG:
				case CASE:
				case COALESCE:
				case COUNT:
				case DATE:
				case FALSE:
				case FLOOR:
				case FROM:
				case FUNCTION:
				case INNER:
				case KEY:
				case LEFT:
				case MAX:
				case MIN:
				case NEW:
				case NULLIF:
				case ORDER:
				case OUTER:
				case POWER:
				case SIGN:
				case SUM:
				case TIME:
				case TREAT:
				case TRUE:
				case TYPE:
				case VALUE:
				case IDENTIFICATION_VARIABLE:
					{
					setState(920);
					boolean_expression();
					}
					break;
				case ALL:
				case ANY:
				case SOME:
					{
					setState(921);
					all_or_any_expression();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(924);
				enum_expression();
				setState(925);
				((Comparison_expressionContext)_localctx).op = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==EQUAL || _la==NOT_EQUAL) ) {
					((Comparison_expressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(928);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__1:
				case T__13:
				case T__14:
				case AVG:
				case CASE:
				case COALESCE:
				case COUNT:
				case DATE:
				case FLOOR:
				case FROM:
				case INNER:
				case KEY:
				case LEFT:
				case MAX:
				case MIN:
				case NEW:
				case NULLIF:
				case ORDER:
				case OUTER:
				case POWER:
				case SIGN:
				case SUM:
				case TIME:
				case TREAT:
				case TYPE:
				case VALUE:
				case IDENTIFICATION_VARIABLE:
					{
					setState(926);
					enum_expression();
					}
					break;
				case ALL:
				case ANY:
				case SOME:
					{
					setState(927);
					all_or_any_expression();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(930);
				datetime_expression();
				setState(931);
				comparison_operator();
				setState(934);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__1:
				case T__13:
				case T__14:
				case AVG:
				case CASE:
				case COALESCE:
				case COUNT:
				case CURRENT_DATE:
				case CURRENT_TIME:
				case CURRENT_TIMESTAMP:
				case DATE:
				case EXTRACT:
				case FLOOR:
				case FROM:
				case FUNCTION:
				case INNER:
				case KEY:
				case LEFT:
				case LOCAL:
				case MAX:
				case MIN:
				case NEW:
				case NULLIF:
				case ORDER:
				case OUTER:
				case POWER:
				case SIGN:
				case SUM:
				case TIME:
				case TREAT:
				case TYPE:
				case VALUE:
				case IDENTIFICATION_VARIABLE:
				case STRINGLITERAL:
					{
					setState(932);
					datetime_expression();
					}
					break;
				case ALL:
				case ANY:
				case SOME:
					{
					setState(933);
					all_or_any_expression();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(936);
				entity_expression();
				setState(937);
				((Comparison_expressionContext)_localctx).op = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==EQUAL || _la==NOT_EQUAL) ) {
					((Comparison_expressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(940);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__13:
				case T__14:
				case AVG:
				case COUNT:
				case DATE:
				case FLOOR:
				case FROM:
				case INNER:
				case KEY:
				case LEFT:
				case MAX:
				case MIN:
				case NEW:
				case ORDER:
				case OUTER:
				case POWER:
				case SIGN:
				case SUM:
				case TIME:
				case TREAT:
				case TYPE:
				case VALUE:
				case IDENTIFICATION_VARIABLE:
					{
					setState(938);
					entity_expression();
					}
					break;
				case ALL:
				case ANY:
				case SOME:
					{
					setState(939);
					all_or_any_expression();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(942);
				arithmetic_expression(0);
				setState(943);
				comparison_operator();
				setState(946);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__1:
				case T__9:
				case T__10:
				case T__13:
				case T__14:
				case ABS:
				case AVG:
				case CASE:
				case CEILING:
				case COALESCE:
				case COUNT:
				case DATE:
				case EXP:
				case EXTRACT:
				case FLOOR:
				case FROM:
				case FUNCTION:
				case INDEX:
				case INNER:
				case KEY:
				case LEFT:
				case LENGTH:
				case LN:
				case LOCATE:
				case MAX:
				case MIN:
				case MOD:
				case NEW:
				case NULLIF:
				case ORDER:
				case OUTER:
				case POWER:
				case ROUND:
				case SIGN:
				case SIZE:
				case SQRT:
				case SUM:
				case TIME:
				case TREAT:
				case TYPE:
				case VALUE:
				case IDENTIFICATION_VARIABLE:
				case FLOATLITERAL:
				case INTLITERAL:
				case LONGLITERAL:
					{
					setState(944);
					arithmetic_expression(0);
					}
					break;
				case ALL:
				case ANY:
				case SOME:
					{
					setState(945);
					all_or_any_expression();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(948);
				entity_type_expression();
				setState(949);
				((Comparison_expressionContext)_localctx).op = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==EQUAL || _la==NOT_EQUAL) ) {
					((Comparison_expressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(950);
				entity_type_expression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Comparison_operatorContext extends ParserRuleContext {
		public Token op;
		public TerminalNode EQUAL() { return getToken(JpqlParser.EQUAL, 0); }
		public TerminalNode NOT_EQUAL() { return getToken(JpqlParser.NOT_EQUAL, 0); }
		public Comparison_operatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparison_operator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterComparison_operator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitComparison_operator(this);
		}
	}

	public final Comparison_operatorContext comparison_operator() throws RecognitionException {
		Comparison_operatorContext _localctx = new Comparison_operatorContext(_ctx, getState());
		enterRule(_localctx, 144, RULE_comparison_operator);
		try {
			setState(960);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case EQUAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(954);
				((Comparison_operatorContext)_localctx).op = match(EQUAL);
				}
				break;
			case T__5:
				enterOuterAlt(_localctx, 2);
				{
				setState(955);
				((Comparison_operatorContext)_localctx).op = match(T__5);
				}
				break;
			case T__6:
				enterOuterAlt(_localctx, 3);
				{
				setState(956);
				((Comparison_operatorContext)_localctx).op = match(T__6);
				}
				break;
			case T__7:
				enterOuterAlt(_localctx, 4);
				{
				setState(957);
				((Comparison_operatorContext)_localctx).op = match(T__7);
				}
				break;
			case T__8:
				enterOuterAlt(_localctx, 5);
				{
				setState(958);
				((Comparison_operatorContext)_localctx).op = match(T__8);
				}
				break;
			case NOT_EQUAL:
				enterOuterAlt(_localctx, 6);
				{
				setState(959);
				((Comparison_operatorContext)_localctx).op = match(NOT_EQUAL);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Arithmetic_expressionContext extends ParserRuleContext {
		public Token op;
		public Arithmetic_termContext arithmetic_term() {
			return getRuleContext(Arithmetic_termContext.class,0);
		}
		public Arithmetic_expressionContext arithmetic_expression() {
			return getRuleContext(Arithmetic_expressionContext.class,0);
		}
		public Arithmetic_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmetic_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterArithmetic_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitArithmetic_expression(this);
		}
	}

	public final Arithmetic_expressionContext arithmetic_expression() throws RecognitionException {
		return arithmetic_expression(0);
	}

	private Arithmetic_expressionContext arithmetic_expression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		Arithmetic_expressionContext _localctx = new Arithmetic_expressionContext(_ctx, _parentState);
		Arithmetic_expressionContext _prevctx = _localctx;
		int _startState = 146;
		enterRecursionRule(_localctx, 146, RULE_arithmetic_expression, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(963);
			arithmetic_term(0);
			}
			_ctx.stop = _input.LT(-1);
			setState(970);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,104,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new Arithmetic_expressionContext(_parentctx, _parentState);
					pushNewRecursionContext(_localctx, _startState, RULE_arithmetic_expression);
					setState(965);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(966);
					((Arithmetic_expressionContext)_localctx).op = _input.LT(1);
					_la = _input.LA(1);
					if ( !(_la==T__9 || _la==T__10) ) {
						((Arithmetic_expressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(967);
					arithmetic_term(0);
					}
					} 
				}
				setState(972);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,104,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Arithmetic_termContext extends ParserRuleContext {
		public Token op;
		public Arithmetic_factorContext arithmetic_factor() {
			return getRuleContext(Arithmetic_factorContext.class,0);
		}
		public Arithmetic_termContext arithmetic_term() {
			return getRuleContext(Arithmetic_termContext.class,0);
		}
		public Arithmetic_termContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmetic_term; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterArithmetic_term(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitArithmetic_term(this);
		}
	}

	public final Arithmetic_termContext arithmetic_term() throws RecognitionException {
		return arithmetic_term(0);
	}

	private Arithmetic_termContext arithmetic_term(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		Arithmetic_termContext _localctx = new Arithmetic_termContext(_ctx, _parentState);
		Arithmetic_termContext _prevctx = _localctx;
		int _startState = 148;
		enterRecursionRule(_localctx, 148, RULE_arithmetic_term, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(974);
			arithmetic_factor();
			}
			_ctx.stop = _input.LT(-1);
			setState(981);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,105,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new Arithmetic_termContext(_parentctx, _parentState);
					pushNewRecursionContext(_localctx, _startState, RULE_arithmetic_term);
					setState(976);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(977);
					((Arithmetic_termContext)_localctx).op = _input.LT(1);
					_la = _input.LA(1);
					if ( !(_la==T__11 || _la==T__12) ) {
						((Arithmetic_termContext)_localctx).op = (Token)_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(978);
					arithmetic_factor();
					}
					} 
				}
				setState(983);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,105,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Arithmetic_factorContext extends ParserRuleContext {
		public Token op;
		public Arithmetic_primaryContext arithmetic_primary() {
			return getRuleContext(Arithmetic_primaryContext.class,0);
		}
		public Arithmetic_factorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmetic_factor; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterArithmetic_factor(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitArithmetic_factor(this);
		}
	}

	public final Arithmetic_factorContext arithmetic_factor() throws RecognitionException {
		Arithmetic_factorContext _localctx = new Arithmetic_factorContext(_ctx, getState());
		enterRule(_localctx, 150, RULE_arithmetic_factor);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(985);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__9 || _la==T__10) {
				{
				setState(984);
				((Arithmetic_factorContext)_localctx).op = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__9 || _la==T__10) ) {
					((Arithmetic_factorContext)_localctx).op = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
			}

			setState(987);
			arithmetic_primary();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Arithmetic_primaryContext extends ParserRuleContext {
		public State_valued_path_expressionContext state_valued_path_expression() {
			return getRuleContext(State_valued_path_expressionContext.class,0);
		}
		public Numeric_literalContext numeric_literal() {
			return getRuleContext(Numeric_literalContext.class,0);
		}
		public Arithmetic_expressionContext arithmetic_expression() {
			return getRuleContext(Arithmetic_expressionContext.class,0);
		}
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Functions_returning_numericsContext functions_returning_numerics() {
			return getRuleContext(Functions_returning_numericsContext.class,0);
		}
		public Aggregate_expressionContext aggregate_expression() {
			return getRuleContext(Aggregate_expressionContext.class,0);
		}
		public Case_expressionContext case_expression() {
			return getRuleContext(Case_expressionContext.class,0);
		}
		public Function_invocationContext function_invocation() {
			return getRuleContext(Function_invocationContext.class,0);
		}
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public Arithmetic_primaryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmetic_primary; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterArithmetic_primary(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitArithmetic_primary(this);
		}
	}

	public final Arithmetic_primaryContext arithmetic_primary() throws RecognitionException {
		Arithmetic_primaryContext _localctx = new Arithmetic_primaryContext(_ctx, getState());
		enterRule(_localctx, 152, RULE_arithmetic_primary);
		try {
			setState(1004);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,107,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(989);
				state_valued_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(990);
				numeric_literal();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(991);
				match(T__1);
				setState(992);
				arithmetic_expression(0);
				setState(993);
				match(T__2);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(995);
				input_parameter();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(996);
				functions_returning_numerics();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(997);
				aggregate_expression();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(998);
				case_expression();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(999);
				function_invocation();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(1000);
				match(T__1);
				setState(1001);
				subquery();
				setState(1002);
				match(T__2);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class String_expressionContext extends ParserRuleContext {
		public State_valued_path_expressionContext state_valued_path_expression() {
			return getRuleContext(State_valued_path_expressionContext.class,0);
		}
		public String_literalContext string_literal() {
			return getRuleContext(String_literalContext.class,0);
		}
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Functions_returning_stringsContext functions_returning_strings() {
			return getRuleContext(Functions_returning_stringsContext.class,0);
		}
		public Aggregate_expressionContext aggregate_expression() {
			return getRuleContext(Aggregate_expressionContext.class,0);
		}
		public Case_expressionContext case_expression() {
			return getRuleContext(Case_expressionContext.class,0);
		}
		public Function_invocationContext function_invocation() {
			return getRuleContext(Function_invocationContext.class,0);
		}
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public String_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_string_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterString_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitString_expression(this);
		}
	}

	public final String_expressionContext string_expression() throws RecognitionException {
		String_expressionContext _localctx = new String_expressionContext(_ctx, getState());
		enterRule(_localctx, 154, RULE_string_expression);
		try {
			setState(1017);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,108,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1006);
				state_valued_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1007);
				string_literal();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1008);
				input_parameter();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1009);
				functions_returning_strings();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1010);
				aggregate_expression();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(1011);
				case_expression();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(1012);
				function_invocation();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(1013);
				match(T__1);
				setState(1014);
				subquery();
				setState(1015);
				match(T__2);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Datetime_expressionContext extends ParserRuleContext {
		public State_valued_path_expressionContext state_valued_path_expression() {
			return getRuleContext(State_valued_path_expressionContext.class,0);
		}
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Functions_returning_datetimeContext functions_returning_datetime() {
			return getRuleContext(Functions_returning_datetimeContext.class,0);
		}
		public Aggregate_expressionContext aggregate_expression() {
			return getRuleContext(Aggregate_expressionContext.class,0);
		}
		public Case_expressionContext case_expression() {
			return getRuleContext(Case_expressionContext.class,0);
		}
		public Function_invocationContext function_invocation() {
			return getRuleContext(Function_invocationContext.class,0);
		}
		public Date_time_timestamp_literalContext date_time_timestamp_literal() {
			return getRuleContext(Date_time_timestamp_literalContext.class,0);
		}
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public Datetime_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_datetime_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterDatetime_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitDatetime_expression(this);
		}
	}

	public final Datetime_expressionContext datetime_expression() throws RecognitionException {
		Datetime_expressionContext _localctx = new Datetime_expressionContext(_ctx, getState());
		enterRule(_localctx, 156, RULE_datetime_expression);
		try {
			setState(1030);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,109,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1019);
				state_valued_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1020);
				input_parameter();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1021);
				functions_returning_datetime();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1022);
				aggregate_expression();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1023);
				case_expression();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(1024);
				function_invocation();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(1025);
				date_time_timestamp_literal();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(1026);
				match(T__1);
				setState(1027);
				subquery();
				setState(1028);
				match(T__2);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Boolean_expressionContext extends ParserRuleContext {
		public State_valued_path_expressionContext state_valued_path_expression() {
			return getRuleContext(State_valued_path_expressionContext.class,0);
		}
		public Boolean_literalContext boolean_literal() {
			return getRuleContext(Boolean_literalContext.class,0);
		}
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Case_expressionContext case_expression() {
			return getRuleContext(Case_expressionContext.class,0);
		}
		public Function_invocationContext function_invocation() {
			return getRuleContext(Function_invocationContext.class,0);
		}
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public Boolean_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_boolean_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterBoolean_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitBoolean_expression(this);
		}
	}

	public final Boolean_expressionContext boolean_expression() throws RecognitionException {
		Boolean_expressionContext _localctx = new Boolean_expressionContext(_ctx, getState());
		enterRule(_localctx, 158, RULE_boolean_expression);
		try {
			setState(1041);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AVG:
			case COUNT:
			case DATE:
			case FLOOR:
			case FROM:
			case INNER:
			case KEY:
			case LEFT:
			case MAX:
			case MIN:
			case NEW:
			case ORDER:
			case OUTER:
			case POWER:
			case SIGN:
			case SUM:
			case TIME:
			case TREAT:
			case TYPE:
			case VALUE:
			case IDENTIFICATION_VARIABLE:
				enterOuterAlt(_localctx, 1);
				{
				setState(1032);
				state_valued_path_expression();
				}
				break;
			case FALSE:
			case TRUE:
				enterOuterAlt(_localctx, 2);
				{
				setState(1033);
				boolean_literal();
				}
				break;
			case T__13:
			case T__14:
				enterOuterAlt(_localctx, 3);
				{
				setState(1034);
				input_parameter();
				}
				break;
			case CASE:
			case COALESCE:
			case NULLIF:
				enterOuterAlt(_localctx, 4);
				{
				setState(1035);
				case_expression();
				}
				break;
			case FUNCTION:
				enterOuterAlt(_localctx, 5);
				{
				setState(1036);
				function_invocation();
				}
				break;
			case T__1:
				enterOuterAlt(_localctx, 6);
				{
				setState(1037);
				match(T__1);
				setState(1038);
				subquery();
				setState(1039);
				match(T__2);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Enum_expressionContext extends ParserRuleContext {
		public State_valued_path_expressionContext state_valued_path_expression() {
			return getRuleContext(State_valued_path_expressionContext.class,0);
		}
		public Enum_literalContext enum_literal() {
			return getRuleContext(Enum_literalContext.class,0);
		}
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Case_expressionContext case_expression() {
			return getRuleContext(Case_expressionContext.class,0);
		}
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public Enum_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enum_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterEnum_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitEnum_expression(this);
		}
	}

	public final Enum_expressionContext enum_expression() throws RecognitionException {
		Enum_expressionContext _localctx = new Enum_expressionContext(_ctx, getState());
		enterRule(_localctx, 160, RULE_enum_expression);
		try {
			setState(1051);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,111,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1043);
				state_valued_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1044);
				enum_literal();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1045);
				input_parameter();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1046);
				case_expression();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1047);
				match(T__1);
				setState(1048);
				subquery();
				setState(1049);
				match(T__2);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Entity_expressionContext extends ParserRuleContext {
		public Single_valued_object_path_expressionContext single_valued_object_path_expression() {
			return getRuleContext(Single_valued_object_path_expressionContext.class,0);
		}
		public Simple_entity_expressionContext simple_entity_expression() {
			return getRuleContext(Simple_entity_expressionContext.class,0);
		}
		public Entity_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_entity_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterEntity_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitEntity_expression(this);
		}
	}

	public final Entity_expressionContext entity_expression() throws RecognitionException {
		Entity_expressionContext _localctx = new Entity_expressionContext(_ctx, getState());
		enterRule(_localctx, 162, RULE_entity_expression);
		try {
			setState(1055);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,112,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1053);
				single_valued_object_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1054);
				simple_entity_expression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Simple_entity_expressionContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Simple_entity_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simple_entity_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSimple_entity_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSimple_entity_expression(this);
		}
	}

	public final Simple_entity_expressionContext simple_entity_expression() throws RecognitionException {
		Simple_entity_expressionContext _localctx = new Simple_entity_expressionContext(_ctx, getState());
		enterRule(_localctx, 164, RULE_simple_entity_expression);
		try {
			setState(1059);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AVG:
			case COUNT:
			case DATE:
			case FLOOR:
			case FROM:
			case INNER:
			case KEY:
			case LEFT:
			case MAX:
			case MIN:
			case NEW:
			case ORDER:
			case OUTER:
			case POWER:
			case SIGN:
			case SUM:
			case TIME:
			case TYPE:
			case VALUE:
			case IDENTIFICATION_VARIABLE:
				enterOuterAlt(_localctx, 1);
				{
				setState(1057);
				identification_variable();
				}
				break;
			case T__13:
			case T__14:
				enterOuterAlt(_localctx, 2);
				{
				setState(1058);
				input_parameter();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Entity_type_expressionContext extends ParserRuleContext {
		public Type_discriminatorContext type_discriminator() {
			return getRuleContext(Type_discriminatorContext.class,0);
		}
		public Entity_type_literalContext entity_type_literal() {
			return getRuleContext(Entity_type_literalContext.class,0);
		}
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Entity_type_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_entity_type_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterEntity_type_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitEntity_type_expression(this);
		}
	}

	public final Entity_type_expressionContext entity_type_expression() throws RecognitionException {
		Entity_type_expressionContext _localctx = new Entity_type_expressionContext(_ctx, getState());
		enterRule(_localctx, 166, RULE_entity_type_expression);
		try {
			setState(1064);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,114,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1061);
				type_discriminator();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1062);
				entity_type_literal();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1063);
				input_parameter();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Type_discriminatorContext extends ParserRuleContext {
		public TerminalNode TYPE() { return getToken(JpqlParser.TYPE, 0); }
		public General_identification_variableContext general_identification_variable() {
			return getRuleContext(General_identification_variableContext.class,0);
		}
		public Single_valued_object_path_expressionContext single_valued_object_path_expression() {
			return getRuleContext(Single_valued_object_path_expressionContext.class,0);
		}
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Type_discriminatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_type_discriminator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterType_discriminator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitType_discriminator(this);
		}
	}

	public final Type_discriminatorContext type_discriminator() throws RecognitionException {
		Type_discriminatorContext _localctx = new Type_discriminatorContext(_ctx, getState());
		enterRule(_localctx, 168, RULE_type_discriminator);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1066);
			match(TYPE);
			setState(1067);
			match(T__1);
			setState(1071);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,115,_ctx) ) {
			case 1:
				{
				setState(1068);
				general_identification_variable();
				}
				break;
			case 2:
				{
				setState(1069);
				single_valued_object_path_expression();
				}
				break;
			case 3:
				{
				setState(1070);
				input_parameter();
				}
				break;
			}
			setState(1073);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Functions_returning_numericsContext extends ParserRuleContext {
		public TerminalNode LENGTH() { return getToken(JpqlParser.LENGTH, 0); }
		public List<String_expressionContext> string_expression() {
			return getRuleContexts(String_expressionContext.class);
		}
		public String_expressionContext string_expression(int i) {
			return getRuleContext(String_expressionContext.class,i);
		}
		public TerminalNode LOCATE() { return getToken(JpqlParser.LOCATE, 0); }
		public List<Arithmetic_expressionContext> arithmetic_expression() {
			return getRuleContexts(Arithmetic_expressionContext.class);
		}
		public Arithmetic_expressionContext arithmetic_expression(int i) {
			return getRuleContext(Arithmetic_expressionContext.class,i);
		}
		public TerminalNode ABS() { return getToken(JpqlParser.ABS, 0); }
		public TerminalNode CEILING() { return getToken(JpqlParser.CEILING, 0); }
		public TerminalNode EXP() { return getToken(JpqlParser.EXP, 0); }
		public TerminalNode FLOOR() { return getToken(JpqlParser.FLOOR, 0); }
		public TerminalNode LN() { return getToken(JpqlParser.LN, 0); }
		public TerminalNode SIGN() { return getToken(JpqlParser.SIGN, 0); }
		public TerminalNode SQRT() { return getToken(JpqlParser.SQRT, 0); }
		public TerminalNode MOD() { return getToken(JpqlParser.MOD, 0); }
		public TerminalNode POWER() { return getToken(JpqlParser.POWER, 0); }
		public TerminalNode ROUND() { return getToken(JpqlParser.ROUND, 0); }
		public TerminalNode SIZE() { return getToken(JpqlParser.SIZE, 0); }
		public Collection_valued_path_expressionContext collection_valued_path_expression() {
			return getRuleContext(Collection_valued_path_expressionContext.class,0);
		}
		public TerminalNode INDEX() { return getToken(JpqlParser.INDEX, 0); }
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Extract_datetime_fieldContext extract_datetime_field() {
			return getRuleContext(Extract_datetime_fieldContext.class,0);
		}
		public Functions_returning_numericsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functions_returning_numerics; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterFunctions_returning_numerics(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitFunctions_returning_numerics(this);
		}
	}

	public final Functions_returning_numericsContext functions_returning_numerics() throws RecognitionException {
		Functions_returning_numericsContext _localctx = new Functions_returning_numericsContext(_ctx, getState());
		enterRule(_localctx, 170, RULE_functions_returning_numerics);
		int _la;
		try {
			setState(1158);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LENGTH:
				enterOuterAlt(_localctx, 1);
				{
				setState(1075);
				match(LENGTH);
				setState(1076);
				match(T__1);
				setState(1077);
				string_expression();
				setState(1078);
				match(T__2);
				}
				break;
			case LOCATE:
				enterOuterAlt(_localctx, 2);
				{
				setState(1080);
				match(LOCATE);
				setState(1081);
				match(T__1);
				setState(1082);
				string_expression();
				setState(1083);
				match(T__0);
				setState(1084);
				string_expression();
				setState(1087);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__0) {
					{
					setState(1085);
					match(T__0);
					setState(1086);
					arithmetic_expression(0);
					}
				}

				setState(1089);
				match(T__2);
				}
				break;
			case ABS:
				enterOuterAlt(_localctx, 3);
				{
				setState(1091);
				match(ABS);
				setState(1092);
				match(T__1);
				setState(1093);
				arithmetic_expression(0);
				setState(1094);
				match(T__2);
				}
				break;
			case CEILING:
				enterOuterAlt(_localctx, 4);
				{
				setState(1096);
				match(CEILING);
				setState(1097);
				match(T__1);
				setState(1098);
				arithmetic_expression(0);
				setState(1099);
				match(T__2);
				}
				break;
			case EXP:
				enterOuterAlt(_localctx, 5);
				{
				setState(1101);
				match(EXP);
				setState(1102);
				match(T__1);
				setState(1103);
				arithmetic_expression(0);
				setState(1104);
				match(T__2);
				}
				break;
			case FLOOR:
				enterOuterAlt(_localctx, 6);
				{
				setState(1106);
				match(FLOOR);
				setState(1107);
				match(T__1);
				setState(1108);
				arithmetic_expression(0);
				setState(1109);
				match(T__2);
				}
				break;
			case LN:
				enterOuterAlt(_localctx, 7);
				{
				setState(1111);
				match(LN);
				setState(1112);
				match(T__1);
				setState(1113);
				arithmetic_expression(0);
				setState(1114);
				match(T__2);
				}
				break;
			case SIGN:
				enterOuterAlt(_localctx, 8);
				{
				setState(1116);
				match(SIGN);
				setState(1117);
				match(T__1);
				setState(1118);
				arithmetic_expression(0);
				setState(1119);
				match(T__2);
				}
				break;
			case SQRT:
				enterOuterAlt(_localctx, 9);
				{
				setState(1121);
				match(SQRT);
				setState(1122);
				match(T__1);
				setState(1123);
				arithmetic_expression(0);
				setState(1124);
				match(T__2);
				}
				break;
			case MOD:
				enterOuterAlt(_localctx, 10);
				{
				setState(1126);
				match(MOD);
				setState(1127);
				match(T__1);
				setState(1128);
				arithmetic_expression(0);
				setState(1129);
				match(T__0);
				setState(1130);
				arithmetic_expression(0);
				setState(1131);
				match(T__2);
				}
				break;
			case POWER:
				enterOuterAlt(_localctx, 11);
				{
				setState(1133);
				match(POWER);
				setState(1134);
				match(T__1);
				setState(1135);
				arithmetic_expression(0);
				setState(1136);
				match(T__0);
				setState(1137);
				arithmetic_expression(0);
				setState(1138);
				match(T__2);
				}
				break;
			case ROUND:
				enterOuterAlt(_localctx, 12);
				{
				setState(1140);
				match(ROUND);
				setState(1141);
				match(T__1);
				setState(1142);
				arithmetic_expression(0);
				setState(1143);
				match(T__0);
				setState(1144);
				arithmetic_expression(0);
				setState(1145);
				match(T__2);
				}
				break;
			case SIZE:
				enterOuterAlt(_localctx, 13);
				{
				setState(1147);
				match(SIZE);
				setState(1148);
				match(T__1);
				setState(1149);
				collection_valued_path_expression();
				setState(1150);
				match(T__2);
				}
				break;
			case INDEX:
				enterOuterAlt(_localctx, 14);
				{
				setState(1152);
				match(INDEX);
				setState(1153);
				match(T__1);
				setState(1154);
				identification_variable();
				setState(1155);
				match(T__2);
				}
				break;
			case EXTRACT:
				enterOuterAlt(_localctx, 15);
				{
				setState(1157);
				extract_datetime_field();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Functions_returning_datetimeContext extends ParserRuleContext {
		public TerminalNode CURRENT_DATE() { return getToken(JpqlParser.CURRENT_DATE, 0); }
		public TerminalNode CURRENT_TIME() { return getToken(JpqlParser.CURRENT_TIME, 0); }
		public TerminalNode CURRENT_TIMESTAMP() { return getToken(JpqlParser.CURRENT_TIMESTAMP, 0); }
		public TerminalNode LOCAL() { return getToken(JpqlParser.LOCAL, 0); }
		public TerminalNode DATE() { return getToken(JpqlParser.DATE, 0); }
		public TerminalNode TIME() { return getToken(JpqlParser.TIME, 0); }
		public TerminalNode DATETIME() { return getToken(JpqlParser.DATETIME, 0); }
		public Extract_datetime_partContext extract_datetime_part() {
			return getRuleContext(Extract_datetime_partContext.class,0);
		}
		public Functions_returning_datetimeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functions_returning_datetime; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterFunctions_returning_datetime(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitFunctions_returning_datetime(this);
		}
	}

	public final Functions_returning_datetimeContext functions_returning_datetime() throws RecognitionException {
		Functions_returning_datetimeContext _localctx = new Functions_returning_datetimeContext(_ctx, getState());
		enterRule(_localctx, 172, RULE_functions_returning_datetime);
		try {
			setState(1170);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,118,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1160);
				match(CURRENT_DATE);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1161);
				match(CURRENT_TIME);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1162);
				match(CURRENT_TIMESTAMP);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1163);
				match(LOCAL);
				setState(1164);
				match(DATE);
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1165);
				match(LOCAL);
				setState(1166);
				match(TIME);
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(1167);
				match(LOCAL);
				setState(1168);
				match(DATETIME);
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(1169);
				extract_datetime_part();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Functions_returning_stringsContext extends ParserRuleContext {
		public TerminalNode CONCAT() { return getToken(JpqlParser.CONCAT, 0); }
		public List<String_expressionContext> string_expression() {
			return getRuleContexts(String_expressionContext.class);
		}
		public String_expressionContext string_expression(int i) {
			return getRuleContext(String_expressionContext.class,i);
		}
		public TerminalNode SUBSTRING() { return getToken(JpqlParser.SUBSTRING, 0); }
		public List<Arithmetic_expressionContext> arithmetic_expression() {
			return getRuleContexts(Arithmetic_expressionContext.class);
		}
		public Arithmetic_expressionContext arithmetic_expression(int i) {
			return getRuleContext(Arithmetic_expressionContext.class,i);
		}
		public TerminalNode TRIM() { return getToken(JpqlParser.TRIM, 0); }
		public TerminalNode FROM() { return getToken(JpqlParser.FROM, 0); }
		public Trim_specificationContext trim_specification() {
			return getRuleContext(Trim_specificationContext.class,0);
		}
		public Trim_characterContext trim_character() {
			return getRuleContext(Trim_characterContext.class,0);
		}
		public TerminalNode LOWER() { return getToken(JpqlParser.LOWER, 0); }
		public TerminalNode UPPER() { return getToken(JpqlParser.UPPER, 0); }
		public Functions_returning_stringsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functions_returning_strings; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterFunctions_returning_strings(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitFunctions_returning_strings(this);
		}
	}

	public final Functions_returning_stringsContext functions_returning_strings() throws RecognitionException {
		Functions_returning_stringsContext _localctx = new Functions_returning_stringsContext(_ctx, getState());
		enterRule(_localctx, 174, RULE_functions_returning_strings);
		int _la;
		try {
			setState(1221);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case CONCAT:
				enterOuterAlt(_localctx, 1);
				{
				setState(1172);
				match(CONCAT);
				setState(1173);
				match(T__1);
				setState(1174);
				string_expression();
				setState(1175);
				match(T__0);
				setState(1176);
				string_expression();
				setState(1181);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__0) {
					{
					{
					setState(1177);
					match(T__0);
					setState(1178);
					string_expression();
					}
					}
					setState(1183);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(1184);
				match(T__2);
				}
				break;
			case SUBSTRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(1186);
				match(SUBSTRING);
				setState(1187);
				match(T__1);
				setState(1188);
				string_expression();
				setState(1189);
				match(T__0);
				setState(1190);
				arithmetic_expression(0);
				setState(1193);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__0) {
					{
					setState(1191);
					match(T__0);
					setState(1192);
					arithmetic_expression(0);
					}
				}

				setState(1195);
				match(T__2);
				}
				break;
			case TRIM:
				enterOuterAlt(_localctx, 3);
				{
				setState(1197);
				match(TRIM);
				setState(1198);
				match(T__1);
				setState(1206);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,123,_ctx) ) {
				case 1:
					{
					setState(1200);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==BOTH || _la==LEADING || _la==TRAILING) {
						{
						setState(1199);
						trim_specification();
						}
					}

					setState(1203);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==T__13 || _la==T__14 || _la==CHARACTER) {
						{
						setState(1202);
						trim_character();
						}
					}

					setState(1205);
					match(FROM);
					}
					break;
				}
				setState(1208);
				string_expression();
				setState(1209);
				match(T__2);
				}
				break;
			case LOWER:
				enterOuterAlt(_localctx, 4);
				{
				setState(1211);
				match(LOWER);
				setState(1212);
				match(T__1);
				setState(1213);
				string_expression();
				setState(1214);
				match(T__2);
				}
				break;
			case UPPER:
				enterOuterAlt(_localctx, 5);
				{
				setState(1216);
				match(UPPER);
				setState(1217);
				match(T__1);
				setState(1218);
				string_expression();
				setState(1219);
				match(T__2);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Trim_specificationContext extends ParserRuleContext {
		public TerminalNode LEADING() { return getToken(JpqlParser.LEADING, 0); }
		public TerminalNode TRAILING() { return getToken(JpqlParser.TRAILING, 0); }
		public TerminalNode BOTH() { return getToken(JpqlParser.BOTH, 0); }
		public Trim_specificationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_trim_specification; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterTrim_specification(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitTrim_specification(this);
		}
	}

	public final Trim_specificationContext trim_specification() throws RecognitionException {
		Trim_specificationContext _localctx = new Trim_specificationContext(_ctx, getState());
		enterRule(_localctx, 176, RULE_trim_specification);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1223);
			_la = _input.LA(1);
			if ( !(_la==BOTH || _la==LEADING || _la==TRAILING) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Function_invocationContext extends ParserRuleContext {
		public TerminalNode FUNCTION() { return getToken(JpqlParser.FUNCTION, 0); }
		public Function_nameContext function_name() {
			return getRuleContext(Function_nameContext.class,0);
		}
		public List<Function_argContext> function_arg() {
			return getRuleContexts(Function_argContext.class);
		}
		public Function_argContext function_arg(int i) {
			return getRuleContext(Function_argContext.class,i);
		}
		public Function_invocationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_function_invocation; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterFunction_invocation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitFunction_invocation(this);
		}
	}

	public final Function_invocationContext function_invocation() throws RecognitionException {
		Function_invocationContext _localctx = new Function_invocationContext(_ctx, getState());
		enterRule(_localctx, 178, RULE_function_invocation);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1225);
			match(FUNCTION);
			setState(1226);
			match(T__1);
			setState(1227);
			function_name();
			setState(1232);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(1228);
				match(T__0);
				setState(1229);
				function_arg();
				}
				}
				setState(1234);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(1235);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Extract_datetime_fieldContext extends ParserRuleContext {
		public TerminalNode EXTRACT() { return getToken(JpqlParser.EXTRACT, 0); }
		public Datetime_fieldContext datetime_field() {
			return getRuleContext(Datetime_fieldContext.class,0);
		}
		public TerminalNode FROM() { return getToken(JpqlParser.FROM, 0); }
		public Datetime_expressionContext datetime_expression() {
			return getRuleContext(Datetime_expressionContext.class,0);
		}
		public Extract_datetime_fieldContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_extract_datetime_field; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterExtract_datetime_field(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitExtract_datetime_field(this);
		}
	}

	public final Extract_datetime_fieldContext extract_datetime_field() throws RecognitionException {
		Extract_datetime_fieldContext _localctx = new Extract_datetime_fieldContext(_ctx, getState());
		enterRule(_localctx, 180, RULE_extract_datetime_field);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1237);
			match(EXTRACT);
			setState(1238);
			match(T__1);
			setState(1239);
			datetime_field();
			setState(1240);
			match(FROM);
			setState(1241);
			datetime_expression();
			setState(1242);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Datetime_fieldContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Datetime_fieldContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_datetime_field; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterDatetime_field(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitDatetime_field(this);
		}
	}

	public final Datetime_fieldContext datetime_field() throws RecognitionException {
		Datetime_fieldContext _localctx = new Datetime_fieldContext(_ctx, getState());
		enterRule(_localctx, 182, RULE_datetime_field);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1244);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Extract_datetime_partContext extends ParserRuleContext {
		public TerminalNode EXTRACT() { return getToken(JpqlParser.EXTRACT, 0); }
		public Datetime_partContext datetime_part() {
			return getRuleContext(Datetime_partContext.class,0);
		}
		public TerminalNode FROM() { return getToken(JpqlParser.FROM, 0); }
		public Datetime_expressionContext datetime_expression() {
			return getRuleContext(Datetime_expressionContext.class,0);
		}
		public Extract_datetime_partContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_extract_datetime_part; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterExtract_datetime_part(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitExtract_datetime_part(this);
		}
	}

	public final Extract_datetime_partContext extract_datetime_part() throws RecognitionException {
		Extract_datetime_partContext _localctx = new Extract_datetime_partContext(_ctx, getState());
		enterRule(_localctx, 184, RULE_extract_datetime_part);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1246);
			match(EXTRACT);
			setState(1247);
			match(T__1);
			setState(1248);
			datetime_part();
			setState(1249);
			match(FROM);
			setState(1250);
			datetime_expression();
			setState(1251);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Datetime_partContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Datetime_partContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_datetime_part; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterDatetime_part(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitDatetime_part(this);
		}
	}

	public final Datetime_partContext datetime_part() throws RecognitionException {
		Datetime_partContext _localctx = new Datetime_partContext(_ctx, getState());
		enterRule(_localctx, 186, RULE_datetime_part);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1253);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Function_argContext extends ParserRuleContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public State_valued_path_expressionContext state_valued_path_expression() {
			return getRuleContext(State_valued_path_expressionContext.class,0);
		}
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Scalar_expressionContext scalar_expression() {
			return getRuleContext(Scalar_expressionContext.class,0);
		}
		public Function_argContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_function_arg; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterFunction_arg(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitFunction_arg(this);
		}
	}

	public final Function_argContext function_arg() throws RecognitionException {
		Function_argContext _localctx = new Function_argContext(_ctx, getState());
		enterRule(_localctx, 188, RULE_function_arg);
		try {
			setState(1259);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,126,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1255);
				literal();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1256);
				state_valued_path_expression();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1257);
				input_parameter();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1258);
				scalar_expression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Case_expressionContext extends ParserRuleContext {
		public General_case_expressionContext general_case_expression() {
			return getRuleContext(General_case_expressionContext.class,0);
		}
		public Simple_case_expressionContext simple_case_expression() {
			return getRuleContext(Simple_case_expressionContext.class,0);
		}
		public Coalesce_expressionContext coalesce_expression() {
			return getRuleContext(Coalesce_expressionContext.class,0);
		}
		public Nullif_expressionContext nullif_expression() {
			return getRuleContext(Nullif_expressionContext.class,0);
		}
		public Case_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_case_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterCase_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitCase_expression(this);
		}
	}

	public final Case_expressionContext case_expression() throws RecognitionException {
		Case_expressionContext _localctx = new Case_expressionContext(_ctx, getState());
		enterRule(_localctx, 190, RULE_case_expression);
		try {
			setState(1265);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,127,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1261);
				general_case_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1262);
				simple_case_expression();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1263);
				coalesce_expression();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1264);
				nullif_expression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class General_case_expressionContext extends ParserRuleContext {
		public TerminalNode CASE() { return getToken(JpqlParser.CASE, 0); }
		public List<When_clauseContext> when_clause() {
			return getRuleContexts(When_clauseContext.class);
		}
		public When_clauseContext when_clause(int i) {
			return getRuleContext(When_clauseContext.class,i);
		}
		public TerminalNode ELSE() { return getToken(JpqlParser.ELSE, 0); }
		public Scalar_expressionContext scalar_expression() {
			return getRuleContext(Scalar_expressionContext.class,0);
		}
		public TerminalNode END() { return getToken(JpqlParser.END, 0); }
		public General_case_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_general_case_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterGeneral_case_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitGeneral_case_expression(this);
		}
	}

	public final General_case_expressionContext general_case_expression() throws RecognitionException {
		General_case_expressionContext _localctx = new General_case_expressionContext(_ctx, getState());
		enterRule(_localctx, 192, RULE_general_case_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1267);
			match(CASE);
			setState(1268);
			when_clause();
			setState(1272);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==WHEN) {
				{
				{
				setState(1269);
				when_clause();
				}
				}
				setState(1274);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(1275);
			match(ELSE);
			setState(1276);
			scalar_expression();
			setState(1277);
			match(END);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class When_clauseContext extends ParserRuleContext {
		public TerminalNode WHEN() { return getToken(JpqlParser.WHEN, 0); }
		public Conditional_expressionContext conditional_expression() {
			return getRuleContext(Conditional_expressionContext.class,0);
		}
		public TerminalNode THEN() { return getToken(JpqlParser.THEN, 0); }
		public Scalar_expressionContext scalar_expression() {
			return getRuleContext(Scalar_expressionContext.class,0);
		}
		public When_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_when_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterWhen_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitWhen_clause(this);
		}
	}

	public final When_clauseContext when_clause() throws RecognitionException {
		When_clauseContext _localctx = new When_clauseContext(_ctx, getState());
		enterRule(_localctx, 194, RULE_when_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1279);
			match(WHEN);
			setState(1280);
			conditional_expression(0);
			setState(1281);
			match(THEN);
			setState(1282);
			scalar_expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Simple_case_expressionContext extends ParserRuleContext {
		public TerminalNode CASE() { return getToken(JpqlParser.CASE, 0); }
		public Case_operandContext case_operand() {
			return getRuleContext(Case_operandContext.class,0);
		}
		public List<Simple_when_clauseContext> simple_when_clause() {
			return getRuleContexts(Simple_when_clauseContext.class);
		}
		public Simple_when_clauseContext simple_when_clause(int i) {
			return getRuleContext(Simple_when_clauseContext.class,i);
		}
		public TerminalNode ELSE() { return getToken(JpqlParser.ELSE, 0); }
		public Scalar_expressionContext scalar_expression() {
			return getRuleContext(Scalar_expressionContext.class,0);
		}
		public TerminalNode END() { return getToken(JpqlParser.END, 0); }
		public Simple_case_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simple_case_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSimple_case_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSimple_case_expression(this);
		}
	}

	public final Simple_case_expressionContext simple_case_expression() throws RecognitionException {
		Simple_case_expressionContext _localctx = new Simple_case_expressionContext(_ctx, getState());
		enterRule(_localctx, 196, RULE_simple_case_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1284);
			match(CASE);
			setState(1285);
			case_operand();
			setState(1286);
			simple_when_clause();
			setState(1290);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==WHEN) {
				{
				{
				setState(1287);
				simple_when_clause();
				}
				}
				setState(1292);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(1293);
			match(ELSE);
			setState(1294);
			scalar_expression();
			setState(1295);
			match(END);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Case_operandContext extends ParserRuleContext {
		public State_valued_path_expressionContext state_valued_path_expression() {
			return getRuleContext(State_valued_path_expressionContext.class,0);
		}
		public Type_discriminatorContext type_discriminator() {
			return getRuleContext(Type_discriminatorContext.class,0);
		}
		public Case_operandContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_case_operand; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterCase_operand(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitCase_operand(this);
		}
	}

	public final Case_operandContext case_operand() throws RecognitionException {
		Case_operandContext _localctx = new Case_operandContext(_ctx, getState());
		enterRule(_localctx, 198, RULE_case_operand);
		try {
			setState(1299);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,130,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1297);
				state_valued_path_expression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1298);
				type_discriminator();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Simple_when_clauseContext extends ParserRuleContext {
		public TerminalNode WHEN() { return getToken(JpqlParser.WHEN, 0); }
		public List<Scalar_expressionContext> scalar_expression() {
			return getRuleContexts(Scalar_expressionContext.class);
		}
		public Scalar_expressionContext scalar_expression(int i) {
			return getRuleContext(Scalar_expressionContext.class,i);
		}
		public TerminalNode THEN() { return getToken(JpqlParser.THEN, 0); }
		public Simple_when_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simple_when_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSimple_when_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSimple_when_clause(this);
		}
	}

	public final Simple_when_clauseContext simple_when_clause() throws RecognitionException {
		Simple_when_clauseContext _localctx = new Simple_when_clauseContext(_ctx, getState());
		enterRule(_localctx, 200, RULE_simple_when_clause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1301);
			match(WHEN);
			setState(1302);
			scalar_expression();
			setState(1303);
			match(THEN);
			setState(1304);
			scalar_expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Coalesce_expressionContext extends ParserRuleContext {
		public TerminalNode COALESCE() { return getToken(JpqlParser.COALESCE, 0); }
		public List<Scalar_expressionContext> scalar_expression() {
			return getRuleContexts(Scalar_expressionContext.class);
		}
		public Scalar_expressionContext scalar_expression(int i) {
			return getRuleContext(Scalar_expressionContext.class,i);
		}
		public Coalesce_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_coalesce_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterCoalesce_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitCoalesce_expression(this);
		}
	}

	public final Coalesce_expressionContext coalesce_expression() throws RecognitionException {
		Coalesce_expressionContext _localctx = new Coalesce_expressionContext(_ctx, getState());
		enterRule(_localctx, 202, RULE_coalesce_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1306);
			match(COALESCE);
			setState(1307);
			match(T__1);
			setState(1308);
			scalar_expression();
			setState(1311); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(1309);
				match(T__0);
				setState(1310);
				scalar_expression();
				}
				}
				setState(1313); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==T__0 );
			setState(1315);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Nullif_expressionContext extends ParserRuleContext {
		public TerminalNode NULLIF() { return getToken(JpqlParser.NULLIF, 0); }
		public List<Scalar_expressionContext> scalar_expression() {
			return getRuleContexts(Scalar_expressionContext.class);
		}
		public Scalar_expressionContext scalar_expression(int i) {
			return getRuleContext(Scalar_expressionContext.class,i);
		}
		public Nullif_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nullif_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterNullif_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitNullif_expression(this);
		}
	}

	public final Nullif_expressionContext nullif_expression() throws RecognitionException {
		Nullif_expressionContext _localctx = new Nullif_expressionContext(_ctx, getState());
		enterRule(_localctx, 204, RULE_nullif_expression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1317);
			match(NULLIF);
			setState(1318);
			match(T__1);
			setState(1319);
			scalar_expression();
			setState(1320);
			match(T__0);
			setState(1321);
			scalar_expression();
			setState(1322);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Trim_characterContext extends ParserRuleContext {
		public TerminalNode CHARACTER() { return getToken(JpqlParser.CHARACTER, 0); }
		public Character_valued_input_parameterContext character_valued_input_parameter() {
			return getRuleContext(Character_valued_input_parameterContext.class,0);
		}
		public Trim_characterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_trim_character; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterTrim_character(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitTrim_character(this);
		}
	}

	public final Trim_characterContext trim_character() throws RecognitionException {
		Trim_characterContext _localctx = new Trim_characterContext(_ctx, getState());
		enterRule(_localctx, 206, RULE_trim_character);
		try {
			setState(1326);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,132,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1324);
				match(CHARACTER);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1325);
				character_valued_input_parameter();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Identification_variableContext extends ParserRuleContext {
		public Token f;
		public TerminalNode IDENTIFICATION_VARIABLE() { return getToken(JpqlParser.IDENTIFICATION_VARIABLE, 0); }
		public TerminalNode AVG() { return getToken(JpqlParser.AVG, 0); }
		public TerminalNode COUNT() { return getToken(JpqlParser.COUNT, 0); }
		public TerminalNode DATE() { return getToken(JpqlParser.DATE, 0); }
		public TerminalNode FROM() { return getToken(JpqlParser.FROM, 0); }
		public TerminalNode INNER() { return getToken(JpqlParser.INNER, 0); }
		public TerminalNode KEY() { return getToken(JpqlParser.KEY, 0); }
		public TerminalNode LEFT() { return getToken(JpqlParser.LEFT, 0); }
		public TerminalNode MAX() { return getToken(JpqlParser.MAX, 0); }
		public TerminalNode MIN() { return getToken(JpqlParser.MIN, 0); }
		public TerminalNode NEW() { return getToken(JpqlParser.NEW, 0); }
		public TerminalNode ORDER() { return getToken(JpqlParser.ORDER, 0); }
		public TerminalNode OUTER() { return getToken(JpqlParser.OUTER, 0); }
		public TerminalNode POWER() { return getToken(JpqlParser.POWER, 0); }
		public TerminalNode FLOOR() { return getToken(JpqlParser.FLOOR, 0); }
		public TerminalNode SIGN() { return getToken(JpqlParser.SIGN, 0); }
		public TerminalNode SUM() { return getToken(JpqlParser.SUM, 0); }
		public TerminalNode TIME() { return getToken(JpqlParser.TIME, 0); }
		public TerminalNode TYPE() { return getToken(JpqlParser.TYPE, 0); }
		public TerminalNode VALUE() { return getToken(JpqlParser.VALUE, 0); }
		public Identification_variableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identification_variable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterIdentification_variable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitIdentification_variable(this);
		}
	}

	public final Identification_variableContext identification_variable() throws RecognitionException {
		Identification_variableContext _localctx = new Identification_variableContext(_ctx, getState());
		enterRule(_localctx, 208, RULE_identification_variable);
		int _la;
		try {
			setState(1330);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENTIFICATION_VARIABLE:
				enterOuterAlt(_localctx, 1);
				{
				setState(1328);
				match(IDENTIFICATION_VARIABLE);
				}
				break;
			case AVG:
			case COUNT:
			case DATE:
			case FLOOR:
			case FROM:
			case INNER:
			case KEY:
			case LEFT:
			case MAX:
			case MIN:
			case NEW:
			case ORDER:
			case OUTER:
			case POWER:
			case SIGN:
			case SUM:
			case TIME:
			case TYPE:
			case VALUE:
				enterOuterAlt(_localctx, 2);
				{
				setState(1329);
				((Identification_variableContext)_localctx).f = _input.LT(1);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 5912100447346229248L) != 0) || ((((_la - 69)) & ~0x3f) == 0 && ((1L << (_la - 69)) & 9705910293L) != 0)) ) {
					((Identification_variableContext)_localctx).f = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Constructor_nameContext extends ParserRuleContext {
		public State_field_path_expressionContext state_field_path_expression() {
			return getRuleContext(State_field_path_expressionContext.class,0);
		}
		public Constructor_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constructor_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterConstructor_name(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitConstructor_name(this);
		}
	}

	public final Constructor_nameContext constructor_name() throws RecognitionException {
		Constructor_nameContext _localctx = new Constructor_nameContext(_ctx, getState());
		enterRule(_localctx, 210, RULE_constructor_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1332);
			state_field_path_expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LiteralContext extends ParserRuleContext {
		public TerminalNode STRINGLITERAL() { return getToken(JpqlParser.STRINGLITERAL, 0); }
		public TerminalNode JAVASTRINGLITERAL() { return getToken(JpqlParser.JAVASTRINGLITERAL, 0); }
		public TerminalNode INTLITERAL() { return getToken(JpqlParser.INTLITERAL, 0); }
		public TerminalNode FLOATLITERAL() { return getToken(JpqlParser.FLOATLITERAL, 0); }
		public TerminalNode LONGLITERAL() { return getToken(JpqlParser.LONGLITERAL, 0); }
		public Boolean_literalContext boolean_literal() {
			return getRuleContext(Boolean_literalContext.class,0);
		}
		public Entity_type_literalContext entity_type_literal() {
			return getRuleContext(Entity_type_literalContext.class,0);
		}
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitLiteral(this);
		}
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 212, RULE_literal);
		try {
			setState(1341);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRINGLITERAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(1334);
				match(STRINGLITERAL);
				}
				break;
			case JAVASTRINGLITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(1335);
				match(JAVASTRINGLITERAL);
				}
				break;
			case INTLITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(1336);
				match(INTLITERAL);
				}
				break;
			case FLOATLITERAL:
				enterOuterAlt(_localctx, 4);
				{
				setState(1337);
				match(FLOATLITERAL);
				}
				break;
			case LONGLITERAL:
				enterOuterAlt(_localctx, 5);
				{
				setState(1338);
				match(LONGLITERAL);
				}
				break;
			case FALSE:
			case TRUE:
				enterOuterAlt(_localctx, 6);
				{
				setState(1339);
				boolean_literal();
				}
				break;
			case AVG:
			case COUNT:
			case DATE:
			case FLOOR:
			case FROM:
			case INNER:
			case KEY:
			case LEFT:
			case MAX:
			case MIN:
			case NEW:
			case ORDER:
			case OUTER:
			case POWER:
			case SIGN:
			case SUM:
			case TIME:
			case TYPE:
			case VALUE:
			case IDENTIFICATION_VARIABLE:
				enterOuterAlt(_localctx, 7);
				{
				setState(1340);
				entity_type_literal();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Input_parameterContext extends ParserRuleContext {
		public TerminalNode SPEL() { return getToken(JpqlParser.SPEL, 0); }
		public TerminalNode INTLITERAL() { return getToken(JpqlParser.INTLITERAL, 0); }
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Input_parameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_input_parameter; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterInput_parameter(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitInput_parameter(this);
		}
	}

	public final Input_parameterContext input_parameter() throws RecognitionException {
		Input_parameterContext _localctx = new Input_parameterContext(_ctx, getState());
		enterRule(_localctx, 214, RULE_input_parameter);
		try {
			setState(1351);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,135,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1343);
				match(T__13);
				setState(1344);
				match(SPEL);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1345);
				match(T__14);
				setState(1346);
				match(SPEL);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1347);
				match(T__14);
				setState(1348);
				match(INTLITERAL);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1349);
				match(T__13);
				setState(1350);
				identification_variable();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Pattern_valueContext extends ParserRuleContext {
		public String_expressionContext string_expression() {
			return getRuleContext(String_expressionContext.class,0);
		}
		public Pattern_valueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pattern_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterPattern_value(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitPattern_value(this);
		}
	}

	public final Pattern_valueContext pattern_value() throws RecognitionException {
		Pattern_valueContext _localctx = new Pattern_valueContext(_ctx, getState());
		enterRule(_localctx, 216, RULE_pattern_value);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1353);
			string_expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Date_time_timestamp_literalContext extends ParserRuleContext {
		public TerminalNode STRINGLITERAL() { return getToken(JpqlParser.STRINGLITERAL, 0); }
		public Date_time_timestamp_literalContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_date_time_timestamp_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterDate_time_timestamp_literal(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitDate_time_timestamp_literal(this);
		}
	}

	public final Date_time_timestamp_literalContext date_time_timestamp_literal() throws RecognitionException {
		Date_time_timestamp_literalContext _localctx = new Date_time_timestamp_literalContext(_ctx, getState());
		enterRule(_localctx, 218, RULE_date_time_timestamp_literal);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1355);
			match(STRINGLITERAL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Entity_type_literalContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Entity_type_literalContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_entity_type_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterEntity_type_literal(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitEntity_type_literal(this);
		}
	}

	public final Entity_type_literalContext entity_type_literal() throws RecognitionException {
		Entity_type_literalContext _localctx = new Entity_type_literalContext(_ctx, getState());
		enterRule(_localctx, 220, RULE_entity_type_literal);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1357);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Escape_characterContext extends ParserRuleContext {
		public TerminalNode CHARACTER() { return getToken(JpqlParser.CHARACTER, 0); }
		public Character_valued_input_parameterContext character_valued_input_parameter() {
			return getRuleContext(Character_valued_input_parameterContext.class,0);
		}
		public Escape_characterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_escape_character; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterEscape_character(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitEscape_character(this);
		}
	}

	public final Escape_characterContext escape_character() throws RecognitionException {
		Escape_characterContext _localctx = new Escape_characterContext(_ctx, getState());
		enterRule(_localctx, 222, RULE_escape_character);
		try {
			setState(1361);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,136,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1359);
				match(CHARACTER);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1360);
				character_valued_input_parameter();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Numeric_literalContext extends ParserRuleContext {
		public TerminalNode INTLITERAL() { return getToken(JpqlParser.INTLITERAL, 0); }
		public TerminalNode FLOATLITERAL() { return getToken(JpqlParser.FLOATLITERAL, 0); }
		public TerminalNode LONGLITERAL() { return getToken(JpqlParser.LONGLITERAL, 0); }
		public Numeric_literalContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_numeric_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterNumeric_literal(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitNumeric_literal(this);
		}
	}

	public final Numeric_literalContext numeric_literal() throws RecognitionException {
		Numeric_literalContext _localctx = new Numeric_literalContext(_ctx, getState());
		enterRule(_localctx, 224, RULE_numeric_literal);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1363);
			_la = _input.LA(1);
			if ( !(((((_la - 111)) & ~0x3f) == 0 && ((1L << (_la - 111)) & 7L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Boolean_literalContext extends ParserRuleContext {
		public TerminalNode TRUE() { return getToken(JpqlParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(JpqlParser.FALSE, 0); }
		public Boolean_literalContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_boolean_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterBoolean_literal(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitBoolean_literal(this);
		}
	}

	public final Boolean_literalContext boolean_literal() throws RecognitionException {
		Boolean_literalContext _localctx = new Boolean_literalContext(_ctx, getState());
		enterRule(_localctx, 226, RULE_boolean_literal);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1365);
			_la = _input.LA(1);
			if ( !(_la==FALSE || _la==TRUE) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Enum_literalContext extends ParserRuleContext {
		public State_field_path_expressionContext state_field_path_expression() {
			return getRuleContext(State_field_path_expressionContext.class,0);
		}
		public Enum_literalContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enum_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterEnum_literal(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitEnum_literal(this);
		}
	}

	public final Enum_literalContext enum_literal() throws RecognitionException {
		Enum_literalContext _localctx = new Enum_literalContext(_ctx, getState());
		enterRule(_localctx, 228, RULE_enum_literal);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1367);
			state_field_path_expression();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class String_literalContext extends ParserRuleContext {
		public TerminalNode CHARACTER() { return getToken(JpqlParser.CHARACTER, 0); }
		public TerminalNode STRINGLITERAL() { return getToken(JpqlParser.STRINGLITERAL, 0); }
		public String_literalContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_string_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterString_literal(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitString_literal(this);
		}
	}

	public final String_literalContext string_literal() throws RecognitionException {
		String_literalContext _localctx = new String_literalContext(_ctx, getState());
		enterRule(_localctx, 230, RULE_string_literal);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1369);
			_la = _input.LA(1);
			if ( !(_la==CHARACTER || _la==STRINGLITERAL) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Single_valued_embeddable_object_fieldContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Single_valued_embeddable_object_fieldContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_single_valued_embeddable_object_field; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSingle_valued_embeddable_object_field(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSingle_valued_embeddable_object_field(this);
		}
	}

	public final Single_valued_embeddable_object_fieldContext single_valued_embeddable_object_field() throws RecognitionException {
		Single_valued_embeddable_object_fieldContext _localctx = new Single_valued_embeddable_object_fieldContext(_ctx, getState());
		enterRule(_localctx, 232, RULE_single_valued_embeddable_object_field);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1371);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SubtypeContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public SubtypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_subtype; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSubtype(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSubtype(this);
		}
	}

	public final SubtypeContext subtype() throws RecognitionException {
		SubtypeContext _localctx = new SubtypeContext(_ctx, getState());
		enterRule(_localctx, 234, RULE_subtype);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1373);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Collection_valued_fieldContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Collection_valued_fieldContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_collection_valued_field; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterCollection_valued_field(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitCollection_valued_field(this);
		}
	}

	public final Collection_valued_fieldContext collection_valued_field() throws RecognitionException {
		Collection_valued_fieldContext _localctx = new Collection_valued_fieldContext(_ctx, getState());
		enterRule(_localctx, 236, RULE_collection_valued_field);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1375);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Single_valued_object_fieldContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Single_valued_object_fieldContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_single_valued_object_field; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSingle_valued_object_field(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSingle_valued_object_field(this);
		}
	}

	public final Single_valued_object_fieldContext single_valued_object_field() throws RecognitionException {
		Single_valued_object_fieldContext _localctx = new Single_valued_object_fieldContext(_ctx, getState());
		enterRule(_localctx, 238, RULE_single_valued_object_field);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1377);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class State_fieldContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public State_fieldContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_state_field; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterState_field(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitState_field(this);
		}
	}

	public final State_fieldContext state_field() throws RecognitionException {
		State_fieldContext _localctx = new State_fieldContext(_ctx, getState());
		enterRule(_localctx, 240, RULE_state_field);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1379);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Collection_value_fieldContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Collection_value_fieldContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_collection_value_field; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterCollection_value_field(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitCollection_value_field(this);
		}
	}

	public final Collection_value_fieldContext collection_value_field() throws RecognitionException {
		Collection_value_fieldContext _localctx = new Collection_value_fieldContext(_ctx, getState());
		enterRule(_localctx, 242, RULE_collection_value_field);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1381);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Entity_nameContext extends ParserRuleContext {
		public List<Identification_variableContext> identification_variable() {
			return getRuleContexts(Identification_variableContext.class);
		}
		public Identification_variableContext identification_variable(int i) {
			return getRuleContext(Identification_variableContext.class,i);
		}
		public Entity_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_entity_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterEntity_name(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitEntity_name(this);
		}
	}

	public final Entity_nameContext entity_name() throws RecognitionException {
		Entity_nameContext _localctx = new Entity_nameContext(_ctx, getState());
		enterRule(_localctx, 244, RULE_entity_name);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1383);
			identification_variable();
			setState(1388);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__3) {
				{
				{
				setState(1384);
				match(T__3);
				setState(1385);
				identification_variable();
				}
				}
				setState(1390);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Result_variableContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Result_variableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_result_variable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterResult_variable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitResult_variable(this);
		}
	}

	public final Result_variableContext result_variable() throws RecognitionException {
		Result_variableContext _localctx = new Result_variableContext(_ctx, getState());
		enterRule(_localctx, 246, RULE_result_variable);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1391);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Superquery_identification_variableContext extends ParserRuleContext {
		public Identification_variableContext identification_variable() {
			return getRuleContext(Identification_variableContext.class,0);
		}
		public Superquery_identification_variableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_superquery_identification_variable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSuperquery_identification_variable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSuperquery_identification_variable(this);
		}
	}

	public final Superquery_identification_variableContext superquery_identification_variable() throws RecognitionException {
		Superquery_identification_variableContext _localctx = new Superquery_identification_variableContext(_ctx, getState());
		enterRule(_localctx, 248, RULE_superquery_identification_variable);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1393);
			identification_variable();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Collection_valued_input_parameterContext extends ParserRuleContext {
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Collection_valued_input_parameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_collection_valued_input_parameter; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterCollection_valued_input_parameter(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitCollection_valued_input_parameter(this);
		}
	}

	public final Collection_valued_input_parameterContext collection_valued_input_parameter() throws RecognitionException {
		Collection_valued_input_parameterContext _localctx = new Collection_valued_input_parameterContext(_ctx, getState());
		enterRule(_localctx, 250, RULE_collection_valued_input_parameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1395);
			input_parameter();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Single_valued_input_parameterContext extends ParserRuleContext {
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Single_valued_input_parameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_single_valued_input_parameter; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterSingle_valued_input_parameter(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitSingle_valued_input_parameter(this);
		}
	}

	public final Single_valued_input_parameterContext single_valued_input_parameter() throws RecognitionException {
		Single_valued_input_parameterContext _localctx = new Single_valued_input_parameterContext(_ctx, getState());
		enterRule(_localctx, 252, RULE_single_valued_input_parameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1397);
			input_parameter();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Function_nameContext extends ParserRuleContext {
		public String_literalContext string_literal() {
			return getRuleContext(String_literalContext.class,0);
		}
		public Function_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_function_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterFunction_name(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitFunction_name(this);
		}
	}

	public final Function_nameContext function_name() throws RecognitionException {
		Function_nameContext _localctx = new Function_nameContext(_ctx, getState());
		enterRule(_localctx, 254, RULE_function_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1399);
			string_literal();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Character_valued_input_parameterContext extends ParserRuleContext {
		public TerminalNode CHARACTER() { return getToken(JpqlParser.CHARACTER, 0); }
		public Input_parameterContext input_parameter() {
			return getRuleContext(Input_parameterContext.class,0);
		}
		public Character_valued_input_parameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_character_valued_input_parameter; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).enterCharacter_valued_input_parameter(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JpqlListener ) ((JpqlListener)listener).exitCharacter_valued_input_parameter(this);
		}
	}

	public final Character_valued_input_parameterContext character_valued_input_parameter() throws RecognitionException {
		Character_valued_input_parameterContext _localctx = new Character_valued_input_parameterContext(_ctx, getState());
		enterRule(_localctx, 256, RULE_character_valued_input_parameter);
		try {
			setState(1403);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case CHARACTER:
				enterOuterAlt(_localctx, 1);
				{
				setState(1401);
				match(CHARACTER);
				}
				break;
			case T__13:
			case T__14:
				enterOuterAlt(_localctx, 2);
				{
				setState(1402);
				input_parameter();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 55:
			return conditional_expression_sempred((Conditional_expressionContext)_localctx, predIndex);
		case 56:
			return conditional_term_sempred((Conditional_termContext)_localctx, predIndex);
		case 73:
			return arithmetic_expression_sempred((Arithmetic_expressionContext)_localctx, predIndex);
		case 74:
			return arithmetic_term_sempred((Arithmetic_termContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean conditional_expression_sempred(Conditional_expressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 1);
		}
		return true;
	}
	private boolean conditional_term_sempred(Conditional_termContext _localctx, int predIndex) {
		switch (predIndex) {
		case 1:
			return precpred(_ctx, 1);
		}
		return true;
	}
	private boolean arithmetic_expression_sempred(Arithmetic_expressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 2:
			return precpred(_ctx, 1);
		}
		return true;
	}
	private boolean arithmetic_term_sempred(Arithmetic_termContext _localctx, int predIndex) {
		switch (predIndex) {
		case 3:
			return precpred(_ctx, 1);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001r\u057e\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
		"\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
		"\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e"+
		"\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007\"\u0002"+
		"#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007\'\u0002"+
		"(\u0007(\u0002)\u0007)\u0002*\u0007*\u0002+\u0007+\u0002,\u0007,\u0002"+
		"-\u0007-\u0002.\u0007.\u0002/\u0007/\u00020\u00070\u00021\u00071\u0002"+
		"2\u00072\u00023\u00073\u00024\u00074\u00025\u00075\u00026\u00076\u0002"+
		"7\u00077\u00028\u00078\u00029\u00079\u0002:\u0007:\u0002;\u0007;\u0002"+
		"<\u0007<\u0002=\u0007=\u0002>\u0007>\u0002?\u0007?\u0002@\u0007@\u0002"+
		"A\u0007A\u0002B\u0007B\u0002C\u0007C\u0002D\u0007D\u0002E\u0007E\u0002"+
		"F\u0007F\u0002G\u0007G\u0002H\u0007H\u0002I\u0007I\u0002J\u0007J\u0002"+
		"K\u0007K\u0002L\u0007L\u0002M\u0007M\u0002N\u0007N\u0002O\u0007O\u0002"+
		"P\u0007P\u0002Q\u0007Q\u0002R\u0007R\u0002S\u0007S\u0002T\u0007T\u0002"+
		"U\u0007U\u0002V\u0007V\u0002W\u0007W\u0002X\u0007X\u0002Y\u0007Y\u0002"+
		"Z\u0007Z\u0002[\u0007[\u0002\\\u0007\\\u0002]\u0007]\u0002^\u0007^\u0002"+
		"_\u0007_\u0002`\u0007`\u0002a\u0007a\u0002b\u0007b\u0002c\u0007c\u0002"+
		"d\u0007d\u0002e\u0007e\u0002f\u0007f\u0002g\u0007g\u0002h\u0007h\u0002"+
		"i\u0007i\u0002j\u0007j\u0002k\u0007k\u0002l\u0007l\u0002m\u0007m\u0002"+
		"n\u0007n\u0002o\u0007o\u0002p\u0007p\u0002q\u0007q\u0002r\u0007r\u0002"+
		"s\u0007s\u0002t\u0007t\u0002u\u0007u\u0002v\u0007v\u0002w\u0007w\u0002"+
		"x\u0007x\u0002y\u0007y\u0002z\u0007z\u0002{\u0007{\u0002|\u0007|\u0002"+
		"}\u0007}\u0002~\u0007~\u0002\u007f\u0007\u007f\u0002\u0080\u0007\u0080"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0003\u0001\u0109\b\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u0002"+
		"\u010e\b\u0002\u0001\u0002\u0003\u0002\u0111\b\u0002\u0001\u0002\u0003"+
		"\u0002\u0114\b\u0002\u0001\u0002\u0003\u0002\u0117\b\u0002\u0001\u0003"+
		"\u0001\u0003\u0003\u0003\u011b\b\u0003\u0001\u0004\u0001\u0004\u0003\u0004"+
		"\u011f\b\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0005\u0005"+
		"\u0125\b\u0005\n\u0005\f\u0005\u0128\t\u0005\u0001\u0006\u0001\u0006\u0003"+
		"\u0006\u012c\b\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0005\u0007\u0131"+
		"\b\u0007\n\u0007\f\u0007\u0134\t\u0007\u0001\b\u0001\b\u0003\b\u0138\b"+
		"\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001\t\u0003\t\u013f\b\t\u0001\t\u0001"+
		"\t\u0003\t\u0143\b\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\u000b\u0001"+
		"\u000b\u0003\u000b\u014b\b\u000b\u0001\u000b\u0003\u000b\u014e\b\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001"+
		"\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001"+
		"\r\u0001\r\u0001\r\u0001\r\u0001\r\u0003\r\u0165\b\r\u0001\u000e\u0001"+
		"\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u016c\b\u000e\n"+
		"\u000e\f\u000e\u016f\t\u000e\u0001\u000e\u0001\u000e\u0001\u000f\u0001"+
		"\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0005\u000f\u0178\b\u000f\n"+
		"\u000f\f\u000f\u017b\t\u000f\u0001\u000f\u0001\u000f\u0001\u0010\u0001"+
		"\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0003\u0010\u0184\b\u0010\u0001"+
		"\u0010\u0001\u0010\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001"+
		"\u0011\u0001\u0011\u0003\u0011\u018e\b\u0011\u0001\u0012\u0001\u0012\u0001"+
		"\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001"+
		"\u0012\u0001\u0012\u0003\u0012\u019a\b\u0012\u0001\u0013\u0001\u0013\u0001"+
		"\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001"+
		"\u0013\u0001\u0013\u0003\u0013\u01a6\b\u0013\u0001\u0014\u0001\u0014\u0003"+
		"\u0014\u01aa\b\u0014\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0005"+
		"\u0015\u01b0\b\u0015\n\u0015\f\u0015\u01b3\t\u0015\u0003\u0015\u01b5\b"+
		"\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0005\u0016\u01bb"+
		"\b\u0016\n\u0016\f\u0016\u01be\t\u0016\u0003\u0016\u01c0\b\u0016\u0001"+
		"\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001"+
		"\u0017\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0019\u0001"+
		"\u0019\u0003\u0019\u01cf\b\u0019\u0001\u001a\u0001\u001a\u0001\u001a\u0001"+
		"\u001a\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001c\u0001"+
		"\u001c\u0001\u001c\u0003\u001c\u01dc\b\u001c\u0001\u001c\u0003\u001c\u01df"+
		"\b\u001c\u0001\u001c\u0001\u001c\u0001\u001c\u0001\u001c\u0005\u001c\u01e5"+
		"\b\u001c\n\u001c\f\u001c\u01e8\t\u001c\u0001\u001d\u0001\u001d\u0001\u001d"+
		"\u0003\u001d\u01ed\b\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0005\u001d"+
		"\u01f2\b\u001d\n\u001d\f\u001d\u01f5\t\u001d\u0001\u001d\u0001\u001d\u0003"+
		"\u001d\u01f9\b\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001e\u0001"+
		"\u001e\u0001\u001e\u0003\u001e\u0201\b\u001e\u0001\u001f\u0001\u001f\u0001"+
		"\u001f\u0001\u001f\u0003\u001f\u0207\b\u001f\u0001\u001f\u0003\u001f\u020a"+
		"\b\u001f\u0001 \u0001 \u0003 \u020e\b \u0001 \u0001 \u0001 \u0005 \u0213"+
		"\b \n \f \u0216\t \u0001!\u0001!\u0003!\u021a\b!\u0001!\u0003!\u021d\b"+
		"!\u0001\"\u0001\"\u0001\"\u0001\"\u0001\"\u0001\"\u0001\"\u0001\"\u0001"+
		"\"\u0001\"\u0003\"\u0229\b\"\u0001#\u0001#\u0001#\u0001#\u0001#\u0001"+
		"#\u0005#\u0231\b#\n#\f#\u0234\t#\u0001#\u0001#\u0001$\u0001$\u0001$\u0001"+
		"$\u0001$\u0003$\u023d\b$\u0001%\u0001%\u0001%\u0003%\u0242\b%\u0001%\u0001"+
		"%\u0001%\u0001%\u0001%\u0001%\u0003%\u024a\b%\u0001%\u0001%\u0001%\u0003"+
		"%\u024f\b%\u0001%\u0001%\u0001%\u0003%\u0254\b%\u0001&\u0001&\u0001&\u0001"+
		"\'\u0001\'\u0001\'\u0001\'\u0001\'\u0005\'\u025e\b\'\n\'\f\'\u0261\t\'"+
		"\u0001(\u0001(\u0003(\u0265\b(\u0001)\u0001)\u0001)\u0001*\u0001*\u0001"+
		"*\u0001*\u0001*\u0005*\u026f\b*\n*\f*\u0272\t*\u0001+\u0001+\u0001+\u0003"+
		"+\u0277\b+\u0001+\u0003+\u027a\b+\u0001,\u0001,\u0001,\u0003,\u027f\b"+
		",\u0001,\u0003,\u0282\b,\u0001,\u0003,\u0285\b,\u0001-\u0001-\u0001-\u0001"+
		"-\u0001-\u0003-\u028c\b-\u0005-\u028e\b-\n-\f-\u0291\t-\u0001.\u0001."+
		"\u0001.\u0003.\u0296\b.\u0001.\u0001.\u0005.\u029a\b.\n.\f.\u029d\t.\u0001"+
		".\u0003.\u02a0\b.\u0001/\u0001/\u0001/\u0001/\u0001/\u0001/\u0001/\u0001"+
		"/\u0003/\u02aa\b/\u00010\u00010\u00010\u00010\u00050\u02b0\b0\n0\f0\u02b3"+
		"\t0\u00030\u02b5\b0\u00011\u00011\u00011\u00051\u02ba\b1\n1\f1\u02bd\t"+
		"1\u00012\u00012\u00012\u00012\u00012\u00012\u00012\u00013\u00013\u0001"+
		"3\u00013\u00013\u00013\u00053\u02cc\b3\n3\f3\u02cf\t3\u00013\u00013\u0001"+
		"4\u00014\u00034\u02d5\b4\u00014\u00014\u00015\u00015\u00015\u00015\u0003"+
		"5\u02dd\b5\u00016\u00016\u00016\u00016\u00016\u00016\u00016\u00036\u02e6"+
		"\b6\u00017\u00017\u00017\u00017\u00017\u00017\u00057\u02ee\b7\n7\f7\u02f1"+
		"\t7\u00018\u00018\u00018\u00018\u00018\u00018\u00058\u02f9\b8\n8\f8\u02fc"+
		"\t8\u00019\u00039\u02ff\b9\u00019\u00019\u0001:\u0001:\u0001:\u0001:\u0001"+
		":\u0003:\u0308\b:\u0001;\u0001;\u0001;\u0001;\u0001;\u0001;\u0001;\u0001"+
		";\u0003;\u0312\b;\u0001<\u0001<\u0003<\u0316\b<\u0001<\u0001<\u0001<\u0001"+
		"<\u0001<\u0001<\u0001<\u0003<\u031f\b<\u0001<\u0001<\u0001<\u0001<\u0001"+
		"<\u0001<\u0001<\u0003<\u0328\b<\u0001<\u0001<\u0001<\u0001<\u0001<\u0003"+
		"<\u032f\b<\u0001=\u0001=\u0003=\u0333\b=\u0001=\u0003=\u0336\b=\u0001"+
		"=\u0001=\u0001=\u0001=\u0001=\u0005=\u033d\b=\n=\f=\u0340\t=\u0001=\u0001"+
		"=\u0001=\u0001=\u0001=\u0001=\u0001=\u0003=\u0349\b=\u0001>\u0001>\u0003"+
		">\u034d\b>\u0001?\u0001?\u0003?\u0351\b?\u0001?\u0001?\u0003?\u0355\b"+
		"?\u0001?\u0001?\u0003?\u0359\b?\u0001?\u0001?\u0003?\u035d\b?\u0001@\u0001"+
		"@\u0003@\u0361\b@\u0001@\u0001@\u0003@\u0365\b@\u0001@\u0001@\u0001A\u0001"+
		"A\u0001A\u0003A\u036c\bA\u0001A\u0001A\u0001B\u0001B\u0003B\u0372\bB\u0001"+
		"B\u0001B\u0003B\u0376\bB\u0001B\u0001B\u0001C\u0001C\u0001C\u0003C\u037d"+
		"\bC\u0001D\u0001D\u0001D\u0003D\u0382\bD\u0001E\u0003E\u0385\bE\u0001"+
		"E\u0001E\u0001E\u0001E\u0001E\u0001F\u0001F\u0001F\u0001F\u0001F\u0001"+
		"G\u0001G\u0001G\u0001G\u0003G\u0395\bG\u0001G\u0001G\u0001G\u0001G\u0003"+
		"G\u039b\bG\u0001G\u0001G\u0001G\u0001G\u0003G\u03a1\bG\u0001G\u0001G\u0001"+
		"G\u0001G\u0003G\u03a7\bG\u0001G\u0001G\u0001G\u0001G\u0003G\u03ad\bG\u0001"+
		"G\u0001G\u0001G\u0001G\u0003G\u03b3\bG\u0001G\u0001G\u0001G\u0001G\u0003"+
		"G\u03b9\bG\u0001H\u0001H\u0001H\u0001H\u0001H\u0001H\u0003H\u03c1\bH\u0001"+
		"I\u0001I\u0001I\u0001I\u0001I\u0001I\u0005I\u03c9\bI\nI\fI\u03cc\tI\u0001"+
		"J\u0001J\u0001J\u0001J\u0001J\u0001J\u0005J\u03d4\bJ\nJ\fJ\u03d7\tJ\u0001"+
		"K\u0003K\u03da\bK\u0001K\u0001K\u0001L\u0001L\u0001L\u0001L\u0001L\u0001"+
		"L\u0001L\u0001L\u0001L\u0001L\u0001L\u0001L\u0001L\u0001L\u0001L\u0003"+
		"L\u03ed\bL\u0001M\u0001M\u0001M\u0001M\u0001M\u0001M\u0001M\u0001M\u0001"+
		"M\u0001M\u0001M\u0003M\u03fa\bM\u0001N\u0001N\u0001N\u0001N\u0001N\u0001"+
		"N\u0001N\u0001N\u0001N\u0001N\u0001N\u0003N\u0407\bN\u0001O\u0001O\u0001"+
		"O\u0001O\u0001O\u0001O\u0001O\u0001O\u0001O\u0003O\u0412\bO\u0001P\u0001"+
		"P\u0001P\u0001P\u0001P\u0001P\u0001P\u0001P\u0003P\u041c\bP\u0001Q\u0001"+
		"Q\u0003Q\u0420\bQ\u0001R\u0001R\u0003R\u0424\bR\u0001S\u0001S\u0001S\u0003"+
		"S\u0429\bS\u0001T\u0001T\u0001T\u0001T\u0001T\u0003T\u0430\bT\u0001T\u0001"+
		"T\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001"+
		"U\u0001U\u0001U\u0003U\u0440\bU\u0001U\u0001U\u0001U\u0001U\u0001U\u0001"+
		"U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001"+
		"U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001"+
		"U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001"+
		"U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001"+
		"U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001"+
		"U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001"+
		"U\u0001U\u0001U\u0001U\u0003U\u0487\bU\u0001V\u0001V\u0001V\u0001V\u0001"+
		"V\u0001V\u0001V\u0001V\u0001V\u0001V\u0003V\u0493\bV\u0001W\u0001W\u0001"+
		"W\u0001W\u0001W\u0001W\u0001W\u0005W\u049c\bW\nW\fW\u049f\tW\u0001W\u0001"+
		"W\u0001W\u0001W\u0001W\u0001W\u0001W\u0001W\u0001W\u0003W\u04aa\bW\u0001"+
		"W\u0001W\u0001W\u0001W\u0001W\u0003W\u04b1\bW\u0001W\u0003W\u04b4\bW\u0001"+
		"W\u0003W\u04b7\bW\u0001W\u0001W\u0001W\u0001W\u0001W\u0001W\u0001W\u0001"+
		"W\u0001W\u0001W\u0001W\u0001W\u0001W\u0003W\u04c6\bW\u0001X\u0001X\u0001"+
		"Y\u0001Y\u0001Y\u0001Y\u0001Y\u0005Y\u04cf\bY\nY\fY\u04d2\tY\u0001Y\u0001"+
		"Y\u0001Z\u0001Z\u0001Z\u0001Z\u0001Z\u0001Z\u0001Z\u0001[\u0001[\u0001"+
		"\\\u0001\\\u0001\\\u0001\\\u0001\\\u0001\\\u0001\\\u0001]\u0001]\u0001"+
		"^\u0001^\u0001^\u0001^\u0003^\u04ec\b^\u0001_\u0001_\u0001_\u0001_\u0003"+
		"_\u04f2\b_\u0001`\u0001`\u0001`\u0005`\u04f7\b`\n`\f`\u04fa\t`\u0001`"+
		"\u0001`\u0001`\u0001`\u0001a\u0001a\u0001a\u0001a\u0001a\u0001b\u0001"+
		"b\u0001b\u0001b\u0005b\u0509\bb\nb\fb\u050c\tb\u0001b\u0001b\u0001b\u0001"+
		"b\u0001c\u0001c\u0003c\u0514\bc\u0001d\u0001d\u0001d\u0001d\u0001d\u0001"+
		"e\u0001e\u0001e\u0001e\u0001e\u0004e\u0520\be\u000be\fe\u0521\u0001e\u0001"+
		"e\u0001f\u0001f\u0001f\u0001f\u0001f\u0001f\u0001f\u0001g\u0001g\u0003"+
		"g\u052f\bg\u0001h\u0001h\u0003h\u0533\bh\u0001i\u0001i\u0001j\u0001j\u0001"+
		"j\u0001j\u0001j\u0001j\u0001j\u0003j\u053e\bj\u0001k\u0001k\u0001k\u0001"+
		"k\u0001k\u0001k\u0001k\u0001k\u0003k\u0548\bk\u0001l\u0001l\u0001m\u0001"+
		"m\u0001n\u0001n\u0001o\u0001o\u0003o\u0552\bo\u0001p\u0001p\u0001q\u0001"+
		"q\u0001r\u0001r\u0001s\u0001s\u0001t\u0001t\u0001u\u0001u\u0001v\u0001"+
		"v\u0001w\u0001w\u0001x\u0001x\u0001y\u0001y\u0001z\u0001z\u0001z\u0005"+
		"z\u056b\bz\nz\fz\u056e\tz\u0001{\u0001{\u0001|\u0001|\u0001}\u0001}\u0001"+
		"~\u0001~\u0001\u007f\u0001\u007f\u0001\u0080\u0001\u0080\u0003\u0080\u057c"+
		"\b\u0080\u0001\u0080\u0000\u0004np\u0092\u0094\u0081\u0000\u0002\u0004"+
		"\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a\u001c\u001e \""+
		"$&(*,.02468:<>@BDFHJLNPRTVXZ\\^`bdfhjlnprtvxz|~\u0080\u0082\u0084\u0086"+
		"\u0088\u008a\u008c\u008e\u0090\u0092\u0094\u0096\u0098\u009a\u009c\u009e"+
		"\u00a0\u00a2\u00a4\u00a6\u00a8\u00aa\u00ac\u00ae\u00b0\u00b2\u00b4\u00b6"+
		"\u00b8\u00ba\u00bc\u00be\u00c0\u00c2\u00c4\u00c6\u00c8\u00ca\u00cc\u00ce"+
		"\u00d0\u00d2\u00d4\u00d6\u00d8\u00da\u00dc\u00de\u00e0\u00e2\u00e4\u00e6"+
		"\u00e8\u00ea\u00ec\u00ee\u00f0\u00f2\u00f4\u00f6\u00f8\u00fa\u00fc\u00fe"+
		"\u0100\u0000\u000b\u0004\u0000\u0017\u0017EEGG\\\\\u0002\u0000\u0016\u0016"+
		"&&\u0003\u0000\u0012\u0012\u0014\u0014YY\u0001\u0000ij\u0001\u0000\n\u000b"+
		"\u0001\u0000\f\r\u0003\u0000\u0019\u0019==__\u0010\u0000\u0017\u0017\u001f"+
		"\u001f##2399<<>>EEGGIIQSWW\\\\^^ccff\u0001\u0000oq\u0002\u000000bb\u0002"+
		"\u0000kkmm\u05ef\u0000\u0102\u0001\u0000\u0000\u0000\u0002\u0108\u0001"+
		"\u0000\u0000\u0000\u0004\u010a\u0001\u0000\u0000\u0000\u0006\u0118\u0001"+
		"\u0000\u0000\u0000\b\u011c\u0001\u0000\u0000\u0000\n\u0120\u0001\u0000"+
		"\u0000\u0000\f\u012b\u0001\u0000\u0000\u0000\u000e\u012d\u0001\u0000\u0000"+
		"\u0000\u0010\u0135\u0001\u0000\u0000\u0000\u0012\u013b\u0001\u0000\u0000"+
		"\u0000\u0014\u0144\u0001\u0000\u0000\u0000\u0016\u014d\u0001\u0000\u0000"+
		"\u0000\u0018\u0151\u0001\u0000\u0000\u0000\u001a\u0164\u0001\u0000\u0000"+
		"\u0000\u001c\u0166\u0001\u0000\u0000\u0000\u001e\u0172\u0001\u0000\u0000"+
		"\u0000 \u017e\u0001\u0000\u0000\u0000\"\u018d\u0001\u0000\u0000\u0000"+
		"$\u0199\u0001\u0000\u0000\u0000&\u01a5\u0001\u0000\u0000\u0000(\u01a9"+
		"\u0001\u0000\u0000\u0000*\u01b4\u0001\u0000\u0000\u0000,\u01bf\u0001\u0000"+
		"\u0000\u0000.\u01c1\u0001\u0000\u0000\u00000\u01c8\u0001\u0000\u0000\u0000"+
		"2\u01ce\u0001\u0000\u0000\u00004\u01d0\u0001\u0000\u0000\u00006\u01d4"+
		"\u0001\u0000\u0000\u00008\u01d8\u0001\u0000\u0000\u0000:\u01ec\u0001\u0000"+
		"\u0000\u0000<\u0200\u0001\u0000\u0000\u0000>\u0202\u0001\u0000\u0000\u0000"+
		"@\u020b\u0001\u0000\u0000\u0000B\u0217\u0001\u0000\u0000\u0000D\u0228"+
		"\u0001\u0000\u0000\u0000F\u022a\u0001\u0000\u0000\u0000H\u023c\u0001\u0000"+
		"\u0000\u0000J\u0253\u0001\u0000\u0000\u0000L\u0255\u0001\u0000\u0000\u0000"+
		"N\u0258\u0001\u0000\u0000\u0000P\u0264\u0001\u0000\u0000\u0000R\u0266"+
		"\u0001\u0000\u0000\u0000T\u0269\u0001\u0000\u0000\u0000V\u0276\u0001\u0000"+
		"\u0000\u0000X\u027b\u0001\u0000\u0000\u0000Z\u0286\u0001\u0000\u0000\u0000"+
		"\\\u029f\u0001\u0000\u0000\u0000^\u02a9\u0001\u0000\u0000\u0000`\u02b4"+
		"\u0001\u0000\u0000\u0000b\u02b6\u0001\u0000\u0000\u0000d\u02be\u0001\u0000"+
		"\u0000\u0000f\u02c5\u0001\u0000\u0000\u0000h\u02d2\u0001\u0000\u0000\u0000"+
		"j\u02dc\u0001\u0000\u0000\u0000l\u02e5\u0001\u0000\u0000\u0000n\u02e7"+
		"\u0001\u0000\u0000\u0000p\u02f2\u0001\u0000\u0000\u0000r\u02fe\u0001\u0000"+
		"\u0000\u0000t\u0307\u0001\u0000\u0000\u0000v\u0311\u0001\u0000\u0000\u0000"+
		"x\u032e\u0001\u0000\u0000\u0000z\u0332\u0001\u0000\u0000\u0000|\u034c"+
		"\u0001\u0000\u0000\u0000~\u034e\u0001\u0000\u0000\u0000\u0080\u0360\u0001"+
		"\u0000\u0000\u0000\u0082\u0368\u0001\u0000\u0000\u0000\u0084\u036f\u0001"+
		"\u0000\u0000\u0000\u0086\u037c\u0001\u0000\u0000\u0000\u0088\u0381\u0001"+
		"\u0000\u0000\u0000\u008a\u0384\u0001\u0000\u0000\u0000\u008c\u038b\u0001"+
		"\u0000\u0000\u0000\u008e\u03b8\u0001\u0000\u0000\u0000\u0090\u03c0\u0001"+
		"\u0000\u0000\u0000\u0092\u03c2\u0001\u0000\u0000\u0000\u0094\u03cd\u0001"+
		"\u0000\u0000\u0000\u0096\u03d9\u0001\u0000\u0000\u0000\u0098\u03ec\u0001"+
		"\u0000\u0000\u0000\u009a\u03f9\u0001\u0000\u0000\u0000\u009c\u0406\u0001"+
		"\u0000\u0000\u0000\u009e\u0411\u0001\u0000\u0000\u0000\u00a0\u041b\u0001"+
		"\u0000\u0000\u0000\u00a2\u041f\u0001\u0000\u0000\u0000\u00a4\u0423\u0001"+
		"\u0000\u0000\u0000\u00a6\u0428\u0001\u0000\u0000\u0000\u00a8\u042a\u0001"+
		"\u0000\u0000\u0000\u00aa\u0486\u0001\u0000\u0000\u0000\u00ac\u0492\u0001"+
		"\u0000\u0000\u0000\u00ae\u04c5\u0001\u0000\u0000\u0000\u00b0\u04c7\u0001"+
		"\u0000\u0000\u0000\u00b2\u04c9\u0001\u0000\u0000\u0000\u00b4\u04d5\u0001"+
		"\u0000\u0000\u0000\u00b6\u04dc\u0001\u0000\u0000\u0000\u00b8\u04de\u0001"+
		"\u0000\u0000\u0000\u00ba\u04e5\u0001\u0000\u0000\u0000\u00bc\u04eb\u0001"+
		"\u0000\u0000\u0000\u00be\u04f1\u0001\u0000\u0000\u0000\u00c0\u04f3\u0001"+
		"\u0000\u0000\u0000\u00c2\u04ff\u0001\u0000\u0000\u0000\u00c4\u0504\u0001"+
		"\u0000\u0000\u0000\u00c6\u0513\u0001\u0000\u0000\u0000\u00c8\u0515\u0001"+
		"\u0000\u0000\u0000\u00ca\u051a\u0001\u0000\u0000\u0000\u00cc\u0525\u0001"+
		"\u0000\u0000\u0000\u00ce\u052e\u0001\u0000\u0000\u0000\u00d0\u0532\u0001"+
		"\u0000\u0000\u0000\u00d2\u0534\u0001\u0000\u0000\u0000\u00d4\u053d\u0001"+
		"\u0000\u0000\u0000\u00d6\u0547\u0001\u0000\u0000\u0000\u00d8\u0549\u0001"+
		"\u0000\u0000\u0000\u00da\u054b\u0001\u0000\u0000\u0000\u00dc\u054d\u0001"+
		"\u0000\u0000\u0000\u00de\u0551\u0001\u0000\u0000\u0000\u00e0\u0553\u0001"+
		"\u0000\u0000\u0000\u00e2\u0555\u0001\u0000\u0000\u0000\u00e4\u0557\u0001"+
		"\u0000\u0000\u0000\u00e6\u0559\u0001\u0000\u0000\u0000\u00e8\u055b\u0001"+
		"\u0000\u0000\u0000\u00ea\u055d\u0001\u0000\u0000\u0000\u00ec\u055f\u0001"+
		"\u0000\u0000\u0000\u00ee\u0561\u0001\u0000\u0000\u0000\u00f0\u0563\u0001"+
		"\u0000\u0000\u0000\u00f2\u0565\u0001\u0000\u0000\u0000\u00f4\u0567\u0001"+
		"\u0000\u0000\u0000\u00f6\u056f\u0001\u0000\u0000\u0000\u00f8\u0571\u0001"+
		"\u0000\u0000\u0000\u00fa\u0573\u0001\u0000\u0000\u0000\u00fc\u0575\u0001"+
		"\u0000\u0000\u0000\u00fe\u0577\u0001\u0000\u0000\u0000\u0100\u057b\u0001"+
		"\u0000\u0000\u0000\u0102\u0103\u0003\u0002\u0001\u0000\u0103\u0104\u0005"+
		"\u0000\u0000\u0001\u0104\u0001\u0001\u0000\u0000\u0000\u0105\u0109\u0003"+
		"\u0004\u0002\u0000\u0106\u0109\u0003\u0006\u0003\u0000\u0107\u0109\u0003"+
		"\b\u0004\u0000\u0108\u0105\u0001\u0000\u0000\u0000\u0108\u0106\u0001\u0000"+
		"\u0000\u0000\u0108\u0107\u0001\u0000\u0000\u0000\u0109\u0003\u0001\u0000"+
		"\u0000\u0000\u010a\u010b\u0003@ \u0000\u010b\u010d\u0003\n\u0005\u0000"+
		"\u010c\u010e\u0003L&\u0000\u010d\u010c\u0001\u0000\u0000\u0000\u010d\u010e"+
		"\u0001\u0000\u0000\u0000\u010e\u0110\u0001\u0000\u0000\u0000\u010f\u0111"+
		"\u0003N\'\u0000\u0110\u010f\u0001\u0000\u0000\u0000\u0110\u0111\u0001"+
		"\u0000\u0000\u0000\u0111\u0113\u0001\u0000\u0000\u0000\u0112\u0114\u0003"+
		"R)\u0000\u0113\u0112\u0001\u0000\u0000\u0000\u0113\u0114\u0001\u0000\u0000"+
		"\u0000\u0114\u0116\u0001\u0000\u0000\u0000\u0115\u0117\u0003T*\u0000\u0116"+
		"\u0115\u0001\u0000\u0000\u0000\u0116\u0117\u0001\u0000\u0000\u0000\u0117"+
		"\u0005\u0001\u0000\u0000\u0000\u0118\u011a\u00038\u001c\u0000\u0119\u011b"+
		"\u0003L&\u0000\u011a\u0119\u0001\u0000\u0000\u0000\u011a\u011b\u0001\u0000"+
		"\u0000\u0000\u011b\u0007\u0001\u0000\u0000\u0000\u011c\u011e\u0003>\u001f"+
		"\u0000\u011d\u011f\u0003L&\u0000\u011e\u011d\u0001\u0000\u0000\u0000\u011e"+
		"\u011f\u0001\u0000\u0000\u0000\u011f\t\u0001\u0000\u0000\u0000\u0120\u0121"+
		"\u00053\u0000\u0000\u0121\u0126\u0003\u000e\u0007\u0000\u0122\u0123\u0005"+
		"\u0001\u0000\u0000\u0123\u0125\u0003\f\u0006\u0000\u0124\u0122\u0001\u0000"+
		"\u0000\u0000\u0125\u0128\u0001\u0000\u0000\u0000\u0126\u0124\u0001\u0000"+
		"\u0000\u0000\u0126\u0127\u0001\u0000\u0000\u0000\u0127\u000b\u0001\u0000"+
		"\u0000\u0000\u0128\u0126\u0001\u0000\u0000\u0000\u0129\u012c\u0003\u000e"+
		"\u0007\u0000\u012a\u012c\u0003 \u0010\u0000\u012b\u0129\u0001\u0000\u0000"+
		"\u0000\u012b\u012a\u0001\u0000\u0000\u0000\u012c\r\u0001\u0000\u0000\u0000"+
		"\u012d\u0132\u0003\u0010\b\u0000\u012e\u0131\u0003\u0012\t\u0000\u012f"+
		"\u0131\u0003\u0014\n\u0000\u0130\u012e\u0001\u0000\u0000\u0000\u0130\u012f"+
		"\u0001\u0000\u0000\u0000\u0131\u0134\u0001\u0000\u0000\u0000\u0132\u0130"+
		"\u0001\u0000\u0000\u0000\u0132\u0133\u0001\u0000\u0000\u0000\u0133\u000f"+
		"\u0001\u0000\u0000\u0000\u0134\u0132\u0001\u0000\u0000\u0000\u0135\u0137"+
		"\u0003\u00f4z\u0000\u0136\u0138\u0005\u0015\u0000\u0000\u0137\u0136\u0001"+
		"\u0000\u0000\u0000\u0137\u0138\u0001\u0000\u0000\u0000\u0138\u0139\u0001"+
		"\u0000\u0000\u0000\u0139\u013a\u0003\u00d0h\u0000\u013a\u0011\u0001\u0000"+
		"\u0000\u0000\u013b\u013c\u0003\u0016\u000b\u0000\u013c\u013e\u0003\u001a"+
		"\r\u0000\u013d\u013f\u0005\u0015\u0000\u0000\u013e\u013d\u0001\u0000\u0000"+
		"\u0000\u013e\u013f\u0001\u0000\u0000\u0000\u013f\u0140\u0001\u0000\u0000"+
		"\u0000\u0140\u0142\u0003\u00d0h\u0000\u0141\u0143\u0003\u0018\f\u0000"+
		"\u0142\u0141\u0001\u0000\u0000\u0000\u0142\u0143\u0001\u0000\u0000\u0000"+
		"\u0143\u0013\u0001\u0000\u0000\u0000\u0144\u0145\u0003\u0016\u000b\u0000"+
		"\u0145\u0146\u00051\u0000\u0000\u0146\u0147\u0003\u001a\r\u0000\u0147"+
		"\u0015\u0001\u0000\u0000\u0000\u0148\u014a\u0005>\u0000\u0000\u0149\u014b"+
		"\u0005R\u0000\u0000\u014a\u0149\u0001\u0000\u0000\u0000\u014a\u014b\u0001"+
		"\u0000\u0000\u0000\u014b\u014e\u0001\u0000\u0000\u0000\u014c\u014e\u0005"+
		"9\u0000\u0000\u014d\u0148\u0001\u0000\u0000\u0000\u014d\u014c\u0001\u0000"+
		"\u0000\u0000\u014d\u014e\u0001\u0000\u0000\u0000\u014e\u014f\u0001\u0000"+
		"\u0000\u0000\u014f\u0150\u0005;\u0000\u0000\u0150\u0017\u0001\u0000\u0000"+
		"\u0000\u0151\u0152\u0005O\u0000\u0000\u0152\u0153\u0003n7\u0000\u0153"+
		"\u0019\u0001\u0000\u0000\u0000\u0154\u0165\u0003\u001c\u000e\u0000\u0155"+
		"\u0165\u0003\u001e\u000f\u0000\u0156\u0157\u0005`\u0000\u0000\u0157\u0158"+
		"\u0005\u0002\u0000\u0000\u0158\u0159\u0003\u001c\u000e\u0000\u0159\u015a"+
		"\u0005\u0015\u0000\u0000\u015a\u015b\u0003\u00eau\u0000\u015b\u015c\u0005"+
		"\u0003\u0000\u0000\u015c\u0165\u0001\u0000\u0000\u0000\u015d\u015e\u0005"+
		"`\u0000\u0000\u015e\u015f\u0005\u0002\u0000\u0000\u015f\u0160\u0003\u001e"+
		"\u000f\u0000\u0160\u0161\u0005\u0015\u0000\u0000\u0161\u0162\u0003\u00ea"+
		"u\u0000\u0162\u0163\u0005\u0003\u0000\u0000\u0163\u0165\u0001\u0000\u0000"+
		"\u0000\u0164\u0154\u0001\u0000\u0000\u0000\u0164\u0155\u0001\u0000\u0000"+
		"\u0000\u0164\u0156\u0001\u0000\u0000\u0000\u0164\u015d\u0001\u0000\u0000"+
		"\u0000\u0165\u001b\u0001\u0000\u0000\u0000\u0166\u0167\u0003\u00d0h\u0000"+
		"\u0167\u016d\u0005\u0004\u0000\u0000\u0168\u0169\u0003\u00e8t\u0000\u0169"+
		"\u016a\u0005\u0004\u0000\u0000\u016a\u016c\u0001\u0000\u0000\u0000\u016b"+
		"\u0168\u0001\u0000\u0000\u0000\u016c\u016f\u0001\u0000\u0000\u0000\u016d"+
		"\u016b\u0001\u0000\u0000\u0000\u016d\u016e\u0001\u0000\u0000\u0000\u016e"+
		"\u0170\u0001\u0000\u0000\u0000\u016f\u016d\u0001\u0000\u0000\u0000\u0170"+
		"\u0171\u0003\u00ecv\u0000\u0171\u001d\u0001\u0000\u0000\u0000\u0172\u0173"+
		"\u0003\u00d0h\u0000\u0173\u0179\u0005\u0004\u0000\u0000\u0174\u0175\u0003"+
		"\u00e8t\u0000\u0175\u0176\u0005\u0004\u0000\u0000\u0176\u0178\u0001\u0000"+
		"\u0000\u0000\u0177\u0174\u0001\u0000\u0000\u0000\u0178\u017b\u0001\u0000"+
		"\u0000\u0000\u0179\u0177\u0001\u0000\u0000\u0000\u0179\u017a\u0001\u0000"+
		"\u0000\u0000\u017a\u017c\u0001\u0000\u0000\u0000\u017b\u0179\u0001\u0000"+
		"\u0000\u0000\u017c\u017d\u0003\u00eew\u0000\u017d\u001f\u0001\u0000\u0000"+
		"\u0000\u017e\u017f\u00057\u0000\u0000\u017f\u0180\u0005\u0002\u0000\u0000"+
		"\u0180\u0181\u00036\u001b\u0000\u0181\u0183\u0005\u0003\u0000\u0000\u0182"+
		"\u0184\u0005\u0015\u0000\u0000\u0183\u0182\u0001\u0000\u0000\u0000\u0183"+
		"\u0184\u0001\u0000\u0000\u0000\u0184\u0185\u0001\u0000\u0000\u0000\u0185"+
		"\u0186\u0003\u00d0h\u0000\u0186!\u0001\u0000\u0000\u0000\u0187\u018e\u0003"+
		"$\u0012\u0000\u0188\u0189\u0005+\u0000\u0000\u0189\u018a\u0005\u0002\u0000"+
		"\u0000\u018a\u018b\u0003\u00d0h\u0000\u018b\u018c\u0005\u0003\u0000\u0000"+
		"\u018c\u018e\u0001\u0000\u0000\u0000\u018d\u0187\u0001\u0000\u0000\u0000"+
		"\u018d\u0188\u0001\u0000\u0000\u0000\u018e#\u0001\u0000\u0000\u0000\u018f"+
		"\u0190\u0005<\u0000\u0000\u0190\u0191\u0005\u0002\u0000\u0000\u0191\u0192"+
		"\u0003\u00d0h\u0000\u0192\u0193\u0005\u0003\u0000\u0000\u0193\u019a\u0001"+
		"\u0000\u0000\u0000\u0194\u0195\u0005f\u0000\u0000\u0195\u0196\u0005\u0002"+
		"\u0000\u0000\u0196\u0197\u0003\u00d0h\u0000\u0197\u0198\u0005\u0003\u0000"+
		"\u0000\u0198\u019a\u0001\u0000\u0000\u0000\u0199\u018f\u0001\u0000\u0000"+
		"\u0000\u0199\u0194\u0001\u0000\u0000\u0000\u019a%\u0001\u0000\u0000\u0000"+
		"\u019b\u01a6\u0003\"\u0011\u0000\u019c\u019d\u0005`\u0000\u0000\u019d"+
		"\u019e\u0005\u0002\u0000\u0000\u019e\u019f\u0003\"\u0011\u0000\u019f\u01a0"+
		"\u0005\u0015\u0000\u0000\u01a0\u01a1\u0003\u00eau\u0000\u01a1\u01a2\u0005"+
		"\u0003\u0000\u0000\u01a2\u01a6\u0001\u0000\u0000\u0000\u01a3\u01a6\u0003"+
		"0\u0018\u0000\u01a4\u01a6\u00034\u001a\u0000\u01a5\u019b\u0001\u0000\u0000"+
		"\u0000\u01a5\u019c\u0001\u0000\u0000\u0000\u01a5\u01a3\u0001\u0000\u0000"+
		"\u0000\u01a5\u01a4\u0001\u0000\u0000\u0000\u01a6\'\u0001\u0000\u0000\u0000"+
		"\u01a7\u01aa\u0003\u00d0h\u0000\u01a8\u01aa\u0003$\u0012\u0000\u01a9\u01a7"+
		"\u0001\u0000\u0000\u0000\u01a9\u01a8\u0001\u0000\u0000\u0000\u01aa)\u0001"+
		"\u0000\u0000\u0000\u01ab\u01b5\u0003,\u0016\u0000\u01ac\u01b1\u0003.\u0017"+
		"\u0000\u01ad\u01ae\u0005\u0004\u0000\u0000\u01ae\u01b0\u0003\u00eew\u0000"+
		"\u01af\u01ad\u0001\u0000\u0000\u0000\u01b0\u01b3\u0001\u0000\u0000\u0000"+
		"\u01b1\u01af\u0001\u0000\u0000\u0000\u01b1\u01b2\u0001\u0000\u0000\u0000"+
		"\u01b2\u01b5\u0001\u0000\u0000\u0000\u01b3\u01b1\u0001\u0000\u0000\u0000"+
		"\u01b4\u01ab\u0001\u0000\u0000\u0000\u01b4\u01ac\u0001\u0000\u0000\u0000"+
		"\u01b5+\u0001\u0000\u0000\u0000\u01b6\u01c0\u0003(\u0014\u0000\u01b7\u01bc"+
		"\u0003(\u0014\u0000\u01b8\u01b9\u0005\u0004\u0000\u0000\u01b9\u01bb\u0003"+
		"\u00eew\u0000\u01ba\u01b8\u0001\u0000\u0000\u0000\u01bb\u01be\u0001\u0000"+
		"\u0000\u0000\u01bc\u01ba\u0001\u0000\u0000\u0000\u01bc\u01bd\u0001\u0000"+
		"\u0000\u0000\u01bd\u01c0\u0001\u0000\u0000\u0000\u01be\u01bc\u0001\u0000"+
		"\u0000\u0000\u01bf\u01b6\u0001\u0000\u0000\u0000\u01bf\u01b7\u0001\u0000"+
		"\u0000\u0000\u01c0-\u0001\u0000\u0000\u0000\u01c1\u01c2\u0005`\u0000\u0000"+
		"\u01c2\u01c3\u0005\u0002\u0000\u0000\u01c3\u01c4\u0003*\u0015\u0000\u01c4"+
		"\u01c5\u0005\u0015\u0000\u0000\u01c5\u01c6\u0003\u00eau\u0000\u01c6\u01c7"+
		"\u0005\u0003\u0000\u0000\u01c7/\u0001\u0000\u0000\u0000\u01c8\u01c9\u0003"+
		"*\u0015\u0000\u01c9\u01ca\u0005\u0004\u0000\u0000\u01ca\u01cb\u0003\u00f0"+
		"x\u0000\u01cb1\u0001\u0000\u0000\u0000\u01cc\u01cf\u00030\u0018\u0000"+
		"\u01cd\u01cf\u0003(\u0014\u0000\u01ce\u01cc\u0001\u0000\u0000\u0000\u01ce"+
		"\u01cd\u0001\u0000\u0000\u0000\u01cf3\u0001\u0000\u0000\u0000\u01d0\u01d1"+
		"\u0003*\u0015\u0000\u01d1\u01d2\u0005\u0004\u0000\u0000\u01d2\u01d3\u0003"+
		"\u00eew\u0000\u01d35\u0001\u0000\u0000\u0000\u01d4\u01d5\u0003*\u0015"+
		"\u0000\u01d5\u01d6\u0005\u0004\u0000\u0000\u01d6\u01d7\u0003\u00f2y\u0000"+
		"\u01d77\u0001\u0000\u0000\u0000\u01d8\u01d9\u0005d\u0000\u0000\u01d9\u01de"+
		"\u0003\u00f4z\u0000\u01da\u01dc\u0005\u0015\u0000\u0000\u01db\u01da\u0001"+
		"\u0000\u0000\u0000\u01db\u01dc\u0001\u0000\u0000\u0000\u01dc\u01dd\u0001"+
		"\u0000\u0000\u0000\u01dd\u01df\u0003\u00d0h\u0000\u01de\u01db\u0001\u0000"+
		"\u0000\u0000\u01de\u01df\u0001\u0000\u0000\u0000\u01df\u01e0\u0001\u0000"+
		"\u0000\u0000\u01e0\u01e1\u0005V\u0000\u0000\u01e1\u01e6\u0003:\u001d\u0000"+
		"\u01e2\u01e3\u0005\u0001\u0000\u0000\u01e3\u01e5\u0003:\u001d\u0000\u01e4"+
		"\u01e2\u0001\u0000\u0000\u0000\u01e5\u01e8\u0001\u0000\u0000\u0000\u01e6"+
		"\u01e4\u0001\u0000\u0000\u0000\u01e6\u01e7\u0001\u0000\u0000\u0000\u01e7"+
		"9\u0001\u0000\u0000\u0000\u01e8\u01e6\u0001\u0000\u0000\u0000\u01e9\u01ea"+
		"\u0003\u00d0h\u0000\u01ea\u01eb\u0005\u0004\u0000\u0000\u01eb\u01ed\u0001"+
		"\u0000\u0000\u0000\u01ec\u01e9\u0001\u0000\u0000\u0000\u01ec\u01ed\u0001"+
		"\u0000\u0000\u0000\u01ed\u01f3\u0001\u0000\u0000\u0000\u01ee\u01ef\u0003"+
		"\u00e8t\u0000\u01ef\u01f0\u0005\u0004\u0000\u0000\u01f0\u01f2\u0001\u0000"+
		"\u0000\u0000\u01f1\u01ee\u0001\u0000\u0000\u0000\u01f2\u01f5\u0001\u0000"+
		"\u0000\u0000\u01f3\u01f1\u0001\u0000\u0000\u0000\u01f3\u01f4\u0001\u0000"+
		"\u0000\u0000\u01f4\u01f8\u0001\u0000\u0000\u0000\u01f5\u01f3\u0001\u0000"+
		"\u0000\u0000\u01f6\u01f9\u0003\u00f0x\u0000\u01f7\u01f9\u0003\u00eew\u0000"+
		"\u01f8\u01f6\u0001\u0000\u0000\u0000\u01f8\u01f7\u0001\u0000\u0000\u0000"+
		"\u01f9\u01fa\u0001\u0000\u0000\u0000\u01fa\u01fb\u0005i\u0000\u0000\u01fb"+
		"\u01fc\u0003<\u001e\u0000\u01fc;\u0001\u0000\u0000\u0000\u01fd\u0201\u0003"+
		"l6\u0000\u01fe\u0201\u0003\u00a4R\u0000\u01ff\u0201\u0005K\u0000\u0000"+
		"\u0200\u01fd\u0001\u0000\u0000\u0000\u0200\u01fe\u0001\u0000\u0000\u0000"+
		"\u0200\u01ff\u0001\u0000\u0000\u0000\u0201=\u0001\u0000\u0000\u0000\u0202"+
		"\u0203\u0005%\u0000\u0000\u0203\u0204\u00053\u0000\u0000\u0204\u0209\u0003"+
		"\u00f4z\u0000\u0205\u0207\u0005\u0015\u0000\u0000\u0206\u0205\u0001\u0000"+
		"\u0000\u0000\u0206\u0207\u0001\u0000\u0000\u0000\u0207\u0208\u0001\u0000"+
		"\u0000\u0000\u0208\u020a\u0003\u00d0h\u0000\u0209\u0206\u0001\u0000\u0000"+
		"\u0000\u0209\u020a\u0001\u0000\u0000\u0000\u020a?\u0001\u0000\u0000\u0000"+
		"\u020b\u020d\u0005U\u0000\u0000\u020c\u020e\u0005\'\u0000\u0000\u020d"+
		"\u020c\u0001\u0000\u0000\u0000\u020d\u020e\u0001\u0000\u0000\u0000\u020e"+
		"\u020f\u0001\u0000\u0000\u0000\u020f\u0214\u0003B!\u0000\u0210\u0211\u0005"+
		"\u0001\u0000\u0000\u0211\u0213\u0003B!\u0000\u0212\u0210\u0001\u0000\u0000"+
		"\u0000\u0213\u0216\u0001\u0000\u0000\u0000\u0214\u0212\u0001\u0000\u0000"+
		"\u0000\u0214\u0215\u0001\u0000\u0000\u0000\u0215A\u0001\u0000\u0000\u0000"+
		"\u0216\u0214\u0001\u0000\u0000\u0000\u0217\u021c\u0003D\"\u0000\u0218"+
		"\u021a\u0005\u0015\u0000\u0000\u0219\u0218\u0001\u0000\u0000\u0000\u0219"+
		"\u021a\u0001\u0000\u0000\u0000\u021a\u021b\u0001\u0000\u0000\u0000\u021b"+
		"\u021d\u0003\u00f6{\u0000\u021c\u0219\u0001\u0000\u0000\u0000\u021c\u021d"+
		"\u0001\u0000\u0000\u0000\u021dC\u0001\u0000\u0000\u0000\u021e\u0229\u0003"+
		"&\u0013\u0000\u021f\u0229\u0003l6\u0000\u0220\u0229\u0003J%\u0000\u0221"+
		"\u0229\u0003\u00d0h\u0000\u0222\u0223\u0005M\u0000\u0000\u0223\u0224\u0005"+
		"\u0002\u0000\u0000\u0224\u0225\u0003\u00d0h\u0000\u0225\u0226\u0005\u0003"+
		"\u0000\u0000\u0226\u0229\u0001\u0000\u0000\u0000\u0227\u0229\u0003F#\u0000"+
		"\u0228\u021e\u0001\u0000\u0000\u0000\u0228\u021f\u0001\u0000\u0000\u0000"+
		"\u0228\u0220\u0001\u0000\u0000\u0000\u0228\u0221\u0001\u0000\u0000\u0000"+
		"\u0228\u0222\u0001\u0000\u0000\u0000\u0228\u0227\u0001\u0000\u0000\u0000"+
		"\u0229E\u0001\u0000\u0000\u0000\u022a\u022b\u0005I\u0000\u0000\u022b\u022c"+
		"\u0003\u00d2i\u0000\u022c\u022d\u0005\u0002\u0000\u0000\u022d\u0232\u0003"+
		"H$\u0000\u022e\u022f\u0005\u0001\u0000\u0000\u022f\u0231\u0003H$\u0000"+
		"\u0230\u022e\u0001\u0000\u0000\u0000\u0231\u0234\u0001\u0000\u0000\u0000"+
		"\u0232\u0230\u0001\u0000\u0000\u0000\u0232\u0233\u0001\u0000\u0000\u0000"+
		"\u0233\u0235\u0001\u0000\u0000\u0000\u0234\u0232\u0001\u0000\u0000\u0000"+
		"\u0235\u0236\u0005\u0003\u0000\u0000\u0236G\u0001\u0000\u0000\u0000\u0237"+
		"\u023d\u0003&\u0013\u0000\u0238\u023d\u0003l6\u0000\u0239\u023d\u0003"+
		"J%\u0000\u023a\u023d\u0003\u00d0h\u0000\u023b\u023d\u0003\u00d4j\u0000"+
		"\u023c\u0237\u0001\u0000\u0000\u0000\u023c\u0238\u0001\u0000\u0000\u0000"+
		"\u023c\u0239\u0001\u0000\u0000\u0000\u023c\u023a\u0001\u0000\u0000\u0000"+
		"\u023c\u023b\u0001\u0000\u0000\u0000\u023dI\u0001\u0000\u0000\u0000\u023e"+
		"\u023f\u0007\u0000\u0000\u0000\u023f\u0241\u0005\u0002\u0000\u0000\u0240"+
		"\u0242\u0005\'\u0000\u0000\u0241\u0240\u0001\u0000\u0000\u0000\u0241\u0242"+
		"\u0001\u0000\u0000\u0000\u0242\u0243\u0001\u0000\u0000\u0000\u0243\u0244"+
		"\u00032\u0019\u0000\u0244\u0245\u0005\u0003\u0000\u0000\u0245\u0254\u0001"+
		"\u0000\u0000\u0000\u0246\u0247\u0005\u001f\u0000\u0000\u0247\u0249\u0005"+
		"\u0002\u0000\u0000\u0248\u024a\u0005\'\u0000\u0000\u0249\u0248\u0001\u0000"+
		"\u0000\u0000\u0249\u024a\u0001\u0000\u0000\u0000\u024a\u024e\u0001\u0000"+
		"\u0000\u0000\u024b\u024f\u0003\u00d0h\u0000\u024c\u024f\u00032\u0019\u0000"+
		"\u024d\u024f\u00034\u001a\u0000\u024e\u024b\u0001\u0000\u0000\u0000\u024e"+
		"\u024c\u0001\u0000\u0000\u0000\u024e\u024d\u0001\u0000\u0000\u0000\u024f"+
		"\u0250\u0001\u0000\u0000\u0000\u0250\u0251\u0005\u0003\u0000\u0000\u0251"+
		"\u0254\u0001\u0000\u0000\u0000\u0252\u0254\u0003\u00b2Y\u0000\u0253\u023e"+
		"\u0001\u0000\u0000\u0000\u0253\u0246\u0001\u0000\u0000\u0000\u0253\u0252"+
		"\u0001\u0000\u0000\u0000\u0254K\u0001\u0000\u0000\u0000\u0255\u0256\u0005"+
		"h\u0000\u0000\u0256\u0257\u0003n7\u0000\u0257M\u0001\u0000\u0000\u0000"+
		"\u0258\u0259\u00055\u0000\u0000\u0259\u025a\u0005\u001a\u0000\u0000\u025a"+
		"\u025f\u0003P(\u0000\u025b\u025c\u0005\u0001\u0000\u0000\u025c\u025e\u0003"+
		"P(\u0000\u025d\u025b\u0001\u0000\u0000\u0000\u025e\u0261\u0001\u0000\u0000"+
		"\u0000\u025f\u025d\u0001\u0000\u0000\u0000\u025f\u0260\u0001\u0000\u0000"+
		"\u0000\u0260O\u0001\u0000\u0000\u0000\u0261\u025f\u0001\u0000\u0000\u0000"+
		"\u0262\u0265\u0003&\u0013\u0000\u0263\u0265\u0003\u00d0h\u0000\u0264\u0262"+
		"\u0001\u0000\u0000\u0000\u0264\u0263\u0001\u0000\u0000\u0000\u0265Q\u0001"+
		"\u0000\u0000\u0000\u0266\u0267\u00056\u0000\u0000\u0267\u0268\u0003n7"+
		"\u0000\u0268S\u0001\u0000\u0000\u0000\u0269\u026a\u0005Q\u0000\u0000\u026a"+
		"\u026b\u0005\u001a\u0000\u0000\u026b\u0270\u0003V+\u0000\u026c\u026d\u0005"+
		"\u0001\u0000\u0000\u026d\u026f\u0003V+\u0000\u026e\u026c\u0001\u0000\u0000"+
		"\u0000\u026f\u0272\u0001\u0000\u0000\u0000\u0270\u026e\u0001\u0000\u0000"+
		"\u0000\u0270\u0271\u0001\u0000\u0000\u0000\u0271U\u0001\u0000\u0000\u0000"+
		"\u0272\u0270\u0001\u0000\u0000\u0000\u0273\u0277\u00030\u0018\u0000\u0274"+
		"\u0277\u0003(\u0014\u0000\u0275\u0277\u0003\u00f6{\u0000\u0276\u0273\u0001"+
		"\u0000\u0000\u0000\u0276\u0274\u0001\u0000\u0000\u0000\u0276\u0275\u0001"+
		"\u0000\u0000\u0000\u0277\u0279\u0001\u0000\u0000\u0000\u0278\u027a\u0007"+
		"\u0001\u0000\u0000\u0279\u0278\u0001\u0000\u0000\u0000\u0279\u027a\u0001"+
		"\u0000\u0000\u0000\u027aW\u0001\u0000\u0000\u0000\u027b\u027c\u0003h4"+
		"\u0000\u027c\u027e\u0003Z-\u0000\u027d\u027f\u0003L&\u0000\u027e\u027d"+
		"\u0001\u0000\u0000\u0000\u027e\u027f\u0001\u0000\u0000\u0000\u027f\u0281"+
		"\u0001\u0000\u0000\u0000\u0280\u0282\u0003N\'\u0000\u0281\u0280\u0001"+
		"\u0000\u0000\u0000\u0281\u0282\u0001\u0000\u0000\u0000\u0282\u0284\u0001"+
		"\u0000\u0000\u0000\u0283\u0285\u0003R)\u0000\u0284\u0283\u0001\u0000\u0000"+
		"\u0000\u0284\u0285\u0001\u0000\u0000\u0000\u0285Y\u0001\u0000\u0000\u0000"+
		"\u0286\u0287\u00053\u0000\u0000\u0287\u028f\u0003\\.\u0000\u0288\u028b"+
		"\u0005\u0001\u0000\u0000\u0289\u028c\u0003\\.\u0000\u028a\u028c\u0003"+
		" \u0010\u0000\u028b\u0289\u0001\u0000\u0000\u0000\u028b\u028a\u0001\u0000"+
		"\u0000\u0000\u028c\u028e\u0001\u0000\u0000\u0000\u028d\u0288\u0001\u0000"+
		"\u0000\u0000\u028e\u0291\u0001\u0000\u0000\u0000\u028f\u028d\u0001\u0000"+
		"\u0000\u0000\u028f\u0290\u0001\u0000\u0000\u0000\u0290[\u0001\u0000\u0000"+
		"\u0000\u0291\u028f\u0001\u0000\u0000\u0000\u0292\u02a0\u0003\u000e\u0007"+
		"\u0000\u0293\u0295\u0003^/\u0000\u0294\u0296\u0005\u0015\u0000\u0000\u0295"+
		"\u0294\u0001\u0000\u0000\u0000\u0295\u0296\u0001\u0000\u0000\u0000\u0296"+
		"\u0297\u0001\u0000\u0000\u0000\u0297\u029b\u0003\u00d0h\u0000\u0298\u029a"+
		"\u0003\u0012\t\u0000\u0299\u0298\u0001\u0000\u0000\u0000\u029a\u029d\u0001"+
		"\u0000\u0000\u0000\u029b\u0299\u0001\u0000\u0000\u0000\u029b\u029c\u0001"+
		"\u0000\u0000\u0000\u029c\u02a0\u0001\u0000\u0000\u0000\u029d\u029b\u0001"+
		"\u0000\u0000\u0000\u029e\u02a0\u0003f3\u0000\u029f\u0292\u0001\u0000\u0000"+
		"\u0000\u029f\u0293\u0001\u0000\u0000\u0000\u029f\u029e\u0001\u0000\u0000"+
		"\u0000\u02a0]\u0001\u0000\u0000\u0000\u02a1\u02a2\u0003`0\u0000\u02a2"+
		"\u02a3\u0005\u0004\u0000\u0000\u02a3\u02a4\u0003\u00eew\u0000\u02a4\u02aa"+
		"\u0001\u0000\u0000\u0000\u02a5\u02a6\u0003`0\u0000\u02a6\u02a7\u0005\u0004"+
		"\u0000\u0000\u02a7\u02a8\u0003\u00ecv\u0000\u02a8\u02aa\u0001\u0000\u0000"+
		"\u0000\u02a9\u02a1\u0001\u0000\u0000\u0000\u02a9\u02a5\u0001\u0000\u0000"+
		"\u0000\u02aa_\u0001\u0000\u0000\u0000\u02ab\u02b5\u0003b1\u0000\u02ac"+
		"\u02b1\u0003d2\u0000\u02ad\u02ae\u0005\u0004\u0000\u0000\u02ae\u02b0\u0003"+
		"\u00eew\u0000\u02af\u02ad\u0001\u0000\u0000\u0000\u02b0\u02b3\u0001\u0000"+
		"\u0000\u0000\u02b1\u02af\u0001\u0000\u0000\u0000\u02b1\u02b2\u0001\u0000"+
		"\u0000\u0000\u02b2\u02b5\u0001\u0000\u0000\u0000\u02b3\u02b1\u0001\u0000"+
		"\u0000\u0000\u02b4\u02ab\u0001\u0000\u0000\u0000\u02b4\u02ac\u0001\u0000"+
		"\u0000\u0000\u02b5a\u0001\u0000\u0000\u0000\u02b6\u02bb\u0003\u00f8|\u0000"+
		"\u02b7\u02b8\u0005\u0004\u0000\u0000\u02b8\u02ba\u0003\u00eew\u0000\u02b9"+
		"\u02b7\u0001\u0000\u0000\u0000\u02ba\u02bd\u0001\u0000\u0000\u0000\u02bb"+
		"\u02b9\u0001\u0000\u0000\u0000\u02bb\u02bc\u0001\u0000\u0000\u0000\u02bc"+
		"c\u0001\u0000\u0000\u0000\u02bd\u02bb\u0001\u0000\u0000\u0000\u02be\u02bf"+
		"\u0005`\u0000\u0000\u02bf\u02c0\u0005\u0002\u0000\u0000\u02c0\u02c1\u0003"+
		"`0\u0000\u02c1\u02c2\u0005\u0015\u0000\u0000\u02c2\u02c3\u0003\u00eau"+
		"\u0000\u02c3\u02c4\u0005\u0003\u0000\u0000\u02c4e\u0001\u0000\u0000\u0000"+
		"\u02c5\u02c6\u00057\u0000\u0000\u02c6\u02c7\u0003\u00f8|\u0000\u02c7\u02cd"+
		"\u0005\u0004\u0000\u0000\u02c8\u02c9\u0003\u00eew\u0000\u02c9\u02ca\u0005"+
		"\u0004\u0000\u0000\u02ca\u02cc\u0001\u0000\u0000\u0000\u02cb\u02c8\u0001"+
		"\u0000\u0000\u0000\u02cc\u02cf\u0001\u0000\u0000\u0000\u02cd\u02cb\u0001"+
		"\u0000\u0000\u0000\u02cd\u02ce\u0001\u0000\u0000\u0000\u02ce\u02d0\u0001"+
		"\u0000\u0000\u0000\u02cf\u02cd\u0001\u0000\u0000\u0000\u02d0\u02d1\u0003"+
		"\u00ecv\u0000\u02d1g\u0001\u0000\u0000\u0000\u02d2\u02d4\u0005U\u0000"+
		"\u0000\u02d3\u02d5\u0005\'\u0000\u0000\u02d4\u02d3\u0001\u0000\u0000\u0000"+
		"\u02d4\u02d5\u0001\u0000\u0000\u0000\u02d5\u02d6\u0001\u0000\u0000\u0000"+
		"\u02d6\u02d7\u0003j5\u0000\u02d7i\u0001\u0000\u0000\u0000\u02d8\u02dd"+
		"\u0003&\u0013\u0000\u02d9\u02dd\u0003l6\u0000\u02da\u02dd\u0003J%\u0000"+
		"\u02db\u02dd\u0003\u00d0h\u0000\u02dc\u02d8\u0001\u0000\u0000\u0000\u02dc"+
		"\u02d9\u0001\u0000\u0000\u0000\u02dc\u02da\u0001\u0000\u0000\u0000\u02dc"+
		"\u02db\u0001\u0000\u0000\u0000\u02ddk\u0001\u0000\u0000\u0000\u02de\u02e6"+
		"\u0003\u0092I\u0000\u02df\u02e6\u0003\u009aM\u0000\u02e0\u02e6\u0003\u00a0"+
		"P\u0000\u02e1\u02e6\u0003\u009cN\u0000\u02e2\u02e6\u0003\u009eO\u0000"+
		"\u02e3\u02e6\u0003\u00be_\u0000\u02e4\u02e6\u0003\u00a6S\u0000\u02e5\u02de"+
		"\u0001\u0000\u0000\u0000\u02e5\u02df\u0001\u0000\u0000\u0000\u02e5\u02e0"+
		"\u0001\u0000\u0000\u0000\u02e5\u02e1\u0001\u0000\u0000\u0000\u02e5\u02e2"+
		"\u0001\u0000\u0000\u0000\u02e5\u02e3\u0001\u0000\u0000\u0000\u02e5\u02e4"+
		"\u0001\u0000\u0000\u0000\u02e6m\u0001\u0000\u0000\u0000\u02e7\u02e8\u0006"+
		"7\uffff\uffff\u0000\u02e8\u02e9\u0003p8\u0000\u02e9\u02ef\u0001\u0000"+
		"\u0000\u0000\u02ea\u02eb\n\u0001\u0000\u0000\u02eb\u02ec\u0005P\u0000"+
		"\u0000\u02ec\u02ee\u0003p8\u0000\u02ed\u02ea\u0001\u0000\u0000\u0000\u02ee"+
		"\u02f1\u0001\u0000\u0000\u0000\u02ef\u02ed\u0001\u0000\u0000\u0000\u02ef"+
		"\u02f0\u0001\u0000\u0000\u0000\u02f0o\u0001\u0000\u0000\u0000\u02f1\u02ef"+
		"\u0001\u0000\u0000\u0000\u02f2\u02f3\u00068\uffff\uffff\u0000\u02f3\u02f4"+
		"\u0003r9\u0000\u02f4\u02fa\u0001\u0000\u0000\u0000\u02f5\u02f6\n\u0001"+
		"\u0000\u0000\u02f6\u02f7\u0005\u0013\u0000\u0000\u02f7\u02f9\u0003r9\u0000"+
		"\u02f8\u02f5\u0001\u0000\u0000\u0000\u02f9\u02fc\u0001\u0000\u0000\u0000"+
		"\u02fa\u02f8\u0001\u0000\u0000\u0000\u02fa\u02fb\u0001\u0000\u0000\u0000"+
		"\u02fbq\u0001\u0000\u0000\u0000\u02fc\u02fa\u0001\u0000\u0000\u0000\u02fd"+
		"\u02ff\u0005J\u0000\u0000\u02fe\u02fd\u0001\u0000\u0000\u0000\u02fe\u02ff"+
		"\u0001\u0000\u0000\u0000\u02ff\u0300\u0001\u0000\u0000\u0000\u0300\u0301"+
		"\u0003t:\u0000\u0301s\u0001\u0000\u0000\u0000\u0302\u0308\u0003v;\u0000"+
		"\u0303\u0304\u0005\u0002\u0000\u0000\u0304\u0305\u0003n7\u0000\u0305\u0306"+
		"\u0005\u0003\u0000\u0000\u0306\u0308\u0001\u0000\u0000\u0000\u0307\u0302"+
		"\u0001\u0000\u0000\u0000\u0307\u0303\u0001\u0000\u0000\u0000\u0308u\u0001"+
		"\u0000\u0000\u0000\u0309\u0312\u0003\u008eG\u0000\u030a\u0312\u0003x<"+
		"\u0000\u030b\u0312\u0003z=\u0000\u030c\u0312\u0003~?\u0000\u030d\u0312"+
		"\u0003\u0080@\u0000\u030e\u0312\u0003\u0082A\u0000\u030f\u0312\u0003\u0084"+
		"B\u0000\u0310\u0312\u0003\u008aE\u0000\u0311\u0309\u0001\u0000\u0000\u0000"+
		"\u0311\u030a\u0001\u0000\u0000\u0000\u0311\u030b\u0001\u0000\u0000\u0000"+
		"\u0311\u030c\u0001\u0000\u0000\u0000\u0311\u030d\u0001\u0000\u0000\u0000"+
		"\u0311\u030e\u0001\u0000\u0000\u0000\u0311\u030f\u0001\u0000\u0000\u0000"+
		"\u0311\u0310\u0001\u0000\u0000\u0000\u0312w\u0001\u0000\u0000\u0000\u0313"+
		"\u0315\u0003\u0092I\u0000\u0314\u0316\u0005J\u0000\u0000\u0315\u0314\u0001"+
		"\u0000\u0000\u0000\u0315\u0316\u0001\u0000\u0000\u0000\u0316\u0317\u0001"+
		"\u0000\u0000\u0000\u0317\u0318\u0005\u0018\u0000\u0000\u0318\u0319\u0003"+
		"\u0092I\u0000\u0319\u031a\u0005\u0013\u0000\u0000\u031a\u031b\u0003\u0092"+
		"I\u0000\u031b\u032f\u0001\u0000\u0000\u0000\u031c\u031e\u0003\u009aM\u0000"+
		"\u031d\u031f\u0005J\u0000\u0000\u031e\u031d\u0001\u0000\u0000\u0000\u031e"+
		"\u031f\u0001\u0000\u0000\u0000\u031f\u0320\u0001\u0000\u0000\u0000\u0320"+
		"\u0321\u0005\u0018\u0000\u0000\u0321\u0322\u0003\u009aM\u0000\u0322\u0323"+
		"\u0005\u0013\u0000\u0000\u0323\u0324\u0003\u009aM\u0000\u0324\u032f\u0001"+
		"\u0000\u0000\u0000\u0325\u0327\u0003\u009cN\u0000\u0326\u0328\u0005J\u0000"+
		"\u0000\u0327\u0326\u0001\u0000\u0000\u0000\u0327\u0328\u0001\u0000\u0000"+
		"\u0000\u0328\u0329\u0001\u0000\u0000\u0000\u0329\u032a\u0005\u0018\u0000"+
		"\u0000\u032a\u032b\u0003\u009cN\u0000\u032b\u032c\u0005\u0013\u0000\u0000"+
		"\u032c\u032d\u0003\u009cN\u0000\u032d\u032f\u0001\u0000\u0000\u0000\u032e"+
		"\u0313\u0001\u0000\u0000\u0000\u032e\u031c\u0001\u0000\u0000\u0000\u032e"+
		"\u0325\u0001\u0000\u0000\u0000\u032fy\u0001\u0000\u0000\u0000\u0330\u0333"+
		"\u00032\u0019\u0000\u0331\u0333\u0003\u00a8T\u0000\u0332\u0330\u0001\u0000"+
		"\u0000\u0000\u0332\u0331\u0001\u0000\u0000\u0000\u0333\u0335\u0001\u0000"+
		"\u0000\u0000\u0334\u0336\u0005J\u0000\u0000\u0335\u0334\u0001\u0000\u0000"+
		"\u0000\u0335\u0336\u0001\u0000\u0000\u0000\u0336\u0337\u0001\u0000\u0000"+
		"\u0000\u0337\u0348\u00057\u0000\u0000\u0338\u0339\u0005\u0002\u0000\u0000"+
		"\u0339\u033e\u0003|>\u0000\u033a\u033b\u0005\u0001\u0000\u0000\u033b\u033d"+
		"\u0003|>\u0000\u033c\u033a\u0001\u0000\u0000\u0000\u033d\u0340\u0001\u0000"+
		"\u0000\u0000\u033e\u033c\u0001\u0000\u0000\u0000\u033e\u033f\u0001\u0000"+
		"\u0000\u0000\u033f\u0341\u0001\u0000\u0000\u0000\u0340\u033e\u0001\u0000"+
		"\u0000\u0000\u0341\u0342\u0005\u0003\u0000\u0000\u0342\u0349\u0001\u0000"+
		"\u0000\u0000\u0343\u0344\u0005\u0002\u0000\u0000\u0344\u0345\u0003X,\u0000"+
		"\u0345\u0346\u0005\u0003\u0000\u0000\u0346\u0349\u0001\u0000\u0000\u0000"+
		"\u0347\u0349\u0003\u00fa}\u0000\u0348\u0338\u0001\u0000\u0000\u0000\u0348"+
		"\u0343\u0001\u0000\u0000\u0000\u0348\u0347\u0001\u0000\u0000\u0000\u0349"+
		"{\u0001\u0000\u0000\u0000\u034a\u034d\u0003\u00d4j\u0000\u034b\u034d\u0003"+
		"\u00fc~\u0000\u034c\u034a\u0001\u0000\u0000\u0000\u034c\u034b\u0001\u0000"+
		"\u0000\u0000\u034d}\u0001\u0000\u0000\u0000\u034e\u0350\u0003\u009aM\u0000"+
		"\u034f\u0351\u0005J\u0000\u0000\u0350\u034f\u0001\u0000\u0000\u0000\u0350"+
		"\u0351\u0001\u0000\u0000\u0000\u0351\u0352\u0001\u0000\u0000\u0000\u0352"+
		"\u0354\u0005@\u0000\u0000\u0353\u0355\u0005\u0005\u0000\u0000\u0354\u0353"+
		"\u0001\u0000\u0000\u0000\u0354\u0355\u0001\u0000\u0000\u0000\u0355\u0356"+
		"\u0001\u0000\u0000\u0000\u0356\u0358\u0003\u00d8l\u0000\u0357\u0359\u0005"+
		"\u0005\u0000\u0000\u0358\u0357\u0001\u0000\u0000\u0000\u0358\u0359\u0001"+
		"\u0000\u0000\u0000\u0359\u035c\u0001\u0000\u0000\u0000\u035a\u035b\u0005"+
		",\u0000\u0000\u035b\u035d\u0003\u00deo\u0000\u035c\u035a\u0001\u0000\u0000"+
		"\u0000\u035c\u035d\u0001\u0000\u0000\u0000\u035d\u007f\u0001\u0000\u0000"+
		"\u0000\u035e\u0361\u0003&\u0013\u0000\u035f\u0361\u0003\u00d6k\u0000\u0360"+
		"\u035e\u0001\u0000\u0000\u0000\u0360\u035f\u0001\u0000\u0000\u0000\u0361"+
		"\u0362\u0001\u0000\u0000\u0000\u0362\u0364\u0005:\u0000\u0000\u0363\u0365"+
		"\u0005J\u0000\u0000\u0364\u0363\u0001\u0000\u0000\u0000\u0364\u0365\u0001"+
		"\u0000\u0000\u0000\u0365\u0366\u0001\u0000\u0000\u0000\u0366\u0367\u0005"+
		"K\u0000\u0000\u0367\u0081\u0001\u0000\u0000\u0000\u0368\u0369\u00036\u001b"+
		"\u0000\u0369\u036b\u0005:\u0000\u0000\u036a\u036c\u0005J\u0000\u0000\u036b"+
		"\u036a\u0001\u0000\u0000\u0000\u036b\u036c\u0001\u0000\u0000\u0000\u036c"+
		"\u036d\u0001\u0000\u0000\u0000\u036d\u036e\u0005*\u0000\u0000\u036e\u0083"+
		"\u0001\u0000\u0000\u0000\u036f\u0371\u0003\u0086C\u0000\u0370\u0372\u0005"+
		"J\u0000\u0000\u0371\u0370\u0001\u0000\u0000\u0000\u0371\u0372\u0001\u0000"+
		"\u0000\u0000\u0372\u0373\u0001\u0000\u0000\u0000\u0373\u0375\u0005F\u0000"+
		"\u0000\u0374\u0376\u0005N\u0000\u0000\u0375\u0374\u0001\u0000\u0000\u0000"+
		"\u0375\u0376\u0001\u0000\u0000\u0000\u0376\u0377\u0001\u0000\u0000\u0000"+
		"\u0377\u0378\u00036\u001b\u0000\u0378\u0085\u0001\u0000\u0000\u0000\u0379"+
		"\u037d\u00034\u001a\u0000\u037a\u037d\u00030\u0018\u0000\u037b\u037d\u0003"+
		"\u0088D\u0000\u037c\u0379\u0001\u0000\u0000\u0000\u037c\u037a\u0001\u0000"+
		"\u0000\u0000\u037c\u037b\u0001\u0000\u0000\u0000\u037d\u0087\u0001\u0000"+
		"\u0000\u0000\u037e\u0382\u0003\u00d0h\u0000\u037f\u0382\u0003\u00d6k\u0000"+
		"\u0380\u0382\u0003\u00d4j\u0000\u0381\u037e\u0001\u0000\u0000\u0000\u0381"+
		"\u037f\u0001\u0000\u0000\u0000\u0381\u0380\u0001\u0000\u0000\u0000\u0382"+
		"\u0089\u0001\u0000\u0000\u0000\u0383\u0385\u0005J\u0000\u0000\u0384\u0383"+
		"\u0001\u0000\u0000\u0000\u0384\u0385\u0001\u0000\u0000\u0000\u0385\u0386"+
		"\u0001\u0000\u0000\u0000\u0386\u0387\u0005-\u0000\u0000\u0387\u0388\u0005"+
		"\u0002\u0000\u0000\u0388\u0389\u0003X,\u0000\u0389\u038a\u0005\u0003\u0000"+
		"\u0000\u038a\u008b\u0001\u0000\u0000\u0000\u038b\u038c\u0007\u0002\u0000"+
		"\u0000\u038c\u038d\u0005\u0002\u0000\u0000\u038d\u038e\u0003X,\u0000\u038e"+
		"\u038f\u0005\u0003\u0000\u0000\u038f\u008d\u0001\u0000\u0000\u0000\u0390"+
		"\u0391\u0003\u009aM\u0000\u0391\u0394\u0003\u0090H\u0000\u0392\u0395\u0003"+
		"\u009aM\u0000\u0393\u0395\u0003\u008cF\u0000\u0394\u0392\u0001\u0000\u0000"+
		"\u0000\u0394\u0393\u0001\u0000\u0000\u0000\u0395\u03b9\u0001\u0000\u0000"+
		"\u0000\u0396\u0397\u0003\u009eO\u0000\u0397\u039a\u0007\u0003\u0000\u0000"+
		"\u0398\u039b\u0003\u009eO\u0000\u0399\u039b\u0003\u008cF\u0000\u039a\u0398"+
		"\u0001\u0000\u0000\u0000\u039a\u0399\u0001\u0000\u0000\u0000\u039b\u03b9"+
		"\u0001\u0000\u0000\u0000\u039c\u039d\u0003\u00a0P\u0000\u039d\u03a0\u0007"+
		"\u0003\u0000\u0000\u039e\u03a1\u0003\u00a0P\u0000\u039f\u03a1\u0003\u008c"+
		"F\u0000\u03a0\u039e\u0001\u0000\u0000\u0000\u03a0\u039f\u0001\u0000\u0000"+
		"\u0000\u03a1\u03b9\u0001\u0000\u0000\u0000\u03a2\u03a3\u0003\u009cN\u0000"+
		"\u03a3\u03a6\u0003\u0090H\u0000\u03a4\u03a7\u0003\u009cN\u0000\u03a5\u03a7"+
		"\u0003\u008cF\u0000\u03a6\u03a4\u0001\u0000\u0000\u0000\u03a6\u03a5\u0001"+
		"\u0000\u0000\u0000\u03a7\u03b9\u0001\u0000\u0000\u0000\u03a8\u03a9\u0003"+
		"\u00a2Q\u0000\u03a9\u03ac\u0007\u0003\u0000\u0000\u03aa\u03ad\u0003\u00a2"+
		"Q\u0000\u03ab\u03ad\u0003\u008cF\u0000\u03ac\u03aa\u0001\u0000\u0000\u0000"+
		"\u03ac\u03ab\u0001\u0000\u0000\u0000\u03ad\u03b9\u0001\u0000\u0000\u0000"+
		"\u03ae\u03af\u0003\u0092I\u0000\u03af\u03b2\u0003\u0090H\u0000\u03b0\u03b3"+
		"\u0003\u0092I\u0000\u03b1\u03b3\u0003\u008cF\u0000\u03b2\u03b0\u0001\u0000"+
		"\u0000\u0000\u03b2\u03b1\u0001\u0000\u0000\u0000\u03b3\u03b9\u0001\u0000"+
		"\u0000\u0000\u03b4\u03b5\u0003\u00a6S\u0000\u03b5\u03b6\u0007\u0003\u0000"+
		"\u0000\u03b6\u03b7\u0003\u00a6S\u0000\u03b7\u03b9\u0001\u0000\u0000\u0000"+
		"\u03b8\u0390\u0001\u0000\u0000\u0000\u03b8\u0396\u0001\u0000\u0000\u0000"+
		"\u03b8\u039c\u0001\u0000\u0000\u0000\u03b8\u03a2\u0001\u0000\u0000\u0000"+
		"\u03b8\u03a8\u0001\u0000\u0000\u0000\u03b8\u03ae\u0001\u0000\u0000\u0000"+
		"\u03b8\u03b4\u0001\u0000\u0000\u0000\u03b9\u008f\u0001\u0000\u0000\u0000"+
		"\u03ba\u03c1\u0005i\u0000\u0000\u03bb\u03c1\u0005\u0006\u0000\u0000\u03bc"+
		"\u03c1\u0005\u0007\u0000\u0000\u03bd\u03c1\u0005\b\u0000\u0000\u03be\u03c1"+
		"\u0005\t\u0000\u0000\u03bf\u03c1\u0005j\u0000\u0000\u03c0\u03ba\u0001"+
		"\u0000\u0000\u0000\u03c0\u03bb\u0001\u0000\u0000\u0000\u03c0\u03bc\u0001"+
		"\u0000\u0000\u0000\u03c0\u03bd\u0001\u0000\u0000\u0000\u03c0\u03be\u0001"+
		"\u0000\u0000\u0000\u03c0\u03bf\u0001\u0000\u0000\u0000\u03c1\u0091\u0001"+
		"\u0000\u0000\u0000\u03c2\u03c3\u0006I\uffff\uffff\u0000\u03c3\u03c4\u0003"+
		"\u0094J\u0000\u03c4\u03ca\u0001\u0000\u0000\u0000\u03c5\u03c6\n\u0001"+
		"\u0000\u0000\u03c6\u03c7\u0007\u0004\u0000\u0000\u03c7\u03c9\u0003\u0094"+
		"J\u0000\u03c8\u03c5\u0001\u0000\u0000\u0000\u03c9\u03cc\u0001\u0000\u0000"+
		"\u0000\u03ca\u03c8\u0001\u0000\u0000\u0000\u03ca\u03cb\u0001\u0000\u0000"+
		"\u0000\u03cb\u0093\u0001\u0000\u0000\u0000\u03cc\u03ca\u0001\u0000\u0000"+
		"\u0000\u03cd\u03ce\u0006J\uffff\uffff\u0000\u03ce\u03cf\u0003\u0096K\u0000"+
		"\u03cf\u03d5\u0001\u0000\u0000\u0000\u03d0\u03d1\n\u0001\u0000\u0000\u03d1"+
		"\u03d2\u0007\u0005\u0000\u0000\u03d2\u03d4\u0003\u0096K\u0000\u03d3\u03d0"+
		"\u0001\u0000\u0000\u0000\u03d4\u03d7\u0001\u0000\u0000\u0000\u03d5\u03d3"+
		"\u0001\u0000\u0000\u0000\u03d5\u03d6\u0001\u0000\u0000\u0000\u03d6\u0095"+
		"\u0001\u0000\u0000\u0000\u03d7\u03d5\u0001\u0000\u0000\u0000\u03d8\u03da"+
		"\u0007\u0004\u0000\u0000\u03d9\u03d8\u0001\u0000\u0000\u0000\u03d9\u03da"+
		"\u0001\u0000\u0000\u0000\u03da\u03db\u0001\u0000\u0000\u0000\u03db\u03dc"+
		"\u0003\u0098L\u0000\u03dc\u0097\u0001\u0000\u0000\u0000\u03dd\u03ed\u0003"+
		"2\u0019\u0000\u03de\u03ed\u0003\u00e0p\u0000\u03df\u03e0\u0005\u0002\u0000"+
		"\u0000\u03e0\u03e1\u0003\u0092I\u0000\u03e1\u03e2\u0005\u0003\u0000\u0000"+
		"\u03e2\u03ed\u0001\u0000\u0000\u0000\u03e3\u03ed\u0003\u00d6k\u0000\u03e4"+
		"\u03ed\u0003\u00aaU\u0000\u03e5\u03ed\u0003J%\u0000\u03e6\u03ed\u0003"+
		"\u00be_\u0000\u03e7\u03ed\u0003\u00b2Y\u0000\u03e8\u03e9\u0005\u0002\u0000"+
		"\u0000\u03e9\u03ea\u0003X,\u0000\u03ea\u03eb\u0005\u0003\u0000\u0000\u03eb"+
		"\u03ed\u0001\u0000\u0000\u0000\u03ec\u03dd\u0001\u0000\u0000\u0000\u03ec"+
		"\u03de\u0001\u0000\u0000\u0000\u03ec\u03df\u0001\u0000\u0000\u0000\u03ec"+
		"\u03e3\u0001\u0000\u0000\u0000\u03ec\u03e4\u0001\u0000\u0000\u0000\u03ec"+
		"\u03e5\u0001\u0000\u0000\u0000\u03ec\u03e6\u0001\u0000\u0000\u0000\u03ec"+
		"\u03e7\u0001\u0000\u0000\u0000\u03ec\u03e8\u0001\u0000\u0000\u0000\u03ed"+
		"\u0099\u0001\u0000\u0000\u0000\u03ee\u03fa\u00032\u0019\u0000\u03ef\u03fa"+
		"\u0003\u00e6s\u0000\u03f0\u03fa\u0003\u00d6k\u0000\u03f1\u03fa\u0003\u00ae"+
		"W\u0000\u03f2\u03fa\u0003J%\u0000\u03f3\u03fa\u0003\u00be_\u0000\u03f4"+
		"\u03fa\u0003\u00b2Y\u0000\u03f5\u03f6\u0005\u0002\u0000\u0000\u03f6\u03f7"+
		"\u0003X,\u0000\u03f7\u03f8\u0005\u0003\u0000\u0000\u03f8\u03fa\u0001\u0000"+
		"\u0000\u0000\u03f9\u03ee\u0001\u0000\u0000\u0000\u03f9\u03ef\u0001\u0000"+
		"\u0000\u0000\u03f9\u03f0\u0001\u0000\u0000\u0000\u03f9\u03f1\u0001\u0000"+
		"\u0000\u0000\u03f9\u03f2\u0001\u0000\u0000\u0000\u03f9\u03f3\u0001\u0000"+
		"\u0000\u0000\u03f9\u03f4\u0001\u0000\u0000\u0000\u03f9\u03f5\u0001\u0000"+
		"\u0000\u0000\u03fa\u009b\u0001\u0000\u0000\u0000\u03fb\u0407\u00032\u0019"+
		"\u0000\u03fc\u0407\u0003\u00d6k\u0000\u03fd\u0407\u0003\u00acV\u0000\u03fe"+
		"\u0407\u0003J%\u0000\u03ff\u0407\u0003\u00be_\u0000\u0400\u0407\u0003"+
		"\u00b2Y\u0000\u0401\u0407\u0003\u00dam\u0000\u0402\u0403\u0005\u0002\u0000"+
		"\u0000\u0403\u0404\u0003X,\u0000\u0404\u0405\u0005\u0003\u0000\u0000\u0405"+
		"\u0407\u0001\u0000\u0000\u0000\u0406\u03fb\u0001\u0000\u0000\u0000\u0406"+
		"\u03fc\u0001\u0000\u0000\u0000\u0406\u03fd\u0001\u0000\u0000\u0000\u0406"+
		"\u03fe\u0001\u0000\u0000\u0000\u0406\u03ff\u0001\u0000\u0000\u0000\u0406"+
		"\u0400\u0001\u0000\u0000\u0000\u0406\u0401\u0001\u0000\u0000\u0000\u0406"+
		"\u0402\u0001\u0000\u0000\u0000\u0407\u009d\u0001\u0000\u0000\u0000\u0408"+
		"\u0412\u00032\u0019\u0000\u0409\u0412\u0003\u00e2q\u0000\u040a\u0412\u0003"+
		"\u00d6k\u0000\u040b\u0412\u0003\u00be_\u0000\u040c\u0412\u0003\u00b2Y"+
		"\u0000\u040d\u040e\u0005\u0002\u0000\u0000\u040e\u040f\u0003X,\u0000\u040f"+
		"\u0410\u0005\u0003\u0000\u0000\u0410\u0412\u0001\u0000\u0000\u0000\u0411"+
		"\u0408\u0001\u0000\u0000\u0000\u0411\u0409\u0001\u0000\u0000\u0000\u0411"+
		"\u040a\u0001\u0000\u0000\u0000\u0411\u040b\u0001\u0000\u0000\u0000\u0411"+
		"\u040c\u0001\u0000\u0000\u0000\u0411\u040d\u0001\u0000\u0000\u0000\u0412"+
		"\u009f\u0001\u0000\u0000\u0000\u0413\u041c\u00032\u0019\u0000\u0414\u041c"+
		"\u0003\u00e4r\u0000\u0415\u041c\u0003\u00d6k\u0000\u0416\u041c\u0003\u00be"+
		"_\u0000\u0417\u0418\u0005\u0002\u0000\u0000\u0418\u0419\u0003X,\u0000"+
		"\u0419\u041a\u0005\u0003\u0000\u0000\u041a\u041c\u0001\u0000\u0000\u0000"+
		"\u041b\u0413\u0001\u0000\u0000\u0000\u041b\u0414\u0001\u0000\u0000\u0000"+
		"\u041b\u0415\u0001\u0000\u0000\u0000\u041b\u0416\u0001\u0000\u0000\u0000"+
		"\u041b\u0417\u0001\u0000\u0000\u0000\u041c\u00a1\u0001\u0000\u0000\u0000"+
		"\u041d\u0420\u00034\u001a\u0000\u041e\u0420\u0003\u00a4R\u0000\u041f\u041d"+
		"\u0001\u0000\u0000\u0000\u041f\u041e\u0001\u0000\u0000\u0000\u0420\u00a3"+
		"\u0001\u0000\u0000\u0000\u0421\u0424\u0003\u00d0h\u0000\u0422\u0424\u0003"+
		"\u00d6k\u0000\u0423\u0421\u0001\u0000\u0000\u0000\u0423\u0422\u0001\u0000"+
		"\u0000\u0000\u0424\u00a5\u0001\u0000\u0000\u0000\u0425\u0429\u0003\u00a8"+
		"T\u0000\u0426\u0429\u0003\u00dcn\u0000\u0427\u0429\u0003\u00d6k\u0000"+
		"\u0428\u0425\u0001\u0000\u0000\u0000\u0428\u0426\u0001\u0000\u0000\u0000"+
		"\u0428\u0427\u0001\u0000\u0000\u0000\u0429\u00a7\u0001\u0000\u0000\u0000"+
		"\u042a\u042b\u0005c\u0000\u0000\u042b\u042f\u0005\u0002\u0000\u0000\u042c"+
		"\u0430\u0003(\u0014\u0000\u042d\u0430\u00034\u001a\u0000\u042e\u0430\u0003"+
		"\u00d6k\u0000\u042f\u042c\u0001\u0000\u0000\u0000\u042f\u042d\u0001\u0000"+
		"\u0000\u0000\u042f\u042e\u0001\u0000\u0000\u0000\u0430\u0431\u0001\u0000"+
		"\u0000\u0000\u0431\u0432\u0005\u0003\u0000\u0000\u0432\u00a9\u0001\u0000"+
		"\u0000\u0000\u0433\u0434\u0005?\u0000\u0000\u0434\u0435\u0005\u0002\u0000"+
		"\u0000\u0435\u0436\u0003\u009aM\u0000\u0436\u0437\u0005\u0003\u0000\u0000"+
		"\u0437\u0487\u0001\u0000\u0000\u0000\u0438\u0439\u0005C\u0000\u0000\u0439"+
		"\u043a\u0005\u0002\u0000\u0000\u043a\u043b\u0003\u009aM\u0000\u043b\u043c"+
		"\u0005\u0001\u0000\u0000\u043c\u043f\u0003\u009aM\u0000\u043d\u043e\u0005"+
		"\u0001\u0000\u0000\u043e\u0440\u0003\u0092I\u0000\u043f\u043d\u0001\u0000"+
		"\u0000\u0000\u043f\u0440\u0001\u0000\u0000\u0000\u0440\u0441\u0001\u0000"+
		"\u0000\u0000\u0441\u0442\u0005\u0003\u0000\u0000\u0442\u0487\u0001\u0000"+
		"\u0000\u0000\u0443\u0444\u0005\u0011\u0000\u0000\u0444\u0445\u0005\u0002"+
		"\u0000\u0000\u0445\u0446\u0003\u0092I\u0000\u0446\u0447\u0005\u0003\u0000"+
		"\u0000\u0447\u0487\u0001\u0000\u0000\u0000\u0448\u0449\u0005\u001c\u0000"+
		"\u0000\u0449\u044a\u0005\u0002\u0000\u0000\u044a\u044b\u0003\u0092I\u0000"+
		"\u044b\u044c\u0005\u0003\u0000\u0000\u044c\u0487\u0001\u0000\u0000\u0000"+
		"\u044d\u044e\u0005.\u0000\u0000\u044e\u044f\u0005\u0002\u0000\u0000\u044f"+
		"\u0450\u0003\u0092I\u0000\u0450\u0451\u0005\u0003\u0000\u0000\u0451\u0487"+
		"\u0001\u0000\u0000\u0000\u0452\u0453\u00052\u0000\u0000\u0453\u0454\u0005"+
		"\u0002\u0000\u0000\u0454\u0455\u0003\u0092I\u0000\u0455\u0456\u0005\u0003"+
		"\u0000\u0000\u0456\u0487\u0001\u0000\u0000\u0000\u0457\u0458\u0005A\u0000"+
		"\u0000\u0458\u0459\u0005\u0002\u0000\u0000\u0459\u045a\u0003\u0092I\u0000"+
		"\u045a\u045b\u0005\u0003\u0000\u0000\u045b\u0487\u0001\u0000\u0000\u0000"+
		"\u045c\u045d\u0005W\u0000\u0000\u045d\u045e\u0005\u0002\u0000\u0000\u045e"+
		"\u045f\u0003\u0092I\u0000\u045f\u0460\u0005\u0003\u0000\u0000\u0460\u0487"+
		"\u0001\u0000\u0000\u0000\u0461\u0462\u0005Z\u0000\u0000\u0462\u0463\u0005"+
		"\u0002\u0000\u0000\u0463\u0464\u0003\u0092I\u0000\u0464\u0465\u0005\u0003"+
		"\u0000\u0000\u0465\u0487\u0001\u0000\u0000\u0000\u0466\u0467\u0005H\u0000"+
		"\u0000\u0467\u0468\u0005\u0002\u0000\u0000\u0468\u0469\u0003\u0092I\u0000"+
		"\u0469\u046a\u0005\u0001\u0000\u0000\u046a\u046b\u0003\u0092I\u0000\u046b"+
		"\u046c\u0005\u0003\u0000\u0000\u046c\u0487\u0001\u0000\u0000\u0000\u046d"+
		"\u046e\u0005S\u0000\u0000\u046e\u046f\u0005\u0002\u0000\u0000\u046f\u0470"+
		"\u0003\u0092I\u0000\u0470\u0471\u0005\u0001\u0000\u0000\u0471\u0472\u0003"+
		"\u0092I\u0000\u0472\u0473\u0005\u0003\u0000\u0000\u0473\u0487\u0001\u0000"+
		"\u0000\u0000\u0474\u0475\u0005T\u0000\u0000\u0475\u0476\u0005\u0002\u0000"+
		"\u0000\u0476\u0477\u0003\u0092I\u0000\u0477\u0478\u0005\u0001\u0000\u0000"+
		"\u0478\u0479\u0003\u0092I\u0000\u0479\u047a\u0005\u0003\u0000\u0000\u047a"+
		"\u0487\u0001\u0000\u0000\u0000\u047b\u047c\u0005X\u0000\u0000\u047c\u047d"+
		"\u0005\u0002\u0000\u0000\u047d\u047e\u00036\u001b\u0000\u047e\u047f\u0005"+
		"\u0003\u0000\u0000\u047f\u0487\u0001\u0000\u0000\u0000\u0480\u0481\u0005"+
		"8\u0000\u0000\u0481\u0482\u0005\u0002\u0000\u0000\u0482\u0483\u0003\u00d0"+
		"h\u0000\u0483\u0484\u0005\u0003\u0000\u0000\u0484\u0487\u0001\u0000\u0000"+
		"\u0000\u0485\u0487\u0003\u00b4Z\u0000\u0486\u0433\u0001\u0000\u0000\u0000"+
		"\u0486\u0438\u0001\u0000\u0000\u0000\u0486\u0443\u0001\u0000\u0000\u0000"+
		"\u0486\u0448\u0001\u0000\u0000\u0000\u0486\u044d\u0001\u0000\u0000\u0000"+
		"\u0486\u0452\u0001\u0000\u0000\u0000\u0486\u0457\u0001\u0000\u0000\u0000"+
		"\u0486\u045c\u0001\u0000\u0000\u0000\u0486\u0461\u0001\u0000\u0000\u0000"+
		"\u0486\u0466\u0001\u0000\u0000\u0000\u0486\u046d\u0001\u0000\u0000\u0000"+
		"\u0486\u0474\u0001\u0000\u0000\u0000\u0486\u047b\u0001\u0000\u0000\u0000"+
		"\u0486\u0480\u0001\u0000\u0000\u0000\u0486\u0485\u0001\u0000\u0000\u0000"+
		"\u0487\u00ab\u0001\u0000\u0000\u0000\u0488\u0493\u0005 \u0000\u0000\u0489"+
		"\u0493\u0005!\u0000\u0000\u048a\u0493\u0005\"\u0000\u0000\u048b\u048c"+
		"\u0005B\u0000\u0000\u048c\u0493\u0005#\u0000\u0000\u048d\u048e\u0005B"+
		"\u0000\u0000\u048e\u0493\u0005^\u0000\u0000\u048f\u0490\u0005B\u0000\u0000"+
		"\u0490\u0493\u0005$\u0000\u0000\u0491\u0493\u0003\u00b8\\\u0000\u0492"+
		"\u0488\u0001\u0000\u0000\u0000\u0492\u0489\u0001\u0000\u0000\u0000\u0492"+
		"\u048a\u0001\u0000\u0000\u0000\u0492\u048b\u0001\u0000\u0000\u0000\u0492"+
		"\u048d\u0001\u0000\u0000\u0000\u0492\u048f\u0001\u0000\u0000\u0000\u0492"+
		"\u0491\u0001\u0000\u0000\u0000\u0493\u00ad\u0001\u0000\u0000\u0000\u0494"+
		"\u0495\u0005\u001e\u0000\u0000\u0495\u0496\u0005\u0002\u0000\u0000\u0496"+
		"\u0497\u0003\u009aM\u0000\u0497\u0498\u0005\u0001\u0000\u0000\u0498\u049d"+
		"\u0003\u009aM\u0000\u0499\u049a\u0005\u0001\u0000\u0000\u049a\u049c\u0003"+
		"\u009aM\u0000\u049b\u0499\u0001\u0000\u0000\u0000\u049c\u049f\u0001\u0000"+
		"\u0000\u0000\u049d\u049b\u0001\u0000\u0000\u0000\u049d\u049e\u0001\u0000"+
		"\u0000\u0000\u049e\u04a0\u0001\u0000\u0000\u0000\u049f\u049d\u0001\u0000"+
		"\u0000\u0000\u04a0\u04a1\u0005\u0003\u0000\u0000\u04a1\u04c6\u0001\u0000"+
		"\u0000\u0000\u04a2\u04a3\u0005[\u0000\u0000\u04a3\u04a4\u0005\u0002\u0000"+
		"\u0000\u04a4\u04a5\u0003\u009aM\u0000\u04a5\u04a6\u0005\u0001\u0000\u0000"+
		"\u04a6\u04a9\u0003\u0092I\u0000\u04a7\u04a8\u0005\u0001\u0000\u0000\u04a8"+
		"\u04aa\u0003\u0092I\u0000\u04a9\u04a7\u0001\u0000\u0000\u0000\u04a9\u04aa"+
		"\u0001\u0000\u0000\u0000\u04aa\u04ab\u0001\u0000\u0000\u0000\u04ab\u04ac"+
		"\u0005\u0003\u0000\u0000\u04ac\u04c6\u0001\u0000\u0000\u0000\u04ad\u04ae"+
		"\u0005a\u0000\u0000\u04ae\u04b6\u0005\u0002\u0000\u0000\u04af\u04b1\u0003"+
		"\u00b0X\u0000\u04b0\u04af\u0001\u0000\u0000\u0000\u04b0\u04b1\u0001\u0000"+
		"\u0000\u0000\u04b1\u04b3\u0001\u0000\u0000\u0000\u04b2\u04b4\u0003\u00ce"+
		"g\u0000\u04b3\u04b2\u0001\u0000\u0000\u0000\u04b3\u04b4\u0001\u0000\u0000"+
		"\u0000\u04b4\u04b5\u0001\u0000\u0000\u0000\u04b5\u04b7\u00053\u0000\u0000"+
		"\u04b6\u04b0\u0001\u0000\u0000\u0000\u04b6\u04b7\u0001\u0000\u0000\u0000"+
		"\u04b7\u04b8\u0001\u0000\u0000\u0000\u04b8\u04b9\u0003\u009aM\u0000\u04b9"+
		"\u04ba\u0005\u0003\u0000\u0000\u04ba\u04c6\u0001\u0000\u0000\u0000\u04bb"+
		"\u04bc\u0005D\u0000\u0000\u04bc\u04bd\u0005\u0002\u0000\u0000\u04bd\u04be"+
		"\u0003\u009aM\u0000\u04be\u04bf\u0005\u0003\u0000\u0000\u04bf\u04c6\u0001"+
		"\u0000\u0000\u0000\u04c0\u04c1\u0005e\u0000\u0000\u04c1\u04c2\u0005\u0002"+
		"\u0000\u0000\u04c2\u04c3\u0003\u009aM\u0000\u04c3\u04c4\u0005\u0003\u0000"+
		"\u0000\u04c4\u04c6\u0001\u0000\u0000\u0000\u04c5\u0494\u0001\u0000\u0000"+
		"\u0000\u04c5\u04a2\u0001\u0000\u0000\u0000\u04c5\u04ad\u0001\u0000\u0000"+
		"\u0000\u04c5\u04bb\u0001\u0000\u0000\u0000\u04c5\u04c0\u0001\u0000\u0000"+
		"\u0000\u04c6\u00af\u0001\u0000\u0000\u0000\u04c7\u04c8\u0007\u0006\u0000"+
		"\u0000\u04c8\u00b1\u0001\u0000\u0000\u0000\u04c9\u04ca\u00054\u0000\u0000"+
		"\u04ca\u04cb\u0005\u0002\u0000\u0000\u04cb\u04d0\u0003\u00fe\u007f\u0000"+
		"\u04cc\u04cd\u0005\u0001\u0000\u0000\u04cd\u04cf\u0003\u00bc^\u0000\u04ce"+
		"\u04cc\u0001\u0000\u0000\u0000\u04cf\u04d2\u0001\u0000\u0000\u0000\u04d0"+
		"\u04ce\u0001\u0000\u0000\u0000\u04d0\u04d1\u0001\u0000\u0000\u0000\u04d1"+
		"\u04d3\u0001\u0000\u0000\u0000\u04d2\u04d0\u0001\u0000\u0000\u0000\u04d3"+
		"\u04d4\u0005\u0003\u0000\u0000\u04d4\u00b3\u0001\u0000\u0000\u0000\u04d5"+
		"\u04d6\u0005/\u0000\u0000\u04d6\u04d7\u0005\u0002\u0000\u0000\u04d7\u04d8"+
		"\u0003\u00b6[\u0000\u04d8\u04d9\u00053\u0000\u0000\u04d9\u04da\u0003\u009c"+
		"N\u0000\u04da\u04db\u0005\u0003\u0000\u0000\u04db\u00b5\u0001\u0000\u0000"+
		"\u0000\u04dc\u04dd\u0003\u00d0h\u0000\u04dd\u00b7\u0001\u0000\u0000\u0000"+
		"\u04de\u04df\u0005/\u0000\u0000\u04df\u04e0\u0005\u0002\u0000\u0000\u04e0"+
		"\u04e1\u0003\u00ba]\u0000\u04e1\u04e2\u00053\u0000\u0000\u04e2\u04e3\u0003"+
		"\u009cN\u0000\u04e3\u04e4\u0005\u0003\u0000\u0000\u04e4\u00b9\u0001\u0000"+
		"\u0000\u0000\u04e5\u04e6\u0003\u00d0h\u0000\u04e6\u00bb\u0001\u0000\u0000"+
		"\u0000\u04e7\u04ec\u0003\u00d4j\u0000\u04e8\u04ec\u00032\u0019\u0000\u04e9"+
		"\u04ec\u0003\u00d6k\u0000\u04ea\u04ec\u0003l6\u0000\u04eb\u04e7\u0001"+
		"\u0000\u0000\u0000\u04eb\u04e8\u0001\u0000\u0000\u0000\u04eb\u04e9\u0001"+
		"\u0000\u0000\u0000\u04eb\u04ea\u0001\u0000\u0000\u0000\u04ec\u00bd\u0001"+
		"\u0000\u0000\u0000\u04ed\u04f2\u0003\u00c0`\u0000\u04ee\u04f2\u0003\u00c4"+
		"b\u0000\u04ef\u04f2\u0003\u00cae\u0000\u04f0\u04f2\u0003\u00ccf\u0000"+
		"\u04f1\u04ed\u0001\u0000\u0000\u0000\u04f1\u04ee\u0001\u0000\u0000\u0000"+
		"\u04f1\u04ef\u0001\u0000\u0000\u0000\u04f1\u04f0\u0001\u0000\u0000\u0000"+
		"\u04f2\u00bf\u0001\u0000\u0000\u0000\u04f3\u04f4\u0005\u001b\u0000\u0000"+
		"\u04f4\u04f8\u0003\u00c2a\u0000\u04f5\u04f7\u0003\u00c2a\u0000\u04f6\u04f5"+
		"\u0001\u0000\u0000\u0000\u04f7\u04fa\u0001\u0000\u0000\u0000\u04f8\u04f6"+
		"\u0001\u0000\u0000\u0000\u04f8\u04f9\u0001\u0000\u0000\u0000\u04f9\u04fb"+
		"\u0001\u0000\u0000\u0000\u04fa\u04f8\u0001\u0000\u0000\u0000\u04fb\u04fc"+
		"\u0005)\u0000\u0000\u04fc\u04fd\u0003l6\u0000\u04fd\u04fe\u0005(\u0000"+
		"\u0000\u04fe\u00c1\u0001\u0000\u0000\u0000\u04ff\u0500\u0005g\u0000\u0000"+
		"\u0500\u0501\u0003n7\u0000\u0501\u0502\u0005]\u0000\u0000\u0502\u0503"+
		"\u0003l6\u0000\u0503\u00c3\u0001\u0000\u0000\u0000\u0504\u0505\u0005\u001b"+
		"\u0000\u0000\u0505\u0506\u0003\u00c6c\u0000\u0506\u050a\u0003\u00c8d\u0000"+
		"\u0507\u0509\u0003\u00c8d\u0000\u0508\u0507\u0001\u0000\u0000\u0000\u0509"+
		"\u050c\u0001\u0000\u0000\u0000\u050a\u0508\u0001\u0000\u0000\u0000\u050a"+
		"\u050b\u0001\u0000\u0000\u0000\u050b\u050d\u0001\u0000\u0000\u0000\u050c"+
		"\u050a\u0001\u0000\u0000\u0000\u050d\u050e\u0005)\u0000\u0000\u050e\u050f"+
		"\u0003l6\u0000\u050f\u0510\u0005(\u0000\u0000\u0510\u00c5\u0001\u0000"+
		"\u0000\u0000\u0511\u0514\u00032\u0019\u0000\u0512\u0514\u0003\u00a8T\u0000"+
		"\u0513\u0511\u0001\u0000\u0000\u0000\u0513\u0512\u0001\u0000\u0000\u0000"+
		"\u0514\u00c7\u0001\u0000\u0000\u0000\u0515\u0516\u0005g\u0000\u0000\u0516"+
		"\u0517\u0003l6\u0000\u0517\u0518\u0005]\u0000\u0000\u0518\u0519\u0003"+
		"l6\u0000\u0519\u00c9\u0001\u0000\u0000\u0000\u051a\u051b\u0005\u001d\u0000"+
		"\u0000\u051b\u051c\u0005\u0002\u0000\u0000\u051c\u051f\u0003l6\u0000\u051d"+
		"\u051e\u0005\u0001\u0000\u0000\u051e\u0520\u0003l6\u0000\u051f\u051d\u0001"+
		"\u0000\u0000\u0000\u0520\u0521\u0001\u0000\u0000\u0000\u0521\u051f\u0001"+
		"\u0000\u0000\u0000\u0521\u0522\u0001\u0000\u0000\u0000\u0522\u0523\u0001"+
		"\u0000\u0000\u0000\u0523\u0524\u0005\u0003\u0000\u0000\u0524\u00cb\u0001"+
		"\u0000\u0000\u0000\u0525\u0526\u0005L\u0000\u0000\u0526\u0527\u0005\u0002"+
		"\u0000\u0000\u0527\u0528\u0003l6\u0000\u0528\u0529\u0005\u0001\u0000\u0000"+
		"\u0529\u052a\u0003l6\u0000\u052a\u052b\u0005\u0003\u0000\u0000\u052b\u00cd"+
		"\u0001\u0000\u0000\u0000\u052c\u052f\u0005k\u0000\u0000\u052d\u052f\u0003"+
		"\u0100\u0080\u0000\u052e\u052c\u0001\u0000\u0000\u0000\u052e\u052d\u0001"+
		"\u0000\u0000\u0000\u052f\u00cf\u0001\u0000\u0000\u0000\u0530\u0533\u0005"+
		"l\u0000\u0000\u0531\u0533\u0007\u0007\u0000\u0000\u0532\u0530\u0001\u0000"+
		"\u0000\u0000\u0532\u0531\u0001\u0000\u0000\u0000\u0533\u00d1\u0001\u0000"+
		"\u0000\u0000\u0534\u0535\u00030\u0018\u0000\u0535\u00d3\u0001\u0000\u0000"+
		"\u0000\u0536\u053e\u0005m\u0000\u0000\u0537\u053e\u0005n\u0000\u0000\u0538"+
		"\u053e\u0005p\u0000\u0000\u0539\u053e\u0005o\u0000\u0000\u053a\u053e\u0005"+
		"q\u0000\u0000\u053b\u053e\u0003\u00e2q\u0000\u053c\u053e\u0003\u00dcn"+
		"\u0000\u053d\u0536\u0001\u0000\u0000\u0000\u053d\u0537\u0001\u0000\u0000"+
		"\u0000\u053d\u0538\u0001\u0000\u0000\u0000\u053d\u0539\u0001\u0000\u0000"+
		"\u0000\u053d\u053a\u0001\u0000\u0000\u0000\u053d\u053b\u0001\u0000\u0000"+
		"\u0000\u053d\u053c\u0001\u0000\u0000\u0000\u053e\u00d5\u0001\u0000\u0000"+
		"\u0000\u053f\u0540\u0005\u000e\u0000\u0000\u0540\u0548\u0005r\u0000\u0000"+
		"\u0541\u0542\u0005\u000f\u0000\u0000\u0542\u0548\u0005r\u0000\u0000\u0543"+
		"\u0544\u0005\u000f\u0000\u0000\u0544\u0548\u0005p\u0000\u0000\u0545\u0546"+
		"\u0005\u000e\u0000\u0000\u0546\u0548\u0003\u00d0h\u0000\u0547\u053f\u0001"+
		"\u0000\u0000\u0000\u0547\u0541\u0001\u0000\u0000\u0000\u0547\u0543\u0001"+
		"\u0000\u0000\u0000\u0547\u0545\u0001\u0000\u0000\u0000\u0548\u00d7\u0001"+
		"\u0000\u0000\u0000\u0549\u054a\u0003\u009aM\u0000\u054a\u00d9\u0001\u0000"+
		"\u0000\u0000\u054b\u054c\u0005m\u0000\u0000\u054c\u00db\u0001\u0000\u0000"+
		"\u0000\u054d\u054e\u0003\u00d0h\u0000\u054e\u00dd\u0001\u0000\u0000\u0000"+
		"\u054f\u0552\u0005k\u0000\u0000\u0550\u0552\u0003\u0100\u0080\u0000\u0551"+
		"\u054f\u0001\u0000\u0000\u0000\u0551\u0550\u0001\u0000\u0000\u0000\u0552"+
		"\u00df\u0001\u0000\u0000\u0000\u0553\u0554\u0007\b\u0000\u0000\u0554\u00e1"+
		"\u0001\u0000\u0000\u0000\u0555\u0556\u0007\t\u0000\u0000\u0556\u00e3\u0001"+
		"\u0000\u0000\u0000\u0557\u0558\u00030\u0018\u0000\u0558\u00e5\u0001\u0000"+
		"\u0000\u0000\u0559\u055a\u0007\n\u0000\u0000\u055a\u00e7\u0001\u0000\u0000"+
		"\u0000\u055b\u055c\u0003\u00d0h\u0000\u055c\u00e9\u0001\u0000\u0000\u0000"+
		"\u055d\u055e\u0003\u00d0h\u0000\u055e\u00eb\u0001\u0000\u0000\u0000\u055f"+
		"\u0560\u0003\u00d0h\u0000\u0560\u00ed\u0001\u0000\u0000\u0000\u0561\u0562"+
		"\u0003\u00d0h\u0000\u0562\u00ef\u0001\u0000\u0000\u0000\u0563\u0564\u0003"+
		"\u00d0h\u0000\u0564\u00f1\u0001\u0000\u0000\u0000\u0565\u0566\u0003\u00d0"+
		"h\u0000\u0566\u00f3\u0001\u0000\u0000\u0000\u0567\u056c\u0003\u00d0h\u0000"+
		"\u0568\u0569\u0005\u0004\u0000\u0000\u0569\u056b\u0003\u00d0h\u0000\u056a"+
		"\u0568\u0001\u0000\u0000\u0000\u056b\u056e\u0001\u0000\u0000\u0000\u056c"+
		"\u056a\u0001\u0000\u0000\u0000\u056c\u056d\u0001\u0000\u0000\u0000\u056d"+
		"\u00f5\u0001\u0000\u0000\u0000\u056e\u056c\u0001\u0000\u0000\u0000\u056f"+
		"\u0570\u0003\u00d0h\u0000\u0570\u00f7\u0001\u0000\u0000\u0000\u0571\u0572"+
		"\u0003\u00d0h\u0000\u0572\u00f9\u0001\u0000\u0000\u0000\u0573\u0574\u0003"+
		"\u00d6k\u0000\u0574\u00fb\u0001\u0000\u0000\u0000\u0575\u0576\u0003\u00d6"+
		"k\u0000\u0576\u00fd\u0001\u0000\u0000\u0000\u0577\u0578\u0003\u00e6s\u0000"+
		"\u0578\u00ff\u0001\u0000\u0000\u0000\u0579\u057c\u0005k\u0000\u0000\u057a"+
		"\u057c\u0003\u00d6k\u0000\u057b\u0579\u0001\u0000\u0000\u0000\u057b\u057a"+
		"\u0001\u0000\u0000\u0000\u057c\u0101\u0001\u0000\u0000\u0000\u008b\u0108"+
		"\u010d\u0110\u0113\u0116\u011a\u011e\u0126\u012b\u0130\u0132\u0137\u013e"+
		"\u0142\u014a\u014d\u0164\u016d\u0179\u0183\u018d\u0199\u01a5\u01a9\u01b1"+
		"\u01b4\u01bc\u01bf\u01ce\u01db\u01de\u01e6\u01ec\u01f3\u01f8\u0200\u0206"+
		"\u0209\u020d\u0214\u0219\u021c\u0228\u0232\u023c\u0241\u0249\u024e\u0253"+
		"\u025f\u0264\u0270\u0276\u0279\u027e\u0281\u0284\u028b\u028f\u0295\u029b"+
		"\u029f\u02a9\u02b1\u02b4\u02bb\u02cd\u02d4\u02dc\u02e5\u02ef\u02fa\u02fe"+
		"\u0307\u0311\u0315\u031e\u0327\u032e\u0332\u0335\u033e\u0348\u034c\u0350"+
		"\u0354\u0358\u035c\u0360\u0364\u036b\u0371\u0375\u037c\u0381\u0384\u0394"+
		"\u039a\u03a0\u03a6\u03ac\u03b2\u03b8\u03c0\u03ca\u03d5\u03d9\u03ec\u03f9"+
		"\u0406\u0411\u041b\u041f\u0423\u0428\u042f\u043f\u0486\u0492\u049d\u04a9"+
		"\u04b0\u04b3\u04b6\u04c5\u04d0\u04eb\u04f1\u04f8\u050a\u0513\u0521\u052e"+
		"\u0532\u053d\u0547\u0551\u056c\u057b";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}