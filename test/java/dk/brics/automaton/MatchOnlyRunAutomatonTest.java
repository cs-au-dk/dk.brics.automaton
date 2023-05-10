package dk.brics.automaton;

import static dk.brics.automaton.RunAutomatonTest.NOT_MATCHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link MatchOnlyRunAutomaton}.
 */
final class MatchOnlyRunAutomatonTest {

	private final DatatypesAutomatonProvider automatonProvider = new DatatypesAutomatonProvider(true,
			true,
			true);

	@MethodSource("dk.brics.automaton.RunAutomatonTest#runScenarios")
	@ParameterizedTest
	void matches_substring_as_expected(final int posStart, final int posEnd, final RegExp regex,
			final String input) {

		// Do not append anything to the input for expected non-matches, as these might then match
		// For matching cases we pad the string before and after to test substring matching
		final String inputWithPreAndPostfix =
				posStart == NOT_MATCHED ? input : PREFIX_TEXT + input + POSTFIX_TEXT;

		final Automaton automaton = regex.toAutomaton(automatonProvider, true);
		final boolean expectedMatch = posStart != NOT_MATCHED;

		final MatchOnlyRunAutomaton noTableize = new MatchOnlyRunAutomaton(automaton, false);
		assertEquals(expectedMatch, noTableize.matches(inputWithPreAndPostfix));

		final int posToStart = expectedMatch ? PREFIX_TEXT.length() - 1 : 0;

		// Match from pos
		assertEquals(expectedMatch,
				noTableize.matches(inputWithPreAndPostfix, posToStart));

		final MatchOnlyRunAutomaton tableize = new MatchOnlyRunAutomaton(automaton, true);
		assertEquals(expectedMatch, tableize.matches(input));
		// Match from pos
		assertEquals(expectedMatch, tableize.matches(inputWithPreAndPostfix, posToStart));
	}

	@Test
	void handles_invalid_inputs() {
		final MatchOnlyRunAutomaton testee = new MatchOnlyRunAutomaton(
				new RegExp(".*").toAutomaton());

		// null input
		assertFalse(testee.matches(null));
		// pos outside string
		assertThrows(IllegalArgumentException.class, () -> testee.matches("123", 3));
		// negative pos
		assertThrows(IllegalArgumentException.class, () -> testee.matches("123", -1));
	}

	@Test
	void stores_and_loads_as_expected(@TempDir final File tmpdir)
			throws IOException, ClassNotFoundException {

		final Automaton automaton = new RegExp("12345.+").toAutomaton();
		final MatchOnlyRunAutomaton testee = new MatchOnlyRunAutomaton(automaton);
		final String input = "12345abc";

		assertTrue(testee.matches(input));

		// Store
		final File file = new File(tmpdir, "serialized");
		try (final FileOutputStream fos = new FileOutputStream(file)) {
			testee.store(fos);
		}

		// Load
		final MatchOnlyRunAutomaton deserialized;
		try (final FileInputStream fis = new FileInputStream(file)) {
			deserialized = MatchOnlyRunAutomaton.load(fis);
		}

		assertNotNull(deserialized);
		assertTrue(deserialized.matches(input));
	}

	private static final String PREFIX_TEXT = "$£ndmd ";
	private static final String POSTFIX_TEXT = " a+;@|Zn";
}
