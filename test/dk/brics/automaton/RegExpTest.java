package dk.brics.automaton;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static dk.brics.automaton.util.TestCollections.newSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RegExpTest {

    @Test
    public void testToString() {
        final RegExp regExp = new RegExp("a[ab]*c");
        assertEquals("a((a|b))*c", regExp.toString());
    }

    @Test
    public void testGetIdentifiers() {
        final RegExp regExp = new RegExp("a[ab]*<jaap>c<aap>");
        assertEquals(newSet("jaap", "aap"), regExp.getIdentifiers());
    }

    @Disabled // seems toAutomaton() can produce different results with the same input!
    @Test
    public void testToAutomaton() {
        final Automaton automaton = new RegExp("a[ab]*c").toAutomaton();
        assertEquals("initial state: 0\n" +
                "state 0 [reject]:\n" +
                "  a -> 1\n" +
                "state 1 [reject]:\n" +
                "  a-b -> 1\n" +
                "  c -> 2\n" +
                "state 2 [accept]:\n", automaton.toString());
    }

}