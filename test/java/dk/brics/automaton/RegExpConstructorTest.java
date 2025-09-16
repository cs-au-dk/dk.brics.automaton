package dk.brics.automaton;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link RegExp} syntax across all flag combinations.
 */
final class RegExpConstructorTest {

	/**
	 * Test case data structure
	 */
	private static class TestCase {
		final String input;
		// if shouldParse(flags) then we expect new Regexp(input, flags) to succeed. Otherwise we
		// expect it to raise.
		final IntPredicate shouldParse;
		final List<String> expectedToStringResults;

		TestCase(String input, IntPredicate shouldParse, List<String> expectedToStringResults) {
			this.input = input;
			this.shouldParse = shouldParse;
			this.expectedToStringResults = expectedToStringResults;
		}

		TestCase(String input, IntPredicate shouldParse) {
			this(input, shouldParse, new ArrayList<>());
		}

		TestCase(String input, IntPredicate shouldParse, String expectedToString) {
			this(input, shouldParse, Arrays.asList(expectedToString));
		}

		TestCase(String input, IntPredicate shouldParse,
				 String expectedToString1, String expectedToString2) {
			this(input, shouldParse,
				 Arrays.asList(expectedToString1, expectedToString2));
		}
	}

	// Predicate constants for parsing behavior
	private static final IntPredicate NEVER_FAILS = flags -> true;
	private static final IntPredicate ALWAYS_FAILS = flags -> false;

	static Stream<TestCase> runScenarios() {
		return Stream.of(
				new TestCase("", NEVER_FAILS, "\"\""),
				new TestCase(".", NEVER_FAILS, "."),
				new TestCase("a", NEVER_FAILS, "a"),
				new TestCase(".*", NEVER_FAILS, "(.)*"),
				new TestCase("$", NEVER_FAILS, "$"),
				new TestCase("()", NEVER_FAILS, "\"\""),

				new TestCase("a|b", NEVER_FAILS, "(a|b)"),
				new TestCase("a|()", NEVER_FAILS, "(a|\"\")"),
				new TestCase("()|a", NEVER_FAILS, "(\"\"|a)"),
				new TestCase("|a", NEVER_FAILS, "(\"\"|a)"),
				new TestCase("a|", NEVER_FAILS, "(a|\"\")"),

				new TestCase("a()b", NEVER_FAILS, "\"ab\""),

				new TestCase("a&b", NEVER_FAILS, "(a&b)", "\"a&b\""),
				new TestCase("a&", NEVER_FAILS, "(a&\"\")", "\"a&\""),
				new TestCase("&a", NEVER_FAILS, "(\"\"&a)", "\"&a\""),

				new TestCase("*", ALWAYS_FAILS),
				new TestCase("?", ALWAYS_FAILS),
				new TestCase("+", ALWAYS_FAILS),
				new TestCase("|", NEVER_FAILS, "(\"\"|\"\")"),
				new TestCase(">", NEVER_FAILS, "\\>"),
				new TestCase("&", NEVER_FAILS, "(\"\"&\"\")", "\\&"),
				new TestCase("]", NEVER_FAILS, "\\]"),
				new TestCase("{", NEVER_FAILS, "\\{"),
				new TestCase("}", NEVER_FAILS, "\\}"),
				new TestCase("(", ALWAYS_FAILS),
				new TestCase(")", ALWAYS_FAILS),
				new TestCase("\"", ALWAYS_FAILS),
				new TestCase("#", NEVER_FAILS, "#", "\\#"),
				new TestCase("@", NEVER_FAILS, "@", "\\@"),
				new TestCase("~", flags -> ((flags & RegExp.COMPLEMENT) == 0), "\\~"),

				new TestCase("<a>",
							 flags -> ((flags & RegExp.AUTOMATON) != 0 || (flags & RegExp.INTERVAL) == 0),
							 "<a>", "\"<a>\""),
				new TestCase("<3-4>",
							 flags -> ((flags & RegExp.INTERVAL) != 0 || (flags & RegExp.AUTOMATON) == 0),
							 "<3-4>", "\"<3-4>\""),
				new TestCase("<3,4>",
							 flags -> ((flags & RegExp.AUTOMATON) != 0 || (flags & RegExp.INTERVAL) == 0),
							 "<3,4>", "\"<3,4>\""),
				new TestCase("<",
							 flags -> ((flags & (RegExp.INTERVAL | RegExp.AUTOMATON)) == 0),
							 "\\<"), // Unclosed angle bracket

				new TestCase("~a", NEVER_FAILS, "~(a)", "\"~a\""),
				new TestCase("~\\~", NEVER_FAILS, "~(\\~)", "\"~~\""),

				new TestCase("[", ALWAYS_FAILS), // Unclosed character class
				new TestCase("[]", ALWAYS_FAILS), // ‘empty’ (actually unclosed) character class
				new TestCase("[\\]", ALWAYS_FAILS), // Unclosed character class with escape
				new TestCase("[^]", ALWAYS_FAILS), // Unclosed character class with complement
				new TestCase("[]]", NEVER_FAILS, "\\]"),
				new TestCase("[[]]", NEVER_FAILS, "\"[]\""),
				new TestCase("[][]", NEVER_FAILS, "(\\]|\\[)"),
				new TestCase("[[]", NEVER_FAILS, "\\["),
				new TestCase("[-z]", NEVER_FAILS, "(\\-|z)"),
				new TestCase("[a-]", NEVER_FAILS, "(a|\\-)"),
				new TestCase("[a-z-A]", NEVER_FAILS, "(([\\a-\\z]|\\-)|A)"),

				new TestCase("{}", NEVER_FAILS, "\"{}\""),
				new TestCase("{1}", NEVER_FAILS, "\"{1}\""),
				new TestCase("{{1}", NEVER_FAILS, "(\\{){1,1}"),
				new TestCase("{{1,2}", NEVER_FAILS, "(\\{){1,2}"),
				new TestCase("{1;2}", NEVER_FAILS, "\"{1;2}\""),
				new TestCase("{1,2}", NEVER_FAILS, "\"{1,2}\""),
				new TestCase("{{", ALWAYS_FAILS), // Unclosed repetition
				new TestCase("{{1;2}", ALWAYS_FAILS) // Malformed nested repetition
		);
	}

	@MethodSource("runScenarios")
	@ParameterizedTest
	void test_flag_combination(TestCase testCase) {
		Set<String> observedToStringResults = new HashSet<>();

		int allFlags = RegExp.INTERSECTION | RegExp.COMPLEMENT | RegExp.EMPTY
			| RegExp.ANYSTRING | RegExp.AUTOMATON | RegExp.INTERVAL;
		for (int flags = 0; flags <= allFlags; flags++) { // Test all combinations of the 6 flags
			boolean shouldParse = testCase.shouldParse.test(flags);
			boolean actuallyParsed = false;
			RegExp regexp = null;

			try {
				regexp = new RegExp(testCase.input, flags);
				actuallyParsed = true;
			} catch (IllegalArgumentException e) {
				actuallyParsed = false;
			}

			if (!shouldParse && actuallyParsed) {
				fail("Unexpected parse for input=\"" + testCase.input
					 + "\" flags=0x" + Integer.toHexString(flags)
					 + " (" + describeFlagCombination(flags)
					 + ") - expected to fail but parsed successfully");
			} else if (shouldParse && !actuallyParsed) {
				fail("Unexpected failure for input=\"" + testCase.input
					 + "\" flags=0x" + Integer.toHexString(flags)
					 + " (" + describeFlagCombination(flags)
					 + ") - expected to parse but failed");
			} else if (actuallyParsed) {
				// Collect toString results for successful parses
				String toStringResult = regexp.toString();
				observedToStringResults.add(toStringResult);

				// Check if this result is in the expected list
				if (!testCase.expectedToStringResults.contains(toStringResult)) {
					fail("Unexpected toString result for input=\"" + testCase.input
						 + "\" flags=0x" + Integer.toHexString(flags) +
						 " expected one of " + testCase.expectedToStringResults
						 + " but got \"" + toStringResult + "\"");
				}
			}
		}

		// Check that we saw every expected toString result at least once
		if (!testCase.expectedToStringResults.isEmpty()) {
			Set<String> expectedSet = new HashSet<>(testCase.expectedToStringResults);
			if (!observedToStringResults.containsAll(expectedSet)) {
				Set<String> missing = new HashSet<>(expectedSet);
				missing.removeAll(observedToStringResults);
				fail("Missing expected toString results for input=\"" + testCase.input + "\": "
					 + missing + " (observed: " + observedToStringResults + ")");
			}
		}
	}

	// Helper method to describe flag combinations in readable format
	private static String describeFlagCombination(int flags) {
		List<String> parts = new ArrayList<>();
		if ((flags & RegExp.INTERSECTION) != 0) parts.add("INTERSECTION");
		if ((flags & RegExp.COMPLEMENT) != 0) parts.add("COMPLEMENT");
		if ((flags & RegExp.EMPTY) != 0) parts.add("EMPTY");
		if ((flags & RegExp.ANYSTRING) != 0) parts.add("ANYSTRING");
		if ((flags & RegExp.AUTOMATON) != 0) parts.add("AUTOMATON");
		if ((flags & RegExp.INTERVAL) != 0) parts.add("INTERVAL");

		if (parts.isEmpty()) return "NONE";
		return String.join("|", parts);
	}
}
