package ar.edu.itba.paw.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

// Las pestañas y ordenes que llegan en la URL: un valor que no existe nunca da error, cae al de por defecto.
public class UrlEnumParamsTest {

    @Test
    public void testAKnownReportTabIsReturnedAsIs() {
        assertEquals(ReportStatusFilter.IN_REVIEW, ReportStatusFilter.fromParam("IN_REVIEW"));
    }

    @Test
    public void testAnUnknownReportTabFallsBackToAll() {
        assertEquals(ReportStatusFilter.ALL, ReportStatusFilter.fromParam("inventada"));
    }

    @Test
    public void testAMissingReportTabFallsBackToAll() {
        assertEquals(ReportStatusFilter.ALL, ReportStatusFilter.fromParam(null));
    }

    @Test
    public void testAKnownAdminSortIsReturnedAsIs() {
        assertEquals(AdminEventSort.TITLE_ASC, AdminEventSort.fromParam("TITLE_ASC"));
    }

    @Test
    public void testAnUnknownAdminSortFallsBackToTheDefault() {
        assertEquals(AdminEventSort.CREATED_DESC, AdminEventSort.fromParam("inventado"));
    }

    @Test
    public void testAMissingAdminSortFallsBackToTheDefault() {
        assertEquals(AdminEventSort.CREATED_DESC, AdminEventSort.fromParam(null));
    }
}
