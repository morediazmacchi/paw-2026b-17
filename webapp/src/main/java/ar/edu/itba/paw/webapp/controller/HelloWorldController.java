package ar.edu.itba.paw.webapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.beans.factory.annotation.Autowired;
import ar.edu.itba.paw.services.UserServiceImpl;

@Controller
public class HelloWorldController {
    private final UserServiceImpl us;

    @Autowired
    public HelloWorldController(final UserServiceImpl us) {
        this.us = us;
    }

    @RequestMapping("/")
    public ModelAndView helloWorld() {
        final ModelAndView mav = new ModelAndView("helloworld/index");
        mav.addObject("username", us.findById(1l));
        return mav;
    }
}
