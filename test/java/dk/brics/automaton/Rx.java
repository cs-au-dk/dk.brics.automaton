package dk.brics.automaton;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regular expression abstract syntax for testing, with a renderer to the
 * {@link RegExp} syntax and a reference matcher that is independent of the library.
 */
final class Rx {

	enum Kind {
		EMPTY, EPS, CHAR, STR, RANGE, CLASS, ANY, ANYSTR, INTERVAL, NAMED,
		UNION, INTER, CONCAT, OPT, STAR, PLUS, REP, COMPL
	}

	/** Characters that are reserved in some part of the syntax. */
	static final String SPECIAL = "|&?*+{},![]^-.#@\"()<>\\~ ";

	private static final AtomicInteger counter = new AtomicInteger();

	/** Unique id, used as memoization key by the matcher. */
	final int id = counter.getAndIncrement();

	final Kind kind;

	// The following fields are used depending on the kind:
	//   CHAR: ch
	//   STR: str
	//   RANGE: lo, hi
	//   CLASS: items, negated
	//   INTERVAL: minText, maxText (end points as written), min, max (their values, ordered)
	//   NAMED: name
	//   UNION, INTER, CONCAT: left, right
	//   OPT, STAR, PLUS, COMPL: left
	//   REP: left, min, max (max -1: unbounded)

	Rx left, right;
	char ch, lo, hi;
	String str;
	List<char[]> items; // {min, max} for each item
	boolean negated;
	int min, max;
	String minText, maxText;
	String name;

	private Rx(Kind kind) {
		this.kind = kind;
	}

	static Rx leaf(Kind kind) {
		return new Rx(kind);
	}

	static Rx ch(char ch) {
		Rx x = new Rx(Kind.CHAR);
		x.ch = ch;
		return x;
	}

	static Rx str(String str) {
		Rx x = new Rx(Kind.STR);
		x.str = str;
		return x;
	}

	static Rx range(char lo, char hi) {
		Rx x = new Rx(Kind.RANGE);
		x.lo = lo;
		x.hi = hi;
		return x;
	}

	static Rx bin(Kind kind, Rx left, Rx right) {
		Rx x = new Rx(kind);
		x.left = left;
		x.right = right;
		return x;
	}

	static Rx un(Kind kind, Rx operand) {
		Rx x = new Rx(kind);
		x.left = operand;
		return x;
	}

	static Rx rep(Rx operand, int min, int max) {
		Rx x = new Rx(Kind.REP);
		x.left = operand;
		x.min = min;
		x.max = max;
		return x;
	}

	static Rx interval(String minText, String maxText) {
		Rx x = new Rx(Kind.INTERVAL);
		x.minText = minText;
		x.maxText = maxText;
		int i1 = Integer.parseInt(minText), i2 = Integer.parseInt(maxText);
		x.min = Math.min(i1, i2);
		x.max = Math.max(i1, i2);
		return x;
	}

	static Rx named(String name) {
		Rx x = new Rx(Kind.NAMED);
		x.name = name;
		return x;
	}

	// ---------------------------------------------------------------------------------------
	// Random generation
	// ---------------------------------------------------------------------------------------

	/** A choice in the random generation, with a relative weight. */
	private interface Weighted {
		int weight();

		/** Returns true if the choice is only used with the extended syntax. */
		boolean extended();
	}

	/** Choices of leaves. */
	private enum Leaf implements Weighted {
		EPS(1, false), STR(1, false), RANGE(1, false), CLASS(1, false), CHAR(4, false),
		ANY(1, true), EMPTY(1, true), ANYSTR(1, true), WIDE_RANGE(1, true), INTERVAL(1, true), NAMED(1, true);

		private final int weight;
		private final boolean extended;

		Leaf(int weight, boolean extended) {
			this.weight = weight;
			this.extended = extended;
		}

		@Override
		public int weight() {
			return weight;
		}

		@Override
		public boolean extended() {
			return extended;
		}
	}

	/** Choices of operators. */
	private enum Op implements Weighted {
		UNION(2, false), CONCAT(2, false), OPT(1, false), STAR_OR_PLUS(1, false), REP(2, false),
		INTER(2, true), COMPL(1, true);

		private final int weight;
		private final boolean extended;

		Op(int weight, boolean extended) {
			this.weight = weight;
			this.extended = extended;
		}

		@Override
		public int weight() {
			return weight;
		}

		@Override
		public boolean extended() {
			return extended;
		}
	}

	/** Picks one of the choices with probability proportional to its weight. */
	private static <T extends Weighted> T choose(Random r, T[] choices, boolean extended) {
		int total = 0;
		for (T c : choices)
			if (extended || !c.extended())
				total += c.weight();
		int p = r.nextInt(total);
		for (T c : choices)
			if (extended || !c.extended()) {
				p -= c.weight();
				if (p < 0)
					return c;
			}
		throw new AssertionError();
	}

	/** Random char: mostly a, b, or c, and occasionally a reserved char or a boundary code point. */
	static char randomChar(Random r) {
		int p = r.nextInt(12);
		if (p == 0)
			return SPECIAL.charAt(r.nextInt(SPECIAL.length()));
		if (p == 1)
			return Lang.POINTS[r.nextInt(Lang.POINTS.length)];
		return "abc".charAt(r.nextInt(3));
	}

	/**
	 * Random regular expression. If <code>extended</code>, the optional syntax (intersection,
	 * complement, empty language, any string, intervals) and wide character ranges are used.
	 * Named automata are used if <code>names</code> is non-null.
	 */
	static Rx random(Random r, int depth, boolean extended, List<String> names) {
		if (depth <= 0 || r.nextInt(5) == 0)
			return randomLeaf(r, extended, names);
		switch (choose(r, Op.values(), extended)) {
		case UNION:
			return bin(Kind.UNION, random(r, depth - 1, extended, names), random(r, depth - 1, extended, names));
		case CONCAT:
			return bin(Kind.CONCAT, random(r, depth - 1, extended, names), random(r, depth - 1, extended, names));
		case OPT:
			return un(Kind.OPT, random(r, depth - 1, extended, names));
		case STAR_OR_PLUS:
			return un(r.nextBoolean() ? Kind.STAR : Kind.PLUS, random(r, depth - 1, extended, names));
		case REP: {
			Rx a = random(r, depth - 1, extended, names);
			if (a.kind == Kind.REP)
				return a; // nested repetitions, such as .{2,4}{2,4}, can make the subset construction blow up
			int min = r.nextInt(3);
			int max;
			switch (r.nextInt(3)) {
			case 0: // fixed number
				max = min;
				break;
			case 1: // unbounded
				max = -1;
				break;
			default:
				max = min + r.nextInt(3);
			}
			return rep(a, min, max);
		}
		case INTER:
			return bin(Kind.INTER, random(r, depth - 1, extended, names), random(r, depth - 1, extended, names));
		case COMPL:
			return un(Kind.COMPL, random(r, depth - 1, extended, names));
		}
		throw new AssertionError();
	}

	private static Rx randomLeaf(Random r, boolean extended, List<String> names) {
		switch (choose(r, Leaf.values(), extended)) {
		case EPS:
			return leaf(Kind.EPS);
		case STR: {
			StringBuilder b = new StringBuilder();
			int n = r.nextInt(4);
			for (int i = 0; i < n; i++) {
				char c = randomChar(r);
				if (c != '"')
					b.append(c);
			}
			return str(b.toString());
		}
		case RANGE: {
			char x = "abc".charAt(r.nextInt(3)), y = "abc".charAt(r.nextInt(3));
			return range((char) Math.min(x, y), (char) Math.max(x, y));
		}
		case CLASS: {
			Rx x = new Rx(Kind.CLASS);
			x.items = new ArrayList<>();
			int n = 1 + r.nextInt(3);
			for (int i = 0; i < n; i++) {
				char p = randomChar(r), q = r.nextInt(3) == 0 ? randomChar(r) : p;
				x.items.add(new char[] {(char) Math.min(p, q), (char) Math.max(p, q)});
			}
			x.negated = r.nextInt(3) == 0;
			return x;
		}
		case CHAR:
			return ch(randomChar(r));
		case ANY:
			return leaf(Kind.ANY);
		case EMPTY:
			return leaf(Kind.EMPTY);
		case ANYSTR:
			return leaf(Kind.ANYSTR);
		case WIDE_RANGE: {
			char x = Lang.POINTS[r.nextInt(Lang.POINTS.length)], y = Lang.POINTS[r.nextInt(Lang.POINTS.length)];
			return range((char) Math.min(x, y), (char) Math.max(x, y));
		}
		case INTERVAL: {
			// mostly narrow intervals, sometimes wide ones with end points of different lengths
			int i1 = r.nextInt(30), i2 = i1 + r.nextInt(r.nextBoolean() ? 15 : 150);
			String s1 = Integer.toString(i1), s2 = Integer.toString(i2);
			if (r.nextBoolean()) { // fixed number of digits
				int d = Math.max(s1.length(), s2.length()) + r.nextInt(2);
				s1 = Fuzz.pad(s1, d);
				s2 = Fuzz.pad(s2, d);
			}
			return r.nextInt(4) == 0 ? interval(s2, s1) : interval(s1, s2);
		}
		case NAMED:
			if (names != null)
				return named(names.get(r.nextInt(names.size())));
			return leaf(Kind.ANY);
		}
		throw new AssertionError();
	}

	// ---------------------------------------------------------------------------------------
	// Rendering in the RegExp syntax
	// ---------------------------------------------------------------------------------------

	/** Precedence: 1 union, 2 intersection, 3 concatenation, 4 repetition, 5 complement, 6 atom. */
	int prec() {
		switch (kind) {
		case UNION:
			return 1;
		case INTER:
			return 2;
		case CONCAT:
			return 3;
		case OPT:
		case STAR:
		case PLUS:
		case REP:
			return 4;
		case COMPL:
			return 5;
		default:
			return 6;
		}
	}

	String render(Random r) {
		StringBuilder b = new StringBuilder();
		render(r, b);
		return b.toString();
	}

	private void renderSub(Rx x, int minprec, Random r, StringBuilder b) {
		if (x.prec() < minprec || r.nextInt(8) == 0) {
			b.append('(');
			x.render(r, b);
			b.append(')');
		} else
			x.render(r, b);
	}

	private void render(Random r, StringBuilder b) {
		switch (kind) {
		case EMPTY:
			b.append('#');
			break;
		case EPS:
			b.append(r.nextBoolean() ? "()" : "\"\"");
			break;
		case CHAR:
			appendChar(ch, r, b);
			break;
		case STR:
			if (r.nextBoolean())
				b.append('"').append(str).append('"');
			else if (str.isEmpty())
				b.append("()");
			else {
				// a sequence of chars is not atomic, unlike a quoted string
				if (str.length() > 1)
					b.append('(');
				for (char x : str.toCharArray())
					appendChar(x, r, b);
				if (str.length() > 1)
					b.append(')');
			}
			break;
		case RANGE:
			b.append('[');
			appendClassChar(lo, r, b);
			b.append('-');
			appendClassChar(hi, r, b);
			b.append(']');
			break;
		case CLASS:
			b.append('[');
			if (negated)
				b.append('^');
			for (char[] item : items) {
				appendClassChar(item[0], r, b);
				if (item[0] != item[1]) {
					b.append('-');
					appendClassChar(item[1], r, b);
				}
			}
			b.append(']');
			break;
		case ANY:
			b.append('.');
			break;
		case ANYSTR:
			b.append('@');
			break;
		case INTERVAL:
			b.append('<').append(minText).append('-').append(maxText).append('>');
			break;
		case NAMED:
			b.append('<').append(name).append('>');
			break;
		case UNION:
			// empty branches are allowed for the empty string
			if (left.kind == Kind.EPS && r.nextBoolean()) {
				// nothing
			} else
				renderSub(left, 2, r, b);
			b.append('|');
			if (right.kind == Kind.EPS && r.nextBoolean()) {
				// nothing
			} else
				renderSub(right, 1, r, b);
			break;
		case INTER:
			renderSub(left, 3, r, b);
			b.append('&');
			renderSub(right, 2, r, b);
			break;
		case CONCAT:
			renderSub(left, 4, r, b);
			renderSub(right, 3, r, b);
			break;
		case OPT:
			renderSub(left, 4, r, b);
			b.append('?');
			break;
		case STAR:
			renderSub(left, 4, r, b);
			b.append('*');
			break;
		case PLUS:
			renderSub(left, 4, r, b);
			b.append('+');
			break;
		case REP:
			renderSub(left, 4, r, b);
			b.append('{').append(min);
			if (max < 0)
				b.append(",}");
			else if (max == min && r.nextBoolean())
				b.append('}');
			else
				b.append(',').append(max).append('}');
			break;
		case COMPL:
			b.append('~');
			renderSub(left, 5, r, b);
			break;
		}
	}

	private static void appendChar(char c, Random r, StringBuilder b) {
		if (Character.isLetterOrDigit(c) && c < 0x80 && r.nextBoolean())
			b.append(c);
		else
			b.append('\\').append(c);
	}

	private static void appendClassChar(char c, Random r, StringBuilder b) {
		if ("]\\-^".indexOf(c) == -1 && r.nextBoolean())
			b.append(c);
		else
			b.append('\\').append(c);
	}

	// ---------------------------------------------------------------------------------------
	// Reference matcher
	// ---------------------------------------------------------------------------------------

	boolean matches(String input, Map<String, Lang> named) {
		return new Matcher(input, named).match(this, 0, input.length());
	}

	private static final class Matcher {
		final String input;
		final Map<String, Lang> named;
		final Map<Long, Boolean> memo = new HashMap<>(); // keys: id, start, and end
		final Map<List<Integer>, Boolean> repmemo = new HashMap<>();

		Matcher(String input, Map<String, Lang> named) {
			this.input = input;
			this.named = named;
		}

		/** Checks whether the substring from i to j matches x. */
		boolean match(Rx x, int i, int j) {
			long n = input.length() + 1;
			long key = (x.id * n + i) * n + j;
			Boolean v = memo.get(key);
			if (v == null) {
				v = compute(x, i, j);
				memo.put(key, v);
			}
			return v;
		}

		private boolean compute(Rx x, int i, int j) {
			switch (x.kind) {
			case EMPTY:
				return false;
			case EPS:
				return i == j;
			case CHAR:
				return j == i + 1 && input.charAt(i) == x.ch;
			case STR:
				return input.substring(i, j).equals(x.str);
			case RANGE:
				return j == i + 1 && x.lo <= input.charAt(i) && input.charAt(i) <= x.hi;
			case CLASS: {
				if (j != i + 1)
					return false;
				char c = input.charAt(i);
				boolean in = false;
				for (char[] item : x.items)
					if (item[0] <= c && c <= item[1])
						in = true;
				return in != x.negated;
			}
			case ANY:
				return j == i + 1;
			case ANYSTR:
				return true;
			case INTERVAL: {
				String s = input.substring(i, j);
				if (s.isEmpty() || !s.chars().allMatch(ch -> ch >= '0' && ch <= '9'))
					return false;
				if (x.minText.length() == x.maxText.length() && s.length() != x.minText.length())
					return false;
				BigInteger v = new BigInteger(s);
				return v.compareTo(BigInteger.valueOf(x.min)) >= 0 && v.compareTo(BigInteger.valueOf(x.max)) <= 0;
			}
			case NAMED:
				return named.get(x.name).contains(input.substring(i, j));
			case UNION:
				return match(x.left, i, j) || match(x.right, i, j);
			case INTER:
				return match(x.left, i, j) && match(x.right, i, j);
			case COMPL:
				return !match(x.left, i, j);
			case CONCAT:
				for (int p = i; p <= j; p++)
					if (match(x.left, i, p) && match(x.right, p, j))
						return true;
				return false;
			case OPT:
				return i == j || match(x.left, i, j);
			case STAR:
				return rep(x.left, 0, -1, i, j);
			case PLUS:
				return rep(x.left, 1, -1, i, j);
			case REP:
				return rep(x.left, x.min, x.max, i, j);
			}
			throw new AssertionError();
		}

		/** Checks whether the substring from i to j matches between <code>need</code> and <code>remaining</code> (-1: unbounded) repetitions of x. */
		private boolean rep(Rx x, int need, int remaining, int i, int j) {
			if (i == j)
				return need == 0 || match(x, i, i);
			if (remaining == 0)
				return false;
			List<Integer> key = Arrays.asList(x.id, need, remaining, i, j);
			Boolean v = repmemo.get(key);
			if (v == null) {
				v = false;
				for (int p = i + 1; p <= j && !v; p++)
					if (match(x, i, p) && rep(x, Math.max(need - 1, 0), remaining < 0 ? -1 : remaining - 1, p, j))
						v = true;
				repmemo.put(key, v);
			}
			return v;
		}
	}

	// ---------------------------------------------------------------------------------------
	// Sampling
	// ---------------------------------------------------------------------------------------

	/** Returns a string that is likely in the language, or null. */
	String sample(Random r, Map<String, Lang> named) {
		switch (kind) {
		case EMPTY:
			return null;
		case EPS:
			return "";
		case CHAR:
			return "" + ch;
		case STR:
			return str;
		case RANGE:
			return "" + Fuzz.pick(lo, hi, r);
		case CLASS: {
			if (!negated) {
				char[] item = items.get(r.nextInt(items.size()));
				return "" + Fuzz.pick(item[0], item[1], r);
			}
			return "" + Lang.POINTS[r.nextInt(Lang.POINTS.length)];
		}
		case ANY:
			return "" + randomChar(r);
		case ANYSTR:
		case COMPL:
			return Fuzz.randomString(r, "abc" + SPECIAL, 3);
		case INTERVAL: {
			String v = Integer.toString(min + r.nextInt(max - min + 1));
			if (minText.length() == maxText.length())
				return Fuzz.pad(v, minText.length());
			return Fuzz.pad(v, v.length() + r.nextInt(2));
		}
		case NAMED: {
			List<String> ms = named.get(name).members(r, 1);
			return ms.isEmpty() ? null : ms.get(0);
		}
		case UNION: {
			String x = (r.nextBoolean() ? left : right).sample(r, named);
			return x != null ? x : left.sample(r, named);
		}
		case INTER:
			return left.sample(r, named);
		case CONCAT: {
			String x = left.sample(r, named), y = right.sample(r, named);
			return x == null || y == null ? null : x + y;
		}
		case OPT:
			return r.nextBoolean() ? "" : left.sample(r, named);
		case STAR:
			return repeatSample(r, named, 0, 3);
		case PLUS:
			return repeatSample(r, named, 1, 3);
		case REP:
			return repeatSample(r, named, min, max < 0 ? min + 2 : max);
		}
		throw new AssertionError();
	}

	private String repeatSample(Random r, Map<String, Lang> named, int lo, int hi) {
		int n = lo + r.nextInt(hi - lo + 1);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			String x = left.sample(r, named);
			if (x == null)
				return null;
			sb.append(x);
		}
		return sb.toString();
	}

	/** Collects the characters that are relevant for the language. */
	void chars(Set<Character> cs) {
		switch (kind) {
		case CHAR:
			cs.add(ch);
			break;
		case STR:
			for (char x : str.toCharArray())
				cs.add(x);
			break;
		case RANGE:
			Lang.addRangeChars(cs, lo, hi);
			break;
		case CLASS:
			for (char[] item : items)
				Lang.addRangeChars(cs, item[0], item[1]);
			break;
		case INTERVAL:
			for (char d = '0'; d <= '9'; d++)
				cs.add(d);
			break;
		default:
			if (left != null)
				left.chars(cs);
			if (right != null)
				right.chars(cs);
		}
	}

	/** Structural description, independent of the rendering. */
	String dump() {
		switch (kind) {
		case EMPTY:
		case EPS:
		case ANY:
		case ANYSTR:
			return kind.toString();
		case CHAR:
			return "CHAR(" + Fuzz.show("" + ch) + ")";
		case STR:
			return "STR(" + Fuzz.show(str) + ")";
		case RANGE:
			return "RANGE(" + Fuzz.show("" + lo) + "," + Fuzz.show("" + hi) + ")";
		case CLASS: {
			StringBuilder b = new StringBuilder(negated ? "NCLASS(" : "CLASS(");
			for (char[] item : items)
				b.append(Fuzz.show("" + item[0])).append("..").append(Fuzz.show("" + item[1])).append(' ');
			return b.append(')').toString();
		}
		case INTERVAL:
			return "INTERVAL(" + minText + "," + maxText + ")";
		case NAMED:
			return "NAMED(" + name + ")";
		case REP:
			return "REP(" + left.dump() + "," + min + "," + max + ")";
		default:
			return kind + "(" + left.dump() + (right != null ? "," + right.dump() : "") + ")";
		}
	}

	@Override
	public String toString() {
		return dump();
	}
}
