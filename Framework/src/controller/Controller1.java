package controller;

import java.util.Vector;

import Annotations.Controller;
import Annotations.Get;
import Annotations.Param;
import Annotations.Post;
import Annotations.RequestBody;
import Annotations.Restapi;
import mg.prom16.ModelView;
import models.Employe;

@Controller
public class Controller1 {

    @Get(value = "/message")
    public String get_message(String message) {
        System.out.println("Message reçu : " + message); // Log
        return message;
    }

    @Post(value = "/saveEmploye")
    public ModelView saveEmploye(@RequestBody Employe employe) {
        System.out.println("Tentative d'enregistrement de l'employé : " + employe); // Log
        ModelView mv = new ModelView();
        if (employe.getNom() == null || employe.getPrenom() == null) {
            mv.setUrl("/views/ErrorPage.jsp");
            mv.addObject("message", "Erreur : Les informations de l'employé sont incomplètes.");
            return mv;
        }
        mv.setUrl("/views/Employe.jsp");
        mv.addObject("employe", employe);
        return mv;
    }

    @Get(value = "/pageNotFound")
    public ModelView pageNotFound() {
        ModelView modelView = new ModelView();
        modelView.setUrl("/views/ErrorPage.jsp");
        modelView.addObject("message", "Page Not Found");
        modelView.addObject("code", 404);
        return modelView;
    }

    @Get(value = "/date")
    public java.util.Date get_Date() {
        return new java.util.Date();
    }

    @Get(value = "/employe")
    public ModelView get_employe(@Param(name = "id") String id, @Param(name = "nom") String nom, @Param(name = "prenom") String prenom) {
        ModelView mv = new ModelView();
        mv.setUrl("/views/Employe.jsp");
        mv.addObject("id", id);
        mv.addObject("nom", nom);
        mv.addObject("prenom", prenom);
        return mv;
    }

    @Restapi
    @Get(value = "/listemploye")
    public ModelView list_employe() {
        ModelView mv = new ModelView();

        Employe employe1 = new Employe();
        employe1.setId("1");
        employe1.setNom("Rakotoarilova");
        employe1.setPrenom("Olivier");

        Employe employe2 = new Employe();
        employe2.setId("2");
        employe2.setNom("Razafy");
        employe2.setPrenom("Olona");

        Vector<Employe> listEmploye = new Vector<>();
        listEmploye.add(employe1);
        listEmploye.add(employe2);

        mv.addObject("listemploye", listEmploye);
        System.out.println("Liste des employés retournée : " + listEmploye); // Log
        return mv;
    }

    @Restapi
    @Get(value = "/restAPI")
    public Employe get_Employe() {
        Employe employe2 = new Employe();
        employe2.setId("2");
        employe2.setNom("Rakoto");
        employe2.setPrenom("Bozy");

        System.out.println("Employé BOZY retourné : " + employe2); // Log
        return employe2;
    }
}
