package dk.brics.automaton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A language with an independent membership oracle and a way to build an automaton for it.
 * <p>
 * The oracles are implemented directly (regular expression matching by dynamic programming,
 * NFA simulation, set membership) without using the library, so they can serve as reference
 * semantics for the library operations.
 */
abstract class Lang {

	/** Reference membership test. */
	abstract boolean contains(String s);

	/** Builds a fresh automaton for this language (never shared, so it may be mutated). */
	abstract Automaton automaton();

	/** Characters that are relevant for the language (used for generating test strings). */
	abstract Set<Character> chars();

	/** Some strings in the language (may be empty if none are found). */
	abstract List<String> members(Random r, int count);

	/** The finite set of strings in the language, or null if not known to be finite. */
	Set<String> finiteStrings() {
		return null;
	}

	/** Characters that are generally interesting as transition labels and in test strings. */
	static final char[] POINTS = {'\u0000', '`', 'a', 'b', 'c', 'd', 'z', '\uffff'};

	/** Adds the end points of the range and their neighbors outside the range. */
	static void addRangeChars(Set<Character> cs, char lo, char hi) {
		cs.add(lo);
		cs.add(hi);
		if (lo > Character.MIN_VALUE)
			cs.add((char) (lo - 1));
		if (hi < Character.MAX_VALUE)
			cs.add((char) (hi + 1));
	}

	// ---------------------------------------------------------------------------------------
	// Languages defined by predicates and by combinations of other languages
	// ---------------------------------------------------------------------------------------

	/** A language defined by a predicate, for oracles of operation results. */
	static Lang of(final String desc, final Predicate<String> p, final Lang... from) {
		return new Lang() {
			@Override
			boolean contains(String s) {
				return p.test(s);
			}

			@Override
			Automaton automaton() {
				throw new UnsupportedOperationException();
			}

			@Override
			Set<Character> chars() {
				Set<Character> cs = new TreeSet<>();
				for (Lang l : from)
					cs.addAll(l.chars());
				return cs;
			}

			@Override
			List<String> members(Random r, int count) {
				return new ArrayList<>();
			}

			@Override
			public String toString() {
				return desc + Arrays.toString(from);
			}
		};
	}

	/** A language with the given oracle and automata built by the given supplier. */
	static Lang built(final Lang oracle, final Supplier<Automaton> supplier) {
		return new Lang() {
			@Override
			boolean contains(String s) {
				return oracle.contains(s);
			}

			@Override
			Automaton automaton() {
				return supplier.get();
			}

			@Override
			Set<Character> chars() {
				return oracle.chars();
			}

			@Override
			List<String> members(Random r, int count) {
				List<String> res = oracle.members(r, count);
				if (res.isEmpty())
					res = Fuzz.walks(automaton(), r, count);
				return res;
			}

			@Override
			Set<String> finiteStrings() {
				return oracle.finiteStrings();
			}

			@Override
			public String toString() {
				return oracle.toString();
			}
		};
	}

	static Lang union(final Lang a, final Lang b) {
		return of("union", s -> a.contains(s) || b.contains(s), a, b);
	}

	static Lang intersection(final Lang a, final Lang b) {
		return of("intersection", s -> a.contains(s) && b.contains(s), a, b);
	}

	static Lang minus(final Lang a, final Lang b) {
		return of("minus", s -> a.contains(s) && !b.contains(s), a, b);
	}

	static Lang complement(final Lang a) {
		return of("complement", s -> !a.contains(s), a);
	}

	static Lang concat(final Lang a, final Lang b) {
		return of("concat", s -> {
			for (int i = 0; i <= s.length(); i++)
				if (a.contains(s.substring(0, i)) && b.contains(s.substring(i)))
					return true;
			return false;
		}, a, b);
	}

	/** Between <code>min</code> and <code>max</code> (-1 for unbounded) concatenated repetitions. */
	static Lang repeat(final Lang a, final int min, final int max) {
		return of("repeat", s -> repeatContains(a, s, min, max), a);
	}

	static boolean repeatContains(Lang a, String s, int min, int max) {
		int n = s.length();
		boolean eps = a.contains("");
		// reach[k][i]: prefix of length i is a concatenation of k nonempty members
		int kmax = max < 0 ? n : Math.min(max, n);
		boolean[][] reach = new boolean[kmax + 1][n + 1];
		reach[0][0] = true;
		for (int k = 1; k <= kmax; k++)
			for (int i = 0; i < n; i++)
				if (reach[k - 1][i])
					for (int j = i + 1; j <= n; j++)
						if (a.contains(s.substring(i, j)))
							reach[k][j] = true;
		for (int k = 0; k <= kmax; k++)
			if (reach[k][n] && (k >= min || eps) && (max < 0 || k <= max))
				return true;
		// with no nonempty pieces at all
		return n == 0 && (min == 0 || eps);
	}

	static Lang reverse(final Lang a) {
		return of("reverse", s -> a.contains(new StringBuilder(s).reverse().toString()), a);
	}

	static Lang finite(Collection<String> strings) {
		return new FiniteLang(strings);
	}

	// ---------------------------------------------------------------------------------------
	// Random languages
	// ---------------------------------------------------------------------------------------

	/** Random language from any of the available sources. */
	static Lang random(Random r) {
		switch (r.nextInt(5)) {
		case 0:
		case 1:
			return RegexLang.random(r, 1 + r.nextInt(4), true);
		case 2:
		case 3:
			return NfaLang.random(r, false);
		default:
			return FiniteLang.random(r, "abc", 6, 4);
		}
	}

	/** Random language using only the chars a, b, c (no wide character ranges). */
	static Lang randomNarrow(Random r) {
		switch (r.nextInt(3)) {
		case 0:
			return RegexLang.random(r, 1 + r.nextInt(3), false);
		case 1:
			return NfaLang.random(r, true);
		default:
			return FiniteLang.random(r, "abc", 6, 4);
		}
	}

	// ---------------------------------------------------------------------------------------
	// Finite languages
	// ---------------------------------------------------------------------------------------

	static final class FiniteLang extends Lang {

		final Set<String> strings;

		private final int construction;

		FiniteLang(Collection<String> strings) {
			this(strings, 0);
		}

		/** The construction (0-3) selects how the automaton is built. */
		FiniteLang(Collection<String> strings, int construction) {
			this.strings = new TreeSet<>(strings);
			this.construction = construction;
		}

		/** Random construction of an automaton for the given strings. */
		static FiniteLang random(Random r, Collection<String> strings) {
			return new FiniteLang(strings, r.nextInt(4));
		}

		static FiniteLang random(Random r, String alphabet, int maxcount, int maxlen) {
			Set<String> ss = new TreeSet<>();
			int n = r.nextInt(maxcount + 1);
			for (int i = 0; i < n; i++)
				ss.add(Fuzz.randomString(r, alphabet, maxlen));
			return random(r, ss);
		}

		@Override
		boolean contains(String s) {
			return strings.contains(s);
		}

		@Override
		Set<String> finiteStrings() {
			return strings;
		}

		@Override
		Automaton automaton() {
			switch (construction) {
			case 0:
				return BasicAutomata.makeStringUnion(strings.toArray(new String[0]));
			case 1: { // union of singletons
				List<Automaton> l = new ArrayList<>();
				for (String s : strings)
					l.add(BasicAutomata.makeString(s));
				return BasicOperations.union(l);
			}
			case 2: { // nondeterministic: separate path for each string
				State init = new State();
				for (String s : strings) {
					State p = init;
					if (s.isEmpty())
						init.setAccept(true);
					for (int i = 0; i < s.length(); i++) {
						State q = new State();
						p.addTransition(new Transition(s.charAt(i), q));
						p = q;
					}
					p.setAccept(true);
				}
				Automaton a = new Automaton();
				a.setInitialState(init);
				a.setDeterministic(false);
				return a;
			}
			default:
				if (strings.size() == 1)
					return BasicAutomata.makeString(strings.iterator().next());
				Automaton a = BasicAutomata.makeStringUnion(strings.toArray(new String[0]));
				a.minimize();
				return a;
			}
		}

		@Override
		Set<Character> chars() {
			Set<Character> cs = new TreeSet<>();
			for (String s : strings)
				for (char c : s.toCharArray())
					cs.add(c);
			return cs;
		}

		@Override
		List<String> members(Random r, int count) {
			return new ArrayList<>(strings);
		}

		@Override
		public String toString() {
			return "finite" + Fuzz.show(strings) + " (construction " + construction + ")";
		}
	}

	// ---------------------------------------------------------------------------------------
	// Random NFAs
	// ---------------------------------------------------------------------------------------

	/** Random NFA, possibly with epsilon transitions, dead states, and overlapping ranges. */
	static final class NfaLang extends Lang {

		int n;
		boolean[] accept;
		List<int[]> trans = new ArrayList<>(); // {from, min, max, to}
		List<int[]> eps = new ArrayList<>(); // {from, to}

		static NfaLang random(Random r, boolean narrow) {
			NfaLang l = new NfaLang();
			l.n = 1 + r.nextInt(6);
			l.accept = new boolean[l.n];
			for (int i = 0; i < l.n; i++)
				l.accept[i] = r.nextInt(3) == 0;
			int m = r.nextInt(3 * l.n + 1);
			for (int k = 0; k < m; k++) {
				char min, max;
				if (narrow || r.nextInt(4) != 0) {
					min = "abc".charAt(r.nextInt(3));
					max = r.nextInt(3) == 0 ? (char) Math.min('c', min + r.nextInt(3)) : min;
				} else {
					min = POINTS[r.nextInt(POINTS.length)];
					max = POINTS[r.nextInt(POINTS.length)];
					if (min > max) {
						char t = min;
						min = max;
						max = t;
					}
				}
				l.trans.add(new int[] {r.nextInt(l.n), min, max, r.nextInt(l.n)});
			}
			int e = r.nextInt(4) == 0 ? 1 + r.nextInt(2) : 0;
			for (int k = 0; k < e; k++)
				l.eps.add(new int[] {r.nextInt(l.n), r.nextInt(l.n)});
			return l;
		}

		@Override
		Automaton automaton() {
			State[] st = new State[n];
			for (int i = 0; i < n; i++) {
				st[i] = new State();
				st[i].setAccept(accept[i]);
			}
			for (int[] t : trans)
				st[t[0]].addTransition(new Transition((char) t[1], (char) t[2], st[t[3]]));
			Automaton a = new Automaton();
			a.setInitialState(st[0]);
			a.setDeterministic(false);
			if (!eps.isEmpty()) {
				List<StatePair> pairs = new ArrayList<>();
				for (int[] e : eps)
					pairs.add(new StatePair(st[e[0]], st[e[1]]));
				a.addEpsilons(pairs);
			}
			a.restoreInvariant(); // the operations assume that there are no transitions to dead states
			return a;
		}

		private Set<Integer> closure(Set<Integer> ss) {
			Set<Integer> res = new HashSet<>(ss);
			boolean changed = true;
			while (changed) {
				changed = false;
				for (int[] e : eps)
					if (res.contains(e[0]) && res.add(e[1]))
						changed = true;
			}
			return res;
		}

		@Override
		boolean contains(String s) {
			Set<Integer> cur = new HashSet<>();
			cur.add(0);
			cur = closure(cur);
			for (char c : s.toCharArray()) {
				Set<Integer> next = new HashSet<>();
				for (int[] t : trans)
					if (cur.contains(t[0]) && t[1] <= c && c <= t[2])
						next.add(t[3]);
				cur = closure(next);
			}
			for (int q : cur)
				if (accept[q])
					return true;
			return false;
		}

		@Override
		Set<Character> chars() {
			Set<Character> cs = new TreeSet<>();
			for (int[] t : trans)
				addRangeChars(cs, (char) t[1], (char) t[2]);
			return cs;
		}

		@Override
		List<String> members(Random r, int count) {
			return Fuzz.walks(automaton(), r, count);
		}

		@Override
		public String toString() {
			StringBuilder b = new StringBuilder("nfa(states=" + n + ", accept=");
			for (int i = 0; i < n; i++)
				if (accept[i])
					b.append(i).append(' ');
			b.append(", transitions=");
			for (int[] t : trans)
				b.append(t[0]).append("-[").append(Fuzz.show("" + (char) t[1])).append("..").append(Fuzz.show("" + (char) t[2])).append("]->").append(t[3]).append(' ');
			b.append(", epsilons=");
			for (int[] e : eps)
				b.append(e[0]).append("->").append(e[1]).append(' ');
			return b.append(")").toString();
		}
	}

	// ---------------------------------------------------------------------------------------
	// Random regular expressions
	// ---------------------------------------------------------------------------------------

	/** Language of a random regular expression, with a matcher that is independent of the library. */
	static final class RegexLang extends Lang {

		final Rx rx;
		final String regexp;
		final boolean minimize;
		final Map<String, Lang> named;

		RegexLang(Rx rx, boolean minimize, Map<String, Lang> named, Random r) {
			this.rx = rx;
			this.minimize = minimize;
			this.named = named;
			this.regexp = rx.render(r);
		}

		static RegexLang random(Random r, int depth, boolean extended) {
			RegexLang l;
			int tries = 0;
			do {
				Rx rx = Rx.random(r, depth, extended, null);
				l = new RegexLang(rx, r.nextBoolean(), null, r);
			} while (l.automaton().getNumberOfStates() > MAX_STATES && ++tries < MAX_TRIES);
			return l;
		}

		/** Regular expressions with larger automata are regenerated (up to <code>MAX_TRIES</code> times) to avoid occasional blowups. */
		static final int MAX_STATES = 25, MAX_TRIES = 20;

		@Override
		boolean contains(String s) {
			return rx.matches(s, named);
		}

		@Override
		Automaton automaton() {
			RegExp re = new RegExp(regexp, RegExp.ALL);
			if (named == null)
				return re.toAutomaton(minimize);
			Map<String, Automaton> m = new HashMap<>();
			for (Map.Entry<String, Lang> e : named.entrySet())
				m.put(e.getKey(), e.getValue().automaton());
			return re.toAutomaton(m, minimize);
		}

		@Override
		Set<Character> chars() {
			Set<Character> cs = new TreeSet<>();
			rx.chars(cs);
			if (named != null)
				for (Lang l : named.values())
					cs.addAll(l.chars());
			return cs;
		}

		@Override
		List<String> members(Random r, int count) {
			List<String> res = new ArrayList<>();
			for (int i = 0; i < count * 3 && res.size() < count; i++) {
				String s = rx.sample(r, named);
				if (s != null && contains(s))
					res.add(s);
			}
			return res;
		}

		@Override
		public String toString() {
			return "regexp " + Fuzz.show(regexp) + (minimize ? " (minimized)" : "") + " = " + rx.dump();
		}
	}
}
