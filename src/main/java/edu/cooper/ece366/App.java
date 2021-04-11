package edu.cooper.ece366;

import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.initExceptionHandler;
import static spark.Spark.options;
import static spark.Spark.post;
import static spark.Spark.staticFiles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.cooper.ece366.auth.authLobby.AuthLobbyStoreImpl;
import edu.cooper.ece366.auth.authUser.AuthUserStoreImpl;
import edu.cooper.ece366.framework.User;
import edu.cooper.ece366.framework.UserBuilder;
import edu.cooper.ece366.handler.Handler;
import edu.cooper.ece366.service.SwipingServiceImpl;
import edu.cooper.ece366.store.*;
import io.norberg.automatter.AutoMatter;
import io.norberg.automatter.gson.AutoMatterTypeAdapterFactory;
import spark.Request;
import spark.Response;
import spark.ResponseTransformer;

import java.util.HashMap;
import java.util.Optional;
import java.util.Map;


public class App 
{
    public static void main( String[] args )
    {

        staticFiles.location("/public"); // Static files

        Gson gson =
                new GsonBuilder().registerTypeAdapterFactory(new AutoMatterTypeAdapterFactory()).create();

        ResponseTransformer responseTransformer =
                model -> {
                    if (model == null){
                        return "";
                    }
                    return gson.toJson(model);
                };

        initExceptionHandler(Throwable::printStackTrace);

        UserStore userStore = new UserStoreImpl();
        ConnectStore connectStore = new ConnectStoreImpl();
        LobbyPreferences lobbyPreferences = new LobbyPreferencesImpl();
        UserPreferences userPreferences = new UserPreferencesImpl();
        Handler handler = new Handler(connectStore, lobbyPreferences, userPreferences,
              userStore, new LobbyStoreImpl(), new RestaurantStoreImpl() ,
              new SwipingServiceImpl(connectStore, lobbyPreferences, userPreferences), new AuthUserStoreImpl(), new AuthLobbyStoreImpl(), gson);

        options(
            "/*",
            (request, response) -> {
                String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
                if (accessControlRequestHeaders != null) {
                    response.header("Access-Control-Allow-Headers", "*");
                }

                String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
                if (accessControlRequestMethod != null) {
                    response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
                    response.header("Access-Control-Allow-Methods", "*");
                }

                return "OK";
            });

        before(
            (req, res) -> {
                res.header("Access-Control-Allow-Origin", "*");
                res.header("Access-Control-Allow-Headers", "*");
                res.type("application/json");
            });

        get("/ping", (req, res) -> "OK");
        get("/me", (req, res) -> handler.me(req, res), gson::toJson);

        get("/cookie-example", App::cookieExample, responseTransformer);
        get("/header-example", App::headerExample, responseTransformer);
        get("/user/:userId", (req, res) -> handler.getUser(req), gson::toJson);
        get("/lobby/:lobbyId", (req, res) -> handler.getLobby(req), gson::toJson);

        get("/getConnectionMap", (req, res) -> handler.getConnectionMap(), gson::toJson);
        get("/getLobbyLikes", (req, res) -> handler.getLobbyMap(), gson::toJson);
        get("/getPreferenceMap", (req, res) -> handler.getPreferenceMap(), gson::toJson);

        get("/:lobbyId/init", (req, res) -> handler.initLobbyMap(req), gson::toJson);
        //get("/:lobbyId/recommendation", (req, res) -> handler.result(req), gson::toJson);

        post("/:userId/:lobbyID/:restID/like", (req, res) -> handler.like(req), gson::toJson);
        post("/:userId/:lobbyID/:restID/dislike", (req, res) -> handler.dislike(req), gson::toJson);

        post("/login", (req, res) -> handler.login(req, res), gson::toJson);
        post("/logout", (req, res) -> handler.logout(req, res), gson::toJson);

        post("/joinLobby", (req, res) -> handler.joinLobby(req, res), gson::toJson);
        post("/leaveLobby", (req, res) -> handler.leaveLobby(req, res), gson::toJson);
    }


    private static HeaderExample headerExample(final Request request, final Response response) {
        String accessToken = Optional.ofNullable(request.headers("access-token")).orElseThrow();
        response.header("current-time", "now");
        response.header("my-app-header", "yeet");
        return new HeaderExampleBuilder().build();
    }

    @AutoMatter
    interface CookieExample {
        String requestCookie();

        String responseCookie();
    }

    @AutoMatter
    interface HeaderExample {
        Optional<String> request();

        Optional<String> response();
    }

    private static final Map<String, User> cookieMap = new HashMap<>();

    static {
        cookieMap.put("decafbad", new UserBuilder().ID("Pablo").nickname("Pablo").build());
    }

    // "me" endpoint

    private static User cookieExample(final Request request, final Response response) {
        String msg = Optional.ofNullable(request.cookie("user")).orElseThrow();
        //String msg = Optional.ofNullable(request.cookie("msg")).orElse("default-msg");

        User user = cookieMap.get(msg);
//
        if (user == null) {
          response.status(401);
          return null;
        }

            //response.cookie("server-msg", "yeet");

            //return new CookieExampleBuilder().requestCookie(msg).responseCookie("yeet").build();
        return user;
      }

}
