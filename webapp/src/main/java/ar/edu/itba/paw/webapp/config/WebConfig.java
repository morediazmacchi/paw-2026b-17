package ar.edu.itba.paw.webapp.config;

import ar.edu.itba.paw.services.AccountSummaryService;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventChatService;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.InboxService;
import ar.edu.itba.paw.services.ModerationService;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.SavedSearchService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.security.AdminSecurity;
import ar.edu.itba.paw.webapp.security.EventSecurity;
import ar.edu.itba.paw.webapp.security.SavedSearchSecurity;
import ar.edu.itba.paw.webapp.interceptor.AccountSidebarInterceptor;
import ar.edu.itba.paw.webapp.interceptor.CurrentLocaleInterceptor;
import ar.edu.itba.paw.webapp.interceptor.UnreadInboxInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.JstlView;


// Contexto de SPRING MVC (lo carga el DispatcherServlet): solo lo que tiene que ver con la
// web. La persistencia, los services y la seguridad estan en RootConfig (contexto raiz).
@EnableWebMvc
// Seguridad por metodos (@PreAuthorize en los controllers). Se activa en ESTE contexto, no en
// RootConfig, porque los proxies se aplican a los beans del contexto donde se declara: los
// controllers viven aca. proxyTargetClass: los controllers no tienen interfaz.
@EnableGlobalMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
@ComponentScan("ar.edu.itba.paw.webapp.controller")
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // Reglas de ownership para @PreAuthorize (ver EventSecurity). Vive en este contexto porque
    // la seguridad por metodos se activa aca, donde estan los controllers; no se escanea porque
    // RootConfig solo escanea sus propios paquetes.
    @Bean
    public EventSecurity eventSecurity(final EventService eventService,
                                       final RegistrationService registrationService,
                                       final EventAccessService eventAccessService,
                                       final ImageService imageService,
                                       final EventChatService eventChatService,
                                       final EventQuestionService eventQuestionService) {
        return new EventSecurity(eventService, registrationService, eventAccessService, imageService,
                eventChatService, eventQuestionService);
    }

    // Ownership de las busquedas guardadas para @PreAuthorize en ProfileController (ver SavedSearchSecurity).
    @Bean
    public SavedSearchSecurity savedSearchSecurity(final SavedSearchService savedSearchService) {
        return new SavedSearchSecurity(savedSearchService);
    }

    // Reglas de admin sobre otros usuarios para @PreAuthorize en AdminController (ver AdminSecurity).
    @Bean
    public AdminSecurity adminSecurity(final UserService userService, final EventService eventService,
                                       final ModerationService moderationService) {
        return new AdminSecurity(userService, eventService, moderationService);
    }

    // Se llama messageSource (no otro nombre): es el nombre de bean que Spring
    // busca por convencion para resolver <spring:message> en los JSP. El de los
    // mails vive aparte, en services/MailConfig, como mailMessageSource.
    @Bean
    public MessageSource messageSource() {
        final ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/messages");
        ms.setDefaultEncoding("UTF-8");
        // Relee los .properties cada 5 segundos en vez de una sola vez al arrancar:
        // conviene en desarrollo, y el costo en produccion es insignificante.
        ms.setCacheSeconds(5);
        // messages_es.properties esta vacio a proposito (el espanol vive en
        // messages.properties). Sin esto, ante una clave que falta en el idioma
        // pedido Spring probaria primero el idioma del sistema operativo del
        // servidor (en pampero puede ser ingles) antes de caer al archivo base.
        ms.setFallbackToSystemLocale(false);
        return ms;
    }

    // @EnableWebMvc registra, en ESTE contexto, su propio bean "localeResolver" (un
    // AcceptHeaderLocaleResolver, que solo lee el header del navegador y no se puede cambiar:
    // setLocale() tira UnsupportedOperationException). Como el nombre es el mismo, ese bean tapa
    // al de RootConfig para todo lo que se inyecta aca (LocaleController, el DispatcherServlet), y
    // el boton de idioma terminaba en un 500. Redefinirlo con el mismo nombre hace que gane este.
    // Es el mismo resolver que el de RootConfig (la cookie es el estado, no el objeto), asi que
    // lo que escribe aca lo lee tambien el contexto raiz.
    @Bean
    public LocaleResolver localeResolver() {
        return RootConfig.cookieLocaleResolver();
    }

    @Bean
    public ViewResolver viewResolver() {
        final InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setViewClass(JstlView.class);
        viewResolver.setPrefix("/WEB-INF/jsp/");
        viewResolver.setSuffix(".jsp");
        return viewResolver;
    }

    // El servidor (Tomcat/Jetty) acepta el archivo gracias al <multipart-config> del web.xml.
    // Este bean es la otra mitad: hace que Spring lo convierta en un MultipartFile usable.
    // OJO: el metodo TIENE que llamarse multipartResolver, Spring lo busca por nombre.
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }

    // Los services viven en el contexto raiz (ver RootConfig); este contexto, hijo del raiz,
    // los ve y los inyecta para los interceptores de abajo.
    @Autowired
    private InboxService inboxService;
    @Autowired
    private AccountSummaryService accountSummaryService;
    @Autowired
    private UserService userService;

    // Validador de los forms (@Valid) conectado al messageSource de arriba: asi los
    // mensajes de los validadores propios (webapp/validation), que son claves del
    // estilo "{ValidEventDate.past}", salen en el idioma del pedido y no con un texto
    // fijo. Es un bean (y no un new suelto en getValidator()) para que Spring le
    // inyecte las dependencias a los validadores (CategoriasValidator usa
    // CategoryService con @Autowired).
    @Bean
    public LocalValidatorFactoryBean validator() {
        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource());
        return validator;
    }

    @Override
    public Validator getValidator() {
        return validator();
    }

    // El numerito de la bandeja en la navbar de todas las paginas (ver UnreadInboxInterceptor).
    // El corte de sesion de las cuentas suspendidas ya no es un interceptor: ver SessionExpiringListener.
    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(new UnreadInboxInterceptor(inboxService));
        // La barra lateral de la cuenta: solo las paginas de /perfil (ver AccountSidebarInterceptor).
        registry.addInterceptor(new AccountSidebarInterceptor(accountSummaryService, userService))
                .addPathPatterns("/perfil/**");
        registry.addInterceptor(new CurrentLocaleInterceptor());
    }

    // Spring MVC, por defecto, enruta "/ruta/" al mismo handler que "/ruta". Los antMatchers de Spring
    // Security (WebAuthConfig) NO: "/admin/usuarios/5/revocar/" no matcheaba la regla de SUPERADMIN, caia
    // en "/admin/**" (cualquier ADMIN) y aun asi llegaba al controller. Sin la tolerancia de la barra
    // final, las dos capas ven exactamente las mismas URLs y una regla de ruta no se puede esquivar.
    // Ningun link, redirect ni form de la app termina en "/".
    @Override
    public void configurePathMatch(final PathMatchConfigurer configurer) {
        configurer.setUseTrailingSlashMatch(false);
    }

    // Esto es lo que mostro Sotuyo en clase: le dice a Spring que las carpetas
    // /css y /js NO tienen que pasar por el Front Controller (DispatcherServlet).
    // Son archivos estaticos, se sirven directo, no son una ruta nuestra (@RequestMapping).
    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**").addResourceLocations("/css/");
        registry.addResourceHandler("/js/**").addResourceLocations("/js/");
        registry.addResourceHandler("/favicon.svg").addResourceLocations("/");
    }
}
