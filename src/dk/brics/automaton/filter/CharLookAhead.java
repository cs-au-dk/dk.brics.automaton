/*
 * dk.brics.automaton - CharLookAhead
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
 * Accepts a match result if it fulfills a look-ahead of a single character after the last
 * character of the match {@link FilteringAutomatonMatcher#end() end}.
 * <p/>
 * It is possible to create a positive look-ahead, for example to accept a match result
 * only if it is followed by a dash or a parenthesis:
 * <pre>
 *   new CharLookAhead("[\\-)]"), false);
 * </pre>
 * <p/>
 * It is possible to create a negative look-ahead, for example to accept a match result
 * only if it is not followed by a word character:
 * <pre>
 *   new CharLookAhead("[^a-zA-Z0-9_]", true);
 * </pre>
 *
 * @author Bruno Roustant &lt;<a href="mailto:bruno.roustant@netcourrier.com">bruno.roustant@netcourrier.com</a>&gt;
 * @see FilteringAutomatonMatcher
 */
public class CharLookAhead implements MatchFilter {

    private final CharFilter charFilter;
    private final boolean acceptsEndOfInput;

    /**
     * Constructs a single character look-ahead.
     *
     * @param charFilter       The {@link CharFilter} which accepts or rejects the character
     *                          at the match {@link FilteringAutomatonMatcher#end() end}.
     * @param acceptsEndOfInput Whether to accept a match ending at the end of
     *                          the input (at index {@link CharSequence#length()}).
     */
    public CharLookAhead(CharFilter charFilter, boolean acceptsEndOfInput) {
        this.charFilter = charFilter;
        this.acceptsEndOfInput = acceptsEndOfInput;
    }

    /**
     * Convenient constructor which builds an {@link AutomatonCharFilter} from the provided regex.
     *
     * @see dk.brics.automaton.RegExp
     */
    public CharLookAhead(String acceptedCharsRegex, boolean acceptsEndOfInput) {
        this(new AutomatonCharFilter(acceptedCharsRegex), acceptsEndOfInput);
    }

    @Override
    public boolean accepts(int matchStart, int matchEnd, CharSequence chars) {
        return matchEnd == chars.length() ? acceptsEndOfInput : charFilter.accepts(chars.charAt(matchEnd));
    }
}