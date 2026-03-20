// Generated from Hql.g4 by ANTLR 4.13.2
package org.springframework.ide.vscode.parser.hql;

/**
 * HQL per https://docs.jboss.org/hibernate/orm/6.1/userguide/html_single/Hibernate_User_Guide.html#query-language
 *
 * This is a mixture of Hibernate's BNF and missing bits of grammar. There are gaps and inconsistencies in the
 * BNF itself, explained by other fragments of their spec. Additionally, alternate labels are used to provide easier
 * management of complex rules in the generated Visitor. Finally, there are labels applied to rule elements (op=('+'|'-')
 * to simplify the processing.
 *
 * @author Greg Turnquist
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
public class HqlParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, T__22=23, WS=24, ALL=25, 
		AND=26, ANY=27, AS=28, ASC=29, ASTERISK=30, AVG=31, BETWEEN=32, BOTH=33, 
		BREADTH=34, BY=35, CASE=36, CAST=37, CEILING=38, COLLATE=39, COUNT=40, 
		CROSS=41, CUBE=42, CURRENT=43, CURRENT_DATE=44, CURRENT_INSTANT=45, CURRENT_TIME=46, 
		CURRENT_TIMESTAMP=47, CYCLE=48, DATE=49, DATETIME=50, DAY=51, DEFAULT=52, 
		DELETE=53, DEPTH=54, DESC=55, DISTINCT=56, ELEMENT=57, ELEMENTS=58, ELSE=59, 
		EMPTY=60, END=61, ENTRY=62, EPOCH=63, ERROR=64, ESCAPE=65, EVERY=66, EXCEPT=67, 
		EXCLUDE=68, EXISTS=69, EXP=70, EXTRACT=71, FALSE=72, FETCH=73, FILTER=74, 
		FIRST=75, FK=76, FLOOR=77, FOLLOWING=78, FOR=79, FORMAT=80, FROM=81, FULL=82, 
		FUNCTION=83, GROUP=84, GROUPS=85, HAVING=86, HOUR=87, ID=88, IGNORE=89, 
		ILIKE=90, IN=91, INDEX=92, INDICES=93, INNER=94, INSERT=95, INSTANT=96, 
		INTERSECT=97, INTO=98, IS=99, JOIN=100, KEY=101, LAST=102, LATERAL=103, 
		LEADING=104, LEFT=105, LIKE=106, LIMIT=107, LIST=108, LISTAGG=109, LN=110, 
		LOCAL=111, LOCAL_DATE=112, LOCAL_DATETIME=113, LOCAL_TIME=114, MAP=115, 
		MATERIALIZED=116, MAX=117, MAXELEMENT=118, MAXINDEX=119, MEMBER=120, MICROSECOND=121, 
		MILLISECOND=122, MIN=123, MINELEMENT=124, MININDEX=125, MINUTE=126, MONTH=127, 
		NANOSECOND=128, NATURALID=129, NEW=130, NEXT=131, NO=132, NOT=133, NULL=134, 
		NULLS=135, OBJECT=136, OF=137, OFFSET=138, OFFSET_DATETIME=139, ON=140, 
		ONLY=141, OR=142, ORDER=143, OTHERS=144, OUTER=145, OVER=146, OVERFLOW=147, 
		OVERLAY=148, PAD=149, PARTITION=150, PERCENT=151, PLACING=152, POSITION=153, 
		POWER=154, PRECEDING=155, QUARTER=156, RANGE=157, RESPECT=158, RIGHT=159, 
		ROLLUP=160, ROW=161, ROWS=162, SEARCH=163, SECOND=164, SELECT=165, SET=166, 
		SIZE=167, SOME=168, SUBSTRING=169, SUM=170, THEN=171, TIES=172, TIME=173, 
		TIMESTAMP=174, TIMEZONE_HOUR=175, TIMEZONE_MINUTE=176, TO=177, TRAILING=178, 
		TREAT=179, TRIM=180, TRUE=181, TRUNC=182, TRUNCATE=183, TYPE=184, UNBOUNDED=185, 
		UNION=186, UPDATE=187, USING=188, VALUE=189, VALUES=190, VERSION=191, 
		VERSIONED=192, WEEK=193, WHEN=194, WHERE=195, WITH=196, WITHIN=197, WITHOUT=198, 
		YEAR=199, CHARACTER=200, STRINGLITERAL=201, JAVASTRINGLITERAL=202, INTEGER_LITERAL=203, 
		FLOAT_LITERAL=204, HEXLITERAL=205, BINARY_LITERAL=206, IDENTIFICATION_VARIABLE=207, 
		SPEL=208;
	public static final int
		RULE_start = 0, RULE_ql_statement = 1, RULE_selectStatement = 2, RULE_queryExpression = 3, 
		RULE_withClause = 4, RULE_cte = 5, RULE_searchClause = 6, RULE_searchSpecifications = 7, 
		RULE_searchSpecification = 8, RULE_cycleClause = 9, RULE_cteAttributes = 10, 
		RULE_orderedQuery = 11, RULE_query = 12, RULE_queryOrder = 13, RULE_fromClause = 14, 
		RULE_entityWithJoins = 15, RULE_joinSpecifier = 16, RULE_fromRoot = 17, 
		RULE_join = 18, RULE_joinTarget = 19, RULE_updateStatement = 20, RULE_targetEntity = 21, 
		RULE_setClause = 22, RULE_assignment = 23, RULE_deleteStatement = 24, 
		RULE_insertStatement = 25, RULE_targetFields = 26, RULE_valuesList = 27, 
		RULE_values = 28, RULE_instantiation = 29, RULE_alias = 30, RULE_groupedItem = 31, 
		RULE_sortedItem = 32, RULE_sortExpression = 33, RULE_sortDirection = 34, 
		RULE_nullsPrecedence = 35, RULE_limitClause = 36, RULE_offsetClause = 37, 
		RULE_fetchClause = 38, RULE_subquery = 39, RULE_selectClause = 40, RULE_selectionList = 41, 
		RULE_selection = 42, RULE_selectExpression = 43, RULE_mapEntrySelection = 44, 
		RULE_jpaSelectObjectSyntax = 45, RULE_whereClause = 46, RULE_joinType = 47, 
		RULE_crossJoin = 48, RULE_joinRestriction = 49, RULE_jpaCollectionJoin = 50, 
		RULE_groupByClause = 51, RULE_orderByClause = 52, RULE_havingClause = 53, 
		RULE_setOperator = 54, RULE_literal = 55, RULE_booleanLiteral = 56, RULE_stringLiteral = 57, 
		RULE_numericLiteral = 58, RULE_dateTimeLiteral = 59, RULE_datetimeField = 60, 
		RULE_binaryLiteral = 61, RULE_expression = 62, RULE_primaryExpression = 63, 
		RULE_identificationVariable = 64, RULE_path = 65, RULE_generalPathFragment = 66, 
		RULE_indexedPathAccessFragment = 67, RULE_simplePath = 68, RULE_simplePathElement = 69, 
		RULE_caseList = 70, RULE_simpleCaseExpression = 71, RULE_searchedCaseExpression = 72, 
		RULE_caseWhenExpressionClause = 73, RULE_caseWhenPredicateClause = 74, 
		RULE_function = 75, RULE_functionArguments = 76, RULE_filterClause = 77, 
		RULE_withinGroup = 78, RULE_overClause = 79, RULE_partitionClause = 80, 
		RULE_frameClause = 81, RULE_frameStart = 82, RULE_frameExclusion = 83, 
		RULE_frameEnd = 84, RULE_castFunction = 85, RULE_castTarget = 86, RULE_castTargetType = 87, 
		RULE_extractFunction = 88, RULE_trimFunction = 89, RULE_dateTimeFunction = 90, 
		RULE_everyFunction = 91, RULE_anyFunction = 92, RULE_treatedPath = 93, 
		RULE_pathContinutation = 94, RULE_predicate = 95, RULE_expressionOrPredicate = 96, 
		RULE_relationalExpression = 97, RULE_betweenExpression = 98, RULE_dealingWithNullExpression = 99, 
		RULE_stringPatternMatching = 100, RULE_inExpression = 101, RULE_inList = 102, 
		RULE_existsExpression = 103, RULE_collectionExpression = 104, RULE_instantiationTarget = 105, 
		RULE_instantiationArguments = 106, RULE_instantiationArgument = 107, RULE_parameterOrIntegerLiteral = 108, 
		RULE_parameterOrNumberLiteral = 109, RULE_variable = 110, RULE_parameter = 111, 
		RULE_entityName = 112, RULE_identifier = 113, RULE_character = 114, RULE_functionName = 115, 
		RULE_reservedWord = 116;
	private static String[] makeRuleNames() {
		return new String[] {
			"start", "ql_statement", "selectStatement", "queryExpression", "withClause", 
			"cte", "searchClause", "searchSpecifications", "searchSpecification", 
			"cycleClause", "cteAttributes", "orderedQuery", "query", "queryOrder", 
			"fromClause", "entityWithJoins", "joinSpecifier", "fromRoot", "join", 
			"joinTarget", "updateStatement", "targetEntity", "setClause", "assignment", 
			"deleteStatement", "insertStatement", "targetFields", "valuesList", "values", 
			"instantiation", "alias", "groupedItem", "sortedItem", "sortExpression", 
			"sortDirection", "nullsPrecedence", "limitClause", "offsetClause", "fetchClause", 
			"subquery", "selectClause", "selectionList", "selection", "selectExpression", 
			"mapEntrySelection", "jpaSelectObjectSyntax", "whereClause", "joinType", 
			"crossJoin", "joinRestriction", "jpaCollectionJoin", "groupByClause", 
			"orderByClause", "havingClause", "setOperator", "literal", "booleanLiteral", 
			"stringLiteral", "numericLiteral", "dateTimeLiteral", "datetimeField", 
			"binaryLiteral", "expression", "primaryExpression", "identificationVariable", 
			"path", "generalPathFragment", "indexedPathAccessFragment", "simplePath", 
			"simplePathElement", "caseList", "simpleCaseExpression", "searchedCaseExpression", 
			"caseWhenExpressionClause", "caseWhenPredicateClause", "function", "functionArguments", 
			"filterClause", "withinGroup", "overClause", "partitionClause", "frameClause", 
			"frameStart", "frameExclusion", "frameEnd", "castFunction", "castTarget", 
			"castTargetType", "extractFunction", "trimFunction", "dateTimeFunction", 
			"everyFunction", "anyFunction", "treatedPath", "pathContinutation", "predicate", 
			"expressionOrPredicate", "relationalExpression", "betweenExpression", 
			"dealingWithNullExpression", "stringPatternMatching", "inExpression", 
			"inList", "existsExpression", "collectionExpression", "instantiationTarget", 
			"instantiationArguments", "instantiationArgument", "parameterOrIntegerLiteral", 
			"parameterOrNumberLiteral", "variable", "parameter", "entityName", "identifier", 
			"character", "functionName", "reservedWord"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "','", "'('", "')'", "'='", "'%'", "'{'", "'}'", "'+'", "'-'", 
			"'/'", "'||'", "'['", "']'", "'.'", "'>'", "'>='", "'<'", "'<='", "'<>'", 
			"'!='", "'^='", "':'", "'?'", null, null, null, null, null, null, "'*'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			"WS", "ALL", "AND", "ANY", "AS", "ASC", "ASTERISK", "AVG", "BETWEEN", 
			"BOTH", "BREADTH", "BY", "CASE", "CAST", "CEILING", "COLLATE", "COUNT", 
			"CROSS", "CUBE", "CURRENT", "CURRENT_DATE", "CURRENT_INSTANT", "CURRENT_TIME", 
			"CURRENT_TIMESTAMP", "CYCLE", "DATE", "DATETIME", "DAY", "DEFAULT", "DELETE", 
			"DEPTH", "DESC", "DISTINCT", "ELEMENT", "ELEMENTS", "ELSE", "EMPTY", 
			"END", "ENTRY", "EPOCH", "ERROR", "ESCAPE", "EVERY", "EXCEPT", "EXCLUDE", 
			"EXISTS", "EXP", "EXTRACT", "FALSE", "FETCH", "FILTER", "FIRST", "FK", 
			"FLOOR", "FOLLOWING", "FOR", "FORMAT", "FROM", "FULL", "FUNCTION", "GROUP", 
			"GROUPS", "HAVING", "HOUR", "ID", "IGNORE", "ILIKE", "IN", "INDEX", "INDICES", 
			"INNER", "INSERT", "INSTANT", "INTERSECT", "INTO", "IS", "JOIN", "KEY", 
			"LAST", "LATERAL", "LEADING", "LEFT", "LIKE", "LIMIT", "LIST", "LISTAGG", 
			"LN", "LOCAL", "LOCAL_DATE", "LOCAL_DATETIME", "LOCAL_TIME", "MAP", "MATERIALIZED", 
			"MAX", "MAXELEMENT", "MAXINDEX", "MEMBER", "MICROSECOND", "MILLISECOND", 
			"MIN", "MINELEMENT", "MININDEX", "MINUTE", "MONTH", "NANOSECOND", "NATURALID", 
			"NEW", "NEXT", "NO", "NOT", "NULL", "NULLS", "OBJECT", "OF", "OFFSET", 
			"OFFSET_DATETIME", "ON", "ONLY", "OR", "ORDER", "OTHERS", "OUTER", "OVER", 
			"OVERFLOW", "OVERLAY", "PAD", "PARTITION", "PERCENT", "PLACING", "POSITION", 
			"POWER", "PRECEDING", "QUARTER", "RANGE", "RESPECT", "RIGHT", "ROLLUP", 
			"ROW", "ROWS", "SEARCH", "SECOND", "SELECT", "SET", "SIZE", "SOME", "SUBSTRING", 
			"SUM", "THEN", "TIES", "TIME", "TIMESTAMP", "TIMEZONE_HOUR", "TIMEZONE_MINUTE", 
			"TO", "TRAILING", "TREAT", "TRIM", "TRUE", "TRUNC", "TRUNCATE", "TYPE", 
			"UNBOUNDED", "UNION", "UPDATE", "USING", "VALUE", "VALUES", "VERSION", 
			"VERSIONED", "WEEK", "WHEN", "WHERE", "WITH", "WITHIN", "WITHOUT", "YEAR", 
			"CHARACTER", "STRINGLITERAL", "JAVASTRINGLITERAL", "INTEGER_LITERAL", 
			"FLOAT_LITERAL", "HEXLITERAL", "BINARY_LITERAL", "IDENTIFICATION_VARIABLE", 
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
	public String getGrammarFileName() { return "Hql.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public HqlParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StartContext extends ParserRuleContext {
		public Ql_statementContext ql_statement() {
			return getRuleContext(Ql_statementContext.class,0);
		}
		public TerminalNode EOF() { return getToken(HqlParser.EOF, 0); }
		public StartContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_start; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterStart(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitStart(this);
		}
	}

	public final StartContext start() throws RecognitionException {
		StartContext _localctx = new StartContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_start);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(234);
			ql_statement();
			setState(235);
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
		public SelectStatementContext selectStatement() {
			return getRuleContext(SelectStatementContext.class,0);
		}
		public UpdateStatementContext updateStatement() {
			return getRuleContext(UpdateStatementContext.class,0);
		}
		public DeleteStatementContext deleteStatement() {
			return getRuleContext(DeleteStatementContext.class,0);
		}
		public InsertStatementContext insertStatement() {
			return getRuleContext(InsertStatementContext.class,0);
		}
		public Ql_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ql_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterQl_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitQl_statement(this);
		}
	}

	public final Ql_statementContext ql_statement() throws RecognitionException {
		Ql_statementContext _localctx = new Ql_statementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_ql_statement);
		try {
			setState(241);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__1:
			case FROM:
			case SELECT:
			case WITH:
				enterOuterAlt(_localctx, 1);
				{
				setState(237);
				selectStatement();
				}
				break;
			case UPDATE:
				enterOuterAlt(_localctx, 2);
				{
				setState(238);
				updateStatement();
				}
				break;
			case DELETE:
				enterOuterAlt(_localctx, 3);
				{
				setState(239);
				deleteStatement();
				}
				break;
			case INSERT:
				enterOuterAlt(_localctx, 4);
				{
				setState(240);
				insertStatement();
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
	public static class SelectStatementContext extends ParserRuleContext {
		public QueryExpressionContext queryExpression() {
			return getRuleContext(QueryExpressionContext.class,0);
		}
		public SelectStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_selectStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSelectStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSelectStatement(this);
		}
	}

	public final SelectStatementContext selectStatement() throws RecognitionException {
		SelectStatementContext _localctx = new SelectStatementContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_selectStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(243);
			queryExpression();
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
	public static class QueryExpressionContext extends ParserRuleContext {
		public List<OrderedQueryContext> orderedQuery() {
			return getRuleContexts(OrderedQueryContext.class);
		}
		public OrderedQueryContext orderedQuery(int i) {
			return getRuleContext(OrderedQueryContext.class,i);
		}
		public WithClauseContext withClause() {
			return getRuleContext(WithClauseContext.class,0);
		}
		public List<SetOperatorContext> setOperator() {
			return getRuleContexts(SetOperatorContext.class);
		}
		public SetOperatorContext setOperator(int i) {
			return getRuleContext(SetOperatorContext.class,i);
		}
		public QueryExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_queryExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterQueryExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitQueryExpression(this);
		}
	}

	public final QueryExpressionContext queryExpression() throws RecognitionException {
		QueryExpressionContext _localctx = new QueryExpressionContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_queryExpression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(246);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WITH) {
				{
				setState(245);
				withClause();
				}
			}

			setState(248);
			orderedQuery();
			setState(254);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==EXCEPT || _la==INTERSECT || _la==UNION) {
				{
				{
				setState(249);
				setOperator();
				setState(250);
				orderedQuery();
				}
				}
				setState(256);
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
	public static class WithClauseContext extends ParserRuleContext {
		public TerminalNode WITH() { return getToken(HqlParser.WITH, 0); }
		public List<CteContext> cte() {
			return getRuleContexts(CteContext.class);
		}
		public CteContext cte(int i) {
			return getRuleContext(CteContext.class,i);
		}
		public WithClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_withClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterWithClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitWithClause(this);
		}
	}

	public final WithClauseContext withClause() throws RecognitionException {
		WithClauseContext _localctx = new WithClauseContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_withClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(257);
			match(WITH);
			setState(258);
			cte();
			setState(263);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(259);
				match(T__0);
				setState(260);
				cte();
				}
				}
				setState(265);
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
	public static class CteContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode AS() { return getToken(HqlParser.AS, 0); }
		public QueryExpressionContext queryExpression() {
			return getRuleContext(QueryExpressionContext.class,0);
		}
		public TerminalNode MATERIALIZED() { return getToken(HqlParser.MATERIALIZED, 0); }
		public SearchClauseContext searchClause() {
			return getRuleContext(SearchClauseContext.class,0);
		}
		public CycleClauseContext cycleClause() {
			return getRuleContext(CycleClauseContext.class,0);
		}
		public TerminalNode NOT() { return getToken(HqlParser.NOT, 0); }
		public CteContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_cte; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCte(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCte(this);
		}
	}

	public final CteContext cte() throws RecognitionException {
		CteContext _localctx = new CteContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_cte);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(266);
			identifier();
			setState(267);
			match(AS);
			setState(272);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==MATERIALIZED || _la==NOT) {
				{
				setState(269);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(268);
					match(NOT);
					}
				}

				setState(271);
				match(MATERIALIZED);
				}
			}

			setState(274);
			match(T__1);
			setState(275);
			queryExpression();
			setState(276);
			match(T__2);
			setState(278);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SEARCH) {
				{
				setState(277);
				searchClause();
				}
			}

			setState(281);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==CYCLE) {
				{
				setState(280);
				cycleClause();
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
	public static class SearchClauseContext extends ParserRuleContext {
		public TerminalNode SEARCH() { return getToken(HqlParser.SEARCH, 0); }
		public TerminalNode FIRST() { return getToken(HqlParser.FIRST, 0); }
		public TerminalNode BY() { return getToken(HqlParser.BY, 0); }
		public SearchSpecificationsContext searchSpecifications() {
			return getRuleContext(SearchSpecificationsContext.class,0);
		}
		public TerminalNode SET() { return getToken(HqlParser.SET, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode BREADTH() { return getToken(HqlParser.BREADTH, 0); }
		public TerminalNode DEPTH() { return getToken(HqlParser.DEPTH, 0); }
		public SearchClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_searchClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSearchClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSearchClause(this);
		}
	}

	public final SearchClauseContext searchClause() throws RecognitionException {
		SearchClauseContext _localctx = new SearchClauseContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_searchClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(283);
			match(SEARCH);
			setState(284);
			_la = _input.LA(1);
			if ( !(_la==BREADTH || _la==DEPTH) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(285);
			match(FIRST);
			setState(286);
			match(BY);
			setState(287);
			searchSpecifications();
			setState(288);
			match(SET);
			setState(289);
			identifier();
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
	public static class SearchSpecificationsContext extends ParserRuleContext {
		public List<SearchSpecificationContext> searchSpecification() {
			return getRuleContexts(SearchSpecificationContext.class);
		}
		public SearchSpecificationContext searchSpecification(int i) {
			return getRuleContext(SearchSpecificationContext.class,i);
		}
		public SearchSpecificationsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_searchSpecifications; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSearchSpecifications(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSearchSpecifications(this);
		}
	}

	public final SearchSpecificationsContext searchSpecifications() throws RecognitionException {
		SearchSpecificationsContext _localctx = new SearchSpecificationsContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_searchSpecifications);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(291);
			searchSpecification();
			setState(296);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(292);
				match(T__0);
				setState(293);
				searchSpecification();
				}
				}
				setState(298);
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
	public static class SearchSpecificationContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public SortDirectionContext sortDirection() {
			return getRuleContext(SortDirectionContext.class,0);
		}
		public NullsPrecedenceContext nullsPrecedence() {
			return getRuleContext(NullsPrecedenceContext.class,0);
		}
		public SearchSpecificationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_searchSpecification; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSearchSpecification(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSearchSpecification(this);
		}
	}

	public final SearchSpecificationContext searchSpecification() throws RecognitionException {
		SearchSpecificationContext _localctx = new SearchSpecificationContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_searchSpecification);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(299);
			identifier();
			setState(301);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASC || _la==DESC) {
				{
				setState(300);
				sortDirection();
				}
			}

			setState(304);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NULLS) {
				{
				setState(303);
				nullsPrecedence();
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
	public static class CycleClauseContext extends ParserRuleContext {
		public TerminalNode CYCLE() { return getToken(HqlParser.CYCLE, 0); }
		public CteAttributesContext cteAttributes() {
			return getRuleContext(CteAttributesContext.class,0);
		}
		public TerminalNode SET() { return getToken(HqlParser.SET, 0); }
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public TerminalNode TO() { return getToken(HqlParser.TO, 0); }
		public List<LiteralContext> literal() {
			return getRuleContexts(LiteralContext.class);
		}
		public LiteralContext literal(int i) {
			return getRuleContext(LiteralContext.class,i);
		}
		public TerminalNode DEFAULT() { return getToken(HqlParser.DEFAULT, 0); }
		public TerminalNode USING() { return getToken(HqlParser.USING, 0); }
		public CycleClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_cycleClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCycleClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCycleClause(this);
		}
	}

	public final CycleClauseContext cycleClause() throws RecognitionException {
		CycleClauseContext _localctx = new CycleClauseContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_cycleClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(306);
			match(CYCLE);
			setState(307);
			cteAttributes();
			setState(308);
			match(SET);
			setState(309);
			identifier();
			setState(315);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==TO) {
				{
				setState(310);
				match(TO);
				setState(311);
				literal();
				setState(312);
				match(DEFAULT);
				setState(313);
				literal();
				}
			}

			setState(319);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==USING) {
				{
				setState(317);
				match(USING);
				setState(318);
				identifier();
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
	public static class CteAttributesContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public CteAttributesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_cteAttributes; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCteAttributes(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCteAttributes(this);
		}
	}

	public final CteAttributesContext cteAttributes() throws RecognitionException {
		CteAttributesContext _localctx = new CteAttributesContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_cteAttributes);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(321);
			identifier();
			setState(326);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(322);
				match(T__0);
				setState(323);
				identifier();
				}
				}
				setState(328);
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
	public static class OrderedQueryContext extends ParserRuleContext {
		public QueryContext query() {
			return getRuleContext(QueryContext.class,0);
		}
		public QueryExpressionContext queryExpression() {
			return getRuleContext(QueryExpressionContext.class,0);
		}
		public QueryOrderContext queryOrder() {
			return getRuleContext(QueryOrderContext.class,0);
		}
		public OrderedQueryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_orderedQuery; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterOrderedQuery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitOrderedQuery(this);
		}
	}

	public final OrderedQueryContext orderedQuery() throws RecognitionException {
		OrderedQueryContext _localctx = new OrderedQueryContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_orderedQuery);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(334);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case FROM:
			case SELECT:
				{
				setState(329);
				query();
				}
				break;
			case T__1:
				{
				setState(330);
				match(T__1);
				setState(331);
				queryExpression();
				setState(332);
				match(T__2);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(337);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ORDER) {
				{
				setState(336);
				queryOrder();
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
	public static class QueryContext extends ParserRuleContext {
		public QueryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_query; }
	 
		public QueryContext() { }
		public void copyFrom(QueryContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SelectQueryContext extends QueryContext {
		public SelectClauseContext selectClause() {
			return getRuleContext(SelectClauseContext.class,0);
		}
		public FromClauseContext fromClause() {
			return getRuleContext(FromClauseContext.class,0);
		}
		public WhereClauseContext whereClause() {
			return getRuleContext(WhereClauseContext.class,0);
		}
		public GroupByClauseContext groupByClause() {
			return getRuleContext(GroupByClauseContext.class,0);
		}
		public HavingClauseContext havingClause() {
			return getRuleContext(HavingClauseContext.class,0);
		}
		public SelectQueryContext(QueryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSelectQuery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSelectQuery(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FromQueryContext extends QueryContext {
		public FromClauseContext fromClause() {
			return getRuleContext(FromClauseContext.class,0);
		}
		public WhereClauseContext whereClause() {
			return getRuleContext(WhereClauseContext.class,0);
		}
		public GroupByClauseContext groupByClause() {
			return getRuleContext(GroupByClauseContext.class,0);
		}
		public SelectClauseContext selectClause() {
			return getRuleContext(SelectClauseContext.class,0);
		}
		public HavingClauseContext havingClause() {
			return getRuleContext(HavingClauseContext.class,0);
		}
		public FromQueryContext(QueryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterFromQuery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitFromQuery(this);
		}
	}

	public final QueryContext query() throws RecognitionException {
		QueryContext _localctx = new QueryContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_query);
		int _la;
		try {
			setState(365);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case SELECT:
				_localctx = new SelectQueryContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(339);
				selectClause();
				setState(341);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FROM) {
					{
					setState(340);
					fromClause();
					}
				}

				setState(344);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==WHERE) {
					{
					setState(343);
					whereClause();
					}
				}

				setState(350);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==GROUP) {
					{
					setState(346);
					groupByClause();
					setState(348);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==HAVING) {
						{
						setState(347);
						havingClause();
						}
					}

					}
				}

				}
				break;
			case FROM:
				_localctx = new FromQueryContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(352);
				fromClause();
				setState(354);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==WHERE) {
					{
					setState(353);
					whereClause();
					}
				}

				setState(360);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==GROUP) {
					{
					setState(356);
					groupByClause();
					setState(358);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==HAVING) {
						{
						setState(357);
						havingClause();
						}
					}

					}
				}

				setState(363);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==SELECT) {
					{
					setState(362);
					selectClause();
					}
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
	public static class QueryOrderContext extends ParserRuleContext {
		public OrderByClauseContext orderByClause() {
			return getRuleContext(OrderByClauseContext.class,0);
		}
		public LimitClauseContext limitClause() {
			return getRuleContext(LimitClauseContext.class,0);
		}
		public OffsetClauseContext offsetClause() {
			return getRuleContext(OffsetClauseContext.class,0);
		}
		public FetchClauseContext fetchClause() {
			return getRuleContext(FetchClauseContext.class,0);
		}
		public QueryOrderContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_queryOrder; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterQueryOrder(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitQueryOrder(this);
		}
	}

	public final QueryOrderContext queryOrder() throws RecognitionException {
		QueryOrderContext _localctx = new QueryOrderContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_queryOrder);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(367);
			orderByClause();
			setState(369);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LIMIT) {
				{
				setState(368);
				limitClause();
				}
			}

			setState(372);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OFFSET) {
				{
				setState(371);
				offsetClause();
				}
			}

			setState(375);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==FETCH) {
				{
				setState(374);
				fetchClause();
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
	public static class FromClauseContext extends ParserRuleContext {
		public TerminalNode FROM() { return getToken(HqlParser.FROM, 0); }
		public List<EntityWithJoinsContext> entityWithJoins() {
			return getRuleContexts(EntityWithJoinsContext.class);
		}
		public EntityWithJoinsContext entityWithJoins(int i) {
			return getRuleContext(EntityWithJoinsContext.class,i);
		}
		public FromClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fromClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterFromClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitFromClause(this);
		}
	}

	public final FromClauseContext fromClause() throws RecognitionException {
		FromClauseContext _localctx = new FromClauseContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_fromClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(377);
			match(FROM);
			setState(378);
			entityWithJoins();
			setState(383);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(379);
				match(T__0);
				setState(380);
				entityWithJoins();
				}
				}
				setState(385);
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
	public static class EntityWithJoinsContext extends ParserRuleContext {
		public FromRootContext fromRoot() {
			return getRuleContext(FromRootContext.class,0);
		}
		public List<JoinSpecifierContext> joinSpecifier() {
			return getRuleContexts(JoinSpecifierContext.class);
		}
		public JoinSpecifierContext joinSpecifier(int i) {
			return getRuleContext(JoinSpecifierContext.class,i);
		}
		public EntityWithJoinsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_entityWithJoins; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterEntityWithJoins(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitEntityWithJoins(this);
		}
	}

	public final EntityWithJoinsContext entityWithJoins() throws RecognitionException {
		EntityWithJoinsContext _localctx = new EntityWithJoinsContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_entityWithJoins);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(386);
			fromRoot();
			setState(390);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,29,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(387);
					joinSpecifier();
					}
					} 
				}
				setState(392);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,29,_ctx);
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
	public static class JoinSpecifierContext extends ParserRuleContext {
		public JoinContext join() {
			return getRuleContext(JoinContext.class,0);
		}
		public CrossJoinContext crossJoin() {
			return getRuleContext(CrossJoinContext.class,0);
		}
		public JpaCollectionJoinContext jpaCollectionJoin() {
			return getRuleContext(JpaCollectionJoinContext.class,0);
		}
		public JoinSpecifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_joinSpecifier; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterJoinSpecifier(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitJoinSpecifier(this);
		}
	}

	public final JoinSpecifierContext joinSpecifier() throws RecognitionException {
		JoinSpecifierContext _localctx = new JoinSpecifierContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_joinSpecifier);
		try {
			setState(396);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,30,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(393);
				join();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(394);
				crossJoin();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(395);
				jpaCollectionJoin();
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
	public static class FromRootContext extends ParserRuleContext {
		public EntityNameContext entityName() {
			return getRuleContext(EntityNameContext.class,0);
		}
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public TerminalNode LATERAL() { return getToken(HqlParser.LATERAL, 0); }
		public FromRootContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fromRoot; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterFromRoot(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitFromRoot(this);
		}
	}

	public final FromRootContext fromRoot() throws RecognitionException {
		FromRootContext _localctx = new FromRootContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_fromRoot);
		int _la;
		try {
			setState(411);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ALL:
			case AND:
			case ANY:
			case AS:
			case ASC:
			case AVG:
			case BETWEEN:
			case BOTH:
			case BREADTH:
			case BY:
			case CASE:
			case CAST:
			case COLLATE:
			case COUNT:
			case CROSS:
			case CUBE:
			case CURRENT:
			case CURRENT_DATE:
			case CURRENT_INSTANT:
			case CURRENT_TIME:
			case CURRENT_TIMESTAMP:
			case CYCLE:
			case DATE:
			case DATETIME:
			case DAY:
			case DEFAULT:
			case DELETE:
			case DEPTH:
			case DESC:
			case DISTINCT:
			case ELEMENT:
			case ELEMENTS:
			case ELSE:
			case EMPTY:
			case END:
			case ENTRY:
			case EPOCH:
			case ERROR:
			case ESCAPE:
			case EVERY:
			case EXCEPT:
			case EXCLUDE:
			case EXISTS:
			case EXTRACT:
			case FETCH:
			case FILTER:
			case FIRST:
			case FLOOR:
			case FOLLOWING:
			case FOR:
			case FORMAT:
			case FROM:
			case FULL:
			case FUNCTION:
			case GROUP:
			case GROUPS:
			case HAVING:
			case HOUR:
			case ID:
			case IGNORE:
			case ILIKE:
			case IN:
			case INDEX:
			case INDICES:
			case INNER:
			case INSERT:
			case INSTANT:
			case INTERSECT:
			case INTO:
			case IS:
			case JOIN:
			case KEY:
			case LAST:
			case LEADING:
			case LEFT:
			case LIKE:
			case LIMIT:
			case LIST:
			case LISTAGG:
			case LOCAL:
			case LOCAL_DATE:
			case LOCAL_DATETIME:
			case LOCAL_TIME:
			case MAP:
			case MATERIALIZED:
			case MAX:
			case MAXELEMENT:
			case MAXINDEX:
			case MEMBER:
			case MICROSECOND:
			case MILLISECOND:
			case MIN:
			case MINELEMENT:
			case MININDEX:
			case MINUTE:
			case MONTH:
			case NANOSECOND:
			case NATURALID:
			case NEW:
			case NEXT:
			case NO:
			case NOT:
			case NULLS:
			case OBJECT:
			case OF:
			case OFFSET:
			case OFFSET_DATETIME:
			case ON:
			case ONLY:
			case OR:
			case ORDER:
			case OTHERS:
			case OUTER:
			case OVER:
			case OVERFLOW:
			case OVERLAY:
			case PAD:
			case PARTITION:
			case PERCENT:
			case PLACING:
			case POSITION:
			case POWER:
			case PRECEDING:
			case QUARTER:
			case RANGE:
			case RESPECT:
			case RIGHT:
			case ROLLUP:
			case ROW:
			case ROWS:
			case SEARCH:
			case SECOND:
			case SELECT:
			case SET:
			case SIZE:
			case SOME:
			case SUBSTRING:
			case SUM:
			case THEN:
			case TIES:
			case TIME:
			case TIMESTAMP:
			case TIMEZONE_HOUR:
			case TIMEZONE_MINUTE:
			case TO:
			case TRAILING:
			case TREAT:
			case TRIM:
			case TRUNC:
			case TRUNCATE:
			case TYPE:
			case UNBOUNDED:
			case UNION:
			case UPDATE:
			case USING:
			case VALUE:
			case VALUES:
			case VERSION:
			case VERSIONED:
			case WEEK:
			case WHEN:
			case WHERE:
			case WITH:
			case WITHIN:
			case WITHOUT:
			case YEAR:
			case IDENTIFICATION_VARIABLE:
			case SPEL:
				enterOuterAlt(_localctx, 1);
				{
				setState(398);
				entityName();
				setState(400);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,31,_ctx) ) {
				case 1:
					{
					setState(399);
					variable();
					}
					break;
				}
				}
				break;
			case T__1:
			case LATERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(403);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==LATERAL) {
					{
					setState(402);
					match(LATERAL);
					}
				}

				setState(405);
				match(T__1);
				setState(406);
				subquery();
				setState(407);
				match(T__2);
				setState(409);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,33,_ctx) ) {
				case 1:
					{
					setState(408);
					variable();
					}
					break;
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
	public static class JoinContext extends ParserRuleContext {
		public JoinTypeContext joinType() {
			return getRuleContext(JoinTypeContext.class,0);
		}
		public TerminalNode JOIN() { return getToken(HqlParser.JOIN, 0); }
		public JoinTargetContext joinTarget() {
			return getRuleContext(JoinTargetContext.class,0);
		}
		public TerminalNode FETCH() { return getToken(HqlParser.FETCH, 0); }
		public JoinRestrictionContext joinRestriction() {
			return getRuleContext(JoinRestrictionContext.class,0);
		}
		public JoinContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_join; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterJoin(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitJoin(this);
		}
	}

	public final JoinContext join() throws RecognitionException {
		JoinContext _localctx = new JoinContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_join);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(413);
			joinType();
			setState(414);
			match(JOIN);
			setState(416);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,35,_ctx) ) {
			case 1:
				{
				setState(415);
				match(FETCH);
				}
				break;
			}
			setState(418);
			joinTarget();
			setState(420);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ON || _la==WITH) {
				{
				setState(419);
				joinRestriction();
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
	public static class JoinTargetContext extends ParserRuleContext {
		public JoinTargetContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_joinTarget; }
	 
		public JoinTargetContext() { }
		public void copyFrom(JoinTargetContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class JoinPathContext extends JoinTargetContext {
		public PathContext path() {
			return getRuleContext(PathContext.class,0);
		}
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public JoinPathContext(JoinTargetContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterJoinPath(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitJoinPath(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class JoinSubqueryContext extends JoinTargetContext {
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public TerminalNode LATERAL() { return getToken(HqlParser.LATERAL, 0); }
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public JoinSubqueryContext(JoinTargetContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterJoinSubquery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitJoinSubquery(this);
		}
	}

	public final JoinTargetContext joinTarget() throws RecognitionException {
		JoinTargetContext _localctx = new JoinTargetContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_joinTarget);
		int _la;
		try {
			setState(435);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ALL:
			case AND:
			case ANY:
			case AS:
			case ASC:
			case AVG:
			case BETWEEN:
			case BOTH:
			case BREADTH:
			case BY:
			case CASE:
			case CAST:
			case COLLATE:
			case COUNT:
			case CROSS:
			case CUBE:
			case CURRENT:
			case CURRENT_DATE:
			case CURRENT_INSTANT:
			case CURRENT_TIME:
			case CURRENT_TIMESTAMP:
			case CYCLE:
			case DATE:
			case DATETIME:
			case DAY:
			case DEFAULT:
			case DELETE:
			case DEPTH:
			case DESC:
			case DISTINCT:
			case ELEMENT:
			case ELEMENTS:
			case ELSE:
			case EMPTY:
			case END:
			case ENTRY:
			case EPOCH:
			case ERROR:
			case ESCAPE:
			case EVERY:
			case EXCEPT:
			case EXCLUDE:
			case EXISTS:
			case EXTRACT:
			case FETCH:
			case FILTER:
			case FIRST:
			case FLOOR:
			case FOLLOWING:
			case FOR:
			case FORMAT:
			case FROM:
			case FULL:
			case FUNCTION:
			case GROUP:
			case GROUPS:
			case HAVING:
			case HOUR:
			case ID:
			case IGNORE:
			case ILIKE:
			case IN:
			case INDEX:
			case INDICES:
			case INNER:
			case INSERT:
			case INSTANT:
			case INTERSECT:
			case INTO:
			case IS:
			case JOIN:
			case KEY:
			case LAST:
			case LEADING:
			case LEFT:
			case LIKE:
			case LIMIT:
			case LIST:
			case LISTAGG:
			case LOCAL:
			case LOCAL_DATE:
			case LOCAL_DATETIME:
			case LOCAL_TIME:
			case MAP:
			case MATERIALIZED:
			case MAX:
			case MAXELEMENT:
			case MAXINDEX:
			case MEMBER:
			case MICROSECOND:
			case MILLISECOND:
			case MIN:
			case MINELEMENT:
			case MININDEX:
			case MINUTE:
			case MONTH:
			case NANOSECOND:
			case NATURALID:
			case NEW:
			case NEXT:
			case NO:
			case NOT:
			case NULLS:
			case OBJECT:
			case OF:
			case OFFSET:
			case OFFSET_DATETIME:
			case ON:
			case ONLY:
			case OR:
			case ORDER:
			case OTHERS:
			case OUTER:
			case OVER:
			case OVERFLOW:
			case OVERLAY:
			case PAD:
			case PARTITION:
			case PERCENT:
			case PLACING:
			case POSITION:
			case POWER:
			case PRECEDING:
			case QUARTER:
			case RANGE:
			case RESPECT:
			case RIGHT:
			case ROLLUP:
			case ROW:
			case ROWS:
			case SEARCH:
			case SECOND:
			case SELECT:
			case SET:
			case SIZE:
			case SOME:
			case SUBSTRING:
			case SUM:
			case THEN:
			case TIES:
			case TIME:
			case TIMESTAMP:
			case TIMEZONE_HOUR:
			case TIMEZONE_MINUTE:
			case TO:
			case TRAILING:
			case TREAT:
			case TRIM:
			case TRUNC:
			case TRUNCATE:
			case TYPE:
			case UNBOUNDED:
			case UNION:
			case UPDATE:
			case USING:
			case VALUE:
			case VALUES:
			case VERSION:
			case VERSIONED:
			case WEEK:
			case WHEN:
			case WHERE:
			case WITH:
			case WITHIN:
			case WITHOUT:
			case YEAR:
			case IDENTIFICATION_VARIABLE:
				_localctx = new JoinPathContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(422);
				path();
				setState(424);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,37,_ctx) ) {
				case 1:
					{
					setState(423);
					variable();
					}
					break;
				}
				}
				break;
			case T__1:
			case LATERAL:
				_localctx = new JoinSubqueryContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(427);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==LATERAL) {
					{
					setState(426);
					match(LATERAL);
					}
				}

				setState(429);
				match(T__1);
				setState(430);
				subquery();
				setState(431);
				match(T__2);
				setState(433);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,39,_ctx) ) {
				case 1:
					{
					setState(432);
					variable();
					}
					break;
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
	public static class UpdateStatementContext extends ParserRuleContext {
		public TerminalNode UPDATE() { return getToken(HqlParser.UPDATE, 0); }
		public TargetEntityContext targetEntity() {
			return getRuleContext(TargetEntityContext.class,0);
		}
		public SetClauseContext setClause() {
			return getRuleContext(SetClauseContext.class,0);
		}
		public TerminalNode VERSIONED() { return getToken(HqlParser.VERSIONED, 0); }
		public WhereClauseContext whereClause() {
			return getRuleContext(WhereClauseContext.class,0);
		}
		public UpdateStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_updateStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterUpdateStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitUpdateStatement(this);
		}
	}

	public final UpdateStatementContext updateStatement() throws RecognitionException {
		UpdateStatementContext _localctx = new UpdateStatementContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_updateStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(437);
			match(UPDATE);
			setState(439);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,41,_ctx) ) {
			case 1:
				{
				setState(438);
				match(VERSIONED);
				}
				break;
			}
			setState(441);
			targetEntity();
			setState(442);
			setClause();
			setState(444);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(443);
				whereClause();
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
	public static class TargetEntityContext extends ParserRuleContext {
		public EntityNameContext entityName() {
			return getRuleContext(EntityNameContext.class,0);
		}
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public TargetEntityContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_targetEntity; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterTargetEntity(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitTargetEntity(this);
		}
	}

	public final TargetEntityContext targetEntity() throws RecognitionException {
		TargetEntityContext _localctx = new TargetEntityContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_targetEntity);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(446);
			entityName();
			setState(448);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,43,_ctx) ) {
			case 1:
				{
				setState(447);
				variable();
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
	public static class SetClauseContext extends ParserRuleContext {
		public TerminalNode SET() { return getToken(HqlParser.SET, 0); }
		public List<AssignmentContext> assignment() {
			return getRuleContexts(AssignmentContext.class);
		}
		public AssignmentContext assignment(int i) {
			return getRuleContext(AssignmentContext.class,i);
		}
		public SetClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_setClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSetClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSetClause(this);
		}
	}

	public final SetClauseContext setClause() throws RecognitionException {
		SetClauseContext _localctx = new SetClauseContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_setClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(450);
			match(SET);
			setState(451);
			assignment();
			setState(456);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(452);
				match(T__0);
				setState(453);
				assignment();
				}
				}
				setState(458);
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
	public static class AssignmentContext extends ParserRuleContext {
		public SimplePathContext simplePath() {
			return getRuleContext(SimplePathContext.class,0);
		}
		public ExpressionOrPredicateContext expressionOrPredicate() {
			return getRuleContext(ExpressionOrPredicateContext.class,0);
		}
		public AssignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assignment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterAssignment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitAssignment(this);
		}
	}

	public final AssignmentContext assignment() throws RecognitionException {
		AssignmentContext _localctx = new AssignmentContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_assignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(459);
			simplePath();
			setState(460);
			match(T__3);
			setState(461);
			expressionOrPredicate();
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
	public static class DeleteStatementContext extends ParserRuleContext {
		public TerminalNode DELETE() { return getToken(HqlParser.DELETE, 0); }
		public TargetEntityContext targetEntity() {
			return getRuleContext(TargetEntityContext.class,0);
		}
		public TerminalNode FROM() { return getToken(HqlParser.FROM, 0); }
		public WhereClauseContext whereClause() {
			return getRuleContext(WhereClauseContext.class,0);
		}
		public DeleteStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_deleteStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterDeleteStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitDeleteStatement(this);
		}
	}

	public final DeleteStatementContext deleteStatement() throws RecognitionException {
		DeleteStatementContext _localctx = new DeleteStatementContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_deleteStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(463);
			match(DELETE);
			setState(465);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,45,_ctx) ) {
			case 1:
				{
				setState(464);
				match(FROM);
				}
				break;
			}
			setState(467);
			targetEntity();
			setState(469);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(468);
				whereClause();
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
	public static class InsertStatementContext extends ParserRuleContext {
		public TerminalNode INSERT() { return getToken(HqlParser.INSERT, 0); }
		public TargetEntityContext targetEntity() {
			return getRuleContext(TargetEntityContext.class,0);
		}
		public TargetFieldsContext targetFields() {
			return getRuleContext(TargetFieldsContext.class,0);
		}
		public QueryExpressionContext queryExpression() {
			return getRuleContext(QueryExpressionContext.class,0);
		}
		public ValuesListContext valuesList() {
			return getRuleContext(ValuesListContext.class,0);
		}
		public TerminalNode INTO() { return getToken(HqlParser.INTO, 0); }
		public InsertStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_insertStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterInsertStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitInsertStatement(this);
		}
	}

	public final InsertStatementContext insertStatement() throws RecognitionException {
		InsertStatementContext _localctx = new InsertStatementContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_insertStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(471);
			match(INSERT);
			setState(473);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,47,_ctx) ) {
			case 1:
				{
				setState(472);
				match(INTO);
				}
				break;
			}
			setState(475);
			targetEntity();
			setState(476);
			targetFields();
			setState(479);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__1:
			case FROM:
			case SELECT:
			case WITH:
				{
				setState(477);
				queryExpression();
				}
				break;
			case VALUES:
				{
				setState(478);
				valuesList();
				}
				break;
			default:
				throw new NoViableAltException(this);
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
	public static class TargetFieldsContext extends ParserRuleContext {
		public List<SimplePathContext> simplePath() {
			return getRuleContexts(SimplePathContext.class);
		}
		public SimplePathContext simplePath(int i) {
			return getRuleContext(SimplePathContext.class,i);
		}
		public TargetFieldsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_targetFields; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterTargetFields(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitTargetFields(this);
		}
	}

	public final TargetFieldsContext targetFields() throws RecognitionException {
		TargetFieldsContext _localctx = new TargetFieldsContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_targetFields);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(481);
			match(T__1);
			setState(482);
			simplePath();
			setState(487);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(483);
				match(T__0);
				setState(484);
				simplePath();
				}
				}
				setState(489);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(490);
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
	public static class ValuesListContext extends ParserRuleContext {
		public TerminalNode VALUES() { return getToken(HqlParser.VALUES, 0); }
		public List<ValuesContext> values() {
			return getRuleContexts(ValuesContext.class);
		}
		public ValuesContext values(int i) {
			return getRuleContext(ValuesContext.class,i);
		}
		public ValuesListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_valuesList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterValuesList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitValuesList(this);
		}
	}

	public final ValuesListContext valuesList() throws RecognitionException {
		ValuesListContext _localctx = new ValuesListContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_valuesList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(492);
			match(VALUES);
			setState(493);
			values();
			setState(498);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(494);
				match(T__0);
				setState(495);
				values();
				}
				}
				setState(500);
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
	public static class ValuesContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public ValuesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_values; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterValues(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitValues(this);
		}
	}

	public final ValuesContext values() throws RecognitionException {
		ValuesContext _localctx = new ValuesContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_values);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(501);
			match(T__1);
			setState(502);
			expression(0);
			setState(507);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(503);
				match(T__0);
				setState(504);
				expression(0);
				}
				}
				setState(509);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(510);
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
	public static class InstantiationContext extends ParserRuleContext {
		public TerminalNode NEW() { return getToken(HqlParser.NEW, 0); }
		public InstantiationTargetContext instantiationTarget() {
			return getRuleContext(InstantiationTargetContext.class,0);
		}
		public InstantiationArgumentsContext instantiationArguments() {
			return getRuleContext(InstantiationArgumentsContext.class,0);
		}
		public InstantiationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_instantiation; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterInstantiation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitInstantiation(this);
		}
	}

	public final InstantiationContext instantiation() throws RecognitionException {
		InstantiationContext _localctx = new InstantiationContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_instantiation);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(512);
			match(NEW);
			setState(513);
			instantiationTarget();
			setState(514);
			match(T__1);
			setState(515);
			instantiationArguments();
			setState(516);
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
	public static class AliasContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode AS() { return getToken(HqlParser.AS, 0); }
		public AliasContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_alias; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterAlias(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitAlias(this);
		}
	}

	public final AliasContext alias() throws RecognitionException {
		AliasContext _localctx = new AliasContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_alias);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(519);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,52,_ctx) ) {
			case 1:
				{
				setState(518);
				match(AS);
				}
				break;
			}
			setState(521);
			identifier();
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
	public static class GroupedItemContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode INTEGER_LITERAL() { return getToken(HqlParser.INTEGER_LITERAL, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public GroupedItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_groupedItem; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterGroupedItem(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitGroupedItem(this);
		}
	}

	public final GroupedItemContext groupedItem() throws RecognitionException {
		GroupedItemContext _localctx = new GroupedItemContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_groupedItem);
		try {
			setState(526);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,53,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(523);
				identifier();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(524);
				match(INTEGER_LITERAL);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(525);
				expression(0);
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
	public static class SortedItemContext extends ParserRuleContext {
		public SortExpressionContext sortExpression() {
			return getRuleContext(SortExpressionContext.class,0);
		}
		public SortDirectionContext sortDirection() {
			return getRuleContext(SortDirectionContext.class,0);
		}
		public NullsPrecedenceContext nullsPrecedence() {
			return getRuleContext(NullsPrecedenceContext.class,0);
		}
		public SortedItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sortedItem; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSortedItem(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSortedItem(this);
		}
	}

	public final SortedItemContext sortedItem() throws RecognitionException {
		SortedItemContext _localctx = new SortedItemContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_sortedItem);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(528);
			sortExpression();
			setState(530);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASC || _la==DESC) {
				{
				setState(529);
				sortDirection();
				}
			}

			setState(533);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NULLS) {
				{
				setState(532);
				nullsPrecedence();
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
	public static class SortExpressionContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode INTEGER_LITERAL() { return getToken(HqlParser.INTEGER_LITERAL, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public SortExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sortExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSortExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSortExpression(this);
		}
	}

	public final SortExpressionContext sortExpression() throws RecognitionException {
		SortExpressionContext _localctx = new SortExpressionContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_sortExpression);
		try {
			setState(538);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,56,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(535);
				identifier();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(536);
				match(INTEGER_LITERAL);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(537);
				expression(0);
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
	public static class SortDirectionContext extends ParserRuleContext {
		public TerminalNode ASC() { return getToken(HqlParser.ASC, 0); }
		public TerminalNode DESC() { return getToken(HqlParser.DESC, 0); }
		public SortDirectionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sortDirection; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSortDirection(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSortDirection(this);
		}
	}

	public final SortDirectionContext sortDirection() throws RecognitionException {
		SortDirectionContext _localctx = new SortDirectionContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_sortDirection);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(540);
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
	public static class NullsPrecedenceContext extends ParserRuleContext {
		public TerminalNode NULLS() { return getToken(HqlParser.NULLS, 0); }
		public TerminalNode FIRST() { return getToken(HqlParser.FIRST, 0); }
		public TerminalNode LAST() { return getToken(HqlParser.LAST, 0); }
		public NullsPrecedenceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nullsPrecedence; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterNullsPrecedence(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitNullsPrecedence(this);
		}
	}

	public final NullsPrecedenceContext nullsPrecedence() throws RecognitionException {
		NullsPrecedenceContext _localctx = new NullsPrecedenceContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_nullsPrecedence);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(542);
			match(NULLS);
			setState(543);
			_la = _input.LA(1);
			if ( !(_la==FIRST || _la==LAST) ) {
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
	public static class LimitClauseContext extends ParserRuleContext {
		public TerminalNode LIMIT() { return getToken(HqlParser.LIMIT, 0); }
		public ParameterOrIntegerLiteralContext parameterOrIntegerLiteral() {
			return getRuleContext(ParameterOrIntegerLiteralContext.class,0);
		}
		public LimitClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_limitClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterLimitClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitLimitClause(this);
		}
	}

	public final LimitClauseContext limitClause() throws RecognitionException {
		LimitClauseContext _localctx = new LimitClauseContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_limitClause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(545);
			match(LIMIT);
			setState(546);
			parameterOrIntegerLiteral();
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
	public static class OffsetClauseContext extends ParserRuleContext {
		public TerminalNode OFFSET() { return getToken(HqlParser.OFFSET, 0); }
		public ParameterOrIntegerLiteralContext parameterOrIntegerLiteral() {
			return getRuleContext(ParameterOrIntegerLiteralContext.class,0);
		}
		public TerminalNode ROW() { return getToken(HqlParser.ROW, 0); }
		public TerminalNode ROWS() { return getToken(HqlParser.ROWS, 0); }
		public OffsetClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_offsetClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterOffsetClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitOffsetClause(this);
		}
	}

	public final OffsetClauseContext offsetClause() throws RecognitionException {
		OffsetClauseContext _localctx = new OffsetClauseContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_offsetClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(548);
			match(OFFSET);
			setState(549);
			parameterOrIntegerLiteral();
			setState(551);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ROW || _la==ROWS) {
				{
				setState(550);
				_la = _input.LA(1);
				if ( !(_la==ROW || _la==ROWS) ) {
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
	public static class FetchClauseContext extends ParserRuleContext {
		public TerminalNode FETCH() { return getToken(HqlParser.FETCH, 0); }
		public TerminalNode FIRST() { return getToken(HqlParser.FIRST, 0); }
		public TerminalNode NEXT() { return getToken(HqlParser.NEXT, 0); }
		public TerminalNode ROW() { return getToken(HqlParser.ROW, 0); }
		public TerminalNode ROWS() { return getToken(HqlParser.ROWS, 0); }
		public ParameterOrIntegerLiteralContext parameterOrIntegerLiteral() {
			return getRuleContext(ParameterOrIntegerLiteralContext.class,0);
		}
		public ParameterOrNumberLiteralContext parameterOrNumberLiteral() {
			return getRuleContext(ParameterOrNumberLiteralContext.class,0);
		}
		public TerminalNode ONLY() { return getToken(HqlParser.ONLY, 0); }
		public TerminalNode WITH() { return getToken(HqlParser.WITH, 0); }
		public TerminalNode TIES() { return getToken(HqlParser.TIES, 0); }
		public FetchClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fetchClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterFetchClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitFetchClause(this);
		}
	}

	public final FetchClauseContext fetchClause() throws RecognitionException {
		FetchClauseContext _localctx = new FetchClauseContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_fetchClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(553);
			match(FETCH);
			setState(554);
			_la = _input.LA(1);
			if ( !(_la==FIRST || _la==NEXT) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(559);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,58,_ctx) ) {
			case 1:
				{
				setState(555);
				parameterOrIntegerLiteral();
				}
				break;
			case 2:
				{
				setState(556);
				parameterOrNumberLiteral();
				setState(557);
				match(T__4);
				}
				break;
			}
			setState(561);
			_la = _input.LA(1);
			if ( !(_la==ROW || _la==ROWS) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(565);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ONLY:
				{
				setState(562);
				match(ONLY);
				}
				break;
			case WITH:
				{
				setState(563);
				match(WITH);
				setState(564);
				match(TIES);
				}
				break;
			default:
				throw new NoViableAltException(this);
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
		public QueryExpressionContext queryExpression() {
			return getRuleContext(QueryExpressionContext.class,0);
		}
		public SubqueryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_subquery; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSubquery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSubquery(this);
		}
	}

	public final SubqueryContext subquery() throws RecognitionException {
		SubqueryContext _localctx = new SubqueryContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_subquery);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(567);
			queryExpression();
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
	public static class SelectClauseContext extends ParserRuleContext {
		public TerminalNode SELECT() { return getToken(HqlParser.SELECT, 0); }
		public SelectionListContext selectionList() {
			return getRuleContext(SelectionListContext.class,0);
		}
		public TerminalNode DISTINCT() { return getToken(HqlParser.DISTINCT, 0); }
		public SelectClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_selectClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSelectClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSelectClause(this);
		}
	}

	public final SelectClauseContext selectClause() throws RecognitionException {
		SelectClauseContext _localctx = new SelectClauseContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_selectClause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(569);
			match(SELECT);
			setState(571);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,60,_ctx) ) {
			case 1:
				{
				setState(570);
				match(DISTINCT);
				}
				break;
			}
			setState(573);
			selectionList();
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
	public static class SelectionListContext extends ParserRuleContext {
		public List<SelectionContext> selection() {
			return getRuleContexts(SelectionContext.class);
		}
		public SelectionContext selection(int i) {
			return getRuleContext(SelectionContext.class,i);
		}
		public SelectionListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_selectionList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSelectionList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSelectionList(this);
		}
	}

	public final SelectionListContext selectionList() throws RecognitionException {
		SelectionListContext _localctx = new SelectionListContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_selectionList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(575);
			selection();
			setState(580);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(576);
				match(T__0);
				setState(577);
				selection();
				}
				}
				setState(582);
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
	public static class SelectionContext extends ParserRuleContext {
		public SelectExpressionContext selectExpression() {
			return getRuleContext(SelectExpressionContext.class,0);
		}
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public SelectionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_selection; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSelection(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSelection(this);
		}
	}

	public final SelectionContext selection() throws RecognitionException {
		SelectionContext _localctx = new SelectionContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_selection);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(583);
			selectExpression();
			setState(585);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,62,_ctx) ) {
			case 1:
				{
				setState(584);
				variable();
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
	public static class SelectExpressionContext extends ParserRuleContext {
		public InstantiationContext instantiation() {
			return getRuleContext(InstantiationContext.class,0);
		}
		public MapEntrySelectionContext mapEntrySelection() {
			return getRuleContext(MapEntrySelectionContext.class,0);
		}
		public JpaSelectObjectSyntaxContext jpaSelectObjectSyntax() {
			return getRuleContext(JpaSelectObjectSyntaxContext.class,0);
		}
		public ExpressionOrPredicateContext expressionOrPredicate() {
			return getRuleContext(ExpressionOrPredicateContext.class,0);
		}
		public SelectExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_selectExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSelectExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSelectExpression(this);
		}
	}

	public final SelectExpressionContext selectExpression() throws RecognitionException {
		SelectExpressionContext _localctx = new SelectExpressionContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_selectExpression);
		try {
			setState(591);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,63,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(587);
				instantiation();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(588);
				mapEntrySelection();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(589);
				jpaSelectObjectSyntax();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(590);
				expressionOrPredicate();
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
	public static class MapEntrySelectionContext extends ParserRuleContext {
		public TerminalNode ENTRY() { return getToken(HqlParser.ENTRY, 0); }
		public PathContext path() {
			return getRuleContext(PathContext.class,0);
		}
		public MapEntrySelectionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mapEntrySelection; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterMapEntrySelection(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitMapEntrySelection(this);
		}
	}

	public final MapEntrySelectionContext mapEntrySelection() throws RecognitionException {
		MapEntrySelectionContext _localctx = new MapEntrySelectionContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_mapEntrySelection);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(593);
			match(ENTRY);
			setState(594);
			match(T__1);
			setState(595);
			path();
			setState(596);
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
	public static class JpaSelectObjectSyntaxContext extends ParserRuleContext {
		public TerminalNode OBJECT() { return getToken(HqlParser.OBJECT, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public JpaSelectObjectSyntaxContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_jpaSelectObjectSyntax; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterJpaSelectObjectSyntax(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitJpaSelectObjectSyntax(this);
		}
	}

	public final JpaSelectObjectSyntaxContext jpaSelectObjectSyntax() throws RecognitionException {
		JpaSelectObjectSyntaxContext _localctx = new JpaSelectObjectSyntaxContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_jpaSelectObjectSyntax);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(598);
			match(OBJECT);
			setState(599);
			match(T__1);
			setState(600);
			identifier();
			setState(601);
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
	public static class WhereClauseContext extends ParserRuleContext {
		public TerminalNode WHERE() { return getToken(HqlParser.WHERE, 0); }
		public List<PredicateContext> predicate() {
			return getRuleContexts(PredicateContext.class);
		}
		public PredicateContext predicate(int i) {
			return getRuleContext(PredicateContext.class,i);
		}
		public WhereClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_whereClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterWhereClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitWhereClause(this);
		}
	}

	public final WhereClauseContext whereClause() throws RecognitionException {
		WhereClauseContext _localctx = new WhereClauseContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_whereClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(603);
			match(WHERE);
			setState(604);
			predicate(0);
			setState(609);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(605);
				match(T__0);
				setState(606);
				predicate(0);
				}
				}
				setState(611);
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
	public static class JoinTypeContext extends ParserRuleContext {
		public TerminalNode INNER() { return getToken(HqlParser.INNER, 0); }
		public TerminalNode OUTER() { return getToken(HqlParser.OUTER, 0); }
		public TerminalNode LEFT() { return getToken(HqlParser.LEFT, 0); }
		public TerminalNode RIGHT() { return getToken(HqlParser.RIGHT, 0); }
		public TerminalNode FULL() { return getToken(HqlParser.FULL, 0); }
		public TerminalNode CROSS() { return getToken(HqlParser.CROSS, 0); }
		public JoinTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_joinType; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterJoinType(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitJoinType(this);
		}
	}

	public final JoinTypeContext joinType() throws RecognitionException {
		JoinTypeContext _localctx = new JoinTypeContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_joinType);
		int _la;
		try {
			setState(622);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,68,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(613);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==INNER) {
					{
					setState(612);
					match(INNER);
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(616);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FULL || _la==LEFT || _la==RIGHT) {
					{
					setState(615);
					_la = _input.LA(1);
					if ( !(_la==FULL || _la==LEFT || _la==RIGHT) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					}
				}

				setState(619);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==OUTER) {
					{
					setState(618);
					match(OUTER);
					}
				}

				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(621);
				match(CROSS);
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
	public static class CrossJoinContext extends ParserRuleContext {
		public TerminalNode CROSS() { return getToken(HqlParser.CROSS, 0); }
		public TerminalNode JOIN() { return getToken(HqlParser.JOIN, 0); }
		public EntityNameContext entityName() {
			return getRuleContext(EntityNameContext.class,0);
		}
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public CrossJoinContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_crossJoin; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCrossJoin(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCrossJoin(this);
		}
	}

	public final CrossJoinContext crossJoin() throws RecognitionException {
		CrossJoinContext _localctx = new CrossJoinContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_crossJoin);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(624);
			match(CROSS);
			setState(625);
			match(JOIN);
			setState(626);
			entityName();
			setState(628);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,69,_ctx) ) {
			case 1:
				{
				setState(627);
				variable();
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
	public static class JoinRestrictionContext extends ParserRuleContext {
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public TerminalNode ON() { return getToken(HqlParser.ON, 0); }
		public TerminalNode WITH() { return getToken(HqlParser.WITH, 0); }
		public JoinRestrictionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_joinRestriction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterJoinRestriction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitJoinRestriction(this);
		}
	}

	public final JoinRestrictionContext joinRestriction() throws RecognitionException {
		JoinRestrictionContext _localctx = new JoinRestrictionContext(_ctx, getState());
		enterRule(_localctx, 98, RULE_joinRestriction);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(630);
			_la = _input.LA(1);
			if ( !(_la==ON || _la==WITH) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(631);
			predicate(0);
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
	public static class JpaCollectionJoinContext extends ParserRuleContext {
		public TerminalNode IN() { return getToken(HqlParser.IN, 0); }
		public PathContext path() {
			return getRuleContext(PathContext.class,0);
		}
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public JpaCollectionJoinContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_jpaCollectionJoin; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterJpaCollectionJoin(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitJpaCollectionJoin(this);
		}
	}

	public final JpaCollectionJoinContext jpaCollectionJoin() throws RecognitionException {
		JpaCollectionJoinContext _localctx = new JpaCollectionJoinContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_jpaCollectionJoin);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(633);
			match(T__0);
			setState(634);
			match(IN);
			setState(635);
			match(T__1);
			setState(636);
			path();
			setState(637);
			match(T__2);
			setState(639);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,70,_ctx) ) {
			case 1:
				{
				setState(638);
				variable();
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
	public static class GroupByClauseContext extends ParserRuleContext {
		public TerminalNode GROUP() { return getToken(HqlParser.GROUP, 0); }
		public TerminalNode BY() { return getToken(HqlParser.BY, 0); }
		public List<GroupedItemContext> groupedItem() {
			return getRuleContexts(GroupedItemContext.class);
		}
		public GroupedItemContext groupedItem(int i) {
			return getRuleContext(GroupedItemContext.class,i);
		}
		public GroupByClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_groupByClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterGroupByClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitGroupByClause(this);
		}
	}

	public final GroupByClauseContext groupByClause() throws RecognitionException {
		GroupByClauseContext _localctx = new GroupByClauseContext(_ctx, getState());
		enterRule(_localctx, 102, RULE_groupByClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(641);
			match(GROUP);
			setState(642);
			match(BY);
			setState(643);
			groupedItem();
			setState(648);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(644);
				match(T__0);
				setState(645);
				groupedItem();
				}
				}
				setState(650);
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
	public static class OrderByClauseContext extends ParserRuleContext {
		public TerminalNode ORDER() { return getToken(HqlParser.ORDER, 0); }
		public TerminalNode BY() { return getToken(HqlParser.BY, 0); }
		public List<SortedItemContext> sortedItem() {
			return getRuleContexts(SortedItemContext.class);
		}
		public SortedItemContext sortedItem(int i) {
			return getRuleContext(SortedItemContext.class,i);
		}
		public OrderByClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_orderByClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterOrderByClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitOrderByClause(this);
		}
	}

	public final OrderByClauseContext orderByClause() throws RecognitionException {
		OrderByClauseContext _localctx = new OrderByClauseContext(_ctx, getState());
		enterRule(_localctx, 104, RULE_orderByClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(651);
			match(ORDER);
			setState(652);
			match(BY);
			setState(653);
			sortedItem();
			setState(658);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(654);
				match(T__0);
				setState(655);
				sortedItem();
				}
				}
				setState(660);
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
	public static class HavingClauseContext extends ParserRuleContext {
		public TerminalNode HAVING() { return getToken(HqlParser.HAVING, 0); }
		public List<PredicateContext> predicate() {
			return getRuleContexts(PredicateContext.class);
		}
		public PredicateContext predicate(int i) {
			return getRuleContext(PredicateContext.class,i);
		}
		public HavingClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_havingClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterHavingClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitHavingClause(this);
		}
	}

	public final HavingClauseContext havingClause() throws RecognitionException {
		HavingClauseContext _localctx = new HavingClauseContext(_ctx, getState());
		enterRule(_localctx, 106, RULE_havingClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(661);
			match(HAVING);
			setState(662);
			predicate(0);
			setState(667);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(663);
				match(T__0);
				setState(664);
				predicate(0);
				}
				}
				setState(669);
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
	public static class SetOperatorContext extends ParserRuleContext {
		public TerminalNode UNION() { return getToken(HqlParser.UNION, 0); }
		public TerminalNode ALL() { return getToken(HqlParser.ALL, 0); }
		public TerminalNode INTERSECT() { return getToken(HqlParser.INTERSECT, 0); }
		public TerminalNode EXCEPT() { return getToken(HqlParser.EXCEPT, 0); }
		public SetOperatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_setOperator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSetOperator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSetOperator(this);
		}
	}

	public final SetOperatorContext setOperator() throws RecognitionException {
		SetOperatorContext _localctx = new SetOperatorContext(_ctx, getState());
		enterRule(_localctx, 108, RULE_setOperator);
		int _la;
		try {
			setState(682);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case UNION:
				enterOuterAlt(_localctx, 1);
				{
				setState(670);
				match(UNION);
				setState(672);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ALL) {
					{
					setState(671);
					match(ALL);
					}
				}

				}
				break;
			case INTERSECT:
				enterOuterAlt(_localctx, 2);
				{
				setState(674);
				match(INTERSECT);
				setState(676);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ALL) {
					{
					setState(675);
					match(ALL);
					}
				}

				}
				break;
			case EXCEPT:
				enterOuterAlt(_localctx, 3);
				{
				setState(678);
				match(EXCEPT);
				setState(680);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ALL) {
					{
					setState(679);
					match(ALL);
					}
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
	public static class LiteralContext extends ParserRuleContext {
		public TerminalNode NULL() { return getToken(HqlParser.NULL, 0); }
		public BooleanLiteralContext booleanLiteral() {
			return getRuleContext(BooleanLiteralContext.class,0);
		}
		public StringLiteralContext stringLiteral() {
			return getRuleContext(StringLiteralContext.class,0);
		}
		public NumericLiteralContext numericLiteral() {
			return getRuleContext(NumericLiteralContext.class,0);
		}
		public DateTimeLiteralContext dateTimeLiteral() {
			return getRuleContext(DateTimeLiteralContext.class,0);
		}
		public BinaryLiteralContext binaryLiteral() {
			return getRuleContext(BinaryLiteralContext.class,0);
		}
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitLiteral(this);
		}
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 110, RULE_literal);
		try {
			setState(690);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NULL:
				enterOuterAlt(_localctx, 1);
				{
				setState(684);
				match(NULL);
				}
				break;
			case FALSE:
			case TRUE:
				enterOuterAlt(_localctx, 2);
				{
				setState(685);
				booleanLiteral();
				}
				break;
			case CHARACTER:
			case STRINGLITERAL:
			case JAVASTRINGLITERAL:
				enterOuterAlt(_localctx, 3);
				{
				setState(686);
				stringLiteral();
				}
				break;
			case INTEGER_LITERAL:
			case FLOAT_LITERAL:
			case HEXLITERAL:
				enterOuterAlt(_localctx, 4);
				{
				setState(687);
				numericLiteral();
				}
				break;
			case CURRENT:
			case CURRENT_DATE:
			case CURRENT_TIME:
			case CURRENT_TIMESTAMP:
			case INSTANT:
			case LOCAL:
			case LOCAL_DATE:
			case LOCAL_DATETIME:
			case LOCAL_TIME:
			case OFFSET:
			case OFFSET_DATETIME:
				enterOuterAlt(_localctx, 5);
				{
				setState(688);
				dateTimeLiteral();
				}
				break;
			case T__5:
			case BINARY_LITERAL:
				enterOuterAlt(_localctx, 6);
				{
				setState(689);
				binaryLiteral();
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
	public static class BooleanLiteralContext extends ParserRuleContext {
		public TerminalNode TRUE() { return getToken(HqlParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(HqlParser.FALSE, 0); }
		public BooleanLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_booleanLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterBooleanLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitBooleanLiteral(this);
		}
	}

	public final BooleanLiteralContext booleanLiteral() throws RecognitionException {
		BooleanLiteralContext _localctx = new BooleanLiteralContext(_ctx, getState());
		enterRule(_localctx, 112, RULE_booleanLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(692);
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
	public static class StringLiteralContext extends ParserRuleContext {
		public TerminalNode STRINGLITERAL() { return getToken(HqlParser.STRINGLITERAL, 0); }
		public TerminalNode JAVASTRINGLITERAL() { return getToken(HqlParser.JAVASTRINGLITERAL, 0); }
		public TerminalNode CHARACTER() { return getToken(HqlParser.CHARACTER, 0); }
		public StringLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stringLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterStringLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitStringLiteral(this);
		}
	}

	public final StringLiteralContext stringLiteral() throws RecognitionException {
		StringLiteralContext _localctx = new StringLiteralContext(_ctx, getState());
		enterRule(_localctx, 114, RULE_stringLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(694);
			_la = _input.LA(1);
			if ( !(((((_la - 200)) & ~0x3f) == 0 && ((1L << (_la - 200)) & 7L) != 0)) ) {
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
	public static class NumericLiteralContext extends ParserRuleContext {
		public TerminalNode INTEGER_LITERAL() { return getToken(HqlParser.INTEGER_LITERAL, 0); }
		public TerminalNode FLOAT_LITERAL() { return getToken(HqlParser.FLOAT_LITERAL, 0); }
		public TerminalNode HEXLITERAL() { return getToken(HqlParser.HEXLITERAL, 0); }
		public NumericLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_numericLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterNumericLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitNumericLiteral(this);
		}
	}

	public final NumericLiteralContext numericLiteral() throws RecognitionException {
		NumericLiteralContext _localctx = new NumericLiteralContext(_ctx, getState());
		enterRule(_localctx, 116, RULE_numericLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(696);
			_la = _input.LA(1);
			if ( !(((((_la - 203)) & ~0x3f) == 0 && ((1L << (_la - 203)) & 7L) != 0)) ) {
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
	public static class DateTimeLiteralContext extends ParserRuleContext {
		public TerminalNode LOCAL_DATE() { return getToken(HqlParser.LOCAL_DATE, 0); }
		public TerminalNode LOCAL_TIME() { return getToken(HqlParser.LOCAL_TIME, 0); }
		public TerminalNode LOCAL_DATETIME() { return getToken(HqlParser.LOCAL_DATETIME, 0); }
		public TerminalNode CURRENT_DATE() { return getToken(HqlParser.CURRENT_DATE, 0); }
		public TerminalNode CURRENT_TIME() { return getToken(HqlParser.CURRENT_TIME, 0); }
		public TerminalNode CURRENT_TIMESTAMP() { return getToken(HqlParser.CURRENT_TIMESTAMP, 0); }
		public TerminalNode OFFSET_DATETIME() { return getToken(HqlParser.OFFSET_DATETIME, 0); }
		public TerminalNode DATE() { return getToken(HqlParser.DATE, 0); }
		public TerminalNode LOCAL() { return getToken(HqlParser.LOCAL, 0); }
		public TerminalNode CURRENT() { return getToken(HqlParser.CURRENT, 0); }
		public TerminalNode TIME() { return getToken(HqlParser.TIME, 0); }
		public TerminalNode DATETIME() { return getToken(HqlParser.DATETIME, 0); }
		public TerminalNode OFFSET() { return getToken(HqlParser.OFFSET, 0); }
		public TerminalNode INSTANT() { return getToken(HqlParser.INSTANT, 0); }
		public DateTimeLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dateTimeLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterDateTimeLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitDateTimeLiteral(this);
		}
	}

	public final DateTimeLiteralContext dateTimeLiteral() throws RecognitionException {
		DateTimeLiteralContext _localctx = new DateTimeLiteralContext(_ctx, getState());
		enterRule(_localctx, 118, RULE_dateTimeLiteral);
		int _la;
		try {
			setState(712);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,79,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(698);
				match(LOCAL_DATE);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(699);
				match(LOCAL_TIME);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(700);
				match(LOCAL_DATETIME);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(701);
				match(CURRENT_DATE);
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(702);
				match(CURRENT_TIME);
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(703);
				match(CURRENT_TIMESTAMP);
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(704);
				match(OFFSET_DATETIME);
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(705);
				_la = _input.LA(1);
				if ( !(_la==CURRENT || _la==LOCAL) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(706);
				match(DATE);
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(707);
				_la = _input.LA(1);
				if ( !(_la==CURRENT || _la==LOCAL) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(708);
				match(TIME);
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(709);
				_la = _input.LA(1);
				if ( !(_la==CURRENT || _la==LOCAL || _la==OFFSET) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(710);
				match(DATETIME);
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(711);
				match(INSTANT);
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
	public static class DatetimeFieldContext extends ParserRuleContext {
		public TerminalNode YEAR() { return getToken(HqlParser.YEAR, 0); }
		public TerminalNode MONTH() { return getToken(HqlParser.MONTH, 0); }
		public TerminalNode DAY() { return getToken(HqlParser.DAY, 0); }
		public TerminalNode WEEK() { return getToken(HqlParser.WEEK, 0); }
		public TerminalNode QUARTER() { return getToken(HqlParser.QUARTER, 0); }
		public TerminalNode HOUR() { return getToken(HqlParser.HOUR, 0); }
		public TerminalNode MINUTE() { return getToken(HqlParser.MINUTE, 0); }
		public TerminalNode SECOND() { return getToken(HqlParser.SECOND, 0); }
		public TerminalNode NANOSECOND() { return getToken(HqlParser.NANOSECOND, 0); }
		public TerminalNode EPOCH() { return getToken(HqlParser.EPOCH, 0); }
		public DatetimeFieldContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_datetimeField; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterDatetimeField(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitDatetimeField(this);
		}
	}

	public final DatetimeFieldContext datetimeField() throws RecognitionException {
		DatetimeFieldContext _localctx = new DatetimeFieldContext(_ctx, getState());
		enterRule(_localctx, 120, RULE_datetimeField);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(714);
			_la = _input.LA(1);
			if ( !(_la==DAY || _la==EPOCH || ((((_la - 87)) & ~0x3f) == 0 && ((1L << (_la - 87)) & 3848290697217L) != 0) || ((((_la - 156)) & ~0x3f) == 0 && ((1L << (_la - 156)) & 8933531975937L) != 0)) ) {
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
	public static class BinaryLiteralContext extends ParserRuleContext {
		public TerminalNode BINARY_LITERAL() { return getToken(HqlParser.BINARY_LITERAL, 0); }
		public List<TerminalNode> HEXLITERAL() { return getTokens(HqlParser.HEXLITERAL); }
		public TerminalNode HEXLITERAL(int i) {
			return getToken(HqlParser.HEXLITERAL, i);
		}
		public BinaryLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_binaryLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterBinaryLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitBinaryLiteral(this);
		}
	}

	public final BinaryLiteralContext binaryLiteral() throws RecognitionException {
		BinaryLiteralContext _localctx = new BinaryLiteralContext(_ctx, getState());
		enterRule(_localctx, 122, RULE_binaryLiteral);
		int _la;
		try {
			setState(727);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BINARY_LITERAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(716);
				match(BINARY_LITERAL);
				}
				break;
			case T__5:
				enterOuterAlt(_localctx, 2);
				{
				setState(717);
				match(T__5);
				setState(718);
				match(HEXLITERAL);
				setState(723);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__0) {
					{
					{
					setState(719);
					match(T__0);
					setState(720);
					match(HEXLITERAL);
					}
					}
					setState(725);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(726);
				match(T__6);
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
	public static class ExpressionContext extends ParserRuleContext {
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
	 
		public ExpressionContext() { }
		public void copyFrom(ExpressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AdditionExpressionContext extends ExpressionContext {
		public Token op;
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public AdditionExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterAdditionExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitAdditionExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FromDurationExpressionContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode BY() { return getToken(HqlParser.BY, 0); }
		public DatetimeFieldContext datetimeField() {
			return getRuleContext(DatetimeFieldContext.class,0);
		}
		public FromDurationExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterFromDurationExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitFromDurationExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PlainPrimaryExpressionContext extends ExpressionContext {
		public PrimaryExpressionContext primaryExpression() {
			return getRuleContext(PrimaryExpressionContext.class,0);
		}
		public PlainPrimaryExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterPlainPrimaryExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitPlainPrimaryExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class TupleExpressionContext extends ExpressionContext {
		public List<ExpressionOrPredicateContext> expressionOrPredicate() {
			return getRuleContexts(ExpressionOrPredicateContext.class);
		}
		public ExpressionOrPredicateContext expressionOrPredicate(int i) {
			return getRuleContext(ExpressionOrPredicateContext.class,i);
		}
		public TupleExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterTupleExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitTupleExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class GroupedExpressionContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public GroupedExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterGroupedExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitGroupedExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SignedNumericLiteralContext extends ExpressionContext {
		public Token op;
		public NumericLiteralContext numericLiteral() {
			return getRuleContext(NumericLiteralContext.class,0);
		}
		public SignedNumericLiteralContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSignedNumericLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSignedNumericLiteral(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ToDurationExpressionContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public DatetimeFieldContext datetimeField() {
			return getRuleContext(DatetimeFieldContext.class,0);
		}
		public ToDurationExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterToDurationExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitToDurationExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SubqueryExpressionContext extends ExpressionContext {
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public SubqueryExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSubqueryExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSubqueryExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DayOfMonthExpressionContext extends ExpressionContext {
		public TerminalNode DAY() { return getToken(HqlParser.DAY, 0); }
		public TerminalNode OF() { return getToken(HqlParser.OF, 0); }
		public TerminalNode MONTH() { return getToken(HqlParser.MONTH, 0); }
		public DayOfMonthExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterDayOfMonthExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitDayOfMonthExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DayOfWeekExpressionContext extends ExpressionContext {
		public TerminalNode DAY() { return getToken(HqlParser.DAY, 0); }
		public TerminalNode OF() { return getToken(HqlParser.OF, 0); }
		public TerminalNode WEEK() { return getToken(HqlParser.WEEK, 0); }
		public DayOfWeekExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterDayOfWeekExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitDayOfWeekExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class WeekOfYearExpressionContext extends ExpressionContext {
		public TerminalNode WEEK() { return getToken(HqlParser.WEEK, 0); }
		public TerminalNode OF() { return getToken(HqlParser.OF, 0); }
		public TerminalNode YEAR() { return getToken(HqlParser.YEAR, 0); }
		public WeekOfYearExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterWeekOfYearExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitWeekOfYearExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class HqlConcatenationExpressionContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public HqlConcatenationExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterHqlConcatenationExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitHqlConcatenationExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MultiplicationExpressionContext extends ExpressionContext {
		public Token op;
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode ASTERISK() { return getToken(HqlParser.ASTERISK, 0); }
		public MultiplicationExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterMultiplicationExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitMultiplicationExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SignedExpressionContext extends ExpressionContext {
		public Token op;
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public SignedExpressionContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSignedExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSignedExpression(this);
		}
	}

	public final ExpressionContext expression() throws RecognitionException {
		return expression(0);
	}

	private ExpressionContext expression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExpressionContext _localctx = new ExpressionContext(_ctx, _parentState);
		ExpressionContext _prevctx = _localctx;
		int _startState = 124;
		enterRecursionRule(_localctx, 124, RULE_expression, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(762);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,83,_ctx) ) {
			case 1:
				{
				_localctx = new GroupedExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(730);
				match(T__1);
				setState(731);
				expression(0);
				setState(732);
				match(T__2);
				}
				break;
			case 2:
				{
				_localctx = new TupleExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(734);
				match(T__1);
				setState(735);
				expressionOrPredicate();
				setState(738); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(736);
					match(T__0);
					setState(737);
					expressionOrPredicate();
					}
					}
					setState(740); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==T__0 );
				setState(742);
				match(T__2);
				}
				break;
			case 3:
				{
				_localctx = new SubqueryExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(744);
				match(T__1);
				setState(745);
				subquery();
				setState(746);
				match(T__2);
				}
				break;
			case 4:
				{
				_localctx = new PlainPrimaryExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(748);
				primaryExpression();
				}
				break;
			case 5:
				{
				_localctx = new SignedNumericLiteralContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(749);
				((SignedNumericLiteralContext)_localctx).op = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__7 || _la==T__8) ) {
					((SignedNumericLiteralContext)_localctx).op = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(750);
				numericLiteral();
				}
				break;
			case 6:
				{
				_localctx = new SignedExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(751);
				((SignedExpressionContext)_localctx).op = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__7 || _la==T__8) ) {
					((SignedExpressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(752);
				expression(9);
				}
				break;
			case 7:
				{
				_localctx = new DayOfWeekExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(753);
				match(DAY);
				setState(754);
				match(OF);
				setState(755);
				match(WEEK);
				}
				break;
			case 8:
				{
				_localctx = new DayOfMonthExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(756);
				match(DAY);
				setState(757);
				match(OF);
				setState(758);
				match(MONTH);
				}
				break;
			case 9:
				{
				_localctx = new WeekOfYearExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(759);
				match(WEEK);
				setState(760);
				match(OF);
				setState(761);
				match(YEAR);
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(780);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,85,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(778);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,84,_ctx) ) {
					case 1:
						{
						_localctx = new MultiplicationExpressionContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(764);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(765);
						((MultiplicationExpressionContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__9 || _la==ASTERISK) ) {
							((MultiplicationExpressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(766);
						expression(7);
						}
						break;
					case 2:
						{
						_localctx = new AdditionExpressionContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(767);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(768);
						((AdditionExpressionContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__7 || _la==T__8) ) {
							((AdditionExpressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(769);
						expression(6);
						}
						break;
					case 3:
						{
						_localctx = new HqlConcatenationExpressionContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(770);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(771);
						match(T__10);
						setState(772);
						expression(5);
						}
						break;
					case 4:
						{
						_localctx = new ToDurationExpressionContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(773);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(774);
						datetimeField();
						}
						break;
					case 5:
						{
						_localctx = new FromDurationExpressionContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(775);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(776);
						match(BY);
						setState(777);
						datetimeField();
						}
						break;
					}
					} 
				}
				setState(782);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,85,_ctx);
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
	public static class PrimaryExpressionContext extends ParserRuleContext {
		public PrimaryExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_primaryExpression; }
	 
		public PrimaryExpressionContext() { }
		public void copyFrom(PrimaryExpressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FunctionExpressionContext extends PrimaryExpressionContext {
		public FunctionContext function() {
			return getRuleContext(FunctionContext.class,0);
		}
		public FunctionExpressionContext(PrimaryExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterFunctionExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitFunctionExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class LiteralExpressionContext extends PrimaryExpressionContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public LiteralExpressionContext(PrimaryExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterLiteralExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitLiteralExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ParameterExpressionContext extends PrimaryExpressionContext {
		public ParameterContext parameter() {
			return getRuleContext(ParameterContext.class,0);
		}
		public ParameterExpressionContext(PrimaryExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterParameterExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitParameterExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class GeneralPathExpressionContext extends PrimaryExpressionContext {
		public GeneralPathFragmentContext generalPathFragment() {
			return getRuleContext(GeneralPathFragmentContext.class,0);
		}
		public GeneralPathExpressionContext(PrimaryExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterGeneralPathExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitGeneralPathExpression(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CaseExpressionContext extends PrimaryExpressionContext {
		public CaseListContext caseList() {
			return getRuleContext(CaseListContext.class,0);
		}
		public CaseExpressionContext(PrimaryExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCaseExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCaseExpression(this);
		}
	}

	public final PrimaryExpressionContext primaryExpression() throws RecognitionException {
		PrimaryExpressionContext _localctx = new PrimaryExpressionContext(_ctx, getState());
		enterRule(_localctx, 126, RULE_primaryExpression);
		try {
			setState(788);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,86,_ctx) ) {
			case 1:
				_localctx = new CaseExpressionContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(783);
				caseList();
				}
				break;
			case 2:
				_localctx = new LiteralExpressionContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(784);
				literal();
				}
				break;
			case 3:
				_localctx = new ParameterExpressionContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(785);
				parameter();
				}
				break;
			case 4:
				_localctx = new FunctionExpressionContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(786);
				function();
				}
				break;
			case 5:
				_localctx = new GeneralPathExpressionContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(787);
				generalPathFragment();
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
	public static class IdentificationVariableContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public SimplePathContext simplePath() {
			return getRuleContext(SimplePathContext.class,0);
		}
		public IdentificationVariableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identificationVariable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterIdentificationVariable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitIdentificationVariable(this);
		}
	}

	public final IdentificationVariableContext identificationVariable() throws RecognitionException {
		IdentificationVariableContext _localctx = new IdentificationVariableContext(_ctx, getState());
		enterRule(_localctx, 128, RULE_identificationVariable);
		try {
			setState(792);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,87,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(790);
				identifier();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(791);
				simplePath();
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
	public static class PathContext extends ParserRuleContext {
		public TreatedPathContext treatedPath() {
			return getRuleContext(TreatedPathContext.class,0);
		}
		public PathContinutationContext pathContinutation() {
			return getRuleContext(PathContinutationContext.class,0);
		}
		public GeneralPathFragmentContext generalPathFragment() {
			return getRuleContext(GeneralPathFragmentContext.class,0);
		}
		public PathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_path; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterPath(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitPath(this);
		}
	}

	public final PathContext path() throws RecognitionException {
		PathContext _localctx = new PathContext(_ctx, getState());
		enterRule(_localctx, 130, RULE_path);
		try {
			setState(799);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,89,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(794);
				treatedPath();
				setState(796);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,88,_ctx) ) {
				case 1:
					{
					setState(795);
					pathContinutation();
					}
					break;
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(798);
				generalPathFragment();
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
	public static class GeneralPathFragmentContext extends ParserRuleContext {
		public SimplePathContext simplePath() {
			return getRuleContext(SimplePathContext.class,0);
		}
		public IndexedPathAccessFragmentContext indexedPathAccessFragment() {
			return getRuleContext(IndexedPathAccessFragmentContext.class,0);
		}
		public GeneralPathFragmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_generalPathFragment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterGeneralPathFragment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitGeneralPathFragment(this);
		}
	}

	public final GeneralPathFragmentContext generalPathFragment() throws RecognitionException {
		GeneralPathFragmentContext _localctx = new GeneralPathFragmentContext(_ctx, getState());
		enterRule(_localctx, 132, RULE_generalPathFragment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(801);
			simplePath();
			setState(803);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,90,_ctx) ) {
			case 1:
				{
				setState(802);
				indexedPathAccessFragment();
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
	public static class IndexedPathAccessFragmentContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public GeneralPathFragmentContext generalPathFragment() {
			return getRuleContext(GeneralPathFragmentContext.class,0);
		}
		public IndexedPathAccessFragmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_indexedPathAccessFragment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterIndexedPathAccessFragment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitIndexedPathAccessFragment(this);
		}
	}

	public final IndexedPathAccessFragmentContext indexedPathAccessFragment() throws RecognitionException {
		IndexedPathAccessFragmentContext _localctx = new IndexedPathAccessFragmentContext(_ctx, getState());
		enterRule(_localctx, 134, RULE_indexedPathAccessFragment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(805);
			match(T__11);
			setState(806);
			expression(0);
			setState(807);
			match(T__12);
			setState(810);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,91,_ctx) ) {
			case 1:
				{
				setState(808);
				match(T__13);
				setState(809);
				generalPathFragment();
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
	public static class SimplePathContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public List<SimplePathElementContext> simplePathElement() {
			return getRuleContexts(SimplePathElementContext.class);
		}
		public SimplePathElementContext simplePathElement(int i) {
			return getRuleContext(SimplePathElementContext.class,i);
		}
		public SimplePathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simplePath; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSimplePath(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSimplePath(this);
		}
	}

	public final SimplePathContext simplePath() throws RecognitionException {
		SimplePathContext _localctx = new SimplePathContext(_ctx, getState());
		enterRule(_localctx, 136, RULE_simplePath);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(812);
			identifier();
			setState(816);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,92,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(813);
					simplePathElement();
					}
					} 
				}
				setState(818);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,92,_ctx);
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
	public static class SimplePathElementContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public SimplePathElementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simplePathElement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSimplePathElement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSimplePathElement(this);
		}
	}

	public final SimplePathElementContext simplePathElement() throws RecognitionException {
		SimplePathElementContext _localctx = new SimplePathElementContext(_ctx, getState());
		enterRule(_localctx, 138, RULE_simplePathElement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(819);
			match(T__13);
			setState(820);
			identifier();
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
	public static class CaseListContext extends ParserRuleContext {
		public SimpleCaseExpressionContext simpleCaseExpression() {
			return getRuleContext(SimpleCaseExpressionContext.class,0);
		}
		public SearchedCaseExpressionContext searchedCaseExpression() {
			return getRuleContext(SearchedCaseExpressionContext.class,0);
		}
		public CaseListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_caseList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCaseList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCaseList(this);
		}
	}

	public final CaseListContext caseList() throws RecognitionException {
		CaseListContext _localctx = new CaseListContext(_ctx, getState());
		enterRule(_localctx, 140, RULE_caseList);
		try {
			setState(824);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,93,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(822);
				simpleCaseExpression();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(823);
				searchedCaseExpression();
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
	public static class SimpleCaseExpressionContext extends ParserRuleContext {
		public TerminalNode CASE() { return getToken(HqlParser.CASE, 0); }
		public List<ExpressionOrPredicateContext> expressionOrPredicate() {
			return getRuleContexts(ExpressionOrPredicateContext.class);
		}
		public ExpressionOrPredicateContext expressionOrPredicate(int i) {
			return getRuleContext(ExpressionOrPredicateContext.class,i);
		}
		public TerminalNode END() { return getToken(HqlParser.END, 0); }
		public List<CaseWhenExpressionClauseContext> caseWhenExpressionClause() {
			return getRuleContexts(CaseWhenExpressionClauseContext.class);
		}
		public CaseWhenExpressionClauseContext caseWhenExpressionClause(int i) {
			return getRuleContext(CaseWhenExpressionClauseContext.class,i);
		}
		public TerminalNode ELSE() { return getToken(HqlParser.ELSE, 0); }
		public SimpleCaseExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simpleCaseExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSimpleCaseExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSimpleCaseExpression(this);
		}
	}

	public final SimpleCaseExpressionContext simpleCaseExpression() throws RecognitionException {
		SimpleCaseExpressionContext _localctx = new SimpleCaseExpressionContext(_ctx, getState());
		enterRule(_localctx, 142, RULE_simpleCaseExpression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(826);
			match(CASE);
			setState(827);
			expressionOrPredicate();
			setState(829); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(828);
				caseWhenExpressionClause();
				}
				}
				setState(831); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==WHEN );
			setState(835);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(833);
				match(ELSE);
				setState(834);
				expressionOrPredicate();
				}
			}

			setState(837);
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
	public static class SearchedCaseExpressionContext extends ParserRuleContext {
		public TerminalNode CASE() { return getToken(HqlParser.CASE, 0); }
		public TerminalNode END() { return getToken(HqlParser.END, 0); }
		public List<CaseWhenPredicateClauseContext> caseWhenPredicateClause() {
			return getRuleContexts(CaseWhenPredicateClauseContext.class);
		}
		public CaseWhenPredicateClauseContext caseWhenPredicateClause(int i) {
			return getRuleContext(CaseWhenPredicateClauseContext.class,i);
		}
		public TerminalNode ELSE() { return getToken(HqlParser.ELSE, 0); }
		public ExpressionOrPredicateContext expressionOrPredicate() {
			return getRuleContext(ExpressionOrPredicateContext.class,0);
		}
		public SearchedCaseExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_searchedCaseExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterSearchedCaseExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitSearchedCaseExpression(this);
		}
	}

	public final SearchedCaseExpressionContext searchedCaseExpression() throws RecognitionException {
		SearchedCaseExpressionContext _localctx = new SearchedCaseExpressionContext(_ctx, getState());
		enterRule(_localctx, 144, RULE_searchedCaseExpression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(839);
			match(CASE);
			setState(841); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(840);
				caseWhenPredicateClause();
				}
				}
				setState(843); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==WHEN );
			setState(847);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(845);
				match(ELSE);
				setState(846);
				expressionOrPredicate();
				}
			}

			setState(849);
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
	public static class CaseWhenExpressionClauseContext extends ParserRuleContext {
		public TerminalNode WHEN() { return getToken(HqlParser.WHEN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode THEN() { return getToken(HqlParser.THEN, 0); }
		public ExpressionOrPredicateContext expressionOrPredicate() {
			return getRuleContext(ExpressionOrPredicateContext.class,0);
		}
		public CaseWhenExpressionClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_caseWhenExpressionClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCaseWhenExpressionClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCaseWhenExpressionClause(this);
		}
	}

	public final CaseWhenExpressionClauseContext caseWhenExpressionClause() throws RecognitionException {
		CaseWhenExpressionClauseContext _localctx = new CaseWhenExpressionClauseContext(_ctx, getState());
		enterRule(_localctx, 146, RULE_caseWhenExpressionClause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(851);
			match(WHEN);
			setState(852);
			expression(0);
			setState(853);
			match(THEN);
			setState(854);
			expressionOrPredicate();
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
	public static class CaseWhenPredicateClauseContext extends ParserRuleContext {
		public TerminalNode WHEN() { return getToken(HqlParser.WHEN, 0); }
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public TerminalNode THEN() { return getToken(HqlParser.THEN, 0); }
		public ExpressionOrPredicateContext expressionOrPredicate() {
			return getRuleContext(ExpressionOrPredicateContext.class,0);
		}
		public CaseWhenPredicateClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_caseWhenPredicateClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCaseWhenPredicateClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCaseWhenPredicateClause(this);
		}
	}

	public final CaseWhenPredicateClauseContext caseWhenPredicateClause() throws RecognitionException {
		CaseWhenPredicateClauseContext _localctx = new CaseWhenPredicateClauseContext(_ctx, getState());
		enterRule(_localctx, 148, RULE_caseWhenPredicateClause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(856);
			match(WHEN);
			setState(857);
			predicate(0);
			setState(858);
			match(THEN);
			setState(859);
			expressionOrPredicate();
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
	public static class FunctionContext extends ParserRuleContext {
		public FunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_function; }
	 
		public FunctionContext() { }
		public void copyFrom(FunctionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CastFunctionInvocationContext extends FunctionContext {
		public CastFunctionContext castFunction() {
			return getRuleContext(CastFunctionContext.class,0);
		}
		public CastFunctionInvocationContext(FunctionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCastFunctionInvocation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCastFunctionInvocation(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class TreatedPathInvocationContext extends FunctionContext {
		public TreatedPathContext treatedPath() {
			return getRuleContext(TreatedPathContext.class,0);
		}
		public TreatedPathInvocationContext(FunctionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterTreatedPathInvocation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitTreatedPathInvocation(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class FunctionWithSubqueryContext extends FunctionContext {
		public FunctionNameContext functionName() {
			return getRuleContext(FunctionNameContext.class,0);
		}
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public FunctionWithSubqueryContext(FunctionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterFunctionWithSubquery(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitFunctionWithSubquery(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AnyFunctionInvocationContext extends FunctionContext {
		public AnyFunctionContext anyFunction() {
			return getRuleContext(AnyFunctionContext.class,0);
		}
		public AnyFunctionInvocationContext(FunctionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterAnyFunctionInvocation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitAnyFunctionInvocation(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExtractFunctionInvocationContext extends FunctionContext {
		public ExtractFunctionContext extractFunction() {
			return getRuleContext(ExtractFunctionContext.class,0);
		}
		public ExtractFunctionInvocationContext(FunctionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterExtractFunctionInvocation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitExtractFunctionInvocation(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class GenericFunctionContext extends FunctionContext {
		public FunctionNameContext functionName() {
			return getRuleContext(FunctionNameContext.class,0);
		}
		public FunctionArgumentsContext functionArguments() {
			return getRuleContext(FunctionArgumentsContext.class,0);
		}
		public TerminalNode ASTERISK() { return getToken(HqlParser.ASTERISK, 0); }
		public PathContinutationContext pathContinutation() {
			return getRuleContext(PathContinutationContext.class,0);
		}
		public FilterClauseContext filterClause() {
			return getRuleContext(FilterClauseContext.class,0);
		}
		public WithinGroupContext withinGroup() {
			return getRuleContext(WithinGroupContext.class,0);
		}
		public OverClauseContext overClause() {
			return getRuleContext(OverClauseContext.class,0);
		}
		public GenericFunctionContext(FunctionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterGenericFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitGenericFunction(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class EveryFunctionInvocationContext extends FunctionContext {
		public EveryFunctionContext everyFunction() {
			return getRuleContext(EveryFunctionContext.class,0);
		}
		public EveryFunctionInvocationContext(FunctionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterEveryFunctionInvocation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitEveryFunctionInvocation(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class TrimFunctionInvocationContext extends FunctionContext {
		public TrimFunctionContext trimFunction() {
			return getRuleContext(TrimFunctionContext.class,0);
		}
		public TrimFunctionInvocationContext(FunctionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterTrimFunctionInvocation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitTrimFunctionInvocation(this);
		}
	}

	public final FunctionContext function() throws RecognitionException {
		FunctionContext _localctx = new FunctionContext(_ctx, getState());
		enterRule(_localctx, 150, RULE_function);
		try {
			setState(891);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,103,_ctx) ) {
			case 1:
				_localctx = new GenericFunctionContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(861);
				functionName();
				setState(862);
				match(T__1);
				setState(865);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__1:
				case T__5:
				case T__7:
				case T__8:
				case T__21:
				case T__22:
				case ALL:
				case AND:
				case ANY:
				case AS:
				case ASC:
				case AVG:
				case BETWEEN:
				case BOTH:
				case BREADTH:
				case BY:
				case CASE:
				case CAST:
				case COLLATE:
				case COUNT:
				case CROSS:
				case CUBE:
				case CURRENT:
				case CURRENT_DATE:
				case CURRENT_INSTANT:
				case CURRENT_TIME:
				case CURRENT_TIMESTAMP:
				case CYCLE:
				case DATE:
				case DATETIME:
				case DAY:
				case DEFAULT:
				case DELETE:
				case DEPTH:
				case DESC:
				case DISTINCT:
				case ELEMENT:
				case ELEMENTS:
				case ELSE:
				case EMPTY:
				case END:
				case ENTRY:
				case EPOCH:
				case ERROR:
				case ESCAPE:
				case EVERY:
				case EXCEPT:
				case EXCLUDE:
				case EXISTS:
				case EXTRACT:
				case FALSE:
				case FETCH:
				case FILTER:
				case FIRST:
				case FLOOR:
				case FOLLOWING:
				case FOR:
				case FORMAT:
				case FROM:
				case FULL:
				case FUNCTION:
				case GROUP:
				case GROUPS:
				case HAVING:
				case HOUR:
				case ID:
				case IGNORE:
				case ILIKE:
				case IN:
				case INDEX:
				case INDICES:
				case INNER:
				case INSERT:
				case INSTANT:
				case INTERSECT:
				case INTO:
				case IS:
				case JOIN:
				case KEY:
				case LAST:
				case LEADING:
				case LEFT:
				case LIKE:
				case LIMIT:
				case LIST:
				case LISTAGG:
				case LOCAL:
				case LOCAL_DATE:
				case LOCAL_DATETIME:
				case LOCAL_TIME:
				case MAP:
				case MATERIALIZED:
				case MAX:
				case MAXELEMENT:
				case MAXINDEX:
				case MEMBER:
				case MICROSECOND:
				case MILLISECOND:
				case MIN:
				case MINELEMENT:
				case MININDEX:
				case MINUTE:
				case MONTH:
				case NANOSECOND:
				case NATURALID:
				case NEW:
				case NEXT:
				case NO:
				case NOT:
				case NULL:
				case NULLS:
				case OBJECT:
				case OF:
				case OFFSET:
				case OFFSET_DATETIME:
				case ON:
				case ONLY:
				case OR:
				case ORDER:
				case OTHERS:
				case OUTER:
				case OVER:
				case OVERFLOW:
				case OVERLAY:
				case PAD:
				case PARTITION:
				case PERCENT:
				case PLACING:
				case POSITION:
				case POWER:
				case PRECEDING:
				case QUARTER:
				case RANGE:
				case RESPECT:
				case RIGHT:
				case ROLLUP:
				case ROW:
				case ROWS:
				case SEARCH:
				case SECOND:
				case SELECT:
				case SET:
				case SIZE:
				case SOME:
				case SUBSTRING:
				case SUM:
				case THEN:
				case TIES:
				case TIME:
				case TIMESTAMP:
				case TIMEZONE_HOUR:
				case TIMEZONE_MINUTE:
				case TO:
				case TRAILING:
				case TREAT:
				case TRIM:
				case TRUE:
				case TRUNC:
				case TRUNCATE:
				case TYPE:
				case UNBOUNDED:
				case UNION:
				case UPDATE:
				case USING:
				case VALUE:
				case VALUES:
				case VERSION:
				case VERSIONED:
				case WEEK:
				case WHEN:
				case WHERE:
				case WITH:
				case WITHIN:
				case WITHOUT:
				case YEAR:
				case CHARACTER:
				case STRINGLITERAL:
				case JAVASTRINGLITERAL:
				case INTEGER_LITERAL:
				case FLOAT_LITERAL:
				case HEXLITERAL:
				case BINARY_LITERAL:
				case IDENTIFICATION_VARIABLE:
					{
					setState(863);
					functionArguments();
					}
					break;
				case ASTERISK:
					{
					setState(864);
					match(ASTERISK);
					}
					break;
				case T__2:
					break;
				default:
					break;
				}
				setState(867);
				match(T__2);
				setState(869);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,99,_ctx) ) {
				case 1:
					{
					setState(868);
					pathContinutation();
					}
					break;
				}
				setState(872);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,100,_ctx) ) {
				case 1:
					{
					setState(871);
					filterClause();
					}
					break;
				}
				setState(875);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,101,_ctx) ) {
				case 1:
					{
					setState(874);
					withinGroup();
					}
					break;
				}
				setState(878);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,102,_ctx) ) {
				case 1:
					{
					setState(877);
					overClause();
					}
					break;
				}
				}
				break;
			case 2:
				_localctx = new FunctionWithSubqueryContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(880);
				functionName();
				setState(881);
				match(T__1);
				setState(882);
				subquery();
				setState(883);
				match(T__2);
				}
				break;
			case 3:
				_localctx = new CastFunctionInvocationContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(885);
				castFunction();
				}
				break;
			case 4:
				_localctx = new ExtractFunctionInvocationContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(886);
				extractFunction();
				}
				break;
			case 5:
				_localctx = new TrimFunctionInvocationContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(887);
				trimFunction();
				}
				break;
			case 6:
				_localctx = new EveryFunctionInvocationContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(888);
				everyFunction();
				}
				break;
			case 7:
				_localctx = new AnyFunctionInvocationContext(_localctx);
				enterOuterAlt(_localctx, 7);
				{
				setState(889);
				anyFunction();
				}
				break;
			case 8:
				_localctx = new TreatedPathInvocationContext(_localctx);
				enterOuterAlt(_localctx, 8);
				{
				setState(890);
				treatedPath();
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
	public static class FunctionArgumentsContext extends ParserRuleContext {
		public List<ExpressionOrPredicateContext> expressionOrPredicate() {
			return getRuleContexts(ExpressionOrPredicateContext.class);
		}
		public ExpressionOrPredicateContext expressionOrPredicate(int i) {
			return getRuleContext(ExpressionOrPredicateContext.class,i);
		}
		public TerminalNode DISTINCT() { return getToken(HqlParser.DISTINCT, 0); }
		public FunctionArgumentsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionArguments; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterFunctionArguments(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitFunctionArguments(this);
		}
	}

	public final FunctionArgumentsContext functionArguments() throws RecognitionException {
		FunctionArgumentsContext _localctx = new FunctionArgumentsContext(_ctx, getState());
		enterRule(_localctx, 152, RULE_functionArguments);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(894);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,104,_ctx) ) {
			case 1:
				{
				setState(893);
				match(DISTINCT);
				}
				break;
			}
			setState(896);
			expressionOrPredicate();
			setState(901);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(897);
				match(T__0);
				setState(898);
				expressionOrPredicate();
				}
				}
				setState(903);
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
	public static class FilterClauseContext extends ParserRuleContext {
		public TerminalNode FILTER() { return getToken(HqlParser.FILTER, 0); }
		public WhereClauseContext whereClause() {
			return getRuleContext(WhereClauseContext.class,0);
		}
		public FilterClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_filterClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterFilterClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitFilterClause(this);
		}
	}

	public final FilterClauseContext filterClause() throws RecognitionException {
		FilterClauseContext _localctx = new FilterClauseContext(_ctx, getState());
		enterRule(_localctx, 154, RULE_filterClause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(904);
			match(FILTER);
			setState(905);
			match(T__1);
			setState(906);
			whereClause();
			setState(907);
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
	public static class WithinGroupContext extends ParserRuleContext {
		public TerminalNode WITHIN() { return getToken(HqlParser.WITHIN, 0); }
		public TerminalNode GROUP() { return getToken(HqlParser.GROUP, 0); }
		public OrderByClauseContext orderByClause() {
			return getRuleContext(OrderByClauseContext.class,0);
		}
		public WithinGroupContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_withinGroup; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterWithinGroup(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitWithinGroup(this);
		}
	}

	public final WithinGroupContext withinGroup() throws RecognitionException {
		WithinGroupContext _localctx = new WithinGroupContext(_ctx, getState());
		enterRule(_localctx, 156, RULE_withinGroup);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(909);
			match(WITHIN);
			setState(910);
			match(GROUP);
			setState(911);
			match(T__1);
			setState(912);
			orderByClause();
			setState(913);
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
	public static class OverClauseContext extends ParserRuleContext {
		public TerminalNode OVER() { return getToken(HqlParser.OVER, 0); }
		public PartitionClauseContext partitionClause() {
			return getRuleContext(PartitionClauseContext.class,0);
		}
		public OrderByClauseContext orderByClause() {
			return getRuleContext(OrderByClauseContext.class,0);
		}
		public FrameClauseContext frameClause() {
			return getRuleContext(FrameClauseContext.class,0);
		}
		public OverClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_overClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterOverClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitOverClause(this);
		}
	}

	public final OverClauseContext overClause() throws RecognitionException {
		OverClauseContext _localctx = new OverClauseContext(_ctx, getState());
		enterRule(_localctx, 158, RULE_overClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(915);
			match(OVER);
			setState(916);
			match(T__1);
			setState(918);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==PARTITION) {
				{
				setState(917);
				partitionClause();
				}
			}

			setState(921);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ORDER) {
				{
				setState(920);
				orderByClause();
				}
			}

			setState(924);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==GROUPS || _la==RANGE || _la==ROWS) {
				{
				setState(923);
				frameClause();
				}
			}

			setState(926);
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
	public static class PartitionClauseContext extends ParserRuleContext {
		public TerminalNode PARTITION() { return getToken(HqlParser.PARTITION, 0); }
		public TerminalNode BY() { return getToken(HqlParser.BY, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public PartitionClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_partitionClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterPartitionClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitPartitionClause(this);
		}
	}

	public final PartitionClauseContext partitionClause() throws RecognitionException {
		PartitionClauseContext _localctx = new PartitionClauseContext(_ctx, getState());
		enterRule(_localctx, 160, RULE_partitionClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(928);
			match(PARTITION);
			setState(929);
			match(BY);
			setState(930);
			expression(0);
			setState(935);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(931);
				match(T__0);
				setState(932);
				expression(0);
				}
				}
				setState(937);
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
	public static class FrameClauseContext extends ParserRuleContext {
		public FrameStartContext frameStart() {
			return getRuleContext(FrameStartContext.class,0);
		}
		public TerminalNode RANGE() { return getToken(HqlParser.RANGE, 0); }
		public TerminalNode ROWS() { return getToken(HqlParser.ROWS, 0); }
		public TerminalNode GROUPS() { return getToken(HqlParser.GROUPS, 0); }
		public FrameExclusionContext frameExclusion() {
			return getRuleContext(FrameExclusionContext.class,0);
		}
		public TerminalNode BETWEEN() { return getToken(HqlParser.BETWEEN, 0); }
		public TerminalNode AND() { return getToken(HqlParser.AND, 0); }
		public FrameEndContext frameEnd() {
			return getRuleContext(FrameEndContext.class,0);
		}
		public FrameClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_frameClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterFrameClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitFrameClause(this);
		}
	}

	public final FrameClauseContext frameClause() throws RecognitionException {
		FrameClauseContext _localctx = new FrameClauseContext(_ctx, getState());
		enterRule(_localctx, 162, RULE_frameClause);
		int _la;
		try {
			setState(951);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,112,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(938);
				_la = _input.LA(1);
				if ( !(_la==GROUPS || _la==RANGE || _la==ROWS) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(939);
				frameStart();
				setState(941);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EXCLUDE) {
					{
					setState(940);
					frameExclusion();
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(943);
				_la = _input.LA(1);
				if ( !(_la==GROUPS || _la==RANGE || _la==ROWS) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(944);
				match(BETWEEN);
				setState(945);
				frameStart();
				setState(946);
				match(AND);
				setState(947);
				frameEnd();
				setState(949);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EXCLUDE) {
					{
					setState(948);
					frameExclusion();
					}
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
	public static class FrameStartContext extends ParserRuleContext {
		public FrameStartContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_frameStart; }
	 
		public FrameStartContext() { }
		public void copyFrom(FrameStartContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionFollowingFrameStartContext extends FrameStartContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode FOLLOWING() { return getToken(HqlParser.FOLLOWING, 0); }
		public ExpressionFollowingFrameStartContext(FrameStartContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterExpressionFollowingFrameStart(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitExpressionFollowingFrameStart(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionPrecedingFrameStartContext extends FrameStartContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode PRECEDING() { return getToken(HqlParser.PRECEDING, 0); }
		public ExpressionPrecedingFrameStartContext(FrameStartContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterExpressionPrecedingFrameStart(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitExpressionPrecedingFrameStart(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CurrentRowFrameStartContext extends FrameStartContext {
		public TerminalNode CURRENT() { return getToken(HqlParser.CURRENT, 0); }
		public TerminalNode ROW() { return getToken(HqlParser.ROW, 0); }
		public CurrentRowFrameStartContext(FrameStartContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCurrentRowFrameStart(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCurrentRowFrameStart(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class UnboundedPrecedingFrameStartContext extends FrameStartContext {
		public TerminalNode UNBOUNDED() { return getToken(HqlParser.UNBOUNDED, 0); }
		public TerminalNode PRECEDING() { return getToken(HqlParser.PRECEDING, 0); }
		public UnboundedPrecedingFrameStartContext(FrameStartContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterUnboundedPrecedingFrameStart(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitUnboundedPrecedingFrameStart(this);
		}
	}

	public final FrameStartContext frameStart() throws RecognitionException {
		FrameStartContext _localctx = new FrameStartContext(_ctx, getState());
		enterRule(_localctx, 164, RULE_frameStart);
		try {
			setState(963);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,113,_ctx) ) {
			case 1:
				_localctx = new UnboundedPrecedingFrameStartContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(953);
				match(UNBOUNDED);
				setState(954);
				match(PRECEDING);
				}
				break;
			case 2:
				_localctx = new ExpressionPrecedingFrameStartContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(955);
				expression(0);
				setState(956);
				match(PRECEDING);
				}
				break;
			case 3:
				_localctx = new CurrentRowFrameStartContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(958);
				match(CURRENT);
				setState(959);
				match(ROW);
				}
				break;
			case 4:
				_localctx = new ExpressionFollowingFrameStartContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(960);
				expression(0);
				setState(961);
				match(FOLLOWING);
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
	public static class FrameExclusionContext extends ParserRuleContext {
		public FrameExclusionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_frameExclusion; }
	 
		public FrameExclusionContext() { }
		public void copyFrom(FrameExclusionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NoOthersFrameExclusionContext extends FrameExclusionContext {
		public TerminalNode EXCLUDE() { return getToken(HqlParser.EXCLUDE, 0); }
		public TerminalNode NO() { return getToken(HqlParser.NO, 0); }
		public TerminalNode OTHERS() { return getToken(HqlParser.OTHERS, 0); }
		public NoOthersFrameExclusionContext(FrameExclusionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterNoOthersFrameExclusion(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitNoOthersFrameExclusion(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class GroupFrameExclusionContext extends FrameExclusionContext {
		public TerminalNode EXCLUDE() { return getToken(HqlParser.EXCLUDE, 0); }
		public TerminalNode GROUP() { return getToken(HqlParser.GROUP, 0); }
		public GroupFrameExclusionContext(FrameExclusionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterGroupFrameExclusion(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitGroupFrameExclusion(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class TiesFrameExclusionContext extends FrameExclusionContext {
		public TerminalNode EXCLUDE() { return getToken(HqlParser.EXCLUDE, 0); }
		public TerminalNode TIES() { return getToken(HqlParser.TIES, 0); }
		public TiesFrameExclusionContext(FrameExclusionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterTiesFrameExclusion(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitTiesFrameExclusion(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CurrentRowFrameExclusionContext extends FrameExclusionContext {
		public TerminalNode EXCLUDE() { return getToken(HqlParser.EXCLUDE, 0); }
		public TerminalNode CURRENT() { return getToken(HqlParser.CURRENT, 0); }
		public TerminalNode ROW() { return getToken(HqlParser.ROW, 0); }
		public CurrentRowFrameExclusionContext(FrameExclusionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCurrentRowFrameExclusion(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCurrentRowFrameExclusion(this);
		}
	}

	public final FrameExclusionContext frameExclusion() throws RecognitionException {
		FrameExclusionContext _localctx = new FrameExclusionContext(_ctx, getState());
		enterRule(_localctx, 166, RULE_frameExclusion);
		try {
			setState(975);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,114,_ctx) ) {
			case 1:
				_localctx = new CurrentRowFrameExclusionContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(965);
				match(EXCLUDE);
				setState(966);
				match(CURRENT);
				setState(967);
				match(ROW);
				}
				break;
			case 2:
				_localctx = new GroupFrameExclusionContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(968);
				match(EXCLUDE);
				setState(969);
				match(GROUP);
				}
				break;
			case 3:
				_localctx = new TiesFrameExclusionContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(970);
				match(EXCLUDE);
				setState(971);
				match(TIES);
				}
				break;
			case 4:
				_localctx = new NoOthersFrameExclusionContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(972);
				match(EXCLUDE);
				setState(973);
				match(NO);
				setState(974);
				match(OTHERS);
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
	public static class FrameEndContext extends ParserRuleContext {
		public FrameEndContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_frameEnd; }
	 
		public FrameEndContext() { }
		public void copyFrom(FrameEndContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CurrentRowFrameEndContext extends FrameEndContext {
		public TerminalNode CURRENT() { return getToken(HqlParser.CURRENT, 0); }
		public TerminalNode ROW() { return getToken(HqlParser.ROW, 0); }
		public CurrentRowFrameEndContext(FrameEndContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCurrentRowFrameEnd(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCurrentRowFrameEnd(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionPrecedingFrameEndContext extends FrameEndContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode PRECEDING() { return getToken(HqlParser.PRECEDING, 0); }
		public ExpressionPrecedingFrameEndContext(FrameEndContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterExpressionPrecedingFrameEnd(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitExpressionPrecedingFrameEnd(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionFollowingFrameEndContext extends FrameEndContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode FOLLOWING() { return getToken(HqlParser.FOLLOWING, 0); }
		public ExpressionFollowingFrameEndContext(FrameEndContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterExpressionFollowingFrameEnd(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitExpressionFollowingFrameEnd(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class UnboundedFollowingFrameEndContext extends FrameEndContext {
		public TerminalNode UNBOUNDED() { return getToken(HqlParser.UNBOUNDED, 0); }
		public TerminalNode FOLLOWING() { return getToken(HqlParser.FOLLOWING, 0); }
		public UnboundedFollowingFrameEndContext(FrameEndContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterUnboundedFollowingFrameEnd(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitUnboundedFollowingFrameEnd(this);
		}
	}

	public final FrameEndContext frameEnd() throws RecognitionException {
		FrameEndContext _localctx = new FrameEndContext(_ctx, getState());
		enterRule(_localctx, 168, RULE_frameEnd);
		try {
			setState(987);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,115,_ctx) ) {
			case 1:
				_localctx = new ExpressionPrecedingFrameEndContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(977);
				expression(0);
				setState(978);
				match(PRECEDING);
				}
				break;
			case 2:
				_localctx = new CurrentRowFrameEndContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(980);
				match(CURRENT);
				setState(981);
				match(ROW);
				}
				break;
			case 3:
				_localctx = new ExpressionFollowingFrameEndContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(982);
				expression(0);
				setState(983);
				match(FOLLOWING);
				}
				break;
			case 4:
				_localctx = new UnboundedFollowingFrameEndContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(985);
				match(UNBOUNDED);
				setState(986);
				match(FOLLOWING);
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
	public static class CastFunctionContext extends ParserRuleContext {
		public TerminalNode CAST() { return getToken(HqlParser.CAST, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode AS() { return getToken(HqlParser.AS, 0); }
		public CastTargetContext castTarget() {
			return getRuleContext(CastTargetContext.class,0);
		}
		public CastFunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_castFunction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCastFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCastFunction(this);
		}
	}

	public final CastFunctionContext castFunction() throws RecognitionException {
		CastFunctionContext _localctx = new CastFunctionContext(_ctx, getState());
		enterRule(_localctx, 170, RULE_castFunction);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(989);
			match(CAST);
			setState(990);
			match(T__1);
			setState(991);
			expression(0);
			setState(992);
			match(AS);
			setState(993);
			castTarget();
			setState(994);
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
	public static class CastTargetContext extends ParserRuleContext {
		public CastTargetTypeContext castTargetType() {
			return getRuleContext(CastTargetTypeContext.class,0);
		}
		public List<TerminalNode> INTEGER_LITERAL() { return getTokens(HqlParser.INTEGER_LITERAL); }
		public TerminalNode INTEGER_LITERAL(int i) {
			return getToken(HqlParser.INTEGER_LITERAL, i);
		}
		public CastTargetContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_castTarget; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCastTarget(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCastTarget(this);
		}
	}

	public final CastTargetContext castTarget() throws RecognitionException {
		CastTargetContext _localctx = new CastTargetContext(_ctx, getState());
		enterRule(_localctx, 172, RULE_castTarget);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(996);
			castTargetType();
			setState(1004);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__1) {
				{
				setState(997);
				match(T__1);
				setState(998);
				match(INTEGER_LITERAL);
				setState(1001);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__0) {
					{
					setState(999);
					match(T__0);
					setState(1000);
					match(INTEGER_LITERAL);
					}
				}

				setState(1003);
				match(T__2);
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
	public static class CastTargetTypeContext extends ParserRuleContext {
		public String fullTargetName;
		public IdentifierContext i;
		public IdentifierContext c;
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public CastTargetTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_castTargetType; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCastTargetType(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCastTargetType(this);
		}
	}

	public final CastTargetTypeContext castTargetType() throws RecognitionException {
		CastTargetTypeContext _localctx = new CastTargetTypeContext(_ctx, getState());
		enterRule(_localctx, 174, RULE_castTargetType);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(1006);
			((CastTargetTypeContext)_localctx).i = identifier();
			 ((CastTargetTypeContext)_localctx).fullTargetName =  _localctx.i.getText(); 
			}
			setState(1015);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(1009);
				match(T__13);
				setState(1010);
				((CastTargetTypeContext)_localctx).c = identifier();
				 _localctx.fullTargetName += ("." + _localctx.c.getText() ); 
				}
				}
				setState(1017);
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
	public static class ExtractFunctionContext extends ParserRuleContext {
		public TerminalNode EXTRACT() { return getToken(HqlParser.EXTRACT, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode FROM() { return getToken(HqlParser.FROM, 0); }
		public DateTimeFunctionContext dateTimeFunction() {
			return getRuleContext(DateTimeFunctionContext.class,0);
		}
		public ExtractFunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_extractFunction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterExtractFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitExtractFunction(this);
		}
	}

	public final ExtractFunctionContext extractFunction() throws RecognitionException {
		ExtractFunctionContext _localctx = new ExtractFunctionContext(_ctx, getState());
		enterRule(_localctx, 176, RULE_extractFunction);
		try {
			setState(1030);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case EXTRACT:
				enterOuterAlt(_localctx, 1);
				{
				setState(1018);
				match(EXTRACT);
				setState(1019);
				match(T__1);
				setState(1020);
				expression(0);
				setState(1021);
				match(FROM);
				setState(1022);
				expression(0);
				setState(1023);
				match(T__2);
				}
				break;
			case DAY:
			case EPOCH:
			case HOUR:
			case MINUTE:
			case MONTH:
			case NANOSECOND:
			case QUARTER:
			case SECOND:
			case WEEK:
			case YEAR:
				enterOuterAlt(_localctx, 2);
				{
				setState(1025);
				dateTimeFunction();
				setState(1026);
				match(T__1);
				setState(1027);
				expression(0);
				setState(1028);
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
	public static class TrimFunctionContext extends ParserRuleContext {
		public TerminalNode TRIM() { return getToken(HqlParser.TRIM, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public StringLiteralContext stringLiteral() {
			return getRuleContext(StringLiteralContext.class,0);
		}
		public TerminalNode FROM() { return getToken(HqlParser.FROM, 0); }
		public TerminalNode LEADING() { return getToken(HqlParser.LEADING, 0); }
		public TerminalNode TRAILING() { return getToken(HqlParser.TRAILING, 0); }
		public TerminalNode BOTH() { return getToken(HqlParser.BOTH, 0); }
		public TrimFunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_trimFunction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterTrimFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitTrimFunction(this);
		}
	}

	public final TrimFunctionContext trimFunction() throws RecognitionException {
		TrimFunctionContext _localctx = new TrimFunctionContext(_ctx, getState());
		enterRule(_localctx, 178, RULE_trimFunction);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1032);
			match(TRIM);
			setState(1033);
			match(T__1);
			setState(1035);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,120,_ctx) ) {
			case 1:
				{
				setState(1034);
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
				break;
			}
			setState(1038);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,121,_ctx) ) {
			case 1:
				{
				setState(1037);
				stringLiteral();
				}
				break;
			}
			setState(1041);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,122,_ctx) ) {
			case 1:
				{
				setState(1040);
				match(FROM);
				}
				break;
			}
			setState(1043);
			expression(0);
			setState(1044);
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
	public static class DateTimeFunctionContext extends ParserRuleContext {
		public Token d;
		public TerminalNode YEAR() { return getToken(HqlParser.YEAR, 0); }
		public TerminalNode MONTH() { return getToken(HqlParser.MONTH, 0); }
		public TerminalNode DAY() { return getToken(HqlParser.DAY, 0); }
		public TerminalNode WEEK() { return getToken(HqlParser.WEEK, 0); }
		public TerminalNode QUARTER() { return getToken(HqlParser.QUARTER, 0); }
		public TerminalNode HOUR() { return getToken(HqlParser.HOUR, 0); }
		public TerminalNode MINUTE() { return getToken(HqlParser.MINUTE, 0); }
		public TerminalNode SECOND() { return getToken(HqlParser.SECOND, 0); }
		public TerminalNode NANOSECOND() { return getToken(HqlParser.NANOSECOND, 0); }
		public TerminalNode EPOCH() { return getToken(HqlParser.EPOCH, 0); }
		public DateTimeFunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dateTimeFunction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterDateTimeFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitDateTimeFunction(this);
		}
	}

	public final DateTimeFunctionContext dateTimeFunction() throws RecognitionException {
		DateTimeFunctionContext _localctx = new DateTimeFunctionContext(_ctx, getState());
		enterRule(_localctx, 180, RULE_dateTimeFunction);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1046);
			((DateTimeFunctionContext)_localctx).d = _input.LT(1);
			_la = _input.LA(1);
			if ( !(_la==DAY || _la==EPOCH || ((((_la - 87)) & ~0x3f) == 0 && ((1L << (_la - 87)) & 3848290697217L) != 0) || ((((_la - 156)) & ~0x3f) == 0 && ((1L << (_la - 156)) & 8933531975937L) != 0)) ) {
				((DateTimeFunctionContext)_localctx).d = (Token)_errHandler.recoverInline(this);
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
	public static class EveryFunctionContext extends ParserRuleContext {
		public Token every;
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public TerminalNode EVERY() { return getToken(HqlParser.EVERY, 0); }
		public TerminalNode ALL() { return getToken(HqlParser.ALL, 0); }
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public SimplePathContext simplePath() {
			return getRuleContext(SimplePathContext.class,0);
		}
		public TerminalNode ELEMENTS() { return getToken(HqlParser.ELEMENTS, 0); }
		public TerminalNode INDICES() { return getToken(HqlParser.INDICES, 0); }
		public EveryFunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_everyFunction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterEveryFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitEveryFunction(this);
		}
	}

	public final EveryFunctionContext everyFunction() throws RecognitionException {
		EveryFunctionContext _localctx = new EveryFunctionContext(_ctx, getState());
		enterRule(_localctx, 182, RULE_everyFunction);
		int _la;
		try {
			setState(1064);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,123,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1048);
				((EveryFunctionContext)_localctx).every = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==ALL || _la==EVERY) ) {
					((EveryFunctionContext)_localctx).every = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(1049);
				match(T__1);
				setState(1050);
				predicate(0);
				setState(1051);
				match(T__2);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1053);
				((EveryFunctionContext)_localctx).every = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==ALL || _la==EVERY) ) {
					((EveryFunctionContext)_localctx).every = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(1054);
				match(T__1);
				setState(1055);
				subquery();
				setState(1056);
				match(T__2);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1058);
				((EveryFunctionContext)_localctx).every = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==ALL || _la==EVERY) ) {
					((EveryFunctionContext)_localctx).every = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(1059);
				_la = _input.LA(1);
				if ( !(_la==ELEMENTS || _la==INDICES) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(1060);
				match(T__1);
				setState(1061);
				simplePath();
				setState(1062);
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
	public static class AnyFunctionContext extends ParserRuleContext {
		public Token any;
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public TerminalNode ANY() { return getToken(HqlParser.ANY, 0); }
		public TerminalNode SOME() { return getToken(HqlParser.SOME, 0); }
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public SimplePathContext simplePath() {
			return getRuleContext(SimplePathContext.class,0);
		}
		public TerminalNode ELEMENTS() { return getToken(HqlParser.ELEMENTS, 0); }
		public TerminalNode INDICES() { return getToken(HqlParser.INDICES, 0); }
		public AnyFunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_anyFunction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterAnyFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitAnyFunction(this);
		}
	}

	public final AnyFunctionContext anyFunction() throws RecognitionException {
		AnyFunctionContext _localctx = new AnyFunctionContext(_ctx, getState());
		enterRule(_localctx, 184, RULE_anyFunction);
		int _la;
		try {
			setState(1082);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,124,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1066);
				((AnyFunctionContext)_localctx).any = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==ANY || _la==SOME) ) {
					((AnyFunctionContext)_localctx).any = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(1067);
				match(T__1);
				setState(1068);
				predicate(0);
				setState(1069);
				match(T__2);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1071);
				((AnyFunctionContext)_localctx).any = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==ANY || _la==SOME) ) {
					((AnyFunctionContext)_localctx).any = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(1072);
				match(T__1);
				setState(1073);
				subquery();
				setState(1074);
				match(T__2);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1076);
				((AnyFunctionContext)_localctx).any = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==ANY || _la==SOME) ) {
					((AnyFunctionContext)_localctx).any = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(1077);
				_la = _input.LA(1);
				if ( !(_la==ELEMENTS || _la==INDICES) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(1078);
				match(T__1);
				setState(1079);
				simplePath();
				setState(1080);
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
	public static class TreatedPathContext extends ParserRuleContext {
		public TerminalNode TREAT() { return getToken(HqlParser.TREAT, 0); }
		public PathContext path() {
			return getRuleContext(PathContext.class,0);
		}
		public TerminalNode AS() { return getToken(HqlParser.AS, 0); }
		public SimplePathContext simplePath() {
			return getRuleContext(SimplePathContext.class,0);
		}
		public PathContinutationContext pathContinutation() {
			return getRuleContext(PathContinutationContext.class,0);
		}
		public TreatedPathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_treatedPath; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterTreatedPath(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitTreatedPath(this);
		}
	}

	public final TreatedPathContext treatedPath() throws RecognitionException {
		TreatedPathContext _localctx = new TreatedPathContext(_ctx, getState());
		enterRule(_localctx, 186, RULE_treatedPath);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1084);
			match(TREAT);
			setState(1085);
			match(T__1);
			setState(1086);
			path();
			setState(1087);
			match(AS);
			setState(1088);
			simplePath();
			setState(1089);
			match(T__2);
			setState(1091);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,125,_ctx) ) {
			case 1:
				{
				setState(1090);
				pathContinutation();
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
	public static class PathContinutationContext extends ParserRuleContext {
		public SimplePathContext simplePath() {
			return getRuleContext(SimplePathContext.class,0);
		}
		public PathContinutationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pathContinutation; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterPathContinutation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitPathContinutation(this);
		}
	}

	public final PathContinutationContext pathContinutation() throws RecognitionException {
		PathContinutationContext _localctx = new PathContinutationContext(_ctx, getState());
		enterRule(_localctx, 188, RULE_pathContinutation);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1093);
			match(T__13);
			setState(1094);
			simplePath();
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
	public static class PredicateContext extends ParserRuleContext {
		public PredicateContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_predicate; }
	 
		public PredicateContext() { }
		public void copyFrom(PredicateContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NullExpressionPredicateContext extends PredicateContext {
		public DealingWithNullExpressionContext dealingWithNullExpression() {
			return getRuleContext(DealingWithNullExpressionContext.class,0);
		}
		public NullExpressionPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterNullExpressionPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitNullExpressionPredicate(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BetweenPredicateContext extends PredicateContext {
		public BetweenExpressionContext betweenExpression() {
			return getRuleContext(BetweenExpressionContext.class,0);
		}
		public BetweenPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterBetweenPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitBetweenPredicate(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class OrPredicateContext extends PredicateContext {
		public List<PredicateContext> predicate() {
			return getRuleContexts(PredicateContext.class);
		}
		public PredicateContext predicate(int i) {
			return getRuleContext(PredicateContext.class,i);
		}
		public TerminalNode OR() { return getToken(HqlParser.OR, 0); }
		public OrPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterOrPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitOrPredicate(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class RelationalPredicateContext extends PredicateContext {
		public RelationalExpressionContext relationalExpression() {
			return getRuleContext(RelationalExpressionContext.class,0);
		}
		public RelationalPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterRelationalPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitRelationalPredicate(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExistsPredicateContext extends PredicateContext {
		public ExistsExpressionContext existsExpression() {
			return getRuleContext(ExistsExpressionContext.class,0);
		}
		public ExistsPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterExistsPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitExistsPredicate(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CollectionPredicateContext extends PredicateContext {
		public CollectionExpressionContext collectionExpression() {
			return getRuleContext(CollectionExpressionContext.class,0);
		}
		public CollectionPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCollectionPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCollectionPredicate(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AndPredicateContext extends PredicateContext {
		public List<PredicateContext> predicate() {
			return getRuleContexts(PredicateContext.class);
		}
		public PredicateContext predicate(int i) {
			return getRuleContext(PredicateContext.class,i);
		}
		public TerminalNode AND() { return getToken(HqlParser.AND, 0); }
		public AndPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterAndPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitAndPredicate(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class GroupedPredicateContext extends PredicateContext {
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public GroupedPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterGroupedPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitGroupedPredicate(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class LikePredicateContext extends PredicateContext {
		public StringPatternMatchingContext stringPatternMatching() {
			return getRuleContext(StringPatternMatchingContext.class,0);
		}
		public LikePredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterLikePredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitLikePredicate(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class InPredicateContext extends PredicateContext {
		public InExpressionContext inExpression() {
			return getRuleContext(InExpressionContext.class,0);
		}
		public InPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterInPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitInPredicate(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NotPredicateContext extends PredicateContext {
		public TerminalNode NOT() { return getToken(HqlParser.NOT, 0); }
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public NotPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterNotPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitNotPredicate(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionPredicateContext extends PredicateContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ExpressionPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterExpressionPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitExpressionPredicate(this);
		}
	}

	public final PredicateContext predicate() throws RecognitionException {
		return predicate(0);
	}

	private PredicateContext predicate(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		PredicateContext _localctx = new PredicateContext(_ctx, _parentState);
		PredicateContext _prevctx = _localctx;
		int _startState = 190;
		enterRecursionRule(_localctx, 190, RULE_predicate, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1111);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,126,_ctx) ) {
			case 1:
				{
				_localctx = new GroupedPredicateContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(1097);
				match(T__1);
				setState(1098);
				predicate(0);
				setState(1099);
				match(T__2);
				}
				break;
			case 2:
				{
				_localctx = new NullExpressionPredicateContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(1101);
				dealingWithNullExpression();
				}
				break;
			case 3:
				{
				_localctx = new InPredicateContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(1102);
				inExpression();
				}
				break;
			case 4:
				{
				_localctx = new BetweenPredicateContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(1103);
				betweenExpression();
				}
				break;
			case 5:
				{
				_localctx = new RelationalPredicateContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(1104);
				relationalExpression();
				}
				break;
			case 6:
				{
				_localctx = new LikePredicateContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(1105);
				stringPatternMatching();
				}
				break;
			case 7:
				{
				_localctx = new ExistsPredicateContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(1106);
				existsExpression();
				}
				break;
			case 8:
				{
				_localctx = new CollectionPredicateContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(1107);
				collectionExpression();
				}
				break;
			case 9:
				{
				_localctx = new NotPredicateContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(1108);
				match(NOT);
				setState(1109);
				predicate(4);
				}
				break;
			case 10:
				{
				_localctx = new ExpressionPredicateContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(1110);
				expression(0);
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(1121);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,128,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(1119);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,127,_ctx) ) {
					case 1:
						{
						_localctx = new AndPredicateContext(new PredicateContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_predicate);
						setState(1113);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(1114);
						match(AND);
						setState(1115);
						predicate(4);
						}
						break;
					case 2:
						{
						_localctx = new OrPredicateContext(new PredicateContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_predicate);
						setState(1116);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(1117);
						match(OR);
						setState(1118);
						predicate(3);
						}
						break;
					}
					} 
				}
				setState(1123);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,128,_ctx);
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
	public static class ExpressionOrPredicateContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public ExpressionOrPredicateContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expressionOrPredicate; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterExpressionOrPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitExpressionOrPredicate(this);
		}
	}

	public final ExpressionOrPredicateContext expressionOrPredicate() throws RecognitionException {
		ExpressionOrPredicateContext _localctx = new ExpressionOrPredicateContext(_ctx, getState());
		enterRule(_localctx, 192, RULE_expressionOrPredicate);
		try {
			setState(1126);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,129,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1124);
				expression(0);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1125);
				predicate(0);
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
	public static class RelationalExpressionContext extends ParserRuleContext {
		public Token op;
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public RelationalExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_relationalExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterRelationalExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitRelationalExpression(this);
		}
	}

	public final RelationalExpressionContext relationalExpression() throws RecognitionException {
		RelationalExpressionContext _localctx = new RelationalExpressionContext(_ctx, getState());
		enterRule(_localctx, 194, RULE_relationalExpression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1128);
			expression(0);
			setState(1129);
			((RelationalExpressionContext)_localctx).op = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 4161552L) != 0)) ) {
				((RelationalExpressionContext)_localctx).op = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(1130);
			expression(0);
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
	public static class BetweenExpressionContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode BETWEEN() { return getToken(HqlParser.BETWEEN, 0); }
		public TerminalNode AND() { return getToken(HqlParser.AND, 0); }
		public TerminalNode NOT() { return getToken(HqlParser.NOT, 0); }
		public BetweenExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_betweenExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterBetweenExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitBetweenExpression(this);
		}
	}

	public final BetweenExpressionContext betweenExpression() throws RecognitionException {
		BetweenExpressionContext _localctx = new BetweenExpressionContext(_ctx, getState());
		enterRule(_localctx, 196, RULE_betweenExpression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1132);
			expression(0);
			setState(1134);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NOT) {
				{
				setState(1133);
				match(NOT);
				}
			}

			setState(1136);
			match(BETWEEN);
			setState(1137);
			expression(0);
			setState(1138);
			match(AND);
			setState(1139);
			expression(0);
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
	public static class DealingWithNullExpressionContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode IS() { return getToken(HqlParser.IS, 0); }
		public TerminalNode NULL() { return getToken(HqlParser.NULL, 0); }
		public TerminalNode NOT() { return getToken(HqlParser.NOT, 0); }
		public TerminalNode DISTINCT() { return getToken(HqlParser.DISTINCT, 0); }
		public TerminalNode FROM() { return getToken(HqlParser.FROM, 0); }
		public DealingWithNullExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dealingWithNullExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterDealingWithNullExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitDealingWithNullExpression(this);
		}
	}

	public final DealingWithNullExpressionContext dealingWithNullExpression() throws RecognitionException {
		DealingWithNullExpressionContext _localctx = new DealingWithNullExpressionContext(_ctx, getState());
		enterRule(_localctx, 198, RULE_dealingWithNullExpression);
		int _la;
		try {
			setState(1157);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,133,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1141);
				expression(0);
				setState(1142);
				match(IS);
				setState(1144);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(1143);
					match(NOT);
					}
				}

				setState(1146);
				match(NULL);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1148);
				expression(0);
				setState(1149);
				match(IS);
				setState(1151);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(1150);
					match(NOT);
					}
				}

				setState(1153);
				match(DISTINCT);
				setState(1154);
				match(FROM);
				setState(1155);
				expression(0);
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
	public static class StringPatternMatchingContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode LIKE() { return getToken(HqlParser.LIKE, 0); }
		public TerminalNode ILIKE() { return getToken(HqlParser.ILIKE, 0); }
		public TerminalNode NOT() { return getToken(HqlParser.NOT, 0); }
		public TerminalNode ESCAPE() { return getToken(HqlParser.ESCAPE, 0); }
		public StringLiteralContext stringLiteral() {
			return getRuleContext(StringLiteralContext.class,0);
		}
		public ParameterContext parameter() {
			return getRuleContext(ParameterContext.class,0);
		}
		public StringPatternMatchingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stringPatternMatching; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterStringPatternMatching(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitStringPatternMatching(this);
		}
	}

	public final StringPatternMatchingContext stringPatternMatching() throws RecognitionException {
		StringPatternMatchingContext _localctx = new StringPatternMatchingContext(_ctx, getState());
		enterRule(_localctx, 200, RULE_stringPatternMatching);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1159);
			expression(0);
			setState(1161);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NOT) {
				{
				setState(1160);
				match(NOT);
				}
			}

			setState(1163);
			_la = _input.LA(1);
			if ( !(_la==ILIKE || _la==LIKE) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(1165);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(1164);
				match(T__4);
				}
			}

			setState(1167);
			expression(0);
			setState(1169);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,136,_ctx) ) {
			case 1:
				{
				setState(1168);
				match(T__4);
				}
				break;
			}
			setState(1176);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,138,_ctx) ) {
			case 1:
				{
				setState(1171);
				match(ESCAPE);
				setState(1174);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case CHARACTER:
				case STRINGLITERAL:
				case JAVASTRINGLITERAL:
					{
					setState(1172);
					stringLiteral();
					}
					break;
				case T__21:
				case T__22:
					{
					setState(1173);
					parameter();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
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
	public static class InExpressionContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode IN() { return getToken(HqlParser.IN, 0); }
		public InListContext inList() {
			return getRuleContext(InListContext.class,0);
		}
		public TerminalNode NOT() { return getToken(HqlParser.NOT, 0); }
		public InExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterInExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitInExpression(this);
		}
	}

	public final InExpressionContext inExpression() throws RecognitionException {
		InExpressionContext _localctx = new InExpressionContext(_ctx, getState());
		enterRule(_localctx, 202, RULE_inExpression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1178);
			expression(0);
			setState(1180);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NOT) {
				{
				setState(1179);
				match(NOT);
				}
			}

			setState(1182);
			match(IN);
			setState(1183);
			inList();
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
	public static class InListContext extends ParserRuleContext {
		public SimplePathContext simplePath() {
			return getRuleContext(SimplePathContext.class,0);
		}
		public TerminalNode ELEMENTS() { return getToken(HqlParser.ELEMENTS, 0); }
		public TerminalNode INDICES() { return getToken(HqlParser.INDICES, 0); }
		public SubqueryContext subquery() {
			return getRuleContext(SubqueryContext.class,0);
		}
		public ParameterContext parameter() {
			return getRuleContext(ParameterContext.class,0);
		}
		public List<ExpressionOrPredicateContext> expressionOrPredicate() {
			return getRuleContexts(ExpressionOrPredicateContext.class);
		}
		public ExpressionOrPredicateContext expressionOrPredicate(int i) {
			return getRuleContext(ExpressionOrPredicateContext.class,i);
		}
		public InListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterInList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitInList(this);
		}
	}

	public final InListContext inList() throws RecognitionException {
		InListContext _localctx = new InListContext(_ctx, getState());
		enterRule(_localctx, 204, RULE_inList);
		int _la;
		try {
			setState(1207);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,142,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1185);
				_la = _input.LA(1);
				if ( !(_la==ELEMENTS || _la==INDICES) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(1186);
				match(T__1);
				setState(1187);
				simplePath();
				setState(1188);
				match(T__2);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1190);
				match(T__1);
				setState(1191);
				subquery();
				setState(1192);
				match(T__2);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1194);
				parameter();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1195);
				match(T__1);
				setState(1204);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & -275972619452L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & -70918499995713L) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & -1L) != 0) || ((((_la - 192)) & ~0x3f) == 0 && ((1L << (_la - 192)) & 65535L) != 0)) {
					{
					setState(1196);
					expressionOrPredicate();
					setState(1201);
					_errHandler.sync(this);
					_la = _input.LA(1);
					while (_la==T__0) {
						{
						{
						setState(1197);
						match(T__0);
						setState(1198);
						expressionOrPredicate();
						}
						}
						setState(1203);
						_errHandler.sync(this);
						_la = _input.LA(1);
					}
					}
				}

				setState(1206);
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
	public static class ExistsExpressionContext extends ParserRuleContext {
		public TerminalNode EXISTS() { return getToken(HqlParser.EXISTS, 0); }
		public SimplePathContext simplePath() {
			return getRuleContext(SimplePathContext.class,0);
		}
		public TerminalNode ELEMENTS() { return getToken(HqlParser.ELEMENTS, 0); }
		public TerminalNode INDICES() { return getToken(HqlParser.INDICES, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ExistsExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_existsExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterExistsExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitExistsExpression(this);
		}
	}

	public final ExistsExpressionContext existsExpression() throws RecognitionException {
		ExistsExpressionContext _localctx = new ExistsExpressionContext(_ctx, getState());
		enterRule(_localctx, 206, RULE_existsExpression);
		int _la;
		try {
			setState(1217);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,143,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1209);
				match(EXISTS);
				setState(1210);
				_la = _input.LA(1);
				if ( !(_la==ELEMENTS || _la==INDICES) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(1211);
				match(T__1);
				setState(1212);
				simplePath();
				setState(1213);
				match(T__2);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1215);
				match(EXISTS);
				setState(1216);
				expression(0);
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
	public static class CollectionExpressionContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode IS() { return getToken(HqlParser.IS, 0); }
		public TerminalNode EMPTY() { return getToken(HqlParser.EMPTY, 0); }
		public TerminalNode NOT() { return getToken(HqlParser.NOT, 0); }
		public TerminalNode MEMBER() { return getToken(HqlParser.MEMBER, 0); }
		public TerminalNode OF() { return getToken(HqlParser.OF, 0); }
		public PathContext path() {
			return getRuleContext(PathContext.class,0);
		}
		public CollectionExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_collectionExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCollectionExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCollectionExpression(this);
		}
	}

	public final CollectionExpressionContext collectionExpression() throws RecognitionException {
		CollectionExpressionContext _localctx = new CollectionExpressionContext(_ctx, getState());
		enterRule(_localctx, 208, RULE_collectionExpression);
		int _la;
		try {
			setState(1234);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,146,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1219);
				expression(0);
				setState(1220);
				match(IS);
				setState(1222);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(1221);
					match(NOT);
					}
				}

				setState(1224);
				match(EMPTY);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1226);
				expression(0);
				setState(1228);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(1227);
					match(NOT);
					}
				}

				setState(1230);
				match(MEMBER);
				setState(1231);
				match(OF);
				setState(1232);
				path();
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
	public static class InstantiationTargetContext extends ParserRuleContext {
		public TerminalNode LIST() { return getToken(HqlParser.LIST, 0); }
		public TerminalNode MAP() { return getToken(HqlParser.MAP, 0); }
		public SimplePathContext simplePath() {
			return getRuleContext(SimplePathContext.class,0);
		}
		public InstantiationTargetContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_instantiationTarget; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterInstantiationTarget(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitInstantiationTarget(this);
		}
	}

	public final InstantiationTargetContext instantiationTarget() throws RecognitionException {
		InstantiationTargetContext _localctx = new InstantiationTargetContext(_ctx, getState());
		enterRule(_localctx, 210, RULE_instantiationTarget);
		try {
			setState(1239);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,147,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1236);
				match(LIST);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1237);
				match(MAP);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1238);
				simplePath();
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
	public static class InstantiationArgumentsContext extends ParserRuleContext {
		public List<InstantiationArgumentContext> instantiationArgument() {
			return getRuleContexts(InstantiationArgumentContext.class);
		}
		public InstantiationArgumentContext instantiationArgument(int i) {
			return getRuleContext(InstantiationArgumentContext.class,i);
		}
		public InstantiationArgumentsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_instantiationArguments; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterInstantiationArguments(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitInstantiationArguments(this);
		}
	}

	public final InstantiationArgumentsContext instantiationArguments() throws RecognitionException {
		InstantiationArgumentsContext _localctx = new InstantiationArgumentsContext(_ctx, getState());
		enterRule(_localctx, 212, RULE_instantiationArguments);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1241);
			instantiationArgument();
			setState(1246);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__0) {
				{
				{
				setState(1242);
				match(T__0);
				setState(1243);
				instantiationArgument();
				}
				}
				setState(1248);
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
	public static class InstantiationArgumentContext extends ParserRuleContext {
		public ExpressionOrPredicateContext expressionOrPredicate() {
			return getRuleContext(ExpressionOrPredicateContext.class,0);
		}
		public InstantiationContext instantiation() {
			return getRuleContext(InstantiationContext.class,0);
		}
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public InstantiationArgumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_instantiationArgument; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterInstantiationArgument(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitInstantiationArgument(this);
		}
	}

	public final InstantiationArgumentContext instantiationArgument() throws RecognitionException {
		InstantiationArgumentContext _localctx = new InstantiationArgumentContext(_ctx, getState());
		enterRule(_localctx, 214, RULE_instantiationArgument);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1251);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,149,_ctx) ) {
			case 1:
				{
				setState(1249);
				expressionOrPredicate();
				}
				break;
			case 2:
				{
				setState(1250);
				instantiation();
				}
				break;
			}
			setState(1254);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 25)) & ~0x3f) == 0 && ((1L << (_la - 25)) & -2427721674137633L) != 0) || ((((_la - 89)) & ~0x3f) == 0 && ((1L << (_la - 89)) & -35184374202369L) != 0) || ((((_la - 153)) & ~0x3f) == 0 && ((1L << (_la - 153)) & 18155135729401855L) != 0)) {
				{
				setState(1253);
				variable();
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
	public static class ParameterOrIntegerLiteralContext extends ParserRuleContext {
		public ParameterContext parameter() {
			return getRuleContext(ParameterContext.class,0);
		}
		public TerminalNode INTEGER_LITERAL() { return getToken(HqlParser.INTEGER_LITERAL, 0); }
		public ParameterOrIntegerLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameterOrIntegerLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterParameterOrIntegerLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitParameterOrIntegerLiteral(this);
		}
	}

	public final ParameterOrIntegerLiteralContext parameterOrIntegerLiteral() throws RecognitionException {
		ParameterOrIntegerLiteralContext _localctx = new ParameterOrIntegerLiteralContext(_ctx, getState());
		enterRule(_localctx, 216, RULE_parameterOrIntegerLiteral);
		try {
			setState(1258);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__21:
			case T__22:
				enterOuterAlt(_localctx, 1);
				{
				setState(1256);
				parameter();
				}
				break;
			case INTEGER_LITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(1257);
				match(INTEGER_LITERAL);
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
	public static class ParameterOrNumberLiteralContext extends ParserRuleContext {
		public ParameterContext parameter() {
			return getRuleContext(ParameterContext.class,0);
		}
		public NumericLiteralContext numericLiteral() {
			return getRuleContext(NumericLiteralContext.class,0);
		}
		public ParameterOrNumberLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameterOrNumberLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterParameterOrNumberLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitParameterOrNumberLiteral(this);
		}
	}

	public final ParameterOrNumberLiteralContext parameterOrNumberLiteral() throws RecognitionException {
		ParameterOrNumberLiteralContext _localctx = new ParameterOrNumberLiteralContext(_ctx, getState());
		enterRule(_localctx, 218, RULE_parameterOrNumberLiteral);
		try {
			setState(1262);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__21:
			case T__22:
				enterOuterAlt(_localctx, 1);
				{
				setState(1260);
				parameter();
				}
				break;
			case INTEGER_LITERAL:
			case FLOAT_LITERAL:
			case HEXLITERAL:
				enterOuterAlt(_localctx, 2);
				{
				setState(1261);
				numericLiteral();
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
	public static class VariableContext extends ParserRuleContext {
		public TerminalNode AS() { return getToken(HqlParser.AS, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ReservedWordContext reservedWord() {
			return getRuleContext(ReservedWordContext.class,0);
		}
		public VariableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterVariable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitVariable(this);
		}
	}

	public final VariableContext variable() throws RecognitionException {
		VariableContext _localctx = new VariableContext(_ctx, getState());
		enterRule(_localctx, 220, RULE_variable);
		try {
			setState(1267);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,153,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1264);
				match(AS);
				setState(1265);
				identifier();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1266);
				reservedWord();
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
	public static class ParameterContext extends ParserRuleContext {
		public Token prefix;
		public TerminalNode SPEL() { return getToken(HqlParser.SPEL, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode INTEGER_LITERAL() { return getToken(HqlParser.INTEGER_LITERAL, 0); }
		public ParameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameter; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterParameter(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitParameter(this);
		}
	}

	public final ParameterContext parameter() throws RecognitionException {
		ParameterContext _localctx = new ParameterContext(_ctx, getState());
		enterRule(_localctx, 222, RULE_parameter);
		try {
			setState(1279);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,155,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1269);
				((ParameterContext)_localctx).prefix = match(T__21);
				setState(1270);
				match(SPEL);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1271);
				((ParameterContext)_localctx).prefix = match(T__21);
				setState(1272);
				identifier();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1273);
				((ParameterContext)_localctx).prefix = match(T__22);
				setState(1274);
				match(SPEL);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1275);
				((ParameterContext)_localctx).prefix = match(T__22);
				setState(1277);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,154,_ctx) ) {
				case 1:
					{
					setState(1276);
					match(INTEGER_LITERAL);
					}
					break;
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
	public static class EntityNameContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public TerminalNode SPEL() { return getToken(HqlParser.SPEL, 0); }
		public EntityNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_entityName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterEntityName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitEntityName(this);
		}
	}

	public final EntityNameContext entityName() throws RecognitionException {
		EntityNameContext _localctx = new EntityNameContext(_ctx, getState());
		enterRule(_localctx, 224, RULE_entityName);
		int _la;
		try {
			setState(1290);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ALL:
			case AND:
			case ANY:
			case AS:
			case ASC:
			case AVG:
			case BETWEEN:
			case BOTH:
			case BREADTH:
			case BY:
			case CASE:
			case CAST:
			case COLLATE:
			case COUNT:
			case CROSS:
			case CUBE:
			case CURRENT:
			case CURRENT_DATE:
			case CURRENT_INSTANT:
			case CURRENT_TIME:
			case CURRENT_TIMESTAMP:
			case CYCLE:
			case DATE:
			case DATETIME:
			case DAY:
			case DEFAULT:
			case DELETE:
			case DEPTH:
			case DESC:
			case DISTINCT:
			case ELEMENT:
			case ELEMENTS:
			case ELSE:
			case EMPTY:
			case END:
			case ENTRY:
			case EPOCH:
			case ERROR:
			case ESCAPE:
			case EVERY:
			case EXCEPT:
			case EXCLUDE:
			case EXISTS:
			case EXTRACT:
			case FETCH:
			case FILTER:
			case FIRST:
			case FLOOR:
			case FOLLOWING:
			case FOR:
			case FORMAT:
			case FROM:
			case FULL:
			case FUNCTION:
			case GROUP:
			case GROUPS:
			case HAVING:
			case HOUR:
			case ID:
			case IGNORE:
			case ILIKE:
			case IN:
			case INDEX:
			case INDICES:
			case INNER:
			case INSERT:
			case INSTANT:
			case INTERSECT:
			case INTO:
			case IS:
			case JOIN:
			case KEY:
			case LAST:
			case LEADING:
			case LEFT:
			case LIKE:
			case LIMIT:
			case LIST:
			case LISTAGG:
			case LOCAL:
			case LOCAL_DATE:
			case LOCAL_DATETIME:
			case LOCAL_TIME:
			case MAP:
			case MATERIALIZED:
			case MAX:
			case MAXELEMENT:
			case MAXINDEX:
			case MEMBER:
			case MICROSECOND:
			case MILLISECOND:
			case MIN:
			case MINELEMENT:
			case MININDEX:
			case MINUTE:
			case MONTH:
			case NANOSECOND:
			case NATURALID:
			case NEW:
			case NEXT:
			case NO:
			case NOT:
			case NULLS:
			case OBJECT:
			case OF:
			case OFFSET:
			case OFFSET_DATETIME:
			case ON:
			case ONLY:
			case OR:
			case ORDER:
			case OTHERS:
			case OUTER:
			case OVER:
			case OVERFLOW:
			case OVERLAY:
			case PAD:
			case PARTITION:
			case PERCENT:
			case PLACING:
			case POSITION:
			case POWER:
			case PRECEDING:
			case QUARTER:
			case RANGE:
			case RESPECT:
			case RIGHT:
			case ROLLUP:
			case ROW:
			case ROWS:
			case SEARCH:
			case SECOND:
			case SELECT:
			case SET:
			case SIZE:
			case SOME:
			case SUBSTRING:
			case SUM:
			case THEN:
			case TIES:
			case TIME:
			case TIMESTAMP:
			case TIMEZONE_HOUR:
			case TIMEZONE_MINUTE:
			case TO:
			case TRAILING:
			case TREAT:
			case TRIM:
			case TRUNC:
			case TRUNCATE:
			case TYPE:
			case UNBOUNDED:
			case UNION:
			case UPDATE:
			case USING:
			case VALUE:
			case VALUES:
			case VERSION:
			case VERSIONED:
			case WEEK:
			case WHEN:
			case WHERE:
			case WITH:
			case WITHIN:
			case WITHOUT:
			case YEAR:
			case IDENTIFICATION_VARIABLE:
				enterOuterAlt(_localctx, 1);
				{
				setState(1281);
				identifier();
				setState(1286);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__13) {
					{
					{
					setState(1282);
					match(T__13);
					setState(1283);
					identifier();
					}
					}
					setState(1288);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				break;
			case SPEL:
				enterOuterAlt(_localctx, 2);
				{
				setState(1289);
				match(SPEL);
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
	public static class IdentifierContext extends ParserRuleContext {
		public ReservedWordContext reservedWord() {
			return getRuleContext(ReservedWordContext.class,0);
		}
		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterIdentifier(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitIdentifier(this);
		}
	}

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 226, RULE_identifier);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1292);
			reservedWord();
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
	public static class CharacterContext extends ParserRuleContext {
		public TerminalNode CHARACTER() { return getToken(HqlParser.CHARACTER, 0); }
		public CharacterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_character; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterCharacter(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitCharacter(this);
		}
	}

	public final CharacterContext character() throws RecognitionException {
		CharacterContext _localctx = new CharacterContext(_ctx, getState());
		enterRule(_localctx, 228, RULE_character);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1294);
			match(CHARACTER);
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
	public static class FunctionNameContext extends ParserRuleContext {
		public List<ReservedWordContext> reservedWord() {
			return getRuleContexts(ReservedWordContext.class);
		}
		public ReservedWordContext reservedWord(int i) {
			return getRuleContext(ReservedWordContext.class,i);
		}
		public FunctionNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterFunctionName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitFunctionName(this);
		}
	}

	public final FunctionNameContext functionName() throws RecognitionException {
		FunctionNameContext _localctx = new FunctionNameContext(_ctx, getState());
		enterRule(_localctx, 230, RULE_functionName);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1296);
			reservedWord();
			setState(1301);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(1297);
				match(T__13);
				setState(1298);
				reservedWord();
				}
				}
				setState(1303);
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
	public static class ReservedWordContext extends ParserRuleContext {
		public Token f;
		public TerminalNode IDENTIFICATION_VARIABLE() { return getToken(HqlParser.IDENTIFICATION_VARIABLE, 0); }
		public TerminalNode ALL() { return getToken(HqlParser.ALL, 0); }
		public TerminalNode AND() { return getToken(HqlParser.AND, 0); }
		public TerminalNode ANY() { return getToken(HqlParser.ANY, 0); }
		public TerminalNode AS() { return getToken(HqlParser.AS, 0); }
		public TerminalNode ASC() { return getToken(HqlParser.ASC, 0); }
		public TerminalNode AVG() { return getToken(HqlParser.AVG, 0); }
		public TerminalNode BETWEEN() { return getToken(HqlParser.BETWEEN, 0); }
		public TerminalNode BOTH() { return getToken(HqlParser.BOTH, 0); }
		public TerminalNode BREADTH() { return getToken(HqlParser.BREADTH, 0); }
		public TerminalNode BY() { return getToken(HqlParser.BY, 0); }
		public TerminalNode CASE() { return getToken(HqlParser.CASE, 0); }
		public TerminalNode CAST() { return getToken(HqlParser.CAST, 0); }
		public TerminalNode COLLATE() { return getToken(HqlParser.COLLATE, 0); }
		public TerminalNode COUNT() { return getToken(HqlParser.COUNT, 0); }
		public TerminalNode CROSS() { return getToken(HqlParser.CROSS, 0); }
		public TerminalNode CUBE() { return getToken(HqlParser.CUBE, 0); }
		public TerminalNode CURRENT() { return getToken(HqlParser.CURRENT, 0); }
		public TerminalNode CURRENT_DATE() { return getToken(HqlParser.CURRENT_DATE, 0); }
		public TerminalNode CURRENT_INSTANT() { return getToken(HqlParser.CURRENT_INSTANT, 0); }
		public TerminalNode CURRENT_TIME() { return getToken(HqlParser.CURRENT_TIME, 0); }
		public TerminalNode CURRENT_TIMESTAMP() { return getToken(HqlParser.CURRENT_TIMESTAMP, 0); }
		public TerminalNode CYCLE() { return getToken(HqlParser.CYCLE, 0); }
		public TerminalNode DATE() { return getToken(HqlParser.DATE, 0); }
		public TerminalNode DATETIME() { return getToken(HqlParser.DATETIME, 0); }
		public TerminalNode DAY() { return getToken(HqlParser.DAY, 0); }
		public TerminalNode DEFAULT() { return getToken(HqlParser.DEFAULT, 0); }
		public TerminalNode DELETE() { return getToken(HqlParser.DELETE, 0); }
		public TerminalNode DEPTH() { return getToken(HqlParser.DEPTH, 0); }
		public TerminalNode DESC() { return getToken(HqlParser.DESC, 0); }
		public TerminalNode DISTINCT() { return getToken(HqlParser.DISTINCT, 0); }
		public TerminalNode ELEMENT() { return getToken(HqlParser.ELEMENT, 0); }
		public TerminalNode ELEMENTS() { return getToken(HqlParser.ELEMENTS, 0); }
		public TerminalNode ELSE() { return getToken(HqlParser.ELSE, 0); }
		public TerminalNode EMPTY() { return getToken(HqlParser.EMPTY, 0); }
		public TerminalNode END() { return getToken(HqlParser.END, 0); }
		public TerminalNode ENTRY() { return getToken(HqlParser.ENTRY, 0); }
		public TerminalNode EPOCH() { return getToken(HqlParser.EPOCH, 0); }
		public TerminalNode ERROR() { return getToken(HqlParser.ERROR, 0); }
		public TerminalNode ESCAPE() { return getToken(HqlParser.ESCAPE, 0); }
		public TerminalNode EVERY() { return getToken(HqlParser.EVERY, 0); }
		public TerminalNode EXCEPT() { return getToken(HqlParser.EXCEPT, 0); }
		public TerminalNode EXCLUDE() { return getToken(HqlParser.EXCLUDE, 0); }
		public TerminalNode EXISTS() { return getToken(HqlParser.EXISTS, 0); }
		public TerminalNode EXTRACT() { return getToken(HqlParser.EXTRACT, 0); }
		public TerminalNode FETCH() { return getToken(HqlParser.FETCH, 0); }
		public TerminalNode FILTER() { return getToken(HqlParser.FILTER, 0); }
		public TerminalNode FIRST() { return getToken(HqlParser.FIRST, 0); }
		public TerminalNode FLOOR() { return getToken(HqlParser.FLOOR, 0); }
		public TerminalNode FOLLOWING() { return getToken(HqlParser.FOLLOWING, 0); }
		public TerminalNode FOR() { return getToken(HqlParser.FOR, 0); }
		public TerminalNode FORMAT() { return getToken(HqlParser.FORMAT, 0); }
		public TerminalNode FROM() { return getToken(HqlParser.FROM, 0); }
		public TerminalNode FULL() { return getToken(HqlParser.FULL, 0); }
		public TerminalNode FUNCTION() { return getToken(HqlParser.FUNCTION, 0); }
		public TerminalNode GROUP() { return getToken(HqlParser.GROUP, 0); }
		public TerminalNode GROUPS() { return getToken(HqlParser.GROUPS, 0); }
		public TerminalNode HAVING() { return getToken(HqlParser.HAVING, 0); }
		public TerminalNode HOUR() { return getToken(HqlParser.HOUR, 0); }
		public TerminalNode ID() { return getToken(HqlParser.ID, 0); }
		public TerminalNode IGNORE() { return getToken(HqlParser.IGNORE, 0); }
		public TerminalNode ILIKE() { return getToken(HqlParser.ILIKE, 0); }
		public TerminalNode IN() { return getToken(HqlParser.IN, 0); }
		public TerminalNode INDEX() { return getToken(HqlParser.INDEX, 0); }
		public TerminalNode INDICES() { return getToken(HqlParser.INDICES, 0); }
		public TerminalNode INNER() { return getToken(HqlParser.INNER, 0); }
		public TerminalNode INSERT() { return getToken(HqlParser.INSERT, 0); }
		public TerminalNode INSTANT() { return getToken(HqlParser.INSTANT, 0); }
		public TerminalNode INTERSECT() { return getToken(HqlParser.INTERSECT, 0); }
		public TerminalNode INTO() { return getToken(HqlParser.INTO, 0); }
		public TerminalNode IS() { return getToken(HqlParser.IS, 0); }
		public TerminalNode JOIN() { return getToken(HqlParser.JOIN, 0); }
		public TerminalNode KEY() { return getToken(HqlParser.KEY, 0); }
		public TerminalNode LAST() { return getToken(HqlParser.LAST, 0); }
		public TerminalNode LEADING() { return getToken(HqlParser.LEADING, 0); }
		public TerminalNode LEFT() { return getToken(HqlParser.LEFT, 0); }
		public TerminalNode LIKE() { return getToken(HqlParser.LIKE, 0); }
		public TerminalNode LIMIT() { return getToken(HqlParser.LIMIT, 0); }
		public TerminalNode LIST() { return getToken(HqlParser.LIST, 0); }
		public TerminalNode LISTAGG() { return getToken(HqlParser.LISTAGG, 0); }
		public TerminalNode LOCAL() { return getToken(HqlParser.LOCAL, 0); }
		public TerminalNode LOCAL_DATE() { return getToken(HqlParser.LOCAL_DATE, 0); }
		public TerminalNode LOCAL_DATETIME() { return getToken(HqlParser.LOCAL_DATETIME, 0); }
		public TerminalNode LOCAL_TIME() { return getToken(HqlParser.LOCAL_TIME, 0); }
		public TerminalNode MAP() { return getToken(HqlParser.MAP, 0); }
		public TerminalNode MATERIALIZED() { return getToken(HqlParser.MATERIALIZED, 0); }
		public TerminalNode MAX() { return getToken(HqlParser.MAX, 0); }
		public TerminalNode MAXELEMENT() { return getToken(HqlParser.MAXELEMENT, 0); }
		public TerminalNode MAXINDEX() { return getToken(HqlParser.MAXINDEX, 0); }
		public TerminalNode MEMBER() { return getToken(HqlParser.MEMBER, 0); }
		public TerminalNode MICROSECOND() { return getToken(HqlParser.MICROSECOND, 0); }
		public TerminalNode MILLISECOND() { return getToken(HqlParser.MILLISECOND, 0); }
		public TerminalNode MIN() { return getToken(HqlParser.MIN, 0); }
		public TerminalNode MINELEMENT() { return getToken(HqlParser.MINELEMENT, 0); }
		public TerminalNode MININDEX() { return getToken(HqlParser.MININDEX, 0); }
		public TerminalNode MINUTE() { return getToken(HqlParser.MINUTE, 0); }
		public TerminalNode MONTH() { return getToken(HqlParser.MONTH, 0); }
		public TerminalNode NANOSECOND() { return getToken(HqlParser.NANOSECOND, 0); }
		public TerminalNode NATURALID() { return getToken(HqlParser.NATURALID, 0); }
		public TerminalNode NEW() { return getToken(HqlParser.NEW, 0); }
		public TerminalNode NEXT() { return getToken(HqlParser.NEXT, 0); }
		public TerminalNode NO() { return getToken(HqlParser.NO, 0); }
		public TerminalNode NOT() { return getToken(HqlParser.NOT, 0); }
		public TerminalNode NULLS() { return getToken(HqlParser.NULLS, 0); }
		public TerminalNode OBJECT() { return getToken(HqlParser.OBJECT, 0); }
		public TerminalNode OF() { return getToken(HqlParser.OF, 0); }
		public TerminalNode OFFSET() { return getToken(HqlParser.OFFSET, 0); }
		public TerminalNode OFFSET_DATETIME() { return getToken(HqlParser.OFFSET_DATETIME, 0); }
		public TerminalNode ON() { return getToken(HqlParser.ON, 0); }
		public TerminalNode ONLY() { return getToken(HqlParser.ONLY, 0); }
		public TerminalNode OR() { return getToken(HqlParser.OR, 0); }
		public TerminalNode ORDER() { return getToken(HqlParser.ORDER, 0); }
		public TerminalNode OTHERS() { return getToken(HqlParser.OTHERS, 0); }
		public TerminalNode OUTER() { return getToken(HqlParser.OUTER, 0); }
		public TerminalNode OVER() { return getToken(HqlParser.OVER, 0); }
		public TerminalNode OVERFLOW() { return getToken(HqlParser.OVERFLOW, 0); }
		public TerminalNode OVERLAY() { return getToken(HqlParser.OVERLAY, 0); }
		public TerminalNode PAD() { return getToken(HqlParser.PAD, 0); }
		public TerminalNode PARTITION() { return getToken(HqlParser.PARTITION, 0); }
		public TerminalNode PERCENT() { return getToken(HqlParser.PERCENT, 0); }
		public TerminalNode PLACING() { return getToken(HqlParser.PLACING, 0); }
		public TerminalNode POSITION() { return getToken(HqlParser.POSITION, 0); }
		public TerminalNode POWER() { return getToken(HqlParser.POWER, 0); }
		public TerminalNode PRECEDING() { return getToken(HqlParser.PRECEDING, 0); }
		public TerminalNode QUARTER() { return getToken(HqlParser.QUARTER, 0); }
		public TerminalNode RANGE() { return getToken(HqlParser.RANGE, 0); }
		public TerminalNode RESPECT() { return getToken(HqlParser.RESPECT, 0); }
		public TerminalNode RIGHT() { return getToken(HqlParser.RIGHT, 0); }
		public TerminalNode ROLLUP() { return getToken(HqlParser.ROLLUP, 0); }
		public TerminalNode ROW() { return getToken(HqlParser.ROW, 0); }
		public TerminalNode ROWS() { return getToken(HqlParser.ROWS, 0); }
		public TerminalNode SEARCH() { return getToken(HqlParser.SEARCH, 0); }
		public TerminalNode SECOND() { return getToken(HqlParser.SECOND, 0); }
		public TerminalNode SELECT() { return getToken(HqlParser.SELECT, 0); }
		public TerminalNode SET() { return getToken(HqlParser.SET, 0); }
		public TerminalNode SIZE() { return getToken(HqlParser.SIZE, 0); }
		public TerminalNode SOME() { return getToken(HqlParser.SOME, 0); }
		public TerminalNode SUBSTRING() { return getToken(HqlParser.SUBSTRING, 0); }
		public TerminalNode SUM() { return getToken(HqlParser.SUM, 0); }
		public TerminalNode THEN() { return getToken(HqlParser.THEN, 0); }
		public TerminalNode TIES() { return getToken(HqlParser.TIES, 0); }
		public TerminalNode TIME() { return getToken(HqlParser.TIME, 0); }
		public TerminalNode TIMESTAMP() { return getToken(HqlParser.TIMESTAMP, 0); }
		public TerminalNode TIMEZONE_HOUR() { return getToken(HqlParser.TIMEZONE_HOUR, 0); }
		public TerminalNode TIMEZONE_MINUTE() { return getToken(HqlParser.TIMEZONE_MINUTE, 0); }
		public TerminalNode TO() { return getToken(HqlParser.TO, 0); }
		public TerminalNode TRAILING() { return getToken(HqlParser.TRAILING, 0); }
		public TerminalNode TREAT() { return getToken(HqlParser.TREAT, 0); }
		public TerminalNode TRIM() { return getToken(HqlParser.TRIM, 0); }
		public TerminalNode TRUNC() { return getToken(HqlParser.TRUNC, 0); }
		public TerminalNode TRUNCATE() { return getToken(HqlParser.TRUNCATE, 0); }
		public TerminalNode TYPE() { return getToken(HqlParser.TYPE, 0); }
		public TerminalNode UNBOUNDED() { return getToken(HqlParser.UNBOUNDED, 0); }
		public TerminalNode UNION() { return getToken(HqlParser.UNION, 0); }
		public TerminalNode UPDATE() { return getToken(HqlParser.UPDATE, 0); }
		public TerminalNode USING() { return getToken(HqlParser.USING, 0); }
		public TerminalNode VALUE() { return getToken(HqlParser.VALUE, 0); }
		public TerminalNode VALUES() { return getToken(HqlParser.VALUES, 0); }
		public TerminalNode VERSION() { return getToken(HqlParser.VERSION, 0); }
		public TerminalNode VERSIONED() { return getToken(HqlParser.VERSIONED, 0); }
		public TerminalNode WEEK() { return getToken(HqlParser.WEEK, 0); }
		public TerminalNode WHEN() { return getToken(HqlParser.WHEN, 0); }
		public TerminalNode WHERE() { return getToken(HqlParser.WHERE, 0); }
		public TerminalNode WITH() { return getToken(HqlParser.WITH, 0); }
		public TerminalNode WITHIN() { return getToken(HqlParser.WITHIN, 0); }
		public TerminalNode WITHOUT() { return getToken(HqlParser.WITHOUT, 0); }
		public TerminalNode YEAR() { return getToken(HqlParser.YEAR, 0); }
		public ReservedWordContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_reservedWord; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).enterReservedWord(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof HqlListener ) ((HqlListener)listener).exitReservedWord(this);
		}
	}

	public final ReservedWordContext reservedWord() throws RecognitionException {
		ReservedWordContext _localctx = new ReservedWordContext(_ctx, getState());
		enterRule(_localctx, 232, RULE_reservedWord);
		int _la;
		try {
			setState(1306);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENTIFICATION_VARIABLE:
				enterOuterAlt(_localctx, 1);
				{
				setState(1304);
				match(IDENTIFICATION_VARIABLE);
				}
				break;
			case ALL:
			case AND:
			case ANY:
			case AS:
			case ASC:
			case AVG:
			case BETWEEN:
			case BOTH:
			case BREADTH:
			case BY:
			case CASE:
			case CAST:
			case COLLATE:
			case COUNT:
			case CROSS:
			case CUBE:
			case CURRENT:
			case CURRENT_DATE:
			case CURRENT_INSTANT:
			case CURRENT_TIME:
			case CURRENT_TIMESTAMP:
			case CYCLE:
			case DATE:
			case DATETIME:
			case DAY:
			case DEFAULT:
			case DELETE:
			case DEPTH:
			case DESC:
			case DISTINCT:
			case ELEMENT:
			case ELEMENTS:
			case ELSE:
			case EMPTY:
			case END:
			case ENTRY:
			case EPOCH:
			case ERROR:
			case ESCAPE:
			case EVERY:
			case EXCEPT:
			case EXCLUDE:
			case EXISTS:
			case EXTRACT:
			case FETCH:
			case FILTER:
			case FIRST:
			case FLOOR:
			case FOLLOWING:
			case FOR:
			case FORMAT:
			case FROM:
			case FULL:
			case FUNCTION:
			case GROUP:
			case GROUPS:
			case HAVING:
			case HOUR:
			case ID:
			case IGNORE:
			case ILIKE:
			case IN:
			case INDEX:
			case INDICES:
			case INNER:
			case INSERT:
			case INSTANT:
			case INTERSECT:
			case INTO:
			case IS:
			case JOIN:
			case KEY:
			case LAST:
			case LEADING:
			case LEFT:
			case LIKE:
			case LIMIT:
			case LIST:
			case LISTAGG:
			case LOCAL:
			case LOCAL_DATE:
			case LOCAL_DATETIME:
			case LOCAL_TIME:
			case MAP:
			case MATERIALIZED:
			case MAX:
			case MAXELEMENT:
			case MAXINDEX:
			case MEMBER:
			case MICROSECOND:
			case MILLISECOND:
			case MIN:
			case MINELEMENT:
			case MININDEX:
			case MINUTE:
			case MONTH:
			case NANOSECOND:
			case NATURALID:
			case NEW:
			case NEXT:
			case NO:
			case NOT:
			case NULLS:
			case OBJECT:
			case OF:
			case OFFSET:
			case OFFSET_DATETIME:
			case ON:
			case ONLY:
			case OR:
			case ORDER:
			case OTHERS:
			case OUTER:
			case OVER:
			case OVERFLOW:
			case OVERLAY:
			case PAD:
			case PARTITION:
			case PERCENT:
			case PLACING:
			case POSITION:
			case POWER:
			case PRECEDING:
			case QUARTER:
			case RANGE:
			case RESPECT:
			case RIGHT:
			case ROLLUP:
			case ROW:
			case ROWS:
			case SEARCH:
			case SECOND:
			case SELECT:
			case SET:
			case SIZE:
			case SOME:
			case SUBSTRING:
			case SUM:
			case THEN:
			case TIES:
			case TIME:
			case TIMESTAMP:
			case TIMEZONE_HOUR:
			case TIMEZONE_MINUTE:
			case TO:
			case TRAILING:
			case TREAT:
			case TRIM:
			case TRUNC:
			case TRUNCATE:
			case TYPE:
			case UNBOUNDED:
			case UNION:
			case UPDATE:
			case USING:
			case VALUE:
			case VALUES:
			case VERSION:
			case VERSIONED:
			case WEEK:
			case WHEN:
			case WHERE:
			case WITH:
			case WITHIN:
			case WITHOUT:
			case YEAR:
				enterOuterAlt(_localctx, 2);
				{
				setState(1305);
				((ReservedWordContext)_localctx).f = _input.LT(1);
				_la = _input.LA(1);
				if ( !(((((_la - 25)) & ~0x3f) == 0 && ((1L << (_la - 25)) & -2427721674137633L) != 0) || ((((_la - 89)) & ~0x3f) == 0 && ((1L << (_la - 89)) & -35184374202369L) != 0) || ((((_la - 153)) & ~0x3f) == 0 && ((1L << (_la - 153)) & 140737219919871L) != 0)) ) {
					((ReservedWordContext)_localctx).f = (Token)_errHandler.recoverInline(this);
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

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 62:
			return expression_sempred((ExpressionContext)_localctx, predIndex);
		case 95:
			return predicate_sempred((PredicateContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expression_sempred(ExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 6);
		case 1:
			return precpred(_ctx, 5);
		case 2:
			return precpred(_ctx, 4);
		case 3:
			return precpred(_ctx, 8);
		case 4:
			return precpred(_ctx, 7);
		}
		return true;
	}
	private boolean predicate_sempred(PredicateContext _localctx, int predIndex) {
		switch (predIndex) {
		case 5:
			return precpred(_ctx, 3);
		case 6:
			return precpred(_ctx, 2);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u00d0\u051d\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b"+
		"\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007"+
		"\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007"+
		"\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007"+
		"\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007"+
		"\u0018\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007"+
		"\u001b\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007"+
		"\u001e\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007"+
		"\"\u0002#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007"+
		"\'\u0002(\u0007(\u0002)\u0007)\u0002*\u0007*\u0002+\u0007+\u0002,\u0007"+
		",\u0002-\u0007-\u0002.\u0007.\u0002/\u0007/\u00020\u00070\u00021\u0007"+
		"1\u00022\u00072\u00023\u00073\u00024\u00074\u00025\u00075\u00026\u0007"+
		"6\u00027\u00077\u00028\u00078\u00029\u00079\u0002:\u0007:\u0002;\u0007"+
		";\u0002<\u0007<\u0002=\u0007=\u0002>\u0007>\u0002?\u0007?\u0002@\u0007"+
		"@\u0002A\u0007A\u0002B\u0007B\u0002C\u0007C\u0002D\u0007D\u0002E\u0007"+
		"E\u0002F\u0007F\u0002G\u0007G\u0002H\u0007H\u0002I\u0007I\u0002J\u0007"+
		"J\u0002K\u0007K\u0002L\u0007L\u0002M\u0007M\u0002N\u0007N\u0002O\u0007"+
		"O\u0002P\u0007P\u0002Q\u0007Q\u0002R\u0007R\u0002S\u0007S\u0002T\u0007"+
		"T\u0002U\u0007U\u0002V\u0007V\u0002W\u0007W\u0002X\u0007X\u0002Y\u0007"+
		"Y\u0002Z\u0007Z\u0002[\u0007[\u0002\\\u0007\\\u0002]\u0007]\u0002^\u0007"+
		"^\u0002_\u0007_\u0002`\u0007`\u0002a\u0007a\u0002b\u0007b\u0002c\u0007"+
		"c\u0002d\u0007d\u0002e\u0007e\u0002f\u0007f\u0002g\u0007g\u0002h\u0007"+
		"h\u0002i\u0007i\u0002j\u0007j\u0002k\u0007k\u0002l\u0007l\u0002m\u0007"+
		"m\u0002n\u0007n\u0002o\u0007o\u0002p\u0007p\u0002q\u0007q\u0002r\u0007"+
		"r\u0002s\u0007s\u0002t\u0007t\u0001\u0000\u0001\u0000\u0001\u0000\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001\u00f2\b\u0001\u0001"+
		"\u0002\u0001\u0002\u0001\u0003\u0003\u0003\u00f7\b\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0005\u0003\u00fd\b\u0003\n\u0003\f\u0003"+
		"\u0100\t\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0005\u0004"+
		"\u0106\b\u0004\n\u0004\f\u0004\u0109\t\u0004\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0003\u0005\u010e\b\u0005\u0001\u0005\u0003\u0005\u0111\b\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0003\u0005\u0117\b\u0005"+
		"\u0001\u0005\u0003\u0005\u011a\b\u0005\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0005\u0007\u0127\b\u0007\n\u0007\f\u0007\u012a"+
		"\t\u0007\u0001\b\u0001\b\u0003\b\u012e\b\b\u0001\b\u0003\b\u0131\b\b\u0001"+
		"\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0003"+
		"\t\u013c\b\t\u0001\t\u0001\t\u0003\t\u0140\b\t\u0001\n\u0001\n\u0001\n"+
		"\u0005\n\u0145\b\n\n\n\f\n\u0148\t\n\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0003\u000b\u014f\b\u000b\u0001\u000b\u0003\u000b"+
		"\u0152\b\u000b\u0001\f\u0001\f\u0003\f\u0156\b\f\u0001\f\u0003\f\u0159"+
		"\b\f\u0001\f\u0001\f\u0003\f\u015d\b\f\u0003\f\u015f\b\f\u0001\f\u0001"+
		"\f\u0003\f\u0163\b\f\u0001\f\u0001\f\u0003\f\u0167\b\f\u0003\f\u0169\b"+
		"\f\u0001\f\u0003\f\u016c\b\f\u0003\f\u016e\b\f\u0001\r\u0001\r\u0003\r"+
		"\u0172\b\r\u0001\r\u0003\r\u0175\b\r\u0001\r\u0003\r\u0178\b\r\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u017e\b\u000e\n\u000e"+
		"\f\u000e\u0181\t\u000e\u0001\u000f\u0001\u000f\u0005\u000f\u0185\b\u000f"+
		"\n\u000f\f\u000f\u0188\t\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0003"+
		"\u0010\u018d\b\u0010\u0001\u0011\u0001\u0011\u0003\u0011\u0191\b\u0011"+
		"\u0001\u0011\u0003\u0011\u0194\b\u0011\u0001\u0011\u0001\u0011\u0001\u0011"+
		"\u0001\u0011\u0003\u0011\u019a\b\u0011\u0003\u0011\u019c\b\u0011\u0001"+
		"\u0012\u0001\u0012\u0001\u0012\u0003\u0012\u01a1\b\u0012\u0001\u0012\u0001"+
		"\u0012\u0003\u0012\u01a5\b\u0012\u0001\u0013\u0001\u0013\u0003\u0013\u01a9"+
		"\b\u0013\u0001\u0013\u0003\u0013\u01ac\b\u0013\u0001\u0013\u0001\u0013"+
		"\u0001\u0013\u0001\u0013\u0003\u0013\u01b2\b\u0013\u0003\u0013\u01b4\b"+
		"\u0013\u0001\u0014\u0001\u0014\u0003\u0014\u01b8\b\u0014\u0001\u0014\u0001"+
		"\u0014\u0001\u0014\u0003\u0014\u01bd\b\u0014\u0001\u0015\u0001\u0015\u0003"+
		"\u0015\u01c1\b\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0005"+
		"\u0016\u01c7\b\u0016\n\u0016\f\u0016\u01ca\t\u0016\u0001\u0017\u0001\u0017"+
		"\u0001\u0017\u0001\u0017\u0001\u0018\u0001\u0018\u0003\u0018\u01d2\b\u0018"+
		"\u0001\u0018\u0001\u0018\u0003\u0018\u01d6\b\u0018\u0001\u0019\u0001\u0019"+
		"\u0003\u0019\u01da\b\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019"+
		"\u0003\u0019\u01e0\b\u0019\u0001\u001a\u0001\u001a\u0001\u001a\u0001\u001a"+
		"\u0005\u001a\u01e6\b\u001a\n\u001a\f\u001a\u01e9\t\u001a\u0001\u001a\u0001"+
		"\u001a\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0005\u001b\u01f1"+
		"\b\u001b\n\u001b\f\u001b\u01f4\t\u001b\u0001\u001c\u0001\u001c\u0001\u001c"+
		"\u0001\u001c\u0005\u001c\u01fa\b\u001c\n\u001c\f\u001c\u01fd\t\u001c\u0001"+
		"\u001c\u0001\u001c\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0001"+
		"\u001d\u0001\u001d\u0001\u001e\u0003\u001e\u0208\b\u001e\u0001\u001e\u0001"+
		"\u001e\u0001\u001f\u0001\u001f\u0001\u001f\u0003\u001f\u020f\b\u001f\u0001"+
		" \u0001 \u0003 \u0213\b \u0001 \u0003 \u0216\b \u0001!\u0001!\u0001!\u0003"+
		"!\u021b\b!\u0001\"\u0001\"\u0001#\u0001#\u0001#\u0001$\u0001$\u0001$\u0001"+
		"%\u0001%\u0001%\u0003%\u0228\b%\u0001&\u0001&\u0001&\u0001&\u0001&\u0001"+
		"&\u0003&\u0230\b&\u0001&\u0001&\u0001&\u0001&\u0003&\u0236\b&\u0001\'"+
		"\u0001\'\u0001(\u0001(\u0003(\u023c\b(\u0001(\u0001(\u0001)\u0001)\u0001"+
		")\u0005)\u0243\b)\n)\f)\u0246\t)\u0001*\u0001*\u0003*\u024a\b*\u0001+"+
		"\u0001+\u0001+\u0001+\u0003+\u0250\b+\u0001,\u0001,\u0001,\u0001,\u0001"+
		",\u0001-\u0001-\u0001-\u0001-\u0001-\u0001.\u0001.\u0001.\u0001.\u0005"+
		".\u0260\b.\n.\f.\u0263\t.\u0001/\u0003/\u0266\b/\u0001/\u0003/\u0269\b"+
		"/\u0001/\u0003/\u026c\b/\u0001/\u0003/\u026f\b/\u00010\u00010\u00010\u0001"+
		"0\u00030\u0275\b0\u00011\u00011\u00011\u00012\u00012\u00012\u00012\u0001"+
		"2\u00012\u00032\u0280\b2\u00013\u00013\u00013\u00013\u00013\u00053\u0287"+
		"\b3\n3\f3\u028a\t3\u00014\u00014\u00014\u00014\u00014\u00054\u0291\b4"+
		"\n4\f4\u0294\t4\u00015\u00015\u00015\u00015\u00055\u029a\b5\n5\f5\u029d"+
		"\t5\u00016\u00016\u00036\u02a1\b6\u00016\u00016\u00036\u02a5\b6\u0001"+
		"6\u00016\u00036\u02a9\b6\u00036\u02ab\b6\u00017\u00017\u00017\u00017\u0001"+
		"7\u00017\u00037\u02b3\b7\u00018\u00018\u00019\u00019\u0001:\u0001:\u0001"+
		";\u0001;\u0001;\u0001;\u0001;\u0001;\u0001;\u0001;\u0001;\u0001;\u0001"+
		";\u0001;\u0001;\u0001;\u0003;\u02c9\b;\u0001<\u0001<\u0001=\u0001=\u0001"+
		"=\u0001=\u0001=\u0005=\u02d2\b=\n=\f=\u02d5\t=\u0001=\u0003=\u02d8\b="+
		"\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0004"+
		">\u02e3\b>\u000b>\f>\u02e4\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0001"+
		">\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0001"+
		">\u0001>\u0001>\u0001>\u0003>\u02fb\b>\u0001>\u0001>\u0001>\u0001>\u0001"+
		">\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0001>\u0005"+
		">\u030b\b>\n>\f>\u030e\t>\u0001?\u0001?\u0001?\u0001?\u0001?\u0003?\u0315"+
		"\b?\u0001@\u0001@\u0003@\u0319\b@\u0001A\u0001A\u0003A\u031d\bA\u0001"+
		"A\u0003A\u0320\bA\u0001B\u0001B\u0003B\u0324\bB\u0001C\u0001C\u0001C\u0001"+
		"C\u0001C\u0003C\u032b\bC\u0001D\u0001D\u0005D\u032f\bD\nD\fD\u0332\tD"+
		"\u0001E\u0001E\u0001E\u0001F\u0001F\u0003F\u0339\bF\u0001G\u0001G\u0001"+
		"G\u0004G\u033e\bG\u000bG\fG\u033f\u0001G\u0001G\u0003G\u0344\bG\u0001"+
		"G\u0001G\u0001H\u0001H\u0004H\u034a\bH\u000bH\fH\u034b\u0001H\u0001H\u0003"+
		"H\u0350\bH\u0001H\u0001H\u0001I\u0001I\u0001I\u0001I\u0001I\u0001J\u0001"+
		"J\u0001J\u0001J\u0001J\u0001K\u0001K\u0001K\u0001K\u0003K\u0362\bK\u0001"+
		"K\u0001K\u0003K\u0366\bK\u0001K\u0003K\u0369\bK\u0001K\u0003K\u036c\b"+
		"K\u0001K\u0003K\u036f\bK\u0001K\u0001K\u0001K\u0001K\u0001K\u0001K\u0001"+
		"K\u0001K\u0001K\u0001K\u0001K\u0003K\u037c\bK\u0001L\u0003L\u037f\bL\u0001"+
		"L\u0001L\u0001L\u0005L\u0384\bL\nL\fL\u0387\tL\u0001M\u0001M\u0001M\u0001"+
		"M\u0001M\u0001N\u0001N\u0001N\u0001N\u0001N\u0001N\u0001O\u0001O\u0001"+
		"O\u0003O\u0397\bO\u0001O\u0003O\u039a\bO\u0001O\u0003O\u039d\bO\u0001"+
		"O\u0001O\u0001P\u0001P\u0001P\u0001P\u0001P\u0005P\u03a6\bP\nP\fP\u03a9"+
		"\tP\u0001Q\u0001Q\u0001Q\u0003Q\u03ae\bQ\u0001Q\u0001Q\u0001Q\u0001Q\u0001"+
		"Q\u0001Q\u0003Q\u03b6\bQ\u0003Q\u03b8\bQ\u0001R\u0001R\u0001R\u0001R\u0001"+
		"R\u0001R\u0001R\u0001R\u0001R\u0001R\u0003R\u03c4\bR\u0001S\u0001S\u0001"+
		"S\u0001S\u0001S\u0001S\u0001S\u0001S\u0001S\u0001S\u0003S\u03d0\bS\u0001"+
		"T\u0001T\u0001T\u0001T\u0001T\u0001T\u0001T\u0001T\u0001T\u0001T\u0003"+
		"T\u03dc\bT\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001U\u0001V\u0001"+
		"V\u0001V\u0001V\u0001V\u0003V\u03ea\bV\u0001V\u0003V\u03ed\bV\u0001W\u0001"+
		"W\u0001W\u0001W\u0001W\u0001W\u0001W\u0005W\u03f6\bW\nW\fW\u03f9\tW\u0001"+
		"X\u0001X\u0001X\u0001X\u0001X\u0001X\u0001X\u0001X\u0001X\u0001X\u0001"+
		"X\u0001X\u0003X\u0407\bX\u0001Y\u0001Y\u0001Y\u0003Y\u040c\bY\u0001Y\u0003"+
		"Y\u040f\bY\u0001Y\u0003Y\u0412\bY\u0001Y\u0001Y\u0001Y\u0001Z\u0001Z\u0001"+
		"[\u0001[\u0001[\u0001[\u0001[\u0001[\u0001[\u0001[\u0001[\u0001[\u0001"+
		"[\u0001[\u0001[\u0001[\u0001[\u0001[\u0003[\u0429\b[\u0001\\\u0001\\\u0001"+
		"\\\u0001\\\u0001\\\u0001\\\u0001\\\u0001\\\u0001\\\u0001\\\u0001\\\u0001"+
		"\\\u0001\\\u0001\\\u0001\\\u0001\\\u0003\\\u043b\b\\\u0001]\u0001]\u0001"+
		"]\u0001]\u0001]\u0001]\u0001]\u0003]\u0444\b]\u0001^\u0001^\u0001^\u0001"+
		"_\u0001_\u0001_\u0001_\u0001_\u0001_\u0001_\u0001_\u0001_\u0001_\u0001"+
		"_\u0001_\u0001_\u0001_\u0001_\u0003_\u0458\b_\u0001_\u0001_\u0001_\u0001"+
		"_\u0001_\u0001_\u0005_\u0460\b_\n_\f_\u0463\t_\u0001`\u0001`\u0003`\u0467"+
		"\b`\u0001a\u0001a\u0001a\u0001a\u0001b\u0001b\u0003b\u046f\bb\u0001b\u0001"+
		"b\u0001b\u0001b\u0001b\u0001c\u0001c\u0001c\u0003c\u0479\bc\u0001c\u0001"+
		"c\u0001c\u0001c\u0001c\u0003c\u0480\bc\u0001c\u0001c\u0001c\u0001c\u0003"+
		"c\u0486\bc\u0001d\u0001d\u0003d\u048a\bd\u0001d\u0001d\u0003d\u048e\b"+
		"d\u0001d\u0001d\u0003d\u0492\bd\u0001d\u0001d\u0001d\u0003d\u0497\bd\u0003"+
		"d\u0499\bd\u0001e\u0001e\u0003e\u049d\be\u0001e\u0001e\u0001e\u0001f\u0001"+
		"f\u0001f\u0001f\u0001f\u0001f\u0001f\u0001f\u0001f\u0001f\u0001f\u0001"+
		"f\u0001f\u0001f\u0005f\u04b0\bf\nf\ff\u04b3\tf\u0003f\u04b5\bf\u0001f"+
		"\u0003f\u04b8\bf\u0001g\u0001g\u0001g\u0001g\u0001g\u0001g\u0001g\u0001"+
		"g\u0003g\u04c2\bg\u0001h\u0001h\u0001h\u0003h\u04c7\bh\u0001h\u0001h\u0001"+
		"h\u0001h\u0003h\u04cd\bh\u0001h\u0001h\u0001h\u0001h\u0003h\u04d3\bh\u0001"+
		"i\u0001i\u0001i\u0003i\u04d8\bi\u0001j\u0001j\u0001j\u0005j\u04dd\bj\n"+
		"j\fj\u04e0\tj\u0001k\u0001k\u0003k\u04e4\bk\u0001k\u0003k\u04e7\bk\u0001"+
		"l\u0001l\u0003l\u04eb\bl\u0001m\u0001m\u0003m\u04ef\bm\u0001n\u0001n\u0001"+
		"n\u0003n\u04f4\bn\u0001o\u0001o\u0001o\u0001o\u0001o\u0001o\u0001o\u0001"+
		"o\u0003o\u04fe\bo\u0003o\u0500\bo\u0001p\u0001p\u0001p\u0005p\u0505\b"+
		"p\np\fp\u0508\tp\u0001p\u0003p\u050b\bp\u0001q\u0001q\u0001r\u0001r\u0001"+
		"s\u0001s\u0001s\u0005s\u0514\bs\ns\fs\u0517\ts\u0001t\u0001t\u0003t\u051b"+
		"\bt\u0001t\u0000\u0002|\u00beu\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010"+
		"\u0012\u0014\u0016\u0018\u001a\u001c\u001e \"$&(*,.02468:<>@BDFHJLNPR"+
		"TVXZ\\^`bdfhjlnprtvxz|~\u0080\u0082\u0084\u0086\u0088\u008a\u008c\u008e"+
		"\u0090\u0092\u0094\u0096\u0098\u009a\u009c\u009e\u00a0\u00a2\u00a4\u00a6"+
		"\u00a8\u00aa\u00ac\u00ae\u00b0\u00b2\u00b4\u00b6\u00b8\u00ba\u00bc\u00be"+
		"\u00c0\u00c2\u00c4\u00c6\u00c8\u00ca\u00cc\u00ce\u00d0\u00d2\u00d4\u00d6"+
		"\u00d8\u00da\u00dc\u00de\u00e0\u00e2\u00e4\u00e6\u00e8\u0000\u0017\u0002"+
		"\u0000\"\"66\u0002\u0000\u001d\u001d77\u0002\u0000KKff\u0001\u0000\u00a1"+
		"\u00a2\u0002\u0000KK\u0083\u0083\u0003\u0000RRii\u009f\u009f\u0002\u0000"+
		"\u008c\u008c\u00c4\u00c4\u0002\u0000HH\u00b5\u00b5\u0001\u0000\u00c8\u00ca"+
		"\u0001\u0000\u00cb\u00cd\u0002\u0000++oo\u0003\u0000++oo\u008a\u008a\b"+
		"\u000033??WW~\u0080\u009c\u009c\u00a4\u00a4\u00c1\u00c1\u00c7\u00c7\u0001"+
		"\u0000\b\t\u0002\u0000\n\n\u001e\u001e\u0003\u0000UU\u009d\u009d\u00a2"+
		"\u00a2\u0003\u0000!!hh\u00b2\u00b2\u0002\u0000\u0019\u0019BB\u0002\u0000"+
		"::]]\u0002\u0000\u001b\u001b\u00a8\u00a8\u0002\u0000\u0004\u0004\u000f"+
		"\u0015\u0002\u0000ZZjj\n\u0000\u0019\u001d\u001f%\'EGGIKMfhmo\u0085\u0087"+
		"\u00b4\u00b6\u00c7\u0586\u0000\u00ea\u0001\u0000\u0000\u0000\u0002\u00f1"+
		"\u0001\u0000\u0000\u0000\u0004\u00f3\u0001\u0000\u0000\u0000\u0006\u00f6"+
		"\u0001\u0000\u0000\u0000\b\u0101\u0001\u0000\u0000\u0000\n\u010a\u0001"+
		"\u0000\u0000\u0000\f\u011b\u0001\u0000\u0000\u0000\u000e\u0123\u0001\u0000"+
		"\u0000\u0000\u0010\u012b\u0001\u0000\u0000\u0000\u0012\u0132\u0001\u0000"+
		"\u0000\u0000\u0014\u0141\u0001\u0000\u0000\u0000\u0016\u014e\u0001\u0000"+
		"\u0000\u0000\u0018\u016d\u0001\u0000\u0000\u0000\u001a\u016f\u0001\u0000"+
		"\u0000\u0000\u001c\u0179\u0001\u0000\u0000\u0000\u001e\u0182\u0001\u0000"+
		"\u0000\u0000 \u018c\u0001\u0000\u0000\u0000\"\u019b\u0001\u0000\u0000"+
		"\u0000$\u019d\u0001\u0000\u0000\u0000&\u01b3\u0001\u0000\u0000\u0000("+
		"\u01b5\u0001\u0000\u0000\u0000*\u01be\u0001\u0000\u0000\u0000,\u01c2\u0001"+
		"\u0000\u0000\u0000.\u01cb\u0001\u0000\u0000\u00000\u01cf\u0001\u0000\u0000"+
		"\u00002\u01d7\u0001\u0000\u0000\u00004\u01e1\u0001\u0000\u0000\u00006"+
		"\u01ec\u0001\u0000\u0000\u00008\u01f5\u0001\u0000\u0000\u0000:\u0200\u0001"+
		"\u0000\u0000\u0000<\u0207\u0001\u0000\u0000\u0000>\u020e\u0001\u0000\u0000"+
		"\u0000@\u0210\u0001\u0000\u0000\u0000B\u021a\u0001\u0000\u0000\u0000D"+
		"\u021c\u0001\u0000\u0000\u0000F\u021e\u0001\u0000\u0000\u0000H\u0221\u0001"+
		"\u0000\u0000\u0000J\u0224\u0001\u0000\u0000\u0000L\u0229\u0001\u0000\u0000"+
		"\u0000N\u0237\u0001\u0000\u0000\u0000P\u0239\u0001\u0000\u0000\u0000R"+
		"\u023f\u0001\u0000\u0000\u0000T\u0247\u0001\u0000\u0000\u0000V\u024f\u0001"+
		"\u0000\u0000\u0000X\u0251\u0001\u0000\u0000\u0000Z\u0256\u0001\u0000\u0000"+
		"\u0000\\\u025b\u0001\u0000\u0000\u0000^\u026e\u0001\u0000\u0000\u0000"+
		"`\u0270\u0001\u0000\u0000\u0000b\u0276\u0001\u0000\u0000\u0000d\u0279"+
		"\u0001\u0000\u0000\u0000f\u0281\u0001\u0000\u0000\u0000h\u028b\u0001\u0000"+
		"\u0000\u0000j\u0295\u0001\u0000\u0000\u0000l\u02aa\u0001\u0000\u0000\u0000"+
		"n\u02b2\u0001\u0000\u0000\u0000p\u02b4\u0001\u0000\u0000\u0000r\u02b6"+
		"\u0001\u0000\u0000\u0000t\u02b8\u0001\u0000\u0000\u0000v\u02c8\u0001\u0000"+
		"\u0000\u0000x\u02ca\u0001\u0000\u0000\u0000z\u02d7\u0001\u0000\u0000\u0000"+
		"|\u02fa\u0001\u0000\u0000\u0000~\u0314\u0001\u0000\u0000\u0000\u0080\u0318"+
		"\u0001\u0000\u0000\u0000\u0082\u031f\u0001\u0000\u0000\u0000\u0084\u0321"+
		"\u0001\u0000\u0000\u0000\u0086\u0325\u0001\u0000\u0000\u0000\u0088\u032c"+
		"\u0001\u0000\u0000\u0000\u008a\u0333\u0001\u0000\u0000\u0000\u008c\u0338"+
		"\u0001\u0000\u0000\u0000\u008e\u033a\u0001\u0000\u0000\u0000\u0090\u0347"+
		"\u0001\u0000\u0000\u0000\u0092\u0353\u0001\u0000\u0000\u0000\u0094\u0358"+
		"\u0001\u0000\u0000\u0000\u0096\u037b\u0001\u0000\u0000\u0000\u0098\u037e"+
		"\u0001\u0000\u0000\u0000\u009a\u0388\u0001\u0000\u0000\u0000\u009c\u038d"+
		"\u0001\u0000\u0000\u0000\u009e\u0393\u0001\u0000\u0000\u0000\u00a0\u03a0"+
		"\u0001\u0000\u0000\u0000\u00a2\u03b7\u0001\u0000\u0000\u0000\u00a4\u03c3"+
		"\u0001\u0000\u0000\u0000\u00a6\u03cf\u0001\u0000\u0000\u0000\u00a8\u03db"+
		"\u0001\u0000\u0000\u0000\u00aa\u03dd\u0001\u0000\u0000\u0000\u00ac\u03e4"+
		"\u0001\u0000\u0000\u0000\u00ae\u03ee\u0001\u0000\u0000\u0000\u00b0\u0406"+
		"\u0001\u0000\u0000\u0000\u00b2\u0408\u0001\u0000\u0000\u0000\u00b4\u0416"+
		"\u0001\u0000\u0000\u0000\u00b6\u0428\u0001\u0000\u0000\u0000\u00b8\u043a"+
		"\u0001\u0000\u0000\u0000\u00ba\u043c\u0001\u0000\u0000\u0000\u00bc\u0445"+
		"\u0001\u0000\u0000\u0000\u00be\u0457\u0001\u0000\u0000\u0000\u00c0\u0466"+
		"\u0001\u0000\u0000\u0000\u00c2\u0468\u0001\u0000\u0000\u0000\u00c4\u046c"+
		"\u0001\u0000\u0000\u0000\u00c6\u0485\u0001\u0000\u0000\u0000\u00c8\u0487"+
		"\u0001\u0000\u0000\u0000\u00ca\u049a\u0001\u0000\u0000\u0000\u00cc\u04b7"+
		"\u0001\u0000\u0000\u0000\u00ce\u04c1\u0001\u0000\u0000\u0000\u00d0\u04d2"+
		"\u0001\u0000\u0000\u0000\u00d2\u04d7\u0001\u0000\u0000\u0000\u00d4\u04d9"+
		"\u0001\u0000\u0000\u0000\u00d6\u04e3\u0001\u0000\u0000\u0000\u00d8\u04ea"+
		"\u0001\u0000\u0000\u0000\u00da\u04ee\u0001\u0000\u0000\u0000\u00dc\u04f3"+
		"\u0001\u0000\u0000\u0000\u00de\u04ff\u0001\u0000\u0000\u0000\u00e0\u050a"+
		"\u0001\u0000\u0000\u0000\u00e2\u050c\u0001\u0000\u0000\u0000\u00e4\u050e"+
		"\u0001\u0000\u0000\u0000\u00e6\u0510\u0001\u0000\u0000\u0000\u00e8\u051a"+
		"\u0001\u0000\u0000\u0000\u00ea\u00eb\u0003\u0002\u0001\u0000\u00eb\u00ec"+
		"\u0005\u0000\u0000\u0001\u00ec\u0001\u0001\u0000\u0000\u0000\u00ed\u00f2"+
		"\u0003\u0004\u0002\u0000\u00ee\u00f2\u0003(\u0014\u0000\u00ef\u00f2\u0003"+
		"0\u0018\u0000\u00f0\u00f2\u00032\u0019\u0000\u00f1\u00ed\u0001\u0000\u0000"+
		"\u0000\u00f1\u00ee\u0001\u0000\u0000\u0000\u00f1\u00ef\u0001\u0000\u0000"+
		"\u0000\u00f1\u00f0\u0001\u0000\u0000\u0000\u00f2\u0003\u0001\u0000\u0000"+
		"\u0000\u00f3\u00f4\u0003\u0006\u0003\u0000\u00f4\u0005\u0001\u0000\u0000"+
		"\u0000\u00f5\u00f7\u0003\b\u0004\u0000\u00f6\u00f5\u0001\u0000\u0000\u0000"+
		"\u00f6\u00f7\u0001\u0000\u0000\u0000\u00f7\u00f8\u0001\u0000\u0000\u0000"+
		"\u00f8\u00fe\u0003\u0016\u000b\u0000\u00f9\u00fa\u0003l6\u0000\u00fa\u00fb"+
		"\u0003\u0016\u000b\u0000\u00fb\u00fd\u0001\u0000\u0000\u0000\u00fc\u00f9"+
		"\u0001\u0000\u0000\u0000\u00fd\u0100\u0001\u0000\u0000\u0000\u00fe\u00fc"+
		"\u0001\u0000\u0000\u0000\u00fe\u00ff\u0001\u0000\u0000\u0000\u00ff\u0007"+
		"\u0001\u0000\u0000\u0000\u0100\u00fe\u0001\u0000\u0000\u0000\u0101\u0102"+
		"\u0005\u00c4\u0000\u0000\u0102\u0107\u0003\n\u0005\u0000\u0103\u0104\u0005"+
		"\u0001\u0000\u0000\u0104\u0106\u0003\n\u0005\u0000\u0105\u0103\u0001\u0000"+
		"\u0000\u0000\u0106\u0109\u0001\u0000\u0000\u0000\u0107\u0105\u0001\u0000"+
		"\u0000\u0000\u0107\u0108\u0001\u0000\u0000\u0000\u0108\t\u0001\u0000\u0000"+
		"\u0000\u0109\u0107\u0001\u0000\u0000\u0000\u010a\u010b\u0003\u00e2q\u0000"+
		"\u010b\u0110\u0005\u001c\u0000\u0000\u010c\u010e\u0005\u0085\u0000\u0000"+
		"\u010d\u010c\u0001\u0000\u0000\u0000\u010d\u010e\u0001\u0000\u0000\u0000"+
		"\u010e\u010f\u0001\u0000\u0000\u0000\u010f\u0111\u0005t\u0000\u0000\u0110"+
		"\u010d\u0001\u0000\u0000\u0000\u0110\u0111\u0001\u0000\u0000\u0000\u0111"+
		"\u0112\u0001\u0000\u0000\u0000\u0112\u0113\u0005\u0002\u0000\u0000\u0113"+
		"\u0114\u0003\u0006\u0003\u0000\u0114\u0116\u0005\u0003\u0000\u0000\u0115"+
		"\u0117\u0003\f\u0006\u0000\u0116\u0115\u0001\u0000\u0000\u0000\u0116\u0117"+
		"\u0001\u0000\u0000\u0000\u0117\u0119\u0001\u0000\u0000\u0000\u0118\u011a"+
		"\u0003\u0012\t\u0000\u0119\u0118\u0001\u0000\u0000\u0000\u0119\u011a\u0001"+
		"\u0000\u0000\u0000\u011a\u000b\u0001\u0000\u0000\u0000\u011b\u011c\u0005"+
		"\u00a3\u0000\u0000\u011c\u011d\u0007\u0000\u0000\u0000\u011d\u011e\u0005"+
		"K\u0000\u0000\u011e\u011f\u0005#\u0000\u0000\u011f\u0120\u0003\u000e\u0007"+
		"\u0000\u0120\u0121\u0005\u00a6\u0000\u0000\u0121\u0122\u0003\u00e2q\u0000"+
		"\u0122\r\u0001\u0000\u0000\u0000\u0123\u0128\u0003\u0010\b\u0000\u0124"+
		"\u0125\u0005\u0001\u0000\u0000\u0125\u0127\u0003\u0010\b\u0000\u0126\u0124"+
		"\u0001\u0000\u0000\u0000\u0127\u012a\u0001\u0000\u0000\u0000\u0128\u0126"+
		"\u0001\u0000\u0000\u0000\u0128\u0129\u0001\u0000\u0000\u0000\u0129\u000f"+
		"\u0001\u0000\u0000\u0000\u012a\u0128\u0001\u0000\u0000\u0000\u012b\u012d"+
		"\u0003\u00e2q\u0000\u012c\u012e\u0003D\"\u0000\u012d\u012c\u0001\u0000"+
		"\u0000\u0000\u012d\u012e\u0001\u0000\u0000\u0000\u012e\u0130\u0001\u0000"+
		"\u0000\u0000\u012f\u0131\u0003F#\u0000\u0130\u012f\u0001\u0000\u0000\u0000"+
		"\u0130\u0131\u0001\u0000\u0000\u0000\u0131\u0011\u0001\u0000\u0000\u0000"+
		"\u0132\u0133\u00050\u0000\u0000\u0133\u0134\u0003\u0014\n\u0000\u0134"+
		"\u0135\u0005\u00a6\u0000\u0000\u0135\u013b\u0003\u00e2q\u0000\u0136\u0137"+
		"\u0005\u00b1\u0000\u0000\u0137\u0138\u0003n7\u0000\u0138\u0139\u00054"+
		"\u0000\u0000\u0139\u013a\u0003n7\u0000\u013a\u013c\u0001\u0000\u0000\u0000"+
		"\u013b\u0136\u0001\u0000\u0000\u0000\u013b\u013c\u0001\u0000\u0000\u0000"+
		"\u013c\u013f\u0001\u0000\u0000\u0000\u013d\u013e\u0005\u00bc\u0000\u0000"+
		"\u013e\u0140\u0003\u00e2q\u0000\u013f\u013d\u0001\u0000\u0000\u0000\u013f"+
		"\u0140\u0001\u0000\u0000\u0000\u0140\u0013\u0001\u0000\u0000\u0000\u0141"+
		"\u0146\u0003\u00e2q\u0000\u0142\u0143\u0005\u0001\u0000\u0000\u0143\u0145"+
		"\u0003\u00e2q\u0000\u0144\u0142\u0001\u0000\u0000\u0000\u0145\u0148\u0001"+
		"\u0000\u0000\u0000\u0146\u0144\u0001\u0000\u0000\u0000\u0146\u0147\u0001"+
		"\u0000\u0000\u0000\u0147\u0015\u0001\u0000\u0000\u0000\u0148\u0146\u0001"+
		"\u0000\u0000\u0000\u0149\u014f\u0003\u0018\f\u0000\u014a\u014b\u0005\u0002"+
		"\u0000\u0000\u014b\u014c\u0003\u0006\u0003\u0000\u014c\u014d\u0005\u0003"+
		"\u0000\u0000\u014d\u014f\u0001\u0000\u0000\u0000\u014e\u0149\u0001\u0000"+
		"\u0000\u0000\u014e\u014a\u0001\u0000\u0000\u0000\u014f\u0151\u0001\u0000"+
		"\u0000\u0000\u0150\u0152\u0003\u001a\r\u0000\u0151\u0150\u0001\u0000\u0000"+
		"\u0000\u0151\u0152\u0001\u0000\u0000\u0000\u0152\u0017\u0001\u0000\u0000"+
		"\u0000\u0153\u0155\u0003P(\u0000\u0154\u0156\u0003\u001c\u000e\u0000\u0155"+
		"\u0154\u0001\u0000\u0000\u0000\u0155\u0156\u0001\u0000\u0000\u0000\u0156"+
		"\u0158\u0001\u0000\u0000\u0000\u0157\u0159\u0003\\.\u0000\u0158\u0157"+
		"\u0001\u0000\u0000\u0000\u0158\u0159\u0001\u0000\u0000\u0000\u0159\u015e"+
		"\u0001\u0000\u0000\u0000\u015a\u015c\u0003f3\u0000\u015b\u015d\u0003j"+
		"5\u0000\u015c\u015b\u0001\u0000\u0000\u0000\u015c\u015d\u0001\u0000\u0000"+
		"\u0000\u015d\u015f\u0001\u0000\u0000\u0000\u015e\u015a\u0001\u0000\u0000"+
		"\u0000\u015e\u015f\u0001\u0000\u0000\u0000\u015f\u016e\u0001\u0000\u0000"+
		"\u0000\u0160\u0162\u0003\u001c\u000e\u0000\u0161\u0163\u0003\\.\u0000"+
		"\u0162\u0161\u0001\u0000\u0000\u0000\u0162\u0163\u0001\u0000\u0000\u0000"+
		"\u0163\u0168\u0001\u0000\u0000\u0000\u0164\u0166\u0003f3\u0000\u0165\u0167"+
		"\u0003j5\u0000\u0166\u0165\u0001\u0000\u0000\u0000\u0166\u0167\u0001\u0000"+
		"\u0000\u0000\u0167\u0169\u0001\u0000\u0000\u0000\u0168\u0164\u0001\u0000"+
		"\u0000\u0000\u0168\u0169\u0001\u0000\u0000\u0000\u0169\u016b\u0001\u0000"+
		"\u0000\u0000\u016a\u016c\u0003P(\u0000\u016b\u016a\u0001\u0000\u0000\u0000"+
		"\u016b\u016c\u0001\u0000\u0000\u0000\u016c\u016e\u0001\u0000\u0000\u0000"+
		"\u016d\u0153\u0001\u0000\u0000\u0000\u016d\u0160\u0001\u0000\u0000\u0000"+
		"\u016e\u0019\u0001\u0000\u0000\u0000\u016f\u0171\u0003h4\u0000\u0170\u0172"+
		"\u0003H$\u0000\u0171\u0170\u0001\u0000\u0000\u0000\u0171\u0172\u0001\u0000"+
		"\u0000\u0000\u0172\u0174\u0001\u0000\u0000\u0000\u0173\u0175\u0003J%\u0000"+
		"\u0174\u0173\u0001\u0000\u0000\u0000\u0174\u0175\u0001\u0000\u0000\u0000"+
		"\u0175\u0177\u0001\u0000\u0000\u0000\u0176\u0178\u0003L&\u0000\u0177\u0176"+
		"\u0001\u0000\u0000\u0000\u0177\u0178\u0001\u0000\u0000\u0000\u0178\u001b"+
		"\u0001\u0000\u0000\u0000\u0179\u017a\u0005Q\u0000\u0000\u017a\u017f\u0003"+
		"\u001e\u000f\u0000\u017b\u017c\u0005\u0001\u0000\u0000\u017c\u017e\u0003"+
		"\u001e\u000f\u0000\u017d\u017b\u0001\u0000\u0000\u0000\u017e\u0181\u0001"+
		"\u0000\u0000\u0000\u017f\u017d\u0001\u0000\u0000\u0000\u017f\u0180\u0001"+
		"\u0000\u0000\u0000\u0180\u001d\u0001\u0000\u0000\u0000\u0181\u017f\u0001"+
		"\u0000\u0000\u0000\u0182\u0186\u0003\"\u0011\u0000\u0183\u0185\u0003 "+
		"\u0010\u0000\u0184\u0183\u0001\u0000\u0000\u0000\u0185\u0188\u0001\u0000"+
		"\u0000\u0000\u0186\u0184\u0001\u0000\u0000\u0000\u0186\u0187\u0001\u0000"+
		"\u0000\u0000\u0187\u001f\u0001\u0000\u0000\u0000\u0188\u0186\u0001\u0000"+
		"\u0000\u0000\u0189\u018d\u0003$\u0012\u0000\u018a\u018d\u0003`0\u0000"+
		"\u018b\u018d\u0003d2\u0000\u018c\u0189\u0001\u0000\u0000\u0000\u018c\u018a"+
		"\u0001\u0000\u0000\u0000\u018c\u018b\u0001\u0000\u0000\u0000\u018d!\u0001"+
		"\u0000\u0000\u0000\u018e\u0190\u0003\u00e0p\u0000\u018f\u0191\u0003\u00dc"+
		"n\u0000\u0190\u018f\u0001\u0000\u0000\u0000\u0190\u0191\u0001\u0000\u0000"+
		"\u0000\u0191\u019c\u0001\u0000\u0000\u0000\u0192\u0194\u0005g\u0000\u0000"+
		"\u0193\u0192\u0001\u0000\u0000\u0000\u0193\u0194\u0001\u0000\u0000\u0000"+
		"\u0194\u0195\u0001\u0000\u0000\u0000\u0195\u0196\u0005\u0002\u0000\u0000"+
		"\u0196\u0197\u0003N\'\u0000\u0197\u0199\u0005\u0003\u0000\u0000\u0198"+
		"\u019a\u0003\u00dcn\u0000\u0199\u0198\u0001\u0000\u0000\u0000\u0199\u019a"+
		"\u0001\u0000\u0000\u0000\u019a\u019c\u0001\u0000\u0000\u0000\u019b\u018e"+
		"\u0001\u0000\u0000\u0000\u019b\u0193\u0001\u0000\u0000\u0000\u019c#\u0001"+
		"\u0000\u0000\u0000\u019d\u019e\u0003^/\u0000\u019e\u01a0\u0005d\u0000"+
		"\u0000\u019f\u01a1\u0005I\u0000\u0000\u01a0\u019f\u0001\u0000\u0000\u0000"+
		"\u01a0\u01a1\u0001\u0000\u0000\u0000\u01a1\u01a2\u0001\u0000\u0000\u0000"+
		"\u01a2\u01a4\u0003&\u0013\u0000\u01a3\u01a5\u0003b1\u0000\u01a4\u01a3"+
		"\u0001\u0000\u0000\u0000\u01a4\u01a5\u0001\u0000\u0000\u0000\u01a5%\u0001"+
		"\u0000\u0000\u0000\u01a6\u01a8\u0003\u0082A\u0000\u01a7\u01a9\u0003\u00dc"+
		"n\u0000\u01a8\u01a7\u0001\u0000\u0000\u0000\u01a8\u01a9\u0001\u0000\u0000"+
		"\u0000\u01a9\u01b4\u0001\u0000\u0000\u0000\u01aa\u01ac\u0005g\u0000\u0000"+
		"\u01ab\u01aa\u0001\u0000\u0000\u0000\u01ab\u01ac\u0001\u0000\u0000\u0000"+
		"\u01ac\u01ad\u0001\u0000\u0000\u0000\u01ad\u01ae\u0005\u0002\u0000\u0000"+
		"\u01ae\u01af\u0003N\'\u0000\u01af\u01b1\u0005\u0003\u0000\u0000\u01b0"+
		"\u01b2\u0003\u00dcn\u0000\u01b1\u01b0\u0001\u0000\u0000\u0000\u01b1\u01b2"+
		"\u0001\u0000\u0000\u0000\u01b2\u01b4\u0001\u0000\u0000\u0000\u01b3\u01a6"+
		"\u0001\u0000\u0000\u0000\u01b3\u01ab\u0001\u0000\u0000\u0000\u01b4\'\u0001"+
		"\u0000\u0000\u0000\u01b5\u01b7\u0005\u00bb\u0000\u0000\u01b6\u01b8\u0005"+
		"\u00c0\u0000\u0000\u01b7\u01b6\u0001\u0000\u0000\u0000\u01b7\u01b8\u0001"+
		"\u0000\u0000\u0000\u01b8\u01b9\u0001\u0000\u0000\u0000\u01b9\u01ba\u0003"+
		"*\u0015\u0000\u01ba\u01bc\u0003,\u0016\u0000\u01bb\u01bd\u0003\\.\u0000"+
		"\u01bc\u01bb\u0001\u0000\u0000\u0000\u01bc\u01bd\u0001\u0000\u0000\u0000"+
		"\u01bd)\u0001\u0000\u0000\u0000\u01be\u01c0\u0003\u00e0p\u0000\u01bf\u01c1"+
		"\u0003\u00dcn\u0000\u01c0\u01bf\u0001\u0000\u0000\u0000\u01c0\u01c1\u0001"+
		"\u0000\u0000\u0000\u01c1+\u0001\u0000\u0000\u0000\u01c2\u01c3\u0005\u00a6"+
		"\u0000\u0000\u01c3\u01c8\u0003.\u0017\u0000\u01c4\u01c5\u0005\u0001\u0000"+
		"\u0000\u01c5\u01c7\u0003.\u0017\u0000\u01c6\u01c4\u0001\u0000\u0000\u0000"+
		"\u01c7\u01ca\u0001\u0000\u0000\u0000\u01c8\u01c6\u0001\u0000\u0000\u0000"+
		"\u01c8\u01c9\u0001\u0000\u0000\u0000\u01c9-\u0001\u0000\u0000\u0000\u01ca"+
		"\u01c8\u0001\u0000\u0000\u0000\u01cb\u01cc\u0003\u0088D\u0000\u01cc\u01cd"+
		"\u0005\u0004\u0000\u0000\u01cd\u01ce\u0003\u00c0`\u0000\u01ce/\u0001\u0000"+
		"\u0000\u0000\u01cf\u01d1\u00055\u0000\u0000\u01d0\u01d2\u0005Q\u0000\u0000"+
		"\u01d1\u01d0\u0001\u0000\u0000\u0000\u01d1\u01d2\u0001\u0000\u0000\u0000"+
		"\u01d2\u01d3\u0001\u0000\u0000\u0000\u01d3\u01d5\u0003*\u0015\u0000\u01d4"+
		"\u01d6\u0003\\.\u0000\u01d5\u01d4\u0001\u0000\u0000\u0000\u01d5\u01d6"+
		"\u0001\u0000\u0000\u0000\u01d61\u0001\u0000\u0000\u0000\u01d7\u01d9\u0005"+
		"_\u0000\u0000\u01d8\u01da\u0005b\u0000\u0000\u01d9\u01d8\u0001\u0000\u0000"+
		"\u0000\u01d9\u01da\u0001\u0000\u0000\u0000\u01da\u01db\u0001\u0000\u0000"+
		"\u0000\u01db\u01dc\u0003*\u0015\u0000\u01dc\u01df\u00034\u001a\u0000\u01dd"+
		"\u01e0\u0003\u0006\u0003\u0000\u01de\u01e0\u00036\u001b\u0000\u01df\u01dd"+
		"\u0001\u0000\u0000\u0000\u01df\u01de\u0001\u0000\u0000\u0000\u01e03\u0001"+
		"\u0000\u0000\u0000\u01e1\u01e2\u0005\u0002\u0000\u0000\u01e2\u01e7\u0003"+
		"\u0088D\u0000\u01e3\u01e4\u0005\u0001\u0000\u0000\u01e4\u01e6\u0003\u0088"+
		"D\u0000\u01e5\u01e3\u0001\u0000\u0000\u0000\u01e6\u01e9\u0001\u0000\u0000"+
		"\u0000\u01e7\u01e5\u0001\u0000\u0000\u0000\u01e7\u01e8\u0001\u0000\u0000"+
		"\u0000\u01e8\u01ea\u0001\u0000\u0000\u0000\u01e9\u01e7\u0001\u0000\u0000"+
		"\u0000\u01ea\u01eb\u0005\u0003\u0000\u0000\u01eb5\u0001\u0000\u0000\u0000"+
		"\u01ec\u01ed\u0005\u00be\u0000\u0000\u01ed\u01f2\u00038\u001c\u0000\u01ee"+
		"\u01ef\u0005\u0001\u0000\u0000\u01ef\u01f1\u00038\u001c\u0000\u01f0\u01ee"+
		"\u0001\u0000\u0000\u0000\u01f1\u01f4\u0001\u0000\u0000\u0000\u01f2\u01f0"+
		"\u0001\u0000\u0000\u0000\u01f2\u01f3\u0001\u0000\u0000\u0000\u01f37\u0001"+
		"\u0000\u0000\u0000\u01f4\u01f2\u0001\u0000\u0000\u0000\u01f5\u01f6\u0005"+
		"\u0002\u0000\u0000\u01f6\u01fb\u0003|>\u0000\u01f7\u01f8\u0005\u0001\u0000"+
		"\u0000\u01f8\u01fa\u0003|>\u0000\u01f9\u01f7\u0001\u0000\u0000\u0000\u01fa"+
		"\u01fd\u0001\u0000\u0000\u0000\u01fb\u01f9\u0001\u0000\u0000\u0000\u01fb"+
		"\u01fc\u0001\u0000\u0000\u0000\u01fc\u01fe\u0001\u0000\u0000\u0000\u01fd"+
		"\u01fb\u0001\u0000\u0000\u0000\u01fe\u01ff\u0005\u0003\u0000\u0000\u01ff"+
		"9\u0001\u0000\u0000\u0000\u0200\u0201\u0005\u0082\u0000\u0000\u0201\u0202"+
		"\u0003\u00d2i\u0000\u0202\u0203\u0005\u0002\u0000\u0000\u0203\u0204\u0003"+
		"\u00d4j\u0000\u0204\u0205\u0005\u0003\u0000\u0000\u0205;\u0001\u0000\u0000"+
		"\u0000\u0206\u0208\u0005\u001c\u0000\u0000\u0207\u0206\u0001\u0000\u0000"+
		"\u0000\u0207\u0208\u0001\u0000\u0000\u0000\u0208\u0209\u0001\u0000\u0000"+
		"\u0000\u0209\u020a\u0003\u00e2q\u0000\u020a=\u0001\u0000\u0000\u0000\u020b"+
		"\u020f\u0003\u00e2q\u0000\u020c\u020f\u0005\u00cb\u0000\u0000\u020d\u020f"+
		"\u0003|>\u0000\u020e\u020b\u0001\u0000\u0000\u0000\u020e\u020c\u0001\u0000"+
		"\u0000\u0000\u020e\u020d\u0001\u0000\u0000\u0000\u020f?\u0001\u0000\u0000"+
		"\u0000\u0210\u0212\u0003B!\u0000\u0211\u0213\u0003D\"\u0000\u0212\u0211"+
		"\u0001\u0000\u0000\u0000\u0212\u0213\u0001\u0000\u0000\u0000\u0213\u0215"+
		"\u0001\u0000\u0000\u0000\u0214\u0216\u0003F#\u0000\u0215\u0214\u0001\u0000"+
		"\u0000\u0000\u0215\u0216\u0001\u0000\u0000\u0000\u0216A\u0001\u0000\u0000"+
		"\u0000\u0217\u021b\u0003\u00e2q\u0000\u0218\u021b\u0005\u00cb\u0000\u0000"+
		"\u0219\u021b\u0003|>\u0000\u021a\u0217\u0001\u0000\u0000\u0000\u021a\u0218"+
		"\u0001\u0000\u0000\u0000\u021a\u0219\u0001\u0000\u0000\u0000\u021bC\u0001"+
		"\u0000\u0000\u0000\u021c\u021d\u0007\u0001\u0000\u0000\u021dE\u0001\u0000"+
		"\u0000\u0000\u021e\u021f\u0005\u0087\u0000\u0000\u021f\u0220\u0007\u0002"+
		"\u0000\u0000\u0220G\u0001\u0000\u0000\u0000\u0221\u0222\u0005k\u0000\u0000"+
		"\u0222\u0223\u0003\u00d8l\u0000\u0223I\u0001\u0000\u0000\u0000\u0224\u0225"+
		"\u0005\u008a\u0000\u0000\u0225\u0227\u0003\u00d8l\u0000\u0226\u0228\u0007"+
		"\u0003\u0000\u0000\u0227\u0226\u0001\u0000\u0000\u0000\u0227\u0228\u0001"+
		"\u0000\u0000\u0000\u0228K\u0001\u0000\u0000\u0000\u0229\u022a\u0005I\u0000"+
		"\u0000\u022a\u022f\u0007\u0004\u0000\u0000\u022b\u0230\u0003\u00d8l\u0000"+
		"\u022c\u022d\u0003\u00dam\u0000\u022d\u022e\u0005\u0005\u0000\u0000\u022e"+
		"\u0230\u0001\u0000\u0000\u0000\u022f\u022b\u0001\u0000\u0000\u0000\u022f"+
		"\u022c\u0001\u0000\u0000\u0000\u0230\u0231\u0001\u0000\u0000\u0000\u0231"+
		"\u0235\u0007\u0003\u0000\u0000\u0232\u0236\u0005\u008d\u0000\u0000\u0233"+
		"\u0234\u0005\u00c4\u0000\u0000\u0234\u0236\u0005\u00ac\u0000\u0000\u0235"+
		"\u0232\u0001\u0000\u0000\u0000\u0235\u0233\u0001\u0000\u0000\u0000\u0236"+
		"M\u0001\u0000\u0000\u0000\u0237\u0238\u0003\u0006\u0003\u0000\u0238O\u0001"+
		"\u0000\u0000\u0000\u0239\u023b\u0005\u00a5\u0000\u0000\u023a\u023c\u0005"+
		"8\u0000\u0000\u023b\u023a\u0001\u0000\u0000\u0000\u023b\u023c\u0001\u0000"+
		"\u0000\u0000\u023c\u023d\u0001\u0000\u0000\u0000\u023d\u023e\u0003R)\u0000"+
		"\u023eQ\u0001\u0000\u0000\u0000\u023f\u0244\u0003T*\u0000\u0240\u0241"+
		"\u0005\u0001\u0000\u0000\u0241\u0243\u0003T*\u0000\u0242\u0240\u0001\u0000"+
		"\u0000\u0000\u0243\u0246\u0001\u0000\u0000\u0000\u0244\u0242\u0001\u0000"+
		"\u0000\u0000\u0244\u0245\u0001\u0000\u0000\u0000\u0245S\u0001\u0000\u0000"+
		"\u0000\u0246\u0244\u0001\u0000\u0000\u0000\u0247\u0249\u0003V+\u0000\u0248"+
		"\u024a\u0003\u00dcn\u0000\u0249\u0248\u0001\u0000\u0000\u0000\u0249\u024a"+
		"\u0001\u0000\u0000\u0000\u024aU\u0001\u0000\u0000\u0000\u024b\u0250\u0003"+
		":\u001d\u0000\u024c\u0250\u0003X,\u0000\u024d\u0250\u0003Z-\u0000\u024e"+
		"\u0250\u0003\u00c0`\u0000\u024f\u024b\u0001\u0000\u0000\u0000\u024f\u024c"+
		"\u0001\u0000\u0000\u0000\u024f\u024d\u0001\u0000\u0000\u0000\u024f\u024e"+
		"\u0001\u0000\u0000\u0000\u0250W\u0001\u0000\u0000\u0000\u0251\u0252\u0005"+
		">\u0000\u0000\u0252\u0253\u0005\u0002\u0000\u0000\u0253\u0254\u0003\u0082"+
		"A\u0000\u0254\u0255\u0005\u0003\u0000\u0000\u0255Y\u0001\u0000\u0000\u0000"+
		"\u0256\u0257\u0005\u0088\u0000\u0000\u0257\u0258\u0005\u0002\u0000\u0000"+
		"\u0258\u0259\u0003\u00e2q\u0000\u0259\u025a\u0005\u0003\u0000\u0000\u025a"+
		"[\u0001\u0000\u0000\u0000\u025b\u025c\u0005\u00c3\u0000\u0000\u025c\u0261"+
		"\u0003\u00be_\u0000\u025d\u025e\u0005\u0001\u0000\u0000\u025e\u0260\u0003"+
		"\u00be_\u0000\u025f\u025d\u0001\u0000\u0000\u0000\u0260\u0263\u0001\u0000"+
		"\u0000\u0000\u0261\u025f\u0001\u0000\u0000\u0000\u0261\u0262\u0001\u0000"+
		"\u0000\u0000\u0262]\u0001\u0000\u0000\u0000\u0263\u0261\u0001\u0000\u0000"+
		"\u0000\u0264\u0266\u0005^\u0000\u0000\u0265\u0264\u0001\u0000\u0000\u0000"+
		"\u0265\u0266\u0001\u0000\u0000\u0000\u0266\u026f\u0001\u0000\u0000\u0000"+
		"\u0267\u0269\u0007\u0005\u0000\u0000\u0268\u0267\u0001\u0000\u0000\u0000"+
		"\u0268\u0269\u0001\u0000\u0000\u0000\u0269\u026b\u0001\u0000\u0000\u0000"+
		"\u026a\u026c\u0005\u0091\u0000\u0000\u026b\u026a\u0001\u0000\u0000\u0000"+
		"\u026b\u026c\u0001\u0000\u0000\u0000\u026c\u026f\u0001\u0000\u0000\u0000"+
		"\u026d\u026f\u0005)\u0000\u0000\u026e\u0265\u0001\u0000\u0000\u0000\u026e"+
		"\u0268\u0001\u0000\u0000\u0000\u026e\u026d\u0001\u0000\u0000\u0000\u026f"+
		"_\u0001\u0000\u0000\u0000\u0270\u0271\u0005)\u0000\u0000\u0271\u0272\u0005"+
		"d\u0000\u0000\u0272\u0274\u0003\u00e0p\u0000\u0273\u0275\u0003\u00dcn"+
		"\u0000\u0274\u0273\u0001\u0000\u0000\u0000\u0274\u0275\u0001\u0000\u0000"+
		"\u0000\u0275a\u0001\u0000\u0000\u0000\u0276\u0277\u0007\u0006\u0000\u0000"+
		"\u0277\u0278\u0003\u00be_\u0000\u0278c\u0001\u0000\u0000\u0000\u0279\u027a"+
		"\u0005\u0001\u0000\u0000\u027a\u027b\u0005[\u0000\u0000\u027b\u027c\u0005"+
		"\u0002\u0000\u0000\u027c\u027d\u0003\u0082A\u0000\u027d\u027f\u0005\u0003"+
		"\u0000\u0000\u027e\u0280\u0003\u00dcn\u0000\u027f\u027e\u0001\u0000\u0000"+
		"\u0000\u027f\u0280\u0001\u0000\u0000\u0000\u0280e\u0001\u0000\u0000\u0000"+
		"\u0281\u0282\u0005T\u0000\u0000\u0282\u0283\u0005#\u0000\u0000\u0283\u0288"+
		"\u0003>\u001f\u0000\u0284\u0285\u0005\u0001\u0000\u0000\u0285\u0287\u0003"+
		">\u001f\u0000\u0286\u0284\u0001\u0000\u0000\u0000\u0287\u028a\u0001\u0000"+
		"\u0000\u0000\u0288\u0286\u0001\u0000\u0000\u0000\u0288\u0289\u0001\u0000"+
		"\u0000\u0000\u0289g\u0001\u0000\u0000\u0000\u028a\u0288\u0001\u0000\u0000"+
		"\u0000\u028b\u028c\u0005\u008f\u0000\u0000\u028c\u028d\u0005#\u0000\u0000"+
		"\u028d\u0292\u0003@ \u0000\u028e\u028f\u0005\u0001\u0000\u0000\u028f\u0291"+
		"\u0003@ \u0000\u0290\u028e\u0001\u0000\u0000\u0000\u0291\u0294\u0001\u0000"+
		"\u0000\u0000\u0292\u0290\u0001\u0000\u0000\u0000\u0292\u0293\u0001\u0000"+
		"\u0000\u0000\u0293i\u0001\u0000\u0000\u0000\u0294\u0292\u0001\u0000\u0000"+
		"\u0000\u0295\u0296\u0005V\u0000\u0000\u0296\u029b\u0003\u00be_\u0000\u0297"+
		"\u0298\u0005\u0001\u0000\u0000\u0298\u029a\u0003\u00be_\u0000\u0299\u0297"+
		"\u0001\u0000\u0000\u0000\u029a\u029d\u0001\u0000\u0000\u0000\u029b\u0299"+
		"\u0001\u0000\u0000\u0000\u029b\u029c\u0001\u0000\u0000\u0000\u029ck\u0001"+
		"\u0000\u0000\u0000\u029d\u029b\u0001\u0000\u0000\u0000\u029e\u02a0\u0005"+
		"\u00ba\u0000\u0000\u029f\u02a1\u0005\u0019\u0000\u0000\u02a0\u029f\u0001"+
		"\u0000\u0000\u0000\u02a0\u02a1\u0001\u0000\u0000\u0000\u02a1\u02ab\u0001"+
		"\u0000\u0000\u0000\u02a2\u02a4\u0005a\u0000\u0000\u02a3\u02a5\u0005\u0019"+
		"\u0000\u0000\u02a4\u02a3\u0001\u0000\u0000\u0000\u02a4\u02a5\u0001\u0000"+
		"\u0000\u0000\u02a5\u02ab\u0001\u0000\u0000\u0000\u02a6\u02a8\u0005C\u0000"+
		"\u0000\u02a7\u02a9\u0005\u0019\u0000\u0000\u02a8\u02a7\u0001\u0000\u0000"+
		"\u0000\u02a8\u02a9\u0001\u0000\u0000\u0000\u02a9\u02ab\u0001\u0000\u0000"+
		"\u0000\u02aa\u029e\u0001\u0000\u0000\u0000\u02aa\u02a2\u0001\u0000\u0000"+
		"\u0000\u02aa\u02a6\u0001\u0000\u0000\u0000\u02abm\u0001\u0000\u0000\u0000"+
		"\u02ac\u02b3\u0005\u0086\u0000\u0000\u02ad\u02b3\u0003p8\u0000\u02ae\u02b3"+
		"\u0003r9\u0000\u02af\u02b3\u0003t:\u0000\u02b0\u02b3\u0003v;\u0000\u02b1"+
		"\u02b3\u0003z=\u0000\u02b2\u02ac\u0001\u0000\u0000\u0000\u02b2\u02ad\u0001"+
		"\u0000\u0000\u0000\u02b2\u02ae\u0001\u0000\u0000\u0000\u02b2\u02af\u0001"+
		"\u0000\u0000\u0000\u02b2\u02b0\u0001\u0000\u0000\u0000\u02b2\u02b1\u0001"+
		"\u0000\u0000\u0000\u02b3o\u0001\u0000\u0000\u0000\u02b4\u02b5\u0007\u0007"+
		"\u0000\u0000\u02b5q\u0001\u0000\u0000\u0000\u02b6\u02b7\u0007\b\u0000"+
		"\u0000\u02b7s\u0001\u0000\u0000\u0000\u02b8\u02b9\u0007\t\u0000\u0000"+
		"\u02b9u\u0001\u0000\u0000\u0000\u02ba\u02c9\u0005p\u0000\u0000\u02bb\u02c9"+
		"\u0005r\u0000\u0000\u02bc\u02c9\u0005q\u0000\u0000\u02bd\u02c9\u0005,"+
		"\u0000\u0000\u02be\u02c9\u0005.\u0000\u0000\u02bf\u02c9\u0005/\u0000\u0000"+
		"\u02c0\u02c9\u0005\u008b\u0000\u0000\u02c1\u02c2\u0007\n\u0000\u0000\u02c2"+
		"\u02c9\u00051\u0000\u0000\u02c3\u02c4\u0007\n\u0000\u0000\u02c4\u02c9"+
		"\u0005\u00ad\u0000\u0000\u02c5\u02c6\u0007\u000b\u0000\u0000\u02c6\u02c9"+
		"\u00052\u0000\u0000\u02c7\u02c9\u0005`\u0000\u0000\u02c8\u02ba\u0001\u0000"+
		"\u0000\u0000\u02c8\u02bb\u0001\u0000\u0000\u0000\u02c8\u02bc\u0001\u0000"+
		"\u0000\u0000\u02c8\u02bd\u0001\u0000\u0000\u0000\u02c8\u02be\u0001\u0000"+
		"\u0000\u0000\u02c8\u02bf\u0001\u0000\u0000\u0000\u02c8\u02c0\u0001\u0000"+
		"\u0000\u0000\u02c8\u02c1\u0001\u0000\u0000\u0000\u02c8\u02c3\u0001\u0000"+
		"\u0000\u0000\u02c8\u02c5\u0001\u0000\u0000\u0000\u02c8\u02c7\u0001\u0000"+
		"\u0000\u0000\u02c9w\u0001\u0000\u0000\u0000\u02ca\u02cb\u0007\f\u0000"+
		"\u0000\u02cby\u0001\u0000\u0000\u0000\u02cc\u02d8\u0005\u00ce\u0000\u0000"+
		"\u02cd\u02ce\u0005\u0006\u0000\u0000\u02ce\u02d3\u0005\u00cd\u0000\u0000"+
		"\u02cf\u02d0\u0005\u0001\u0000\u0000\u02d0\u02d2\u0005\u00cd\u0000\u0000"+
		"\u02d1\u02cf\u0001\u0000\u0000\u0000\u02d2\u02d5\u0001\u0000\u0000\u0000"+
		"\u02d3\u02d1\u0001\u0000\u0000\u0000\u02d3\u02d4\u0001\u0000\u0000\u0000"+
		"\u02d4\u02d6\u0001\u0000\u0000\u0000\u02d5\u02d3\u0001\u0000\u0000\u0000"+
		"\u02d6\u02d8\u0005\u0007\u0000\u0000\u02d7\u02cc\u0001\u0000\u0000\u0000"+
		"\u02d7\u02cd\u0001\u0000\u0000\u0000\u02d8{\u0001\u0000\u0000\u0000\u02d9"+
		"\u02da\u0006>\uffff\uffff\u0000\u02da\u02db\u0005\u0002\u0000\u0000\u02db"+
		"\u02dc\u0003|>\u0000\u02dc\u02dd\u0005\u0003\u0000\u0000\u02dd\u02fb\u0001"+
		"\u0000\u0000\u0000\u02de\u02df\u0005\u0002\u0000\u0000\u02df\u02e2\u0003"+
		"\u00c0`\u0000\u02e0\u02e1\u0005\u0001\u0000\u0000\u02e1\u02e3\u0003\u00c0"+
		"`\u0000\u02e2\u02e0\u0001\u0000\u0000\u0000\u02e3\u02e4\u0001\u0000\u0000"+
		"\u0000\u02e4\u02e2\u0001\u0000\u0000\u0000\u02e4\u02e5\u0001\u0000\u0000"+
		"\u0000\u02e5\u02e6\u0001\u0000\u0000\u0000\u02e6\u02e7\u0005\u0003\u0000"+
		"\u0000\u02e7\u02fb\u0001\u0000\u0000\u0000\u02e8\u02e9\u0005\u0002\u0000"+
		"\u0000\u02e9\u02ea\u0003N\'\u0000\u02ea\u02eb\u0005\u0003\u0000\u0000"+
		"\u02eb\u02fb\u0001\u0000\u0000\u0000\u02ec\u02fb\u0003~?\u0000\u02ed\u02ee"+
		"\u0007\r\u0000\u0000\u02ee\u02fb\u0003t:\u0000\u02ef\u02f0\u0007\r\u0000"+
		"\u0000\u02f0\u02fb\u0003|>\t\u02f1\u02f2\u00053\u0000\u0000\u02f2\u02f3"+
		"\u0005\u0089\u0000\u0000\u02f3\u02fb\u0005\u00c1\u0000\u0000\u02f4\u02f5"+
		"\u00053\u0000\u0000\u02f5\u02f6\u0005\u0089\u0000\u0000\u02f6\u02fb\u0005"+
		"\u007f\u0000\u0000\u02f7\u02f8\u0005\u00c1\u0000\u0000\u02f8\u02f9\u0005"+
		"\u0089\u0000\u0000\u02f9\u02fb\u0005\u00c7\u0000\u0000\u02fa\u02d9\u0001"+
		"\u0000\u0000\u0000\u02fa\u02de\u0001\u0000\u0000\u0000\u02fa\u02e8\u0001"+
		"\u0000\u0000\u0000\u02fa\u02ec\u0001\u0000\u0000\u0000\u02fa\u02ed\u0001"+
		"\u0000\u0000\u0000\u02fa\u02ef\u0001\u0000\u0000\u0000\u02fa\u02f1\u0001"+
		"\u0000\u0000\u0000\u02fa\u02f4\u0001\u0000\u0000\u0000\u02fa\u02f7\u0001"+
		"\u0000\u0000\u0000\u02fb\u030c\u0001\u0000\u0000\u0000\u02fc\u02fd\n\u0006"+
		"\u0000\u0000\u02fd\u02fe\u0007\u000e\u0000\u0000\u02fe\u030b\u0003|>\u0007"+
		"\u02ff\u0300\n\u0005\u0000\u0000\u0300\u0301\u0007\r\u0000\u0000\u0301"+
		"\u030b\u0003|>\u0006\u0302\u0303\n\u0004\u0000\u0000\u0303\u0304\u0005"+
		"\u000b\u0000\u0000\u0304\u030b\u0003|>\u0005\u0305\u0306\n\b\u0000\u0000"+
		"\u0306\u030b\u0003x<\u0000\u0307\u0308\n\u0007\u0000\u0000\u0308\u0309"+
		"\u0005#\u0000\u0000\u0309\u030b\u0003x<\u0000\u030a\u02fc\u0001\u0000"+
		"\u0000\u0000\u030a\u02ff\u0001\u0000\u0000\u0000\u030a\u0302\u0001\u0000"+
		"\u0000\u0000\u030a\u0305\u0001\u0000\u0000\u0000\u030a\u0307\u0001\u0000"+
		"\u0000\u0000\u030b\u030e\u0001\u0000\u0000\u0000\u030c\u030a\u0001\u0000"+
		"\u0000\u0000\u030c\u030d\u0001\u0000\u0000\u0000\u030d}\u0001\u0000\u0000"+
		"\u0000\u030e\u030c\u0001\u0000\u0000\u0000\u030f\u0315\u0003\u008cF\u0000"+
		"\u0310\u0315\u0003n7\u0000\u0311\u0315\u0003\u00deo\u0000\u0312\u0315"+
		"\u0003\u0096K\u0000\u0313\u0315\u0003\u0084B\u0000\u0314\u030f\u0001\u0000"+
		"\u0000\u0000\u0314\u0310\u0001\u0000\u0000\u0000\u0314\u0311\u0001\u0000"+
		"\u0000\u0000\u0314\u0312\u0001\u0000\u0000\u0000\u0314\u0313\u0001\u0000"+
		"\u0000\u0000\u0315\u007f\u0001\u0000\u0000\u0000\u0316\u0319\u0003\u00e2"+
		"q\u0000\u0317\u0319\u0003\u0088D\u0000\u0318\u0316\u0001\u0000\u0000\u0000"+
		"\u0318\u0317\u0001\u0000\u0000\u0000\u0319\u0081\u0001\u0000\u0000\u0000"+
		"\u031a\u031c\u0003\u00ba]\u0000\u031b\u031d\u0003\u00bc^\u0000\u031c\u031b"+
		"\u0001\u0000\u0000\u0000\u031c\u031d\u0001\u0000\u0000\u0000\u031d\u0320"+
		"\u0001\u0000\u0000\u0000\u031e\u0320\u0003\u0084B\u0000\u031f\u031a\u0001"+
		"\u0000\u0000\u0000\u031f\u031e\u0001\u0000\u0000\u0000\u0320\u0083\u0001"+
		"\u0000\u0000\u0000\u0321\u0323\u0003\u0088D\u0000\u0322\u0324\u0003\u0086"+
		"C\u0000\u0323\u0322\u0001\u0000\u0000\u0000\u0323\u0324\u0001\u0000\u0000"+
		"\u0000\u0324\u0085\u0001\u0000\u0000\u0000\u0325\u0326\u0005\f\u0000\u0000"+
		"\u0326\u0327\u0003|>\u0000\u0327\u032a\u0005\r\u0000\u0000\u0328\u0329"+
		"\u0005\u000e\u0000\u0000\u0329\u032b\u0003\u0084B\u0000\u032a\u0328\u0001"+
		"\u0000\u0000\u0000\u032a\u032b\u0001\u0000\u0000\u0000\u032b\u0087\u0001"+
		"\u0000\u0000\u0000\u032c\u0330\u0003\u00e2q\u0000\u032d\u032f\u0003\u008a"+
		"E\u0000\u032e\u032d\u0001\u0000\u0000\u0000\u032f\u0332\u0001\u0000\u0000"+
		"\u0000\u0330\u032e\u0001\u0000\u0000\u0000\u0330\u0331\u0001\u0000\u0000"+
		"\u0000\u0331\u0089\u0001\u0000\u0000\u0000\u0332\u0330\u0001\u0000\u0000"+
		"\u0000\u0333\u0334\u0005\u000e\u0000\u0000\u0334\u0335\u0003\u00e2q\u0000"+
		"\u0335\u008b\u0001\u0000\u0000\u0000\u0336\u0339\u0003\u008eG\u0000\u0337"+
		"\u0339\u0003\u0090H\u0000\u0338\u0336\u0001\u0000\u0000\u0000\u0338\u0337"+
		"\u0001\u0000\u0000\u0000\u0339\u008d\u0001\u0000\u0000\u0000\u033a\u033b"+
		"\u0005$\u0000\u0000\u033b\u033d\u0003\u00c0`\u0000\u033c\u033e\u0003\u0092"+
		"I\u0000\u033d\u033c\u0001\u0000\u0000\u0000\u033e\u033f\u0001\u0000\u0000"+
		"\u0000\u033f\u033d\u0001\u0000\u0000\u0000\u033f\u0340\u0001\u0000\u0000"+
		"\u0000\u0340\u0343\u0001\u0000\u0000\u0000\u0341\u0342\u0005;\u0000\u0000"+
		"\u0342\u0344\u0003\u00c0`\u0000\u0343\u0341\u0001\u0000\u0000\u0000\u0343"+
		"\u0344\u0001\u0000\u0000\u0000\u0344\u0345\u0001\u0000\u0000\u0000\u0345"+
		"\u0346\u0005=\u0000\u0000\u0346\u008f\u0001\u0000\u0000\u0000\u0347\u0349"+
		"\u0005$\u0000\u0000\u0348\u034a\u0003\u0094J\u0000\u0349\u0348\u0001\u0000"+
		"\u0000\u0000\u034a\u034b\u0001\u0000\u0000\u0000\u034b\u0349\u0001\u0000"+
		"\u0000\u0000\u034b\u034c\u0001\u0000\u0000\u0000\u034c\u034f\u0001\u0000"+
		"\u0000\u0000\u034d\u034e\u0005;\u0000\u0000\u034e\u0350\u0003\u00c0`\u0000"+
		"\u034f\u034d\u0001\u0000\u0000\u0000\u034f\u0350\u0001\u0000\u0000\u0000"+
		"\u0350\u0351\u0001\u0000\u0000\u0000\u0351\u0352\u0005=\u0000\u0000\u0352"+
		"\u0091\u0001\u0000\u0000\u0000\u0353\u0354\u0005\u00c2\u0000\u0000\u0354"+
		"\u0355\u0003|>\u0000\u0355\u0356\u0005\u00ab\u0000\u0000\u0356\u0357\u0003"+
		"\u00c0`\u0000\u0357\u0093\u0001\u0000\u0000\u0000\u0358\u0359\u0005\u00c2"+
		"\u0000\u0000\u0359\u035a\u0003\u00be_\u0000\u035a\u035b\u0005\u00ab\u0000"+
		"\u0000\u035b\u035c\u0003\u00c0`\u0000\u035c\u0095\u0001\u0000\u0000\u0000"+
		"\u035d\u035e\u0003\u00e6s\u0000\u035e\u0361\u0005\u0002\u0000\u0000\u035f"+
		"\u0362\u0003\u0098L\u0000\u0360\u0362\u0005\u001e\u0000\u0000\u0361\u035f"+
		"\u0001\u0000\u0000\u0000\u0361\u0360\u0001\u0000\u0000\u0000\u0361\u0362"+
		"\u0001\u0000\u0000\u0000\u0362\u0363\u0001\u0000\u0000\u0000\u0363\u0365"+
		"\u0005\u0003\u0000\u0000\u0364\u0366\u0003\u00bc^\u0000\u0365\u0364\u0001"+
		"\u0000\u0000\u0000\u0365\u0366\u0001\u0000\u0000\u0000\u0366\u0368\u0001"+
		"\u0000\u0000\u0000\u0367\u0369\u0003\u009aM\u0000\u0368\u0367\u0001\u0000"+
		"\u0000\u0000\u0368\u0369\u0001\u0000\u0000\u0000\u0369\u036b\u0001\u0000"+
		"\u0000\u0000\u036a\u036c\u0003\u009cN\u0000\u036b\u036a\u0001\u0000\u0000"+
		"\u0000\u036b\u036c\u0001\u0000\u0000\u0000\u036c\u036e\u0001\u0000\u0000"+
		"\u0000\u036d\u036f\u0003\u009eO\u0000\u036e\u036d\u0001\u0000\u0000\u0000"+
		"\u036e\u036f\u0001\u0000\u0000\u0000\u036f\u037c\u0001\u0000\u0000\u0000"+
		"\u0370\u0371\u0003\u00e6s\u0000\u0371\u0372\u0005\u0002\u0000\u0000\u0372"+
		"\u0373\u0003N\'\u0000\u0373\u0374\u0005\u0003\u0000\u0000\u0374\u037c"+
		"\u0001\u0000\u0000\u0000\u0375\u037c\u0003\u00aaU\u0000\u0376\u037c\u0003"+
		"\u00b0X\u0000\u0377\u037c\u0003\u00b2Y\u0000\u0378\u037c\u0003\u00b6["+
		"\u0000\u0379\u037c\u0003\u00b8\\\u0000\u037a\u037c\u0003\u00ba]\u0000"+
		"\u037b\u035d\u0001\u0000\u0000\u0000\u037b\u0370\u0001\u0000\u0000\u0000"+
		"\u037b\u0375\u0001\u0000\u0000\u0000\u037b\u0376\u0001\u0000\u0000\u0000"+
		"\u037b\u0377\u0001\u0000\u0000\u0000\u037b\u0378\u0001\u0000\u0000\u0000"+
		"\u037b\u0379\u0001\u0000\u0000\u0000\u037b\u037a\u0001\u0000\u0000\u0000"+
		"\u037c\u0097\u0001\u0000\u0000\u0000\u037d\u037f\u00058\u0000\u0000\u037e"+
		"\u037d\u0001\u0000\u0000\u0000\u037e\u037f\u0001\u0000\u0000\u0000\u037f"+
		"\u0380\u0001\u0000\u0000\u0000\u0380\u0385\u0003\u00c0`\u0000\u0381\u0382"+
		"\u0005\u0001\u0000\u0000\u0382\u0384\u0003\u00c0`\u0000\u0383\u0381\u0001"+
		"\u0000\u0000\u0000\u0384\u0387\u0001\u0000\u0000\u0000\u0385\u0383\u0001"+
		"\u0000\u0000\u0000\u0385\u0386\u0001\u0000\u0000\u0000\u0386\u0099\u0001"+
		"\u0000\u0000\u0000\u0387\u0385\u0001\u0000\u0000\u0000\u0388\u0389\u0005"+
		"J\u0000\u0000\u0389\u038a\u0005\u0002\u0000\u0000\u038a\u038b\u0003\\"+
		".\u0000\u038b\u038c\u0005\u0003\u0000\u0000\u038c\u009b\u0001\u0000\u0000"+
		"\u0000\u038d\u038e\u0005\u00c5\u0000\u0000\u038e\u038f\u0005T\u0000\u0000"+
		"\u038f\u0390\u0005\u0002\u0000\u0000\u0390\u0391\u0003h4\u0000\u0391\u0392"+
		"\u0005\u0003\u0000\u0000\u0392\u009d\u0001\u0000\u0000\u0000\u0393\u0394"+
		"\u0005\u0092\u0000\u0000\u0394\u0396\u0005\u0002\u0000\u0000\u0395\u0397"+
		"\u0003\u00a0P\u0000\u0396\u0395\u0001\u0000\u0000\u0000\u0396\u0397\u0001"+
		"\u0000\u0000\u0000\u0397\u0399\u0001\u0000\u0000\u0000\u0398\u039a\u0003"+
		"h4\u0000\u0399\u0398\u0001\u0000\u0000\u0000\u0399\u039a\u0001\u0000\u0000"+
		"\u0000\u039a\u039c\u0001\u0000\u0000\u0000\u039b\u039d\u0003\u00a2Q\u0000"+
		"\u039c\u039b\u0001\u0000\u0000\u0000\u039c\u039d\u0001\u0000\u0000\u0000"+
		"\u039d\u039e\u0001\u0000\u0000\u0000\u039e\u039f\u0005\u0003\u0000\u0000"+
		"\u039f\u009f\u0001\u0000\u0000\u0000\u03a0\u03a1\u0005\u0096\u0000\u0000"+
		"\u03a1\u03a2\u0005#\u0000\u0000\u03a2\u03a7\u0003|>\u0000\u03a3\u03a4"+
		"\u0005\u0001\u0000\u0000\u03a4\u03a6\u0003|>\u0000\u03a5\u03a3\u0001\u0000"+
		"\u0000\u0000\u03a6\u03a9\u0001\u0000\u0000\u0000\u03a7\u03a5\u0001\u0000"+
		"\u0000\u0000\u03a7\u03a8\u0001\u0000\u0000\u0000\u03a8\u00a1\u0001\u0000"+
		"\u0000\u0000\u03a9\u03a7\u0001\u0000\u0000\u0000\u03aa\u03ab\u0007\u000f"+
		"\u0000\u0000\u03ab\u03ad\u0003\u00a4R\u0000\u03ac\u03ae\u0003\u00a6S\u0000"+
		"\u03ad\u03ac\u0001\u0000\u0000\u0000\u03ad\u03ae\u0001\u0000\u0000\u0000"+
		"\u03ae\u03b8\u0001\u0000\u0000\u0000\u03af\u03b0\u0007\u000f\u0000\u0000"+
		"\u03b0\u03b1\u0005 \u0000\u0000\u03b1\u03b2\u0003\u00a4R\u0000\u03b2\u03b3"+
		"\u0005\u001a\u0000\u0000\u03b3\u03b5\u0003\u00a8T\u0000\u03b4\u03b6\u0003"+
		"\u00a6S\u0000\u03b5\u03b4\u0001\u0000\u0000\u0000\u03b5\u03b6\u0001\u0000"+
		"\u0000\u0000\u03b6\u03b8\u0001\u0000\u0000\u0000\u03b7\u03aa\u0001\u0000"+
		"\u0000\u0000\u03b7\u03af\u0001\u0000\u0000\u0000\u03b8\u00a3\u0001\u0000"+
		"\u0000\u0000\u03b9\u03ba\u0005\u00b9\u0000\u0000\u03ba\u03c4\u0005\u009b"+
		"\u0000\u0000\u03bb\u03bc\u0003|>\u0000\u03bc\u03bd\u0005\u009b\u0000\u0000"+
		"\u03bd\u03c4\u0001\u0000\u0000\u0000\u03be\u03bf\u0005+\u0000\u0000\u03bf"+
		"\u03c4\u0005\u00a1\u0000\u0000\u03c0\u03c1\u0003|>\u0000\u03c1\u03c2\u0005"+
		"N\u0000\u0000\u03c2\u03c4\u0001\u0000\u0000\u0000\u03c3\u03b9\u0001\u0000"+
		"\u0000\u0000\u03c3\u03bb\u0001\u0000\u0000\u0000\u03c3\u03be\u0001\u0000"+
		"\u0000\u0000\u03c3\u03c0\u0001\u0000\u0000\u0000\u03c4\u00a5\u0001\u0000"+
		"\u0000\u0000\u03c5\u03c6\u0005D\u0000\u0000\u03c6\u03c7\u0005+\u0000\u0000"+
		"\u03c7\u03d0\u0005\u00a1\u0000\u0000\u03c8\u03c9\u0005D\u0000\u0000\u03c9"+
		"\u03d0\u0005T\u0000\u0000\u03ca\u03cb\u0005D\u0000\u0000\u03cb\u03d0\u0005"+
		"\u00ac\u0000\u0000\u03cc\u03cd\u0005D\u0000\u0000\u03cd\u03ce\u0005\u0084"+
		"\u0000\u0000\u03ce\u03d0\u0005\u0090\u0000\u0000\u03cf\u03c5\u0001\u0000"+
		"\u0000\u0000\u03cf\u03c8\u0001\u0000\u0000\u0000\u03cf\u03ca\u0001\u0000"+
		"\u0000\u0000\u03cf\u03cc\u0001\u0000\u0000\u0000\u03d0\u00a7\u0001\u0000"+
		"\u0000\u0000\u03d1\u03d2\u0003|>\u0000\u03d2\u03d3\u0005\u009b\u0000\u0000"+
		"\u03d3\u03dc\u0001\u0000\u0000\u0000\u03d4\u03d5\u0005+\u0000\u0000\u03d5"+
		"\u03dc\u0005\u00a1\u0000\u0000\u03d6\u03d7\u0003|>\u0000\u03d7\u03d8\u0005"+
		"N\u0000\u0000\u03d8\u03dc\u0001\u0000\u0000\u0000\u03d9\u03da\u0005\u00b9"+
		"\u0000\u0000\u03da\u03dc\u0005N\u0000\u0000\u03db\u03d1\u0001\u0000\u0000"+
		"\u0000\u03db\u03d4\u0001\u0000\u0000\u0000\u03db\u03d6\u0001\u0000\u0000"+
		"\u0000\u03db\u03d9\u0001\u0000\u0000\u0000\u03dc\u00a9\u0001\u0000\u0000"+
		"\u0000\u03dd\u03de\u0005%\u0000\u0000\u03de\u03df\u0005\u0002\u0000\u0000"+
		"\u03df\u03e0\u0003|>\u0000\u03e0\u03e1\u0005\u001c\u0000\u0000\u03e1\u03e2"+
		"\u0003\u00acV\u0000\u03e2\u03e3\u0005\u0003\u0000\u0000\u03e3\u00ab\u0001"+
		"\u0000\u0000\u0000\u03e4\u03ec\u0003\u00aeW\u0000\u03e5\u03e6\u0005\u0002"+
		"\u0000\u0000\u03e6\u03e9\u0005\u00cb\u0000\u0000\u03e7\u03e8\u0005\u0001"+
		"\u0000\u0000\u03e8\u03ea\u0005\u00cb\u0000\u0000\u03e9\u03e7\u0001\u0000"+
		"\u0000\u0000\u03e9\u03ea\u0001\u0000\u0000\u0000\u03ea\u03eb\u0001\u0000"+
		"\u0000\u0000\u03eb\u03ed\u0005\u0003\u0000\u0000\u03ec\u03e5\u0001\u0000"+
		"\u0000\u0000\u03ec\u03ed\u0001\u0000\u0000\u0000\u03ed\u00ad\u0001\u0000"+
		"\u0000\u0000\u03ee\u03ef\u0003\u00e2q\u0000\u03ef\u03f0\u0006W\uffff\uffff"+
		"\u0000\u03f0\u03f7\u0001\u0000\u0000\u0000\u03f1\u03f2\u0005\u000e\u0000"+
		"\u0000\u03f2\u03f3\u0003\u00e2q\u0000\u03f3\u03f4\u0006W\uffff\uffff\u0000"+
		"\u03f4\u03f6\u0001\u0000\u0000\u0000\u03f5\u03f1\u0001\u0000\u0000\u0000"+
		"\u03f6\u03f9\u0001\u0000\u0000\u0000\u03f7\u03f5\u0001\u0000\u0000\u0000"+
		"\u03f7\u03f8\u0001\u0000\u0000\u0000\u03f8\u00af\u0001\u0000\u0000\u0000"+
		"\u03f9\u03f7\u0001\u0000\u0000\u0000\u03fa\u03fb\u0005G\u0000\u0000\u03fb"+
		"\u03fc\u0005\u0002\u0000\u0000\u03fc\u03fd\u0003|>\u0000\u03fd\u03fe\u0005"+
		"Q\u0000\u0000\u03fe\u03ff\u0003|>\u0000\u03ff\u0400\u0005\u0003\u0000"+
		"\u0000\u0400\u0407\u0001\u0000\u0000\u0000\u0401\u0402\u0003\u00b4Z\u0000"+
		"\u0402\u0403\u0005\u0002\u0000\u0000\u0403\u0404\u0003|>\u0000\u0404\u0405"+
		"\u0005\u0003\u0000\u0000\u0405\u0407\u0001\u0000\u0000\u0000\u0406\u03fa"+
		"\u0001\u0000\u0000\u0000\u0406\u0401\u0001\u0000\u0000\u0000\u0407\u00b1"+
		"\u0001\u0000\u0000\u0000\u0408\u0409\u0005\u00b4\u0000\u0000\u0409\u040b"+
		"\u0005\u0002\u0000\u0000\u040a\u040c\u0007\u0010\u0000\u0000\u040b\u040a"+
		"\u0001\u0000\u0000\u0000\u040b\u040c\u0001\u0000\u0000\u0000\u040c\u040e"+
		"\u0001\u0000\u0000\u0000\u040d\u040f\u0003r9\u0000\u040e\u040d\u0001\u0000"+
		"\u0000\u0000\u040e\u040f\u0001\u0000\u0000\u0000\u040f\u0411\u0001\u0000"+
		"\u0000\u0000\u0410\u0412\u0005Q\u0000\u0000\u0411\u0410\u0001\u0000\u0000"+
		"\u0000\u0411\u0412\u0001\u0000\u0000\u0000\u0412\u0413\u0001\u0000\u0000"+
		"\u0000\u0413\u0414\u0003|>\u0000\u0414\u0415\u0005\u0003\u0000\u0000\u0415"+
		"\u00b3\u0001\u0000\u0000\u0000\u0416\u0417\u0007\f\u0000\u0000\u0417\u00b5"+
		"\u0001\u0000\u0000\u0000\u0418\u0419\u0007\u0011\u0000\u0000\u0419\u041a"+
		"\u0005\u0002\u0000\u0000\u041a\u041b\u0003\u00be_\u0000\u041b\u041c\u0005"+
		"\u0003\u0000\u0000\u041c\u0429\u0001\u0000\u0000\u0000\u041d\u041e\u0007"+
		"\u0011\u0000\u0000\u041e\u041f\u0005\u0002\u0000\u0000\u041f\u0420\u0003"+
		"N\'\u0000\u0420\u0421\u0005\u0003\u0000\u0000\u0421\u0429\u0001\u0000"+
		"\u0000\u0000\u0422\u0423\u0007\u0011\u0000\u0000\u0423\u0424\u0007\u0012"+
		"\u0000\u0000\u0424\u0425\u0005\u0002\u0000\u0000\u0425\u0426\u0003\u0088"+
		"D\u0000\u0426\u0427\u0005\u0003\u0000\u0000\u0427\u0429\u0001\u0000\u0000"+
		"\u0000\u0428\u0418\u0001\u0000\u0000\u0000\u0428\u041d\u0001\u0000\u0000"+
		"\u0000\u0428\u0422\u0001\u0000\u0000\u0000\u0429\u00b7\u0001\u0000\u0000"+
		"\u0000\u042a\u042b\u0007\u0013\u0000\u0000\u042b\u042c\u0005\u0002\u0000"+
		"\u0000\u042c\u042d\u0003\u00be_\u0000\u042d\u042e\u0005\u0003\u0000\u0000"+
		"\u042e\u043b\u0001\u0000\u0000\u0000\u042f\u0430\u0007\u0013\u0000\u0000"+
		"\u0430\u0431\u0005\u0002\u0000\u0000\u0431\u0432\u0003N\'\u0000\u0432"+
		"\u0433\u0005\u0003\u0000\u0000\u0433\u043b\u0001\u0000\u0000\u0000\u0434"+
		"\u0435\u0007\u0013\u0000\u0000\u0435\u0436\u0007\u0012\u0000\u0000\u0436"+
		"\u0437\u0005\u0002\u0000\u0000\u0437\u0438\u0003\u0088D\u0000\u0438\u0439"+
		"\u0005\u0003\u0000\u0000\u0439\u043b\u0001\u0000\u0000\u0000\u043a\u042a"+
		"\u0001\u0000\u0000\u0000\u043a\u042f\u0001\u0000\u0000\u0000\u043a\u0434"+
		"\u0001\u0000\u0000\u0000\u043b\u00b9\u0001\u0000\u0000\u0000\u043c\u043d"+
		"\u0005\u00b3\u0000\u0000\u043d\u043e\u0005\u0002\u0000\u0000\u043e\u043f"+
		"\u0003\u0082A\u0000\u043f\u0440\u0005\u001c\u0000\u0000\u0440\u0441\u0003"+
		"\u0088D\u0000\u0441\u0443\u0005\u0003\u0000\u0000\u0442\u0444\u0003\u00bc"+
		"^\u0000\u0443\u0442\u0001\u0000\u0000\u0000\u0443\u0444\u0001\u0000\u0000"+
		"\u0000\u0444\u00bb\u0001\u0000\u0000\u0000\u0445\u0446\u0005\u000e\u0000"+
		"\u0000\u0446\u0447\u0003\u0088D\u0000\u0447\u00bd\u0001\u0000\u0000\u0000"+
		"\u0448\u0449\u0006_\uffff\uffff\u0000\u0449\u044a\u0005\u0002\u0000\u0000"+
		"\u044a\u044b\u0003\u00be_\u0000\u044b\u044c\u0005\u0003\u0000\u0000\u044c"+
		"\u0458\u0001\u0000\u0000\u0000\u044d\u0458\u0003\u00c6c\u0000\u044e\u0458"+
		"\u0003\u00cae\u0000\u044f\u0458\u0003\u00c4b\u0000\u0450\u0458\u0003\u00c2"+
		"a\u0000\u0451\u0458\u0003\u00c8d\u0000\u0452\u0458\u0003\u00ceg\u0000"+
		"\u0453\u0458\u0003\u00d0h\u0000\u0454\u0455\u0005\u0085\u0000\u0000\u0455"+
		"\u0458\u0003\u00be_\u0004\u0456\u0458\u0003|>\u0000\u0457\u0448\u0001"+
		"\u0000\u0000\u0000\u0457\u044d\u0001\u0000\u0000\u0000\u0457\u044e\u0001"+
		"\u0000\u0000\u0000\u0457\u044f\u0001\u0000\u0000\u0000\u0457\u0450\u0001"+
		"\u0000\u0000\u0000\u0457\u0451\u0001\u0000\u0000\u0000\u0457\u0452\u0001"+
		"\u0000\u0000\u0000\u0457\u0453\u0001\u0000\u0000\u0000\u0457\u0454\u0001"+
		"\u0000\u0000\u0000\u0457\u0456\u0001\u0000\u0000\u0000\u0458\u0461\u0001"+
		"\u0000\u0000\u0000\u0459\u045a\n\u0003\u0000\u0000\u045a\u045b\u0005\u001a"+
		"\u0000\u0000\u045b\u0460\u0003\u00be_\u0004\u045c\u045d\n\u0002\u0000"+
		"\u0000\u045d\u045e\u0005\u008e\u0000\u0000\u045e\u0460\u0003\u00be_\u0003"+
		"\u045f\u0459\u0001\u0000\u0000\u0000\u045f\u045c\u0001\u0000\u0000\u0000"+
		"\u0460\u0463\u0001\u0000\u0000\u0000\u0461\u045f\u0001\u0000\u0000\u0000"+
		"\u0461\u0462\u0001\u0000\u0000\u0000\u0462\u00bf\u0001\u0000\u0000\u0000"+
		"\u0463\u0461\u0001\u0000\u0000\u0000\u0464\u0467\u0003|>\u0000\u0465\u0467"+
		"\u0003\u00be_\u0000\u0466\u0464\u0001\u0000\u0000\u0000\u0466\u0465\u0001"+
		"\u0000\u0000\u0000\u0467\u00c1\u0001\u0000\u0000\u0000\u0468\u0469\u0003"+
		"|>\u0000\u0469\u046a\u0007\u0014\u0000\u0000\u046a\u046b\u0003|>\u0000"+
		"\u046b\u00c3\u0001\u0000\u0000\u0000\u046c\u046e\u0003|>\u0000\u046d\u046f"+
		"\u0005\u0085\u0000\u0000\u046e\u046d\u0001\u0000\u0000\u0000\u046e\u046f"+
		"\u0001\u0000\u0000\u0000\u046f\u0470\u0001\u0000\u0000\u0000\u0470\u0471"+
		"\u0005 \u0000\u0000\u0471\u0472\u0003|>\u0000\u0472\u0473\u0005\u001a"+
		"\u0000\u0000\u0473\u0474\u0003|>\u0000\u0474\u00c5\u0001\u0000\u0000\u0000"+
		"\u0475\u0476\u0003|>\u0000\u0476\u0478\u0005c\u0000\u0000\u0477\u0479"+
		"\u0005\u0085\u0000\u0000\u0478\u0477\u0001\u0000\u0000\u0000\u0478\u0479"+
		"\u0001\u0000\u0000\u0000\u0479\u047a\u0001\u0000\u0000\u0000\u047a\u047b"+
		"\u0005\u0086\u0000\u0000\u047b\u0486\u0001\u0000\u0000\u0000\u047c\u047d"+
		"\u0003|>\u0000\u047d\u047f\u0005c\u0000\u0000\u047e\u0480\u0005\u0085"+
		"\u0000\u0000\u047f\u047e\u0001\u0000\u0000\u0000\u047f\u0480\u0001\u0000"+
		"\u0000\u0000\u0480\u0481\u0001\u0000\u0000\u0000\u0481\u0482\u00058\u0000"+
		"\u0000\u0482\u0483\u0005Q\u0000\u0000\u0483\u0484\u0003|>\u0000\u0484"+
		"\u0486\u0001\u0000\u0000\u0000\u0485\u0475\u0001\u0000\u0000\u0000\u0485"+
		"\u047c\u0001\u0000\u0000\u0000\u0486\u00c7\u0001\u0000\u0000\u0000\u0487"+
		"\u0489\u0003|>\u0000\u0488\u048a\u0005\u0085\u0000\u0000\u0489\u0488\u0001"+
		"\u0000\u0000\u0000\u0489\u048a\u0001\u0000\u0000\u0000\u048a\u048b\u0001"+
		"\u0000\u0000\u0000\u048b\u048d\u0007\u0015\u0000\u0000\u048c\u048e\u0005"+
		"\u0005\u0000\u0000\u048d\u048c\u0001\u0000\u0000\u0000\u048d\u048e\u0001"+
		"\u0000\u0000\u0000\u048e\u048f\u0001\u0000\u0000\u0000\u048f\u0491\u0003"+
		"|>\u0000\u0490\u0492\u0005\u0005\u0000\u0000\u0491\u0490\u0001\u0000\u0000"+
		"\u0000\u0491\u0492\u0001\u0000\u0000\u0000\u0492\u0498\u0001\u0000\u0000"+
		"\u0000\u0493\u0496\u0005A\u0000\u0000\u0494\u0497\u0003r9\u0000\u0495"+
		"\u0497\u0003\u00deo\u0000\u0496\u0494\u0001\u0000\u0000\u0000\u0496\u0495"+
		"\u0001\u0000\u0000\u0000\u0497\u0499\u0001\u0000\u0000\u0000\u0498\u0493"+
		"\u0001\u0000\u0000\u0000\u0498\u0499\u0001\u0000\u0000\u0000\u0499\u00c9"+
		"\u0001\u0000\u0000\u0000\u049a\u049c\u0003|>\u0000\u049b\u049d\u0005\u0085"+
		"\u0000\u0000\u049c\u049b\u0001\u0000\u0000\u0000\u049c\u049d\u0001\u0000"+
		"\u0000\u0000\u049d\u049e\u0001\u0000\u0000\u0000\u049e\u049f\u0005[\u0000"+
		"\u0000\u049f\u04a0\u0003\u00ccf\u0000\u04a0\u00cb\u0001\u0000\u0000\u0000"+
		"\u04a1\u04a2\u0007\u0012\u0000\u0000\u04a2\u04a3\u0005\u0002\u0000\u0000"+
		"\u04a3\u04a4\u0003\u0088D\u0000\u04a4\u04a5\u0005\u0003\u0000\u0000\u04a5"+
		"\u04b8\u0001\u0000\u0000\u0000\u04a6\u04a7\u0005\u0002\u0000\u0000\u04a7"+
		"\u04a8\u0003N\'\u0000\u04a8\u04a9\u0005\u0003\u0000\u0000\u04a9\u04b8"+
		"\u0001\u0000\u0000\u0000\u04aa\u04b8\u0003\u00deo\u0000\u04ab\u04b4\u0005"+
		"\u0002\u0000\u0000\u04ac\u04b1\u0003\u00c0`\u0000\u04ad\u04ae\u0005\u0001"+
		"\u0000\u0000\u04ae\u04b0\u0003\u00c0`\u0000\u04af\u04ad\u0001\u0000\u0000"+
		"\u0000\u04b0\u04b3\u0001\u0000\u0000\u0000\u04b1\u04af\u0001\u0000\u0000"+
		"\u0000\u04b1\u04b2\u0001\u0000\u0000\u0000\u04b2\u04b5\u0001\u0000\u0000"+
		"\u0000\u04b3\u04b1\u0001\u0000\u0000\u0000\u04b4\u04ac\u0001\u0000\u0000"+
		"\u0000\u04b4\u04b5\u0001\u0000\u0000\u0000\u04b5\u04b6\u0001\u0000\u0000"+
		"\u0000\u04b6\u04b8\u0005\u0003\u0000\u0000\u04b7\u04a1\u0001\u0000\u0000"+
		"\u0000\u04b7\u04a6\u0001\u0000\u0000\u0000\u04b7\u04aa\u0001\u0000\u0000"+
		"\u0000\u04b7\u04ab\u0001\u0000\u0000\u0000\u04b8\u00cd\u0001\u0000\u0000"+
		"\u0000\u04b9\u04ba\u0005E\u0000\u0000\u04ba\u04bb\u0007\u0012\u0000\u0000"+
		"\u04bb\u04bc\u0005\u0002\u0000\u0000\u04bc\u04bd\u0003\u0088D\u0000\u04bd"+
		"\u04be\u0005\u0003\u0000\u0000\u04be\u04c2\u0001\u0000\u0000\u0000\u04bf"+
		"\u04c0\u0005E\u0000\u0000\u04c0\u04c2\u0003|>\u0000\u04c1\u04b9\u0001"+
		"\u0000\u0000\u0000\u04c1\u04bf\u0001\u0000\u0000\u0000\u04c2\u00cf\u0001"+
		"\u0000\u0000\u0000\u04c3\u04c4\u0003|>\u0000\u04c4\u04c6\u0005c\u0000"+
		"\u0000\u04c5\u04c7\u0005\u0085\u0000\u0000\u04c6\u04c5\u0001\u0000\u0000"+
		"\u0000\u04c6\u04c7\u0001\u0000\u0000\u0000\u04c7\u04c8\u0001\u0000\u0000"+
		"\u0000\u04c8\u04c9\u0005<\u0000\u0000\u04c9\u04d3\u0001\u0000\u0000\u0000"+
		"\u04ca\u04cc\u0003|>\u0000\u04cb\u04cd\u0005\u0085\u0000\u0000\u04cc\u04cb"+
		"\u0001\u0000\u0000\u0000\u04cc\u04cd\u0001\u0000\u0000\u0000\u04cd\u04ce"+
		"\u0001\u0000\u0000\u0000\u04ce\u04cf\u0005x\u0000\u0000\u04cf\u04d0\u0005"+
		"\u0089\u0000\u0000\u04d0\u04d1\u0003\u0082A\u0000\u04d1\u04d3\u0001\u0000"+
		"\u0000\u0000\u04d2\u04c3\u0001\u0000\u0000\u0000\u04d2\u04ca\u0001\u0000"+
		"\u0000\u0000\u04d3\u00d1\u0001\u0000\u0000\u0000\u04d4\u04d8\u0005l\u0000"+
		"\u0000\u04d5\u04d8\u0005s\u0000\u0000\u04d6\u04d8\u0003\u0088D\u0000\u04d7"+
		"\u04d4\u0001\u0000\u0000\u0000\u04d7\u04d5\u0001\u0000\u0000\u0000\u04d7"+
		"\u04d6\u0001\u0000\u0000\u0000\u04d8\u00d3\u0001\u0000\u0000\u0000\u04d9"+
		"\u04de\u0003\u00d6k\u0000\u04da\u04db\u0005\u0001\u0000\u0000\u04db\u04dd"+
		"\u0003\u00d6k\u0000\u04dc\u04da\u0001\u0000\u0000\u0000\u04dd\u04e0\u0001"+
		"\u0000\u0000\u0000\u04de\u04dc\u0001\u0000\u0000\u0000\u04de\u04df\u0001"+
		"\u0000\u0000\u0000\u04df\u00d5\u0001\u0000\u0000\u0000\u04e0\u04de\u0001"+
		"\u0000\u0000\u0000\u04e1\u04e4\u0003\u00c0`\u0000\u04e2\u04e4\u0003:\u001d"+
		"\u0000\u04e3\u04e1\u0001\u0000\u0000\u0000\u04e3\u04e2\u0001\u0000\u0000"+
		"\u0000\u04e4\u04e6\u0001\u0000\u0000\u0000\u04e5\u04e7\u0003\u00dcn\u0000"+
		"\u04e6\u04e5\u0001\u0000\u0000\u0000\u04e6\u04e7\u0001\u0000\u0000\u0000"+
		"\u04e7\u00d7\u0001\u0000\u0000\u0000\u04e8\u04eb\u0003\u00deo\u0000\u04e9"+
		"\u04eb\u0005\u00cb\u0000\u0000\u04ea\u04e8\u0001\u0000\u0000\u0000\u04ea"+
		"\u04e9\u0001\u0000\u0000\u0000\u04eb\u00d9\u0001\u0000\u0000\u0000\u04ec"+
		"\u04ef\u0003\u00deo\u0000\u04ed\u04ef\u0003t:\u0000\u04ee\u04ec\u0001"+
		"\u0000\u0000\u0000\u04ee\u04ed\u0001\u0000\u0000\u0000\u04ef\u00db\u0001"+
		"\u0000\u0000\u0000\u04f0\u04f1\u0005\u001c\u0000\u0000\u04f1\u04f4\u0003"+
		"\u00e2q\u0000\u04f2\u04f4\u0003\u00e8t\u0000\u04f3\u04f0\u0001\u0000\u0000"+
		"\u0000\u04f3\u04f2\u0001\u0000\u0000\u0000\u04f4\u00dd\u0001\u0000\u0000"+
		"\u0000\u04f5\u04f6\u0005\u0016\u0000\u0000\u04f6\u0500\u0005\u00d0\u0000"+
		"\u0000\u04f7\u04f8\u0005\u0016\u0000\u0000\u04f8\u0500\u0003\u00e2q\u0000"+
		"\u04f9\u04fa\u0005\u0017\u0000\u0000\u04fa\u0500\u0005\u00d0\u0000\u0000"+
		"\u04fb\u04fd\u0005\u0017\u0000\u0000\u04fc\u04fe\u0005\u00cb\u0000\u0000"+
		"\u04fd\u04fc\u0001\u0000\u0000\u0000\u04fd\u04fe\u0001\u0000\u0000\u0000"+
		"\u04fe\u0500\u0001\u0000\u0000\u0000\u04ff\u04f5\u0001\u0000\u0000\u0000"+
		"\u04ff\u04f7\u0001\u0000\u0000\u0000\u04ff\u04f9\u0001\u0000\u0000\u0000"+
		"\u04ff\u04fb\u0001\u0000\u0000\u0000\u0500\u00df\u0001\u0000\u0000\u0000"+
		"\u0501\u0506\u0003\u00e2q\u0000\u0502\u0503\u0005\u000e\u0000\u0000\u0503"+
		"\u0505\u0003\u00e2q\u0000\u0504\u0502\u0001\u0000\u0000\u0000\u0505\u0508"+
		"\u0001\u0000\u0000\u0000\u0506\u0504\u0001\u0000\u0000\u0000\u0506\u0507"+
		"\u0001\u0000\u0000\u0000\u0507\u050b\u0001\u0000\u0000\u0000\u0508\u0506"+
		"\u0001\u0000\u0000\u0000\u0509\u050b\u0005\u00d0\u0000\u0000\u050a\u0501"+
		"\u0001\u0000\u0000\u0000\u050a\u0509\u0001\u0000\u0000\u0000\u050b\u00e1"+
		"\u0001\u0000\u0000\u0000\u050c\u050d\u0003\u00e8t\u0000\u050d\u00e3\u0001"+
		"\u0000\u0000\u0000\u050e\u050f\u0005\u00c8\u0000\u0000\u050f\u00e5\u0001"+
		"\u0000\u0000\u0000\u0510\u0515\u0003\u00e8t\u0000\u0511\u0512\u0005\u000e"+
		"\u0000\u0000\u0512\u0514\u0003\u00e8t\u0000\u0513\u0511\u0001\u0000\u0000"+
		"\u0000\u0514\u0517\u0001\u0000\u0000\u0000\u0515\u0513\u0001\u0000\u0000"+
		"\u0000\u0515\u0516\u0001\u0000\u0000\u0000\u0516\u00e7\u0001\u0000\u0000"+
		"\u0000\u0517\u0515\u0001\u0000\u0000\u0000\u0518\u051b\u0005\u00cf\u0000"+
		"\u0000\u0519\u051b\u0007\u0016\u0000\u0000\u051a\u0518\u0001\u0000\u0000"+
		"\u0000\u051a\u0519\u0001\u0000\u0000\u0000\u051b\u00e9\u0001\u0000\u0000"+
		"\u0000\u00a0\u00f1\u00f6\u00fe\u0107\u010d\u0110\u0116\u0119\u0128\u012d"+
		"\u0130\u013b\u013f\u0146\u014e\u0151\u0155\u0158\u015c\u015e\u0162\u0166"+
		"\u0168\u016b\u016d\u0171\u0174\u0177\u017f\u0186\u018c\u0190\u0193\u0199"+
		"\u019b\u01a0\u01a4\u01a8\u01ab\u01b1\u01b3\u01b7\u01bc\u01c0\u01c8\u01d1"+
		"\u01d5\u01d9\u01df\u01e7\u01f2\u01fb\u0207\u020e\u0212\u0215\u021a\u0227"+
		"\u022f\u0235\u023b\u0244\u0249\u024f\u0261\u0265\u0268\u026b\u026e\u0274"+
		"\u027f\u0288\u0292\u029b\u02a0\u02a4\u02a8\u02aa\u02b2\u02c8\u02d3\u02d7"+
		"\u02e4\u02fa\u030a\u030c\u0314\u0318\u031c\u031f\u0323\u032a\u0330\u0338"+
		"\u033f\u0343\u034b\u034f\u0361\u0365\u0368\u036b\u036e\u037b\u037e\u0385"+
		"\u0396\u0399\u039c\u03a7\u03ad\u03b5\u03b7\u03c3\u03cf\u03db\u03e9\u03ec"+
		"\u03f7\u0406\u040b\u040e\u0411\u0428\u043a\u0443\u0457\u045f\u0461\u0466"+
		"\u046e\u0478\u047f\u0485\u0489\u048d\u0491\u0496\u0498\u049c\u04b1\u04b4"+
		"\u04b7\u04c1\u04c6\u04cc\u04d2\u04d7\u04de\u04e3\u04e6\u04ea\u04ee\u04f3"+
		"\u04fd\u04ff\u0506\u050a\u0515\u051a";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}