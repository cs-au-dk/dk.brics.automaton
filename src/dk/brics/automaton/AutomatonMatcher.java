/*
 * dk.brics.automaton - AutomatonMatcher
 *
 * Copyright (c) 2008-2011 John Gibson
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package dk.brics.automaton;

import java.util.regex.MatchResult;

/**
 * A tool that performs match operations on a given character sequence using
 * a compiled automaton.
 *
 * @author John Gibson &lt;<a href="mailto:jgibson@mitre.org">jgibson@mitre.org</a>&gt;
 * @see RunAutomaton#newMatcher(java.lang.CharSequence)
 * @see RunAutomaton#newMatcher(java.lang.CharSequence, int, int)
 */
public class AutomatonMatcher implements MatchResult {

	AutomatonMatcher(final CharSequence chars, final RunAutomaton automaton) {
		this.chars = chars;
		this.automaton = automaton;
	}

	private final RunAutomaton automaton;
	private final CharSequence chars;

	private int matchStart = -1;

	private int matchEnd = -1;

	/**
	 * Find the next matching subsequence of the input.
	 * <br />
	 * This also updates the values for the {@code start}, {@code end}, and
	 * {@code group} methods.
	 *
	 * @return {@code true} if there is a matching subsequence.
	 */
	public boolean find() {
		int begin;
		switch(getMatchStart()) {
			case -2:
			return false;
			case -1:
			begin = 0;
				break;
			default:
			begin = getMatchEnd();
				// This occurs when a previous find() call matched the empty string. This can happen when the pattern is a* for example.
				if(begin == getMatchStart()) {
					begin += 1;
					if(begin > getChars().length()) {
						setMatch(-2, -2);
						return false;
					}
				}
		}

		int match_start;
		int match_end;
		if (automaton.isAccept(automaton.getInitialState())) {
			match_start = begin;
			match_end = begin;
		} else {
			match_start = -1;
			match_end = -1;
		}
		int l = getChars().length();
		while (begin < l) {
			int p = automaton.getInitialState();
			for (int i = begin; i < l; i++) {
				final int new_state = automaton.step(p, getChars().charAt(i));
				if (new_state == -1) {
				    break;
				} else if (automaton.isAccept(new_state)) {
				    // found a match from begin to (i+1)
				    match_start = begin;
				    match_end=(i+1);
				}
				p = new_state;
			}
			if (match_start != -1) {
				setMatch(match_start, match_end);
				return true;
			}
			begin += 1;
		}
		if (match_start != -1) {
			setMatch(match_start, match_end);
			return true;
		} else {
			setMatch(-2, -2);
			return false;
		}
	}

	private void setMatch(final int matchStart, final int matchEnd) throws IllegalArgumentException {
		if (matchStart > matchEnd) {
			throw new IllegalArgumentException("Start must be less than or equal to end: " + matchStart + ", " + matchEnd);
		}
		this.matchStart = matchStart;
		this.matchEnd = matchEnd;
	}

	private int getMatchStart() {
		return matchStart;
	}

	private int getMatchEnd() {
		return matchEnd;
	}

	private CharSequence getChars() {
		return chars;
	}

	/**
	 * Returns the offset after the last character matched.
	 *
	 * @return The offset after the last character matched.
	 * @throws IllegalStateException if there has not been a match attempt or
	 *  if the last attempt yielded no results.
	 */
	public int end() throws IllegalStateException {
		matchGood();
		return matchEnd;
	}

	/**
	 * Returns the offset after the last character matched of the specified
	 * capturing group.
	 * <br />
	 * Note that because the automaton does not support capturing groups the
	 * only valid group is 0 (the entire match).
	 *
	 * @param group the desired capturing group.
	 * @return The offset after the last character matched of the specified
	 *  capturing group.
	 * @throws IllegalStateException if there has not been a match attempt or
	 *  if the last attempt yielded no results.
	 * @throws IndexOutOfBoundsException if the specified capturing group does
	 *  not exist in the underlying automaton.
	 */
	public int end(final int group) throws IndexOutOfBoundsException, IllegalStateException {
		onlyZero(group);
		return end();
	}

	/**
	 * Returns the subsequence of the input found by the previous match.
	 *
	 * @return The subsequence of the input found by the previous match.
	 * @throws IllegalStateException if there has not been a match attempt or
	 *  if the last attempt yielded no results.
	 */
	public String group() throws IllegalStateException {
		matchGood();
		return chars.subSequence(matchStart, matchEnd).toString();
	}

	/**
	 * Returns the subsequence of the input found by the specified capturing
	 * group during the previous match operation.
	 * <br />
	 * Note that because the automaton does not support capturing groups the
	 * only valid group is 0 (the entire match).
	 *
	 * @param group the desired capturing group.
	 * @return The subsequence of the input found by the specified capturing
	 *  group during the previous match operation the previous match. Or
	 *  {@code null} if the given group did match.
	 * @throws IllegalStateException if there has not been a match attempt or
	 *  if the last attempt yielded no results.
	 * @throws IndexOutOfBoundsException if the specified capturing group does
	 *  not exist in the underlying automaton.
	 */
	public String group(final int group) throws IndexOutOfBoundsException, IllegalStateException {
		onlyZero(group);
		return group();
	}

	/**
	 * Returns the number of capturing groups in the underlying automaton.
	 * <br />
	 * Note that because the automaton does not support capturing groups this
	 * method will always return 0.
	 *
	 * @return The number of capturing groups in the underlying automaton.
	 */
	public int groupCount() {
		return 0;
	}

	/**
	 * Returns the offset of the first character matched.
	 *
	 * @return The offset of the first character matched.
	 * @throws IllegalStateException if there has not been a match attempt or
	 *  if the last attempt yielded no results.
	 */
	public int start() throws IllegalStateException {
		matchGood();
		return matchStart;
	}

	/**
	 * Returns the offset of the first character matched of the specified
	 * capturing group.
	 * <br />
	 * Note that because the automaton does not support capturing groups the
	 * only valid group is 0 (the entire match).
	 *
	 * @param group the desired capturing group.
	 * @return The offset of the first character matched of the specified
	 *  capturing group.
	 * @throws IllegalStateException if there has not been a match attempt or
	 *  if the last attempt yielded no results.
	 * @throws IndexOutOfBoundsException if the specified capturing group does
	 *  not exist in the underlying automaton.
	 */
	public int start(int group) throws IndexOutOfBoundsException, IllegalStateException {
		onlyZero(group);
		return start();
	}

	/**
	 * Returns the current state of this {@code AutomatonMatcher} as a
	 * {@code MatchResult}.
	 * The result is unaffected by subsequent operations on this object.
	 *
	 * @return a {@code MatchResult} with the state of this
	 *  {@code AutomatonMatcher}.
	 */
	public MatchResult toMatchResult() {
		final AutomatonMatcher match = new AutomatonMatcher(chars, automaton);
		match.matchStart = this.matchStart;
		match.matchEnd = this.matchEnd;
		return match;
	}

	/** Helper method that requires the group argument to be 0. */
	private static void onlyZero(final int group) throws IndexOutOfBoundsException {
		if (group != 0) {
			throw new IndexOutOfBoundsException("The only group supported is 0.");
		}
	}

	/** Helper method to check that the last match attempt was valid. */
	private void matchGood() throws IllegalStateException {
		if ((matchStart < 0) || (matchEnd < 0)) {
			throw new IllegalStateException("There was no available match.");
		}
	}
}
