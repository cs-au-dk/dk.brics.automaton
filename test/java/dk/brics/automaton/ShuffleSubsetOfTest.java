package dk.brics.automaton;

import static dk.brics.automaton.Fuzz.check;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Checks {@link ShuffleOperations#shuffleSubsetOf} against a brute-force enumeration of the
 * interleavings of finite languages, with surrogate pairs (also unpaired surrogates) and
 * suspend/resume markers.
 */
final class ShuffleSubsetOfTest {

	static final char H = '\ud800', L = '\udc00', SUSPEND = '\ue000', RESUME = '\ue001';

	static final String[] TOKENS = {"a", "b", "" + H + L, "" + H, "" + L, SUSPEND + "a" + RESUME, SUSPEND + "" + H + L + "b" + RESUME, SUSPEND + "" + H + RESUME};

	static String randomTokens(Random r, boolean markers, int maxTokens) {
		StringBuilder b = new StringBuilder();
		for (int i = r.nextInt(maxTokens + 1); i > 0; i--)
			b.append(TOKENS[r.nextInt(markers ? TOKENS.length : 5)]);
		return b.toString();
	}

	/**
	 * All interleavings of the given strings where a surrogate pair (a high surrogate followed by a low
	 * surrogate in the same string) is never split, and where the chars between a suspend marker and
	 * a resume marker of a string are not interleaved with other chars.
	 */
	static void interleavings(String[] w, int[] pos, int suspended, StringBuilder cur, Set<String> out) {
		boolean done = true;
		for (int i = 0; i < w.length; i++)
			if (pos[i] < w[i].length())
				done = false;
		if (done) {
			out.add(cur.toString());
			return;
		}
		for (int i = 0; i < w.length; i++) {
			if ((suspended >= 0 && i != suspended) || pos[i] >= w[i].length())
				continue;
			char c = w[i].charAt(pos[i]);
			int len = Character.isHighSurrogate(c) && pos[i] + 1 < w[i].length() && Character.isLowSurrogate(w[i].charAt(pos[i] + 1)) ? 2 : 1;
			int ns = c == SUSPEND ? i : c == RESUME ? -1 : suspended;
			int l = cur.length();
			cur.append(w[i], pos[i], pos[i] + len);
			pos[i] += len;
			interleavings(w, pos, ns, cur, out);
			pos[i] -= len;
			cur.setLength(l);
		}
	}

	@Test
	void shuffle_subset_of() {
		Fuzz.repeat("shuffleSubsetOf", 5000, (r, ctx) -> {
			boolean markers = r.nextBoolean();
			int k = 1 + r.nextInt(3);
			List<Automaton> ca = new ArrayList<>();
			List<Set<String>> langs = new ArrayList<>();
			for (int i = 0; i < k; i++) {
				Set<String> lang = new HashSet<>();
				for (int j = 1 + r.nextInt(2); j > 0; j--)
					lang.add(randomTokens(r, markers, 3));
				langs.add(lang);
				ca.add(Lang.FiniteLang.random(r, lang).automaton());
			}
			for (Set<String> lang : langs)
				ctx.add("language", Fuzz.show(lang));
			ctx.add("markers", markers);
			// all interleavings
			Set<String> all = new HashSet<>();
			List<List<String>> combos = new ArrayList<>();
			combos.add(new ArrayList<>());
			for (Set<String> lang : langs) {
				List<List<String>> next = new ArrayList<>();
				for (List<String> c : combos)
					for (String s : lang) {
						List<String> c2 = new ArrayList<>(c);
						c2.add(s);
						next.add(c2);
					}
				combos = next;
			}
			for (List<String> c : combos)
				interleavings(c.toArray(new String[0]), new int[k], -1, new StringBuilder(), all);
			// the target language: all interleavings, possibly except one, possibly with an extra string
			List<String> sorted = new ArrayList<>(all);
			Collections.sort(sorted);
			String removed = sorted.isEmpty() || r.nextInt(3) == 0 ? null : sorted.get(r.nextInt(sorted.size()));
			ctx.add("removed", Fuzz.show(removed));
			List<String> keep = new ArrayList<>();
			for (String s : sorted)
				if (!s.equals(removed))
					keep.add(s);
			if (r.nextBoolean())
				keep.add("zz");
			Automaton a = BasicAutomata.makeStringUnion(keep.toArray(new String[0]));
			if (r.nextBoolean())
				a = a.union(BasicAutomata.makeString("zz")); // nondeterministic
			String res = ShuffleOperations.shuffleSubsetOf(ca, a, markers ? SUSPEND : null, markers ? RESUME : null);
			if (removed == null)
				check(res == null, "no counterexample expected, got " + Fuzz.show(res));
			else {
				check(res != null, "counterexample " + Fuzz.show(removed) + " not found");
				check(all.contains(res), "counterexample " + Fuzz.show(res) + " is not a valid interleaving");
				check(!a.run(res), "counterexample " + Fuzz.show(res) + " is accepted");
			}
		});
	}
}
