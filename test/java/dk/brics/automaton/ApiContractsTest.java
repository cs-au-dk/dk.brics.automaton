package dk.brics.automaton;

import static dk.brics.automaton.Fuzz.check;
import static dk.brics.automaton.Fuzz.checkEq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Checks the contracts of the smaller API methods on random automata.
 */
final class ApiContractsTest {

	@Test
	void states_and_transitions() {
		Fuzz.repeat("api", 300, (r, ctx) -> {
			Lang l = Lang.random(r);
			ctx.add("L", l);
			Automaton a = l.automaton();
			a.expandSingleton();
			Automaton.setStateNumbers(a.getStates()); // getSortedTransitions(true) sorts by state number
			for (State s : a.getStates()) {
				for (boolean toFirst : new boolean[] {false, true}) {
					List<Transition> ts = s.getSortedTransitions(toFirst);
					checkEq(s.getTransitions().size(), ts.size(), "getSortedTransitions size");
					for (int i = 1; i < ts.size(); i++) {
						Transition t1 = ts.get(i - 1), t2 = ts.get(i);
						int to = Integer.compare(t1.getDest().number, t2.getDest().number);
						boolean minMax = t1.getMin() < t2.getMin() || (t1.getMin() == t2.getMin() && t1.getMax() >= t2.getMax());
						if (toFirst)
							check(to < 0 || (to == 0 && minMax), "transitions should be sorted by (to, min, reverse max): " + t1 + ", " + t2);
						else
							check(minMax, "transitions should be sorted by (min, reverse max): " + t1 + ", " + t2);
					}
				}
				// step agrees with the transitions
				for (char c : Lang.POINTS) {
					List<State> dest = new ArrayList<>();
					s.step(c, dest);
					List<State> expected = new ArrayList<>();
					for (Transition t : s.getTransitions())
						if (t.getMin() <= c && c <= t.getMax())
							expected.add(t.getDest());
					checkEq(new HashSet<>(expected), new HashSet<>(dest), "step(c, dest)");
					State one = s.step(c);
					check(one == null ? expected.isEmpty() : expected.contains(one), "step(" + Fuzz.show("" + c) + ") should return a destination of a transition on that char");
				}
				for (Transition t : s.getTransitions()) {
					Transition c = t.clone();
					check(c.equals(t) && c.hashCode() == t.hashCode() && c != t, "Transition.clone");
					check(!t.equals(new Transition(t.getMin(), t.getMax(), new State())), "transitions to different states should differ");
					check(t.toString().contains("->"), "Transition.toString");
				}
				check(s.compareTo(s) == 0, "State.compareTo");
			}
			StatePair p = new StatePair(a.getInitialState(), a.getInitialState());
			check(p.equals(new StatePair(a.getInitialState(), a.getInitialState())) && p.hashCode() == new StatePair(a.getInitialState(), a.getInitialState()).hashCode(), "StatePair equality");
			check(p.getFirstState() == a.getInitialState() && p.getSecondState() == a.getInitialState(), "StatePair accessors");
			// toDot and toString mention all states
			String dot = a.toDot();
			check(dot.startsWith("digraph"), "toDot");
			checkEq(a.getNumberOfStates(), a.toString().split("\nstate ", -1).length - 1, "toString lists all states");
			// info
			a.setInfo("x");
			checkEq("x", a.getInfo(), "getInfo");
			// clones are independent
			Automaton c = a.clone();
			Set<String> samples = Fuzz.samples(r, l);
			for (State s : c.getStates())
				s.setAccept(!s.isAccept());
			c.restoreInvariant();
			Fuzz.checkLanguage(a, l, samples, "original after modifying the clone");
		});
	}

	@Test
	void singletons() {
		Fuzz.repeat("singleton", 200, (r, ctx) -> {
			String s = Fuzz.randomString(r, "ab\u0000\uffff", 4);
			ctx.add("s", Fuzz.show(s));
			Automaton a = BasicAutomata.makeString(s);
			checkEq(s, a.getSingleton(), "getSingleton");
			Automaton b = a.clone();
			b.expandSingleton();
			check(b.getSingleton() == null, "getSingleton after expandSingleton");
			check(a.equals(b) && b.equals(a) && a.hashCode() == b.hashCode(), "singleton should equal its expansion");
			checkEq(s.length() + 1, b.getNumberOfStates(), "number of states of expanded singleton");
			checkEq(s.length() + 1, a.getNumberOfStates(), "number of states of singleton");
			checkEq(s.length(), a.getNumberOfTransitions(), "number of transitions of singleton");
			check(a.getInitialState() != null && a.getSingleton() == null, "getInitialState expands the singleton");
		});
	}

	@Test
	void regexp_allow_mutate_flag() {
		RegExp re = new RegExp("a*b");
		assertFalse(re.setAllowMutate(true), "default allow mutate flag of RegExp should be false");
		assertTrue(re.setAllowMutate(false), "setAllowMutate should return the previous value");
		boolean old = Automaton.setAllowMutate(false);
		try {
			Automaton a = new RegExp("(ab)*|c+").toAutomaton();
			RegExp re2 = new RegExp("(ab)*|c+");
			re2.setAllowMutate(true);
			assertEquals(a, re2.toAutomaton(), "the allow mutate flag should not change the result");
			assertFalse(Automaton.setAllowMutate(false), "the global flag should be restored");
		} finally {
			Automaton.setAllowMutate(old);
		}
	}
}
