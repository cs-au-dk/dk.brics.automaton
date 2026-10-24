package dk.brics.automaton;

import static dk.brics.automaton.Fuzz.check;
import static dk.brics.automaton.Fuzz.show;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Metamorphic tests: algebraic laws of regular languages must hold for the results of the
 * library operations, under all combinations of the global flags. Language equality is
 * checked both with {@link Automaton#equals(Object)} and with the reference oracles on
 * sample strings.
 */
final class AlgebraicLawsTest {

	static Stream<Arguments> flags() {
		return Stream.of(
				Arguments.of(false, false, Automaton.MINIMIZE_HOPCROFT),
				Arguments.of(true, false, Automaton.MINIMIZE_HOPCROFT),
				Arguments.of(false, true, Automaton.MINIMIZE_HOPCROFT),
				Arguments.of(true, true, Automaton.MINIMIZE_HOPCROFT),
				Arguments.of(false, true, Automaton.MINIMIZE_HUFFMAN),
				Arguments.of(false, true, Automaton.MINIMIZE_BRZOZOWSKI),
				Arguments.of(false, true, Automaton.MINIMIZE_VALMARI),
				Arguments.of(true, true, Automaton.MINIMIZE_VALMARI));
	}

	private static String name(String laws, boolean allowMutate, boolean minimizeAlways, int minimization) {
		return laws + " (allowMutate=" + allowMutate + ", minimizeAlways=" + minimizeAlways + ", minimization=" + minimization + ")";
	}

	/** Checks that two automata (built from fresh inputs) have the same language. */
	private static final class Laws {
		final Random r;
		final Lang la, lb, lc;
		final Set<String> samples;
		final boolean minimizeAlways;

		Laws(Random r, Fuzz.Context ctx, boolean minimizeAlways) {
			this.r = r;
			this.minimizeAlways = minimizeAlways;
			la = Lang.random(r);
			lb = Lang.random(r);
			lc = Lang.random(r);
			ctx.add("A", la).add("B", lb).add("C", lc);
			samples = Fuzz.samples(r, la, lb, lc);
		}

		// automata built directly from the inputs (not results of operations)
		final Set<Automaton> raw = Collections.newSetFromMap(new IdentityHashMap<>());

		// fresh automata for each use, since operations may mutate their inputs if allowed
		Automaton a() {
			return fresh(la);
		}

		Automaton b() {
			return fresh(lb);
		}

		Automaton c() {
			return fresh(lc);
		}

		private Automaton fresh(Lang l) {
			Automaton x = l.automaton();
			if (minimizeAlways)
				x.minimize(); // operations on minimal automata should then produce minimal automata
			else
				raw.add(x);
			return x;
		}

		void same(String law, Automaton x, Automaton y, Lang expected) {
			if (expected != null) {
				Fuzz.checkLanguage(x, expected, samples, law + " (left side)");
				Fuzz.checkLanguage(y, expected, samples, law + " (right side)");
			}
			if (minimizeAlways) {
				checkMinimal(x, law + " (left side)");
				checkMinimal(y, law + " (right side)");
			}
			if (!x.equals(y)) {
				String w = x.clone().minus(y.clone()).union(y.clone().minus(x.clone())).getShortestExample(true);
				throw new AssertionError(law + ": the two sides should be equal, but differ on " + show(w));
			}
			check(x.hashCode() == y.hashCode(), law + ": equal automata should have equal hash codes");
		}

		void checkMinimal(Automaton x, String what) {
			if (x.isSingleton() || raw.contains(x))
				return;
			check(Fuzz.isActuallyDeterministic(x), what + ": should be deterministic with minimize-always");
			int n = x.getNumberOfStates();
			Automaton m = x.clone();
			MinimizationOperations.minimizeHopcroft(m);
			check(m.getNumberOfStates() == n, what + ": should be minimal with minimize-always (" + n + " states, minimal has " + m.getNumberOfStates() + ")");
		}
	}

	@ParameterizedTest(name = "allowMutate={0}, minimizeAlways={1}, minimization={2}")
	@MethodSource("flags")
	void boolean_algebra(boolean allowMutate, boolean minimizeAlways, int minimization) {
		Fuzz.withFlags(allowMutate, minimizeAlways, minimization, () -> Fuzz.repeat(name("boolean laws", allowMutate, minimizeAlways, minimization), 40, (r, ctx) -> {
			Laws l = new Laws(r, ctx, minimizeAlways);
			Lang aub = Lang.union(l.la, l.lb), aib = Lang.intersection(l.la, l.lb);
			l.same("union commutative", l.a().union(l.b()), l.b().union(l.a()), aub);
			l.same("intersection commutative", l.a().intersection(l.b()), l.b().intersection(l.a()), aib);
			l.same("union associative", l.a().union(l.b()).union(l.c()), l.a().union(l.b().union(l.c())), Lang.union(aub, l.lc));
			l.same("intersection associative", l.a().intersection(l.b()).intersection(l.c()), l.a().intersection(l.b().intersection(l.c())), Lang.intersection(aib, l.lc));
			l.same("union idempotent", l.a().union(l.a()), l.a(), l.la);
			l.same("intersection idempotent", l.a().intersection(l.a()), l.a(), l.la);
			l.same("distributivity", l.a().intersection(l.b().union(l.c())), l.a().intersection(l.b()).union(l.a().intersection(l.c())), null);
			l.same("De Morgan (union)", l.a().union(l.b()).complement(), l.a().complement().intersection(l.b().complement()), Lang.complement(aub));
			l.same("De Morgan (intersection)", l.a().intersection(l.b()).complement(), l.a().complement().union(l.b().complement()), Lang.complement(aib));
			l.same("double complement", l.a().complement().complement(), l.a(), l.la);
			l.same("minus", l.a().minus(l.b()), l.a().intersection(l.b().complement()), Lang.minus(l.la, l.lb));
			l.same("union with complement", l.a().union(l.a().complement()), BasicAutomata.makeAnyString(), null);
			l.same("intersection with complement", l.a().intersection(l.a().complement()), BasicAutomata.makeEmpty(), null);
			l.same("union with empty", l.a().union(BasicAutomata.makeEmpty()), l.a(), l.la);
			l.same("intersection with any", l.a().intersection(BasicAutomata.makeAnyString()), l.a(), l.la);
			l.same("union of collection", Automaton.union(Arrays.asList(l.a(), l.b(), l.c())), l.a().union(l.b()).union(l.c()), null);
			check(l.a().subsetOf(l.a().union(l.b())), "A should be a subset of A|B");
			check(l.a().intersection(l.b()).subsetOf(l.a()), "A&B should be a subset of A");
			check(l.a().minus(l.b()).intersection(l.b()).isEmpty(), "(A-B)&B should be empty");
			check(l.a().union(l.a().complement()).isTotal(), "A|~A should be total");
			check(l.a().subsetOf(l.b()) == l.a().union(l.b()).equals(l.b()), "A subset of B iff A|B = B");
			check(l.a().subsetOf(l.b()) == l.a().minus(l.b()).isEmpty(), "A subset of B iff A-B is empty");
		}));
	}

	@ParameterizedTest(name = "allowMutate={0}, minimizeAlways={1}, minimization={2}")
	@MethodSource("flags")
	void concatenation_and_repetition(boolean allowMutate, boolean minimizeAlways, int minimization) {
		Fuzz.withFlags(allowMutate, minimizeAlways, minimization, () -> Fuzz.repeat(name("concat laws", allowMutate, minimizeAlways, minimization), 40, (r, ctx) -> {
			Laws l = new Laws(r, ctx, minimizeAlways);
			Automaton eps = BasicAutomata.makeEmptyString();
			l.same("concatenation associative", l.a().concatenate(l.b()).concatenate(l.c()), l.a().concatenate(l.b().concatenate(l.c())),
					Lang.concat(Lang.concat(l.la, l.lb), l.lc));
			l.same("concatenation of list", Automaton.concatenate(Arrays.asList(l.a(), l.b(), l.c())), l.a().concatenate(l.b()).concatenate(l.c()), null);
			l.same("empty string is neutral (left)", BasicAutomata.makeEmptyString().concatenate(l.a()), l.a(), l.la);
			l.same("empty string is neutral (right)", l.a().concatenate(BasicAutomata.makeEmptyString()), l.a(), l.la);
			check(l.a().concatenate(BasicAutomata.makeEmpty()).isEmpty(), "concatenation with the empty language should be empty");
			l.same("concatenation distributes over union", l.a().concatenate(l.b().union(l.c())), l.a().concatenate(l.b()).union(l.a().concatenate(l.c())), null);
			l.same("star unfolding", l.a().repeat(), eps.union(l.a().concatenate(l.a().repeat())), Lang.repeat(l.la, 0, -1));
			l.same("star idempotent", l.a().repeat().repeat(), l.a().repeat(), null);
			l.same("plus", l.a().repeat(1), l.a().concatenate(l.a().repeat()), Lang.repeat(l.la, 1, -1));
			l.same("optional", l.a().optional(), l.a().union(BasicAutomata.makeEmptyString()), null);
			l.same("star of optional", l.a().optional().repeat(), l.a().repeat(), null);
			int min = r.nextInt(3), max = min + r.nextInt(3);
			Automaton expected = BasicAutomata.makeEmpty();
			for (int k = min; k <= max; k++)
				expected = expected.union(power(l, k));
			l.same("repeat(" + min + "," + max + ")", l.a().repeat(min, max), expected, Lang.repeat(l.la, min, max));
			l.same("repeat(" + min + ")", l.a().repeat(min), power(l, min).concatenate(l.a().repeat()), Lang.repeat(l.la, min, -1));
			check(l.a().repeat(max + 1, max).isEmpty(), "repeat with min > max should be empty");
			l.same("repeat(0,0)", l.a().repeat(0, 0), BasicAutomata.makeEmptyString(), null);
			l.same("repeat(1,1)", l.a().repeat(1, 1), l.a(), l.la);
		}));
	}

	private static Automaton power(Laws l, int k) {
		Automaton res = BasicAutomata.makeEmptyString();
		for (int i = 0; i < k; i++)
			res = res.concatenate(l.a());
		return res;
	}

	@ParameterizedTest(name = "allowMutate={0}, minimizeAlways={1}, minimization={2}")
	@MethodSource("flags")
	void representations(boolean allowMutate, boolean minimizeAlways, int minimization) {
		Fuzz.withFlags(allowMutate, minimizeAlways, minimization, () -> Fuzz.repeat(name("representation laws", allowMutate, minimizeAlways, minimization), 40, (r, ctx) -> {
			Laws l = new Laws(r, ctx, minimizeAlways);
			Automaton d = l.a();
			d.determinize();
			l.same("determinize", d, l.a(), l.la);
			Automaton m = l.a();
			m.minimize();
			l.same("minimize", m, l.a(), l.la);
			l.same("clone", l.a().clone(), l.a(), l.la);
			l.same("store/load", Fuzz.storeLoad(l.a()), l.a(), l.la);
			Automaton x = l.a();
			x.expandSingleton();
			l.same("expandSingleton", x, l.a(), l.la);
			x = l.a();
			x.removeDeadTransitions();
			l.same("removeDeadTransitions", x, l.a(), l.la);
			Automaton rev = l.a();
			rev.expandSingleton();
			SpecialOperations.reverse(rev);
			Automaton rev2 = rev.clone();
			SpecialOperations.reverse(rev2);
			l.raw.add(rev2); // reverse is a low-level operation that does not minimize
			l.same("reverse of reverse", rev2, l.a(), l.la);
			Automaton ab = l.a().concatenate(l.b());
			ab.expandSingleton();
			SpecialOperations.reverse(ab);
			l.raw.add(ab);
			Automaton ra = l.a(), rb = l.b();
			ra.expandSingleton();
			rb.expandSingleton();
			SpecialOperations.reverse(ra);
			SpecialOperations.reverse(rb);
			l.same("reverse of concatenation", ab, rb.concatenate(ra), null);
		}));
	}
}
