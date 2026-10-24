package dk.brics.automaton;

import static dk.brics.automaton.Fuzz.check;
import static dk.brics.automaton.Fuzz.checkEq;
import static dk.brics.automaton.Fuzz.show;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Checks regular expression parsing and conversion against the reference matcher.
 */
final class RegExpFuzzTest {

	@Test
	void parse_and_convert() {
		Fuzz.repeat("RegExp", 500, (r, ctx) -> {
			boolean extended = r.nextBoolean();
			Rx rx = Rx.random(r, 1 + r.nextInt(4), extended, null);
			Lang.RegexLang l = new Lang.RegexLang(rx, true, null, r);
			ctx.add("L", l);
			Set<String> samples = Fuzz.samples(r, l);
			RegExp re = new RegExp(l.regexp, RegExp.ALL);
			Fuzz.checkLanguage(re.toAutomaton(), l, samples, "toAutomaton()");
			Fuzz.checkLanguage(re.toAutomaton(false), l, samples, "toAutomaton(false)");
			// toString produces an equivalent regular expression
			String s = re.toString();
			ctx.add("toString", show(s));
			Fuzz.checkLanguage(new RegExp(s, RegExp.ALL).toAutomaton(), l, samples, "parsed toString()");
			if (!extended) {
				// without the optional syntax, the same language is obtained with and without syntax flags
				Fuzz.checkLanguage(new RegExp(l.regexp, RegExp.NONE).toAutomaton(), l, samples, "parsed with no syntax flags");
				Fuzz.checkLanguage(new RegExp(s, RegExp.NONE).toAutomaton(), l, samples, "parsed toString() with no syntax flags");
			}
			check(re.getIdentifiers().isEmpty(), "no identifiers expected");
		});
	}

	@Test
	void named_automata() {
		Fuzz.repeat("named", 300, (r, ctx) -> {
			List<String> names = Arrays.asList("x", "yy", "a.b", "1");
			Map<String, Lang> langs = new HashMap<>();
			for (String n : names) {
				Lang nl = Lang.random(r);
				langs.put(n, nl);
				ctx.add("<" + n + ">", nl);
			}
			Rx rx = Rx.random(r, 1 + r.nextInt(3), true, names);
			Lang.RegexLang l = new Lang.RegexLang(rx, r.nextBoolean(), langs, r);
			ctx.add("L", l);
			Set<String> samples = Fuzz.samples(r, l);
			RegExp re = new RegExp(l.regexp, RegExp.ALL);
			Set<String> ids = new TreeSet<>();
			collectNames(rx, ids);
			checkEq(ids, new TreeSet<>(re.getIdentifiers()), "getIdentifiers");
			Fuzz.checkLanguage(l.automaton(), l, samples, "toAutomaton(map)");
			// via an automaton provider
			AutomatonProvider p = name -> langs.containsKey(name) ? langs.get(name).automaton() : null;
			Fuzz.checkLanguage(re.toAutomaton(p), l, samples, "toAutomaton(provider)");
			// the given automata are not modified
			Map<String, Automaton> m = new HashMap<>();
			for (Map.Entry<String, Lang> e : langs.entrySet())
				m.put(e.getKey(), e.getValue().automaton());
			re.toAutomaton(m);
			for (Map.Entry<String, Lang> e : langs.entrySet())
				Fuzz.checkLanguage(m.get(e.getKey()), e.getValue(), samples, "named automaton after use");
			// a missing automaton is reported
			if (!ids.isEmpty())
				assertThrows(IllegalArgumentException.class, () -> re.toAutomaton(new HashMap<>()), "missing named automaton should be reported");
		});
	}

	private static void collectNames(Rx rx, Set<String> ids) {
		if (rx.kind == Rx.Kind.NAMED)
			ids.add(rx.name);
		if (rx.left != null)
			collectNames(rx.left, ids);
		if (rx.right != null)
			collectNames(rx.right, ids);
	}

	static final Pattern LARGE_REPETITION = Pattern.compile("\\{[0-9,]*[0-9]{2}");

	@Test
	void parser_robustness() {
		Fuzz.repeat("parser robustness", 3000, (r, ctx) -> {
			String s;
			if (r.nextBoolean())
				s = Fuzz.randomString(r, "ab|&?*+{},![]^-.#@\"()<>\\~0123456789", 10);
			else // mutation of a valid regular expression
				s = Fuzz.mutate(r, Rx.random(r, 3, true, null).render(r), "ab|&?*+{},[]^-.#@\"()<>\\~0", 1 + r.nextInt(3));
			ctx.add("regexp", show(s));
			// large repetition counts, such as {101}, can make the subset construction blow up
			Fuzz.assume(!LARGE_REPETITION.matcher(s).find());
			for (int flags : new int[] {RegExp.NONE, RegExp.ALL, RegExp.INTERSECTION | RegExp.COMPLEMENT, RegExp.INTERVAL}) {
				RegExp re;
				try {
					re = new RegExp(s, flags);
				} catch (IllegalArgumentException e) {
					continue;
				}
				Automaton a;
				try {
					a = re.toAutomaton(new HashMap<>());
				} catch (IllegalArgumentException e) {
					continue; // e.g. unknown named automaton
				}
				// if it can be parsed, then toString can be parsed to the same language
				Automaton b = new RegExp(re.toString(), flags).toAutomaton(new HashMap<>());
				check(a.equals(b), "toString() of " + show(s) + " with flags " + flags + " gives a different language: " + show(re.toString()));
			}
		});
	}

	@Test
	void interval_edge_cases() {
		checkInterval("<0-0>", new String[] {"0"}, new String[] {"", "00", "1", "-0"});
		checkInterval("<00-10>", new String[] {"00", "05", "10"}, new String[] {"0", "5", "010", "11"});
		checkInterval("<5-120>", new String[] {"5", "05", "0005", "99", "120", "0120"}, new String[] {"4", "121", "1200"});
		checkInterval("<120-5>", new String[] {"5", "05", "99", "120"}, new String[] {"4", "121"});
		checkInterval("<007-012>", new String[] {"007", "010", "012"}, new String[] {"7", "10", "0007", "013"});
	}

	private static void checkInterval(String regexp, String[] accepted, String[] rejected) {
		Automaton a = new RegExp(regexp).toAutomaton();
		for (String s : accepted)
			assertTrue(a.run(s), regexp + " should accept " + show(s));
		for (String s : rejected)
			assertFalse(a.run(s), regexp + " should reject " + show(s));
	}
}
