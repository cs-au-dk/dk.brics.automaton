package dk.brics.automaton;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Objects;

/**
 * Supports an optimised version of substring matching an automaton against an input string in linear
 * time with no backtracking.
 *
 * <p>In essence we simply take an automaton "mypattern" and match against ".*mypattern". See the docs
 * here from Russ Cox: <a href="https://swtch.com/~rsc/regexp/regexp3.html">Link</a>.
 *
 * <p><strong>As such we do not currently support identifying the start, end position of a
 * match, simply that a match exists.</strong></p>
 *
 * <p>Execution time is linear to the length of the string O(n) for the method {@link
 * #matches(CharSequence)} below.
 */
public class MatchOnlyRunAutomaton implements Serializable {
	static final long serialVersionUID = 50001;

	private final RunAutomaton automaton;

	public MatchOnlyRunAutomaton(final Automaton a) {
		this(a, true);
	}

	public MatchOnlyRunAutomaton(Automaton a, final boolean tableize) {
		Objects.requireNonNull(a);
		this.automaton = new RunAutomaton(addWildcard(a), tableize, ACCEPTED_STATE);
	}

	/**
	 * Search the input in linear time based on the size of the input to identify whether this automaton
	 * is contained anywhere in the input.
	 *
	 * <p>Has the following constraints:
	 *
	 * <ol>
	 *   <li>In the worst case, the execution is equivalent to the length of the input O(n) as we scan
	 *       the whole input.
	 *   <li>There is no backtracking, we simply scan the input char by char until we either match or
	 *       reach the end.
	 *   <li>Method returns immediately if it finds enough chars to complete a match of the pattern.
	 *   <li>The method and class has no ability to locate the position of the match, simply that is
	 *       either exists or not.
	 * </ol>
	 *
	 * @param input the input to match a substring pattern against
	 * @return {@code true} if the pattern is found in a substring in the input, or {@code false} if
	 * the input is not matched. We also return false for null inputs.
	 */
	public boolean matches(final CharSequence input) {
		return matches(input, 0);
	}

	/**
	 * Search the input in linear time based on the size of the input to identify whether this pattern
	 * is contained anywhere in the input from a starting position.
	 *
	 * <p>See {@link #matches(CharSequence)} for more information.
	 *
	 * <p>This version of the method searches from a specific starting position.</p>
	 *
	 * @param input the input to match a substring pattern against
	 * @param pos   the position to start searching from
	 * @return {@code true} if the pattern is found in a substring in the input, or {@code false} if
	 * the input is not matched. We also return false for null inputs
	 * @throws IllegalArgumentException if the pos is negative or larger than the input length.
	 */
	public boolean matches(final CharSequence input, final int pos) {
		if (input == null) {
			return false;
		}

		int len = input.length();

		if (pos < 0 || (pos > len - 1 && pos != 0)) {
			throw new IllegalArgumentException("pos cannot be negative or larger than the input len");
		}

		final int initial = automaton.getInitialState();
		int state = initial;

		for (int i = pos; i < len; i++) {
			state = automaton.step(state, input.charAt(i));
			if (state == -1) {
				// If we fail then continue at the next char from the initial state, i.e. wildcard match
				// from start
				state = initial;
			} else if (state == ACCEPTED_STATE) {
				return true;
			}
		}

		// end of the input return accept for state
		return automaton.isAccept(state);
	}

	/**
	 * Retrieves a serialized <code>MatchOnlyRunAutomaton</code> from a stream.
	 * @param stream input stream with serialized automaton
	 * @exception IOException if input/output related exception occurs
	 * @exception ClassCastException if the data is not a serialized <code>MatchOnlyRunAutomaton</code>
	 * @exception ClassNotFoundException if the class of the serialized object cannot be found
	 */
	public static MatchOnlyRunAutomaton load(final InputStream stream) throws IOException, ClassCastException, ClassNotFoundException {
		final ObjectInputStream s = new ObjectInputStream(stream);
		return (MatchOnlyRunAutomaton) s.readObject();
	}

	/**
	 * Writes this <code>MatchOnlyRunAutomaton</code> to the given stream.
	 * @param stream output stream for serialized automaton
	 * @exception IOException if input/output related exception occurs
	 */
	public void store(final OutputStream stream) throws IOException {
		final ObjectOutputStream s = new ObjectOutputStream(stream);
		s.writeObject(this);
		s.flush();
	}

	@Override
	public String toString() {
		return automaton.toString();
	}

	private static Automaton addWildcard(final Automaton automaton) {
		// Add a wildcard match to the start of the automaton
		return WILDCARD_ZERO_OR_MORE.concatenate(automaton);
	}

	private static final Automaton WILDCARD_ZERO_OR_MORE = new RegExp(".*").toAutomaton(true);
	// Transitions do not contain a negative value of -2, therefore use this to encode whether the
	// accept is set or not for the target state directly into the transition
	private static final int ACCEPTED_STATE = -2;
}
