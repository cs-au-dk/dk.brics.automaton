package dk.brics.automaton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class FiniteStringsTest {

	@ParameterizedTest(name = "{0}")
	@MethodSource("finiteAutomata")
	void enumeratesEveryAcceptedWord(String name, Automaton automaton, Set<String> expected) {
		assertEquals(expected, automaton.getFiniteStrings());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("finiteAutomata")
	void countsEveryWordAgainstTheLimit(String name, Automaton automaton, Set<String> expected) {
		for (int limit : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2, 3, 10}) {
			Set<String> result = automaton.getFiniteStrings(limit);
			if (limit >= 0 && expected.size() > limit)
				assertNull(result, "limit=" + limit);
			else
				assertEquals(expected, result, "limit=" + limit);
		}
	}

	@Test
	void infiniteLanguageStillReturnsNull() {
		Automaton automaton = new RegExp("a*").toAutomaton();
		assertNull(automaton.getFiniteStrings());
		for (int limit : new int[] {-1, 0, 1, 10})
			assertNull(automaton.getFiniteStrings(limit));
	}

	static Stream<Arguments> finiteAutomata() {
		Automaton expandedEmptyWord = BasicAutomata.makeEmptyString();
		expandedEmptyWord.expandSingleton();
		Automaton expandedWord = BasicAutomata.makeString("word");
		expandedWord.expandSingleton();
		Automaton duplicatePaths = new Automaton();
		State first = new State();
		State second = new State();
		first.setAccept(true);
		second.setAccept(true);
		duplicatePaths.getInitialState().setAccept(true);
		duplicatePaths.getInitialState().addTransition(new Transition('a', first));
		duplicatePaths.getInitialState().addTransition(new Transition('a', second));
		duplicatePaths.setDeterministic(false);
		return Stream.of(
				Arguments.of("empty language", BasicAutomata.makeEmpty(), Collections.emptySet()),
				Arguments.of("singleton empty word", BasicAutomata.makeEmptyString(), words("")),
				Arguments.of("expanded empty word", expandedEmptyWord, words("")),
				Arguments.of("singleton nonempty word", BasicAutomata.makeString("word"), words("word")),
				Arguments.of("expanded nonempty word", expandedWord, words("word")),
				Arguments.of("union with empty language", BasicOperations.union(
						BasicAutomata.makeString(""), BasicAutomata.makeEmpty()), words("")),
				Arguments.of("optional character range", new RegExp("[a-c]?").toAutomaton(), words("", "a", "b", "c")),
				Arguments.of("duplicate accepting paths", duplicatePaths, words("", "a")));
	}

	private static Set<String> words(String... values) {
		return new HashSet<String>(Arrays.asList(values));
	}
}
