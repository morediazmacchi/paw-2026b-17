package ar.edu.itba.paw.webapp.controller;

import java.util.Date;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class DemoController {

    @RequestMapping("/demo")
    public ModelAndView demo() {
        final ModelAndView mav = new ModelAndView("demo");
        mav.addObject("hoy", new Date());
        return mav;
    }
}
