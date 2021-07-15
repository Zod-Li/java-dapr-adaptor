package me.lengyan.dapr.core.util;

import java.util.Random;

/**
 * @author lengyan 2018-12-11
 */
public class StringGenerator {
	public static int MAX_RETRYS = 200;

	public static String LOWERCASE_CHARS = "abcdefghijklmnopqrstuvwxyz";
	public static String UPPERCASE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	public static String NUMBERS = "0123456789";
	public static String SPECIALS = ".:+=^!/*?&<>()[]{}@%#";

	public static String BASIC = LOWERCASE_CHARS + NUMBERS;
	public static String WITH_UPPERCASE = BASIC + UPPERCASE_CHARS;
	public static String WITH_SPECIALS = WITH_UPPERCASE + SPECIALS;

	public static Random RANDOM = new Random(System.currentTimeMillis());

	public static String generate(int length) {
		return generate(BASIC, length, true, true, false, false);
	}

	public static String generateWithUppercase(int length) {
		return generate(WITH_UPPERCASE, length, true, true, true, false);
	}

	public static String generateWithSpecial(int length) {
		return generate(WITH_SPECIALS, length, true, true, true, true);
	}


	private static String generate(String chars, int length, boolean lower, boolean numbers, boolean upper, boolean specials) {
		for (int k = 0; k < MAX_RETRYS; k++) {
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < length; i++) {
				str.append(chars.charAt(RANDOM.nextInt(chars.length())));
			}
			String s = str.toString();
			if (test(s, lower, numbers, upper, specials)) {
				return s;
			}
		}
		throw new RuntimeException("Over max generator retrys");
	}

	private static boolean test(String s, boolean lower, boolean numbers, boolean upper, boolean specials) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (lower && LOWERCASE_CHARS.indexOf(c) >= 0) {
				lower = false;
			}
			if (numbers && NUMBERS.indexOf(c) >= 0) {
				numbers = false;
			}
			if (upper && UPPERCASE_CHARS.indexOf(c) >= 0) {
				upper = false;
			}
			if (specials && SPECIALS.indexOf(c) >= 0) {
				specials = false;
			}
		}
		return !(lower || numbers || upper || specials);
	}


	public static void main(String[] args) {
		for (int i = 0; i < 100; i++) {
			System.out.println(generateWithUppercase(8));
			//System.out.println(generateWithSpecial(20));
		}
	}
}
