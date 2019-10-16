package org.builder.session.jackson.console.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ConsoleController {

    @RequestMapping(value = "/",
                    method = RequestMethod.GET)
    public String index(Model model) {
        log.debug("Starting Index page!");
        return "index";
    }

    @RequestMapping(value = "/results",
                    method = RequestMethod.POST)
    public String results(Model model) {
        log.debug("Starting Results page!");
        return "results";
    }
}
