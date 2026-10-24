package dk.brics.automaton;

import static dk.brics.automaton.Fuzz.check;
import static dk.brics.automaton.Fuzz.checkEq;
import static dk.brics.automaton.Fuzz.show;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/**
 * Checks the query operations against brute force computations using the reference oracles.
 */
final class QueryTest {

	/** The chars that start the character classes of the automaton (sufficient for lexicographically least strings). */
	private static String points(Automaton a) {
		Automaton x = a.clone();
		x.expandSingleton();
		StringBuilder b = new StringBuilder();
		for (char c : x.getStartPoints())
			b.append(c);
		return b.toString();
	}

	@Test
	void emptiness_totality_and_shortest_examples() {
		Fuzz.repeat("emptiness", 600, (r, ctx) -> {
			Lang l = r.nextBoolean() ? Lang.random(r) : Lang.NfaLang.random(r, false);
			ctx.add("L", l);
			Automaton a = l.automaton();
			String pts = points(a);
			int maxlen = pts.length() <= 6 ? 4 : 3;
			List<String> all = Fuzz.allStrings(pts, maxlen);
			// brute force: least accepted and rejected strings up to maxlen (all is in length-lexicographic order if pts is sorted)
			String acc = null, rej = null;
			for (String s : all) {
				if (acc == null && l.contains(s))
					acc = s;
				if (rej == null && !l.contains(s))
					rej = s;
			}
			String sa = a.getShortestExample(true);
			String sr = a.getShortestExample(false);
			if (acc != null)
				checkEq(acc, sa, "shortest accepted example");
			else if (sa != null) {
				check(sa.length() > maxlen && l.contains(sa), "shortest accepted example " + show(sa) + " should be longer than " + maxlen + " and accepted");
			}
			if (rej != null)
				checkEq(rej, sr, "shortest rejected example");
			else if (sr != null)
				check(sr.length() > maxlen && !l.contains(sr), "shortest rejected example " + show(sr) + " should be longer than " + maxlen + " and rejected");
			// emptiness etc. agree with the existence of examples
			checkEq(sa == null, a.isEmpty(), "isEmpty");
			checkEq(sr == null, a.isTotal(), "isTotal");
			boolean onlyEmpty = l.contains("") && a.minus(BasicAutomata.makeEmptyString()).getShortestExample(true) == null;
			checkEq(onlyEmpty, a.isEmptyString(), "isEmptyString");
			// the queries do not modify the language
			Fuzz.checkLanguage(a, l, Fuzz.samples(r, l), "automaton after queries");
		});
	}

	@Test
	void finiteness_and_enumeration() {
		Fuzz.repeat("finiteness", 600, (r, ctx) -> {
			Lang l = Lang.randomNarrow(r);
			ctx.add("L", l);
			Automaton a = l.automaton();
			Fuzz.assume(overAbc(a));
			// brute force over "abc": the language is finite iff no string of length in [n, 2n) is accepted,
			// where n is the number of states of the minimal automaton
			Automaton m = a.clone();
			m.minimize();
			int n = m.getNumberOfStates();
			Fuzz.assume(n <= 5);
			boolean infinite = false;
			Set<String> strings = new TreeSet<>();
			for (String s : Fuzz.allStrings("abc", 2 * n - 1))
				if (l.contains(s)) {
					if (s.length() >= n)
						infinite = true;
					else
						strings.add(s);
				}
			checkEq(!infinite, a.isFinite(), "isFinite");
			Set<String> fs = a.getFiniteStrings();
			if (infinite)
				check(fs == null, "getFiniteStrings should be null for an infinite language");
			else
				checkEq(strings, new TreeSet<>(fs), "getFiniteStrings");
			for (int limit = -1; limit <= 3; limit++) {
				Set<String> fl = a.getFiniteStrings(limit);
				boolean expectNull = infinite || (limit >= 0 && strings.size() > limit);
				if (expectNull)
					check(fl == null, "getFiniteStrings(" + limit + ") should be null");
				else
					checkEq(strings, new TreeSet<>(fl), "getFiniteStrings(" + limit + ")");
			}
			for (int len = 0; len <= 3; len++) {
				Set<String> expected = new TreeSet<>();
				for (String s : Fuzz.allStrings("abc", len))
					if (s.length() == len && l.contains(s))
						expected.add(s);
				checkEq(expected, new TreeSet<>(a.getStrings(len)), "getStrings(" + len + ")");
			}
			check(a.getStrings(-1).isEmpty(), "getStrings(-1) should be empty");
		});
	}

	@Test
	void live_and_accept_states() {
		Fuzz.repeat("live", 400, (r, ctx) -> {
			Lang l = Lang.NfaLang.random(r, false);
			ctx.add("L", l);
			Automaton a = l.automaton();
			Set<State> states = a.getStates();
			// reachable states, computed independently
			Set<State> reach = new HashSet<>();
			Deque<State> work = new ArrayDeque<>();
			reach.add(a.getInitialState());
			work.add(a.getInitialState());
			while (!work.isEmpty())
				for (Transition t : work.removeFirst().getTransitions())
					if (reach.add(t.getDest()))
						work.add(t.getDest());
			checkEq(reach, states, "getStates");
			// live states: an accept state is reachable
			Set<State> live = new HashSet<>();
			for (State s : states) {
				Set<State> from = new HashSet<>();
				Deque<State> w = new ArrayDeque<>();
				from.add(s);
				w.add(s);
				boolean found = false;
				while (!w.isEmpty() && !found) {
					State p = w.removeFirst();
					if (p.isAccept())
						found = true;
					for (Transition t : p.getTransitions())
						if (from.add(t.getDest()))
							w.add(t.getDest());
				}
				if (found)
					live.add(s);
			}
			checkEq(live, a.getLiveStates(), "getLiveStates");
			Set<State> accept = new HashSet<>();
			for (State s : states)
				if (s.isAccept())
					accept.add(s);
			checkEq(accept, a.getAcceptStates(), "getAcceptStates");
		});
	}

	@Test
	void single_chars() {
		Fuzz.repeat("singleChars", 400, (r, ctx) -> {
			Lang l = Lang.random(r);
			ctx.add("L", l);
			Automaton a = l.automaton();
			// a char is in singleChars iff it occurs in some accepted string
			Automaton sc = a.singleChars();
			Fuzz.checkWellFormed(sc, "singleChars");
			for (String s : Fuzz.samples(r, l))
				if (l.contains(s))
					for (char c : s.toCharArray())
						check(sc.run("" + c), "singleChars should contain " + show("" + c) + " which occurs in accepted " + show(s));
			Automaton any = BasicAutomata.makeAnyString();
			for (String c : Fuzz.walks(sc, r, 10)) {
				check(c.length() == 1, "singleChars should only accept single chars, got " + show(c));
				Automaton contains = any.concatenate(BasicAutomata.makeString(c)).concatenate(any);
				String w = a.intersection(contains).getShortestExample(true);
				check(w != null && l.contains(w), "singleChars accepts " + show(c) + ", which does not occur in any accepted string");
			}
		});
	}

	@Test
	void common_prefix() {
		Fuzz.repeat("commonPrefix", 400, (r, ctx) -> {
			Lang l = Lang.random(r);
			ctx.add("L", l);
			Automaton a = l.automaton();
			// the common prefix is a prefix of all accepted strings
			String p = a.getCommonPrefix();
			for (String s : Fuzz.samples(r, l))
				if (l.contains(s))
					check(s.startsWith(p), "common prefix " + show(p) + " is not a prefix of accepted " + show(s));
			// for minimal automata, it is the longest common prefix
			Automaton m = a.clone();
			m.minimize();
			String pm = m.getCommonPrefix();
			String sa = m.getShortestExample(true);
			if (sa != null) {
				check(sa.startsWith(pm), "common prefix of minimal automaton is not a prefix of " + show(sa));
				if (!l.contains(pm)) {
					Automaton after = m.intersection(BasicAutomata.makeString(pm).concatenate(BasicAutomata.makeAnyString()));
					check(isLongestCommonPrefix(after, pm), "common prefix " + show(pm) + " of minimal automaton is not the longest");
				}
			}
		});
	}

	@Test
	void common_prefix_of_finite_languages() {
		Fuzz.repeat("commonPrefix finite", 300, (r, ctx) -> {
			Lang.FiniteLang l = Lang.FiniteLang.random(r, "ab", 5, 5);
			ctx.add("L", l);
			Fuzz.assume(!l.strings.isEmpty());
			String lcp = null;
			for (String s : l.strings) {
				if (lcp == null)
					lcp = s;
				int i = 0;
				while (i < lcp.length() && i < s.length() && lcp.charAt(i) == s.charAt(i))
					i++;
				lcp = lcp.substring(0, i);
			}
			Automaton a = l.automaton();
			String p = a.getCommonPrefix();
			for (String s : l.strings)
				check(s.startsWith(p), "common prefix " + show(p) + " is not a prefix of " + show(s));
			// exact for deterministic automata
			a.determinize();
			checkEq(lcp, a.getCommonPrefix(), "common prefix of deterministic automaton");
		});
	}

	/** Checks that all transitions are labeled by a, b, or c (so enumerating strings over abc is exhaustive). */
	private static boolean overAbc(Automaton a) {
		Automaton x = a.clone();
		x.expandSingleton();
		for (State s : x.getStates())
			for (Transition t : s.getTransitions())
				if (t.getMin() < 'a' || t.getMax() > 'c')
					return false;
		return true;
	}

	/**
	 * Checks whether p is the longest common prefix of the strings in the language of <code>a</code>,
	 * which all start with p and do not include p, that is, whether they do not all continue with the same char.
	 */
	private static boolean isLongestCommonPrefix(Automaton a, String p) {
		Automaton any = BasicAutomata.makeAnyString();
		for (char c : points(a).toCharArray()) {
			Automaton x = a.intersection(BasicAutomata.makeString(p + c).concatenate(any));
			if (!x.isEmpty())
				return !x.equals(a);
		}
		return true;
	}
}
