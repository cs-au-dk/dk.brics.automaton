package dk.brics.automaton;

import static dk.brics.automaton.Fuzz.check;
import static dk.brics.automaton.Fuzz.checkEq;
import static dk.brics.automaton.Fuzz.show;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;

import org.junit.jupiter.api.Test;

/**
 * Checks {@link RunAutomaton}, {@link AutomatonMatcher}, and {@link MatchOnlyRunAutomaton}
 * against the reference oracles.
 */
final class RunAutomatonFuzzTest {

	@Test
	void run_automaton() {
		Fuzz.repeat("RunAutomaton", 300, (r, ctx) -> {
			Lang l = Lang.random(r);
			ctx.add("L", l);
			Set<String> samples = Fuzz.samples(r, l);
			for (boolean tableize : new boolean[] {false, true}) {
				RunAutomaton ra = new RunAutomaton(l.automaton(), tableize);
				RunAutomaton loaded = Fuzz.storeLoad(ra);
				char[] intervals = ra.getCharIntervals();
				for (int i = 1; i < intervals.length; i++)
					check(intervals[i - 1] < intervals[i], "char intervals should be increasing");
				for (String s : samples) {
					boolean e = l.contains(s);
					checkEq(e, ra.run(s), "run(" + show(s) + "), tableize=" + tableize);
					checkEq(e, loaded.run(s), "run(" + show(s) + ") after store/load");
					// step by step
					int p = ra.getInitialState();
					for (int i = 0; i < s.length() && p != -1; i++)
						p = ra.step(p, s.charAt(i));
					checkEq(e, p != -1 && ra.isAccept(p), "step/isAccept on " + show(s));
					// longest accepted prefix from each offset
					for (int offset = 0; offset <= s.length(); offset++) {
						int expected = -1;
						for (int end = offset; end <= s.length(); end++)
							if (l.contains(s.substring(offset, end)))
								expected = end - offset;
						checkEq(expected, ra.run(s, offset), "run(" + show(s) + ", " + offset + ")");
					}
				}
			}
		});
	}

	/** Reference implementation of the leftmost-longest matching in {@link AutomatonMatcher}. */
	private static List<int[]> expectedMatches(Lang l, String s) {
		List<int[]> res = new ArrayList<>();
		int pos = 0;
		boolean lastEmpty = false;
		int lastEnd = -1;
		while (pos <= s.length()) {
			int start = -1, end = -1;
			for (int b = pos; b <= s.length() && start == -1; b++) {
				if (lastEmpty && b == lastEnd)
					continue; // an empty match is not repeated at the same position
				for (int e = s.length(); e >= b; e--)
					if (l.contains(s.substring(b, e))) {
						start = b;
						end = e;
						break;
					}
			}
			if (start == -1)
				break;
			res.add(new int[] {start, end});
			lastEmpty = start == end;
			lastEnd = end;
			pos = end;
		}
		return res;
	}

	@Test
	void automaton_matcher() {
		Fuzz.repeat("AutomatonMatcher", 300, (r, ctx) -> {
			Lang l = Lang.random(r);
			ctx.add("L", l);
			RunAutomaton ra = new RunAutomaton(l.automaton());
			List<String> inputs = new ArrayList<>();
			for (int i = 0; i < 15; i++) {
				StringBuilder b = new StringBuilder();
				for (int k = r.nextInt(4); k > 0; k--) {
					b.append(Fuzz.randomString(r, "abcz", 2));
					List<String> m = l.members(r, 1);
					if (!m.isEmpty())
						b.append(m.get(0));
				}
				if (b.length() <= 12)
					inputs.add(b.toString());
			}
			for (String s : inputs) {
				List<int[]> expected = expectedMatches(l, s);
				AutomatonMatcher m = ra.newMatcher(s);
				checkEq(0, m.groupCount(), "groupCount");
				for (int[] e : expected) {
					check(m.find(), "find() should find match " + show(s.substring(e[0], e[1])) + " at " + e[0] + " in " + show(s));
					checkEq(e[0], m.start(), "start() in " + show(s));
					checkEq(e[1], m.end(), "end() in " + show(s));
					checkEq(s.substring(e[0], e[1]), m.group(), "group() in " + show(s));
					checkEq(e[0], m.start(0), "start(0)");
					checkEq(e[1], m.end(0), "end(0)");
					MatchResult mr = m.toMatchResult();
					checkEq(e[0], mr.start(), "toMatchResult().start()");
				}
				check(!m.find(), "find() should not find more matches in " + show(s) + " (found " + expected.size() + ")");
				check(!m.find(), "find() should stay false");
				// matcher on a subsequence
				if (s.length() >= 2) {
					int from = r.nextInt(s.length()), to = from + r.nextInt(s.length() - from + 1);
					List<int[]> sub = expectedMatches(l, s.substring(from, to));
					AutomatonMatcher ms = ra.newMatcher(s, from, to);
					for (int[] e : sub) {
						check(ms.find(), "find() on subsequence");
						checkEq(e[0], ms.start(), "start() on subsequence");
						checkEq(e[1], ms.end(), "end() on subsequence");
					}
					check(!ms.find(), "no more matches on subsequence");
				}
			}
		});
	}

	@Test
	void match_only_run_automaton() {
		Fuzz.repeat("MatchOnlyRunAutomaton", 300, (r, ctx) -> {
			Lang l = Lang.random(r);
			ctx.add("L", l);
			MatchOnlyRunAutomaton mo = new MatchOnlyRunAutomaton(l.automaton(), r.nextBoolean());
			Set<String> samples = Fuzz.samples(r, l);
			for (String s : samples) {
				if (s.length() > 8)
					continue;
				for (int pos = 0; pos <= s.length(); pos++) {
					boolean e = false;
					for (int b = pos; b <= s.length() && !e; b++)
						for (int end = b; end <= s.length() && !e; end++)
							if (l.contains(s.substring(b, end)))
								e = true;
					checkEq(e, mo.matches(s, pos), "matches(" + show(s) + ", " + pos + ")");
				}
			}
			check(!mo.matches(null), "matches(null)");
		});
	}
}
