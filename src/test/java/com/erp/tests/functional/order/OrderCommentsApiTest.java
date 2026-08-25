package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.order.OrderDataFactory;
import com.erp.models.response.OrderCommentResponse;
import com.erp.models.response.OrderResponse;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Orders")
@Feature("REQ-ORD Order comments")
public class OrderCommentsApiTest extends OrderApiTestBase {

    @Test(priority = 10)
    @TestCaseId("TC-ORD-040")
    @Story("Add comment")
    @Severity(SeverityLevel.CRITICAL)
    public void testPostComment() {
        var order = orderFixture.createOrder(REQUESTER);
        String text = "Please expedite order " + order.getId();

        OrderCommentResponse comment = orderFixture.addComment(REQUESTER, order.getId(), text);

        assertThat(comment.getText()).isEqualTo(text);
        assertThat(comment.getCreatedAt()).isNotNull();
    }

    @Test(priority = 11)
    @TestCaseId("TC-ORD-041")
    @Story("List comments")
    public void testGetCommentsNewestFirst() {
        var order = orderFixture.createOrder(REQUESTER);
        orderFixture.addComment(REQUESTER, order.getId(), "older comment");
        orderFixture.addComment(REQUESTER, order.getId(), "newest comment");

        List<OrderCommentResponse> comments = orderFixture.getComments(REQUESTER, order.getId());

        assertThat(comments).hasSizeGreaterThanOrEqualTo(2);
        assertThat(comments.getFirst().getText()).isEqualTo("newest comment");
    }

    @Test(priority = 12)
    @TestCaseId("TC-ORD-042")
    @Story("Comment validation")
    public void testBlankCommentReturns400() {
        var order = orderFixture.createOrder(REQUESTER);

        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_COMMENT,
                REQUESTER,
                OrderDataFactory.buildCommentRequest("   "),
                order.getId());
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test(priority = 13)
    @TestCaseId("TC-ORD-043")
    @Story("Comment from gathering")
    @Description("Коментар дозволений з read на gathering (не requester).")
    public void testGathererCanComment() {
        OrderResponse order = prepareManagedInProgress();
        String text = "gatherer comment " + order.getId();
        OrderCommentResponse comment = orderFixture.addComment(GATHERER, order.getId(), text);
        assertThat(comment.getText()).isEqualTo(text);
    }

    @Test(priority = 14)
    @TestCaseId("TC-ORD-044")
    @Story("Comment without access")
    @Description("Коментар без доступу до заявки → 403.")
    public void testOutsiderCommentDenied() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_COMMENT,
                OUTSIDER,
                OrderDataFactory.buildCommentRequest("outsider comment"),
                order.getId());
        assertThat(response.statusCode()).isIn(403, 404);
    }
}
