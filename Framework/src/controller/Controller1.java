package controller;

import Annotations.*;
import mg.prom16.ModelView;

@Controller
public class Controller1 {

    @Get(value = "/test")
    public String method1() {
        return "Sprint4";
    }

    @Get(value = "/pageNotFound")
    public ModelView method2() { 
        ModelView modelView = new ModelView();
        modelView.setUrl("/WEB-INF/views/ErrorPage.jsp");
        modelView.addObject("message", "Page Not Found");
        modelView.addObject("code", 404);
        return modelView;
    }

    // @Get(value = "/date")
    // public java.util.Date method3() {
    //     return new java.util.Date();
    // }
}