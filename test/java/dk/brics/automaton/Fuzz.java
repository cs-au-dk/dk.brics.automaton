package dk.brics.automaton;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic random testing support.
 * <p>
 * Each test iteration gets its own {@link Random} derived from the test name, the iteration
 * number, and the base seed (system property <code>automaton.test.seed</code>), so failures
 * are reproducible. A single iteration can be rerun with the system property
 * <code>automaton.test.iteration</code>. The number of iterations can be scaled with the system
 * property <code>automaton.test.scale</code> (default 1; use e.g. 10 for a more thorough run).
 */
final class Fuzz {

	static final long SEED = Long.getLong("automaton.test.seed", 20261024L);

	static final double SCALE = Double.parseDouble(System.getProperty("automaton.test.scale", "1"));

	static final Integer ITERATION = Integer.getInteger("automaton.test.iteration");

	private Fuzz() {}

	interface Body {
		void run(Random r, Context ctx) throws Exception;
	}

	/** Describes the inputs of the current iteration, included in failure messages. */
	static final class Context {
		private final StringBuilder b = new StringBuilder();

		Context add(String label, Object value) {
			b.append("\n  ").append(label).append(": ").append(value);
			return this;
		}

		@Override
		public String toString() {
			return b.toString();
		}
	}

	static int iterations(int base) {
		return Math.max(1, (int) Math.round(base * SCALE));
	}

	/** Thrown by {@link #assume} to skip the rest of an iteration. */
	private static final class Skipped extends RuntimeException {
		private static final long serialVersionUID = 1L;

		Skipped() {
			super(null, null, false, false);
		}
	}

	/** Skips the rest of the current iteration of {@link #repeat} if the condition is false (e.g. if the random inputs are too large). */
	static void assume(boolean b) {
		if (!b)
			throw new Skipped();
	}

	/**
	 * Runs <code>body</code> for the given number of (scaled) iterations, or only for the iteration given by <code>automaton.test.iteration</code>.
	 * Fails if more than half of the iterations are skipped by {@link #assume}, since the test would then check little.
	 */
	static void repeat(String name, int base, Body body) {
		int from = ITERATION != null ? ITERATION : 0;
		int to = ITERATION != null ? ITERATION + 1 : iterations(base);
		int skipped = 0;
		for (int i = from; i < to; i++) {
			long seed = SEED * 0x9E3779B97F4A7C15L + name.hashCode() * 0xC2B2AE3D27D4EB4FL + i;
			Context ctx = new Context();
			try {
				body.run(new Random(seed), ctx);
			} catch (Skipped s) {
				skipped++;
			} catch (Throwable t) {
				throw new AssertionError(name + ", iteration " + i + " (rerun with -Dautomaton.test.seed=" + SEED +
						" -Dautomaton.test.iteration=" + i + ")" + ctx + "\n" + t, t);
			}
		}
		if (ITERATION == null)
			check(2 * skipped <= to - from, name + ": " + skipped + " of " + (to - from) + " iterations were skipped");
	}

	static void check(boolean b, String msg) {
		if (!b)
			throw new AssertionError(msg);
	}

	@SuppressWarnings("unchecked")
	static void checkEq(Object expected, Object actual, String msg) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			if (expected instanceof Collection && actual instanceof Collection && isStrings((Collection<?>) expected) && isStrings((Collection<?>) actual))
				throw new AssertionError(msg + ": expected <" + show((Collection<String>) expected) + "> but was <" + show((Collection<String>) actual) + ">");
			if (expected instanceof String && actual instanceof String)
				throw new AssertionError(msg + ": expected <" + show((String) expected) + "> but was <" + show((String) actual) + ">");
			throw new AssertionError(msg + ": expected <" + expected + "> but was <" + actual + ">");
		}
	}

	private static boolean isStrings(Collection<?> c) {
		for (Object o : c)
			if (!(o instanceof String))
				return false;
		return true;
	}

	/** Escapes non-printable characters for messages. */
	static String show(String s) {
		if (s == null)
			return "null";
		StringBuilder b = new StringBuilder("\"");
		for (char c : s.toCharArray())
			if (c >= 0x20 && c < 0x7f && c != '"' && c != '\\')
				b.append(c);
			else
				b.append(String.format("\\u%04x", (int) c));
		return b.append('"').toString();
	}

	static String show(Collection<String> ss) {
		List<String> l = new ArrayList<>();
		for (String s : new TreeSet<>(ss))
			l.add(show(s));
		return l.toString();
	}

	/** Finding a rejected string requires determinization, which may blow up for larger nondeterministic automata. */
	private static final int MAX_STATES_TO_DETERMINIZE = 40;

	/** Checks that <code>a</code> accepts exactly the strings in <code>samples</code> accepted by the oracle. */
	static void checkLanguage(Automaton a, Lang expected, Collection<String> samples, String what) {
		checkWellFormed(a, what);
		for (String s : samples) {
			boolean e = expected.contains(s);
			if (a.run(s) != e)
				throw new AssertionError(what + ": run(" + show(s) + ") should be " + e);
		}
		// shortest examples must agree with the oracle
		String acc = a.getShortestExample(true);
		if (acc != null && !expected.contains(acc))
			throw new AssertionError(what + ": shortest accepted example " + show(acc) + " is not in the expected language");
		if (a.isDeterministic() || a.getNumberOfStates() <= MAX_STATES_TO_DETERMINIZE) {
			String rej = a.getShortestExample(false);
			if (rej != null && expected.contains(rej))
				throw new AssertionError(what + ": shortest rejected example " + show(rej) + " is in the expected language");
		}
		// strings generated from the automaton must be accepted by the oracle (no longer than the samples,
		// since some oracles are expensive for long strings)
		int maxlen = 4;
		for (String s : samples)
			maxlen = Math.max(maxlen, s.length());
		Random r = new Random(samples.size());
		for (String s : walks(a, r, 10, maxlen))
			if (!expected.contains(s))
				throw new AssertionError(what + ": accepts " + show(s) + ", which is not in the expected language");
	}

	/** Checks representation invariants. */
	static void checkWellFormed(Automaton a, String what) {
		if (a.isSingleton()) {
			check(a.getSingleton() != null, what + ": singleton");
			return;
		}
		check(a.getInitialState() != null, what + ": no initial state");
		Set<State> states = a.getStates();
		checkEq(states.size(), a.getNumberOfStates(), what + ": number of states");
		int nt = 0;
		Set<State> accept = new HashSet<>();
		for (State s : states) {
			if (s.isAccept())
				accept.add(s);
			for (Transition t : s.getTransitions()) {
				check(t.getMin() <= t.getMax(), what + ": transition with min > max: " + t);
				check(t.getDest() != null, what + ": transition without destination");
				nt++;
			}
		}
		checkEq(nt, a.getNumberOfTransitions(), what + ": number of transitions");
		checkEq(accept, a.getAcceptStates(), what + ": accept states");
		check(!a.isDeterministic() || isActuallyDeterministic(a), what + ": marked deterministic but has overlapping transitions");
	}

	/** Returns true if the automaton is actually deterministic (regardless of the flag). */
	static boolean isActuallyDeterministic(Automaton a) {
		if (a.isSingleton())
			return true;
		for (State s : a.getStates()) {
			List<Transition> ts = s.getSortedTransitions(false);
			for (int i = 1; i < ts.size(); i++)
				if (ts.get(i - 1).getMax() >= ts.get(i).getMin())
					return false;
		}
		return true;
	}

	/** Returns strings obtained by random walks in the automaton, ending in accept states. */
	static List<String> walks(Automaton a, Random r, int count) {
		return walks(a, r, count, 12);
	}

	/** Returns strings of at most the given length obtained by random walks in the automaton, ending in accept states. */
	static List<String> walks(Automaton a, Random r, int count, int maxlen) {
		List<String> res = new ArrayList<>();
		if (a.isSingleton()) {
			res.add(a.getSingleton());
			return res;
		}
		Set<State> live = a.getLiveStates();
		if (!live.contains(a.getInitialState()))
			return res;
		for (int k = 0; k < count; k++) {
			StringBuilder b = new StringBuilder();
			State s = a.getInitialState();
			for (int steps = 0; steps < maxlen; steps++) {
				if (s.isAccept() && (r.nextInt(3) == 0 || steps == maxlen - 1))
					break;
				List<Transition> ts = new ArrayList<>();
				for (Transition t : s.getSortedTransitions(false))
					if (live.contains(t.getDest()))
						ts.add(t);
				if (ts.isEmpty())
					break;
				Transition t = ts.get(r.nextInt(ts.size()));
				b.append(pick(t.getMin(), t.getMax(), r));
				s = t.getDest();
			}
			if (s.isAccept())
				res.add(b.toString());
		}
		return res;
	}

	/** Picks a char in the interval, preferring the end points. */
	static char pick(char min, char max, Random r) {
		switch (r.nextInt(3)) {
		case 0:
			return min;
		case 1:
			return max;
		default:
			return (char) (min + r.nextInt(max - min + 1));
		}
	}

	/** All strings over the alphabet up to the given length. */
	static List<String> allStrings(String alphabet, int maxlen) {
		List<String> res = new ArrayList<>();
		res.add("");
		int start = 0;
		for (int len = 1; len <= maxlen; len++) {
			int end = res.size();
			for (int i = start; i < end; i++)
				for (char c : alphabet.toCharArray())
					res.add(res.get(i) + c);
			start = end;
		}
		return res;
	}

	/** Pads the string with leading zeros to the given length. */
	static String pad(String s, int length) {
		StringBuilder b = new StringBuilder();
		for (int i = s.length(); i < length; i++)
			b.append('0');
		return b.append(s).toString();
	}

	static String randomString(Random r, String alphabet, int maxlen) {
		int n = r.nextInt(maxlen + 1);
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < n; i++)
			b.append(alphabet.charAt(r.nextInt(alphabet.length())));
		return b.toString();
	}

	/** Applies the given number of random deletions, insertions, and replacements of single chars, using chars from the alphabet. */
	static String mutate(Random r, String s, String alphabet, int edits) {
		StringBuilder b = new StringBuilder(s);
		for (int k = 0; k < edits; k++) {
			int op = b.length() == 0 ? 1 : r.nextInt(3);
			int p = r.nextInt(b.length() + 1);
			char c = alphabet.charAt(r.nextInt(alphabet.length()));
			if (op == 1)
				b.insert(p, c);
			else if (p < b.length()) {
				if (op == 0)
					b.deleteCharAt(p);
				else
					b.setCharAt(p, c);
			}
		}
		return b.toString();
	}

	/**
	 * Sample strings for testing membership in languages: all short strings over a core alphabet,
	 * random strings over the full alphabet, and strings generated from the given languages.
	 */
	static Set<String> samples(Random r, Lang... langs) {
		Set<Character> chars = new TreeSet<>();
		for (Lang l : langs)
			chars.addAll(l.chars());
		chars.add('\u0000');
		chars.add('\uffff');
		StringBuilder full = new StringBuilder();
		for (char c : chars)
			full.append(c);
		Set<String> res = new LinkedHashSet<>(allStrings("ab", 4));
		res.addAll(allStrings("abc", 3));
		for (int i = 0; i < 40; i++)
			res.add(randomString(r, full.toString(), 7));
		for (Lang l : langs) {
			res.addAll(l.members(r, 10));
			try {
				res.addAll(walks(l.automaton(), r, 10));
			} catch (UnsupportedOperationException e) {
				// oracle without automaton
			}
		}
		// combinations of members, useful for concatenation and repetition
		List<String> members = new ArrayList<>();
		for (Lang l : langs)
			members.addAll(l.members(r, 4));
		for (int i = 0; i < 20 && !members.isEmpty(); i++) {
			StringBuilder b = new StringBuilder();
			int k = r.nextInt(4);
			for (int j = 0; j < k; j++)
				b.append(members.get(r.nextInt(members.size())));
			res.add(b.toString());
		}
		return res;
	}

	static Automaton storeLoad(Automaton a) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		a.store(out);
		return Automaton.load(new ByteArrayInputStream(out.toByteArray()));
	}

	static RunAutomaton storeLoad(RunAutomaton a) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		a.store(out);
		return RunAutomaton.load(new ByteArrayInputStream(out.toByteArray()));
	}

	/** Runs the given code with the global automaton flags set as specified, restoring them afterwards. */
	static void withFlags(boolean allowMutate, boolean minimizeAlways, int minimization, Runnable body) {
		boolean oldMutate = Automaton.setAllowMutate(allowMutate);
		boolean oldAlways = Automaton.minimize_always;
		int oldMinimization = Automaton.minimization;
		try {
			Automaton.setMinimizeAlways(minimizeAlways);
			Automaton.setMinimization(minimization);
			body.run();
		} finally {
			Automaton.setAllowMutate(oldMutate);
			Automaton.setMinimizeAlways(oldAlways);
			Automaton.setMinimization(oldMinimization);
		}
	}
}
