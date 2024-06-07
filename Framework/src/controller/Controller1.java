package controller;

import java.text.SimpleDateFormat;
import java.util.Date;

import Annotations.*;
import mg.prom16.ModelView;

@Controller
public class Controller1 {

    @Get(value = "/test")
    public String method1() {
        return "HELLOO!";
    }

    @Get(value = "/pageNotFound")
    public ModelView method2() { 
        ModelView modelView = new ModelView();
        modelView.setUrl("/WEB-INF/views/ErrorPage.jsp");
        modelView.addObject("message", "Page Not Found");
        modelView.addObject("code", 404);
        return modelView;
    }

    @Get(value = "/date")
    public String method3() {
        Date now = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return formatter.format(now);
    }

    @Get(value = "/date1")
    public java.util.Date method4() {
        return new java.util.Date();
    }
}