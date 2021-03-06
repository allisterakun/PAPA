package edu.cooper.ece366.store;

import edu.cooper.ece366.DBconnection.DBconnection;
import edu.cooper.ece366.categories.Restaurant;
import edu.cooper.ece366.categories.RestaurantBuilder;
import edu.cooper.ece366.framework.Lobby;
import edu.cooper.ece366.framework.LobbyBuilder;
import edu.cooper.ece366.yelpAPI.YelpApi;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.*;

public class LobbyStoreImpl implements LobbyStore {

    DataSource dbcp;
    Connection conn = null;

    @Override
    public Lobby getCurrentLobby(DBconnection com_in, String lobbyID) throws SQLException {
        this.dbcp = com_in.getDataSource();
        this.conn = dbcp.getConnection();
        String returnLobbyID = null, returnLobbyCode = null, returnLobbyOwner = null;
        try {
            PreparedStatement getCuisineID = conn.prepareStatement(
                    "SELECT * FROM lobbies WHERE ID=?");
            getCuisineID.setString(1, lobbyID);
            ResultSet rs = getCuisineID.executeQuery();
            while(rs.next()){
                returnLobbyID = rs.getString("ID");
                returnLobbyCode = rs.getString("code");
                returnLobbyOwner = rs.getString("owner");
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        finally {
            conn.close();
        }
        return new LobbyBuilder().ID(returnLobbyID).code(returnLobbyCode).ownerID(returnLobbyOwner).build();
    }

    @Override
    public List<Restaurant> getRestaurantList(DBconnection com_in, String lobbyID) throws SQLException {
        this.dbcp = com_in.getDataSource();
        this.conn = dbcp.getConnection();

        List<String> restaurantIDList = new ArrayList<>();
        List<Restaurant> returnList = new ArrayList<>();

        String returnID = null, returnAlias = null, returnName = null, returnDisplayPhone = null, returnPrice = null;
        String returnCuisine = null, returnAddress = null, returnPhoneNumber = null, returnOperatingHours = null;
        String returnYelpInfo = null;
        Boolean returnIsOpenNow = null;
        Double returnRating = null;
        try{
            PreparedStatement getRestaurantID = conn.prepareStatement(
                    "SELECT restaurantID FROM lobby_preferred_restaurants WHERE lobbyID=?");
            getRestaurantID.setString(1, lobbyID);
            ResultSet rs = getRestaurantID.executeQuery();
            while(rs.next()){
                restaurantIDList.add(rs.getString("restaurantID"));
            }
        } catch(SQLException throwables){
            throwables.printStackTrace();
        }

        for (String restID : restaurantIDList) {

            // get restaurant
            try{
                PreparedStatement getRestaurant = conn.prepareStatement(
                        "SELECT * FROM restaurants WHERE ID=?");
                getRestaurant.setString(1, restID);
                ResultSet rs1 = getRestaurant.executeQuery();
                while(rs1.next()){
                    returnID = rs1.getString("ID");
                    returnAlias = rs1.getString("alias");
                    returnName = rs1.getString("name");
                    returnDisplayPhone = rs1.getString("displayPhone");
                    returnPrice = rs1.getString("price");
                    returnPhoneNumber = rs1.getString("phone");
                    returnIsOpenNow = rs1.getBoolean("isOpenNow");
                    returnRating = rs1.getDouble("rating");

                    returnYelpInfo = rs1.getString("yelpInfo");
                    JSONObject yelpInfo = new JSONObject(returnYelpInfo);

                    returnCuisine = yelpInfo.get("categories").toString();
                    returnAddress = yelpInfo.get("location").toString();
                    if(yelpInfo.has("hours")) {
                        returnOperatingHours = yelpInfo.get("hours").toString();
                    } else {
                        returnOperatingHours = "[{\"open\":[" +
                                "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":0}," +
                                "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":1}," +
                                "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":2}," +
                                "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":3}," +
                                "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":4}," +
                                "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":5}," +
                                "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":6}]," +
                                "\"hours_type\":\"REGULAR\"," +
                                "\"is_open_now\":true}]";
                    }
                }
            } catch(SQLException throwables){
                throwables.printStackTrace();
            }

            // create restaurant object and append to list
            returnList.add(new RestaurantBuilder().ID(returnID)
                    .alias(returnAlias)
                    .name(returnName)
                    .phoneNumber(returnPhoneNumber)
                    .displayPhone(returnDisplayPhone)
                    .price(returnPrice)
                    .cuisine(returnCuisine)
                    .address(returnAddress)
                    .operatingHours(returnOperatingHours)
                    .isOpenNow(returnIsOpenNow)
                    .rating(returnRating)
                    .build());
        }
        conn.close();
        return returnList;
    }

    @Override
    public Lobby initLobby(DBconnection com_in, String ownerID, String location) throws SQLException, IOException {
        this.dbcp = com_in.getDataSource();
        this.conn = dbcp.getConnection();

        String lobbyID = UUID.randomUUID().toString();
        //String lobbyCode = UUID.randomUUID().toString();
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 6;
        Random random = new Random();

        String lobbyCode = random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        try{
            PreparedStatement insertLobby = conn.prepareStatement(
                    "INSERT INTO lobbies (ID, code, owner) " +
                            "VALUES (?, ?, ?)" +
                            "ON DUPLICATE KEY UPDATE code = ?, owner = ?");
            insertLobby.setString(1, lobbyID);
            insertLobby.setString(2, lobbyCode);
            insertLobby.setString(3, ownerID);
            insertLobby.setString(4, lobbyCode);
            insertLobby.setString(5, ownerID);

            try{
                insertLobby.executeUpdate();
            } catch (SQLException throwables) {
                System.err.println("Error when executing SQL command.");
                throwables.printStackTrace();
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        YelpApi yelpApi = new YelpApi();
        JSONArray response = yelpApi.searchForBusinessesByLocation(null, location, 10);

        ArrayList<Object> res = new ArrayList<>();

        for(Object object : response){
            res.add(object);
        }
        RestaurantStoreImpl restaurantstoreimpl = new RestaurantStoreImpl();
        for(Object r : res){
            JSONObject jsonObject = (JSONObject) r;
            String restaurantID = jsonObject.getString("id");
            JSONObject restaurantDetail = yelpApi.searchByBusinessId(jsonObject.getString("id"));
            System.out.println(restaurantDetail.toString());
            // put restaurant into restaurants
            JSONArray cuisine = (JSONArray) restaurantDetail.get("categories");
            JSONObject loc = (JSONObject) restaurantDetail.get("location");
            JSONObject coordinates = (JSONObject) restaurantDetail.get("coordinates");
            JSONArray hour;
            if(restaurantDetail.has("hours")) {
                hour = (JSONArray) restaurantDetail.get("hours");
            } else {
                hour = new JSONArray("[{\"open\":[" +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":0}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":1}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":2}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":3}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":4}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":5}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":6}]," +
                        "\"hours_type\":\"REGULAR\"," +
                        "\"is_open_now\":true}]");
            }
            String price;
            if(restaurantDetail.has("price")) {
                price = restaurantDetail.getString("price");
            } else {
                price = "none";
            }
            //if(restaurantDetail.get("price") != null) {
                //price = restaurantDetail.getString("price");
            //}
            restaurantstoreimpl.storeToDB(com_in,
                    restaurantDetail.toString(),
                    restaurantDetail.getString("id"),
                    restaurantDetail.getString("alias"),
                    restaurantDetail.getString("name"),
                    restaurantDetail.getString("phone"),
                    restaurantDetail.getString("display_phone"),
                    cuisine,
                    restaurantDetail.getDouble("rating"),
                    loc,
                    coordinates,
                    price,
                    hour);

            // put restaurant into lobbyPreference

            try{
                PreparedStatement insertLobby = conn.prepareStatement(
                        "INSERT INTO lobby_preferred_restaurants (lobbyID, restaurantID, vote)" +
                                " VALUES (?, ?, 0)" +
                                "ON DUPLICATE KEY UPDATE lobbyID = ?, restaurantID = ?");
                insertLobby.setString(1, lobbyID);
                insertLobby.setString(2, restaurantID);
                insertLobby.setString(3, lobbyID);
                insertLobby.setString(4, restaurantID);
                try{
                    insertLobby.executeUpdate();
                } catch (SQLException throwables) {
                    System.err.println("Error when executing SQL command.");
                    throwables.printStackTrace();
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }

        joinLobby(com_in, ownerID, lobbyID);
        conn.close();
        return new LobbyBuilder().ID(lobbyID).code(lobbyCode).ownerID(ownerID).build();
    }

    @Override
    public Lobby initLobbyWithKeyword(DBconnection com_in, String ownerID, String location, String keyword) throws IOException, SQLException {
        this.dbcp = com_in.getDataSource();
        this.conn = dbcp.getConnection();

        String lobbyID = UUID.randomUUID().toString();
        //String lobbyCode = UUID.randomUUID().toString();
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 6;
        Random random = new Random();

        String lobbyCode = random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        try{
            PreparedStatement insertLobby = conn.prepareStatement(
                    "INSERT INTO lobbies (ID, code, owner) " +
                            "VALUES (?, ?, ?)" +
                            "ON DUPLICATE KEY UPDATE code = ?, owner = ?");
            insertLobby.setString(1, lobbyID);
            insertLobby.setString(2, lobbyCode);
            insertLobby.setString(3, ownerID);
            insertLobby.setString(4, lobbyCode);
            insertLobby.setString(5, ownerID);

            try{
                insertLobby.executeUpdate();
            } catch (SQLException throwables) {
                System.err.println("Error when executing SQL command.");
                throwables.printStackTrace();
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        YelpApi yelpApi = new YelpApi();
        JSONArray response = yelpApi.searchForBusinessesByLocation(keyword, location, 10);

        ArrayList<Object> res = new ArrayList<>();

        for(Object object : response){
            res.add(object);
        }
        RestaurantStoreImpl restaurantstoreimpl = new RestaurantStoreImpl();
        for(Object r : res){
            JSONObject jsonObject = (JSONObject) r;
            String restaurantID = jsonObject.getString("id");
            JSONObject restaurantDetail = yelpApi.searchByBusinessId(jsonObject.getString("id"));
            System.out.println(restaurantDetail.toString());
            // put restaurant into restaurants
            JSONArray cuisine = (JSONArray) restaurantDetail.get("categories");
            JSONObject loc = (JSONObject) restaurantDetail.get("location");
            JSONObject coordinates = (JSONObject) restaurantDetail.get("coordinates");
            JSONArray hour;
            if(restaurantDetail.has("hours")) {
                hour = (JSONArray) restaurantDetail.get("hours");
            } else{
                hour = new JSONArray("[{\"open\":[" +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":0}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":1}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":2}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":3}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":4}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":5}," +
                        "{\"is_overnight\":false,\"start\":\"none\",\"end\":\"none\",\"day\":6}]," +
                        "\"hours_type\":\"REGULAR\"," +
                        "\"is_open_now\":true}]");
            }
            String price;
            if(restaurantDetail.has("price")) {
                price = restaurantDetail.getString("price");
            } else {
                price = "none";
            }
            //if(restaurantDetail.get("price") != null) {
            //price = restaurantDetail.getString("price");
            //}
            restaurantstoreimpl.storeToDB(com_in,
                    restaurantDetail.toString(),
                    restaurantDetail.getString("id"),
                    restaurantDetail.getString("alias"),
                    restaurantDetail.getString("name"),
                    restaurantDetail.getString("phone"),
                    restaurantDetail.getString("display_phone"),
                    cuisine,
                    restaurantDetail.getDouble("rating"),
                    loc,
                    coordinates,
                    price,
                    hour);

            // put restaurant into lobbyPreference

            try{
                PreparedStatement insertLobby = conn.prepareStatement(
                        "INSERT INTO lobby_preferred_restaurants (lobbyID, restaurantID, vote)" +
                                " VALUES (?, ?, 0)" +
                                "ON DUPLICATE KEY UPDATE lobbyID = ?, restaurantID = ?");
                insertLobby.setString(1, lobbyID);
                insertLobby.setString(2, restaurantID);
                insertLobby.setString(3, lobbyID);
                insertLobby.setString(4, restaurantID);
                try{
                    insertLobby.executeUpdate();
                } catch (SQLException throwables) {
                    System.err.println("Error when executing SQL command.");
                    throwables.printStackTrace();
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }

        joinLobby(com_in, ownerID, lobbyID);
        conn.close();
        return new LobbyBuilder().ID(lobbyID).code(lobbyCode).ownerID(ownerID).build();
    }

    @Override
    public Lobby joinLobby(DBconnection com_in, String userID, String lobbyID) throws SQLException {
        this.dbcp = com_in.getDataSource();
        this.conn = dbcp.getConnection();
        try{
            PreparedStatement insertUsertoLobby = conn.prepareStatement(
                    "INSERT INTO lobbyusers (lobbyID, userID)" +
                            " VALUES (?, ?)" +
                            "ON DUPLICATE KEY UPDATE lobbyID = ?, userID = ?");
            insertUsertoLobby.setString(1, lobbyID);
            insertUsertoLobby.setString(2, userID);
            insertUsertoLobby.setString(3, lobbyID);
            insertUsertoLobby.setString(4, userID);
            try{
                insertUsertoLobby.executeUpdate();
            } catch (SQLException throwables) {
                System.err.println("Error when executing SQL command.");
                throwables.printStackTrace();
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        finally {
            conn.close();
        }
        return getCurrentLobby(com_in, lobbyID);
    }

    @Override
    public void leaveLobby(DBconnection com_in, String lobbyID, String userID) throws SQLException {
        this.dbcp = com_in.getDataSource();
        this.conn = dbcp.getConnection();
        try{
            PreparedStatement deleteUser = conn.prepareStatement(
                    "DELETE FROM lobbyusers WHERE lobbyID = ? AND userID = ?");
            deleteUser.setString(1, lobbyID);
            deleteUser.setString(2, userID);
            try{
                deleteUser.executeUpdate();
            } catch (SQLException throwables) {
                System.err.println("Error when executing SQL command.");
                throwables.printStackTrace();
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        finally {
            conn.close();
        }
    }

    @Override
    public Restaurant getRecommendation(DBconnection com_in, String lobbyID) throws SQLException {
        this.dbcp = com_in.getDataSource();
        this.conn = dbcp.getConnection();
        String returnID = null, returnAlias = null, returnName = null, returnDisplayPhone = null, returnPrice = null;
        String returnCuisine = null, returnAddress = null, returnPhoneNumber = null, returnOperatingHours = null;
        String returnYelpInfo = null;
        Boolean returnIsOpenNow = null;
        Double returnRating = null;
        try{
            PreparedStatement getRestaurantID = conn.prepareStatement(
                    "SELECT restaurantID " +
                            "FROM lobby_preferred_restaurants " +
                            "WHERE lobbyID = ? AND vote = " +
                            "(SELECT MAX(vote) FROM lobby_preferred_restaurants)");
            getRestaurantID.setString(1, lobbyID);
            ResultSet rs = getRestaurantID.executeQuery();
            if(rs.next()){
                try{
                    PreparedStatement getRestaurant = conn.prepareStatement(
                            "SELECT * FROM restaurants WHERE ID=?");
                    getRestaurant.setString(1, rs.getString("restaurantID"));
                    ResultSet rs1 = getRestaurant.executeQuery();
                    while(rs1.next()){
                        returnID = rs1.getString("ID");
                        returnAlias = rs1.getString("alias");
                        returnName = rs1.getString("name");
                        returnDisplayPhone = rs1.getString("displayPhone");
                        returnPrice = rs1.getString("price");
                        returnPhoneNumber = rs1.getString("phone");
                        returnIsOpenNow = rs1.getBoolean("isOpenNow");
                        returnRating = rs1.getDouble("rating");

                        returnYelpInfo = rs1.getString("yelpInfo");
                        JSONObject yelpInfo = new JSONObject(returnYelpInfo);

                        returnCuisine = yelpInfo.get("categories").toString();
                        returnAddress = yelpInfo.get("location").toString();
                        if(yelpInfo.has("hours")) {
                            returnOperatingHours = yelpInfo.get("hours").toString();
                        } else{
                            returnOperatingHours = "{\"hours\":\"none\"}";
                        }
                    }
                } catch(SQLException throwables){
                    throwables.printStackTrace();
                }
            }
        } catch(SQLException throwables){
            throwables.printStackTrace();
        }
        finally {
            conn.close();
        }
        return new RestaurantBuilder().ID(returnID)
                .alias(returnAlias)
                .name(returnName)
                .phoneNumber(returnPhoneNumber)
                .displayPhone(returnDisplayPhone)
                .price(returnPrice)
                .cuisine(returnCuisine)
                .address(returnAddress)
                .operatingHours(returnOperatingHours)
                .isOpenNow(returnIsOpenNow)
                .rating(returnRating)
                .build();
    }

    @Override
    public Lobby getCurrentLobbyByCode(DBconnection com_in, String lobbyCode) throws SQLException {
        this.dbcp = com_in.getDataSource();
        this.conn = dbcp.getConnection();
        String returnLobbyID = null, returnLobbyCode = null, returnLobbyOwner = null;
        try {
            PreparedStatement getCuisineID = conn.prepareStatement(
                    "SELECT * FROM lobbies WHERE code=?");
            getCuisineID.setString(1, lobbyCode);
            ResultSet rs = getCuisineID.executeQuery();
            while(rs.next()){
                returnLobbyID = rs.getString("ID");
                returnLobbyCode = rs.getString("code");
                returnLobbyOwner = rs.getString("owner");
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        finally {
            conn.close();
        }
        return new LobbyBuilder().ID(returnLobbyID).code(returnLobbyCode).ownerID(returnLobbyOwner).build();
    }

    @Override
    public List<String> getLobbyUsers(DBconnection com_in, String lobbyID) throws SQLException {
        this.dbcp = com_in.getDataSource();
        this.conn = dbcp.getConnection();

        UserStore userStore = new UserStoreImpl();

        List<String> userList = new ArrayList<>();

        try {
            this.conn = dbcp.getConnection();
            PreparedStatement getUser = conn.prepareStatement(
                    "SELECT userID FROM lobbyusers WHERE lobbyID=?");
            getUser.setString(1, lobbyID);
            ResultSet rs = getUser.executeQuery();
            while(rs.next()){
                userList.add(userStore.getUserbyID(com_in, rs.getString("userID")).name());
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        finally {
            conn.close();
        }
        return userList;
    }

    @Override
    public List<String> getImageURLs(DBconnection com_in, String lobbyID) throws SQLException {
        this.dbcp = com_in.getDataSource();
        this.conn = dbcp.getConnection();

        RestaurantStore restaurantStore = new RestaurantStoreImpl();

        List<String> URLs = new ArrayList<>();

        List<Restaurant> restaurantList = getRestaurantList(com_in, lobbyID);
        for (Restaurant r : restaurantList){
            URLs.add(restaurantStore.getRestaurantUrl(com_in, r.ID()));
        }

        return URLs;
    }
}
