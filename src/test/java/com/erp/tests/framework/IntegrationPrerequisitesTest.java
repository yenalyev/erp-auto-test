package com.erp.tests.framework;

import com.erp.utils.helpers.ApiResponseHelper;
import com.erp.utils.helpers.IntegrationPrerequisites;
import io.restassured.builder.ResponseBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;

public class IntegrationPrerequisitesTest {
    @DataProvider public Object[][] failedProbes() {
        return new Object[][] {{403, "{}"}, {404, "{}"}, {500, "{}"}, {201, "{}"},
                {200, "<html>login</html>"}, {200, "not json"}, {200, ""}};
    }

    @Test(dataProvider = "failedProbes")
    public void enabledIntegrationFailuresNeverBecomeSkips(int status, String body) {
        assertThatThrownBy(() -> IntegrationPrerequisites.probe(true, "test integration", () ->
                new ResponseBuilder().setStatusCode(status).setBody(body).build()))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(org.testng.SkipException.class);
    }

    @Test public void explicitlyDisabledIntegrationDoesNotProbe() {
        assertThat(IntegrationPrerequisites.probe(false, "disabled", () -> {
            throw new AssertionError("Disabled integration must not make HTTP calls");
        })).isFalse();
    }

    @Test public void enabledHealthyIntegrationRuns() {
        assertThat(IntegrationPrerequisites.probe(true, "enabled", () ->
                new ResponseBuilder().setStatusCode(200).setBody("[]").build())).isTrue();
    }

    @Test public void connectionFailureKeepsItsOriginalCause() {
        RuntimeException failure = new RuntimeException("connection refused");
        assertThatThrownBy(() -> IntegrationPrerequisites.probe(true, "enabled", () -> { throw failure; }))
                .isSameAs(failure);
    }

    @Test public void htmlLoginPageIsAFailureForEveryJsonConsumer() {
        assertThatThrownBy(() -> ApiResponseHelper.ensureJsonBody(
                new ResponseBuilder().setStatusCode(200).setBody("<html>login</html>").build(), "List data"))
                .isInstanceOf(IllegalStateException.class).isNotInstanceOf(org.testng.SkipException.class);
    }
}
