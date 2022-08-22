package dk.brics.automaton;

import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import static dk.brics.automaton.BasicAutomata.makeCodePointRange;
import static java.lang.Character.MAX_CODE_POINT;
import static java.lang.Character.MAX_SURROGATE;
import static java.lang.Character.MIN_CODE_POINT;
import static java.lang.Character.MIN_SURROGATE;

public class BasicAutomataTest {

  private static void test_makeCodePointRange( int min, int max ) {
    RunAutomaton dfa = new RunAutomaton( makeCodePointRange(min, max) );

    for( int c = MIN_CODE_POINT; c <= MAX_CODE_POINT; c++ ) {
      String input = new String(new int[]{ c }, 0, 1);
      boolean isSurrogate = MIN_SURROGATE <= c && c <= MAX_SURROGATE;
      boolean shouldMatch = min <= c && c <= max && !isSurrogate;
      boolean doesMatch = dfa.run(input);
      assert doesMatch == shouldMatch;
    }
  }

  @Example
  void test_makeCodePointRange_examples() {
    int[] samples = {
      MIN_CODE_POINT,
      MIN_CODE_POINT + 1,
      MIN_CODE_POINT + 2,
      MIN_CODE_POINT + MIN_SURROGATE >> 1,
      MIN_SURROGATE - 2,
      MIN_SURROGATE - 1,
      MIN_SURROGATE,
      MIN_SURROGATE + 1,
      MIN_SURROGATE + 2,
      MIN_SURROGATE + MAX_SURROGATE >> 1,
      MAX_SURROGATE - 2,
      MAX_SURROGATE - 1,
      MAX_SURROGATE,
      MAX_SURROGATE + 1,
      MAX_SURROGATE + 2,
      MAX_SURROGATE + Character.MAX_VALUE >> 1,
      Character.MAX_VALUE - 2,
      Character.MAX_VALUE - 1,
      Character.MAX_VALUE,
      Character.MAX_VALUE + 1,
      Character.MAX_VALUE + 2,
      Character.MAX_VALUE + MAX_CODE_POINT >> 1,
      MAX_CODE_POINT - 2,
      MAX_CODE_POINT - 1,
      MAX_CODE_POINT
    };
    for( int min: samples )
      for( int max: samples )
        test_makeCodePointRange(min, max);
  }

  @Property
  void test_makeCodePointRange_1( @ForAll char min, @ForAll char max ) {
    test_makeCodePointRange(min, max);
  }

  @Property
  void test_makeCodePointRange_2( @ForAll char min, @ForAll @IntRange(min=MIN_CODE_POINT, max=MAX_CODE_POINT) int max ) {
    test_makeCodePointRange(min, max);
  }

  @Property
  void test_makeCodePointRange_3( @ForAll @IntRange(min=MIN_CODE_POINT, max=MAX_CODE_POINT) int min, @ForAll char max ) {
    test_makeCodePointRange(min, max);
  }

  @Property
  void test_makeCodePointRange_4(
    @ForAll @IntRange(min=MIN_CODE_POINT, max=MAX_CODE_POINT) int min,
    @ForAll @IntRange(min=MIN_CODE_POINT, max=MAX_CODE_POINT) int max
  ) {
    test_makeCodePointRange(min, max);
  }

}
