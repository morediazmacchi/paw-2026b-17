package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.UserDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UserServiceImplTest {

    private static final long USER_ID = 1;
    private static final String EMAIL = "  Test@Mail.com  ";
    private static final String NORMALIZED_EMAIL = "test@mail.com";
    private static final String NAME = "Ana";

    @Mock
    private UserDao mockUserDao;

    @InjectMocks
    private UserServiceImpl userService;

    private static User someUser() {
        final User user = new User(NORMALIZED_EMAIL, NAME);
        user.setId(USER_ID);
        return user;
    }

    @Test
    public void testFindByIdReturnsThePresentUser() {
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));

        final Optional<User> result = userService.findById(USER_ID);

        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().getId());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheUserDoesNotExist() {
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.empty());

        final Optional<User> result = userService.findById(USER_ID);

        assertFalse(result.isPresent());
    }

    @Test
    public void testFindOrCreateReturnsTheExistingUserAndNormalizesTheEmail() {
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(someUser()));

        final User result = userService.findOrCreate(EMAIL, NAME);

        assertEquals(USER_ID, result.getId());
        assertEquals(NORMALIZED_EMAIL, result.getEmail());
    }

    @Test
    public void testFindOrCreateCreatesANewUserWhenNoneExists() {
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockUserDao.create(any(User.class))).thenReturn(someUser());

        final User result = userService.findOrCreate(EMAIL, NAME);

        assertEquals(USER_ID, result.getId());
    }
}
