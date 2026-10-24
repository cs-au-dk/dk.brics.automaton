package dk.brics.automaton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for fixes in 1.13-1.
 */
final class RegressionTest {

	private static final String PAIR = "𐐀"; // surrogate pair with high surrogate other than U+D800

	@Test
	void shuffle_does_not_split_surrogate_pairs() {
		List<Automaton> ca = Arrays.asList(Automaton.makeString(PAIR + "x"), Automaton.makeString("b"));
		Automaton a = Automaton.makeString(PAIR + "xb").union(Automaton.makeString("b" + PAIR + "x"));
		// the only missing interleaving is the one that keeps the pair intact
		assertEquals(PAIR + "bx", ShuffleOperations.shuffleSubsetOf(ca, a, null, null));
	}

	@Test
	void shuffle_resumes_after_surrogate_pair() {
		List<Automaton> ca = Arrays.asList(Automaton.makeString(PAIR + "x"), Automaton.makeString("b"));
		Automaton a = Automaton.makeString(PAIR + "xb")
				.union(Automaton.makeString(PAIR + "bx"))
				.union(Automaton.makeString("b" + PAIR + "x"));
		assertNull(ShuffleOperations.shuffleSubsetOf(ca, a, null, null));
	}

	@Test
	void shuffle_keeps_suspension_across_surrogate_pair() {
		List<Automaton> ca = Arrays.asList(Automaton.makeString("<" + PAIR + "x>"), Automaton.makeString("b"));
		Automaton a = Automaton.makeString("<" + PAIR + "x>b").union(Automaton.makeString("b<" + PAIR + "x>"));
		assertNull(ShuffleOperations.shuffleSubsetOf(ca, a, '<', '>'));
	}

	@Test
	void shortest_rejected_example_via_missing_transition() {
		assertEquals("\u0000", new RegExp("a*").toAutomaton().getShortestExample(false));
		assertEquals("", BasicOperations.getShortestExample(new RegExp("a|ab").toAutomaton(false), false));
		assertNull(BasicAutomata.makeAnyString().getShortestExample(false));
	}

	@Test
	void shuffle_counterexample_does_not_split_surrogate_pair() {
		List<Automaton> ca = Arrays.asList(Automaton.makeString("x"), Automaton.makeString(PAIR));
		Automaton a = Automaton.makeString("x" + PAIR);
		assertEquals(PAIR + "x", ShuffleOperations.shuffleSubsetOf(ca, a, null, null));
	}

	@Test
	void shuffle_counterexample_respects_suspension() {
		List<Automaton> ca = Arrays.asList(Automaton.makeString("x"), Automaton.makeString("<y>"));
		Automaton a = Automaton.makeString("x<y>");
		assertEquals("<y>x", ShuffleOperations.shuffleSubsetOf(ca, a, '<', '>'));
	}

	@Test
	void shuffle_with_unpaired_high_surrogate() {
		List<Automaton> ca = Arrays.asList(Automaton.makeString("x\uD800"), Automaton.makeString("y"));
		Automaton a = Automaton.makeString("yx\uD800").union(Automaton.makeString("xy\uD800")).union(Automaton.makeString("x\uD800z"));
		assertEquals("x\uD800y", ShuffleOperations.shuffleSubsetOf(ca, a, null, null));
		List<Automaton> ca2 = Arrays.asList(Automaton.makeString("𐀀"), Automaton.makeString("y"));
		Automaton a2 = Automaton.makeString("𐀀y").union(Automaton.makeString("y𐀀"));
		assertNull(ShuffleOperations.shuffleSubsetOf(ca2, a2, null, null));
	}
}
