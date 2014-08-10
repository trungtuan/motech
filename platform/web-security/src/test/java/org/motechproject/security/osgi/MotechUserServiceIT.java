package org.motechproject.security.osgi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.motechproject.security.authentication.MotechPasswordEncoder;
import org.motechproject.security.domain.MotechRole;
import org.motechproject.security.domain.MotechUser;
import org.motechproject.security.repository.MotechRolesDataService;
import org.motechproject.security.repository.MotechUsersDataService;
import org.motechproject.security.service.MotechUserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.motechproject.security.constants.UserRoleNames.USER_ADMIN_ROLE;

public class MotechUserServiceIT extends BaseIT {

    @Inject
    private MotechUserService motechUserService;

    @Inject
    private MotechUsersDataService usersDataService;

    @Inject
    private MotechRolesDataService rolesDataService;

    private MotechPasswordEncoder passwordEncoder;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        passwordEncoder = getFromContext(MotechPasswordEncoder.class);
        AuthenticationManager authenticationManager = getFromContext(AuthenticationManager.class, "authenticationManager");

        usersDataService.deleteAll();
        rolesDataService.deleteAll();

        // authorize
        rolesDataService.create(new MotechRole("IT_ADMIN", asList("addUser", "editUser", "deleteUser", "manageUser", "activateUser", "manageRole"), false));
        motechUserService.register("admin", "admin", "admin@mail.com", "", asList("IT_ADMIN"), Locale.ENGLISH);

        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken("admin", "admin");
        Authentication auth = authenticationManager.authenticate(authRequest);
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(auth);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        usersDataService.deleteAll();
        rolesDataService.deleteAll();
    }

    @Test
    public void testRegister() {
        motechUserService.register("userName", "password", "1234", "", asList("IT_ADMIN", "DB_ADMIN"), Locale.ENGLISH);
        MotechUser motechUser = usersDataService.findByUserName("userName");
        assertNotNull(motechUser);
        assertTrue(motechUser.getRoles().contains("IT_ADMIN"));
        assertTrue(motechUser.getRoles().contains("DB_ADMIN"));
    }

    @Test
    public void shouldActivateUser() {
        motechUserService.register("userName", "password", "1234", "", asList("IT_ADMIN", "DB_ADMIN"), Locale.ENGLISH, false, "");
        motechUserService.activateUser("userName");
        MotechUser motechUser = usersDataService.findByUserName("userName");
        assertTrue(motechUser.isActive());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionIfUserNameIsEmptyForRegister() {
        motechUserService.register("", "password", "ext_id", "", new ArrayList<String>(), Locale.ENGLISH);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionIfUserNameIsEmptyForRegisterWithActiveInfo() {
        motechUserService.register("", "password", "ext_id", "", new ArrayList<String>(), Locale.ENGLISH, true, "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionIfPasswordIsEmptyForRegister() {
        motechUserService.register("user", "", "ext_id", "", new ArrayList<String>(), Locale.ENGLISH);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionIfUserNameisNull() {
        motechUserService.register(null, "", "ext_id", "", new ArrayList<String>(), Locale.ENGLISH);
    }

    @Test
    public void shouldNotActivateInvalidUser() {
        motechUserService.register("userName", "password", "1234", "", asList("IT_ADMIN", "DB_ADMIN"), Locale.ENGLISH, false, "");
        motechUserService.activateUser("userName1");
        MotechUser motechUser = usersDataService.findByUserName("userName");
        assertFalse(motechUser.isActive());
    }

    @Test
    public void shouldCreateActiveUserByDefault() {
        motechUserService.register("userName", "password", "1234", "", asList("IT_ADMIN", "DB_ADMIN"), Locale.ENGLISH);
        MotechUser motechUser = usersDataService.findByUserName("userName");
        assertTrue(motechUser.isActive());
    }

    @Test
    public void shouldCreateInActiveUser() {
        motechUserService.register("userName", "password", "1234", "", asList("IT_ADMIN", "DB_ADMIN"), Locale.ENGLISH, false, "");
        MotechUser motechUser = usersDataService.findByUserName("userName");
        assertFalse(motechUser.isActive());
    }

    @Test
    public void testPasswordEncoding() {
        String plainTextPassword = "testpassword";
        motechUserService.register("testuser", plainTextPassword, "entity1", "", asList("ADMIN"), Locale.ENGLISH);
        MotechUser motechUser = usersDataService.findByUserName("testuser");
        assertTrue(passwordEncoder.isPasswordValid(motechUser.getPassword(), plainTextPassword));
    }

    @Test
    public void shouldChangePassword() {
        motechUserService.register("userName", "password", "1234", "", asList("IT_ADMIN", "DB_ADMIN"), Locale.ENGLISH);
        motechUserService.changePassword("userName", "password", "newPassword");
        MotechUser motechUser = usersDataService.findByUserName("userName");
        assertTrue(passwordEncoder.isPasswordValid(motechUser.getPassword(), "newPassword"));
    }

    @Test
    public void shouldNotChangePasswordWithoutOldPassword() {
        motechUserService.register("userName", "password", "1234", "", asList("IT_ADMIN", "DB_ADMIN"), Locale.ENGLISH);
        motechUserService.changePassword("userName", "foo", "newPassword");
        MotechUser motechUser = usersDataService.findByUserName("userName");
        assertTrue(passwordEncoder.isPasswordValid(motechUser.getPassword(), "password"));
    }

    @Test
    public void hasUserShouldReturnTrueOnlyIfUserExists() {
        assertFalse(motechUserService.hasUser("username"));
        motechUserService.register("userName", "password", "1234", "", Arrays.asList("IT_ADMIN", "DB_ADMIN"), Locale.ENGLISH);
        assertTrue(motechUserService.hasUser("username"));
    }


    @Test
    public void shouldReturnPresenceOfAdminUser() {
        assertFalse(motechUserService.hasActiveAdminUser());
        motechUserService.register("adminUser", "password", "1234", "", asList(USER_ADMIN_ROLE), Locale.ENGLISH, true, "");
        assertTrue(motechUserService.hasActiveAdminUser());
    }

    @Test
    public void shouldValidateUserCredentials() {
        motechUserService.register("username", "password", "1234", "", asList("IT_ADMIN"), Locale.ENGLISH);
        assertNotNull(motechUserService.retrieveUserByCredentials("username", "password"));
        assertNull(motechUserService.retrieveUserByCredentials("username", "passw550rd"));
    }

    @Test
    public void shouldReturnEmptyListOfRolesForNonExistentUser() {
        List<String> roles = motechUserService.getRoles("non-existent");
        assertNotNull(roles);
        assertTrue(roles.isEmpty());
    }
}
