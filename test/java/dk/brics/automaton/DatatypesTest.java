package dk.brics.automaton;

import static dk.brics.automaton.Fuzz.show;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Checks the pre-built automata of {@link Datatypes}: the Unicode tables for consistency, and
 * the XML Schema datatypes against their lexical spaces (XML Schema 1.0 Part 2, second edition).
 */
final class DatatypesTest {

	static final String[] CATEGORIES = {"L", "Lu Ll Lt Lm Lo", "M", "Mn Mc Me", "N", "Nd Nl No", "P", "Pc Pd Ps Pe Pi Pf Po",
			"Z", "Zs Zl Zp", "S", "Sm Sc Sk So", "C", "Cc Cf Co Cn"};

	@Test
	void names() {
		for (String n : new String[] {"NCName", "QName", "Char", "NameChar", "URI", "anyname", "noap", "whitespace", "whitespacechar",
				"string", "boolean", "decimal", "float", "integer", "duration", "dateTime", "time", "date", "gYearMonth", "gYear",
				"gMonthDay", "gDay", "gMonth", "hexBinary", "base64Binary", "NCName2", "NCNames", "QName2", "Nmtoken2", "Nmtokens",
				"Name2", "Names", "language", "BasicLatin", "Specials", "Tags", "Lu", "C"}) {
			assertTrue(Datatypes.exists(n), n + " should exist");
			Automaton a = Datatypes.get(n);
			assertTrue(a != null && !a.isEmpty(), n + " should be non-empty");
		}
		assertFalse(Datatypes.exists("NoSuchAutomaton"), "unknown name");
		assertThrows(IllegalArgumentException.class, () -> Datatypes.get("NoSuchAutomaton"), "get of unknown name should throw");
		assertTrue(Datatypes.isUnicodeBlockName("Specials") && !Datatypes.isUnicodeBlockName("Lu"), "isUnicodeBlockName");
		assertTrue(Datatypes.isUnicodeCategoryName("Lu") && !Datatypes.isUnicodeCategoryName("Specials"), "isUnicodeCategoryName");
		assertTrue(Datatypes.isXMLName("NCName") && !Datatypes.isXMLName("Lu"), "isXMLName");
	}

	@Test
	void unicode_categories() {
		Automaton all = BasicAutomata.makeEmpty();
		List<Automaton> subs = new ArrayList<>();
		for (int i = 0; i < CATEGORIES.length; i += 2) {
			Automaton major = Datatypes.get(CATEGORIES[i]);
			Automaton union = BasicAutomata.makeEmpty();
			for (String sub : CATEGORIES[i + 1].split(" ")) {
				Automaton a = Datatypes.get(sub);
				assertFalse(a.isEmpty(), sub + " should be non-empty");
				union = union.union(a);
				subs.add(a);
			}
			if (CATEGORIES[i].equals("C"))
				union = union.union(BasicAutomata.makeCharRange('\ud800', '\udfff')); // C also contains the surrogates (Cs)
			assertTrue(major.equals(union), CATEGORIES[i] + " should be the union of " + CATEGORIES[i + 1]);
			all = all.union(major);
		}
		// the categories are disjoint
		String[] names = String.join(" ", CATEGORIES[1], CATEGORIES[3], CATEGORIES[5], CATEGORIES[7], CATEGORIES[9], CATEGORIES[11], CATEGORIES[13]).split(" ");
		for (int i = 0; i < subs.size(); i++)
			for (int j = i + 1; j < subs.size(); j++)
				assertTrue(subs.get(i).intersection(subs.get(j)).isEmpty(), names[i] + " and " + names[j] + " should be disjoint");
		// code points in ranges (represented by their first and last code points in the Unicode data)
		String[][] members = {{"Lo", "\u3401"}, {"Lo", "\u4e01"}, {"Lo", "\u9fa4"}, {"Lo", "\uac01"}, {"Lo", "\ud840\udc01"}, {"Lo", "\ud869\uded5"},
				{"Co", "\ue001"}, {"Co", "\udb80\udc01"}, {"Co", "\udbff\udffd"}, {"L", "\u4e01"}, {"C", "\ue001"}};
		for (String[] m : members) {
			assertTrue(Datatypes.get(m[0]).run(m[1]), m[0] + " should contain " + show(m[1]));
			assertFalse(Datatypes.get("Cn").run(m[1]), "Cn should not contain " + show(m[1]));
		}
		assertTrue(Datatypes.get("Cn").run("\ud869\uded7"), "Cn should contain unassigned U+2A6D7");
		assertTrue(Datatypes.get("Cn").run("\u9fa6"), "Cn should contain unassigned U+9FA6");
		// every BMP char that is not a surrogate is in some category (except U+FFFE and U+FFFF, which are not XML chars)
		Automaton bmp = BasicAutomata.makeCharRange('\u0000', '\ud7ff').union(BasicAutomata.makeCharRange('\ue000', '\ufffd'));
		assertTrue(bmp.subsetOf(all), "every non-surrogate BMP char should be in a category, missing e.g. " + show(bmp.minus(all).getShortestExample(true)));
	}

	@Test
	void unicode_blocks() {
		List<String> blocks = new ArrayList<>();
		for (String n : new String[] {"BasicLatin", "Latin-1Supplement", "LatinExtended-A", "LatinExtended-B", "IPAExtensions", "SpacingModifierLetters",
				"CombiningDiacriticalMarks", "Greek", "Cyrillic", "Armenian", "Hebrew", "Arabic", "Syriac", "Thaana", "Devanagari", "Bengali",
				"Gurmukhi", "Gujarati", "Oriya", "Tamil", "Telugu", "Kannada", "Malayalam", "Sinhala", "Thai", "Lao", "Tibetan", "Myanmar",
				"Georgian", "HangulJamo", "Ethiopic", "Cherokee", "UnifiedCanadianAboriginalSyllabics", "Ogham", "Runic", "Khmer", "Mongolian",
				"LatinExtendedAdditional", "GreekExtended", "GeneralPunctuation", "SuperscriptsandSubscripts", "CurrencySymbols",
				"CombiningMarksforSymbols", "LetterlikeSymbols", "NumberForms", "Arrows", "MathematicalOperators", "MiscellaneousTechnical",
				"ControlPictures", "OpticalCharacterRecognition", "EnclosedAlphanumerics", "BoxDrawing", "BlockElements", "GeometricShapes",
				"MiscellaneousSymbols", "Dingbats", "BraillePatterns", "CJKRadicalsSupplement", "KangxiRadicals",
				"IdeographicDescriptionCharacters", "CJKSymbolsandPunctuation", "Hiragana", "Katakana", "Bopomofo", "HangulCompatibilityJamo",
				"Kanbun", "BopomofoExtended", "EnclosedCJKLettersandMonths", "CJKCompatibility", "CJKUnifiedIdeographsExtensionA",
				"CJKUnifiedIdeographs", "YiSyllables", "YiRadicals", "HangulSyllables", "CJKCompatibilityIdeographs",
				"AlphabeticPresentationForms", "ArabicPresentationForms-A", "CombiningHalfMarks", "CJKCompatibilityForms", "SmallFormVariants",
				"ArabicPresentationForms-B", "Specials", "HalfwidthandFullwidthForms", "OldItalic", "Gothic", "Deseret",
				"ByzantineMusicalSymbols", "MusicalSymbols", "MathematicalAlphanumericSymbols", "CJKUnifiedIdeographsExtensionB",
				"CJKCompatibilityIdeographsSupplement", "Tags"}) {
			assertTrue(Datatypes.isUnicodeBlockName(n), n + " should be a block name");
			blocks.add(n);
		}
		for (int i = 0; i < blocks.size(); i++) {
			Automaton a = Datatypes.get(blocks.get(i));
			assertFalse(a.isEmpty(), blocks.get(i) + " should be non-empty");
			for (int j = i + 1; j < blocks.size(); j++)
				assertTrue(a.intersection(Datatypes.get(blocks.get(j))).isEmpty(), blocks.get(i) + " and " + blocks.get(j) + " should be disjoint");
		}
		assertTrue(Datatypes.get("Specials").run("\ufeff"), "Specials should contain U+FEFF");
		assertTrue(Datatypes.get("Specials").run("\ufffd"), "Specials should contain U+FFFD");
	}

	// lexical spaces of XML Schema datatypes, with surrounding whitespace (whiteSpace facet "collapse")

	static final String WS = "[ \t\n\r]*";
	static final String YEAR = "-?([1-9][0-9]{3,}|0[0-9]{3})";
	static final String MONTH = "(0[1-9]|1[0-2])";
	static final String DAY = "(0[1-9]|[12][0-9]|3[01])";
	static final String TIME = "(([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]+)?|24:00:00(\\.0+)?)";
	static final String TZ = "(Z|[+-]((0[0-9]|1[0-3]):[0-5][0-9]|14:00))?";

	static final Map<String, String> LEXICAL = new LinkedHashMap<>();
	static {
		LEXICAL.put("boolean", "(true|false|1|0)");
		// (XML Schema 1.1 Part 2 makes the description of the decimal lexical space precise)
		LEXICAL.put("decimal", "[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)");
		LEXICAL.put("float", "([+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([Ee][+-]?[0-9]+)?|INF|-INF|NaN)");
		LEXICAL.put("integer", "[+-]?[0-9]+");
		LEXICAL.put("nonNegativeInteger", "(\\+?[0-9]+|-0+)");
		LEXICAL.put("positiveInteger", "\\+?0*[1-9][0-9]*");
		LEXICAL.put("nonPositiveInteger", "(-[0-9]+|\\+?0+)");
		LEXICAL.put("negativeInteger", "-0*[1-9][0-9]*");
		LEXICAL.put("hexBinary", "([0-9a-fA-F]{2})*");
		LEXICAL.put("language", "[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*");
		LEXICAL.put("dateTime", "(?!-?0000)" + YEAR + "-" + MONTH + "-" + DAY + "T" + TIME + TZ);
		LEXICAL.put("date", "(?!-?0000)" + YEAR + "-" + MONTH + "-" + DAY + TZ);
		LEXICAL.put("time", TIME + TZ);
		LEXICAL.put("gYearMonth", "(?!-?0000)" + YEAR + "-" + MONTH + TZ);
		LEXICAL.put("gYear", "(?!-?0000)" + YEAR + TZ);
		LEXICAL.put("gMonthDay", "--" + MONTH + "-" + DAY + TZ);
		LEXICAL.put("gDay", "---" + DAY + TZ);
		LEXICAL.put("gMonth", "--" + MONTH + TZ);
		LEXICAL.put("duration", "-?P(?=[0-9])([0-9]+Y)?([0-9]+M)?([0-9]+D)?(T(?=[0-9])([0-9]+H)?([0-9]+M)?([0-9]+(\\.[0-9]+)?S)?)?|-?P(?=T)(T(?=[0-9])([0-9]+H)?([0-9]+M)?([0-9]+(\\.[0-9]+)?S)?)");
	}

	/** Returns a pattern for the lexical space of the given datatype, with surrounding whitespace. */
	static Pattern lexicalPattern(String type) {
		return Pattern.compile(WS + "(" + LEXICAL.get(type) + ")" + WS);
	}

	/** Value ranges of the integer types. */
	static final Map<String, BigInteger[]> RANGES = new LinkedHashMap<>();
	static {
		RANGES.put("long", new BigInteger[] {BigInteger.valueOf(Long.MIN_VALUE), BigInteger.valueOf(Long.MAX_VALUE)});
		RANGES.put("int", new BigInteger[] {BigInteger.valueOf(Integer.MIN_VALUE), BigInteger.valueOf(Integer.MAX_VALUE)});
		RANGES.put("short", new BigInteger[] {BigInteger.valueOf(Short.MIN_VALUE), BigInteger.valueOf(Short.MAX_VALUE)});
		RANGES.put("byte", new BigInteger[] {BigInteger.valueOf(Byte.MIN_VALUE), BigInteger.valueOf(Byte.MAX_VALUE)});
		RANGES.put("unsignedLong", new BigInteger[] {BigInteger.ZERO, new BigInteger("18446744073709551615")});
		RANGES.put("unsignedInt", new BigInteger[] {BigInteger.ZERO, new BigInteger("4294967295")});
		RANGES.put("unsignedShort", new BigInteger[] {BigInteger.ZERO, BigInteger.valueOf(65535)});
		RANGES.put("unsignedByte", new BigInteger[] {BigInteger.ZERO, BigInteger.valueOf(255)});
	}

	static final String[] EXAMPLES = {"true", "0", "1", "-1.23", "12678967.543233", "+100000.00", "210", "1.", ".5", "-1E4", "1267.43233E12",
			"12.78e-2", "12", "INF", "-INF", "NaN", "-0", "+0", "0001", "-01", "0FB7", "en", "en-US", "1999-05-31T13:20:00-05:00",
			"2000-01-01T24:00:00", "-0045-12-31T23:59:59.999Z", "12345-01-01", "1999-05-31", "13:20:00", "13:20:00.5+14:00",
			"1999-05", "1999", "--05-31", "---31", "--05", "P1Y2M3DT10H30M", "-P120D", "PT1.5S", "P1Y", "PT36H",
			"127", "-128", "255", "32767", "-2147483648", "4294967295", "9223372036854775807", "18446744073709551615"};

	static List<String> candidates(Random r) {
		List<String> res = new ArrayList<>(Arrays.asList(EXAMPLES));
		String alphabet = "0123456789-+:.TZPYMDHSeE ";
		for (int i = 0; i < 3000; i++)
			res.add(Fuzz.mutate(r, EXAMPLES[r.nextInt(EXAMPLES.length)], alphabet, 1 + r.nextInt(2)));
		for (String e : EXAMPLES)
			res.add(" \t" + e + "\n");
		return res;
	}

	@Test
	void xml_schema_lexical_spaces() {
		Random r = new Random(Fuzz.SEED);
		List<String> strings = candidates(r);
		for (Map.Entry<String, String> e : LEXICAL.entrySet()) {
			Pattern p = lexicalPattern(e.getKey());
			Automaton a = Datatypes.get(e.getKey());
			for (String s : strings)
				assertEquals(p.matcher(s).matches(), a.run(s), e.getKey() + " accepts " + show(s));
		}
		Pattern integer = lexicalPattern("integer");
		for (Map.Entry<String, BigInteger[]> e : RANGES.entrySet()) {
			Automaton a = Datatypes.get(e.getKey());
			BigInteger[] range = e.getValue();
			List<String> values = new ArrayList<>(strings);
			for (BigInteger b : range)
				for (int d = -1; d <= 1; d++) {
					String v = b.add(BigInteger.valueOf(d)).toString();
					values.add(v);
					values.add(v.startsWith("-") ? "-0" + v.substring(1) : "+" + v);
				}
			for (String s : values) {
				boolean expected = integer.matcher(s).matches();
				if (expected) {
					BigInteger v = new BigInteger(s.trim());
					expected = v.compareTo(range[0]) >= 0 && v.compareTo(range[1]) <= 0;
					if (range[0].signum() == 0 && s.trim().startsWith("-"))
						expected = v.signum() == 0; // unsigned types allow a negative sign only for zero
				}
				assertEquals(expected, a.run(s), e.getKey() + " accepts " + show(s));
			}
		}
	}

	@Test
	void type_hierarchy() {
		String[][] subsets = {{"byte", "short"}, {"short", "int"}, {"int", "long"}, {"long", "integer"}, {"unsignedByte", "unsignedShort"},
				{"unsignedShort", "unsignedInt"}, {"unsignedInt", "unsignedLong"}, {"unsignedLong", "nonNegativeInteger"},
				{"positiveInteger", "nonNegativeInteger"}, {"negativeInteger", "nonPositiveInteger"}, {"nonNegativeInteger", "integer"},
				{"nonPositiveInteger", "integer"}, {"integer", "decimal"}, {"NCName2", "Name2"}, {"Name2", "Nmtoken2"}, {"NCName2", "QName2"}};
		for (String[] s : subsets)
			assertTrue(Datatypes.get(s[0]).subsetOf(Datatypes.get(s[1])), s[0] + " should be a subset of " + s[1]);
		assertTrue(Datatypes.get("nonNegativeInteger").union(Datatypes.get("nonPositiveInteger")).equals(Datatypes.get("integer")),
				"nonNegativeInteger | nonPositiveInteger = integer");
	}
}
