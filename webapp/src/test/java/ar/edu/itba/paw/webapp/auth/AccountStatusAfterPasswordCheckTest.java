package ar.edu.itba.paw.webapp.auth;

import org.junit.Test;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public class AccountStatusAfterPasswordCheckTest {

    private final AccountStatusAfterPasswordCheck check = new AccountStatusAfterPasswordCheck();

    private static User account(final boolean enabled, final boolean nonExpired, final boolean credentialsNonExpired,
                                final boolean nonLocked) {
        return new User("ana@mail.com", "x", enabled, nonExpired, credentialsNonExpired, nonLocked,
                AuthorityUtils.NO_AUTHORITIES);
    }

    @Test(expected = DisabledException.class)
    public void testADisabledAccountIsRejected() {
        check.check(account(false, true, true, true));
    }

    @Test(expected = LockedException.class)
    public void testALockedAccountIsRejected() {
        check.check(account(true, true, true, false));
    }

    @Test(expected = AccountExpiredException.class)
    public void testAnExpiredAccountIsRejected() {
        check.check(account(true, false, true, true));
    }

    @Test(expected = CredentialsExpiredException.class)
    public void testExpiredCredentialsAreRejected() {
        check.check(account(true, true, false, true));
    }
}
