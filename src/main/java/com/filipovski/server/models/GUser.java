package com.filipovski.server.models;

public class GUser {
    private String name;
    private String pictureUrl;
    private String email;

    private GUser() { }

    public static GUser of(String name, String email, String pictureUrl) {
        GUser user = new GUser();
        user.name = name;
        user.email = email;
        user.pictureUrl = pictureUrl;

        return user;
    }

    public String getName() {
        return name;
    }

    public String getPicture() {
        return pictureUrl;
    }

    public String getEmail() {
        return email;
    }
}
