<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="paw" tagdir="/WEB-INF/tags" %>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plancito &middot; <spring:message code="event.edit.title"/></title>
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/favicon.svg">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script src="<c:url value='/js/disable-on-submit.js'/>"></script>
    <script src="<c:url value='/js/quick-options.js'/>"></script>
    <script src="<c:url value='/js/event-wizard.js'/>"></script>
</head>
<body>

<paw:navbar>
    <spring:message code="common.nav.backToEvents" var="backToEventsLabel"/>
    <a class="btn btn-medium" href="<c:url value='/eventos'/>"><c:out value="${backToEventsLabel}"/></a>
</paw:navbar>

<main class="page page-narrow">
    <div class="wizard-card">
        <h1><spring:message code="event.edit.heading"/></h1>

        <%-- Mismo wizard de 5 pasos que publish.jsp, con los campos precargados
             con los valores actuales del evento (eventForm viene armado por
             EventController.editarForm()/editarFormWithErrors()). --%>
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
        <spring:message code="event.edit.submitButton" var="submitButtonLabel"/>
        <spring:message code="event.publish.nextButton" var="nextButtonLabel"/>
        <spring:message code="event.publish.backButton" var="backButtonLabel"/>
        <spring:message code="event.publish.cancelButton" var="cancelButtonLabel"/>

        <form class="wizard-form" action="<c:url value='/eventos/${eventId}/editar'/>" method="post"
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
                    <label for="fechaEventoDate"><c:out value="${dateLabel}"/></label>
                    <div class="wizard-datetime-row">
                        <input type="date" id="fechaEventoDate" required="required"/>
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
                         combinando los campos de arriba (splitea eventForm.fechaEvento
                         en fecha/hora/minutos al cargar la pagina, ver su codigo). --%>
                    <input type="hidden" id="fechaEvento" name="fechaEvento" value="${eventForm.fechaEvento}"/>
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
                    <input type="file" id="images" name="images" accept="image/*" multiple="multiple"
                           class="wizard-dropzone-input"/>
                    <%-- A diferencia de publish.jsp: aca las fotos son opcionales porque
                         ya puede haber alguna cargada -- si no se toca este campo, el
                         controller mantiene la galeria que el evento ya tenia. --%>
                    <p class="wizard-field-hint"><spring:message code="event.edit.currentImageHint"/></p>
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
            </div>

            <%-- ---- Paso 5: revisar y guardar ---- --%>
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
                 en vez de depender de la burbuja nativa del navegador. --%>
            <p class="alert wizard-step-error" hidden></p>

            <div class="wizard-actions">
                <a class="btn btn-medium wizard-cancel" href="<c:url value='/perfil/eventos'/>">
                    <c:out value="${cancelButtonLabel}"/>
                </a>
                <button type="button" class="btn btn-medium wizard-back" data-wizard-back hidden>
                    <c:out value="${backButtonLabel}"/>
                </button>
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
