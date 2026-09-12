package com.erp.tests.framework;

import com.erp.annotations.TestCaseId;
import com.erp.data.BusinessRoleCatalog;
import com.erp.enums.BusinessRole;
import com.erp.enums.LocationProfile;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BusinessRoleCatalogTest {

    @Test
    public void catalogDefinesEveryBusinessRole() {
        Map<BusinessRole, BusinessRoleCatalog.Definition> definitions = BusinessRoleCatalog.definitions();

        assertThat(definitions).containsOnlyKeys(BusinessRole.values());
        assertThat(definitions.values())
                .allSatisfy(definition -> assertThat(definition.keycloakRoles())
                        .isNotEmpty()
                        .doesNotHaveDuplicates()
                        .allSatisfy(role -> assertThat(role).isNotBlank()));
    }

    @Test
    public void unitKomirnikMappingIsCentralized() {
        assertThat(BusinessRoleCatalog.definition(BusinessRole.UNIT_KOMIRNIK).keycloakRoles())
                .containsExactly("Unit_Owner-ROLE", "Crew-Manager-ROLE");
    }

    @Test
    public void testCaseMetadataCarriesBusinessRoles() throws NoSuchMethodException {
        Method method = ExampleCase.class.getDeclaredMethod("warehouseOperation");

        assertThat(method.getAnnotation(TestCaseId.class).roles())
                .containsExactly(BusinessRole.UNIT_KOMIRNIK);
        assertThat(method.getAnnotation(TestCaseId.class).locationProfiles())
                .containsExactly(LocationProfile.BATTALION_UNIT);
    }

    private static class ExampleCase {
        @TestCaseId(
                value = "EXAMPLE",
                roles = BusinessRole.UNIT_KOMIRNIK,
                locationProfiles = LocationProfile.BATTALION_UNIT)
        void warehouseOperation() {
        }
    }
}
