<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<spring:message code="profile.public.anonymousName" var="anonymousName"/>
<c:set var="displayName" value="${empty profile.user.name ? anonymousName : profile.user.name}"/>
<paw:layout title="${displayName}">
<jsp:attribute name="head">
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
</jsp:attribute>
<jsp:body>
<paw:navbar backToEvents="true"/>

<%-- Perfil publico: lo que se muestra sale todo de UserProfile (ya armado por UserProfileService). Nunca el mail. --%>
<main class="page public-profile">
    <c:if test="${profile.own and not empty bioMessage}">
        <p class="alert<c:if test="${bioMessage == 'OK'}"> alert-success</c:if>">
            <spring:message code="profile.public.bio.flash.${bioMessage}"/>
        </p>
    </c:if>

    <c:if test="${profile.own and not empty coverMessage}">
        <p class="alert<c:if test="${coverMessage == 'OK'}"> alert-success</c:if>">
            <spring:message code="profile.public.cover.flash.${coverMessage}"/>
        </p>
    </c:if>

    <section class="public-profile-hero">
        <%-- Portada elegida (se pide por /usuarios/{id}/portada; ?v= cambia al cambiarla, asi el navegador no muestra la
             vieja) o, sin ninguna, el degradado por defecto. --%>
        <c:choose>
            <c:when test="${not empty profile.user.coverImageId}">
                <div class="public-profile-cover public-profile-cover-image"
                     style="background-image: url('<c:url value="/usuarios/${profile.user.id}/portada"><c:param name="v" value="${profile.user.coverImageId}"/></c:url>')"></div>
            </c:when>
            <c:otherwise>
                <div class="public-profile-cover"></div>
            </c:otherwise>
        </c:choose>
        <c:if test="${profile.own}">
            <button type="button" class="public-profile-cover-edit" data-open-modal="coverModal">
                <paw:icon name="image"/> <spring:message code="profile.public.cover.edit"/>
            </button>
        </c:if>
        <div class="public-profile-head">
            <span class="public-profile-avatar"><c:out value="${fn:toUpperCase(fn:substring(displayName, 0, 1))}"/></span>
            <c:if test="${profile.own}">
                <button type="button" class="btn btn-medium" data-open-modal="bioModal">
                    <spring:message code="profile.public.bio.edit"/>
                </button>
            </c:if>
        </div>

        <div class="public-profile-body">
            <div class="public-profile-identity">
                <h1 class="public-profile-name"><c:out value="${displayName}"/></h1>
                <c:if test="${profile.superOrganizer}">
                    <p class="super-organizer">
                        <span class="super-organizer-tag"><spring:message code="profile.public.superOrganizer"/></span>
                        <span class="super-organizer-text">
                            <spring:message code="profile.public.superOrganizer.text" arguments="${profile.organizedCount}"/>
                        </span>
                    </p>
                </c:if>
                <c:choose>
                    <c:when test="${not empty profile.user.bio}">
                        <p class="public-profile-bio"><c:out value="${profile.user.bio}"/></p>
                    </c:when>
                    <c:when test="${profile.own}">
                        <p class="public-profile-bio public-profile-bio-empty"><spring:message code="profile.public.bio.emptyOwn"/></p>
                    </c:when>
                    <c:otherwise>
                        <p class="public-profile-bio public-profile-bio-empty"><spring:message code="profile.public.bio.empty"/></p>
                    </c:otherwise>
                </c:choose>
            </div>

            <div class="public-profile-stats">
                <div class="public-profile-stat public-profile-stat-rating">
                    <c:choose>
                        <c:when test="${profile.hasReviews}">
                            <span class="public-profile-stat-value">
                                <fmt:formatNumber value="${profile.averageRating}" maxFractionDigits="1"/>
                                <paw:stars rating="${profile.averageRating}"/>
                            </span>
                            <span class="public-profile-stat-label">
                                <spring:message code="profile.public.stats.rating"/>
                                (<spring:message code="review.countLabel" arguments="${profile.reviewCount}"/>)
                            </span>
                        </c:when>
                        <c:otherwise>
                            <span class="public-profile-stat-value">&mdash;</span>
                            <span class="public-profile-stat-label"><spring:message code="profile.public.stats.noReviews"/></span>
                        </c:otherwise>
                    </c:choose>
                </div>
                <div class="public-profile-stat">
                    <span class="public-profile-stat-value"><c:out value="${profile.organizedCount}"/></span>
                    <span class="public-profile-stat-label"><spring:message code="profile.public.stats.organized"/></span>
                </div>
                <div class="public-profile-stat">
                    <span class="public-profile-stat-value"><c:out value="${profile.upcomingCount}"/></span>
                    <span class="public-profile-stat-label"><spring:message code="profile.public.stats.upcoming"/></span>
                </div>
            </div>
        </div>
    </section>

    <%-- Pestañas como las de Instagram: se elige cual grupo ver (?tab=proximos | realizados); sin elegir, abre la que
         tiene eventos (primero proximos). Solo se dibuja el paginado del grupo visible. --%>
    <c:choose>
        <c:when test="${param.tab == 'realizados'}"><c:set var="activeTab" value="realizados"/></c:when>
        <c:when test="${param.tab == 'proximos'}"><c:set var="activeTab" value="proximos"/></c:when>
        <c:when test="${profile.upcoming.totalElements == 0 and profile.past.totalElements > 0}"><c:set var="activeTab" value="realizados"/></c:when>
        <c:otherwise><c:set var="activeTab" value="proximos"/></c:otherwise>
    </c:choose>

    <nav class="profile-tabs" aria-label="<spring:message code='profile.public.tabs.label'/>">
        <a class="profile-tab<c:if test="${activeTab == 'proximos'}"> profile-tab-active</c:if>"
           href="<c:url value='/usuarios/${profile.user.id}'><c:param name='tab' value='proximos'/></c:url>"
           <c:if test="${activeTab == 'proximos'}">aria-current="page"</c:if>>
            <paw:icon name="calendar"/>
            <span><spring:message code="profile.public.upcomingHeading"/></span>
            <span class="profile-tab-count"><c:out value="${profile.upcoming.totalElements}"/></span>
        </a>
        <a class="profile-tab<c:if test="${activeTab == 'realizados'}"> profile-tab-active</c:if>"
           href="<c:url value='/usuarios/${profile.user.id}'><c:param name='tab' value='realizados'/></c:url>"
           <c:if test="${activeTab == 'realizados'}">aria-current="page"</c:if>>
            <paw:icon name="check-square"/>
            <span><spring:message code="profile.public.pastHeading"/></span>
            <span class="profile-tab-count"><c:out value="${profile.past.totalElements}"/></span>
        </a>
    </nav>

    <section class="public-profile-section">
        <c:choose>
            <c:when test="${activeTab == 'realizados'}">
                <c:set var="shown" value="${profile.past}"/>
                <c:set var="emptyKey" value="profile.public.pastEmpty"/>
                <c:set var="pageName" value="realizadosPage"/>
            </c:when>
            <c:otherwise>
                <c:set var="shown" value="${profile.upcoming}"/>
                <c:set var="emptyKey" value="profile.public.upcomingEmpty"/>
                <c:set var="pageName" value="proximosPage"/>
            </c:otherwise>
        </c:choose>
        <c:choose>
            <c:when test="${empty shown.content}">
                <p class="empty-state"><spring:message code="${emptyKey}"/></p>
            </c:when>
            <c:otherwise>
                <c:set var="eventsToShow" value="${shown.content}" scope="request"/>
                <jsp:include page="/WEB-INF/jsp/users/_eventCards.jspf"/>
                <paw:pager baseUrl="/usuarios/${profile.user.id}?tab=${activeTab}" pageParam="${pageName}"
                           currentPage="${shown.page}" totalPages="${shown.totalPages}"/>
            </c:otherwise>
        </c:choose>
    </section>
</main>

<c:if test="${profile.own}">
    <spring:message code="event.detail.closeButton" var="closeButtonLabel"/>
    <spring:message code="profile.public.bio.save" var="saveBioLabel"/>
    <div class="modal-overlay" id="bioModal" hidden>
        <div class="modal-box">
            <div class="modal-header">
                <h2><spring:message code="profile.public.bio.heading"/></h2>
                <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                    <paw:icon name="close"/>
                </button>
            </div>
            <form action="<c:url value='/perfil/biografia'/>" method="post" novalidate="novalidate">
                <div class="form-group">
                    <label for="bio"><spring:message code="profile.public.bio.label"/></label>
                    <textarea id="bio" name="bio" rows="5" maxlength="500"><c:out value="${profile.user.bio}"/></textarea>
                </div>
                <paw:button text="${saveBioLabel}" cssClass="btn-accent"/>
            </form>
        </div>
    </div>

    <spring:message code="profile.public.cover.save" var="saveCoverLabel"/>
    <spring:message code="profile.public.cover.remove" var="removeCoverLabel"/>
    <div class="modal-overlay" id="coverModal" hidden>
        <div class="modal-box">
            <div class="modal-header">
                <h2><spring:message code="profile.public.cover.heading"/></h2>
                <button type="button" class="modal-close" data-close-modal aria-label="${closeButtonLabel}">
                    <paw:icon name="close"/>
                </button>
            </div>
            <form action="<c:url value='/perfil/portada'/>" method="post" enctype="multipart/form-data" novalidate="novalidate">
                <div class="form-group">
                    <label for="cover"><spring:message code="profile.public.cover.label"/></label>
                    <input type="file" id="cover" name="cover" accept="image/jpeg,image/png,image/gif,image/webp" required="required"/>
                    <p class="public-profile-cover-hint"><spring:message code="profile.public.cover.hint"/></p>
                </div>
                <paw:button text="${saveCoverLabel}" cssClass="btn-accent"/>
            </form>
            <c:if test="${not empty profile.user.coverImageId}">
                <form action="<c:url value='/perfil/portada/quitar'/>" method="post" class="public-profile-cover-remove">
                    <paw:button text="${removeCoverLabel}"/>
                </form>
            </c:if>
        </div>
    </div>
</c:if>
</jsp:body>
</paw:layout>
