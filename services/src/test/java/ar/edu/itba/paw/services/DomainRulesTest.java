package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.HasCreator;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.ReportTarget;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.UserAccess;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Reglas de dominio que viven en el modelo y que comparten los services, la autorizacion (AdminSecurity,
// EventSecurity) y las vistas: un solo lugar, asi que una sola serie de tests.
public class DomainRulesTest {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private static Event event() {
        final Event event = new Event();
        event.setEventDate(new Date(System.currentTimeMillis() + DAY));
        event.setCreator(ModelFixtures.user(7));
        event.setApprovalStatus(EventApprovalStatus.APPROVED);
        return event;
    }

    private static User userWith(final Role... roles) {
        final User user = new User("u@mail.com", "U");
        user.setRoles(roles.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(java.util.Arrays.asList(roles)));
        return user;
    }

    // ---- Event: vigente / editable / reseñable ----

    @Test
    public void testAFutureNonCancelledEventIsActive() {
        assertTrue(event().isActive());
    }

    @Test
    public void testACancelledEventIsNotActive() {
        final Event event = event();
        event.setCancelledAt(new Date());

        assertFalse(event.isActive());
    }

    @Test
    public void testAnEventThatAlreadyHappenedIsNotActive() {
        final Event event = event();
        event.setEventDate(new Date(System.currentTimeMillis() - DAY));

        assertFalse(event.isActive());
    }

    @Test
    public void testAnApprovedEventIsEditable() {
        assertTrue(event().isEditable());
    }

    @Test
    public void testAnEventPendingApprovalIsEditable() {
        final Event event = event();
        event.setApprovalStatus(EventApprovalStatus.PENDING);

        assertTrue(event.isEditable());
    }

    @Test
    public void testACancelledEventIsNotEditable() {
        final Event event = event();
        event.setCancelledAt(new Date());

        assertFalse(event.isEditable());
    }

    @Test
    public void testARejectedEventIsNotEditable() {
        final Event event = event();
        event.setApprovalStatus(EventApprovalStatus.REJECTED);

        assertFalse(event.isEditable());
    }

    @Test
    public void testAPastNonCancelledEventIsReviewable() {
        final Event event = event();
        event.setEventDate(new Date(System.currentTimeMillis() - DAY));

        assertTrue(event.isReviewable());
    }

    @Test
    public void testAnUpcomingEventIsNotReviewable() {
        assertFalse(event().isReviewable());
    }

    @Test
    public void testAPastCancelledEventIsNotReviewable() {
        final Event event = event();
        event.setEventDate(new Date(System.currentTimeMillis() - DAY));
        event.setCancelledAt(new Date());

        assertFalse(event.isReviewable());
    }

    // ---- quien lo creo / quien lo modera ----

    @Test
    public void testTheCreatorIsTheOnlyOneWhoCreatedTheEvent() {
        final Event event = event();

        assertTrue(event.isCreatedBy(7L));
        assertFalse(event.isCreatedBy(8L));
        assertFalse(event.isCreatedBy(null));
    }

    @Test
    public void testAnAdminCannotModerateTheirOwnEventButCanModerateOthers() {
        final Event event = event();

        assertFalse(event.canBeModeratedBy(7L));
        assertTrue(event.canBeModeratedBy(8L));
    }

    @Test
    public void testAnAdminCannotResolveAReportOnTheirOwnEventButCanOnOthers() {
        final ReportTarget target = new ReportTarget(1L, 7L);

        assertFalse(target.canBeModeratedBy(7L));
        assertTrue(target.canBeModeratedBy(8L));
    }

    @Test
    public void testAReportOnAnEventWithoutAKnownCreatorCanBeResolvedByAnyAdmin() {
        // fila vieja que todavia no paso por el backfill: no hay creador contra quien comparar
        assertTrue(new ReportTarget(1L, null).canBeModeratedBy(7L));
    }

    @Test
    public void testIsCreatorComparesOnlyWhenThereIsAPerson() {
        assertTrue(HasCreator.isCreator(7L, 7L));
        assertFalse(HasCreator.isCreator(8L, 7L));
        assertFalse(HasCreator.isCreator(null, 7L));
        assertFalse(HasCreator.isCreator(7L, null));
    }

    // ---- inscripcion revocable ----

    @Test
    public void testOnlyAConfirmedRegistrationIsRevocable() {
        final Event event = event();

        assertTrue(new Registration(event, null, "a@mail.com", "A", RegistrationStatus.CONFIRMED).isRevocable());
        assertFalse(new Registration(event, null, "a@mail.com", "A", RegistrationStatus.PENDING).isRevocable());
        assertFalse(new Registration(event, null, "a@mail.com", "A", RegistrationStatus.WAITLISTED).isRevocable());
        assertFalse(new Registration(event, null, "a@mail.com", "A", RegistrationStatus.REJECTED).isRevocable());
    }

    // ---- roles: quien puede actuar sobre quien (User y su proyeccion UserAccess dan lo mismo) ----

    @Test
    public void testAnyAdminCanSuspendARegularUser() {
        assertTrue(userWith(Role.USER).canBeSuspendedBy(false));
    }

    @Test
    public void testOnlyASuperadminCanSuspendAnAdmin() {
        final User admin = userWith(Role.USER, Role.ADMIN);

        assertFalse(admin.canBeSuspendedBy(false));
        assertTrue(admin.canBeSuspendedBy(true));
    }

    @Test
    public void testOnlyASuperadminCanSuspendAnotherSuperadmin() {
        final User superadmin = userWith(Role.ADMIN, Role.SUPERADMIN);

        assertFalse(superadmin.canBeSuspendedBy(false));
        assertTrue(superadmin.canBeSuspendedBy(true));
    }

    @Test
    public void testTheAccessProjectionAppliesTheSameSuspendRuleAsTheUser() {
        final UserAccess admin = new UserAccess(1L, EnumSet.of(Role.USER, Role.ADMIN));
        final UserAccess regular = new UserAccess(2L, Collections.singleton(Role.USER));

        assertFalse(admin.canBeSuspendedBy(false));
        assertTrue(admin.canBeSuspendedBy(true));
        assertTrue(regular.canBeSuspendedBy(false));
    }
}
