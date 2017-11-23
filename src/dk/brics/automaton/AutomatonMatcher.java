/*
 * dk.brics.automaton - AutomatonMatcher
 *
 * Copyright (c) 2008-2017 John Gibson
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

    private static final int UNSET = -1;
    private static final int NO_MORE_MATCH = -2;

    private final RunAutomaton automaton;
    private final CharSequence chars;
    private int matchStart = UNSET;
    private int matchEnd = UNSET;
    private int searchStart = UNSET;

    public AutomatonMatcher(CharSequence chars, RunAutomaton automaton) {
        this.chars = chars;
        this.automaton = automaton;
    }

    /**
     * Gets the {@link CharSequence} this {@link AutomatonMatcher} searches in.
     */
    public CharSequence getChars() {
        return chars;
    }

    /**
     * Sets the search start at the provided index.
     * <p/>
     * The next call to {@link #find()} will start searching from this index (included) in the
     * {@link CharSequence}, and will disable the search start at the same time.
     * Subsequent calls to {@link #find()} will start searching from the match {@link #end()}
     * as usual.
     * <p/>
     * Setting a negative value disables the search start and the next call to {@link #find()} will
     * start normally from the {@link #end()} of the previous match.
     * <p/>
     * Setting a value greater than the {@link CharSequence#length()} will set the search start
     * at the end of the {@link CharSequence}.
     */
    public void setSearchStart(int searchStart) {
        this.searchStart = Math.min(searchStart, chars.length());
    }

    /**
     * Finds the next matching subsequence of the input.
     * <p/>
     * Also updates the values for the {@link #start()}, {@link #end()}, and {@link #group()} methods.
     *
     * @return {@code true} if and only if there is a matching subsequence.
     */
    public boolean find() {
        int begin;
        if (searchStart >= 0) {
            begin = searchStart;
            searchStart = UNSET;
        } else {
            switch (matchStart) {
                case NO_MORE_MATCH:
                    return false;
                case UNSET:
                    begin = 0;
                    break;
                default:
                    begin = matchEnd;
                    // This occurs when a previous find() call matched the empty string. This can happen when the pattern is a* for example.
                    if (begin == matchStart) {
                        if (++begin > chars.length()) {
                            setMatch(NO_MORE_MATCH, NO_MORE_MATCH);
                            return false;
                        }
                    }
            }
        }

        int initialState = automaton.getInitialState();
        int match_start;
        int match_end;
        if (automaton.isAccept(initialState)) {
            match_start = begin;
            match_end = begin;
        } else {
            match_start = UNSET;
            match_end = UNSET;
        }
        for (int length = chars.length(); begin < length; begin++) {
            if (!acceptsMatchStart(begin)) {
                continue;
            }
            int state = automaton.step(initialState, chars.charAt(begin));
            if (state != UNSET) {
                int index = begin;
                while (true) {
                    if (automaton.isAccept(state)) {
                        // Found a match from begin to (i+1).
                        match_start = begin;
                        match_end = index + 1;
                    }
                    if (++index >= length) {
                        break;
                    }
                    state = automaton.step(state, chars.charAt(index));
                    if (state == UNSET) {
                        break;
                    }
                }
            }
            if (match_start != UNSET) {
                setMatch(match_start, match_end);
                return true;
            }
        }
        if (match_start != UNSET) {
            setMatch(match_start, match_end);
            return true;
        } else {
            setMatch(NO_MORE_MATCH, NO_MORE_MATCH);
            return false;
        }
    }

    /**
     * Indicates whether the provided candidate match start is accepted.
     * <p/>
     * This method always returns {@link true}, but it can be overridden
     * for specific match filtering (e.g. look-behind filtering).
     */
    protected boolean acceptsMatchStart(int matchStart) {
        return true;
    }

    /**
     * Returns the offset after the last character matched.
     *
     * @return The offset after the last character matched.
     * @throws IllegalStateException if there has not been a match attempt or
     *                               if the last attempt yielded no results.
     */
    @Override
    public int end() throws IllegalStateException {
        checkMatchIsValid();
        return matchEnd;
    }

    /**
     * Returns the offset after the last character matched of the specified
     * capturing group.
     * <br>
     * Note that because the automaton does not support capturing groups the
     * only valid group is 0 (the entire match).
     *
     * @param group the desired capturing group.
     * @return The offset after the last character matched of the specified
     * capturing group.
     * @throws IllegalStateException     if there has not been a match attempt or
     *                                   if the last attempt yielded no results.
     * @throws IndexOutOfBoundsException if the specified capturing group does
     *                                   not exist in the underlying automaton.
     */
    @Override
    public int end(int group) throws IndexOutOfBoundsException, IllegalStateException {
        checkOnlyGroupZero(group);
        return end();
    }

    /**
     * Returns the subsequence of the input found by the previous match.
     *
     * @return The subsequence of the input found by the previous match.
     * @throws IllegalStateException if there has not been a match attempt or
     *                               if the last attempt yielded no results.
     */
    @Override
    public String group() throws IllegalStateException {
        checkMatchIsValid();
        return chars.subSequence(matchStart, matchEnd).toString();
    }

    /**
     * Returns the subsequence of the input found by the specified capturing
     * group during the previous match operation.
     * <br>
     * Note that because the automaton does not support capturing groups the
     * only valid group is 0 (the entire match).
     *
     * @param group the desired capturing group.
     * @return The subsequence of the input found by the specified capturing
     * group during the previous match operation the previous match. Or
     * {@code null} if the given group did match.
     * @throws IllegalStateException     if there has not been a match attempt or
     *                                   if the last attempt yielded no results.
     * @throws IndexOutOfBoundsException if the specified capturing group does
     *                                   not exist in the underlying automaton.
     */
    @Override
    public String group(int group) throws IndexOutOfBoundsException, IllegalStateException {
        checkOnlyGroupZero(group);
        return group();
    }

    /**
     * Returns the number of capturing groups in the underlying automaton.
     * <br>
     * Note that because the automaton does not support capturing groups this
     * method will always return 0.
     *
     * @return The number of capturing groups in the underlying automaton.
     */
    @Override
    public int groupCount() {
        return 0;
    }

    /**
     * Returns the offset of the first character matched.
     *
     * @return The offset of the first character matched.
     * @throws IllegalStateException if there has not been a match attempt or
     *                               if the last attempt yielded no results.
     */
    @Override
    public int start() throws IllegalStateException {
        checkMatchIsValid();
        return matchStart;
    }

    /**
     * Returns the offset of the first character matched of the specified
     * capturing group.
     * <br>
     * Note that because the automaton does not support capturing groups the
     * only valid group is 0 (the entire match).
     *
     * @param group the desired capturing group.
     * @return The offset of the first character matched of the specified
     * capturing group.
     * @throws IllegalStateException     if there has not been a match attempt or
     *                                   if the last attempt yielded no results.
     * @throws IndexOutOfBoundsException if the specified capturing group does
     *                                   not exist in the underlying automaton.
     */
    @Override
    public int start(int group) throws IndexOutOfBoundsException, IllegalStateException {
        checkOnlyGroupZero(group);
        return start();
    }

    /**
     * Returns the current state of this {@code AutomatonMatcher} as a
     * {@code MatchResult}.
     * <p/>
     * The result is unaffected by subsequent operations on this object.
     */
    public MatchResult toMatchResult() {
        AutomatonMatcher match = new AutomatonMatcher(chars, automaton);
        match.matchStart = this.matchStart;
        match.matchEnd = this.matchEnd;
        return match;
    }

    private void setMatch(int matchStart, int matchEnd) {
        assert matchStart <= matchEnd : "Start (" + matchStart + ") must be less than or equal to end (" + matchEnd + ")";
        this.matchStart = matchStart;
        this.matchEnd = matchEnd;
    }

    /**
     * Helper method that requires the group argument to be 0.
     */
    private static void checkOnlyGroupZero(int group) throws IndexOutOfBoundsException {
        if (group != 0) {
            throw new IndexOutOfBoundsException("The only group supported is 0.");
        }
    }

    /**
     * Helper method to check that the last match attempt was valid.
     */
    private void checkMatchIsValid() throws IllegalStateException {
        if (matchStart < 0) {
            throw new IllegalStateException("There was no available match.");
        }
    }
}