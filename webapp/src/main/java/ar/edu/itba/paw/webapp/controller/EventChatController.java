package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.services.ChatJoinResult;
import ar.edu.itba.paw.services.ChatRoom;
import ar.edu.itba.paw.services.ChatSendResult;
import ar.edu.itba.paw.services.EventChatService;
import ar.edu.itba.paw.webapp.form.ChatMessageForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

// Grupo de chat de un evento. Sin WebSockets: la pagina pide los mensajes nuevos cada
// pocos segundos (js/event-chat.js) y los recibe como un fragmento de HTML ya escapado.
@Controller
public class EventChatController {

    // Lo manda el JS del chat (js/event-chat.js) en cada pedido.
    private static final String XHR_HEADER_CONDITION = "X-Requested-With=XMLHttpRequest";

    private final EventChatService chatService;

    @Autowired
    public EventChatController(final EventChatService chatService) {
        this.chatService = chatService;
    }

    // Unirse al grupo. Si sale bien, directo al chat. Solo con el lugar confirmado en el evento: lo
    // decide @PreAuthorize antes de entrar (ver EventSecurity.isConfirmedAttendee).
    @PreAuthorize("@eventSecurity.isConfirmedAttendee(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/chat/unirse", method = RequestMethod.POST)
    public ModelAndView join(@PathVariable final long id,
                             @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                             final RedirectAttributes redirectAttributes) {
        final ChatJoinResult result = chatService.join(id, currentUser.getId());
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
        final ChatRoom room = chatService.openRoom(id).orElseThrow(() -> new EventNotFoundException(id));
        final ModelAndView mav = new ModelAndView("events/chat");
        mav.addObject("event", room.getEvent());
        mav.addObject("messages", room.getMessages());
        mav.addObject("lastId", room.getLastId());
        mav.addObject("currentUserId", currentUser.getId());
        mav.addObject("chatClosed", room.isClosed());
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
        mav.addObject("messages", chatService.findMessages(id, after));
        return mav;
    }

    // "Ya vi los mensajes": el JS (event-chat.js) lo avisa despues de mostrarlos. Es un POST aparte porque leer los
    // mensajes (los dos GET de arriba) no escribe nada. Solo para miembros, igual que el resto.
    @PreAuthorize("@eventSecurity.isChatMember(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/chat/leido", method = RequestMethod.POST)
    public ResponseEntity<Void> markRead(@PathVariable final long id,
                                         @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        chatService.markRead(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    // Mandar un mensaje. Solo para miembros: @PreAuthorize corta con 404 a quien no lo es (ver
    // EventSecurity.isChatMember). Son dos handlers para la misma URL, elegidos por el header que manda el JS: con
    // X-Requested-With (el JS) se responde solo un codigo de estado (204 ok, 400 invalido, 409 el chat se cerro, 404
    // ya no es miembro); sin el (un POST normal, sin JS) se vuelve a la pantalla del chat.
    @PreAuthorize("@eventSecurity.isChatMember(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/chat/mensajes", method = RequestMethod.POST, headers = XHR_HEADER_CONDITION)
    public ResponseEntity<Void> sendAsync(@PathVariable final long id, @Valid final ChatMessageForm form,
                                          final BindingResult errors,
                                          @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        return ResponseEntity.status(statusFor(sendMessage(id, form, errors, currentUser))).build();
    }

    @PreAuthorize("@eventSecurity.isChatMember(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/chat/mensajes", method = RequestMethod.POST)
    public ModelAndView send(@PathVariable final long id, @Valid final ChatMessageForm form,
                             final BindingResult errors,
                             @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        sendMessage(id, form, errors, currentUser);
        return new ModelAndView("redirect:/eventos/" + id + "/chat");
    }

    private ChatSendResult sendMessage(final long id, final ChatMessageForm form, final BindingResult errors,
                                       final User currentUser) {
        return errors.hasErrors() ? ChatSendResult.INVALID : chatService.send(id, currentUser.getId(), form.getText());
    }

    private static int statusFor(final ChatSendResult result) {
        return switch (result) {
            case SENT -> HttpServletResponse.SC_NO_CONTENT;
            case INVALID -> HttpServletResponse.SC_BAD_REQUEST;
            case CLOSED -> HttpServletResponse.SC_CONFLICT;
        };
    }
}
