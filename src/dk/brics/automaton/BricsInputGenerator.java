package dk.brics.automaton;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import dk.brics.automaton.RegExp;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.SpecialOperations;

import java.util.Set;
import java.util.HashSet;

import com.google.gson.Gson;

public class BricsInputGenerator {
	public static void main(String[] args) {
		if (args.length != 3) {
			System.err.println("Generate matching strings up to length L for regex R, controlled by probability P");
			System.err.println("usage: ... R L P");
			System.exit(1);
		}
		String regexPattern = args[0];
		int maxLength = Integer.parseInt(args[1]);
		double prob = Double.parseDouble(args[2]);

		RegExp re = new RegExp(regexPattern);
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
				Set<String> randStrings = SpecialOperations.getRandomStrings(a, i, prob);
				if (randStrings != null) {
					acceptedRandStrings.addAll(randStrings);
				}
			}
		}

		System.out.println(acceptedAllStrings.size() + " all strings");
		for (String s : acceptedAllStrings) {
			System.out.println("ALL STR: " + new Gson().toJson(s));
		}

		System.out.println(acceptedRandStrings.size() + " rand strings");
		for (String s : acceptedRandStrings) {
			System.out.println("RAND STR: " + new Gson().toJson(s));
		}
	}
}
