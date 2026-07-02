package com.erp.listeners;

import com.erp.utils.helpers.TestCaseIdExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes {@code progress.json} for the remote test runner when
 * {@code -Drunner.progress.file=/path/to/progress.json} is set.
 */
@Slf4j
public class RunProgressListener implements ITestListener, ISuiteListener {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger passed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();

    private volatile Path progressFile;
    private volatile String currentTest;
    private volatile String currentTestCaseId;

    @Override
    public void onStart(ISuite suite) {
        progressFile = resolveProgressFile();
        if (progressFile == null) {
            return;
        }
        log.info("Run progress file: {}", progressFile);
        total.set(0);
        writeProgress();
    }

    @Override
    public void onStart(ITestContext context) {
        if (progressFile == null) {
            return;
        }
        total.addAndGet(context.getAllTestMethods().length);
        writeProgress();
    }

    @Override
    public void onTestStart(ITestResult result) {
        if (progressFile == null) {
            return;
        }
        currentTest = result.getMethod().getMethodName();
        currentTestCaseId = TestCaseIdExtractor.getTestCaseId(result);
        if ("NO_ID".equals(currentTestCaseId)) {
            currentTestCaseId = null;
        }
        writeProgress();
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        if (progressFile == null) {
            return;
        }
        passed.incrementAndGet();
        completed.incrementAndGet();
        writeProgress();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        if (progressFile == null) {
            return;
        }
        failed.incrementAndGet();
        completed.incrementAndGet();
        writeProgress();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        if (progressFile == null) {
            return;
        }
        skipped.incrementAndGet();
        completed.incrementAndGet();
        writeProgress();
    }

    private Path resolveProgressFile() {
        String property = System.getProperty("runner.progress.file");
        if (property == null || property.isBlank()) {
            return null;
        }
        return Path.of(property.trim());
    }

    private void writeProgress() {
        Path target = progressFile;
        if (target == null) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            ObjectNode node = MAPPER.createObjectNode();
            node.put("total", total.get());
            node.put("completed", completed.get());
            node.put("passed", passed.get());
            node.put("failed", failed.get());
            node.put("skipped", skipped.get());
            if (currentTest != null) {
                node.put("currentTest", currentTest);
            }
            if (currentTestCaseId != null) {
                node.put("currentTestCaseId", currentTestCaseId);
            }
            node.put("updatedAt", Instant.now().toString());

            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), node);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            log.warn("Failed to write progress file {}: {}", target, ex.getMessage());
        }
    }
}
