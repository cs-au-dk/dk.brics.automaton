/*
 * dk.brics.automaton - CharLookBehind
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
 * Accepts a candidate match start if it fulfills a look-behind of a single character before the match
 * {@link FilteringAutomatonMatcher#start()} start}.
 * <p/>
 * It is possible to create a positive look-behind, for example to accept a candidate match start
 * only if it is preceded by a dash or a parenthesis:
 * <pre>
 *   new CharLookBehind("[\\-(]", false);
 * </pre>
 * <p/>
 * It is possible to create a negative look-behind, for example to accept a candidate match start
 * only if it is not preceded by a word character:
 * <pre>
 *   new CharLookBehind("[^a-zA-Z0-9_]", true);
 * </pre>
 *
 * @author Bruno Roustant &lt;<a href="mailto:bruno.roustant@netcourrier.com">bruno.roustant@netcourrier.com</a>&gt;
 * @see FilteringAutomatonMatcher
 */
public class CharLookBehind implements MatchFilter {

    private final CharFilter charFilter;
    private final boolean acceptsBeginningOfInput;

    /**
     * Constructs a single character look-behind.
     *
     * @param charFilter       The {@link CharFilter} which accepts or rejects the character
     *                                preceding the character at candidate match {@link FilteringAutomatonMatcher#start() start}.
     * @param acceptsBeginningOfInput Whether to accept a match starting at the beginning of
     *                                the input (at index {@code 0}).
     */
    public CharLookBehind(CharFilter charFilter, boolean acceptsBeginningOfInput) {
        this.charFilter = charFilter;
        this.acceptsBeginningOfInput = acceptsBeginningOfInput;
    }

    /**
     * Convenient constructor which builds an {@link AutomatonCharFilter} from the provided regex.
     *
     * @see dk.brics.automaton.RegExp
     */
    public CharLookBehind(String acceptedCharsRegex, boolean acceptsEndOfInput) {
        this(new AutomatonCharFilter(acceptedCharsRegex), acceptsEndOfInput);
    }

    @Override
    public boolean accepts(int matchStart, int matchEnd, CharSequence chars) {
        return matchStart == 0 ? acceptsBeginningOfInput : charFilter.accepts(chars.charAt(matchStart - 1));
    }
}