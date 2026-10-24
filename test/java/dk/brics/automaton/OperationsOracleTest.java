package dk.brics.automaton;

import static dk.brics.automaton.Fuzz.check;
import static dk.brics.automaton.Fuzz.checkEq;
import static dk.brics.automaton.Fuzz.checkLanguage;
import static dk.brics.automaton.Fuzz.isActuallyDeterministic;
import static dk.brics.automaton.Fuzz.show;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Checks the results of the basic operations against reference membership oracles
 * on random languages, and that the operations do not modify the languages of their inputs.
 */
final class OperationsOracleTest {

	@Test
	void constructed_automata_match_their_oracles() {
		Fuzz.repeat("constructed", 400, (r, ctx) -> {
			Lang l = Lang.random(r);
			ctx.add("L", l);
			Set<String> samples = Fuzz.samples(r, l);
			checkLanguage(l.automaton(), l, samples, "automaton");
		});
	}

	@Test
	void boolean_operations() {
		Fuzz.repeat("boolean", 250, (r, ctx) -> {
			Lang la = Lang.random(r), lb = Lang.random(r);
			ctx.add("A", la).add("B", lb);
			Set<String> samples = Fuzz.samples(r, la, lb);
			Automaton a = la.automaton(), b = lb.automaton();
			checkLanguage(a.union(b), Lang.union(la, lb), samples, "union");
			checkLanguage(BasicOperations.union(Arrays.asList(a, b, a)), Lang.union(la, lb), samples, "union of list");
			checkLanguage(a.intersection(b), Lang.intersection(la, lb), samples, "intersection");
			Automaton m = a.minus(b);
			checkLanguage(m, Lang.minus(la, lb), samples, "minus");
			check(isActuallyDeterministic(m), "minus result should be deterministic");
			Automaton c = a.complement();
			checkLanguage(c, Lang.complement(la), samples, "complement");
			check(isActuallyDeterministic(c), "complement result should be deterministic");
			checkLanguage(c.complement(), la, samples, "double complement");
			// operands that are the same object
			checkLanguage(a.union(a), la, samples, "union with itself");
			checkLanguage(a.intersection(a), la, samples, "intersection with itself");
			check(a.minus(a).isEmpty(), "minus itself should be empty");
			// the operations never modify the input languages
			checkLanguage(a, la, samples, "A after operations");
			checkLanguage(b, lb, samples, "B after operations");
		});
	}

	@Test
	void concatenation_and_repetition() {
		Fuzz.repeat("concat", 250, (r, ctx) -> {
			Lang la = Lang.random(r), lb = Lang.random(r);
			ctx.add("A", la).add("B", lb);
			Set<String> samples = Fuzz.samples(r, la, lb);
			Automaton a = la.automaton(), b = lb.automaton();
			checkLanguage(a.concatenate(b), Lang.concat(la, lb), samples, "concatenate");
			checkLanguage(a.concatenate(a), Lang.concat(la, la), samples, "concatenate with itself");
			List<Automaton> l = Arrays.asList(a, b, a);
			checkLanguage(BasicOperations.concatenate(l), Lang.concat(la, Lang.concat(lb, la)), samples, "concatenate list");
			checkLanguage(BasicOperations.concatenate(Arrays.<Automaton>asList()), Lang.finite(Arrays.asList("")), samples, "concatenate empty list");
			checkLanguage(a.optional(), Lang.union(la, Lang.finite(Arrays.asList(""))), samples, "optional");
			checkLanguage(a.repeat(), Lang.repeat(la, 0, -1), samples, "repeat()");
			int min = r.nextInt(4);
			ctx.add("min", min);
			checkLanguage(a.repeat(min), Lang.repeat(la, min, -1), samples, "repeat(min)");
			int max = min + r.nextInt(4) - 1;
			ctx.add("max", max);
			Lang expected = max < min ? Lang.finite(Arrays.<String>asList()) : Lang.repeat(la, min, max);
			checkLanguage(a.repeat(min, max), expected, samples, "repeat(min, max)");
			checkLanguage(a, la, samples, "A after operations");
			checkLanguage(b, lb, samples, "B after operations");
		});
	}

	@Test
	void determinization_minimization_and_cleanup() {
		Fuzz.repeat("determinize", 300, (r, ctx) -> {
			Lang la = Lang.random(r);
			ctx.add("A", la);
			Set<String> samples = Fuzz.samples(r, la);
			Automaton a = la.automaton();
			Automaton d = a.clone();
			d.determinize();
			checkLanguage(d, la, samples, "determinize");
			check(isActuallyDeterministic(d), "determinize result should be deterministic");
			// (the individual minimization algorithms are checked in MinimizationTest)
			Automaton m = a.clone();
			m.minimize();
			checkLanguage(m, la, samples, "minimize");
			Automaton x = a.clone();
			x.reduce();
			checkLanguage(x, la, samples, "reduce");
			x = a.clone();
			x.removeDeadTransitions();
			checkLanguage(x, la, samples, "removeDeadTransitions");
			check(x.isSingleton() || x.getLiveStates().containsAll(x.getStates()) || !x.getLiveStates().contains(x.getInitialState()),
					"removeDeadTransitions should leave only live states (or the empty language)");
			x = a.clone();
			x.restoreInvariant();
			checkLanguage(x, la, samples, "restoreInvariant");
			x = a.clone();
			x.expandSingleton();
			check(!x.isSingleton(), "expandSingleton");
			checkLanguage(x, la, samples, "expandSingleton");
			checkLanguage(Fuzz.storeLoad(a), la, samples, "store/load");
			checkLanguage(Automaton.minimize(a.clone()), la, samples, "static minimize");
			checkLanguage(a, la, samples, "A after operations");
		});
	}

	@Test
	void reverse() {
		Fuzz.repeat("reverse", 300, (r, ctx) -> {
			Lang la = Lang.random(r);
			ctx.add("A", la);
			Set<String> samples = Fuzz.samples(r, la, Lang.reverse(la));
			for (String s : la.members(r, 5))
				samples.add(new StringBuilder(s).reverse().toString());
			Automaton a = la.automaton();
			a.expandSingleton();
			Set<State> accept = a.getAcceptStates();
			Set<State> initials = SpecialOperations.reverse(a);
			checkEq(accept, initials, "the new initial states should be the old accept states");
			checkLanguage(a, Lang.reverse(la), samples, "reverse");
		});
	}

	/** Random language that is likely to be related to the given one (equal or a superset). */
	private static Lang relatedLang(Random r, Lang la) {
		switch (r.nextInt(3)) {
		case 0:
			return Lang.random(r);
		case 1:
			return la;
		default:
			Lang lc = Lang.random(r);
			return Lang.built(Lang.union(la, lc), () -> la.automaton().union(lc.automaton()));
		}
	}

	@Test
	void subset_and_equality_have_witnesses() {
		Fuzz.repeat("subsetOf", 400, (r, ctx) -> {
			Lang la = Lang.random(r);
			Lang lb = relatedLang(r, la);
			Automaton a = la.automaton();
			Automaton b = lb.automaton();
			ctx.add("A", la).add("B", lb);
			Set<String> samples = Fuzz.samples(r, la, lb);
			boolean sub = a.subsetOf(b);
			for (String s : samples)
				if (la.contains(s) && !lb.contains(s))
					check(!sub, "subsetOf is true, but " + show(s) + " is in A and not in B");
			if (!sub) {
				String w = a.minus(b).getShortestExample(true);
				check(w != null && la.contains(w) && !lb.contains(w), "subsetOf is false, but there is no witness (got " + show(w) + ")");
			}
			boolean eq = a.equals(b);
			check(eq == (a.subsetOf(b) && b.subsetOf(a)), "equals should agree with subsetOf");
			if (eq) {
				check(a.hashCode() == b.hashCode(), "equal automata should have equal hash codes");
				for (String s : samples)
					check(la.contains(s) == lb.contains(s), "equals is true, but the languages differ on " + show(s));
			} else {
				String w = a.minus(b).union(b.minus(a)).getShortestExample(true);
				check(w != null && la.contains(w) != lb.contains(w), "equals is false, but there is no witness (got " + show(w) + ")");
			}
			checkLanguage(a, la, samples, "A after operations");
			checkLanguage(b, lb, samples, "B after operations");
		});
	}
}
