package com.erp.utils.helpers;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

public class GoogleSheetsHelper {
    private static final String APPLICATION_NAME = "API Test Framework";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String CREDENTIALS_FILE_PATH = "src/test/resources/credentials/google-credentials.json";

    private Sheets sheetsService;
    private String spreadsheetId;

    public GoogleSheetsHelper(String spreadsheetId) throws GeneralSecurityException, IOException {
        this.spreadsheetId = spreadsheetId;
        this.sheetsService = getSheetsService();
    }

    private Sheets getSheetsService() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleCredential credential = GoogleCredential.fromStream(
                        new FileInputStream(CREDENTIALS_FILE_PATH))
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

        return new Sheets.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public void appendRow(List<Object> values) throws IOException {
        ValueRange body = new ValueRange()
                .setValues(Collections.singletonList(values));

        sheetsService.spreadsheets().values()
                .append(spreadsheetId, "Sheet1!A:I", body)
                .setValueInputOption("RAW")
                .execute();
    }

    public void updateRow(String range, List<Object> values) throws IOException {
        ValueRange body = new ValueRange()
                .setValues(Collections.singletonList(values));

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute();
    }

    public List<List<Object>> readSheet(String range) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        return response.getValues();
    }
}