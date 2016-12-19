package com.suntao;

public class Property {
    private String name;
    private String jdbcType;

    public Property() {}

    public Property(String name, String jdbcType) {
        this.name = name;
        this.jdbcType = jdbcType;
    }

    public String getName() {
        return name;
    }

    public String getJdbcType() {
        return jdbcType;
    }
}
