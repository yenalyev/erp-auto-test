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
                .allSatisfy(definition -> {
                    assertThat(definition.accessRoles()).doesNotHaveDuplicates();
                    assertThat(definition.permissionKeys()).doesNotHaveDuplicates();
                });
    }

    @Test
    public void businessUnitOwnerNeedsNoAdditionalGrant() {
        BusinessRoleCatalog.Definition definition =
                BusinessRoleCatalog.definition(BusinessRole.BUSINESS_UNIT_OWNER);

        assertThat(definition.accessRoles()).isEmpty();
        assertThat(definition.permissionKeys()).isEmpty();
    }

    @Test
    public void unitKomirnikMappingIsCentralized() {
        assertThat(BusinessRoleCatalog.definition(BusinessRole.UNIT_KOMIRNIK).accessRoles())
                .containsExactly("Екіпажі: перегляд");
    }

    @Test
    public void projectOwnerMappingIncludesBothRoles() {
        assertThat(BusinessRoleCatalog.definition(BusinessRole.BUSINESS_UNIT_AND_PROJECT_OWNER).accessRoles())
                .containsExactly("Проєктне виробництво: редактор");
    }

    @Test
    public void regularActorsReceiveLocationHeadBeforeAdditionalRoles() {
        assertThat(BusinessRoleCatalog.effectiveAccessRoles(BusinessRole.ORDER_ADMIN))
                .containsExactly("Керівник локації", "Замовлення: адміністратор");
        assertThat(BusinessRoleCatalog.effectiveAccessRoles(BusinessRole.CREW_STOCK_READER))
                .containsExactly("Керівник локації", "Екіпажі: перегляд");
        assertThat(BusinessRoleCatalog.effectiveAccessRoles(BusinessRole.CREW_INVENTORY_OPERATOR))
                .containsExactly("Керівник локації", "Екіпажі: облік");
        assertThat(BusinessRoleCatalog.effectiveAccessRoles(BusinessRole.BUSINESS_UNIT_OWNER))
                .containsExactly("Керівник локації");
    }

    @Test
    public void productionGroupDirectorUsesExactRole() {
        assertThat(BusinessRoleCatalog.definition(BusinessRole.PRODUCTION_GROUP_DIRECTOR).permissionKeys())
                .containsExactly("production-order.allocate");
    }

    @Test
    public void orderAdminMappingUsesExactDevRoleName() {
        assertThat(BusinessRoleCatalog.definition(BusinessRole.ORDER_ADMIN).accessRoles())
                .containsExactly("Замовлення: адміністратор");
    }

    @Test
    public void resourceViewerMappingUsesGlobalTrackerRoleOnly() {
        BusinessRoleCatalog.Definition definition =
                BusinessRoleCatalog.definition(BusinessRole.RESOURCE_VIEWER);

        assertThat(definition.accessRoles()).containsExactly("Відстеження ресурсів");
        assertThat(definition.permissionKeys()).isEmpty();
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
