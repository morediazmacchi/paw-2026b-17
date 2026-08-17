package ar.itba.edu.paw.webapp.config;

import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.viewResolver;
import org.springframework.web.servlet.view.JstlView;
import org.springframework.web.servlet.view.InternalResoureViewResolver;

@EnableWebMvc
@ComponentScan({
        "ar.edu.itba.paw.services",
        "ar.edu.itba.paw.webapp.controller",
})
@Configuration
public class WebConfig{

    @Bean
    public ViewResolver viewResolver() {
        final InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setViewClass(JstlView.class);
        viewResolver.setPrefix("/WEB-INF/jsp/");
        viewResolver.setSuffix(".jsp");
        return viewResolver;
    }
}
