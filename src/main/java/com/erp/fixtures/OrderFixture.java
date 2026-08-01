package com.erp.fixtures;

import com.erp.api.clients.ApiExecutor;
import com.erp.api.endpoints.ApiEndpointDefinition;
import com.erp.data.factories.order.OrderDataFactory;
import com.erp.enums.BookingState;
import com.erp.enums.UserRole;
import com.erp.models.request.BookingRequest;
import com.erp.models.request.GatheringStorageRequest;
import com.erp.models.request.OrderCommentRequest;
import com.erp.models.request.OrderRequest;
import com.erp.models.request.PreparedRequest;
import com.erp.models.request.RelocationOutputRequest;
import com.erp.models.response.BookingResponse;
import com.erp.models.response.OrderAvailabilityResponse;
import com.erp.models.response.OrderCommentResponse;
import com.erp.models.response.OrderResponse;
import com.erp.models.response.PagedOrderResponse;
import com.erp.models.response.RelocationResponse;
import com.erp.models.response.ResourceResponse;
import com.erp.models.response.SimpleEntityResponse;
import com.erp.test_context.ContextKey;
import com.erp.test_context.TestContext;
import com.erp.utils.config.ConfigProvider;
import com.erp.utils.helpers.DatabaseHelper;
import com.erp.validators.SchemaRegistry;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.SkipException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class OrderFixture extends BaseFixture {

    private static final double DEFAULT_SEED_STOCK = 200.0;

    private final RelocationFixture relocationFixture;
    private Consumer<Long> createdOrderRegistrar;

    public OrderFixture(TestContext testContext, ApiExecutor apiExecutor) {
        super(testContext, apiExecutor);
        this.relocationFixture = new RelocationFixture(testContext, apiExecutor);
    }

    public OrderFixture withCreatedOrderRegistrar(Consumer<Long> registrar) {
        this.createdOrderRegistrar = registrar;
        return this;
    }

    public RelocationFixture relocation() {
        return relocationFixture;
    }

    @Step("FIXTURE: Підготовка середовища для тестів замовлень")
    public void prepareContext() {
        if (testContext.get(ContextKey.ORDER_RESOURCE_ID) != null) {
            return;
        }
        fetchSharedUnit(3);
        fetchSharedResourceCategory();
        setupSharedResourceList(3);

        Long requesterStorage = ConfigProvider.getOwner1StorageId();
        Long gatheringStorage = ConfigProvider.getOrderGatheringStorageId();
        testContext.set(ContextKey.ORDER_REQUESTER_STORAGE_ID, requesterStorage);
        testContext.set(ContextKey.ORDER_GATHERING_STORAGE_ID, gatheringStorage);
        testContext.set(ContextKey.OWNER_1_STORAGE_ID, requesterStorage);
        testContext.set(ContextKey.OWNER_2_STORAGE_ID, ConfigProvider.getOwner2StorageId());

        List<ResourceResponse> resources = testContext.get(ContextKey.SHARED_AVAILABLE_RESOURCES);
        Long resourceId = resources.getFirst().getId();
        testContext.set(ContextKey.ORDER_RESOURCE_ID, resourceId);

        relocationFixture.ensureStock(gatheringStorage, resourceId, DEFAULT_SEED_STOCK);
        log.info("Order fixture ready: requester={}, gathering={} ({}), resource={}",
                requesterStorage, gatheringStorage, ConfigProvider.getOrderGatheringUsername(), resourceId);
    }

    /**
     * Upserts global {@code app_config.order_availability_root_storage} when
     * {@code order.availability.root.storage.id} &gt; 0 and JDBC is available.
     */
    @Step("DB: ensure order_availability_root_storage={rootId}")
    public void ensureAvailabilityRootConfig(DatabaseHelper dbHelper) {
        long rootId = ConfigProvider.getOrderAvailabilityRootStorageId();
        if (dbHelper == null || rootId <= 0) {
            log.info("Skip order_availability_root_storage upsert (db={}, rootId={})",
                    dbHelper != null, rootId);
            return;
        }
        String json = "[{\"name\": \"storageId\", \"values\": [\"" + rootId + "\"]}]";
        Connection connection = dbHelper.getConnection();
        try {
            int updated;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE app_config SET value = ?::jsonb "
                            + "WHERE name = 'order_availability_root_storage' AND username IS NULL")) {
                update.setString(1, json);
                updated = update.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO app_config (name, value, username) VALUES "
                                + "('order_availability_root_storage', ?::jsonb, NULL)")) {
                    insert.setString(1, json);
                    insert.executeUpdate();
                }
            }
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT value::text FROM app_config "
                            + "WHERE name = 'order_availability_root_storage' AND username IS NULL");
                 ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    log.info("order_availability_root_storage = {}", rs.getString(1));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to upsert order_availability_root_storage=" + rootId + ": " + e.getMessage(), e);
        }
    }

    @Step("API: POST create order")
    public OrderResponse createOrder(UserRole role, OrderRequest request) {
        Response response = apiExecutor.execute(ApiEndpointDefinition.ORDER_POST_CREATE, role, request);
        validateSuccess(response, "Create order");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_POST_CREATE);
        OrderResponse order = response.as(OrderResponse.class);
        trackOrder(order.getId());
        return order;
    }

    @Step("API: POST create order (requester storage + single line)")
    public OrderResponse createOrder(UserRole role, Long storageId, Long resourceId, double quantity) {
        return createOrder(role, OrderDataFactory.buildOrderRequest(storageId, resourceId, quantity));
    }

    @Step("API: POST create order from shared context")
    public OrderResponse createOrder(UserRole role) {
        Long storageId = requireRequesterStorageId();
        Long resourceId = requireResourceId();
        return createOrder(role, storageId, resourceId, 5.0);
    }

    @Step("API: PUT take order {orderId} to work")
    public OrderResponse takeToWork(UserRole role, Long orderId, Long requesterStorageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_TAKE_TO_WORK,
                role,
                null,
                orderId,
                requesterStorageId);
        validateSuccess(response, "Take order to work");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_PUT_TAKE_TO_WORK);
        return response.as(OrderResponse.class);
    }

    @Step("API: GET gathering location candidates for order {orderId}")
    public List<SimpleEntityResponse> getGatheringLocations(UserRole role, Long orderId, Long requesterStorageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_GET_GATHERING_LOCATIONS,
                role,
                null,
                orderId,
                requesterStorageId);
        validateSuccess(response, "Get gathering locations");
        List<SimpleEntityResponse> locations = response.jsonPath().getList("", SimpleEntityResponse.class);
        return locations == null ? List.of() : locations;
    }

    /**
     * Prefer configured owner2 gathering if API lists it; otherwise first candidate ≠ requester.
     */
    @Step("Resolve gathering storage in availability scope for order {orderId}")
    public Long resolveGatheringStorageId(UserRole manager, Long orderId, Long requesterStorageId) {
        List<SimpleEntityResponse> candidates = getGatheringLocations(manager, orderId, requesterStorageId);
        if (candidates.isEmpty()) {
            throw new SkipException(
                    "No gathering-location candidates for order " + orderId
                            + " — configure order_availability_root_storage with active STORAGE/PRODUCTION ≠ requester");
        }
        Long preferred = testContext.get(ContextKey.ORDER_GATHERING_STORAGE_ID);
        if (preferred == null) {
            preferred = testContext.get(ContextKey.OWNER_2_STORAGE_ID);
        }
        Long preferredId = preferred;
        return candidates.stream()
                .map(SimpleEntityResponse::getId)
                .filter(id -> id != null && !id.equals(requesterStorageId))
                .filter(id -> preferredId != null && preferredId.equals(id))
                .findFirst()
                .or(() -> candidates.stream()
                        .map(SimpleEntityResponse::getId)
                        .filter(id -> id != null && !id.equals(requesterStorageId))
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "Gathering candidates only include requester storage " + requesterStorageId
                                + "; candidates=" + candidates.stream().map(SimpleEntityResponse::getId).toList()));
    }

    @Step("API: PUT set gathering storage for order {orderId}")
    public OrderResponse setGathering(UserRole role,
                                      Long orderId,
                                      Long requesterStorageId,
                                      Long gatheringStorageId) {
        GatheringStorageRequest request = OrderDataFactory.buildGatheringStorageRequest(gatheringStorageId);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_GATHERING_STORAGE,
                role,
                request,
                orderId,
                requesterStorageId);
        validateSuccess(response, "Set order gathering storage");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_PUT_GATHERING_STORAGE);
        testContext.set(ContextKey.ORDER_GATHERING_STORAGE_ID, gatheringStorageId);
        return response.as(OrderResponse.class);
    }

    @Step("API: POST book resource for order {orderId}")
    public BookingResponse book(UserRole role,
                                Long orderId,
                                Long requesterStorageId,
                                BookingRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_BOOKING,
                role,
                request,
                orderId,
                requesterStorageId);
        validateSuccess(response, "Book order resource");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_POST_BOOKING);
        BookingResponse booking = response.as(BookingResponse.class);
        testContext.set(ContextKey.ORDER_BOOKING_ID, booking.getId());
        return booking;
    }

    @Step("API: POST book resource for order {orderId}")
    public BookingResponse book(UserRole role,
                                Long orderId,
                                Long requesterStorageId,
                                Long resourceId,
                                double amount) {
        return book(role, orderId, requesterStorageId,
                OrderDataFactory.buildBookingRequest(resourceId, amount));
    }

    @Step("API: DELETE release booking {bookingId} for order {orderId}")
    public void releaseBooking(UserRole role,
                               Long orderId,
                               Long bookingId,
                               Long requesterStorageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_DELETE_BOOKING,
                role,
                null,
                orderId,
                bookingId,
                requesterStorageId);
        validateSuccess(response, "Release order booking");
    }

    @Step("API: PUT set prepared={prepared} for booking {bookingId}")
    public BookingResponse setPrepared(UserRole role,
                                       Long orderId,
                                       Long bookingId,
                                       boolean prepared) {
        PreparedRequest request = OrderDataFactory.buildPreparedRequest(prepared);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_BOOKING_PREPARED,
                role,
                request,
                orderId,
                bookingId);
        validateSuccess(response, "Set booking prepared");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_PUT_BOOKING_PREPARED);
        return response.as(BookingResponse.class);
    }

    @Step("API: PUT set prepared={prepared} for all bookings")
    public List<BookingResponse> setAllPrepared(UserRole role, Long orderId, boolean prepared) {
        PreparedRequest request = OrderDataFactory.buildPreparedRequest(prepared);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_BOOKINGS_PREPARED,
                role,
                request,
                orderId);
        validateSuccess(response, "Set all bookings prepared");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_PUT_BOOKINGS_PREPARED);
        return response.jsonPath().getList("", BookingResponse.class);
    }

    @Step("API: PUT mark order {orderId} done")
    public OrderResponse markDone(UserRole role, Long orderId, Long requesterStorageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_MARK_DONE,
                role,
                null,
                orderId,
                requesterStorageId);
        validateSuccess(response, "Mark order done");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_PUT_MARK_DONE);
        return response.as(OrderResponse.class);
    }

    @Step("API: PUT cancel order {orderId}")
    public OrderResponse cancel(UserRole role, Long orderId, Long requesterStorageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_CANCEL,
                role,
                null,
                orderId,
                requesterStorageId);
        validateSuccess(response, "Cancel order");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_PUT_CANCEL);
        return response.as(OrderResponse.class);
    }

    /**
     * Cancels IN_PROGRESS orders on the given storages so ACTIVE booking holds
     * do not pollute shared gathering stock between tests.
     */
    @Step("Clear IN_PROGRESS orders (release holds) on storages {storageIds}")
    public void clearInProgressOrders(UserRole manager, Long... storageIds) {
        if (storageIds == null) {
            return;
        }
        for (Long storageId : storageIds) {
            if (storageId == null) {
                continue;
            }
            int pageNumber = 0;
            int cancelledOnStorage = 0;
            while (pageNumber < 20) {
                int cancelledBeforePage = cancelledOnStorage;
                Response response = apiExecutor.executeWithQueryParams(
                        ApiEndpointDefinition.ORDER_GET_PAGE,
                        manager,
                        Map.of(
                                "storageIds", storageId,
                                "states", "IN_PROGRESS",
                                "page", 0,
                                "size", 100));
                if (response.statusCode() != 200) {
                    log.warn("Skip hold cleanup for storage {}: GET orders status={}",
                            storageId, response.statusCode());
                    break;
                }
                PagedOrderResponse page = response.as(PagedOrderResponse.class);
                List<OrderResponse> content = page.getContent() == null ? List.of() : page.getContent();
                if (content.isEmpty()) {
                    break;
                }
                for (OrderResponse order : content) {
                    if (order.getId() == null) {
                        continue;
                    }
                    Long cancelStorageId = order.getStorage() != null && order.getStorage().getId() != null
                            ? order.getStorage().getId()
                            : storageId;
                    releaseActiveBookingsQuietly(manager, order.getId(), cancelStorageId);
                    try {
                        Response cancelResponse = apiExecutor.execute(
                                ApiEndpointDefinition.ORDER_PUT_CANCEL,
                                manager,
                                null,
                                order.getId(),
                                cancelStorageId);
                        if (cancelResponse.statusCode() >= 400) {
                            log.warn("Could not cancel order {} on storage {}: status={} body={}",
                                    order.getId(), cancelStorageId, cancelResponse.statusCode(),
                                    cancelResponse.body().asString());
                        } else {
                            cancelledOnStorage++;
                            log.info("Cancelled IN_PROGRESS order {} (storage {}) to clear holds",
                                    order.getId(), cancelStorageId);
                        }
                    } catch (Exception e) {
                        log.warn("Could not cancel order {}: {}", order.getId(), e.getMessage());
                    }
                }
                if (cancelledOnStorage == cancelledBeforePage) {
                    log.warn("Hold cleanup stalled on storage {} ({} still IN_PROGRESS)",
                            storageId, content.size());
                    break;
                }
                if (content.size() < 100) {
                    break;
                }
                pageNumber++;
                if (cancelledOnStorage > 500) {
                    log.warn("Stop hold cleanup for storage {} after {} cancels", storageId, cancelledOnStorage);
                    break;
                }
            }
        }
    }

    private void releaseActiveBookingsQuietly(UserRole manager, Long orderId, Long requesterStorageId) {
        try {
            Response response = apiExecutor.execute(
                    ApiEndpointDefinition.ORDER_GET_BOOKINGS, manager, null, orderId);
            if (response.statusCode() != 200) {
                return;
            }
            List<BookingResponse> bookings = response.jsonPath().getList("", BookingResponse.class);
            if (bookings == null) {
                return;
            }
            for (BookingResponse booking : bookings) {
                if (booking.getId() == null || booking.getState() != BookingState.ACTIVE) {
                    continue;
                }
                apiExecutor.execute(
                        ApiEndpointDefinition.ORDER_DELETE_BOOKING,
                        manager,
                        null,
                        orderId,
                        booking.getId(),
                        requesterStorageId);
            }
        } catch (Exception e) {
            log.debug("Could not release bookings for order {}: {}", orderId, e.getMessage());
        }
    }

    @Step("API: POST ship order {orderId} via relocation send")
    public RelocationResponse shipOrder(UserRole role,
                                        Long orderId,
                                        Long gatheringStorageId,
                                        Long requesterStorageId,
                                        Long resourceId,
                                        double amount) {
        RelocationOutputRequest request = OrderDataFactory.buildShipRequest(
                orderId, gatheringStorageId, requesterStorageId, resourceId, amount);
        Response response = apiExecutor.execute(ApiEndpointDefinition.RELOCATION_POST_SEND, role, request);
        validateSuccess(response, "Ship order via relocation send");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.RELOCATION_POST_SEND);
        return response.as(RelocationResponse.class);
    }

    @Step("API: GET order {orderId}")
    public OrderResponse getById(UserRole role, Long orderId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_GET_BY_ID, role, null, orderId);
        validateSuccess(response, "Get order by id");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_GET_BY_ID);
        return response.as(OrderResponse.class);
    }

    @Step("API: GET orders page for storage {storageId}")
    public PagedOrderResponse getPage(UserRole role, Long storageId) {
        Response response = apiExecutor.executeWithQueryParams(
                ApiEndpointDefinition.ORDER_GET_PAGE,
                role,
                Map.of("storageIds", storageId, "page", 0, "size", 50));
        validateSuccess(response, "Get orders page");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_GET_PAGE);
        return response.as(PagedOrderResponse.class);
    }

    @Step("API: POST add comment to order {orderId}")
    public OrderCommentResponse addComment(UserRole role, Long orderId, String text) {
        OrderCommentRequest request = OrderDataFactory.buildCommentRequest(text);
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_POST_COMMENT, role, request, orderId);
        validateSuccess(response, "Add order comment");
        return response.as(OrderCommentResponse.class);
    }

    @Step("API: PUT update order {orderId}")
    public OrderResponse updateOrder(UserRole role, Long orderId, OrderRequest request) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_PUT_UPDATE, role, request, orderId);
        validateSuccess(response, "Update order");
        SchemaRegistry.validateIfSuccess(response, ApiEndpointDefinition.ORDER_PUT_UPDATE);
        return response.as(OrderResponse.class);
    }

    @Step("API: GET bookings for order {orderId}")
    public List<BookingResponse> getBookings(UserRole role, Long orderId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_GET_BOOKINGS, role, null, orderId);
        validateSuccess(response, "Get order bookings");
        List<BookingResponse> bookings = response.jsonPath().getList("", BookingResponse.class);
        return bookings == null ? List.of() : bookings;
    }

    @Step("API: GET availability for order {orderId}")
    public List<OrderAvailabilityResponse> getAvailability(UserRole role,
                                                         Long orderId,
                                                         Long requesterStorageId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_GET_AVAILABILITY,
                role,
                null,
                orderId,
                requesterStorageId);
        validateSuccess(response, "Get order availability");
        List<OrderAvailabilityResponse> availability = response.jsonPath().getList("", OrderAvailabilityResponse.class);
        return availability == null ? List.of() : availability;
    }

    @Step("API: GET comments for order {orderId}")
    public List<OrderCommentResponse> getComments(UserRole role, Long orderId) {
        Response response = apiExecutor.execute(
                ApiEndpointDefinition.ORDER_GET_COMMENTS, role, null, orderId);
        validateSuccess(response, "Get order comments");
        List<OrderCommentResponse> comments = response.jsonPath().getList("", OrderCommentResponse.class);
        return comments == null ? List.of() : comments;
    }

    /**
     * Create as {@code requester}, then Admin manages lifecycle: take-to-work + set gathering.
     * Product model: Owner has create/update; only Administrator has {@code order::manage}.
     */
    @Step("Prepare IN_PROGRESS order with gathering (requester creates, ADMIN manages)")
    public OrderResponse prepareInProgressWithGathering(UserRole requester) {
        return prepareInProgressWithGathering(requester, UserRole.ADMIN);
    }

    @Step("Prepare IN_PROGRESS order with gathering (requester={requester}, manager={manager})")
    public OrderResponse prepareInProgressWithGathering(UserRole requester, UserRole manager) {
        return prepareInProgressWithGathering(requester, manager, 5.0);
    }

    @Step("Prepare IN_PROGRESS order qty={quantity} with gathering (requester={requester}, manager={manager})")
    public OrderResponse prepareInProgressWithGathering(UserRole requester,
                                                        UserRole manager,
                                                        double quantity) {
        Long requesterId = requireRequesterStorageId();
        Long resourceId = requireResourceId();
        OrderResponse order = createOrder(requester, requesterId, resourceId, quantity);
        order = takeToWork(manager, order.getId(), requesterId);
        Long gatheringId = resolveGatheringStorageId(manager, order.getId(), requesterId);
        relocationFixture.ensureStock(gatheringId, resourceId, DEFAULT_SEED_STOCK);
        return setGathering(manager, order.getId(), requesterId, gatheringId);
    }

    private void trackOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        testContext.set(ContextKey.ORDER_ID, orderId);
        testContext.set(ContextKey.SHARED_ORDER_ID, orderId);
        if (createdOrderRegistrar != null) {
            createdOrderRegistrar.accept(orderId);
        }
    }

    private Long requireRequesterStorageId() {
        Long storageId = testContext.get(ContextKey.ORDER_REQUESTER_STORAGE_ID);
        if (storageId == null) {
            storageId = testContext.get(ContextKey.OWNER_1_STORAGE_ID);
        }
        if (storageId == null) {
            throw new IllegalStateException("Requester storage id is not set in test context");
        }
        return storageId;
    }

    private Long requireResourceId() {
        Long resourceId = testContext.get(ContextKey.ORDER_RESOURCE_ID);
        if (resourceId == null) {
            resourceId = testContext.get(ContextKey.SHARED_RESOURCE_ID);
        }
        if (resourceId == null) {
            throw new IllegalStateException("Order resource id is not set in test context");
        }
        return resourceId;
    }
}
