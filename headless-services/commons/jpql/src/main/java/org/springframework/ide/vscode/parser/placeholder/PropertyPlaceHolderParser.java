// Generated from PropertyPlaceHolder.g4 by ANTLR 4.13.2
package org.springframework.ide.vscode.parser.placeholder;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class PropertyPlaceHolderParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		Backslash=1, Colon=2, Equals=3, Exclamation=4, Number=5, Dot=6, LineBreak=7, 
		Space=8, Identifier=9;
	public static final int
		RULE_start = 0, RULE_line = 1, RULE_keyDefaultValuePair = 2, RULE_defaultValue = 3, 
		RULE_key = 4, RULE_identifier = 5, RULE_value = 6;
	private static String[] makeRuleNames() {
		return new String[] {
			"start", "line", "keyDefaultValuePair", "defaultValue", "key", "identifier", 
			"value"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'\\'", "':'", "'='", "'!'", "'#'", "'.'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "Backslash", "Colon", "Equals", "Exclamation", "Number", "Dot", 
			"LineBreak", "Space", "Identifier"
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
	public String getGrammarFileName() { return "PropertyPlaceHolder.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public PropertyPlaceHolderParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StartContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(PropertyPlaceHolderParser.EOF, 0); }
		public List<LineContext> line() {
			return getRuleContexts(LineContext.class);
		}
		public LineContext line(int i) {
			return getRuleContext(LineContext.class,i);
		}
		public StartContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_start; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).enterStart(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).exitStart(this);
		}
	}

	public final StartContext start() throws RecognitionException {
		StartContext _localctx = new StartContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_start);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(17);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Space || _la==Identifier) {
				{
				{
				setState(14);
				line();
				}
				}
				setState(19);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(20);
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
		public KeyDefaultValuePairContext keyDefaultValuePair() {
			return getRuleContext(KeyDefaultValuePairContext.class,0);
		}
		public TerminalNode EOF() { return getToken(PropertyPlaceHolderParser.EOF, 0); }
		public List<TerminalNode> Space() { return getTokens(PropertyPlaceHolderParser.Space); }
		public TerminalNode Space(int i) {
			return getToken(PropertyPlaceHolderParser.Space, i);
		}
		public LineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_line; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).enterLine(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).exitLine(this);
		}
	}

	public final LineContext line() throws RecognitionException {
		LineContext _localctx = new LineContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_line);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(25);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Space) {
				{
				{
				setState(22);
				match(Space);
				}
				}
				setState(27);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(28);
			keyDefaultValuePair();
			setState(32);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Space) {
				{
				{
				setState(29);
				match(Space);
				}
				}
				setState(34);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(35);
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
	public static class KeyDefaultValuePairContext extends ParserRuleContext {
		public KeyContext key() {
			return getRuleContext(KeyContext.class,0);
		}
		public DefaultValueContext defaultValue() {
			return getRuleContext(DefaultValueContext.class,0);
		}
		public KeyDefaultValuePairContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_keyDefaultValuePair; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).enterKeyDefaultValuePair(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).exitKeyDefaultValuePair(this);
		}
	}

	public final KeyDefaultValuePairContext keyDefaultValuePair() throws RecognitionException {
		KeyDefaultValuePairContext _localctx = new KeyDefaultValuePairContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_keyDefaultValuePair);
		try {
			setState(41);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(37);
				key();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(38);
				key();
				setState(39);
				defaultValue();
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
	public static class DefaultValueContext extends ParserRuleContext {
		public TerminalNode Colon() { return getToken(PropertyPlaceHolderParser.Colon, 0); }
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public DefaultValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_defaultValue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).enterDefaultValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).exitDefaultValue(this);
		}
	}

	public final DefaultValueContext defaultValue() throws RecognitionException {
		DefaultValueContext _localctx = new DefaultValueContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_defaultValue);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(43);
			match(Colon);
			setState(44);
			value();
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
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public List<TerminalNode> Dot() { return getTokens(PropertyPlaceHolderParser.Dot); }
		public TerminalNode Dot(int i) {
			return getToken(PropertyPlaceHolderParser.Dot, i);
		}
		public KeyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_key; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).enterKey(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).exitKey(this);
		}
	}

	public final KeyContext key() throws RecognitionException {
		KeyContext _localctx = new KeyContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_key);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(46);
			identifier();
			setState(51);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Dot) {
				{
				{
				setState(47);
				match(Dot);
				setState(48);
				identifier();
				}
				}
				setState(53);
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
	public static class IdentifierContext extends ParserRuleContext {
		public List<TerminalNode> Identifier() { return getTokens(PropertyPlaceHolderParser.Identifier); }
		public TerminalNode Identifier(int i) {
			return getToken(PropertyPlaceHolderParser.Identifier, i);
		}
		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).enterIdentifier(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).exitIdentifier(this);
		}
	}

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_identifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(55); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(54);
				match(Identifier);
				}
				}
				setState(57); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==Identifier );
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
	public static class ValueContext extends ParserRuleContext {
		public List<TerminalNode> Identifier() { return getTokens(PropertyPlaceHolderParser.Identifier); }
		public TerminalNode Identifier(int i) {
			return getToken(PropertyPlaceHolderParser.Identifier, i);
		}
		public List<TerminalNode> Exclamation() { return getTokens(PropertyPlaceHolderParser.Exclamation); }
		public TerminalNode Exclamation(int i) {
			return getToken(PropertyPlaceHolderParser.Exclamation, i);
		}
		public List<TerminalNode> Number() { return getTokens(PropertyPlaceHolderParser.Number); }
		public TerminalNode Number(int i) {
			return getToken(PropertyPlaceHolderParser.Number, i);
		}
		public List<TerminalNode> Space() { return getTokens(PropertyPlaceHolderParser.Space); }
		public TerminalNode Space(int i) {
			return getToken(PropertyPlaceHolderParser.Space, i);
		}
		public List<TerminalNode> Backslash() { return getTokens(PropertyPlaceHolderParser.Backslash); }
		public TerminalNode Backslash(int i) {
			return getToken(PropertyPlaceHolderParser.Backslash, i);
		}
		public List<TerminalNode> LineBreak() { return getTokens(PropertyPlaceHolderParser.LineBreak); }
		public TerminalNode LineBreak(int i) {
			return getToken(PropertyPlaceHolderParser.LineBreak, i);
		}
		public List<TerminalNode> Equals() { return getTokens(PropertyPlaceHolderParser.Equals); }
		public TerminalNode Equals(int i) {
			return getToken(PropertyPlaceHolderParser.Equals, i);
		}
		public List<TerminalNode> Colon() { return getTokens(PropertyPlaceHolderParser.Colon); }
		public TerminalNode Colon(int i) {
			return getToken(PropertyPlaceHolderParser.Colon, i);
		}
		public List<TerminalNode> Dot() { return getTokens(PropertyPlaceHolderParser.Dot); }
		public TerminalNode Dot(int i) {
			return getToken(PropertyPlaceHolderParser.Dot, i);
		}
		public ValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).enterValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PropertyPlaceHolderListener ) ((PropertyPlaceHolderListener)listener).exitValue(this);
		}
	}

	public final ValueContext value() throws RecognitionException {
		ValueContext _localctx = new ValueContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_value);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(72);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,7,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					setState(70);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
					case 1:
						{
						setState(59);
						match(Identifier);
						}
						break;
					case 2:
						{
						setState(60);
						match(Exclamation);
						}
						break;
					case 3:
						{
						setState(61);
						match(Number);
						}
						break;
					case 4:
						{
						setState(62);
						match(Space);
						}
						break;
					case 5:
						{
						setState(63);
						match(Backslash);
						setState(64);
						match(Backslash);
						}
						break;
					case 6:
						{
						setState(65);
						match(Backslash);
						setState(66);
						match(LineBreak);
						}
						break;
					case 7:
						{
						setState(67);
						match(Equals);
						}
						break;
					case 8:
						{
						setState(68);
						match(Colon);
						}
						break;
					case 9:
						{
						setState(69);
						match(Dot);
						}
						break;
					}
					} 
				}
				setState(74);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,7,_ctx);
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
		"\u0004\u0001\tL\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0001\u0000\u0005\u0000\u0010"+
		"\b\u0000\n\u0000\f\u0000\u0013\t\u0000\u0001\u0000\u0001\u0000\u0001\u0001"+
		"\u0005\u0001\u0018\b\u0001\n\u0001\f\u0001\u001b\t\u0001\u0001\u0001\u0001"+
		"\u0001\u0005\u0001\u001f\b\u0001\n\u0001\f\u0001\"\t\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u0002"+
		"*\b\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0005\u00042\b\u0004\n\u0004\f\u00045\t\u0004\u0001\u0005"+
		"\u0004\u00058\b\u0005\u000b\u0005\f\u00059\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0005\u0006G\b\u0006\n\u0006\f\u0006J\t"+
		"\u0006\u0001\u0006\u0000\u0000\u0007\u0000\u0002\u0004\u0006\b\n\f\u0000"+
		"\u0000S\u0000\u0011\u0001\u0000\u0000\u0000\u0002\u0019\u0001\u0000\u0000"+
		"\u0000\u0004)\u0001\u0000\u0000\u0000\u0006+\u0001\u0000\u0000\u0000\b"+
		".\u0001\u0000\u0000\u0000\n7\u0001\u0000\u0000\u0000\fH\u0001\u0000\u0000"+
		"\u0000\u000e\u0010\u0003\u0002\u0001\u0000\u000f\u000e\u0001\u0000\u0000"+
		"\u0000\u0010\u0013\u0001\u0000\u0000\u0000\u0011\u000f\u0001\u0000\u0000"+
		"\u0000\u0011\u0012\u0001\u0000\u0000\u0000\u0012\u0014\u0001\u0000\u0000"+
		"\u0000\u0013\u0011\u0001\u0000\u0000\u0000\u0014\u0015\u0005\u0000\u0000"+
		"\u0001\u0015\u0001\u0001\u0000\u0000\u0000\u0016\u0018\u0005\b\u0000\u0000"+
		"\u0017\u0016\u0001\u0000\u0000\u0000\u0018\u001b\u0001\u0000\u0000\u0000"+
		"\u0019\u0017\u0001\u0000\u0000\u0000\u0019\u001a\u0001\u0000\u0000\u0000"+
		"\u001a\u001c\u0001\u0000\u0000\u0000\u001b\u0019\u0001\u0000\u0000\u0000"+
		"\u001c \u0003\u0004\u0002\u0000\u001d\u001f\u0005\b\u0000\u0000\u001e"+
		"\u001d\u0001\u0000\u0000\u0000\u001f\"\u0001\u0000\u0000\u0000 \u001e"+
		"\u0001\u0000\u0000\u0000 !\u0001\u0000\u0000\u0000!#\u0001\u0000\u0000"+
		"\u0000\" \u0001\u0000\u0000\u0000#$\u0005\u0000\u0000\u0001$\u0003\u0001"+
		"\u0000\u0000\u0000%*\u0003\b\u0004\u0000&\'\u0003\b\u0004\u0000\'(\u0003"+
		"\u0006\u0003\u0000(*\u0001\u0000\u0000\u0000)%\u0001\u0000\u0000\u0000"+
		")&\u0001\u0000\u0000\u0000*\u0005\u0001\u0000\u0000\u0000+,\u0005\u0002"+
		"\u0000\u0000,-\u0003\f\u0006\u0000-\u0007\u0001\u0000\u0000\u0000.3\u0003"+
		"\n\u0005\u0000/0\u0005\u0006\u0000\u000002\u0003\n\u0005\u00001/\u0001"+
		"\u0000\u0000\u000025\u0001\u0000\u0000\u000031\u0001\u0000\u0000\u0000"+
		"34\u0001\u0000\u0000\u00004\t\u0001\u0000\u0000\u000053\u0001\u0000\u0000"+
		"\u000068\u0005\t\u0000\u000076\u0001\u0000\u0000\u000089\u0001\u0000\u0000"+
		"\u000097\u0001\u0000\u0000\u00009:\u0001\u0000\u0000\u0000:\u000b\u0001"+
		"\u0000\u0000\u0000;G\u0005\t\u0000\u0000<G\u0005\u0004\u0000\u0000=G\u0005"+
		"\u0005\u0000\u0000>G\u0005\b\u0000\u0000?@\u0005\u0001\u0000\u0000@G\u0005"+
		"\u0001\u0000\u0000AB\u0005\u0001\u0000\u0000BG\u0005\u0007\u0000\u0000"+
		"CG\u0005\u0003\u0000\u0000DG\u0005\u0002\u0000\u0000EG\u0005\u0006\u0000"+
		"\u0000F;\u0001\u0000\u0000\u0000F<\u0001\u0000\u0000\u0000F=\u0001\u0000"+
		"\u0000\u0000F>\u0001\u0000\u0000\u0000F?\u0001\u0000\u0000\u0000FA\u0001"+
		"\u0000\u0000\u0000FC\u0001\u0000\u0000\u0000FD\u0001\u0000\u0000\u0000"+
		"FE\u0001\u0000\u0000\u0000GJ\u0001\u0000\u0000\u0000HF\u0001\u0000\u0000"+
		"\u0000HI\u0001\u0000\u0000\u0000I\r\u0001\u0000\u0000\u0000JH\u0001\u0000"+
		"\u0000\u0000\b\u0011\u0019 )39FH";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}