package org.unicode.cldr.unittest;

import com.google.common.collect.ImmutableList;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.impl.Row.R5;
import com.ibm.icu.text.UnicodeSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import org.unicode.cldr.icu.dev.test.TestFmwk;
import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.test.CheckCLDR.CheckStatus;
import org.unicode.cldr.test.CheckCLDR.CheckStatus.Subtype;
import org.unicode.cldr.test.CheckCLDR.InputMethod;
import org.unicode.cldr.test.CheckCLDR.Options;
import org.unicode.cldr.test.CheckCLDR.Phase;
import org.unicode.cldr.test.CheckCLDR.StatusAction;
import org.unicode.cldr.test.CheckConsistentCasing;
import org.unicode.cldr.test.CheckDates;
import org.unicode.cldr.test.CheckForExemplars;
import org.unicode.cldr.test.CheckNames;
import org.unicode.cldr.test.CheckNew;
import org.unicode.cldr.test.OutdatedPaths;
import org.unicode.cldr.test.SubmissionLocales;
import org.unicode.cldr.test.TestCache;
import org.unicode.cldr.test.TestCache.TestResultBundle;
import org.unicode.cldr.tool.LikelySubtags;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRInfo.CandidateInfo;
import org.unicode.cldr.util.CLDRInfo.PathValueInfo;
import org.unicode.cldr.util.CLDRInfo.UserInfo;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.DayPeriodInfo;
import org.unicode.cldr.util.DayPeriodInfo.DayPeriod;
import org.unicode.cldr.util.DayPeriodInfo.Type;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.GrammarInfo;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.Organization;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.PathHeader;
import org.unicode.cldr.util.PathHeader.SurveyToolStatus;
import org.unicode.cldr.util.PatternPlaceholders;
import org.unicode.cldr.util.PatternPlaceholders.PlaceholderInfo;
import org.unicode.cldr.util.PatternPlaceholders.PlaceholderStatus;
import org.unicode.cldr.util.SimpleXMLSource;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.StandardCodes.LstrType;
import org.unicode.cldr.util.StringId;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.Validity;
import org.unicode.cldr.util.Validity.Status;
import org.unicode.cldr.util.VoteResolver.VoterInfo;
import org.unicode.cldr.util.XMLSource;

public class TestCheckCLDR extends TestFmwk {

    private static final boolean SHOW_LIMITED =
            System.getProperty("TestCheckCLDR:SHOW_LIMITED") != null;

    static CLDRConfig testInfo = CLDRConfig.getInstance();
    private final Set<String> eightPointLocales =
            new TreeSet<>(
                    Arrays.asList(
                            "ar ca cs da de el es fi fr he hi hr hu id it ja ko lt lv nl no pl pt pt_PT ro ru sk sl sr sv th tr uk vi zh zh_Hant"
                                    .split(" ")));

    public static void main(String[] args) {
        new TestCheckCLDR().run(args);
    }

    static class MyCheckCldr extends org.unicode.cldr.test.CheckCLDR {
        CheckStatus doTest() {
            try {
                throw new IllegalArgumentException("hi");
            } catch (Exception e) {
                return new CheckStatus()
                        .setCause(this)
                        .setMainType(CheckStatus.warningType)
                        .setSubtype(Subtype.abbreviatedDateFieldTooWide)
                        .setMessage("An exception {0}, and a number {1}", e, 1.5);
            }
        }

        @Override
        public CheckCLDR handleCheck(
                String path,
                String fullPath,
                String value,
                Options options,
                List<CheckStatus> result) {
            return null;
        }
    }

    public void TestExceptions() {
        CheckStatus status = new MyCheckCldr().doTest();
        Exception[] exceptions = status.getExceptionParameters();
        assertEquals("Number of exceptions:", exceptions.length, 1);
        assertEquals("Exception message:", "hi", exceptions[0].getMessage());
        logln(Arrays.asList(exceptions[0].getStackTrace()).toString());
        logln(status.getMessage());
    }

    public static void TestCheckConsistentCasing() {
        CheckConsistentCasing c = new CheckConsistentCasing(testInfo.getCldrFactory());
        Map<String, String> options = new LinkedHashMap<>();
        List<CheckStatus> possibleErrors = new ArrayList<>();
        final CLDRFile english = testInfo.getEnglish();
        c.setCldrFileToCheck(english, new CheckCLDR.Options(options), possibleErrors);
        for (String path : english) {
            c.check(
                    path,
                    english.getFullXPath(path),
                    english.getStringValue(path),
                    new CheckCLDR.Options(options),
                    possibleErrors);
        }
    }

    /** Test the TestCache and TestResultBundle objects */
    public void TestTestCache() {
        String localeString = "en";
        CLDRLocale locale = CLDRLocale.getInstance(localeString);
        CheckCLDR.Options checkCldrOptions =
                new Options(locale, Phase.SUBMISSION, "default", "basic");
        TestCache testCache = testInfo.getCldrFactory().getTestCache();
        testCache.setNameMatcher(".*"); // will clear the cache
        TestResultBundle bundle = testCache.getBundle(checkCldrOptions);
        final CLDRFile cldrFile = testInfo.getCLDRFile(localeString, true);
        /*
         * Loop through the set of paths twice. The second time should be much faster.
         * Measured times for the two passes, without pathCache in TestResultBundle,
         * 4017 and 3293 milliseconds. With pathCache, 4125 and 46 milliseconds.
         * That's with locale "en", all 19698 paths. Results for "fr" were similar.
         * To save time, limit the number of paths if getInclusion() is small.
         * A thousand paths take about half a second to loop through twice.
         */
        int maxPathCount = (getInclusion() < 5) ? 1000 : 100000;
        double[] deltaTime = {0, 0};
        for (int i = 0; i < 2; i++) {
            List<CheckStatus> possibleErrors = new ArrayList<>();
            int pathCount = 0;
            double startTime = System.currentTimeMillis();
            for (String path : cldrFile) {
                String fullPath = cldrFile.getFullXPath(path);
                String value = cldrFile.getStringValue(path);
                bundle.check(fullPath, possibleErrors, value);
                if (++pathCount == maxPathCount) {
                    break;
                }
            }
            deltaTime[i] = System.currentTimeMillis() - startTime;
            /*
             * Expect possibleErrors to have size zero.
             * A future enhancement of this test could modify some values to force errors,
             * and confirm that the errors are returned identically the first and second times.
             */
            assertEquals("possibleErrors, loop index " + i, possibleErrors.size(), 0);
        }
        /*
         * Expect second time to be about a hundredth of first time; error if more than a tenth.
         * On one occasion, smoketest had times 171.0 and 5.0.
         */
        if (deltaTime[1] > deltaTime[0] / 10) {
            errln(
                    "TestResultBundle cache should yield more benefit: times "
                            + deltaTime[0]
                            + " and "
                            + deltaTime[1]);
        }
    }

    /** Test the "collisionless" error/warning messages. */
    public static final String INDIVIDUAL_TESTS =
            ".*(CheckCasing|CheckCurrencies|CheckDates|CheckExemplars|CheckForCopy|CheckForExemplars|CheckMetazones|CheckNumbers)";

    static final Factory factory = testInfo.getCldrFactory();
    static final CLDRFile english = testInfo.getEnglish();

    private static final boolean DEBUG = true;

    static final Factory cldrFactory = CLDRConfig.getInstance().getCldrFactory();
    static final Factory cldrFactoryWithSeed =
            CLDRConfig.getInstance().getCommonAndSeedAndMainAndAnnotationsFactory();

    public void testPlaceholderSamples() {
        CLDRFile root = cldrFactory.make("root", true);
        String[][] tests = {
            {"he", "//ldml/numbers/minimalPairs/pluralMinimalPairs[@count=\"one\"]", "שנה"},
            // test edge cases
            // locale, path, value, 0..n Subtype errors
            {"en", "//ldml/localeDisplayNames/localeDisplayPattern/localePattern", "{0}huh?{1}"},
            {
                "en",
                "//ldml/localeDisplayNames/localeDisplayPattern/localePattern",
                "huh?",
                "missingPlaceholders"
            },
            {
                "en",
                "//ldml/localeDisplayNames/localeDisplayPattern/localePattern",
                "huh?{0}",
                "missingPlaceholders"
            },
            {
                "en",
                "//ldml/localeDisplayNames/localeDisplayPattern/localePattern",
                "huh?{1}",
                "missingPlaceholders",
                "gapsInPlaceholderNumbers"
            },
            {
                "en",
                "//ldml/localeDisplayNames/localeDisplayPattern/localePattern",
                "{0}huh?{1}{2}",
                "extraPlaceholders"
            },
            {
                "en",
                "//ldml/localeDisplayNames/localeDisplayPattern/localePattern",
                "{0}huh?{1}{0}",
                "duplicatePlaceholders"
            },
            {
                "fr",
                "//ldml/numbers/minimalPairs/ordinalMinimalPairs[@ordinal=\"other\"]",
                "Prenez la e à droite.",
                "missingPlaceholders"
            },
            {
                "fr",
                "//ldml/numbers/minimalPairs/ordinalMinimalPairs[@ordinal=\"other\"]",
                "Prenez la {0}e à droite."
            },
            {
                "fr",
                "//ldml/numbers/minimalPairs/pluralMinimalPairs[@count=\"other\"]",
                "jours",
                "missingPlaceholders"
            },
            {"fr", "//ldml/numbers/minimalPairs/pluralMinimalPairs[@count=\"other\"]", "{0} jours"},
            {
                "cy",
                "//ldml/numbers/minimalPairs/pluralMinimalPairs[@count=\"other\"]",
                "ci cath",
                "missingPlaceholders"
            },
            {"cy", "//ldml/numbers/minimalPairs/pluralMinimalPairs[@count=\"other\"]", "{0} ci"},
            {
                "cy",
                "//ldml/numbers/minimalPairs/pluralMinimalPairs[@count=\"other\"]",
                "{0} ci, {0} cath"
            },
            {
                "pl",
                "//ldml/numbers/minimalPairs/caseMinimalPairs[@case=\"accusative\"]",
                "biernik",
                "missingPlaceholders"
            },
            {
                "pl",
                "//ldml/numbers/minimalPairs/caseMinimalPairs[@case=\"accusative\"]",
                "{0} biernik"
            },
            {
                "fr",
                "//ldml/numbers/minimalPairs/genderMinimalPairs[@gender=\"feminine\"]",
                "de genre féminin",
                "missingPlaceholders"
            },
            {
                "fr",
                "//ldml/numbers/minimalPairs/genderMinimalPairs[@gender=\"feminine\"]",
                "la {0}"
            },
            {
                "ar",
                "//ldml/units/unitLength[@type=\"long\"]/unit[@type=\"duration-hour\"]/unitPattern[@count=\"one\"]",
                "ساعة"
            },
            {
                "ar",
                "//ldml/units/unitLength[@type=\"long\"]/unit[@type=\"duration-hour\"]/unitPattern[@count=\"one\"]",
                "{0} ساعة"
            },
            {
                "ar",
                "//ldml/units/unitLength[@type=\"long\"]/unit[@type=\"duration-hour\"]/unitPattern[@count=\"one\"]",
                "{1}{0} ساعة",
                "extraPlaceholders"
            },
            {"he", "//ldml/numbers/minimalPairs/pluralMinimalPairs[@count=\"one\"]", "שנה"},
            {"he", "//ldml/numbers/minimalPairs/pluralMinimalPairs[@count=\"two\"]", "שנתיים"},
            {
                "he",
                "//ldml/numbers/minimalPairs/pluralMinimalPairs[@count=\"many\"]",
                "שנה",
                "missingPlaceholders"
            },
            {
                "he",
                "//ldml/numbers/minimalPairs/pluralMinimalPairs[@count=\"other\"]",
                "שנים",
                "missingPlaceholders"
            },
        };
        for (String[] row : tests) {
            String localeId = row[0];
            String path = row[1];
            String value = row[2];
            Set<Subtype> expected = new TreeSet<>();
            for (int i = 3; i < row.length; ++i) {
                expected.add(Subtype.valueOf(row[i]));
            }
            List<CheckStatus> possibleErrors = new ArrayList<>();
            checkPathValue(root, localeId, path, value, possibleErrors);
            Set<Subtype> actual = new TreeSet<>();
            for (CheckStatus item : possibleErrors) {
                if (PatternPlaceholders.PLACEHOLDER_SUBTYPES.contains(item.getSubtype())) {
                    actual.add(item.getSubtype());
                }
            }
            if (!assertEquals(Arrays.asList(row).toString(), expected, actual)) {
                int debug = 0;
            }
        }
    }

    public void checkPathValue(
            CLDRFile root,
            String localeId,
            String path,
            String value,
            List<CheckStatus> possibleErrors) {
        XMLSource localeSource = new SimpleXMLSource(localeId);
        localeSource.putValueAtPath(path, value);

        TestFactory currFactory = makeTestFactory(root, localeSource);
        CLDRFile cldrFile = currFactory.make(localeSource.getLocaleID(), true);
        CheckForExemplars check = new CheckForExemplars(currFactory);

        Options options = new Options();
        check.setCldrFileToCheck(cldrFile, options, possibleErrors);
        check.handleCheck(path, path, value, options, possibleErrors);
    }

    public void TestPlaceholders() {
        CheckCLDR.setDisplayInformation(english);
        checkPlaceholders(english);
        checkPlaceholders(factory.make("de", true));
    }

    public void checkPlaceholders(CLDRFile cldrFileToTest) {
        // verify that every item with {0} has a pattern in pattern
        // placeholders,
        // and that every one generates an error in CheckCDLR for patterns when
        // given "?"
        // and that every non-pattern doesn't have an error in CheckCLDR for
        // patterns when given "?"
        //
        // For the following: traditional placeholders just have {0}, {1}, {2}, ...
        // But personName namePattern placeHolders start with [a-z], then continue with
        // [0-9a-zA-Z-]+
        // They need to be distinguished from non-placeholder patterns using {} in UnicodeSets
        Matcher messagePlaceholder = CheckForExemplars.PLACEHOLDER.matcher("");
        PatternPlaceholders patternPlaceholders = PatternPlaceholders.getInstance();

        CheckCLDR test = CheckCLDR.getCheckAll(factory, ".*");
        List<CheckStatus> possibleErrors = new ArrayList<>();
        Options options = new Options();
        test.setCldrFileToCheck(cldrFileToTest, options, possibleErrors);
        List<CheckStatus> result = new ArrayList<>();

        PathHeader.Factory pathHeaderFactory = PathHeader.getFactory(cldrFileToTest);
        Set<PathHeader> sorted = new TreeSet<>();
        for (String path : cldrFileToTest.fullIterable()) {
            sorted.add(pathHeaderFactory.fromPath(path));
        }
        // test actual example with count=<digits>
        final String testPath =
                "//ldml/numbers/decimalFormats[@numberSystem=\"latn\"]/decimalFormatLength[@type=\"long\"]/decimalFormat[@type=\"standard\"]/pattern[@type=\"1000\"][@count=\"1\"]";
        sorted.add(pathHeaderFactory.fromPath(testPath));

        for (PathHeader pathHeader : sorted) {
            String path = pathHeader.getOriginalPath();
            if (path.contains("/exemplarCharacters") || path.contains("/parseLenients")) {
                // skip some paths with UnicodeSets that may include {} constructs
                // that should not be interpreted as placeholders
                continue;
            }
            String value = cldrFileToTest.getStringValue(path);
            if (value == null) {
                continue;
            }
            boolean containsMessagePattern = messagePlaceholder.reset(value).find();
            final Map<String, PlaceholderInfo> placeholderInfo = patternPlaceholders.get(path);
            final PlaceholderStatus placeholderStatus = patternPlaceholders.getStatus(path);
            if (placeholderStatus == PlaceholderStatus.DISALLOWED) {
                if (containsMessagePattern) {
                    errln(
                            cldrFileToTest.getLocaleID()
                                    + " Value ("
                                    + value
                                    + ") contains placeholder, but placeholder info = «"
                                    + placeholderStatus
                                    + "»\t"
                                    + path);
                    continue;
                }
            } else { // not disallowed
                if (!containsMessagePattern) {
                    // The following error seems wrong if placeholderStatus is LOCALE_DEPENDENT
                    // in which case some entries might not have placeholders; see CLDR-17820
                    errln(
                            cldrFileToTest.getLocaleID()
                                    + " Value ("
                                    + value
                                    + ") does not contain placeholder, but placeholder info = «"
                                    + placeholderStatus
                                    + "»\t"
                                    + path);
                    continue;
                }
                // get the set of placeholders
                HashSet<String> found = new HashSet<>();
                do {
                    found.add(messagePlaceholder.group()); // we loaded first one up above
                } while (messagePlaceholder.find());

                if (!found.equals(placeholderInfo.keySet())) {
                    if (placeholderStatus != PlaceholderStatus.LOCALE_DEPENDENT
                            && placeholderStatus != PlaceholderStatus.OPTIONAL) {
                        errln(
                                cldrFileToTest.getLocaleID()
                                        + " Value ("
                                        + value
                                        + ") has different placeholders than placeholder info «"
                                        + placeholderInfo.keySet()
                                        + "»\t"
                                        + path);
                    }
                } else {
                    logln("placeholder info = " + placeholderInfo + "\t" + path);
                }
            }
        }
    }

    public void TestFullErrors() {
        CheckCLDR test = CheckCLDR.getCheckAll(factory, INDIVIDUAL_TESTS);
        CheckCLDR.setDisplayInformation(english);

        final String localeID = "fr";
        checkLocale(test, localeID, "?", null);
    }

    public void TestAllLocales() {
        CheckCLDR test = CheckCLDR.getCheckAll(factory, INDIVIDUAL_TESTS);
        CheckCLDR.setDisplayInformation(english);
        Set<String> unique = new HashSet<>();
        LanguageTagParser ltp = new LanguageTagParser();
        Set<String> locales = new HashSet<>();
        for (String locale : getInclusion() <= 5 ? eightPointLocales : factory.getAvailable()) {
            checkNullWithInheritanceMark(locale);

            /*
             * For checkLocale, only test locales without regions. E.g., test "pt", skip "pt_PT"
             */
            if (ltp.set(locale).getRegion().isEmpty()) {
                locales.add(locale);
            }
        }
        // With ICU4J libs of 2020-03-23, using locales.parallelStream().forEach below
        // hangs, or crashes with NPE. Likely an ICU4J issue, but we don't really need
        // parallelStream() here anyway since we are only handling around 35 locales.
        // (And in fact this test seems faster without it)
        locales.forEach(locale -> checkLocale(test, locale, null, unique));
        logln("Count:\t" + locales.size());
    }

    private void checkNullWithInheritanceMark(String locale) {
        CLDRFile resolved = cldrFactory.make(locale, true);
        CLDRFile unresolved = resolved.getUnresolved();
        for (String path : unresolved.fullIterable()) {
            String value = unresolved.getStringValue(path);
            if (CldrUtility.INHERITANCE_MARKER.equals(value)) {
                assertNotNull(locale + " " + path, resolved.getStringValue(path));
            }
        }
    }

    public void TestA() {
        CheckCLDR test = CheckCLDR.getCheckAll(factory, INDIVIDUAL_TESTS);
        CheckCLDR.setDisplayInformation(english);
        Set<String> unique = new HashSet<>();

        checkLocale(test, "ko", null, unique);
    }

    public void checkLocale(
            CheckCLDR test, String localeID, String dummyValue, Set<String> unique) {
        checkLocale(test, testInfo.getCLDRFile(localeID, false), dummyValue, unique);
    }

    public void checkLocale(
            CheckCLDR test, CLDRFile nativeFile, String dummyValue, Set<String> unique) {
        String localeID = nativeFile.getLocaleID();
        List<CheckStatus> possibleErrors = new ArrayList<>();
        CheckCLDR.Options options = new CheckCLDR.Options();
        test.setCldrFileToCheck(nativeFile, options, possibleErrors);
        List<CheckStatus> result = new ArrayList<>();

        CLDRFile patched = nativeFile; // new CLDRFile(override);
        PathHeader.Factory pathHeaderFactory = PathHeader.getFactory(english);
        Set<PathHeader> sorted = new TreeSet<>();
        for (String path : patched) {
            final PathHeader pathHeader = pathHeaderFactory.fromPath(path);
            if (pathHeader != null) {
                sorted.add(pathHeader);
            }
        }

        logln("Checking: " + localeID);
        UnicodeSet missingCurrencyExemplars = new UnicodeSet();
        UnicodeSet missingExemplars = new UnicodeSet();

        for (PathHeader pathHeader : sorted) {
            String path = pathHeader.getOriginalPath();
            // override.overridePath = path;
            final String resolvedValue =
                    dummyValue == null ? patched.getStringValueWithBailey(path) : dummyValue;
            test.handleCheck(path, patched.getFullXPath(path), resolvedValue, options, result);
            if (result.size() != 0) {
                for (CheckStatus item : result) {
                    addExemplars(item, missingCurrencyExemplars, missingExemplars);
                    final String mainMessage =
                            StringId.getId(path)
                                    + "\t"
                                    + pathHeader
                                    + "\t"
                                    + english.getStringValue(path)
                                    + "\t"
                                    + item.getType()
                                    + "\t"
                                    + item.getSubtype();
                    if (unique != null) {
                        if (unique.contains(mainMessage)) {
                            continue;
                        } else {
                            unique.add(mainMessage);
                        }
                    }
                    logln(
                            localeID
                                    + "\t"
                                    + mainMessage
                                    + "\t"
                                    + resolvedValue
                                    + "\t"
                                    + item.getMessage()
                                    + "\t"
                                    + pathHeader.getOriginalPath());
                }
            }
        }
        if (missingCurrencyExemplars.size() != 0) {
            logln(
                    localeID
                            + "\tMissing Exemplars (Currency):\t"
                            + missingCurrencyExemplars.toPattern(false));
        }
        if (missingExemplars.size() != 0) {
            logln(localeID + "\tMissing Exemplars:\t" + missingExemplars.toPattern(false));
        }
    }

    void addExemplars(
            CheckStatus status, UnicodeSet missingCurrencyExemplars, UnicodeSet missingExemplars) {
        Object[] parameters = status.getParameters();
        if (parameters != null) {
            if (parameters.length >= 1 && status.getCause().getClass() == CheckForExemplars.class) {
                try {
                    UnicodeSet set = new UnicodeSet(parameters[0].toString());
                    if (status.getMessage().contains("currency")) {
                        missingCurrencyExemplars.addAll(set);
                    } else {
                        missingExemplars.addAll(set);
                    }
                } catch (RuntimeException e) {
                } // skip if not parseable as set
            }
        }
    }

    public void TestCheckNames() {
        CheckCLDR c = new CheckNames();
        Options options = new CheckCLDR.Options(new LinkedHashMap<>());
        List<CheckStatus> possibleErrors = new ArrayList<>();
        final CLDRFile english = testInfo.getEnglish();
        c.setCldrFileToCheck(english, options, possibleErrors);
        String xpath = "//ldml/localeDisplayNames/languages/language[@type=\"mga\"]";
        c.check(xpath, xpath, "Middle Irish (900-1200) ", options, possibleErrors);
        assertEquals("There should be an error", 1, possibleErrors.size());

        possibleErrors.clear();
        xpath = "//ldml/localeDisplayNames/currencies/currency[@type=\"afa\"]/name";
        c.check(xpath, xpath, "Afghan Afghani (1927-2002)", options, possibleErrors);
        assertEquals("Currencies are allowed to have dates", 0, possibleErrors.size());
    }

    /**
     * Check that at least one path in a locale is outdated and one path is not. That may change
     * each time. This needs to be a <locale,path> that is currently outdated (birth older than
     * English's) if the test fails with "no failure message" run GenerateBirths (if you haven't
     * done so) look at readable results in the log file in
     * https://github.com/unicode-org/cldr-staging/blob/main/births/41.0/fr.txt (for the current
     * version, not nec. 41.0) for a reasonable locale ( may change locale to something other than
     * fr) find a path that is outdated. To work on both limited and full submissions, choose one
     * with English = trunk Sometimes the English change is suppressed in a limited release if the
     * change is small. Pick another in that case. check the data files to ensure that it is in fact
     * outdated. change the path to that value the 3rd parameter is the message displayed to the
     * user, or "" if not 'English Changed' So the first group of tests are for items that should
     * not be outdated And the second group is ones that should be outdated.
     */
    public void TestCheckNew() {
        // Not outdated
        checkCheckNew("de", "//ldml/localeDisplayNames/languages/language[@type=\"en\"]", "");

        // Outdated
        checkCheckNew(
                "de",
                "//ldml/localeDisplayNames/territories/territory[@type=\"001\"]",
                "In CLDR 39.0 the English value for this field changed from “World” to “world”, but the corresponding value for your locale didn't change.");
        checkCheckNew(
                "el",
                "//ldml/units/unitLength[@type=\"long\"]/unit[@type=\"mass-grain\"]/displayName",
                "In CLDR 40.0 the English value for this field changed from “grain” to “grains”, but the corresponding value for your locale didn't change.");
    }

    public void checkCheckNew(String locale, String path, String expectedMessage) {
        final String title = "CheckNew " + locale + ", " + path;

        OutdatedPaths outdatedPaths = OutdatedPaths.getInstance();
        boolean isOutdated = outdatedPaths.isOutdated(locale, path);

        //
        String oldEnglishValue = outdatedPaths.getPreviousEnglish(path);
        if (!OutdatedPaths.NO_VALUE.equals(oldEnglishValue)) {
            assertEquals(title, expectedMessage.isEmpty(), !isOutdated);
        }

        CheckCLDR c = new CheckNew(testInfo.getCommonAndSeedAndMainAndAnnotationsFactory());
        List<CheckStatus> result = new ArrayList<>();
        final CheckCLDR.Options options = new CheckCLDR.Options(new HashMap<>());
        c.setCldrFileToCheck(testInfo.getCLDRFile(locale, true), options, result);
        c.check(path, path, "foobar", options, result);
        String actualMessage = "";
        for (CheckStatus status : result) {
            if (status.getSubtype() != Subtype.modifiedEnglishValue) {
                continue;
            }
            actualMessage = status.getMessage();
            break;
        }
        assertEquals(title, expectedMessage, actualMessage);
    }

    public void TestCheckNewRootFailure() {
        // Check that we get an error with the root value for an emoji.
        final Factory annotationsFactory = testInfo.getAnnotationsFactory();
        String locale = "yo"; // the name doesn't matter, since we're going to create a new one
        String path = "//ldml/annotations/annotation[@cp=\"😀\"][@type=\"tts\"]";
        CheckCLDR c = new CheckNew(annotationsFactory);
        List<CheckStatus> result = new ArrayList<>();
        Map<String, String> options = new HashMap<>();
        for (Phase phase : Phase.values()) {
            options.put(Options.Option.phase.getKey(), phase.toString());
            String value = "E10-836";
            // The following code used to check values of both "E10-836" and
            // CldrUtility.INHERITANCE_MARKER="↑↑↑",
            // but the latter does not make sense; "↑↑↑" will be followed up through its parent
            // chain, either
            // yielding a real value or the root value (but never "↑↑↑"). In regular CLDR data the
            // root value will
            // be like "E10-836" but in production data those root entreis are stripped and the root
            // value will be
            // null. Hence CheckNew.handleCheck only checks for entries like "E10-836", not "↑↑↑",
            // and the test
            // should only check those as well.
            {
                // make a fake locale, starting with real root

                CLDRFile root = annotationsFactory.make("root", false);
                XMLSource localeSource = new SimpleXMLSource(locale);
                localeSource.putValueAtPath(path, value);

                TestFactory currFactory = makeTestFactory(root, localeSource);
                CLDRFile cldrFile = currFactory.make(localeSource.getLocaleID(), true);

                c.setCldrFileToCheck(cldrFile, new CheckCLDR.Options(options), result);
                c.check(path, path, value, new CheckCLDR.Options(options), result);
                boolean gotOne = false;
                for (CheckStatus status : result) {
                    if (status.getSubtype() == Subtype.valueMustBeOverridden) {
                        gotOne = true;
                        assertEquals(
                                phase + " Error message check",
                                "This value must be a real translation, NOT the name/keyword placeholder.",
                                status.getMessage());
                    }
                }
                if (!gotOne) {
                    errln(phase + " Missing failure message for value=" + value + "; path=" + path);
                }
            }
        }
    }

    public TestFactory makeTestFactory(CLDRFile root, XMLSource localeSource) {
        CLDRFile localeCldr = new CLDRFile(localeSource);

        TestFactory factory = new TestFactory();
        factory.addFile(root);
        factory.addFile(localeCldr);
        return factory;
    }

    public void TestCheckDates() {
        CheckCLDR.setDisplayInformation(testInfo.getEnglish()); // just in case
        String prefix =
                "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dayPeriods/dayPeriodContext[@type=\"";
        String infix = "\"]/dayPeriodWidth[@type=\"wide\"]/dayPeriod[@type=\"";
        String suffix = "\"]";

        TestFactory testFactory = new TestFactory();

        List<CheckStatus> result = new ArrayList<>();
        Options options = new Options();
        final String collidingValue = "foobar";

        // Selection has stricter collision rules, because is is used to select different messages.
        // So two types with the same localization do collide unless they have exactly the same
        // rules.

        Object[][] tests = {
            {"en"}, // set locale

            // nothing collides with itself
            {Type.format, DayPeriod.night1, Type.format, DayPeriod.night1, Subtype.none},
            {Type.format, DayPeriod.morning1, Type.format, DayPeriod.morning1, Subtype.none},
            {Type.format, DayPeriod.afternoon1, Type.format, DayPeriod.afternoon1, Subtype.none},
            {Type.format, DayPeriod.evening1, Type.format, DayPeriod.evening1, Subtype.none},
            {Type.format, DayPeriod.am, Type.format, DayPeriod.am, Subtype.none},
            {Type.format, DayPeriod.pm, Type.format, DayPeriod.pm, Subtype.none},
            {Type.format, DayPeriod.noon, Type.format, DayPeriod.noon, Subtype.none},
            {Type.format, DayPeriod.midnight, Type.format, DayPeriod.midnight, Subtype.none},
            {Type.selection, DayPeriod.night1, Type.selection, DayPeriod.night1, Subtype.none},
            {Type.selection, DayPeriod.morning1, Type.selection, DayPeriod.morning1, Subtype.none},
            {
                Type.selection,
                DayPeriod.afternoon1,
                Type.selection,
                DayPeriod.afternoon1,
                Subtype.none
            },
            {Type.selection, DayPeriod.evening1, Type.selection, DayPeriod.evening1, Subtype.none},
            {Type.selection, DayPeriod.am, Type.selection, DayPeriod.am, Subtype.none},
            {Type.selection, DayPeriod.pm, Type.selection, DayPeriod.pm, Subtype.none},
            {Type.selection, DayPeriod.noon, Type.selection, DayPeriod.noon, Subtype.none},
            {Type.selection, DayPeriod.midnight, Type.selection, DayPeriod.midnight, Subtype.none},

            // fixed classes always collide
            {Type.format, DayPeriod.am, Type.format, DayPeriod.pm, Subtype.dateSymbolCollision},
            {Type.format, DayPeriod.am, Type.format, DayPeriod.noon, Subtype.dateSymbolCollision},
            {
                Type.format,
                DayPeriod.am,
                Type.format,
                DayPeriod.midnight,
                Subtype.dateSymbolCollision
            },
            {Type.format, DayPeriod.pm, Type.format, DayPeriod.noon, Subtype.dateSymbolCollision},
            {
                Type.format,
                DayPeriod.pm,
                Type.format,
                DayPeriod.midnight,
                Subtype.dateSymbolCollision
            },
            {
                Type.format,
                DayPeriod.noon,
                Type.format,
                DayPeriod.midnight,
                Subtype.dateSymbolCollision
            },
            {
                Type.selection,
                DayPeriod.am,
                Type.selection,
                DayPeriod.pm,
                Subtype.dateSymbolCollision
            },
            {
                Type.selection,
                DayPeriod.am,
                Type.selection,
                DayPeriod.noon,
                Subtype.dateSymbolCollision
            },
            {
                Type.selection,
                DayPeriod.am,
                Type.selection,
                DayPeriod.midnight,
                Subtype.dateSymbolCollision
            },
            {
                Type.selection,
                DayPeriod.pm,
                Type.selection,
                DayPeriod.noon,
                Subtype.dateSymbolCollision
            },
            {
                Type.selection,
                DayPeriod.pm,
                Type.selection,
                DayPeriod.midnight,
                Subtype.dateSymbolCollision
            },
            {
                Type.selection,
                DayPeriod.noon,
                Type.selection,
                DayPeriod.midnight,
                Subtype.dateSymbolCollision
            },

            // 00-12 morning1
            // 12-18 afternoon1
            // 18-21 evening1
            // 21-24 night1
            //
            // So for a 12hour time, we have:
            //
            // 12  1  2  3  4  5  6  7  8  9 10 11
            //  m  m  m  m  m  m  m  m  m  m  m  m
            //  a  a  a  a  a  a  e  e  e  n  n  n

            // Formatting has looser collision rules, because it is always paired with a time.
            // That is, it is not a problem if two items collide,
            // if it doesn't cause a collision when paired with a time.
            // But if 11:00 has the same format (eg 11 X) as 23:00, there IS a collision.
            // So we see if there is an overlap mod 12.

            {
                Type.format,
                DayPeriod.night1,
                Type.format,
                DayPeriod.morning1,
                Subtype.dateSymbolCollision
            },
            {Type.format, DayPeriod.night1, Type.format, DayPeriod.afternoon1, Subtype.none},
            {Type.format, DayPeriod.night1, Type.format, DayPeriod.evening1, Subtype.none},
            {
                Type.format,
                DayPeriod.morning1,
                Type.format,
                DayPeriod.afternoon1,
                Subtype.dateSymbolCollision
            },
            {
                Type.format,
                DayPeriod.morning1,
                Type.format,
                DayPeriod.evening1,
                Subtype.dateSymbolCollision
            },
            {Type.format, DayPeriod.afternoon1, Type.format, DayPeriod.evening1, Subtype.none},

            // Selection has stricter collision rules, because is is used to select different
            // messages.
            // So two types with the same localization do collide unless they have exactly the same
            // rules.
            // We use chr to test the "unless they have exactly the same rules" below.

            {
                Type.selection,
                DayPeriod.morning1,
                Type.selection,
                DayPeriod.night1,
                Subtype.dateSymbolCollision
            },
            {
                Type.selection,
                DayPeriod.morning1,
                Type.selection,
                DayPeriod.afternoon1,
                Subtype.dateSymbolCollision
            },
            {
                Type.selection, DayPeriod.morning1, Type.selection, DayPeriod.am, Subtype.none
            }, // morning1 and am is allowable
            {
                Type.selection,
                DayPeriod.morning1,
                Type.selection,
                DayPeriod.pm,
                Subtype.dateSymbolCollision
            },
            {"fr"},

            // nothing collides with itself
            {Type.format, DayPeriod.night1, Type.format, DayPeriod.night1, Subtype.none},
            {Type.format, DayPeriod.morning1, Type.format, DayPeriod.morning1, Subtype.none},
            {Type.format, DayPeriod.afternoon1, Type.format, DayPeriod.afternoon1, Subtype.none},
            {Type.format, DayPeriod.evening1, Type.format, DayPeriod.evening1, Subtype.none},

            // French has different rules
            // 00-04   night1
            // 04-12   morning1
            // 12-18   afternoon1
            // 18-00   evening1
            //
            // So for a 12hour time, we have:
            //
            // 12  1  2  3  4  5  6  7  8  9 10 11
            //  n  n  n  n  m  m  m  m  m  m  m  m
            //  a  a  a  a  a  a  e  e  e  e  e  e

            {Type.format, DayPeriod.night1, Type.format, DayPeriod.morning1, Subtype.none},
            {
                Type.format,
                DayPeriod.night1,
                Type.format,
                DayPeriod.afternoon1,
                Subtype.dateSymbolCollision
            },
            {Type.format, DayPeriod.night1, Type.format, DayPeriod.evening1, Subtype.none},
            {
                Type.format,
                DayPeriod.morning1,
                Type.format,
                DayPeriod.afternoon1,
                Subtype.dateSymbolCollision
            },
            {
                Type.format,
                DayPeriod.morning1,
                Type.format,
                DayPeriod.evening1,
                Subtype.dateSymbolCollision
            },
            {Type.format, DayPeriod.afternoon1, Type.format, DayPeriod.evening1, Subtype.none},
            {"chr"},
            // Chr lets use test that same rules don't collide in selection
            // <dayPeriodRule type="morning1" from="0:00" before="12:00" />
            // <dayPeriodRule type="noon" at="12:00" />
            // <dayPeriodRule type="afternoon1" after="12:00" before="24:00" />
            {Type.selection, DayPeriod.morning1, Type.selection, DayPeriod.am, Subtype.none},
            {Type.selection, DayPeriod.afternoon1, Type.selection, DayPeriod.pm, Subtype.none},
        };
        CLDRFile testFile = null;
        for (Object[] test : tests) {
            // set locale
            if (test.length == 1) {
                if (testFile != null) {
                    logln("");
                }
                testFile = new CLDRFile(new SimpleXMLSource((String) test[0]));
                testFactory.addFile(testFile);
                continue;
            }
            final DayPeriodInfo.Type type1 = (Type) test[0];
            final DayPeriodInfo.DayPeriod period1 = (DayPeriod) test[1];
            final DayPeriodInfo.Type type2 = (Type) test[2];
            final DayPeriodInfo.DayPeriod period2 = (DayPeriod) test[3];
            final Subtype expectedSubtype = (Subtype) test[4];

            final String path1 = prefix + type1.pathValue + infix + period1 + suffix;
            final String path2 = prefix + type2.pathValue + infix + period2 + suffix;

            testFile.add(path1, collidingValue);
            testFile.add(path2, collidingValue);

            CheckCLDR c = new CheckDates(testFactory);
            c.setCldrFileToCheck(testFile, options, result);

            result.clear();
            c.check(path1, path1, collidingValue, options, result);
            Subtype actualSubtype = Subtype.none;
            String message = null;
            for (CheckStatus status : result) {
                actualSubtype = status.getSubtype();
                message = status.getMessage();
                break;
            }
            assertEquals(
                    testFile.getLocaleID()
                            + " "
                            + type1
                            + "/"
                            + period1
                            + " vs "
                            + type2
                            + "/"
                            + period2
                            + (message == null ? "" : " [" + message + "]"),
                    expectedSubtype,
                    actualSubtype);

            testFile.remove(path1);
            testFile.remove(path2);
        }
        // Test for CLDR-14865
        testFile = new CLDRFile(new SimpleXMLSource("fi"));
        testFactory.addFile(testFile);
        String availableFormatTestPath =
                "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/availableFormats/dateFormatItem[@id=\"GyMd\"]";
        String availableFormatValue = "d.m.y G"; // erroneous, should have M not m
        testFile.add(availableFormatTestPath, availableFormatValue);
        CheckCLDR c = new CheckDates(testFactory);
        c.setCldrFileToCheck(testFile, options, result);
        result.clear();
        c.check(
                availableFormatTestPath,
                availableFormatTestPath,
                availableFormatValue,
                options,
                result);
        Subtype actualSubtype = Subtype.none;
        String message = null;
        for (CheckStatus status : result) {
            actualSubtype = status.getSubtype();
            message = status.getMessage();
            break;
        }
        if (actualSubtype != Subtype.incorrectDatePattern
                || message == null
                || !message.contains("d.M.y G")) {
            String errorMessage =
                    "fi generic availableFormat for id=GyMd with value "
                            + availableFormatValue
                            + ":";
            if (actualSubtype != Subtype.incorrectDatePattern) {
                errorMessage +=
                        " expected Subtype.incorrectDatePattern, got " + actualSubtype + " ;";
            }
            if (message == null || !message.contains("d.M.y G")) {
                errorMessage += " expected message should contain suggested d.M.y G, got message: ";
                errorMessage += (message == null) ? "(null)" : message;
            }
            errln(errorMessage);
        }
    }

    /** Should be some CLDR locales, plus a locale specially allowed in limited submission */
    final List<String> localesForRowAction = ImmutableList.of("cs", "fr");

    /** Needs adjustment for Limited Submission! */
    public void TestShowRowAction() {
        Map<Key, Pair<Boolean, String>> actionToExamplePath = new TreeMap<>();
        Counter<Key> counter = new Counter<>();

        for (String locale : localesForRowAction) {
            DummyPathValueInfo dummyPathValueInfo = new DummyPathValueInfo();
            dummyPathValueInfo.setLocale(CLDRLocale.getInstance(locale));
            CLDRFile cldrFile = testInfo.getCldrFactory().make(locale, true);
            CLDRFile cldrFileUnresolved = testInfo.getCldrFactory().make(locale, false);

            Set<PathHeader> sorted = new TreeSet<>();
            for (String path : cldrFile) {
                PathHeader ph = pathHeaderFactory.fromPath(path);
                sorted.add(ph);
            }

            for (Phase phase : Arrays.asList(Phase.SUBMISSION, Phase.VETTING)) {
                for (CheckStatus.Type status :
                        Arrays.asList(CheckStatus.warningType, CheckStatus.errorType)) {
                    dummyPathValueInfo.checkStatusType = status;

                    for (PathHeader ph : sorted) {
                        String path = ph.getOriginalPath();
                        SurveyToolStatus surveyToolStatus = ph.getSurveyToolStatus();
                        dummyPathValueInfo.setXpath(path);
                        dummyPathValueInfo.setBaselineValue(
                                cldrFileUnresolved.getStringValue(path));
                        StatusAction action =
                                phase.getShowRowAction(
                                        dummyPathValueInfo, InputMethod.DIRECT, ph, dummyUserInfo);

                        if (ph.shouldHide()) {
                            assertEquals(
                                    "HIDE ==> FORBID_READONLY",
                                    StatusAction.FORBID_READONLY,
                                    action);
                        } else if (CheckCLDR.LIMITED_SUBMISSION) {
                            if (status == CheckStatus.Type.Error) {
                                assertEquals("ERROR ==> ALLOW", StatusAction.ALLOW, action);
                            } else if (locale.equalsIgnoreCase("vo")) {
                                assertEquals(
                                        "vo ==> FORBID_READONLY",
                                        StatusAction.FORBID_READONLY,
                                        action);
                            } else if (dummyPathValueInfo.getBaselineValue() == null) {
                                if (!assertEquals(
                                        "missing ==> ALLOW", StatusAction.ALLOW, action)) {
                                    warnln("\t\t" + locale + "\t" + ph);
                                }
                            }
                        }

                        if (isVerbose()) {
                            Key key = new Key(locale, phase, status, surveyToolStatus, action);
                            counter.add(key, 1);

                            if (!actionToExamplePath.containsKey(key)) {
                                // for debugging
                                if (locale.equals("vo") && action == StatusAction.ALLOW) {
                                    StatusAction action2 =
                                            phase.getShowRowAction(
                                                    dummyPathValueInfo,
                                                    InputMethod.DIRECT,
                                                    ph,
                                                    dummyUserInfo);
                                }
                                actionToExamplePath.put(
                                        key,
                                        Pair.of(
                                                dummyPathValueInfo.getBaselineValue() != null,
                                                path));
                            }
                        }
                    }
                }
            }
        }
        if (isVerbose()) {
            for (Entry<Key, Pair<Boolean, String>> entry : actionToExamplePath.entrySet()) {
                System.out.print(
                        "\n"
                                + entry.getKey()
                                + "\t"
                                + entry.getValue().getFirst()
                                + "\t"
                                + entry.getValue().getSecond());
            }
            System.out.println();
            for (R2<Long, Key> entry : counter.getEntrySetSortedByCount(false, null)) {
                System.out.println(entry.get0() + "\t" + entry.get1());
            }
        }
    }

    static class Key extends R5<Phase, CheckStatus.Type, SurveyToolStatus, StatusAction, String> {
        public Key(
                String locale,
                Phase phase,
                CheckStatus.Type status,
                SurveyToolStatus stStatus,
                StatusAction action) {
            super(phase, status, stStatus, action, locale);
        }

        @Override
        public String toString() {
            return get0() + "\t" + get1() + "\t" + get2() + "\t" + get3() + "\t" + get4();
        }
    }

    //    private static CLDRURLS URLS = testInfo.urls();

    private static final PathHeader.Factory pathHeaderFactory =
            PathHeader.getFactory(testInfo.getEnglish());

    //    private static final CoverageInfo coverageInfo = new
    // CoverageInfo(testInfo.getSupplementalDataInfo());

    private static final VoterInfo dummyVoterInfo =
            new VoterInfo(
                    Organization.cldr, org.unicode.cldr.util.VoteResolver.Level.vetter, "somename");

    private static final UserInfo dummyUserInfo =
            new UserInfo() {
                @Override
                public VoterInfo getVoterInfo() {
                    return dummyVoterInfo;
                }
            };

    public static class DummyPathValueInfo implements PathValueInfo {
        private CLDRLocale locale;
        private String xpath;
        private String baselineValue;
        private CheckStatus.Type checkStatusType;

        private CandidateInfo candidateInfo =
                new CandidateInfo() {
                    @Override
                    public String getValue() {
                        return null;
                    }

                    @Override
                    public Collection<UserInfo> getUsersVotingOn() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public List<CheckStatus> getCheckStatusList() {
                        return checkStatusType == null
                                ? Collections.emptyList()
                                : Collections.singletonList(
                                        new CheckStatus().setMainType(checkStatusType));
                    }
                };

        @Override
        public Collection<? extends CandidateInfo> getValues() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CandidateInfo getCurrentItem() {
            return candidateInfo;
        }

        @Override
        public String getBaselineValue() {
            return baselineValue;
        }

        @Override
        public Level getCoverageLevel() {
            return Level.MODERN;
        }

        @Override
        public boolean hadVotesSometimeThisRelease() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CLDRLocale getLocale() {
            return locale;
        }

        @Override
        public String getXpath() {
            return xpath;
        }

        public void setLocale(CLDRLocale locale) {
            this.locale = locale;
        }

        public void setXpath(String xpath) {
            this.xpath = xpath;
        }

        public void setBaselineValue(String baselineValue) {
            this.baselineValue = baselineValue;
        }
    }

    final Set<String> cldrLocales =
            StandardCodes.make().getLocaleCoverageLocales(Organization.cldr);
    final Map<String, Status> validity = Validity.getInstance().getCodeToStatus(LstrType.language);
    final Map<String, R2<List<String>, String>> langAliases =
            CLDRConfig.getInstance().getSupplementalDataInfo().getLocaleAliasInfo().get("language");
    final Set<String> existingLocales =
            CLDRConfig.getInstance().getCommonAndSeedAndMainAndAnnotationsFactory().getAvailable();
    final LikelySubtags likely = new LikelySubtags();

    /** Simple check on locales and paths for limited submissions */
    public void TestSubmissionLocales() {

        for (String locale : SubmissionLocales.ALLOW_ALL_PATHS_BASIC) {
            checkLocaleOk(locale, false);
        }
        for (String locale : SubmissionLocales.LOCALES_FOR_LIMITED) {
            checkLocaleOk(locale, true);
        }
    }

    /**
     * Check that the locale is valid, that it is either in or out of allowed locales, and that the
     * locale is not deprecated
     *
     * @param language
     * @param expectedInCLDRLocales
     */
    private void checkLocaleOk(final String locale, final boolean expectedInCLDRLocales) {
        final LanguageTagParser ltp = new LanguageTagParser().set(locale);
        String language = ltp.getLanguage();

        Status status = validity.get(language);
        assertTrue(
                language + " valid?", status == Status.regular); //  || status == Status.macroregion

        final R2<List<String>, String> alias = langAliases.get(language);
        if (!assertNull(language + " language is not deprecated", alias)) {
            errln(language + ": " + alias);
        }

        // locale tests
        if (!assertTrue(
                locale + " locale is in common or seed", existingLocales.contains(locale))) {
            return;
        }
        if (expectedInCLDRLocales) {
            assertTrue(locale + " is in cldrLocales", cldrLocales.contains(locale));
        }
    }

    final String UNIT_PATH = "//ldml/units/unitLength[@type=\"long\"]";

    enum LimitedStatus {
        allowedUnitMissing,
        allowedUnitNotMissing,
        allowedOtherMissing,
        allowedOtherNotMissing,
        disallowed;

        static LimitedStatus of(boolean unit, boolean missing) {
            if (unit && missing) {
                return allowedUnitMissing;
            } else if (unit && !missing) {
                return allowedUnitNotMissing;
            } else if (!unit && missing) {
                return allowedOtherMissing;
            } else {
                return allowedOtherNotMissing;
            }
        }
    }

    /** Depends on correct values in the above constants. */
    public void TestALLOWED_IN_LIMITED_PATHS() {
        if (!CheckCLDR.LIMITED_SUBMISSION) {
            return;
        }

        /**
         * Note: Constants moved from here to data driven test.
         *
         * <p>see org.unicode.cldr.test.TestSubmissionLocales and TestSubmissionLocales.csv
         */
        if (SHOW_LIMITED) {
            System.out.println();
            for (String locale : cldrFactoryWithSeed.getAvailable()) {
                LanguageTagParser ltp = new LanguageTagParser();
                if (!ltp.set(locale).getRegion().isEmpty()
                        || !ltp.set(locale).getVariants().isEmpty()
                        || locale.equals("root")) {
                    continue;
                }
                CLDRFile cldrFile = cldrFactoryWithSeed.make(locale, false);
                Level cldrLevel =
                        StandardCodes.make().getLocaleCoverageLevel(Organization.cldr, locale);
                // patch until Rohingya is added
                if (cldrLevel == Level.UNDETERMINED && locale.equals("rhg")) {
                    cldrLevel = Level.BASIC;
                }
                Counter<LimitedStatus> counter = new Counter<>();
                for (String path : cldrFile.fullIterable()) {
                    Level coverage =
                            SupplementalDataInfo.getInstance().getCoverageLevel(path, locale);
                    if (coverage.compareTo(cldrLevel) > 0) {
                        continue;
                    }
                    String value = cldrFile.getStringValue(path);
                    boolean isMissing = value == null;
                    boolean allowed =
                            SubmissionLocales.allowEvenIfLimited(locale, path, false, isMissing);
                    if (allowed) {
                        boolean isUnit = path.startsWith(UNIT_PATH);
                        counter.add(LimitedStatus.of(isUnit, isMissing), 1);
                    } else {
                        counter.add(LimitedStatus.disallowed, 1);
                    }
                }
                System.out.print(
                        locale
                                + "\t"
                                + english.nameGetter().getNameFromIdentifier(locale)
                                + "\t"
                                + cldrLevel);
                for (LimitedStatus limitedStatus : LimitedStatus.values()) {
                    System.out.print("\t" + limitedStatus + ":\t" + counter.get(limitedStatus));
                }
                System.out.println();
            }
        } else {
            warnln("Set -DTestCheckCLDR:SHOW_LIMITED to see information about affected paths.");
        }
    }

    public void TestInfohubLinks13979() {
        CLDRFile root = cldrFactory.make("root", true);
        List<CheckStatus> possibleErrors = new ArrayList<>();
        String[][] tests = {
            // test edge cases
            // locale, path, value, expected
            {
                "fr",
                "//ldml/numbers/minimalPairs/genderMinimalPairs[@gender=\"feminine\"]",
                "de genre féminin",
                "Need at least 1 placeholder(s), but only have 0. Placeholders are: {{0}={GENDER}, e.g. “‹noun phrase in this gender›”}; see <a href='http://cldr.unicode.org/translation/error-codes#missingPlaceholders'  target='cldr_error_codes'>missing placeholders</a>."
            },
        };
        for (String[] row : tests) {
            String localeId = row[0];
            String path = row[1];
            String value = row[2];
            String expected = row[3];

            checkPathValue(root, localeId, path, value, possibleErrors);
            for (CheckStatus error : possibleErrors) {
                if (error.getSubtype() == Subtype.missingPlaceholders) {
                    assertEquals("message", expected, error.getMessage());
                }
            }
        }
    }

    public void Test14866() {
        final SupplementalDataInfo supplementalDataInfo = SupplementalDataInfo.getInstance();
        String locale = "pl";
        int expectedCount = 14;

        GrammarInfo grammarInfo = supplementalDataInfo.getGrammarInfo(locale);
        logln("Locale:\t" + locale + "\n\tGrammarInfo:\t" + grammarInfo);
        CLDRFile pl = factory.make(locale, true);
        System.out.println("");
        Collection<PathHeader> pathHeaders = new TreeSet<>(); // new ArrayList(); //
        for (String path : pl.fullIterable()) {
            if (path.startsWith(
                    "//ldml/units/unitLength[@type=\"long\"]/unit[@type=\"duration-century\"]")) {
                PathHeader pathHeader = PathHeader.getFactory().fromPath(path);
                boolean added = pathHeaders.add(pathHeader);
            }
        }
        int count = 0;
        for (PathHeader pathHeader : pathHeaders) {
            String path = pathHeader.getOriginalPath();
            String value = pl.getStringValue(path);
            CLDRFile.Status status = new CLDRFile.Status();
            String localeFound = pl.getSourceLocaleID(path, status);
            Level level = supplementalDataInfo.getCoverageLevel(path, locale);
            logln(
                    "\n\t"
                            + ++count
                            + " Locale:\t"
                            + locale
                            + "\n\tLocaleFound:\t"
                            + (locale.equals(localeFound) ? "«same»" : localeFound)
                            + "\n\tPathHeader:\t"
                            + pathHeader
                            + "\n\tPath:    \t"
                            + path
                            + "\n\tPathFound:\t"
                            + (path.equals(status.pathWhereFound)
                                    ? "«same»"
                                    : status.pathWhereFound)
                            + "\n\tValue:\t"
                            + value
                            + "\n\tLevel:\t"
                            + level);
        }
        assertEquals("right number of elements found", expectedCount, count);
    }
}
