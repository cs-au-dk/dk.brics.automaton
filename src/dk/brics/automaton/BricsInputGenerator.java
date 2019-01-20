package dk.brics.automaton;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import dk.brics.automaton.RegExp;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.SpecialOperations;

import java.util.Set;
import java.util.HashSet;
import java.util.Random;

import com.google.gson.Gson;

public class BricsInputGenerator {
	public static void main(String[] args) {
		if (args.length != 4) {
			System.err.println("Generate matching strings up to length L for regex R, controlled by probability P and seed S");
			System.err.println("usage: ... R L P S");
			System.exit(1);
		}
		String regexPattern = args[0];
		int maxLength = Integer.parseInt(args[1]);
		double prob = Double.parseDouble(args[2]);
		int seed = Integer.parseInt(args[3]);

		Random rnd = new Random();
		if (0 <= seed) {
			System.err.println("Random: Using seed " + seed);
			rnd.setSeed(seed);
		} else {
			System.err.println("Random: No seed provided");
		}

		RegExp re = new RegExp(regexPattern, RegExp.ALL);
		Automaton a = re.toAutomaton();

		boolean getAllStrings = false;
		boolean getRandStrings = true;

		Set<String> acceptedAllStrings = new HashSet<String>();
		Set<String> acceptedRandStrings = new HashSet<String>();
		for (int i = 0; i <= maxLength; i++) {
			if (getAllStrings) {
				Set<String> strings = SpecialOperations.getStrings(a, i);
				if (strings != null) {
					acceptedAllStrings.addAll(strings);
				}
			}
			
			if (getRandStrings) {
				Set<String> randStrings = SpecialOperations.getRandomStrings(a, i, prob, rnd);
				if (randStrings != null) {
					acceptedRandStrings.addAll(randStrings);
				}
			}
		}

		System.out.println(acceptedAllStrings.size() + " all strings");
		for (String s : acceptedAllStrings) {
			System.out.println("FINAL: ALL STR: " + new Gson().toJson(s));
		}

		System.out.println(acceptedRandStrings.size() + " rand strings");
		for (String s : acceptedRandStrings) {
			System.out.println("FINAL: RAND STR: " + new Gson().toJson(s));
		}
	}
}
