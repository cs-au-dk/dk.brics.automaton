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
	void hexCases_does_not_mutate_shared_whitespace_automaton() {
		boolean b = Automaton.setAllowMutate(true);
		try {
			SpecialOperations.hexCases(Automaton.makeString("ab"));
		} finally {
			Automaton.setAllowMutate(b);
		}
		Automaton i = BasicAutomata.makeIntegerValue("5");
		assertTrue(i.run("5"));
		assertTrue(i.run(" 5 "));
	}

	@Test
	void decimal_value_lexical_forms() {
		Automaton half = BasicAutomata.makeDecimalValue("0.5");
		for (String s : new String[] {".5", "+.5", "0.5", "00.50", " .5 "})
			assertTrue(half.run(s), s);
		for (String s : new String[] {"", ".", "5", "0.05"})
			assertFalse(half.run(s), s);
		Automaton zero = BasicAutomata.makeDecimalValue("0");
		for (String s : new String[] {"0", "00", "0.", "0.0", ".0", "+.00"})
			assertTrue(zero.run(s), s);
		for (String s : new String[] {"", ".", "+", "1"})
			assertFalse(zero.run(s), s);
		Automaton one = BasicAutomata.makeDecimalValue("1");
		for (String s : new String[] {"1", "1.", "1.00", "01"})
			assertTrue(one.run(s), s);
		for (String s : new String[] {".1", "10", "."})
			assertFalse(one.run(s), s);
		Automaton neg = BasicAutomata.makeDecimalValue("-1.50");
		for (String s : new String[] {"-1.5", "-01.500"})
			assertTrue(neg.run(s), s);
		assertFalse(neg.run("1.5"));
	}

	@Test
	void zero_value_accepts_any_sign() {
		for (String v : new String[] {"0", "-0", "+0", "-00"}) {
			Automaton i = BasicAutomata.makeIntegerValue(v);
			for (String s : new String[] {"0", "+0", "-0", "-00", " +0 "})
				assertTrue(i.run(s), v + " " + s);
			for (String s : new String[] {"", "-", "+-0", "-1"})
				assertFalse(i.run(s), v + " " + s);
		}
		for (String v : new String[] {"0", "-0", "-0.0", "+.0", "-0."}) {
			Automaton d = BasicAutomata.makeDecimalValue(v);
			for (String s : new String[] {"0", "+0", "-0", "-0.0", "-.0", "+0.", " -.00 "})
				assertTrue(d.run(s), v + " " + s);
			for (String s : new String[] {"", "-", "-.", "+-0", "-0.1"})
				assertFalse(d.run(s), v + " " + s);
		}
		assertFalse(BasicAutomata.makeIntegerValue("-1").run("1"));
		assertFalse(BasicAutomata.makeDecimalValue("1.5").run("-1.5"));
	}

	@Test
	void shortest_rejected_example_via_missing_transition() {
		assertEquals("\u0000", new RegExp("a*").toAutomaton().getShortestExample(false));
		assertEquals("", BasicOperations.getShortestExample(new RegExp("a|ab").toAutomaton(false), false));
		assertNull(BasicAutomata.makeAnyString().getShortestExample(false));
	}

	@Test
	void repeat_does_not_mutate_input() {
		Automaton a = Automaton.makeString("ab");
		a.repeat(1, 2);
		assertTrue(a.isSingleton());
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

	@Test
	void hexCases_result_does_not_share_whitespace_automaton() {
		boolean b = Automaton.setAllowMutate(true);
		try {
			SpecialOperations.hexCases(Automaton.makeString("ab")).concatenate(Automaton.makeChar('z'));
		} finally {
			Automaton.setAllowMutate(b);
		}
		Automaton i = BasicAutomata.makeIntegerValue("5");
		assertTrue(i.run("5"));
		assertTrue(i.run(" 5 "));
	}

	@Test
	void integer_value_does_not_share_whitespace_automaton() {
		int m = Automaton.minimization;
		boolean b = Automaton.setAllowMutate(true);
		try {
			Automaton.setMinimization(Automaton.MINIMIZE_BRZOZOWSKI);
			BasicAutomata.makeIntegerValue("15").concatenate(Automaton.makeChar('z'));
		} finally {
			Automaton.setAllowMutate(b);
			Automaton.setMinimization(m);
		}
		Automaton i = BasicAutomata.makeIntegerValue("5");
		assertTrue(i.run("5"));
		assertTrue(i.run(" 5 "));
	}

	@Test
	void regexp_toString_of_negated_char_class_without_syntax_flags() {
		for (String s : new String[] {"[^b]", "x[^a-c\\]-]y", "[^^]*"}) {
			RegExp r = new RegExp(s, RegExp.NONE);
			assertEquals(r.toAutomaton(), new RegExp(r.toString(), RegExp.NONE).toAutomaton(), s + " -> " + r);
		}
	}

	@Test
	void regexp_toString_escapes_tilde() {
		RegExp r = new RegExp("\\~a");
		Automaton a = new RegExp(r.toString()).toAutomaton();
		assertTrue(a.run("~a"));
		assertFalse(a.run("b"));
	}
}
