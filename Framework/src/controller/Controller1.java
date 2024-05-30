package controller;

import Annotations.*;

@Controller
public class Controller1 {

    @Get(value = "/helloWord")
    public String helloWord() { 
        return "Hello Word";
    }

    @Get(value = "/message")
    public String message(String message) {
        return message;
    }

    @Get(value = "/pageNotFound")
    public int pageNotFound() {
        return 404;
    }
}