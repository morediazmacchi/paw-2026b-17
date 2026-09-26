<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="event.publish.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
          integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
            integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/quick-options.js'/>"></script>
    <script src="<c:url value='/js/event-location-map.js'/>"></script>
    <script src="<c:url value='/js/image-picker.js'/>"></script>
    <%-- data-open-modal/data-close-modal del almanaque de fecha (ver event-wizard.js). --%>
    <script src="<c:url value='/js/modal.js'/>"></script>
    <script src="<c:url value='/js/event-wizard.js'/>"></script>
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<main class="page page-narrow">
    <div class="wizard-card">
        <h1><spring:message code="event.publish.heading"/></h1>

        <%-- 5 circulos conectados: event-wizard.js les agrega/saca las clases
             -active/-done segun el paso en que este parado. --%>
        <div class="wizard-progress">
            <c:forEach var="n" begin="1" end="5">
                <span class="wizard-progress-step" data-step-indicator="${n}">${n}</span>
                <c:if test="${n < 5}"><span class="wizard-progress-line"></span></c:if>
            </c:forEach>
        </div>

        <c:if test="${not empty imageError}">
            <p class="alert"><c:out value="${imageError}"/></p>
        </c:if>

        <spring:message code="event.publish.titleLabel" var="titleLabel"/>
        <spring:message code="event.publish.descriptionLabel" var="descriptionLabel"/>
        <spring:message code="event.publish.categoryLabel" var="categoryLabel"/>
        <spring:message code="event.publish.dateLabel" var="dateLabel"/>
        <spring:message code="event.publish.addressLabel" var="addressLabel"/>
        <spring:message code="event.publish.neighborhoodLabel" var="neighborhoodLabel"/>
        <spring:message code="event.publish.priceLabel" var="priceLabel"/>
        <spring:message code="event.publish.capacityLabel" var="capacityLabel"/>
        <spring:message code="event.publish.imageLabel" var="imageLabel"/>
        <spring:message code="event.publish.freeOption" var="freeOptionLabel"/>
        <spring:message code="event.publish.noLimitOption" var="noLimitOptionLabel"/>
        <spring:message code="event.publish.submitButton" var="submitButtonLabel"/>
        <spring:message code="event.publish.nextButton" var="nextButtonLabel"/>
        <spring:message code="event.publish.backButton" var="backButtonLabel"/>
        <spring:message code="event.publish.cancelButton" var="cancelButtonLabel"/>

        <form class="wizard-form" action="<c:url value='/eventos/publicar'/>" method="post"
              enctype="multipart/form-data" novalidate="novalidate">

            <%-- ---- Paso 1: informacion basica ---- --%>
            <div class="wizard-step" data-step="1">
                <div class="wizard-step-heading">
                    <h2><spring:message code="event.publish.step1Heading"/></h2>
                    <p><spring:message code="event.publish.step1Subheading"/></p>
                </div>
                <paw:input name="titulo" label="${titleLabel}" type="text"
                           value="${eventForm.titulo}" errorMessage="${fieldErrors.titulo}" />
                <paw:input name="descripcion" label="${descriptionLabel}" type="text" required="${false}"
                           value="${eventForm.descripcion}" errorMessage="${fieldErrors.descripcion}" />
            </div>

            <%-- ---- Paso 2: categoria y fecha ---- --%>
            <div class="wizard-step" data-step="2" hidden>
                <div class="wizard-step-heading">
                    <h2><spring:message code="event.publish.step2Heading"/></h2>
                    <p><spring:message code="event.publish.step2Subheading"/></p>
                </div>
                <spring:message code="event.publish.categoryExtraInfoLabel" var="categoryExtraInfoLabel"/>
                <div class="form-group">
                    <label><c:out value="${categoryLabel}"/></label>
                    <div class="wizard-category-list">
                        <c:forEach var="cat" items="${categories}" varStatus="catStatus">
                            <c:set var="catForm" value="${eventForm.categorias[catStatus.index]}"/>
                            <div class="wizard-category-item">
                                <label class="filter-checkbox">
                                    <input type="checkbox" class="wizard-category-checkbox"
                                           name="categorias[${catStatus.index}].seleccionada" value="true"
                                           data-category-index="${catStatus.index}"
                                           ${catForm.seleccionada ? 'checked="checked"' : ''}/>
                                    <spring:message code="category.${cat.name}" text="${cat.name}" htmlEscape="true"/>
                                </label>
                                <input type="hidden" name="categorias[${catStatus.index}].categoryId" value="${cat.id}"/>
                                <div class="wizard-category-extra" ${catForm.seleccionada ? '' : 'hidden'}>
                                    <textarea name="categorias[${catStatus.index}].extraInfo" maxlength="500"
                                              placeholder="${categoryExtraInfoLabel}"><c:out value="${catForm.extraInfo}"/></textarea>
                                </div>
                            </div>
                        </c:forEach>
                    </div>
                    <c:if test="${not empty categoriasError}">
                        <span class="error-message"><c:out value="${categoriasError}"/></span>
                    </c:if>
                </div>
                <div class="form-group">
                    <label for="fechaEventoTrigger"><c:out value="${dateLabel}"/></label>
                    <div class="wizard-datetime-row">
                        <%-- El <input type="date"> nativo no se puede restylear (el popup
                             lo dibuja el SO/navegador): en su lugar, un boton que abre el
                             almanaque propio de mas abajo (mismo modal generico de siempre). --%>
                        <button type="button" id="fechaEventoTrigger" class="calendar-trigger"
                                data-open-modal="fechaCalendarModal">
                            <paw:icon name="calendar"/>
                            <span class="calendar-trigger-text" data-calendar-trigger-text></span>
                        </button>
                        <input type="hidden" id="fechaEventoDate"/>
                        <%-- Nada de <input type="time">: ese widget depende de la
                             configuracion regional del navegador y en muchas
                             termina mostrando AM/PM igual. Dos campos numéricos
                             (0-23 / 0-59) no tienen ese problema: son solo numeros. --%>
                        <div class="wizard-time-row">
                            <input type="number" id="fechaEventoHours" min="0" max="23"
                                   placeholder="HH" inputmode="numeric" required="required"/>
                            <span class="wizard-time-separator">:</span>
                            <input type="number" id="fechaEventoMinutes" min="0" max="59"
                                   placeholder="MM" inputmode="numeric" required="required"/>
                        </div>
                    </div>
                    <p class="wizard-field-hint"><spring:message code="event.publish.timeFormatHint"/></p>
                    <c:if test="${not empty fieldErrors.fechaEvento}">
                        <span class="error-message"><c:out value="${fieldErrors.fechaEvento}"/></span>
                    </c:if>
                    <%-- Lo que realmente viaja al servidor: event-wizard.js lo arma
                         combinando los campos de arriba (el server sigue
                         esperando un solo string "yyyy-MM-ddTHH:mm", no cambio
                         EventForm ni el controller por esto). --%>
                    <input type="hidden" id="fechaEvento" name="fechaEvento" value="<c:out value='${eventForm.fechaEvento}'/>"/>
                </div>

                <%-- Almanaque: la grilla de dias la arma event-wizard.js. Los dias
                     fuera de hoy..+2anios vienen deshabilitados desde el propio JS,
                     asi que no hace falta validar despues "fecha fuera de rango":
                     no se puede clickear una fecha invalida. --%>
                <div class="modal-overlay calendar-modal" id="fechaCalendarModal" hidden>
                    <div class="modal-box calendar-box">
                        <div class="modal-header">
                            <h2><c:out value="${dateLabel}"/></h2>
                            <button type="button" class="modal-close" data-close-modal
                                    aria-label="<spring:message code='event.detail.closeButton'/>">
                                <paw:icon name="close"/>
                            </button>
                        </div>
                        <div class="calendar-header">
                            <button type="button" class="calendar-nav" data-calendar-prev
                                    aria-label="<spring:message code='event.publish.prevMonth'/>">
                                <paw:icon name="chevron-left"/>
                            </button>
                            <span class="calendar-month-label" data-calendar-month-label></span>
                            <button type="button" class="calendar-nav" data-calendar-next
                                    aria-label="<spring:message code='event.publish.nextMonth'/>">
                                <paw:icon name="chevron-right"/>
                            </button>
                        </div>
                        <div class="calendar-weekdays">
                            <span>L</span><span>M</span><span>M</span><span>J</span><span>V</span><span>S</span><span>D</span>
                        </div>
                        <div class="calendar-days" data-calendar-days></div>
                    </div>
                </div>
            </div>

            <%-- ---- Paso 3: ubicacion ---- --%>
            <div class="wizard-step" data-step="3" hidden>
                <div class="wizard-step-heading">
                    <h2><spring:message code="event.publish.step3Heading"/></h2>
                    <p><spring:message code="event.publish.step3Subheading"/></p>
                </div>
                <paw:input name="direccion" label="${addressLabel}" type="text"
                           value="${eventForm.direccion}" errorMessage="${fieldErrors.direccion}" />
                <div class="form-group">
                    <label for="barrio"><c:out value="${neighborhoodLabel}"/></label>
                    <select id="barrio" name="barrio" required="required">
                        <option value="" disabled="disabled" ${empty eventForm.barrio ? 'selected="selected"' : ''}></option>
                        <c:forEach var="b" items="${barrios}">
                            <option value="${b}" ${b.name() == eventForm.barrio ? 'selected' : ''}>
                                <c:out value="${b.displayName}"/>
                            </option>
                        </c:forEach>
                    </select>
                    <c:if test="${not empty fieldErrors.barrio}">
                        <span class="error-message"><c:out value="${fieldErrors.barrio}"/></span>
                    </c:if>
                </div>
                <div class="form-group">
                    <label><spring:message code="event.publish.mapLabel"/></label>
                    <p class="wizard-field-hint"><spring:message code="event.publish.mapHint"/></p>
                    <%-- event-location-map.js la arma: mapa Leaflet + OpenStreetMap,
                         un click pone/mueve el pin y carga los dos inputs ocultos de
                         abajo. Es opcional: si nadie lo toca, viajan vacios y el
                         evento se publica solo con la direccion en texto de arriba. --%>
                    <div id="location-map" class="wizard-map"></div>
                    <input type="hidden" id="latitud" name="latitud" value="<c:out value='${eventForm.latitud}'/>"/>
                    <input type="hidden" id="longitud" name="longitud" value="<c:out value='${eventForm.longitud}'/>"/>
                </div>
            </div>

            <%-- ---- Paso 4: imagen y precio ---- --%>
            <div class="wizard-step" data-step="4" hidden>
                <div class="wizard-step-heading">
                    <h2><spring:message code="event.publish.step4Heading"/></h2>
                    <p><spring:message code="event.publish.step4Subheading"/></p>
                </div>
                <div class="form-group">
                    <label><c:out value="${imageLabel}"/></label>
                    <label class="wizard-dropzone" for="images">
                        <paw:icon name="image"/>
                        <span class="wizard-dropzone-label"><spring:message code="event.publish.imageDropHint"/></span>
                        <span class="wizard-dropzone-filename" hidden></span>
                    </label>
                    <spring:message code="event.publish.tooManyImagesError" var="tooManyImagesMsg"/>
                    <spring:message code="event.publish.coverLabel" var="coverLabelMsg"/>
                    <spring:message code="event.publish.removeImage" var="removeImageMsg"/>
                    <input type="file" id="images" name="images" accept="image/*" multiple="multiple"
                           class="wizard-dropzone-input"
                           data-max-message="<c:out value='${tooManyImagesMsg}'/>"
                           data-cover-label="<c:out value='${coverLabelMsg}'/>"
                           data-remove-label="<c:out value='${removeImageMsg}'/>"/>
                </div>
                <paw:input name="precio" label="${priceLabel}" type="number" min="0"
                           value="${eventForm.precio}" errorMessage="${fieldErrors.precio}"
                           toggleText="${freeOptionLabel}" />
                <paw:input name="cupoMaximo" label="${capacityLabel}" type="number" min="0"
                           value="${eventForm.cupoMaximo}" errorMessage="${fieldErrors.cupoMaximo}"
                           toggleText="${noLimitOptionLabel}" />
                <div class="form-group">
                    <label class="filter-checkbox">
                        <input type="checkbox" id="requiereAprobacion" name="requiereAprobacion" value="true"
                               ${eventForm.requiereAprobacion ? 'checked="checked"' : ''}/>
                        <spring:message code="event.publish.requiresApprovalLabel"/>
                    </label>
                    <p class="wizard-field-hint"><spring:message code="event.publish.requiresApprovalHint"/></p>
                </div>
                <div class="form-group">
                    <label class="filter-checkbox">
                        <input type="checkbox" id="privado" name="privado" value="true"
                               ${eventForm.privado ? 'checked="checked"' : ''}/>
                        <spring:message code="event.publish.privateLabel"/>
                    </label>
                    <p class="wizard-field-hint"><spring:message code="event.publish.privateHint"/></p>
                </div>
                <div class="form-group">
                    <label class="filter-checkbox">
                        <input type="checkbox" id="estacionamiento" name="estacionamiento" value="true"
                               ${eventForm.estacionamiento ? 'checked="checked"' : ''}/>
                        <spring:message code="event.publish.parkingLabel"/>
                    </label>
                </div>
            </div>

            <%-- ---- Paso 5: revisar y publicar ---- --%>
            <div class="wizard-step" data-step="5" hidden>
                <div class="wizard-step-heading">
                    <h2><spring:message code="event.publish.step5Heading"/></h2>
                    <p><spring:message code="event.publish.step5Subheading"/></p>
                </div>
                <%-- event-wizard.js completa esto con lo que se cargo en los pasos
                     anteriores justo antes de mostrar este paso. --%>
                <div class="wizard-review">
                    <div class="wizard-review-row">
                        <span class="wizard-review-label"><c:out value="${titleLabel}"/></span>
                        <span class="wizard-review-value" data-review="titulo"></span>
                    </div>
                    <div class="wizard-review-row">
                        <span class="wizard-review-label"><c:out value="${categoryLabel}"/></span>
                        <span class="wizard-review-value" data-review="categoria"></span>
                    </div>
                    <div class="wizard-review-row">
                        <span class="wizard-review-label"><c:out value="${dateLabel}"/></span>
                        <span class="wizard-review-value" data-review="fecha"></span>
                    </div>
                    <div class="wizard-review-row">
                        <span class="wizard-review-label"><c:out value="${addressLabel}"/></span>
                        <span class="wizard-review-value" data-review="direccion"></span>
                    </div>
                    <div class="wizard-review-row">
                        <span class="wizard-review-label"><c:out value="${priceLabel}"/></span>
                        <span class="wizard-review-value" data-review="precio"></span>
                    </div>
                    <div class="wizard-review-row">
                        <span class="wizard-review-label"><c:out value="${capacityLabel}"/></span>
                        <span class="wizard-review-value" data-review="cupo"></span>
                    </div>
                    <div class="wizard-review-row">
                        <spring:message code="event.publish.requiresApprovalLabel" var="requiresApprovalLabel"/>
                        <spring:message code="common.yes" var="yesLabel"/>
                        <spring:message code="common.no" var="noLabel"/>
                        <span class="wizard-review-label"><c:out value="${requiresApprovalLabel}"/></span>
                        <span class="wizard-review-value" data-review="requiereAprobacion"
                              data-yes-label="${yesLabel}" data-no-label="${noLabel}"></span>
                    </div>
                    <div class="wizard-review-row">
                        <spring:message code="event.publish.privateLabel" var="privateLabel"/>
                        <span class="wizard-review-label"><c:out value="${privateLabel}"/></span>
                        <span class="wizard-review-value" data-review="privado"
                              data-yes-label="${yesLabel}" data-no-label="${noLabel}"></span>
                    </div>
                    <div class="wizard-review-row">
                        <spring:message code="event.publish.parkingLabel" var="parkingLabel"/>
                        <span class="wizard-review-label"><c:out value="${parkingLabel}"/></span>
                        <span class="wizard-review-value" data-review="estacionamiento"
                              data-yes-label="${yesLabel}" data-no-label="${noLabel}"></span>
                    </div>
                    <div class="wizard-review-row" data-review-image-row hidden>
                        <span class="wizard-review-label"><c:out value="${imageLabel}"/></span>
                        <span class="wizard-review-image-wrap">
                            <img class="wizard-review-image" data-review-image alt=""/>
                            <span class="wizard-review-image-count" data-review-image-count></span>
                        </span>
                    </div>
                </div>
            </div>

            <%-- event-wizard.js la muestra cuando "Siguiente" encuentra algo
                 invalido en el paso actual: un mensaje propio, siempre visible,
                 en vez de depender de la burbuja nativa del navegador (que en
                 algunos casos - el ejemplo real: una fecha pasada - es facil
                 que pase desapercibida). --%>
            <p class="alert wizard-step-error" hidden></p>

            <div class="wizard-actions">
                <button type="button" class="btn btn-medium wizard-back" data-wizard-back hidden>
                    <c:out value="${backButtonLabel}"/>
                </button>
                <a class="btn btn-medium wizard-cancel" href="<c:url value='/eventos'/>">
                    <c:out value="${cancelButtonLabel}"/>
                </a>
                <button type="button" class="btn btn-medium btn-accent wizard-next" data-wizard-next>
                    <c:out value="${nextButtonLabel}"/>
                </button>
                <span class="wizard-submit" hidden>
                    <paw:button text="${submitButtonLabel}" cssClass="btn-accent" />
                </span>
            </div>
        </form>
    </div>
</main>

</body>
</html>
