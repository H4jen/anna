package com.echbot.modules.nickometer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a port of Adam Spiers' lame-o-nickometer script.
 * <a href="http://adamspiers.org/computing/nickometer/nickometer.pl">here</a>
 * @author Chris Pearson (ech AT mrq3.com)
 * @version $Id: Nickometer.java,v 1.1 2003/10/20 13:13:23 chris Exp $
 */
public class Nickometer
{
//    private static final Logger log = Logger.getLogger(Nickometer.class);

    private static final List SPECIAL_PATTERNS = new ArrayList();
    private static final Pattern CONSECUTIVE_NONALPHA = Pattern.compile("([^A-Za-z0-9]{2,})");
    private static final Pattern OUTER_BRACKETS1 = Pattern.compile("([^{}]*)([{])(.*)([}])([^{}]*)");
    private static final Pattern OUTER_BRACKETS2 = Pattern.compile("([^\\[\\]]*)(\\[)(.*)(\\])([^\\[\\]]*)");
    private static final Pattern OUTER_BRACKETS3 = Pattern.compile("([^()]*)(\\()(.*)(\\))([^()]*)");
    private static final Pattern BRACKET_MATCH = Pattern.compile("[\\[\\](){}]");
    private static final Pattern MIDDLE_CAP = Pattern.compile("([^A-Za-z]*[A-Z].*[a-z].*?)[_-]?([A-Z])");
    private static final Pattern FIRST_CAP = Pattern.compile("([^A-Za-z]*)([A-Z])([a-z])");
    private static final Pattern LAME_END = Pattern.compile("[XZ][^a-zA-Z]*$");
    private static final Pattern[] DIGITMATCHERS = new Pattern[10];

    /**
     * Each instance of this class can hold the necessary info for ratings
     * using a pre-defined pattern match string.
     */
    private static final class SpecialPattern
    {
        private Pattern pattern;
        private boolean convertNumbers;
        private int cost;
    }

    /**
     * Compile the pattern matches that can be reused whilst the class is
     * loaded.
     */
    static {
        int[] specialCost = new int[]{100, 300, 400, 500, 1000};
        String[][] specialString =
                {
                    {// 100
                        "xx"
                    },
                    {// 300
                        "n[i1]ght"
                    },
                    {// 400
                        "dark"
                    },
                    {// 500
                        "69", "dea?th", "n[i1]te", "fuck", "sh[i1]t", "coo[l1]",
                        "kew[l1]", "lame", "dood", "dude", "rool[sz]",
                        "rule[sz]", "[l1](oo?|u)[sz]er", "[l1]eet", "e[l1]ite",
                        "[l1]ord", "k[i1]ng"
                    },
                    {// 1000
                        "pron", "warez", "\\[rkx]0", "\\0[rkx]"
                    }
                };
        // compile patterns for all the special strings
        for (int i = 0; i < specialString.length; i++) {
            String[] regexes = specialString[i];
            for (int j = 0; j < regexes.length; j++) {
                boolean convertNumbers = !regexes[j].startsWith("\\");
                String regexString = convertNumbers ? regexes[j] :
                        regexes[j].substring(1);

                // create and store a new compiled regex
                SpecialPattern newPattern = new SpecialPattern();
                newPattern.cost = specialCost[i];
                newPattern.convertNumbers = convertNumbers;
                newPattern.pattern = Pattern.compile(regexString, Pattern.CASE_INSENSITIVE);
                SPECIAL_PATTERNS.add(newPattern);
            }
        }
        // build the digit matchers
        for (int i = 0; i <= 9; i++) {
            DIGITMATCHERS[i] = Pattern.compile(Integer.toString(i));
        }
    }

    /**
     * Analyse the given nickname and rate it where a greater rating is a lamer
     * nickname.
     * @param nick the nickname to be rated
     * @return a score representing how lame the given nickname is
     */
    public static final double nickometer(String nick) {
        //todo: log.info("nickometer(" + nick + ")");
        double score = penaliseSpecials(nick) + penaliseConsecutiveNonAlphas(nick);

        // Now penalise for too many brackets
        Matcher matcher;
        if ((matcher = OUTER_BRACKETS1.matcher(nick)).matches() ||
                (matcher = OUTER_BRACKETS2.matcher(nick)).matches() ||
                (matcher = OUTER_BRACKETS3.matcher(nick)).matches()) {
            nick = matcher.group(1) + matcher.group(3) + matcher.group(5);
            //todo: log.info("Removed " + matcher.group(2) + matcher.group(4) + "outside parentheses; nick now " + nick);
        }
        int matchCount = matchCount(BRACKET_MATCH.matcher(nick));
        if (matchCount > 0) {
            score += slowPow(10, matchCount);
            //todo: log.info(matchCount + " extraneous parentheses");
        }

        score += penaliseDigits(nick);
        score += penaliseLameEnd(nick);

        String beforeRelax = nick;
        // Remove a capital in the middle of the nick if it starts with one too
        if ((matcher = MIDDLE_CAP.matcher(nick)).lookingAt()) {
            nick = matcher.replaceFirst(matcher.group(1) + matcher.group(2).toLowerCase());
        }
        // Lowercase first letter if it's a capital
        if ((matcher = FIRST_CAP.matcher(nick)).lookingAt()) {
            nick = matcher.replaceFirst(matcher.group(1) + matcher.group(2).toLowerCase() + matcher.group(3));
        }

        score += penaliseCaseShifts(beforeRelax, nick);
        score += penaliseNumberShifts(nick);
        score += penaliseCaps(nick);

        // Remove all non-alphanumeric chars
        score += penaliseNonAlphanumeric(nick);

        //todo: log.info("Raw lameness score is " + score);

        // Use an appropriate function to map [0, +inf) to [0, 100).
        // Apparently this one is a bit sucky...
        double percentage = 100 *
                (1 + tanh((score - 400) / 400)) *
                (1 - 1 / (1 + score / 5)) / 2;
        double digits = 2 * (2 - roundUp(Math.log(100 - percentage) / Math.log(10)));


        double multiplier = Math.pow(10.0, digits);
        percentage = Math.round(percentage * multiplier) / multiplier;
        //todo: log.info(percentage + " shown to " + digits + " sig figures");

        return percentage;
    }

    /**
     * Generates a measurement of lameness for the given input string based on
     * whether or not a series of predetermined patterns appear within it.
     * @param nick input string for analysis
     * @return how 'lame' the input string is based on the special match search
     */
    private static final double penaliseSpecials(String nick) {
        double penalty = 0;
        String nickWithoutNums = nick.
                replace('0', 'o').
                replace('2', 'z').
                replace('3', 'e').
                replace('4', 'a').
                replace('5', 's').
                replace('7', 't').
                replace('+', 't').
                replace('8', 'b');
        for (Iterator i = SPECIAL_PATTERNS.iterator(); i.hasNext();) {
            SpecialPattern pattern = (SpecialPattern)i.next();
            Matcher matcher = pattern.pattern.matcher(
                    pattern.convertNumbers ? nickWithoutNums : nick);
            if (matcher.find()) {
                //todo: log.info("matched special case /" + pattern.pattern.pattern() + "/");
                penalty += pattern.cost;
            }
        }
        return penalty;
    }

    /**
     * Gets a reading for lameness of the given input string based on the
     * number of times non-alphanumeric characters appear together.
     * @param nick the input string to be rated
     * @return how 'lame' the input string is based on the number of non-alpha
     * characters appearing together
     */
    private static final double penaliseConsecutiveNonAlphas(String nick) {
        Matcher matcher = CONSECUTIVE_NONALPHA.matcher(nick);
        double penalty = 0;
        while (matcher.find()) {
            int groupCount = matcher.groupCount();
            for (int i = 1; i <= groupCount; i++) {
                int matchLength = matcher.group(i).length();
                //todo: log.info(matchLength + " total consecutive non-alphas");
                penalty += slowPow(10, matchLength);
            }
        }
        return penalty;
    }

    /**
     * Screw about with the given exponent, and return the number to the power
     * of the new exponent.
     * @param num number to be multiplied
     * @param exp exponent to be muffed with before being the exponent
     * @return num raised to the power of a monked exponent
     */
    private static final double slowPow(double num, double exp) {
        return Math.pow(num, slowExponent(exp));
    }

    /**
     * Some screwed up way of calculating exponents or something.
     * See http://adamspiers.org/computing/nickometer/nickometer.pl
     * @param num the number to be screwed about with
     * @return the input number, but muffed around a bit
     */
    private static final double slowExponent(double num) {
        return 1.3 * num * (1 - Math.atan(num / 6) * 2 / Math.PI);
    }

    /**
     * Get a weighted lameness rating of the given input string based on the
     * number of time digits appear.
     * @param nick input string to be analysed
     * @return how 'lame' the input string is based on the digits appearing
     * within it
     */
    private static final double penaliseDigits(String nick) {
        int[] digitPenalties = new int[]{5, 5, 2, 5, 2, 3, 1, 2, 2, 2};
        double score = 0;
        for (int i = 0; i < DIGITMATCHERS.length; i++) {
            Pattern pattern = DIGITMATCHERS[i];
            int digitCount = matchCount(pattern.matcher(nick));
            if (digitCount > 0) {
                //todo: log.info(digitCount + " occurences of " + i);
                score += digitPenalties[i] * digitCount * 30;
            }
        }
        return score;
    }

    /**
     * Count the number of matches registered in the given Matcher.
     * @param matcher ready-prepared Matcher object for counting
     * @return how many times the Pattern matches the Matcher's input string
     */
    private static final int matchCount(Matcher matcher) {
        int matches = 0;
        while (matcher.find()) matches++;
        return matches;
    }

    /**
     * Generate a value for how lame the given input string is based on the end
     * of the string.
     * @param nick input string to be analysed
     * @return a value representing how lame the given input string is based on
     * the end of it
     */
    private static final double penaliseLameEnd(String nick) {
        return LAME_END.matcher(nick).find() ? 50 : 0;
    }

    /**
     * Analyse the given input string and tell how 'lame' a nickname is based on
     * the number of times it switches from upper to lower case and vice versa.
     * @param nick input string to be analysed
     * @param relaxed input string before relaxed caps rules were enforced
     * @return how 'lame' the nickname is based on upper/lower case changes
     */
    private static final double penaliseCaseShifts(String nick, String relaxed) {
        int caseShifts = caseShifts(nick);
        if ((caseShifts <= 1) || (countCaps(relaxed) == 0)) return 0;
        //todo: log.info(caseShifts + " case shifts");
        return slowPow(9, caseShifts);
    }

    /**
     * Count the number of capital letters in the given string.
     * @param s input string to be searched for caps
     * @return how many capital letters appear in <code>s</code>
     */
    private static final int countCaps(String s) {
        int count = 0;
        for (int pos = 0; pos < s.length(); pos++) {
            if (Character.isUpperCase(s.charAt(pos))) count++;
        }
        return count;
    }

    /**
     * Count the number of case shifts - i.e. AaAaA has four.
     * @param nick string in which to count case shifts
     * @return the number of times the case switches from upper to lower case
     * and vice versa
     */
    private static final int caseShifts(String nick) {
        int changes = 0;
        boolean isUpper = false, firstLetter = true;
        for (int pos = 0; pos < nick.length(); pos++) {
            char thisChar = nick.charAt(pos);
            if (Character.isLetter(thisChar)) {
                if (firstLetter) {
                    firstLetter = false;
                    isUpper = Character.isUpperCase(thisChar);
                } else if (isUpper != Character.isUpperCase(thisChar)) {
                    // case has changed
                    changes++;
                    isUpper = !isUpper;
                }
            }
        }
        return changes;
    }

    /**
     * Analyse the given input string and tell how 'lame' a nickname is based on
     * the number of times it switches from letters to digits and vice versa.
     * @param nick input string to be analysed
     * @return how 'lame' the nickname is based on letter/number changes
     */
    private static final double penaliseNumberShifts(String nick) {
        int numberShifts = numberShifts(nick);
        if (numberShifts <= 1) return 0;
        //todo: log.info(numberShifts + " letter/number shifts");
        return slowPow(9, numberShifts);
    }

    /**
     * Count the number of letter to digit shifts - i.e. A1A1A has four.
     * @param nick string in which to count digit shifts
     * @return the number of times the string switches from letters to digits
     * and vice versa
     */
    private static final int numberShifts(String nick) {
        int changes = 0;
        boolean isDigit = false, firstMatch = true;
        for (int pos = 0; pos < nick.length(); pos++) {
            char thisChar = nick.charAt(pos);
            if (Character.isLetterOrDigit(thisChar)) {
                if (firstMatch) {
                    firstMatch = false;
                    isDigit = Character.isDigit(thisChar);
                } else if (isDigit != Character.isDigit(thisChar)) {
                    // case has changed
                    changes++;
                    isDigit = !isDigit;
                }
            }
        }
        return changes;
    }

    /**
     * Generate a rating for how 'lame' the given input string is as a nickname
     * based on how many capital letters appear.
     * @param nick input string to analyse
     * @return a rating for how lame the nickname is based upon the number of
     * capital letters appearing in the string
     */
    private static final double penaliseCaps(String nick) {
        int caps = 0;
        for (int pos = 0; pos < nick.length(); pos++) {
            if (Character.isUpperCase(nick.charAt(pos))) {
                caps++;
            }
        }
        if (caps == 0) return 0;
        //todo: log.info(caps + " extraneous caps");
        return slowPow(7, caps);
    }

    /**
     * Generate a rating for how 'lame' the given input string is as a nickname
     * based on how many non-alphanumeric characters there are.
     * @param nick input string to analyse
     * @return a rating for how lame the nickname is based upon the number of
     * non-alphanumeric characters in it
     */
    private static final double penaliseNonAlphanumeric(String nick) {
        int symbols = extractNonAlphanumeric(nick).length();
        if (symbols == 0) return 0;
        //todo: log.info(symbols + " extraneous symbols");
        return 50 * symbols + slowPow(9, symbols);
    }

    /**
     * Dump all alphanumeric characters in the given input string, and return a
     * new string consisting only the remaining characters.
     * @param nick the input string to be mutilated
     * @return the remainder of the input string once all alphanumeric chars
     * are removed
     */
    private static final String extractNonAlphanumeric(String nick) {
        StringBuffer buf = new StringBuffer();
        for (int pos = 0; pos < nick.length(); pos++) {
            char thisChar = nick.charAt(pos);
            if (!Character.isLetterOrDigit(thisChar)) {
                buf.append(thisChar);
            }
        }
        return buf.toString();
    }

    /**
     * Work out the sigmoid, also known as the logistic function.
     * @param a input parameter
     * @return the sigmoid of <code>a</code>
     */
    private static final double sigmoid(double a) {
        return 1.0 / (1.0 + Math.exp(-a));
    }

    /**
     * Work out the hyperbolic tangent.
     * @param a input parameter
     * @return the hyperbolic tangent of <code>a</code>
     */
    private static final double tanh(double a) {
        return 2.0 * sigmoid(2.0 * a) - 1.0;
    }

    /**
     * Rounds the given number up.
     * @param a number to be rounded
     * @return rounded value, still a double
     */
    private static final double roundUp(double a) {
        return (int)a + (((double)((int)a) == a) ? 0 : 1);
    }
}
