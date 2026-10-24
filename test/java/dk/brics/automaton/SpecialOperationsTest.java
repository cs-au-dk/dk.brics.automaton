package dk.brics.automaton;

import static dk.brics.automaton.Fuzz.check;
import static dk.brics.automaton.Fuzz.checkEq;
import static dk.brics.automaton.Fuzz.show;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Checks the special operations. Results are compared with reference oracles defined by
 * preimages (a string is in the result iff one of its preimages is in the input language)
 * or, for finite languages, with the exact sets of strings.
 */
final class SpecialOperationsTest {

	interface Preimages {
		/** All strings that the operation may map to <code>w</code>. */
		Collection<String> of(String w);
	}

	/** Language of strings that have a preimage in <code>l</code>. */
	private static Lang image(String desc, Lang l, Preimages p) {
		return Lang.of(desc, w -> {
			for (String u : p.of(w))
				if (l.contains(u))
					return true;
			return false;
		}, l);
	}

	/** Preimages when each char maps to a set of chars (position by position). */
	private static Collection<String> perChar(String w, Function<Character, Set<Character>> pre) {
		List<String> res = new ArrayList<>();
		res.add("");
		for (char c : w.toCharArray()) {
			List<String> next = new ArrayList<>();
			for (String u : res)
				for (char x : pre.apply(c))
					next.add(u + x);
			res = next;
			if (res.size() > 100000)
				throw new AssertionError("too many preimages");
		}
		return res;
	}

	/**
	 * Samples including images of members of l (computed by the given forward function), and at most
	 * <code>maxlen</code> long since the number of preimages grows exponentially.
	 */
	private static Set<String> samples(Random r, Lang l, Function<String, String> forward, String extra, int maxlen) {
		Set<String> s = Fuzz.samples(r, l);
		for (String m : l.members(r, 10))
			s.add(forward.apply(m));
		for (int i = 0; i < 20; i++)
			s.add(Fuzz.randomString(r, "abc" + extra, 6));
		s.removeIf(w -> w.length() > maxlen);
		return s;
	}

	/** Maximum length of sample strings, since the oracles enumerate preimages, which is exponential in the length. */
	private static final int MAXLEN = 9;

	/** Maximum length of sample strings for oracles with many preimages per char. */
	private static final int MAXLEN_MANY_PREIMAGES = 7;

	/** Maximum length of sample strings for the shuffle oracle, which tries all 2^n splits. */
	private static final int MAXLEN_SHUFFLE = 8;

	/** Maximum product of the numbers of states of the shuffled automata. */
	private static final int MAX_SHUFFLE_STATES = 400;

	@Test
	void subst_char_by_string() {
		Fuzz.repeat("subst(c,s)", 300, (r, ctx) -> {
			String s = Fuzz.randomString(r, "abcd", 3);
			// for s empty, the preimages are unbounded, so use a finite language
			Lang l = s.isEmpty() ? Lang.FiniteLang.random(r, "abc", 6, 5) : Lang.random(r);
			char c = "abc".charAt(r.nextInt(3));
			ctx.add("L", l).add("c", c).add("s", show(s));
			Automaton a = l.automaton();
			Automaton res = a.subst(c, s);
			Lang expected = s.isEmpty() ? Lang.finite(map(l.finiteStrings(), u -> u.replace("" + c, ""))) : image("subst", l, w -> {
				// a c in the preimage becomes s; other chars are unchanged
				Set<String> pre = new HashSet<>();
				substPre(w, 0, "", c, s, pre);
				return pre;
			});
			Fuzz.checkLanguage(res, expected, samples(r, l, m -> m.replace("" + c, s), "d", MAXLEN), "subst");
			Fuzz.checkLanguage(a, l, Fuzz.samples(r, l), "input after subst");
		});
	}

	private static void substPre(String w, int i, String u, char c, String s, Set<String> pre) {
		if (i == w.length())
			pre.add(u);
		if (i < w.length() && w.charAt(i) != c)
			substPre(w, i + 1, u + w.charAt(i), c, s, pre);
		if (w.startsWith(s, i))
			substPre(w, i + s.length(), u + c, c, s, pre);
	}

	private static Set<String> map(Set<String> ss, Function<String, String> f) {
		Set<String> res = new TreeSet<>();
		for (String s : ss)
			res.add(f.apply(s));
		return res;
	}

	@Test
	void subst_map() {
		Fuzz.repeat("subst(map)", 300, (r, ctx) -> {
			Lang l = Lang.random(r);
			Map<Character, Set<Character>> map = randomCharMap(r);
			ctx.add("L", l).add("map", map);
			Lang expected = image("subst", l, w -> perChar(w, x -> substMapPreimages(map, x)));
			// each char may have several preimages
			Set<String> samples = samples(r, l, m -> substMapImage(map, m), "`dz", MAXLEN_MANY_PREIMAGES);
			Automaton a = l.automaton();
			Fuzz.checkLanguage(a.subst(map), expected, samples, "subst(map)");
			Fuzz.checkLanguage(a, l, Fuzz.samples(r, l), "input after subst(map)");
		});
	}

	/** Random map from chars to sets of chars. */
	private static Map<Character, Set<Character>> randomCharMap(Random r) {
		Map<Character, Set<Character>> map = new HashMap<>();
		int n = r.nextInt(4);
		for (int i = 0; i < n; i++) {
			Set<Character> set = new TreeSet<>();
			int m = r.nextInt(3);
			for (int j = 0; j < m; j++)
				set.add(Lang.POINTS[r.nextInt(Lang.POINTS.length)]);
			map.put(Lang.POINTS[r.nextInt(Lang.POINTS.length)], set);
		}
		return map;
	}

	/** The chars that subst(map) may map to <code>x</code>. */
	private static Set<Character> substMapPreimages(Map<Character, Set<Character>> map, char x) {
		Set<Character> pre = new HashSet<>();
		if (!map.containsKey(x))
			pre.add(x);
		for (Map.Entry<Character, Set<Character>> e : map.entrySet())
			if (e.getValue().contains(x))
				pre.add(e.getKey());
		return pre;
	}

	/** One of the images of <code>m</code> under subst(map), or the empty string if there is none. */
	private static String substMapImage(Map<Character, Set<Character>> map, String m) {
		StringBuilder b = new StringBuilder();
		for (char x : m.toCharArray()) {
			Set<Character> set = map.get(x);
			if (set == null)
				b.append(x);
			else if (!set.isEmpty())
				b.append(set.iterator().next());
			else
				return "";
		}
		return b.toString();
	}

	@Test
	void homomorph() {
		Fuzz.repeat("homomorph", 300, (r, ctx) -> {
			Lang l = Lang.random(r);
			// source starts at 0 and is increasing, dest is chosen so that the intervals do not overflow
			int k = 1 + r.nextInt(3);
			TreeSet<Character> starts = new TreeSet<>();
			starts.add('\u0000');
			while (starts.size() < k)
				starts.add(Lang.POINTS[r.nextInt(Lang.POINTS.length - 1)]);
			char[] source = new char[k], dest = new char[k];
			int i = 0;
			for (char s : starts)
				source[i++] = s;
			for (i = 0; i < k; i++) {
				int len = intervalEnd(source, i) - source[i] + 1;
				dest[i] = (char) r.nextInt(0x10000 - len + 1);
				if (r.nextBoolean())
					dest[i] = (char) Math.min(source[i] + r.nextInt(3), 0x10000 - len);
			}
			ctx.add("L", l).add("source", Arrays.toString(toInts(source))).add("dest", Arrays.toString(toInts(dest)));
			Lang expected = image("homomorph", l, w -> perChar(w, x -> {
				Set<Character> pre = new HashSet<>();
				for (int j = 0; j < k; j++) {
					int y = x - dest[j] + source[j];
					if (y >= source[j] && y <= intervalEnd(source, j))
						pre.add((char) y);
				}
				return pre;
			}));
			Automaton a = l.automaton();
			Set<String> samples = samples(r, l, m -> homomorphImage(source, dest, m), "", MAXLEN);
			Fuzz.checkLanguage(a.homomorph(source, dest), expected, samples, "homomorph");
			Fuzz.checkLanguage(a, l, Fuzz.samples(r, l), "input after homomorph");
		});
	}

	/** The last char of the i'th source interval of a homomorphism. */
	private static int intervalEnd(char[] source, int i) {
		return i + 1 < source.length ? source[i + 1] - 1 : Character.MAX_VALUE;
	}

	private static String homomorphImage(char[] source, char[] dest, String m) {
		StringBuilder b = new StringBuilder();
		for (char x : m.toCharArray()) {
			int j = source.length - 1;
			while (source[j] > x)
				j--;
			b.append((char) (dest[j] + x - source[j]));
		}
		return b.toString();
	}

	@Test
	void replace_whitespace() {
		Fuzz.repeat("replaceWhitespace", 300, (r, ctx) -> {
			Lang l = Lang.random(r);
			ctx.add("L", l);
			Lang expected = image("replaceWhitespace", l, w -> perChar(w, x -> {
				Set<Character> pre = new HashSet<>();
				pre.add(x);
				if (x == '\t' || x == '\n' || x == '\r')
					pre.add(' ');
				return pre;
			}));
			Automaton a = l.automaton();
			Set<String> samples = samples(r, l, m -> m.replace(' ', '\t'), " \t\n\r", MAXLEN);
			Fuzz.checkLanguage(SpecialOperations.replaceWhitespace(a), expected, samples, "replaceWhitespace");
			Fuzz.checkLanguage(a, l, Fuzz.samples(r, l), "input after replaceWhitespace");
		});
	}

	private static int[] toInts(char[] cs) {
		int[] res = new int[cs.length];
		for (int i = 0; i < cs.length; i++)
			res[i] = cs[i];
		return res;
	}

	@Test
	void hex_cases() {
		Fuzz.repeat("hexCases", 200, (r, ctx) -> {
			// random language over hex digits and some other chars
			Rx rx = Rx.random(r, 1 + r.nextInt(3), false, null);
			Lang rl = new Lang.RegexLang(rx, false, null, r);
			ctx.add("L", rl);
			Automaton a = rl.automaton();
			// result: optional surrounding whitespace, and a-f/A-F may be in either case
			Lang expected = image("hexCases", rl, w -> {
				Set<String> pre = new HashSet<>();
				for (int p = 0; p <= w.length(); p++)
					for (int q = p; q <= w.length(); q++)
						if (inSet(w.substring(0, p), " \t\n\r") && inSet(w.substring(q), " \t\n\r"))
							pre.addAll(perChar(w.substring(p, q), x -> {
								Set<Character> s = new HashSet<>();
								s.add(x);
								if (x >= 'a' && x <= 'f')
									s.add(Character.toUpperCase(x));
								if (x >= 'A' && x <= 'F')
									s.add(Character.toLowerCase(x));
								return s;
							}));
				return pre;
			});
			Set<String> samples = samples(r, rl, m -> " " + m.toUpperCase() + "\t", " \tABC", MAXLEN);
			Fuzz.checkLanguage(SpecialOperations.hexCases(a), expected, samples, "hexCases");
		});
	}

	@Test
	void trim_and_compress() {
		Fuzz.repeat("trim", 300, (r, ctx) -> {
			Lang l = Lang.random(r);
			char c = "abc ".charAt(r.nextInt(4));
			String set = "" + c + Fuzz.randomString(r, "abc ", 2);
			ctx.add("L", l).add("set", show(set)).add("c", show("" + c));
			Automaton a = l.automaton();
			Lang cexpected = image("compress", l, w -> {
				Set<String> pre = new HashSet<>();
				compressPre(w, 0, "", set, c, pre);
				return pre;
			});
			Set<String> samples = samples(r, l, m -> m.replace("" + c, set + set), set, MAXLEN_MANY_PREIMAGES);
			Fuzz.checkLanguage(a.compress(set, c), cexpected, samples, "compress");
			Lang texpected = image("trim", l, w -> {
				Set<String> pre = new HashSet<>();
				for (int p = 0; p <= w.length(); p++)
					for (int q = p; q <= w.length(); q++)
						if (inSet(w.substring(0, p), set) && inSet(w.substring(q), set))
							compressPre(w.substring(p, q), 0, "", set, c, pre);
				return pre;
			});
			for (String m : l.members(r, 5))
				samples.add(set + m + set);
			samples.removeIf(w -> w.length() > MAXLEN_MANY_PREIMAGES);
			Fuzz.checkLanguage(a.trim(set, c), texpected, samples, "trim");
			Fuzz.checkLanguage(a, l, Fuzz.samples(r, l), "input after operations");
		});
	}

	/** Preimages for compress: a nonempty run of set chars may come from c. */
	private static void compressPre(String w, int i, String u, String set, char c, Set<String> pre) {
		if (i == w.length()) {
			pre.add(u);
			return;
		}
		compressPre(w, i + 1, u + w.charAt(i), set, c, pre);
		for (int k = i + 1; k <= w.length() && set.indexOf(w.charAt(k - 1)) != -1; k++)
			compressPre(w, k, u + c, set, c, pre);
	}

	private static boolean inSet(String s, String set) {
		for (char x : s.toCharArray())
			if (set.indexOf(x) == -1)
				return false;
		return true;
	}

	@Test
	void prefix_close() {
		Fuzz.repeat("prefixClose", 300, (r, ctx) -> {
			Lang l = Lang.random(r);
			ctx.add("L", l);
			Automaton a = l.automaton();
			Automaton any = BasicAutomata.makeAnyString();
			// w is in the result iff it is a prefix of an accepted string
			Lang expected;
			Set<String> fs = l.finiteStrings();
			if (fs != null) {
				Set<String> prefixes = new HashSet<>();
				for (String s : fs)
					for (int i = 0; i <= s.length(); i++)
						prefixes.add(s.substring(0, i));
				expected = Lang.finite(prefixes);
			} else
				expected = Lang.of("prefixes", w -> {
					String x = a.intersection(BasicAutomata.makeString(w).concatenate(any)).getShortestExample(true);
					check(x == null || l.contains(x), "shortest string with prefix " + show(w) + " is " + show(x) + ", which is not in L");
					return x != null;
				}, l);
			Set<String> samples = Fuzz.samples(r, l);
			for (String m : l.members(r, 10))
				for (int i = 0; i <= m.length(); i++)
					samples.add(m.substring(0, i));
			Automaton pc = l.automaton();
			pc.prefixClose();
			Fuzz.checkLanguage(pc, expected, samples, "prefixClose");
		});
	}

	@Test
	void overlap_and_project_on_finite_languages() {
		Fuzz.repeat("overlap", 300, (r, ctx) -> {
			Lang.FiniteLang l1 = Lang.FiniteLang.random(r, "ab", 5, 4), l2 = Lang.FiniteLang.random(r, "ab", 5, 4);
			ctx.add("L1", l1).add("L2", l2);
			// overlap: nonempty v such that xv in L1 for some x in L1, and vy in L2 for some y in L2
			Set<String> expected = new TreeSet<>();
			for (String x : l1.strings)
				for (String xv : l1.strings)
					if (xv.startsWith(x) && xv.length() > x.length()) {
						String v = xv.substring(x.length());
						for (String y : l2.strings)
							if (l2.strings.contains(v + y))
								expected.add(v);
					}
			Automaton o = SpecialOperations.overlap(l1.automaton(), l2.automaton());
			checkEq(expected, new TreeSet<>(o.getFiniteStrings()), "overlap");

			// projection onto a set of private-use chars (and possibly the normal chars)
			Lang.FiniteLang l = Lang.FiniteLang.random(r, "ab\ue000\ue001\ue002\uf8ff", 5, 4);
			Set<Character> chars = new HashSet<>();
			for (char c : "\ue000\ue001\ue002\uf8ff".toCharArray())
				if (r.nextBoolean())
					chars.add(c);
			boolean normal = r.nextBoolean();
			if (normal)
				chars.add(null);
			ctx.add("L", l).add("chars", Fuzz.show(charsToStrings(chars)));
			Set<String> pexpected = new TreeSet<>();
			for (String s : l.strings) {
				StringBuilder b = new StringBuilder();
				for (char c : s.toCharArray())
					if (chars.contains(c) || (normal && (c <= '\udfff' || c >= '\uf900')))
						b.append(c);
				pexpected.add(b.toString());
			}
			Automaton p = SpecialOperations.projectChars(l.automaton(), chars);
			Set<String> pf = p.getFiniteStrings();
			check(pf != null, "projection of a finite language should be finite");
			checkEq(pexpected, new TreeSet<>(pf), "projectChars");
		});
	}

	private static Set<String> charsToStrings(Set<Character> cs) {
		Set<String> res = new TreeSet<>();
		for (Character c : cs)
			res.add(c == null ? "null" : "" + c);
		return res;
	}

	@Test
	void shuffle() {
		Fuzz.repeat("shuffle", 300, (r, ctx) -> {
			Lang.FiniteLang l1 = Lang.FiniteLang.random(r, "abc", 3, 3), l2 = Lang.FiniteLang.random(r, "abc", 3, 3);
			ctx.add("L1", l1).add("L2", l2);
			Set<String> expected = new TreeSet<>();
			for (String x : l1.strings)
				for (String y : l2.strings)
					interleavings(x, y, "", expected);
			Automaton s = ShuffleOperations.shuffle(l1.automaton(), l2.automaton());
			checkEq(expected, new TreeSet<>(s.getFiniteStrings()), "shuffle");
			// infinite languages: membership via the interleavings of splits
			Lang a = Lang.random(r), b = Lang.random(r);
			ctx.add("A", a).add("B", b);
			Automaton da = a.automaton(), db = b.automaton();
			da.determinize();
			db.determinize();
			Fuzz.assume(da.getNumberOfStates() * db.getNumberOfStates() <= MAX_SHUFFLE_STATES);
			Lang sexp = Lang.of("shuffle", w -> isShuffle(w, a, b), a, b);
			Set<String> samples = new LinkedHashSet<>(Fuzz.samples(r, a, b));
			for (String x : a.members(r, 5))
				for (String y : b.members(r, 5))
					if (x.length() + y.length() <= MAXLEN_SHUFFLE) {
						Set<String> is = new TreeSet<>();
						interleavings(x, y, "", is);
						samples.addAll(is);
					}
			samples.removeIf(w -> w.length() > MAXLEN_SHUFFLE);
			Fuzz.checkLanguage(ShuffleOperations.shuffle(a.automaton(), b.automaton()), sexp, samples, "shuffle");
		});
	}

	private static void interleavings(String x, String y, String prefix, Set<String> res) {
		if (x.isEmpty() || y.isEmpty()) {
			res.add(prefix + x + y);
			return;
		}
		interleavings(x.substring(1), y, prefix + x.charAt(0), res);
		interleavings(x, y.substring(1), prefix + y.charAt(0), res);
	}

	/** Checks whether w is an interleaving of a string in a and a string in b (by trying all subsets of positions). */
	private static boolean isShuffle(String w, Lang a, Lang b) {
		int n = w.length();
		for (int mask = 0; mask < (1 << n); mask++) {
			StringBuilder x = new StringBuilder(), y = new StringBuilder();
			for (int i = 0; i < n; i++)
				((mask & (1 << i)) != 0 ? x : y).append(w.charAt(i));
			if (a.contains(x.toString()) && b.contains(y.toString()))
				return true;
		}
		return false;
	}
}
