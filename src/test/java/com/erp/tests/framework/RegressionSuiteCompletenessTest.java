package com.erp.tests.framework;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class RegressionSuiteCompletenessTest {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+);");
    private static final Pattern PUBLIC_CLASS = Pattern.compile(
            "(?m)^public\\s+(?:final\\s+)?class\\s+(\\w+)");
    private static final Pattern TEST_METHOD = Pattern.compile("@Test(?:\\s|\\()");
    private static final Pattern SUITE_CLASS = Pattern.compile("<class\\s+name=\"([^\"]+)\"\\s*/>");

    private static final Set<String> DOCUMENTED_EXCEPTIONS = Set.of(
            "com.erp.tests.functional.statistics.FabergeMalutkaPlanProbeTest",
            "com.erp.tests.functional.statistics.FabergeNeededResourcesDevProbeTest",
            "com.erp.tests.rbac.RbacAccessMatrixTest"
    );

    @Test
    public void allRunnableErpTestsAreRegisteredInRegressionSuite() throws IOException {
        Path root = projectRoot();
        Set<String> registered = registeredClasses(
                root.resolve("src/test/resources/suites/regression.xml"));
        Set<String> missing = new TreeSet<>();

        try (var files = Files.walk(root.resolve("src/test/java/com/erp/tests"))) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/framework/"))
                    .forEach(path -> inspect(path, registered, missing));
        }

        assertThat(missing)
                .as("Runnable ERP test classes missing from regression.xml")
                .isEmpty();
    }

    private static void inspect(Path path, Set<String> registered, Set<String> missing) {
        try {
            String source = Files.readString(path);
            if (!TEST_METHOD.matcher(source).find()) {
                return;
            }
            Matcher packageMatcher = PACKAGE.matcher(source);
            Matcher classMatcher = PUBLIC_CLASS.matcher(source);
            if (!packageMatcher.find() || !classMatcher.find()) {
                return;
            }
            String className = packageMatcher.group(1) + "." + classMatcher.group(1);
            if (!registered.contains(className) && !DOCUMENTED_EXCEPTIONS.contains(className)) {
                missing.add(className);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }

    private static Set<String> registeredClasses(Path suite) throws IOException {
        Matcher matcher = SUITE_CLASS.matcher(Files.readString(suite));
        Set<String> classes = new HashSet<>();
        while (matcher.find()) {
            classes.add(matcher.group(1));
        }
        return classes;
    }

    private static Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))) {
            return parent;
        }
        throw new IllegalStateException("Cannot locate erp-auto-test project root from " + current);
    }
}
