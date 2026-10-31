package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.ChatMessage;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.ChatJoinResult;
import ar.edu.itba.paw.services.ChatSendResult;
import ar.edu.itba.paw.services.EventChatService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.webapp.form.ChatMessageForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;

// Grupo de chat de un evento. Sin WebSockets: la pagina pide los mensajes nuevos cada
// pocos segundos (js/event-chat.js) y los recibe como un fragmento de HTML ya escapado.
@Controller
public class EventChatController {

    private static final String XHR_HEADER = "X-Requested-With";

    private final EventChatService chatService;
    private final EventService eventService;

    @Autowired
    public EventChatController(final EventChatService chatService, final EventService eventService) {
        this.chatService = chatService;
        this.eventService = eventService;
    }

    // Unirse al grupo. Si sale bien, directo al chat. Solo con el lugar confirmado en el evento: lo
    // decide @PreAuthorize antes de entrar (ver EventSecurity.isConfirmedAttendee).
    @PreAuthorize("@eventSecurity.isConfirmedAttendee(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/chat/unirse", method = RequestMethod.POST)
    public ModelAndView join(@PathVariable final long id,
                             @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                             final RedirectAttributes redirectAttributes) {
        final ChatJoinResult result = chatService.join(id, currentUser);
        if (result == ChatJoinResult.JOINED || result == ChatJoinResult.ALREADY_MEMBER) {
            return new ModelAndView("redirect:/eventos/" + id + "/chat");
        }
        redirectAttributes.addFlashAttribute("chatMessage", result.name());
        return new ModelAndView("redirect:/eventos/" + id);
    }

    // Salir del grupo (la inscripcion al evento no se toca).
    @RequestMapping(value = "/eventos/{id}/chat/salir", method = RequestMethod.POST)
    public ModelAndView leave(@PathVariable final long id,
                              @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                              final RedirectAttributes redirectAttributes) {
        chatService.leave(id, currentUser.getId());
        redirectAttributes.addFlashAttribute("chatMessage", "LEFT");
        return new ModelAndView("redirect:/eventos/" + id);
    }

    // La pantalla del chat. Es solo para miembros: quien no es miembro activo (nunca se unio, se dio
    // de baja o lo sacaron) lo corta @PreAuthorize antes de entrar (ver EventSecurity.isChatMember) y
    // ve el 404.
    @PreAuthorize("@eventSecurity.isChatMember(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/chat", method = RequestMethod.GET)
    public ModelAndView chat(@PathVariable final long id,
                             @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = eventService.findByIdOrThrow(id);
        final List<ChatMessage> messages = chatService.findMessages(id, currentUser.getId(), 0);
        final ModelAndView mav = new ModelAndView("events/chat");
        mav.addObject("event", event);
        mav.addObject("messages", messages);
        mav.addObject("lastId", lastId(messages));
        mav.addObject("currentUserId", currentUser.getId());
        mav.addObject("chatClosed", event.isCancelled() || event.isFinished());
        return mav;
    }

    // Los mensajes posteriores a "despues", como fragmento de HTML (lo pide el JS cada pocos
    // segundos). Solo para miembros: @PreAuthorize corta con 404 a quien ya no lo es (ver
    // EventSecurity.isChatMember) y el JS (event-chat.js) deja de preguntar al recibir 404 o 403.
    @PreAuthorize("@eventSecurity.isChatMember(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/chat/mensajes", method = RequestMethod.GET)
    public ModelAndView messages(@PathVariable final long id,
                                 @RequestParam(value = "despues", required = false, defaultValue = "0") final long after,
                                 @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final ModelAndView mav = new ModelAndView("events/chatMessages");
        mav.addObject("currentUserId", currentUser.getId());
        mav.addObject("messages", chatService.findMessages(id, currentUser.getId(), after));
        return mav;
    }

    // Mandar un mensaje. Solo para miembros: @PreAuthorize corta con 404 a quien no lo es (ver
    // EventSecurity.isChatMember). El JS lo manda por XHR y mira solo el codigo de estado
    // (204 ok, 400 invalido, 409 el chat se cerro, 404 ya no es miembro); un POST normal
    // (sin JS) vuelve a la pantalla del chat.
    @PreAuthorize("@eventSecurity.isChatMember(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/chat/mensajes", method = RequestMethod.POST)
    public ModelAndView send(@PathVariable final long id, @Valid final ChatMessageForm form,
                             final BindingResult errors,
                             @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                             final HttpServletRequest request, final HttpServletResponse response) {
        final ChatSendResult result = errors.hasErrors()
                ? ChatSendResult.INVALID
                : chatService.send(id, currentUser, form.getText());
        if (request.getHeader(XHR_HEADER) == null) {
            return new ModelAndView("redirect:/eventos/" + id + "/chat");
        }
        response.setStatus(statusFor(result));
        return null;
    }

    private static int statusFor(final ChatSendResult result) {
        return switch (result) {
            case SENT -> HttpServletResponse.SC_NO_CONTENT;
            case INVALID -> HttpServletResponse.SC_BAD_REQUEST;
            case CLOSED -> HttpServletResponse.SC_CONFLICT;
        };
    }

    private static long lastId(final List<ChatMessage> messages) {
        return messages.isEmpty() ? 0 : messages.get(messages.size() - 1).getId();
    }
}
