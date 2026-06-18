package com.erp.exceptions;

/**
 * Signals a defect in the application under test (not a test or environment issue).
 * Fails the test with an explicit product-bug message in reports.
 */
public class ProductBugException extends AssertionError {

    public ProductBugException(String message) {
        super(message);
    }
}
