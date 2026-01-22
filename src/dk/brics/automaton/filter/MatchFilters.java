/*
 * dk.brics.automaton - MatchFilters
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

/**
 * Creates composite {@link MatchFilter}s.
 *
 * @author Bruno Roustant &lt;<a href="mailto:bruno.roustant@netcourrier.com">bruno.roustant@netcourrier.com</a>&gt;
 */
public class MatchFilters {

    /**
     * Creates a {@link MatchFilter} which is a logical AND between two delegate {@link MatchFilter}s.
     */
    public static MatchFilter and(MatchFilter matchFilter1, MatchFilter matchFilter2) {
        return new And(matchFilter1, matchFilter2);
    }

    private static class And implements MatchFilter {

        private final MatchFilter matchFilter1;
        private final MatchFilter matchFilter2;

        public And(MatchFilter matchFilter1, MatchFilter matchFilter2) {
            this.matchFilter1 = matchFilter1;
            this.matchFilter2 = matchFilter2;
        }

        @Override
        public boolean accepts(int matchStart, int matchEnd, CharSequence chars) {
            return matchFilter1.accepts(matchStart, matchEnd, chars)
                    && matchFilter2.accepts(matchStart, matchEnd, chars);
        }
    }
}