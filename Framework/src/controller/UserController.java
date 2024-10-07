package controller;

import mg.prom16.ModelView;
import mg.prom16.MySession;

import java.util.ArrayList;
import java.util.List;

import Annotations.*;

@Controller
public class UserController {

    @Get("/login")
    public String login(MySession mySession, @Param(name = "username") String username, @Param(name = "password") String password) {
        if ("admin".equals(username) && "admin".equals(password)) {
            mySession.add("username", username);
            List<String> userData = new ArrayList<>();
            userData.add("Yohan");
            userData.add("RAKOTOARISON");
            mySession.add("userData", userData);
            return "redirect:/userInfo";
        } else {
            return "login_failde";
        }
    }

    @SuppressWarnings("unchecked")
    @Get("/userInfo")
    public ModelView showData(MySession mySession) {
        List<String> userData = (List<String>) mySession.get("userData");
        ModelView modelView = new ModelView();
        modelView.setUrl("/views/UserInfo.jsp");
        modelView.addObject("userData", userData);
        return modelView;
    }

    @Get("/logout")
    public String logout(MySession mySession) {
        mySession.delete("username");
        mySession.delete("userData");
        return "redirect:/views/Login.jsp";
    }
}

