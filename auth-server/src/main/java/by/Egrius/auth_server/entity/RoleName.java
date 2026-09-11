package by.Egrius.auth_server.entity;

public enum RoleName {
    USER("USER"), ADMIN("ADMIN");

    private final String abbr;

    RoleName(String abbr) {
        this.abbr = abbr;
    }

    public String getAbbr() {return abbr;}
}