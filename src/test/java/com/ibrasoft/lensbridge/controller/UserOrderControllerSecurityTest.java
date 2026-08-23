package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.service.UserOrderService;
import com.ibrasoft.lensbridge.service.UserService;
import com.ibrasoft.lensbridge.service.UserTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for the tCketManage user-facing endpoints on {@link UserOrderController}.
 *
 * <p>These verify that the authorization model works as designed:
 * <ul>
 *   <li>Anonymous callers are rejected (401).</li>
 *   <li>Authenticated users without the USER role are rejected (403).</li>
 *   <li>Users with the USER role are authorized.</li>
 *   <li>Ticketing operator roles (which do NOT include USER) cannot reach these endpoints.</li>
 * </ul>
 *
 * <p>Business logic (ownership enforcement, order creation) is out of scope — those are
 * tested at the service layer. These tests exercise only the security filter chain and
 * method-level {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = UserOrderController.class)
@Import(MethodSecurityTestConfig.class)
@TestPropertySource(properties = "tcketmanage.enabled=true")
class UserOrderControllerSecurityTest {

    private static final String BASE = "/api/user";
    private static final String VALID_ORDER_JSON = """
            {"eventId":"00000000-0000-0000-0000-000000000001","items":[{"ticketTypeId":"00000000-0000-0000-0000-000000000002","attendeeFirstName":"Jane","attendeeLastName":"Doe","attendeeEmail":"jane@example.com"}]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserOrderService userOrderService;

    @MockitoBean
    private UserTicketService userTicketService;

    @MockitoBean
    private UserService userService;

    @BeforeEach
    void stubUserLookup() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("u@example.com");
        when(userService.findByEmail(anyString())).thenReturn(Optional.of(user));
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private void expectForbidden(MockHttpServletRequestBuilder request, Role... roles) throws Exception {
        mockMvc.perform(request.with(TestAuthorities.as(roles))).andExpect(status().isForbidden());
    }

    private void expectAllowed(MockHttpServletRequestBuilder request, Role... roles) throws Exception {
        mockMvc.perform(request.with(TestAuthorities.as(roles))).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s == 429) {
                throw new AssertionError("Rate limited (429); authorization was never exercised");
            }
            if (s == 401 || s == 403) {
                throw new AssertionError("Expected " + java.util.Arrays.toString(roles) + " to be authorized but got HTTP " + s);
            }
        });
    }

    // ==================== Anonymous ====================

    @Nested
    class Anonymous {
        @Test
        @WithAnonymousUser
        void cannotListOrders() throws Exception {
            mockMvc.perform(get(BASE + "/orders")).andExpect(status().isUnauthorized());
        }

        @Test
        @WithAnonymousUser
        void cannotGetOrder() throws Exception {
            mockMvc.perform(get(BASE + "/orders/" + id())).andExpect(status().isUnauthorized());
        }

        @Test
        @WithAnonymousUser
        void cannotCreateOrder() throws Exception {
            mockMvc.perform(post(BASE + "/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_ORDER_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithAnonymousUser
        void cannotCancelOrder() throws Exception {
            mockMvc.perform(post(BASE + "/orders/" + id() + "/cancel"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithAnonymousUser
        void cannotListTickets() throws Exception {
            mockMvc.perform(get(BASE + "/tickets")).andExpect(status().isUnauthorized());
        }
    }

    // ==================== USER role: authorized ====================

    @Nested
    class UserRole {
        @Test
        void canListOrders() throws Exception {
            expectAllowed(get(BASE + "/orders"), Role.USER);
        }

        @Test
        void canGetOrder() throws Exception {
            expectAllowed(get(BASE + "/orders/" + id()), Role.USER);
        }

        @Test
        void canCreateOrder() throws Exception {
            expectAllowed(post(BASE + "/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_ORDER_JSON), Role.USER);
        }

        @Test
        void canCancelOrder() throws Exception {
            expectAllowed(post(BASE + "/orders/" + id() + "/cancel"), Role.USER);
        }

        @Test
        void canListTickets() throws Exception {
            expectAllowed(get(BASE + "/tickets"), Role.USER);
        }
    }

    // ==================== Ticketing operator roles: no access ====================
    //
    // TCKET_SCANNER and TCKET_MANAGER are operator roles for running events. They do NOT
    // include USER, and these are user self-service endpoints. An operator who also buys
    // tickets has both roles on their account; a volunteer scanning at the door has only
    // TCKET_SCANNER and must not be able to purchase through someone else's session.

    @Nested
    class TicketOperatorRoles {
        @Test
        void scannerCannotListOrders() throws Exception {
            expectForbidden(get(BASE + "/orders"), Role.TCKET_SCANNER);
        }

        @Test
        void scannerCannotCreateOrder() throws Exception {
            expectForbidden(post(BASE + "/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_ORDER_JSON), Role.TCKET_SCANNER);
        }

        @Test
        void managerCannotListOrders() throws Exception {
            expectForbidden(get(BASE + "/orders"), Role.TCKET_MANAGER);
        }

        @Test
        void managerCannotCreateOrder() throws Exception {
            expectForbidden(post(BASE + "/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_ORDER_JSON), Role.TCKET_MANAGER);
        }

        @Test
        void tcketAdminCannotListOrders() throws Exception {
            expectForbidden(get(BASE + "/orders"), Role.TCKET_ADMIN);
        }
    }

    // ==================== Board roles: no access ====================

    @Nested
    class BoardRoles {
        @Test
        void boardViewerCannotListOrders() throws Exception {
            expectForbidden(get(BASE + "/orders"), Role.BOARD_VIEWER);
        }

        @Test
        void boardEditorCannotCreateOrder() throws Exception {
            expectForbidden(post(BASE + "/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_ORDER_JSON), Role.BOARD_EDITOR);
        }

        @Test
        void boardAdminCannotListTickets() throws Exception {
            expectForbidden(get(BASE + "/tickets"), Role.BOARD_ADMIN);
        }
    }

    // ==================== ROOT alone: no access ====================
    //
    // ROOT expands to all *permissions* but does not carry ROLE_USER. These endpoints
    // check hasRole('USER'), not a permission. A root account that also needs to buy
    // tickets would carry both roles on the User entity; ROOT alone is the admin
    // identity, not a buyer identity.

    @Nested
    class RootRoleAlone {
        @Test
        void rootAloneCannotListOrders() throws Exception {
            expectForbidden(get(BASE + "/orders"), Role.ROOT);
        }

        @Test
        void rootAloneCannotCreateOrder() throws Exception {
            expectForbidden(post(BASE + "/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_ORDER_JSON), Role.ROOT);
        }

        @Test
        void rootAloneCannotCancelOrder() throws Exception {
            expectForbidden(post(BASE + "/orders/" + id() + "/cancel"), Role.ROOT);
        }

        @Test
        void rootAloneCannotListTickets() throws Exception {
            expectForbidden(get(BASE + "/tickets"), Role.ROOT);
        }
    }

    // ==================== ROOT + USER: authorized ====================

    @Nested
    class RootPlusUser {
        @Test
        void rootWithUserCanListOrders() throws Exception {
            expectAllowed(get(BASE + "/orders"), Role.ROOT, Role.USER);
        }

        @Test
        void rootWithUserCanCreateOrder() throws Exception {
            expectAllowed(post(BASE + "/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_ORDER_JSON), Role.ROOT, Role.USER);
        }
    }

    // ==================== Bare permission without role: forbidden ====================
    //
    // Having TCKET_MANAGE (the permission) without a role that bundles USER must not
    // grant access. This catches a regression where someone replaces hasRole(USER) with
    // hasAuthority(tcket:manage) on these endpoints.

    @Nested
    class BarePermissions {
        @Test
        void tcketManagePermissionAloneCannotListOrders() throws Exception {
            mockMvc.perform(get(BASE + "/orders")
                    .with(TestAuthorities.asHolderOf(Permission.TCKET_MANAGE)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void tcketManagePermissionAloneCannotCreateOrder() throws Exception {
            mockMvc.perform(post(BASE + "/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_ORDER_JSON)
                    .with(TestAuthorities.asHolderOf(Permission.TCKET_MANAGE)))
                    .andExpect(status().isForbidden());
        }
    }
}
