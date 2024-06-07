package mg.prom16;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import Annotations.*;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontController extends HttpServlet {

    protected List<Class<?>> list_controller = new ArrayList<>();
    protected Map<String, Mapping> urlMappings = new HashMap<>();
    protected Set<String> accessedUrls = new HashSet<>();

    protected void getControllerList(String package_name) throws ClassNotFoundException, ServletException {
        String bin_path = "WEB-INF/classes/" + package_name.replace(".", "/");

        bin_path = getServletContext().getRealPath(bin_path);

        File b = new File(bin_path);

        if (!b.exists() || !b.isDirectory()) {
            throw new ServletException("Package invalide: " + package_name);
        }

        list_controller.clear();
        
        for (File onefile : b.listFiles()) {
            if (onefile.isFile() && onefile.getName().endsWith(".class")) {
                Class<?> clazz = Class.forName(package_name + "." + onefile.getName().split(".class")[0]);
                if (clazz.isAnnotationPresent(Controller.class))
                
                list_controller.add(clazz);

                for (Method method : clazz.getMethods()) {
                    if (method.isAnnotationPresent(Get.class)) {
                        String key = method.getAnnotation(Get.class).value();
                        if (urlMappings.containsKey(key)) {
                            throw new ServletException("MISY URL MITOVY: " + key);
                        }
                        Mapping mapping = new Mapping(clazz.getName(), method.getName());
                        urlMappings.put(key, mapping);
                    }
                }
            }
        }
    }

    protected Object invoke_Method(String className, String methodName) {
        Object returnValue = null;
        try {
            Class<?> clazz = Class.forName(className);
            Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            returnValue = method.invoke(instance);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        return returnValue;
    }

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            getControllerList(getServletContext().getInitParameter("controllerPackage"));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new ServletException("Failed to load classes from the specified package", e);
        } catch (ServletException e) {
            e.printStackTrace();
            throw new ServletException("Initialization error: " + e.getMessage(), e);
        }
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        String url = request.getRequestURI().substring(request.getContextPath().length());
        
        try (PrintWriter out = response.getWriter()) {

            // if (accessedUrls.contains(url)) {
            //     response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            //     out.println("Error: URL deja existant.");
            //     return;
            // }

            // accessedUrls.add(url);

            Mapping mapping = urlMappings.get(url);

            if (mapping != null) {
                Object returnValue = invoke_Method(mapping.getClassName(), mapping.getMethodName());

                if (returnValue instanceof String) {
                    out.println("<p>Contenue de la methode <strong>" + mapping.getMethodName() + "</strong> : " + (String) returnValue + "</p>");
                } else if (returnValue instanceof ModelView) {
                    ModelView modelView = (ModelView) returnValue;
                    String viewUrl = modelView.getUrl();
                    HashMap<String, Object> data = modelView.getData();

                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        request.setAttribute(entry.getKey(), entry.getValue());
                    }

                    RequestDispatcher dispatcher = request.getRequestDispatcher(viewUrl);
                    dispatcher.forward(request, response);
                    
                // } else if (returnValue instanceof Date) {
                //     out.println("<p>Contenue de la methode <strong>" + mapping.getMethodName() + "</strong> : " + returnValue.toString() + "</p>");
                } else {
                    out.println("Type de retour non reconnu: " + returnValue.getClass().getName());

                }
            } else {
                out.println("Pas de methode Get associer a l'URL: " + url);
            }

        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
}
