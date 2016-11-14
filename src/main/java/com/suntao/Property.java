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

    public void setName(String name) {
        this.name = name;
    }

    public String getJdbcType() {
        return jdbcType;
    }

    public void setJdbcType(String jdbcType) {
        this.jdbcType = jdbcType;
    }
}
