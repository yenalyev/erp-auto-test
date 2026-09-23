package com.erp.tests.functional.order;

import com.erp.annotations.TestCaseId;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.user.UserDataFactory;
import com.erp.data.factories.order.OrderDataFactory;
import com.erp.enums.BusinessRole;
import com.erp.enums.UserRole;
import com.erp.fixtures.StorageFixture;
import com.erp.fixtures.UserFixture;
import com.erp.models.response.OrderCommentResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.PagedOrderResponse;
import com.erp.models.response.StorageResponse;
import com.erp.models.response.UserModelResponse;
import io.qameta.allure.*;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Epic("Orders")
@Feature("REQ-ORD Order comments")
public class OrderCommentsApiTest extends OrderApiTestBase {

    private static final UserRole USERNAME_ONLY_AUTHOR = UserRole.ORDER_SOURCE_KEEPER;

    private UserFixture commentAuthorFixture;
    private UserFixture.BusinessActor usernameOnlyAuthor;

    @BeforeClass(alwaysRun = true, dependsOnMethods = "setupOrderApiTests")
    public void setupUsernameOnlyCommentAuthor() {
        commentAuthorFixture = new UserFixture(testContext, apiExecutor);
        StorageResponse requester = new StorageFixture(testContext, apiExecutor)
                .getById(MANAGER, requesterStorageId);
        usernameOnlyAuthor = commentAuthorFixture.createBusinessActor(
                getPlaywrightSessionProvider(), BusinessRole.BUSINESS_UNIT_OWNER, List.of(requester));

        UserModelResponse profile = commentAuthorFixture.getUser(MANAGER, usernameOnlyAuthor.userId());
        UserModelResponse updated = commentAuthorFixture.updateUser(
                MANAGER,
                usernameOnlyAuthor.userId(),
                UserDataFactory.fromExisting(profile).toBuilder()
                        .firstName(null)
                        .lastName(null)
                        .build());
        assertThat(updated.getFirstName() == null || updated.getFirstName().isBlank()).isTrue();
        assertThat(updated.getLastName() == null || updated.getLastName().isBlank()).isTrue();

        apiExecutor.setSessionForRole(
                USERNAME_ONLY_AUTHOR, usernameOnlyAuthor.username(), usernameOnlyAuthor.password());
    }

    @AfterClass(alwaysRun = true)
    public void cleanupUsernameOnlyCommentAuthor() {
        apiExecutor.restoreDefaultSessionForRole(USERNAME_ONLY_AUTHOR);
        if (commentAuthorFixture != null) {
            commentAuthorFixture.deactivateTrackedUsers();
        }
    }

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

    @Test(priority = 15)
    @TestCaseId("TC-ORD-045")
    @Story("Comment author fallback")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Якщо firstName і lastName автора відсутні, POST і GET comments повертають username замість null/«Невідомо».")
    public void usernameUsedWhenCommentAuthorHasNoFirstOrLastName() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        String text = "username fallback " + order.getId();

        OrderCommentResponse created = orderFixture.addComment(
                USERNAME_ONLY_AUTHOR, order.getId(), text);

        assertThat(created.getAuthorName()).isEqualTo(usernameOnlyAuthor.username());
        assertThat(created.getAuthorName()).isNotBlank().isNotEqualTo("Невідомо");

        List<OrderCommentResponse> comments = orderFixture.getComments(REQUESTER, order.getId());
        assertThat(comments)
                .filteredOn(comment -> text.equals(comment.getText()))
                .singleElement()
                .satisfies(comment -> {
                    assertThat(comment.getAuthorName()).isEqualTo(usernameOnlyAuthor.username());
                    assertThat(comment.getCreatedAt()).isNotNull();
                });
    }

    @Test(priority = 16)
    @TestCaseId("TC-ORD-046")
    @Story("Unread comments per user")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Чужий коментар позначається непрочитаним, власний — ні; list/detail/comments і unreadComments filter узгоджені для кожного користувача.")
    public void foreignCommentsAreUnreadButOwnCommentsAreNot() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        String requesterText = "requester own comment " + order.getId();
        String otherText = "foreign unread comment " + order.getId();
        OrderCommentResponse requesterComment = orderFixture.addComment(
                REQUESTER, order.getId(), requesterText);
        OrderCommentResponse otherComment = orderFixture.addComment(
                USERNAME_ONLY_AUTHOR, order.getId(), otherText);

        OrderResponse requesterRow = findOrder(pageFor(REQUESTER, false), order.getId());
        assertThat(requesterRow.getUnreadCommentsCount()).isEqualTo(1);
        assertThat(pageFor(REQUESTER, true).getContent())
                .extracting(OrderResponse::getId)
                .contains(order.getId());

        OrderResponse requesterView = orderFixture.getById(REQUESTER, order.getId());
        assertThat(requesterView.getUnreadCommentsCount()).isEqualTo(1);
        assertCommentUnread(requesterView.getComments(), requesterComment.getId(), false);
        assertCommentUnread(requesterView.getComments(), otherComment.getId(), true);

        List<OrderCommentResponse> requesterComments = orderFixture.getComments(REQUESTER, order.getId());
        assertCommentUnread(requesterComments, requesterComment.getId(), false);
        assertCommentUnread(requesterComments, otherComment.getId(), true);

        OrderResponse authorView = orderFixture.getById(USERNAME_ONLY_AUTHOR, order.getId());
        assertThat(authorView.getUnreadCommentsCount()).isEqualTo(1);
        assertCommentUnread(authorView.getComments(), requesterComment.getId(), true);
        assertCommentUnread(authorView.getComments(), otherComment.getId(), false);
    }

    @Test(priority = 17)
    @TestCaseId("TC-ORD-047")
    @Story("Mark comments read")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST comments/read очищає непрочитані лише поточного користувача; наступний чужий коментар знову вмикає count і filter.")
    public void markReadIsPerUserAndNewForeignCommentBecomesUnreadAgain() {
        OrderResponse order = orderFixture.createOrder(REQUESTER);
        OrderCommentResponse firstForeign = orderFixture.addComment(
                USERNAME_ONLY_AUTHOR, order.getId(), "first unread " + order.getId());
        assertThat(findOrder(pageFor(REQUESTER, false), order.getId()).getUnreadCommentsCount())
                .isEqualTo(1);

        orderFixture.markCommentsRead(REQUESTER, order.getId());

        OrderResponse readView = orderFixture.getById(REQUESTER, order.getId());
        assertThat(readView.getUnreadCommentsCount()).isZero();
        assertCommentUnread(readView.getComments(), firstForeign.getId(), false);
        assertThat(pageFor(REQUESTER, true).getContent())
                .extracting(OrderResponse::getId)
                .doesNotContain(order.getId());

        OrderCommentResponse requesterReply = orderFixture.addComment(
                REQUESTER, order.getId(), "requester reply " + order.getId());
        OrderResponse otherUserView = orderFixture.getById(USERNAME_ONLY_AUTHOR, order.getId());
        assertThat(otherUserView.getUnreadCommentsCount()).isEqualTo(1);
        assertCommentUnread(otherUserView.getComments(), firstForeign.getId(), false);
        assertCommentUnread(otherUserView.getComments(), requesterReply.getId(), true);

        OrderCommentResponse secondForeign = orderFixture.addComment(
                USERNAME_ONLY_AUTHOR, order.getId(), "new after read " + order.getId());
        OrderResponse requesterView = orderFixture.getById(REQUESTER, order.getId());
        assertThat(requesterView.getUnreadCommentsCount()).isEqualTo(1);
        assertCommentUnread(requesterView.getComments(), firstForeign.getId(), false);
        assertCommentUnread(requesterView.getComments(), requesterReply.getId(), false);
        assertCommentUnread(requesterView.getComments(), secondForeign.getId(), true);
        assertThat(pageFor(REQUESTER, true).getContent())
                .extracting(OrderResponse::getId)
                .contains(order.getId());
    }

    private PagedOrderResponse pageFor(UserRole role, boolean unreadOnly) {
        Map<String, Object> query = new java.util.HashMap<>();
        query.put("storageIds", requesterStorageId);
        query.put("page", 0);
        query.put("size", 50);
        if (unreadOnly) {
            query.put("unreadComments", true);
        }
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ORDER_GET_PAGE, role, query);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.as(PagedOrderResponse.class);
    }

    private static OrderResponse findOrder(PagedOrderResponse page, Long orderId) {
        return page.getContent().stream()
                .filter(order -> orderId.equals(order.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Order " + orderId + " is absent from page"));
    }

    private static void assertCommentUnread(List<OrderCommentResponse> comments,
                                            Long commentId,
                                            boolean expectedUnread) {
        assertThat(comments)
                .filteredOn(comment -> commentId.equals(comment.getId()))
                .singleElement()
                .extracting(OrderCommentResponse::getUnread)
                .isEqualTo(expectedUnread);
    }
}
