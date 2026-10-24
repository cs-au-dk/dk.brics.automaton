package dk.brics.automaton;

import static dk.brics.automaton.Fuzz.check;
import static dk.brics.automaton.Fuzz.checkEq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

/**
 * Checks that all minimization algorithms produce minimal automata, using an independent
 * computation of the number of states of the minimal automaton (Moore's partition refinement).
 */
final class MinimizationTest {

	/** Number of states of the minimal deterministic automaton without dead states (at least 1). */
	private static int minimalSize(Automaton a) {
		Automaton d = a.clone();
		d.expandSingleton();
		d.determinize();
		List<State> states = new ArrayList<>(d.getStates());
		char[] points = d.getStartPoints();
		int n = states.size(), dead = n;
		Map<State, Integer> index = new HashMap<>();
		for (int i = 0; i < n; i++)
			index.put(states.get(i), i);
		// total transition function, with an explicit dead state
		int[][] delta = new int[n + 1][points.length];
		for (int i = 0; i <= n; i++)
			for (int j = 0; j < points.length; j++) {
				State q = i < n ? states.get(i).step(points[j]) : null;
				delta[i][j] = q == null ? dead : index.get(q);
			}
		int[] cls = new int[n + 1];
		for (int i = 0; i < n; i++)
			cls[i] = states.get(i).isAccept() ? 1 : 0;
		int count = 0;
		while (true) {
			Map<List<Integer>, Integer> sig = new HashMap<>();
			int[] next = new int[n + 1];
			for (int i = 0; i <= n; i++) {
				List<Integer> s = new ArrayList<>();
				s.add(cls[i]);
				for (int j = 0; j < points.length; j++)
					s.add(cls[delta[i][j]]);
				Integer c = sig.get(s);
				if (c == null) {
					c = sig.size();
					sig.put(s, c);
				}
				next[i] = c;
			}
			cls = next;
			if (sig.size() == count)
				break;
			count = sig.size();
		}
		// the class of the dead state is not represented in the minimal automaton (unless everything is dead)
		return cls[index.get(d.getInitialState())] == cls[dead] ? 1 : count - 1;
	}

	static final Map<String, Consumer<Automaton>> ALGORITHMS = new LinkedHashMap<>();
	static {
		ALGORITHMS.put("Huffman", MinimizationOperations::minimizeHuffman);
		ALGORITHMS.put("Brzozowski", MinimizationOperations::minimizeBrzozowski);
		ALGORITHMS.put("Hopcroft", MinimizationOperations::minimizeHopcroft);
		ALGORITHMS.put("Valmari", MinimizationOperations::minimizeValmari);
	}

	@Test
	void all_algorithms_produce_minimal_automata() {
		Fuzz.repeat("minimize", 400, (r, ctx) -> {
			Lang l = Lang.random(r);
			ctx.add("L", l);
			Automaton a = l.automaton();
			int expected = minimalSize(a);
			Set<String> samples = Fuzz.samples(r, l);
			for (Map.Entry<String, Consumer<Automaton>> alg : ALGORITHMS.entrySet()) {
				Automaton m = a.clone();
				alg.getValue().accept(m);
				String what = alg.getKey() + " minimization";
				Fuzz.checkLanguage(m, l, samples, what);
				m.expandSingleton();
				checkEq(expected, m.getNumberOfStates(), what + ": number of states");
				check(Fuzz.isActuallyDeterministic(m), what + ": should be deterministic");
				check(m.getLiveStates().size() == m.getNumberOfStates() || m.getNumberOfStates() == 1, what + ": should have no dead states");
				// minimizing again does not change anything
				Automaton m2 = m.clone();
				m2.minimize();
				checkEq(expected, m2.getNumberOfStates(), what + ": minimizing again");
			}
		});
	}
}
