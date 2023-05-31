package dk.brics.automaton;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link RunAutomaton}
 */
final class RunAutomatonTest {

	private final DatatypesAutomatonProvider automatonProvider = new DatatypesAutomatonProvider(true,
			true,
			true);

	@MethodSource("runScenarios")
	@ParameterizedTest
	void boolean_matches_as_expected(final int posStart, final int posEnd, final RegExp regex,
			final String input) {

		final Automaton automaton = regex.toAutomaton(automatonProvider, true);
		final boolean expectedMatch = posStart != NOT_MATCHED;

		// Test non-tabelised versions with matcher
		final RunAutomaton noTableize = new RunAutomaton(automaton, false);
		assertEquals(expectedMatch, noTableize.run(input));
		assertEquals(expectedMatch, noTableize.newMatcher(input).find());

		// Test tabelised versions with matcher
		final RunAutomaton tableize = new RunAutomaton(automaton, true);
		assertEquals(expectedMatch, tableize.run(input));
		assertEquals(expectedMatch, tableize.newMatcher(input).find());
	}

	@MethodSource("runScenarios")
	@ParameterizedTest
	void pos_found_as_expected(final int posStart, final int posEnd, final RegExp regex,
			final String input) {

		final Automaton automaton = regex.toAutomaton(automatonProvider, true);
		final int expectedLen = posStart == NOT_MATCHED ? NOT_MATCHED : posEnd - posStart;

		final RunAutomaton noTableize = new RunAutomaton(automaton, false);
		assertEquals(expectedLen, noTableize.run(input, 0));

		final RunAutomaton tableize = new RunAutomaton(automaton, true);
		assertEquals(expectedLen, tableize.run(input, 0));
	}

	static Stream<Arguments> runScenarios() {
		final String aThenbThenc = "a+b+c+";
		final String aToZThen4ExclamationMarks = "([a-z]{1,3}!{4})";

		return Stream.of(
				// Matches
				Arguments.of(0, 8, new RegExp(aThenbThenc), "aaabbbcc"),
				// No match
				Arguments.of(NOT_MATCHED, 0, new RegExp(aThenbThenc), "aaaaBBcc"),
				// Any char
				Arguments.of(0, 9, new RegExp("a+.+c+"), "aaaBB12cc"),
				// Empty language, always rejects
				Arguments.of(NOT_MATCHED, 0, new RegExp("#"), "#"),
				// Escaped hash
				Arguments.of(0, 3, new RegExp(".+\\#"), "12#"),
				// Any string
				Arguments.of(0, 9, new RegExp("a+@"), "aaaBB12cc"),
				// Empty string
				Arguments.of(0, 0, new RegExp("()"), ""),
				// Unicode string without quotes
				Arguments.of(0, 14, new RegExp("123\"MyString\"456"), "123MyString456"),
				// Double quote escaped so not the start of a Unicode string
				Arguments.of(0, 9, new RegExp("123\\\"stuff"), "123\"stuff"),
				// Numerical interval
				Arguments.of(0, 4, new RegExp("(<1-99>)+"), "9897"),
				// Named automaton from Datatypes
				Arguments.of(0, 8, new RegExp("<whitespacechar>+"), "  \n \r \t "),
				// Char class
				Arguments.of(0, 10, new RegExp("[0-9A-z]+"), "13aDfzZ345"),
				// Negation no match with char class
				Arguments.of(NOT_MATCHED, 0, new RegExp("[^0-9A-z]+"), "13aDfzZ345"),
				// Negation that matches
				Arguments.of(0, 6, new RegExp("[^0-9A-z]+"), "!()+{}"),
				// Matches union
				Arguments.of(0, 7, new RegExp(aToZThen4ExclamationMarks), "adf!!!!"),
				// Complement matches
				Arguments.of(0, 7, new RegExp("~" + aToZThen4ExclamationMarks), "Abcd23£"),
				// Match any char 1 or more times
				Arguments.of(0, 3, new RegExp(".+"), "165"),
				// No match empty string
				Arguments.of(NOT_MATCHED, 0, new RegExp(".+"), "")
		);
	}

	static final int NOT_MATCHED = -1;
}
