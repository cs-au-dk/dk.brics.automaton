/*
 * dk.brics.automaton - FilteringAutomatonMatcher
 *
 * Copyright (c) 2017 Bruno Roustant
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

package dk.brics.automaton.filter;

import dk.brics.automaton.AutomatonMatcher;
import dk.brics.automaton.RunAutomaton;

/**
 * {@link AutomatonMatcher} which filters match results with a {@link #setPreFilter(MatchFilter) pre-filter},
  * or a {@link #setPostFilter(MatchFilter) post-filter}, or both.
 * <p/>
 * It is possible to filter match results with positive/negative
 * {@link CharLookAhead look-ahead}/{@link CharLookBehind look-behind}.
 *
 * @author Bruno Roustant &lt;<a href="mailto:bruno.roustant@netcourrier.com">bruno.roustant@netcourrier.com</a>&gt;
 */
public class FilteringAutomatonMatcher extends AutomatonMatcher {

    private MatchFilter preFilter;
    private MatchFilter postFilter;

    /**
     * Creates a {@link FilteringAutomatonMatcher} based on the provided {@link RunAutomaton},
     * with default {@link MatchFilter#ACCEPT_ALL} pre-filter and post-filter.
     */
    public FilteringAutomatonMatcher(CharSequence chars, RunAutomaton automaton) {
        super(chars, automaton);
        preFilter = MatchFilter.ACCEPT_ALL;
        postFilter = MatchFilter.ACCEPT_ALL;
    }

    /**
     * Sets the match pre-filter.
     * <p/>
     * This pre-filter will be used as a pre-validation of each candidate match start
     * in the {@link #find()} loop. If the pre-filter rejects the match start, the {@link #find()}
     * loop tries the next character in sequence. If the pre-filter accepts the match start,
     * the automaton provided in the constructor is run from the candidate match start.
     * <p/>
     * {@code matchEnd} parameter will be {@code -1} for all calls to the pre-filter
     * {@link MatchFilter#accepts(int, int, CharSequence)} method.
     */
    public FilteringAutomatonMatcher setPreFilter(MatchFilter preFilter) {
        this.preFilter = preFilter;
        return this;
    }

    /**
     * Sets the match post-filter.
     * <p/>
     * This post-filter will be used as a post-validation of each match found by
     * the {@link #find()} loop. If the post-filter rejects the match, the {@link #find()}
     * loop tries to match at the next character in sequence. If the post-filter accepts the
     * match, {@link #find()} returns {@code true}.
     */
    public FilteringAutomatonMatcher setPostFilter(MatchFilter postFilter) {
        this.postFilter = postFilter;
        return this;
    }

    /**
     * Finds the next matching subsequence of the input which is accepted by both the
     * {@link #setPreFilter(MatchFilter) pre-filter} and the {@link #setPostFilter(MatchFilter) post-filter}.
     * <p/>
     * Also updates the values for the {@link #start()}, {@link #end()}, and {@link #group()} methods.
     *
     * @return {@code true} if and only if there is a matching subsequence accepted by both the
     * pre-filter and post-filter.
     */
    @Override
    public boolean find() {
        while (super.find()) {
            int matchStart = start();
            if (postFilter.accepts(matchStart, end(), getChars())) {
                return true;
            }
            setSearchStart(matchStart + 1);
        }
        return false;
    }

    @Override
    protected boolean acceptsMatchStart(int matchStart) {
        return preFilter.accepts(matchStart, -1, getChars());
    }
}