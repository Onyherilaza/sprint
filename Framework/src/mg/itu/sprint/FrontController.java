package mg.itu.sprint;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.nio.Buffer;
import java.util.Map;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Part;
import jakarta.servlet.annotation.MultipartConfig;

import mg.itu.utils.Scanner;
import mg.itu.utils.TimestampAdapter;
import mg.itu.utils.Mapping;
import mg.itu.utils.ModelView;
import mg.itu.utils.MySession;
import mg.itu.utils.FileMap;
import mg.itu.utils.JsonReader;
import mg.itu.utils.exception.*;
import mg.itu.utils.Exportation;

import mg.itu.annotation.Param;
import mg.itu.annotation.RequestBody;
import mg.itu.annotation.RestAPI;
import mg.itu.annotation.IfError;
import mg.itu.annotation.Authentification;
import mg.itu.annotation.type.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@MultipartConfig
public class FrontController extends HttpServlet {
    Map<String, Mapping> urlMap;
    ArrayList<String> urlView;
    String authSessionName;
    String message;
    boolean visited;
    static String uriOrg = "";

    public void init() throws ServletException {
        visited = false;
        String controllPackage = this.getInitParameter("controllPackage");
        authSessionName = this.getInitParameter("authSessionName");
        TimestampAdapter.setSdf(this.getInitParameter("formatDate"));
        try {
            this.urlMap = Scanner.scanMethod(controllPackage);
            this.urlView = Scanner.scanView("../..");
        } catch (Exception e) {
            message = "Erreur au niveau du build du projet. Veuillez consulter votre terminal";
            e.printStackTrace();
        }
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse rep) throws ServletException, IOException {
        processRequest(req, rep);
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse rep) throws ServletException, IOException {
        processRequest(req, rep);
    }

    protected void processRequest(HttpServletRequest req, HttpServletResponse rep)
            throws ServletException, IOException {
        String prevu = req.getMethod();
        Map<String, String> paramMap = new HashMap<>();
        boolean ignoreConstraint = false;
        HttpSession sess = req.getSession();
        if(req.getAttribute("ignore") != null){
            ignoreConstraint = true;
            prevu = "GET";
            req.removeAttribute("ignore");
        }
        if (message != null) {
            displayError(message, "400", rep);
        } else {
            String url = req.getRequestURL().toString();
            System.out.println("url passage:"+url);
            String[] urP = url.split("\\?");
            String[] urlParts = urP[0].split("/");
            int i = 1;
            String urlTarget = "/" + urlParts[urlParts.length - 1];
            boolean ver = false;
            try {
                this.methodExist(url);
                while (i < urlParts.length) {
                    if (this.urlMap.containsKey(urlTarget)) {
                        Mapping mapping = this.urlMap.get(urlTarget);
                        if(!mapping.contains(prevu) && !ignoreConstraint){
                            displayError("Erreur de requête : Une requête de type "+prevu+" est attendue", "404", rep);
                            return;
                        }
                        try {
                            Object obj = executeMethode(mapping, req,prevu,rep,authSessionName,this.urlMap);
                            if (obj instanceof String) {
                                if(isJson((String)obj)){
                                    PrintWriter out = rep.getWriter();
                                    rep.setContentType("application/json");
                                    rep.setCharacterEncoding("UTF-8");
                                    out.println((String) obj);
                                }
                            } else if (obj instanceof ModelView) {
                                ModelView modelV = (ModelView) obj;
                                Map<String, Object> map = modelV.getData();
                                try {
                                    this.viewExist(modelV.getViewUrl());
                                    RequestDispatcher dispat = req.getRequestDispatcher(modelV.getViewUrl());
                                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                                        String dataName = (String) entry.getKey();
                                        Object data = entry.getValue();
                                        req.setAttribute(dataName, data);
                                    }
                                    dispat.forward(req, rep);
                                } catch (ExceptionFramework e) {
                                    try{
                                        String uri = modelV.getViewUrl().split("\\?")[0];
                                        this.methodExist(uri);
                                        rep.sendRedirect(modelV.getViewUrl());
                                        return;
                                    }catch(ExceptionFramework ex){
                                        e.printStackTrace();
                                        ex.printStackTrace();
                                        displayError(e.getMessage(), e.getCode(), rep);
                                        return;
                                    }
                                }
                            } else if(obj instanceof Exportation){
                                Exportation export = (Exportation)obj;
                                String type = export.getType();
                                switch (type) {
                                    case "csv":
                                        rep.setContentType("text/csv");
                                        break;
                                    case "pdf":
                                        rep.setContentType("application/pdf");
                                        break;
                                    default:
                                        break;
                                }
                                rep.setHeader("Content-Disposition", "attachment; filename=\"document." + type + "\"");
                                try(OutputStream stream = rep.getOutputStream()){
                                    stream.write(export.getBytes());
                                    stream.flush();
                                }
                            }
                        } catch (ExceptionFramework e) {
                            e.printStackTrace();
                            displayError(e.getMessage(), e.getCode(), rep);
                            return;
                        }
                        ver = true;
                        break;
                    } else {
                        urlTarget = "/" + urlParts[urlParts.length - (i + 1)] + urlTarget;
                    }
                    i++;
                }
            } catch (Exception e) {
                e.printStackTrace();
                displayError(e.getMessage(), "400", rep);
                return;
            }
        }
    }

    public static Object executeMethode(Mapping target,HttpServletRequest req,String prevu,HttpServletResponse rep,String sessionName,Map<String,Mapping> urlMap) throws Exception{
        HttpSession session = req.getSession();

        Enumeration<String> paramNames = req.getParameterNames();
        ArrayList<String> parametersNames = new ArrayList<>();
        while(paramNames.hasMoreElements()){
            String paramName = paramNames.nextElement();
            parametersNames.add(paramName);
        } 

        String className = target.getClassName();
        String methodeName = target.getMethodName(prevu);
        System.out.println("prevu:"+methodeName);
        Class<?>cl = Class.forName(className);

        Authentification authM = cl.getAnnotation(Authentification.class);
        System.out.println(sessionName);
        if(authM != null){
            String[] authorizedRoles = authM.role();
            String role = (String)session.getAttribute(sessionName);
            if(!Scanner.isValidProfil(role,authorizedRoles)){
                throw new AuthException("Insufficient privileges");
            }
        }

        Method[] mes = cl.getDeclaredMethods();
        boolean hasAnnot = true;
        String uriError = "";
        for(Method m : mes){
            if(methodeName.compareTo(m.getName()) == 0){
                Method me = m;
                Authentification auth = me.getAnnotation(Authentification.class);
                if(auth != null){
                    String[] authorizedRoles = auth.role();
                    String role = (String)session.getAttribute(sessionName);
                    if(!Scanner.isValidProfil(role,authorizedRoles)){
                        throw new AuthException("Insufficient privileges");
                    }
                }
                Parameter[] parms = me.getParameters();
                int countParam = parms.length;
                Object instance = cl.getDeclaredConstructor().newInstance();
                Object obj = null;
                boolean isRestAPI = false;
                if(me.getAnnotation(RestAPI.class) != null){
                    isRestAPI = true;
                }
                if(countParam >= 1){
                    ArrayList<Object> paramO = new ArrayList<>();
                    ArrayList<String>passage = new ArrayList<>();
                    boolean hasError = false;
                    for(Parameter p : parms){
                        Class<?> paramType = p.getType();
                        String typeName = paramType.getSimpleName();
                        String annot;
                        if(paramType.getSimpleName().compareTo("MySession") == 0){
                            MySession sess = (MySession)(paramType.getDeclaredConstructor(HttpSession.class).newInstance(session));
                            paramO.add(sess);
                        } else if(paramType.getSimpleName().compareTo("FileMap") == 0){
                            Object[] fileResult = fileHandling(req, p);
                            FileMap files = null;
                            
                            if(fileResult != null && fileResult.length > 0 && fileResult[0] != null) {
                                files = (FileMap)fileResult[0];
                            }
                            
                            // Toujours ajouter le FileMap, même s'il est null
                            paramO.add(files);
                        }else if(p.getAnnotation(RequestBody.class) != null){
                            BufferedReader reader = null;
                            try{
                                reader = req.getReader();
                                Object param = JsonReader.readJson(reader, paramType);
                                paramO.add(param);
                            }catch(Exception e){
                                e.printStackTrace();
                                throw new BadRequestException("Format du json incorrect");
                            }finally{
                                if(reader != null) reader.close();
                            }
                        }
                        else{
                            if(p.getAnnotation(Param.class) != null){
                                annot = p.getAnnotation(Param.class).name();
                            }else{
                                throw new Exception(" Erreur: pas d'annotation");
                            } 

                            Map<String,ArrayList<String>> errorMessage = new HashMap<>();
                            for(String par : parametersNames){
                                String[] paramParts = par.split("_");
                                String argName = "";
                                if(paramParts.length > 1){
                                    String objName = paramParts[0];
                                    argName = paramParts[1];
                                    if(annot.compareTo(objName) == 0){
                                        if(!passage.contains(annot)){
                                            Object instanceParam = paramType.getDeclaredConstructor().newInstance();
                                            paramO.add(instanceParam);
                                            passage.add(annot);
                                        }
                                        Object value = null; 
                                        try{
                                            Field f = Scanner.takeField(paramType,argName);
                                            req.setAttribute(par,req.getParameter(par));
                                            Annotation[] mAnnots = f.getAnnotations();
                                            for(Annotation ann: mAnnots){
                                                try{
                                                    value = Scanner.convertParameterValueWithAnnot(paramType, req.getParameter(par), argName,ann,value,mAnnots.length); 
                                                }catch(Exception e){
                                                    hasError = true;
                                                    if(errorMessage.get(par) == null){
                                                        errorMessage.put(par,new ArrayList<>());
                                                    }
                                                    errorMessage.get(par).add(e.getMessage());
                                                }
                                            }
                                            Object inst = paramO.get(paramO.size() - 1);
                                            String methName = "set"+argName.substring(0,1).toUpperCase() + argName.substring(1);
                                            Method set;
                                            if (value instanceof Integer) {
                                                try{
                                                    set = paramType.getMethod(methName, int.class); 
                                                } catch(Exception e){
                                                    set = paramType.getMethod(methName, value.getClass());
                                                }
                                            } else{
                                                set = paramType.getMethod(methName, value.getClass());   
                                            }
                                            set.invoke(inst, value);
                                        } catch(Exception e){
                                            e.printStackTrace();
                                            displayError(e.getMessage(), "408", rep);
                                            return null;
                                        }
                                    }
                                } else{
                                    argName = paramParts[0];
                                    if(argName.compareTo(annot) == 0){
                                        Object value = null;
                                        value = Scanner.convertParameterValue(paramType, req.getParameter(argName),argName,value);
                                        paramO.add(value);
                                        break;
                                    }
                                }
                            }

                            if(hasError){
                                if(me.getAnnotation(IfError.class) != null){
                                    uriError = me.getAnnotation(IfError.class).url(); 
                                    System.out.println("direction: "+uriError);         
                                } else{
                                    String referer = req.getHeader("referer");
                                    uriError = Scanner.extractUrl(referer,urlMap);
                                    System.out.println("error redirect : "+uriError);
                                }
                                RequestDispatcher dispat = req.getRequestDispatcher(uriError);
                                req.setAttribute("error",errorMessage);
                                HttpServletRequest wrap = new HttpServletRequestWrapper(req){
                                    @Override
                                    public String getMethod(){
                                        return "GET";
                                    }
                                };
                                dispat.forward(wrap,rep);
                                return null;
                            } else{
                                uriOrg = "";
                            }
                        }
                    }

                    Object[] p = paramO.toArray();
                    
                    System.out.println("=== DEBUG PARAMETRES AVANT INVOCATION ===");
                    System.out.println("Nombre de paramètres : " + p.length);
                    for(int idx = 0; idx < p.length; idx++) {
                        if(p[idx] == null) {
                            System.out.println("  Param[" + idx + "]: NULL ⚠️");
                        } else {
                            System.out.println("  Param[" + idx + "]: " + p[idx].toString() + 
                                             " (Type: " + p[idx].getClass().getName() + ")");
                        }
                    }
                    System.out.println("==========================================");
                    
                    Parameter[] methodParams = me.getParameters();
                    for(int idx = 0; idx < methodParams.length; idx++) {
                        Parameter methodParam = methodParams[idx];
                        
                        if(methodParam.getType().equals(FileMap.class) || 
                           methodParam.getType().equals(MySession.class)) {
                            continue;
                        }
                        
                        if(idx < p.length && p[idx] == null) {
                            String paramName = "inconnu";
                            if(methodParam.getAnnotation(Param.class) != null) {
                                paramName = methodParam.getAnnotation(Param.class).name();
                            }
                            
                            System.err.println("ERREUR: Paramètre requis manquant - " + 
                                             paramName + " (index " + idx + 
                                             ", type " + methodParam.getType().getSimpleName() + ")");
                            throw new BadRequestException("Paramètre requis manquant: " + paramName);
                        }
                    }
                    
                    try{
                        System.out.println("Invocation de la méthode avec " + p.length + " paramètres");
                        obj = me.invoke(instance, p);
                        System.out.println("Méthode exécutée avec succès");
                    }catch(Exception e){
                        System.err.println("ERREUR lors de l'invocation de la méthode:");
                        System.err.println("  Méthode: " + methodeName);
                        System.err.println("  Classe: " + className);
                        System.err.println("  Nombre params: " + p.length);
                        
                        Parameter[] expectedParams = me.getParameters();
                        System.err.println("  Types attendus vs reçus:");
                        for(int idx = 0; idx < Math.max(expectedParams.length, p.length); idx++) {
                            String expected = (idx < expectedParams.length) ? 
                                            expectedParams[idx].getType().getSimpleName() : "N/A";
                            String received = (idx < p.length && p[idx] != null) ? 
                                            p[idx].getClass().getSimpleName() : "NULL";
                            System.err.println("    [" + idx + "] Attendu: " + expected + 
                                             " | Reçu: " + received);
                        }
                        
                        e.printStackTrace();
                        throw new BadRequestException("Erreur lors de l'exécution de la méthode: " + e.getMessage());
                    }
                }
                else{
                    try{
                        obj= me.invoke(instance);
                    }catch(Exception e){
                        e.printStackTrace();
                        throw new BadRequestException(e.getMessage());
                    }
                }
                if(!(obj instanceof String) &&!(obj instanceof ModelView) && !(obj instanceof Exportation)){
                    String objType = (obj != null) ? obj.getClass().getSimpleName() : "null";
                    throw new BadRequestException("La methode "+methodeName+" renvoie un objet de type "+objType+".\n Types attendus : ModelView, String, Exportation");
                }
                if(isRestAPI){
                    System.out.println("Est une exportation "+(obj instanceof Exportation));
                    if(!(obj instanceof Exportation)){
                        if(obj != null && obj.getClass().getSimpleName().compareTo("ModelView") == 0){
                            obj = ((ModelView)obj).getData();
                        }
                        Gson gson = new Gson();
                        obj = gson.toJson(obj);
                    }
                }
                return obj;
            }
        }
        return null;
    }

    public void viewExist(String viewUrl) throws Exception {
        ArrayList<String> listView = this.urlView;
        if (!listView.contains(viewUrl)) {
            throw new NotFoudException("La page " + viewUrl + " n'existe pas!");
        }
    }
    
    public void methodExist(String urlMethod) throws Exception {
        Map<String, Mapping> urlList = this.urlMap;
        String[] urlParts = urlMethod.split("/");
        String urlTarget = "/" + urlParts[urlParts.length - 1];
        int i = 1;
        if (this.urlMap.containsKey(urlTarget)) {
            return;
        }
        while (i < urlParts.length) {
            if (this.urlMap.containsKey(urlTarget)) {
                return;
            }
            urlTarget = "/" + urlParts[urlParts.length - (i + 1)] + urlTarget;
            i++;
        }
        throw new NotFoudException("L'url " + urlMethod + " n'est associé à aucune méthode du projet");
    }

    public static boolean isJson(String value){ 
        try{
            JsonElement jsonElement = JsonParser.parseString(value);
            return jsonElement.isJsonObject() || jsonElement.isJsonArray();
        } catch(JsonSyntaxException e){
            return false;
        }
    }

    protected static void displayError(String error,String code,HttpServletResponse rep) throws IOException,ServletException{
        rep.setContentType("text/html");
        PrintWriter out = rep.getWriter();

        out.println("<!DOCTYPE html>");
        out.println("<html>");
        out.println("<head>");
        out.println("<title>Erreur</title>");

        out.println("<style>");
        out.println("body { font-family: Arial, sans-serif; background-color: #f0f0f0; }");
        out.println("h1 { color: red; }");
        out.println("p { font-size: 16px; }");
        out.println("ul { list-style-type: none; }");
        out.println("li { background-color: #e0e0e0; margin: 5px 0; padding: 10px; border-radius: 5px; }");
        out.println("</style>");

        out.println("</head>");
        out.println("<body>");
        out.println("<h1>Vous avez rencontre un probleme</h1>");
        out.println("<p>Code d'erreur : <strong>"+code+"</strong></p>");
        out.println("<p>Message: <strong>"+error+"</strong></p>");
        out.println("</ul>");
        out.println("</body>");
        out.println("</html>");
    }

    static String getFileName(Part part){
        if(part == null) return null;
        String disposition = part.getHeader("content-disposition");
        if(disposition == null) return null;
        for(String content: disposition.split(";")){
            if(content.trim().startsWith("filename")){
                return content.substring(content.indexOf('=') + 1).trim().replace("\"","");
            }
        }
        return null;
    }
    
    static byte[] getByte(Part part) throws Exception{
        if(part == null) return new byte[0];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        InputStream fileContent = null;
        try{
            fileContent = part.getInputStream();
            byte[] data = new byte[1024];
            int reader;
            while((reader = fileContent.read(data,0,data.length)) != -1){
                buffer.write(data,0,reader);
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            if(fileContent != null) fileContent.close();
        }
        return buffer.toByteArray();
    }

    static FileMap getFileInfo(HttpServletRequest req,Parameter p) throws Exception{
        if(p.getType().equals(FileMap.class)){
            Param annotation = p.getAnnotation(Param.class);
            if(annotation == null) return null;
            try {
                Part part = req.getPart(annotation.name());
                if(part == null) return null;
                String fileName = getFileName(part);
                if(fileName == null || fileName.trim().isEmpty()) return null;
                byte[] b = getByte(part);
                return new FileMap(fileName, b);
            } catch(Exception e) {
                System.out.println("Aucun fichier trouvé pour " + annotation.name());
                return null;
            }
        }
        return null;
    }

    static Object[] fileHandling(HttpServletRequest req,Parameter param) throws Exception{
        Object[] files = null;
        try{
            FileMap fileInfo = getFileInfo(req, param);
            if(fileInfo != null){
                files = new Object[1];
                files[0] = fileInfo;
            }
        }catch(Exception e){
            System.err.println("Erreur lors du traitement du fichier: " + e.getMessage());
        }
        return files;
    }
}