package com.erp.api.endpoints;

/**
 * Central repository for all API endpoints
 */
public class ApiEndpoints {

    // Base paths
    private static final String BASE_APP_URL = "http://backend:8080";
    private static final String API_V1 = "/api/v1";

    //----------------------- RESOURCES --------------------------
    public static final String RESOURCE_BASE_URL = API_V1 + "/resources";
    public static final String RESOURCE_GET_ALL = RESOURCE_BASE_URL;
    public static final String RESOURCE_POST_CREATE = RESOURCE_BASE_URL;
    public static final String RESOURCE_UPDATE = RESOURCE_BASE_URL;

    //----------------------- UNIT MEASUREMENT --------------------------
    public static final String UNIT_BASE_URL = API_V1 + "/measurement-unit";
    public static final String UNIT_GET_ALL = UNIT_BASE_URL;

    //----------------------- TECHNOLOGICAL MAP --------------------------
    public static final String TECHNOLOGICAL_MAP_BASE_URL = "/technological-maps";
    public static final String TECHNOLOGICAL_MAP_GET_ALL = TECHNOLOGICAL_MAP_BASE_URL;
    public static final String TECHNOLOGICAL_MAP_POST_CREATE = TECHNOLOGICAL_MAP_BASE_URL;



}