// Generated from JavaProperties.g4 by ANTLR 4.13.2
package org.springframework.ide.vscode.java.properties.antlr.parser;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class JavaPropertiesParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		Backslash=1, Colon=2, Equals=3, Exclamation=4, Number=5, LineBreak=6, 
		Space=7, IdentifierChar=8;
	public static final int
		RULE_parse = 0, RULE_line = 1, RULE_propertyLine = 2, RULE_commentLine = 3, 
		RULE_emptyLine = 4, RULE_keyValuePair = 5, RULE_key = 6, RULE_keyChar = 7, 
		RULE_separatorAndValue = 8, RULE_valueChar = 9, RULE_anyChar = 10;
	private static String[] makeRuleNames() {
		return new String[] {
			"parse", "line", "propertyLine", "commentLine", "emptyLine", "keyValuePair", 
			"key", "keyChar", "separatorAndValue", "valueChar", "anyChar"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'\\'", "':'", "'='", "'!'", "'#'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "Backslash", "Colon", "Equals", "Exclamation", "Number", "LineBreak", 
			"Space", "IdentifierChar"
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
	public String getGrammarFileName() { return "JavaProperties.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public JavaPropertiesParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParseContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(JavaPropertiesParser.EOF, 0); }
		public List<LineContext> line() {
			return getRuleContexts(LineContext.class);
		}
		public LineContext line(int i) {
			return getRuleContext(LineContext.class,i);
		}
		public ParseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parse; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).enterParse(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).exitParse(this);
		}
	}

	public final ParseContext parse() throws RecognitionException {
		ParseContext _localctx = new ParseContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_parse);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(25);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 498L) != 0)) {
				{
				{
				setState(22);
				line();
				}
				}
				setState(27);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(28);
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
	public static class LineContext extends ParserRuleContext {
		public PropertyLineContext propertyLine() {
			return getRuleContext(PropertyLineContext.class,0);
		}
		public CommentLineContext commentLine() {
			return getRuleContext(CommentLineContext.class,0);
		}
		public EmptyLineContext emptyLine() {
			return getRuleContext(EmptyLineContext.class,0);
		}
		public LineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_line; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).enterLine(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).exitLine(this);
		}
	}

	public final LineContext line() throws RecognitionException {
		LineContext _localctx = new LineContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_line);
		try {
			setState(33);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(30);
				propertyLine();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(31);
				commentLine();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(32);
				emptyLine();
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
	public static class PropertyLineContext extends ParserRuleContext {
		public KeyValuePairContext keyValuePair() {
			return getRuleContext(KeyValuePairContext.class,0);
		}
		public List<TerminalNode> Space() { return getTokens(JavaPropertiesParser.Space); }
		public TerminalNode Space(int i) {
			return getToken(JavaPropertiesParser.Space, i);
		}
		public PropertyLineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_propertyLine; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).enterPropertyLine(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).exitPropertyLine(this);
		}
	}

	public final PropertyLineContext propertyLine() throws RecognitionException {
		PropertyLineContext _localctx = new PropertyLineContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_propertyLine);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(38);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Space) {
				{
				{
				setState(35);
				match(Space);
				}
				}
				setState(40);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(41);
			keyValuePair();
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
	public static class CommentLineContext extends ParserRuleContext {
		public TerminalNode Exclamation() { return getToken(JavaPropertiesParser.Exclamation, 0); }
		public TerminalNode Number() { return getToken(JavaPropertiesParser.Number, 0); }
		public List<TerminalNode> LineBreak() { return getTokens(JavaPropertiesParser.LineBreak); }
		public TerminalNode LineBreak(int i) {
			return getToken(JavaPropertiesParser.LineBreak, i);
		}
		public TerminalNode EOF() { return getToken(JavaPropertiesParser.EOF, 0); }
		public List<TerminalNode> Space() { return getTokens(JavaPropertiesParser.Space); }
		public TerminalNode Space(int i) {
			return getToken(JavaPropertiesParser.Space, i);
		}
		public CommentLineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_commentLine; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).enterCommentLine(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).exitCommentLine(this);
		}
	}

	public final CommentLineContext commentLine() throws RecognitionException {
		CommentLineContext _localctx = new CommentLineContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_commentLine);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(46);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Space) {
				{
				{
				setState(43);
				match(Space);
				}
				}
				setState(48);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(49);
			_la = _input.LA(1);
			if ( !(_la==Exclamation || _la==Number) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(53);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 446L) != 0)) {
				{
				{
				setState(50);
				_la = _input.LA(1);
				if ( _la <= 0 || (_la==LineBreak) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				}
				setState(55);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(56);
			_la = _input.LA(1);
			if ( !(_la==EOF || _la==LineBreak) ) {
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
	public static class EmptyLineContext extends ParserRuleContext {
		public TerminalNode LineBreak() { return getToken(JavaPropertiesParser.LineBreak, 0); }
		public List<TerminalNode> Space() { return getTokens(JavaPropertiesParser.Space); }
		public TerminalNode Space(int i) {
			return getToken(JavaPropertiesParser.Space, i);
		}
		public EmptyLineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_emptyLine; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).enterEmptyLine(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).exitEmptyLine(this);
		}
	}

	public final EmptyLineContext emptyLine() throws RecognitionException {
		EmptyLineContext _localctx = new EmptyLineContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_emptyLine);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(61);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Space) {
				{
				{
				setState(58);
				match(Space);
				}
				}
				setState(63);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(64);
			match(LineBreak);
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
	public static class KeyValuePairContext extends ParserRuleContext {
		public KeyContext key() {
			return getRuleContext(KeyContext.class,0);
		}
		public SeparatorAndValueContext separatorAndValue() {
			return getRuleContext(SeparatorAndValueContext.class,0);
		}
		public TerminalNode LineBreak() { return getToken(JavaPropertiesParser.LineBreak, 0); }
		public TerminalNode EOF() { return getToken(JavaPropertiesParser.EOF, 0); }
		public KeyValuePairContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_keyValuePair; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).enterKeyValuePair(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).exitKeyValuePair(this);
		}
	}

	public final KeyValuePairContext keyValuePair() throws RecognitionException {
		KeyValuePairContext _localctx = new KeyValuePairContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_keyValuePair);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(66);
			key();
			setState(67);
			separatorAndValue();
			setState(68);
			_la = _input.LA(1);
			if ( !(_la==EOF || _la==LineBreak) ) {
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
	public static class KeyContext extends ParserRuleContext {
		public List<KeyCharContext> keyChar() {
			return getRuleContexts(KeyCharContext.class);
		}
		public KeyCharContext keyChar(int i) {
			return getRuleContext(KeyCharContext.class,i);
		}
		public KeyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_key; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).enterKey(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).exitKey(this);
		}
	}

	public final KeyContext key() throws RecognitionException {
		KeyContext _localctx = new KeyContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_key);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(71); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(70);
				keyChar();
				}
				}
				setState(73); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==Backslash || _la==IdentifierChar );
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
	public static class KeyCharContext extends ParserRuleContext {
		public TerminalNode IdentifierChar() { return getToken(JavaPropertiesParser.IdentifierChar, 0); }
		public TerminalNode Backslash() { return getToken(JavaPropertiesParser.Backslash, 0); }
		public TerminalNode Colon() { return getToken(JavaPropertiesParser.Colon, 0); }
		public TerminalNode Equals() { return getToken(JavaPropertiesParser.Equals, 0); }
		public KeyCharContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_keyChar; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).enterKeyChar(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).exitKeyChar(this);
		}
	}

	public final KeyCharContext keyChar() throws RecognitionException {
		KeyCharContext _localctx = new KeyCharContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_keyChar);
		int _la;
		try {
			setState(78);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IdentifierChar:
				enterOuterAlt(_localctx, 1);
				{
				setState(75);
				match(IdentifierChar);
				}
				break;
			case Backslash:
				enterOuterAlt(_localctx, 2);
				{
				setState(76);
				match(Backslash);
				setState(77);
				_la = _input.LA(1);
				if ( !(_la==Colon || _la==Equals) ) {
				_errHandler.recoverInline(this);
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
	public static class SeparatorAndValueContext extends ParserRuleContext {
		public TerminalNode Space() { return getToken(JavaPropertiesParser.Space, 0); }
		public TerminalNode Colon() { return getToken(JavaPropertiesParser.Colon, 0); }
		public TerminalNode Equals() { return getToken(JavaPropertiesParser.Equals, 0); }
		public List<ValueCharContext> valueChar() {
			return getRuleContexts(ValueCharContext.class);
		}
		public ValueCharContext valueChar(int i) {
			return getRuleContext(ValueCharContext.class,i);
		}
		public SeparatorAndValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_separatorAndValue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).enterSeparatorAndValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).exitSeparatorAndValue(this);
		}
	}

	public final SeparatorAndValueContext separatorAndValue() throws RecognitionException {
		SeparatorAndValueContext _localctx = new SeparatorAndValueContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_separatorAndValue);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(80);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 140L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(84);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 446L) != 0)) {
				{
				{
				setState(81);
				valueChar();
				}
				}
				setState(86);
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
	public static class ValueCharContext extends ParserRuleContext {
		public TerminalNode IdentifierChar() { return getToken(JavaPropertiesParser.IdentifierChar, 0); }
		public TerminalNode Exclamation() { return getToken(JavaPropertiesParser.Exclamation, 0); }
		public TerminalNode Number() { return getToken(JavaPropertiesParser.Number, 0); }
		public TerminalNode Space() { return getToken(JavaPropertiesParser.Space, 0); }
		public TerminalNode Backslash() { return getToken(JavaPropertiesParser.Backslash, 0); }
		public AnyCharContext anyChar() {
			return getRuleContext(AnyCharContext.class,0);
		}
		public TerminalNode Equals() { return getToken(JavaPropertiesParser.Equals, 0); }
		public TerminalNode Colon() { return getToken(JavaPropertiesParser.Colon, 0); }
		public ValueCharContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_valueChar; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).enterValueChar(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).exitValueChar(this);
		}
	}

	public final ValueCharContext valueChar() throws RecognitionException {
		ValueCharContext _localctx = new ValueCharContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_valueChar);
		try {
			setState(95);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IdentifierChar:
				enterOuterAlt(_localctx, 1);
				{
				setState(87);
				match(IdentifierChar);
				}
				break;
			case Exclamation:
				enterOuterAlt(_localctx, 2);
				{
				setState(88);
				match(Exclamation);
				}
				break;
			case Number:
				enterOuterAlt(_localctx, 3);
				{
				setState(89);
				match(Number);
				}
				break;
			case Space:
				enterOuterAlt(_localctx, 4);
				{
				setState(90);
				match(Space);
				}
				break;
			case Backslash:
				enterOuterAlt(_localctx, 5);
				{
				setState(91);
				match(Backslash);
				setState(92);
				anyChar();
				}
				break;
			case Equals:
				enterOuterAlt(_localctx, 6);
				{
				setState(93);
				match(Equals);
				}
				break;
			case Colon:
				enterOuterAlt(_localctx, 7);
				{
				setState(94);
				match(Colon);
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
	public static class AnyCharContext extends ParserRuleContext {
		public TerminalNode LineBreak() { return getToken(JavaPropertiesParser.LineBreak, 0); }
		public TerminalNode IdentifierChar() { return getToken(JavaPropertiesParser.IdentifierChar, 0); }
		public TerminalNode Backslash() { return getToken(JavaPropertiesParser.Backslash, 0); }
		public TerminalNode Colon() { return getToken(JavaPropertiesParser.Colon, 0); }
		public TerminalNode Equals() { return getToken(JavaPropertiesParser.Equals, 0); }
		public TerminalNode Space() { return getToken(JavaPropertiesParser.Space, 0); }
		public TerminalNode Exclamation() { return getToken(JavaPropertiesParser.Exclamation, 0); }
		public TerminalNode Number() { return getToken(JavaPropertiesParser.Number, 0); }
		public AnyCharContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_anyChar; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).enterAnyChar(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof JavaPropertiesListener ) ((JavaPropertiesListener)listener).exitAnyChar(this);
		}
	}

	public final AnyCharContext anyChar() throws RecognitionException {
		AnyCharContext _localctx = new AnyCharContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_anyChar);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(97);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 510L) != 0)) ) {
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

	public static final String _serializedATN =
		"\u0004\u0001\bd\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0001\u0000\u0005\u0000\u0018"+
		"\b\u0000\n\u0000\f\u0000\u001b\t\u0000\u0001\u0000\u0001\u0000\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0003\u0001\"\b\u0001\u0001\u0002\u0005\u0002"+
		"%\b\u0002\n\u0002\f\u0002(\t\u0002\u0001\u0002\u0001\u0002\u0001\u0003"+
		"\u0005\u0003-\b\u0003\n\u0003\f\u00030\t\u0003\u0001\u0003\u0001\u0003"+
		"\u0005\u00034\b\u0003\n\u0003\f\u00037\t\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0004\u0005\u0004<\b\u0004\n\u0004\f\u0004?\t\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0006"+
		"\u0004\u0006H\b\u0006\u000b\u0006\f\u0006I\u0001\u0007\u0001\u0007\u0001"+
		"\u0007\u0003\u0007O\b\u0007\u0001\b\u0001\b\u0005\bS\b\b\n\b\f\bV\t\b"+
		"\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0003"+
		"\t`\b\t\u0001\n\u0001\n\u0001\n\u0000\u0000\u000b\u0000\u0002\u0004\u0006"+
		"\b\n\f\u000e\u0010\u0012\u0014\u0000\u0006\u0001\u0000\u0004\u0005\u0001"+
		"\u0000\u0006\u0006\u0001\u0001\u0006\u0006\u0001\u0000\u0002\u0003\u0002"+
		"\u0000\u0002\u0003\u0007\u0007\u0001\u0000\u0001\bh\u0000\u0019\u0001"+
		"\u0000\u0000\u0000\u0002!\u0001\u0000\u0000\u0000\u0004&\u0001\u0000\u0000"+
		"\u0000\u0006.\u0001\u0000\u0000\u0000\b=\u0001\u0000\u0000\u0000\nB\u0001"+
		"\u0000\u0000\u0000\fG\u0001\u0000\u0000\u0000\u000eN\u0001\u0000\u0000"+
		"\u0000\u0010P\u0001\u0000\u0000\u0000\u0012_\u0001\u0000\u0000\u0000\u0014"+
		"a\u0001\u0000\u0000\u0000\u0016\u0018\u0003\u0002\u0001\u0000\u0017\u0016"+
		"\u0001\u0000\u0000\u0000\u0018\u001b\u0001\u0000\u0000\u0000\u0019\u0017"+
		"\u0001\u0000\u0000\u0000\u0019\u001a\u0001\u0000\u0000\u0000\u001a\u001c"+
		"\u0001\u0000\u0000\u0000\u001b\u0019\u0001\u0000\u0000\u0000\u001c\u001d"+
		"\u0005\u0000\u0000\u0001\u001d\u0001\u0001\u0000\u0000\u0000\u001e\"\u0003"+
		"\u0004\u0002\u0000\u001f\"\u0003\u0006\u0003\u0000 \"\u0003\b\u0004\u0000"+
		"!\u001e\u0001\u0000\u0000\u0000!\u001f\u0001\u0000\u0000\u0000! \u0001"+
		"\u0000\u0000\u0000\"\u0003\u0001\u0000\u0000\u0000#%\u0005\u0007\u0000"+
		"\u0000$#\u0001\u0000\u0000\u0000%(\u0001\u0000\u0000\u0000&$\u0001\u0000"+
		"\u0000\u0000&\'\u0001\u0000\u0000\u0000\')\u0001\u0000\u0000\u0000(&\u0001"+
		"\u0000\u0000\u0000)*\u0003\n\u0005\u0000*\u0005\u0001\u0000\u0000\u0000"+
		"+-\u0005\u0007\u0000\u0000,+\u0001\u0000\u0000\u0000-0\u0001\u0000\u0000"+
		"\u0000.,\u0001\u0000\u0000\u0000./\u0001\u0000\u0000\u0000/1\u0001\u0000"+
		"\u0000\u00000.\u0001\u0000\u0000\u000015\u0007\u0000\u0000\u000024\b\u0001"+
		"\u0000\u000032\u0001\u0000\u0000\u000047\u0001\u0000\u0000\u000053\u0001"+
		"\u0000\u0000\u000056\u0001\u0000\u0000\u000068\u0001\u0000\u0000\u0000"+
		"75\u0001\u0000\u0000\u000089\u0007\u0002\u0000\u00009\u0007\u0001\u0000"+
		"\u0000\u0000:<\u0005\u0007\u0000\u0000;:\u0001\u0000\u0000\u0000<?\u0001"+
		"\u0000\u0000\u0000=;\u0001\u0000\u0000\u0000=>\u0001\u0000\u0000\u0000"+
		">@\u0001\u0000\u0000\u0000?=\u0001\u0000\u0000\u0000@A\u0005\u0006\u0000"+
		"\u0000A\t\u0001\u0000\u0000\u0000BC\u0003\f\u0006\u0000CD\u0003\u0010"+
		"\b\u0000DE\u0007\u0002\u0000\u0000E\u000b\u0001\u0000\u0000\u0000FH\u0003"+
		"\u000e\u0007\u0000GF\u0001\u0000\u0000\u0000HI\u0001\u0000\u0000\u0000"+
		"IG\u0001\u0000\u0000\u0000IJ\u0001\u0000\u0000\u0000J\r\u0001\u0000\u0000"+
		"\u0000KO\u0005\b\u0000\u0000LM\u0005\u0001\u0000\u0000MO\u0007\u0003\u0000"+
		"\u0000NK\u0001\u0000\u0000\u0000NL\u0001\u0000\u0000\u0000O\u000f\u0001"+
		"\u0000\u0000\u0000PT\u0007\u0004\u0000\u0000QS\u0003\u0012\t\u0000RQ\u0001"+
		"\u0000\u0000\u0000SV\u0001\u0000\u0000\u0000TR\u0001\u0000\u0000\u0000"+
		"TU\u0001\u0000\u0000\u0000U\u0011\u0001\u0000\u0000\u0000VT\u0001\u0000"+
		"\u0000\u0000W`\u0005\b\u0000\u0000X`\u0005\u0004\u0000\u0000Y`\u0005\u0005"+
		"\u0000\u0000Z`\u0005\u0007\u0000\u0000[\\\u0005\u0001\u0000\u0000\\`\u0003"+
		"\u0014\n\u0000]`\u0005\u0003\u0000\u0000^`\u0005\u0002\u0000\u0000_W\u0001"+
		"\u0000\u0000\u0000_X\u0001\u0000\u0000\u0000_Y\u0001\u0000\u0000\u0000"+
		"_Z\u0001\u0000\u0000\u0000_[\u0001\u0000\u0000\u0000_]\u0001\u0000\u0000"+
		"\u0000_^\u0001\u0000\u0000\u0000`\u0013\u0001\u0000\u0000\u0000ab\u0007"+
		"\u0005\u0000\u0000b\u0015\u0001\u0000\u0000\u0000\n\u0019!&.5=INT_";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}