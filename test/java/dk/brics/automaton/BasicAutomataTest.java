package dk.brics.automaton;

import static dk.brics.automaton.Fuzz.check;
import static dk.brics.automaton.Fuzz.checkEq;
import static dk.brics.automaton.Fuzz.show;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Checks the automata constructed by {@link BasicAutomata}, in particular the numerical ones
 * against {@link BigInteger} and {@link BigDecimal}.
 */
final class BasicAutomataTest {

	static final Pattern INTEGER = DatatypesTest.lexicalPattern("integer");
	static final Pattern DECIMAL = DatatypesTest.lexicalPattern("decimal");

	static BigDecimal decimal(String s) {
		s = s.trim();
		if (s.endsWith("."))
			s = s + "0";
		if (s.startsWith(".") || s.startsWith("+.") || s.startsWith("-."))
			s = s.replace(".", "0.");
		return new BigDecimal(s);
	}

	/** Random strings that look like (possibly invalid) decimal numbers. */
	static List<String> numberStrings(Random r, int count) {
		List<String> res = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			StringBuilder b = new StringBuilder();
			if (r.nextInt(4) == 0)
				b.append(" \t\n\r".charAt(r.nextInt(4)));
			if (r.nextInt(3) == 0)
				b.append("+-".charAt(r.nextInt(2)));
			b.append(Fuzz.randomString(r, "00123459", 4));
			if (r.nextBoolean()) {
				b.append('.');
				b.append(Fuzz.randomString(r, "0012345", 4));
			}
			if (r.nextInt(10) == 0)
				b.append(Fuzz.randomString(r, ".+-a", 1));
			if (r.nextInt(4) == 0)
				b.append(' ');
			res.add(b.toString());
		}
		res.add("");
		res.add(".");
		res.add("-");
		res.add(" ");
		return res;
	}

	@Test
	void interval() {
		Fuzz.repeat("makeInterval", 300, (r, ctx) -> {
			int min = r.nextInt(r.nextBoolean() ? 20 : 2000), max = min + r.nextInt(r.nextBoolean() ? 20 : 2000);
			int digits = r.nextBoolean() ? 0 : Integer.toString(max).length() + r.nextInt(2);
			ctx.add("min", min).add("max", max).add("digits", digits);
			Automaton a = BasicAutomata.makeInterval(min, max, digits);
			List<String> strings = new ArrayList<>(Fuzz.allStrings("0123456789", 2));
			for (int i = 0; i < 100; i++) {
				int v = r.nextBoolean() ? min + r.nextInt(max - min + 1) : r.nextInt(2 * max + 2);
				strings.add(Fuzz.pad(Integer.toString(v), r.nextInt(6)));
			}
			strings.add(Integer.toString(min - 1));
			strings.add(Integer.toString(max + 1));
			for (String s : strings) {
				boolean e = !s.isEmpty() && s.matches("[0-9]+") && (digits <= 0 || s.length() == digits);
				if (e) {
					BigInteger v = new BigInteger(s);
					e = v.compareTo(BigInteger.valueOf(min)) >= 0 && v.compareTo(BigInteger.valueOf(max)) <= 0;
				}
				checkEq(e, a.run(s), "makeInterval accepts " + show(s));
			}
		});
		assertThrows(IllegalArgumentException.class, () -> BasicAutomata.makeInterval(5, 4, 0), "min > max should be rejected");
		assertThrows(IllegalArgumentException.class, () -> BasicAutomata.makeInterval(5, 100, 2), "too few digits should be rejected");
	}

	@Test
	void min_and_max_integer() {
		Fuzz.repeat("makeMinMaxInteger", 300, (r, ctx) -> {
			String n = Fuzz.pad(Integer.toString(r.nextInt(r.nextBoolean() ? 30 : 100000)), r.nextInt(3) == 0 ? 6 : 0);
			ctx.add("n", n);
			BigInteger bn = new BigInteger(n);
			Automaton amax = BasicAutomata.makeMaxInteger(n), amin = BasicAutomata.makeMinInteger(n);
			List<String> strings = new ArrayList<>(Fuzz.allStrings("0123456789", 2));
			for (int i = 0; i < 100; i++)
				strings.add(Fuzz.pad(bn.add(BigInteger.valueOf(r.nextInt(21) - 10)).abs().toString(), r.nextInt(8)));
			strings.add("-1");
			strings.add("+1");
			strings.add(" 1");
			for (String s : strings) {
				boolean digits = s.matches("[0-9]+");
				checkEq(digits && new BigInteger(s).compareTo(bn) <= 0, amax.run(s), "makeMaxInteger(" + n + ") accepts " + show(s));
				checkEq(digits && new BigInteger(s).compareTo(bn) >= 0, amin.run(s), "makeMinInteger(" + n + ") accepts " + show(s));
			}
		});
	}

	@Test
	void integer_and_decimal_values() {
		Fuzz.repeat("makeValue", 300, (r, ctx) -> {
			List<String> strings = numberStrings(r, 150);
			String v = strings.get(r.nextInt(strings.size() - 4));
			ctx.add("value", show(v));
			if (DECIMAL.matcher(v).matches()) {
				BigDecimal bv = decimal(v);
				Automaton a = BasicAutomata.makeDecimalValue(v.trim());
				for (String s : strings)
					checkEq(DECIMAL.matcher(s).matches() && decimal(s).compareTo(bv) == 0, a.run(s), "makeDecimalValue(" + show(v.trim()) + ") accepts " + show(s));
			}
			if (INTEGER.matcher(v).matches()) {
				BigDecimal bv = decimal(v);
				Automaton a = BasicAutomata.makeIntegerValue(v.trim());
				for (String s : strings)
					checkEq(INTEGER.matcher(s).matches() && decimal(s).compareTo(bv) == 0, a.run(s), "makeIntegerValue(" + show(v.trim()) + ") accepts " + show(s));
			}
		});
	}

	/** Integer and fraction parts of a decimal, without leading zeros of the integer part and trailing zeros of the fraction part. */
	static String[] digitParts(String s) {
		s = s.trim();
		if (s.startsWith("+") || s.startsWith("-"))
			s = s.substring(1);
		int p = s.indexOf('.');
		String ip = p < 0 ? s : s.substring(0, p), fp = p < 0 ? "" : s.substring(p + 1);
		return new String[] {ip.replaceAll("^0+", ""), fp.replaceAll("0+$", "")};
	}

	/**
	 * Number of digits needed for the value: the value is i * 10^-n with |i| &lt; 10^d and 0 &lt;= n &lt;= d
	 * (XML Schema totalDigits facet).
	 */
	static int totalDigits(String s) {
		String[] parts = digitParts(s);
		return parts[0].length() + parts[1].length();
	}

	/** Number of fraction digits needed for the value (XML Schema fractionDigits facet). */
	static int fractionDigits(String s) {
		return digitParts(s)[1].length();
	}

	@Test
	void total_and_fraction_digits() {
		Fuzz.repeat("makeDigits", 100, (r, ctx) -> {
			int i = 1 + r.nextInt(4);
			ctx.add("i", i);
			Automaton total = BasicAutomata.makeTotalDigits(i), fraction = BasicAutomata.makeFractionDigits(i - 1);
			// the facet automata are only meaningful within the lexical space of xs:decimal
			for (String s : numberStrings(r, 200)) {
				if (!DECIMAL.matcher(s).matches())
					continue;
				checkEq(totalDigits(s) <= i, total.run(s), "makeTotalDigits(" + i + ") accepts " + show(s));
				checkEq(fractionDigits(s) <= i - 1, fraction.run(s), "makeFractionDigits(" + (i - 1) + ") accepts " + show(s));
			}
		});
	}

	@Test
	void string_matcher() {
		Fuzz.repeat("makeStringMatcher", 300, (r, ctx) -> {
			String w = Fuzz.randomString(r, "ab", 4);
			ctx.add("w", show(w));
			Automaton a = BasicAutomata.makeStringMatcher(w);
			check(Fuzz.isActuallyDeterministic(a), "makeStringMatcher should be deterministic");
			List<String> strings = new ArrayList<>(Fuzz.allStrings("abc", 5));
			for (int k = 0; k < 20; k++)
				strings.add(Fuzz.randomString(r, "abc", 3) + w + Fuzz.randomString(r, "abc", 3));
			for (String s : strings)
				checkEq(s.contains(w), a.run(s), "makeStringMatcher(" + show(w) + ") accepts " + show(s));
		});
	}

	@Test
	void simple_automata() {
		Fuzz.repeat("simple", 200, (r, ctx) -> {
			char c1 = Lang.POINTS[r.nextInt(Lang.POINTS.length)], c2 = Lang.POINTS[r.nextInt(Lang.POINTS.length)];
			String set = Fuzz.randomString(r, "abc\u0000\uffff", 4);
			String str = Fuzz.randomString(r, "abc\u0000\uffff", 4);
			ctx.add("c1", show("" + c1)).add("c2", show("" + c2)).add("set", show(set)).add("str", show(str));
			Automaton range = BasicAutomata.makeCharRange(c1, c2);
			Automaton cs = BasicAutomata.makeCharSet(set);
			Automaton st = BasicAutomata.makeString(str);
			for (String s : Fuzz.allStrings("abc\u0000\uffff`d", 2)) {
				checkEq(s.length() == 1 && c1 <= s.charAt(0) && s.charAt(0) <= c2, range.run(s), "makeCharRange accepts " + show(s));
				checkEq(s.length() == 1 && set.indexOf(s.charAt(0)) != -1, cs.run(s), "makeCharSet accepts " + show(s));
				checkEq(s.equals(str), st.run(s), "makeString accepts " + show(s));
				checkEq(s.length() == 1, BasicAutomata.makeAnyChar().run(s), "makeAnyChar");
				check(BasicAutomata.makeAnyString().run(s), "makeAnyString");
				check(!BasicAutomata.makeEmpty().run(s), "makeEmpty");
				checkEq(s.isEmpty(), BasicAutomata.makeEmptyString().run(s), "makeEmptyString");
				checkEq(s.length() == 1 && s.charAt(0) == c1, BasicAutomata.makeChar(c1).run(s), "makeChar");
			}
			for (Automaton a : new Automaton[] {range, cs, st, BasicAutomata.makeAnyChar(), BasicAutomata.makeAnyString(), BasicAutomata.makeEmpty(), BasicAutomata.makeEmptyString()}) {
				Fuzz.checkWellFormed(a, "basic automaton");
				check(Fuzz.isActuallyDeterministic(a), "basic automata should be deterministic");
			}
			checkEq(str, st.getSingleton(), "makeString is a singleton");
		});
	}

	@Test
	void string_union() {
		Fuzz.repeat("makeStringUnion", 300, (r, ctx) -> {
			List<String> strings = new ArrayList<>();
			int n = r.nextInt(8);
			for (int i = 0; i < n; i++)
				strings.add(Fuzz.randomString(r, r.nextBoolean() ? "ab" : "ab\u0000\uffff\ud800\udc00", 5));
			ctx.add("strings", show(strings));
			String[] input = strings.toArray(new String[0]);
			Automaton a = BasicAutomata.makeStringUnion(input);
			checkEq(new TreeSet<>(strings), new TreeSet<>(a.getFiniteStrings()), "makeStringUnion");
			check(Fuzz.isActuallyDeterministic(a), "makeStringUnion should be deterministic");
			Automaton m = a.clone();
			m.minimize();
			if (!a.isSingleton())
				checkEq(m.getNumberOfStates(), a.getNumberOfStates(), "makeStringUnion should be minimal");
			// the input array is sorted in place (in code point order)
			List<String> sorted = new ArrayList<>(Arrays.asList(input));
			sorted.sort(StringUnionOperations.LEXICOGRAPHIC_ORDER);
			checkEq(sorted, Arrays.asList(input), "the input array should be sorted");
		});
	}
}
