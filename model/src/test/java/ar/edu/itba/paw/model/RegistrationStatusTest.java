package ar.edu.itba.paw.model;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class RegistrationStatusTest {

    @Test
    public void testWithConfirmedPlaceIsExactlyTheStatusesThatAreConfirmed() {
        assertEquals(Collections.singleton(RegistrationStatus.CONFIRMED), RegistrationStatus.withConfirmedPlace());
    }
}
