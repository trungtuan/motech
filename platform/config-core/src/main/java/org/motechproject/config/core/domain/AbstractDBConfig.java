package org.motechproject.config.core.domain;

/**
 * This abstract class encapsulates the database configuration, composed of as db url, username and password.
 */
public abstract class AbstractDBConfig {
    private final String url;
    private String driver;
    private String username;
    private String password;

    /**
     * @param url
     * @param driver
     * @param username
     * @param password
     */
    public AbstractDBConfig(String url, String driver, String username, String password) {
        this.url = url;
        this.driver = driver;
        this.username = username;
        this.password = password;
    }

    public String getUrl() {
        return url;
    }

    public String getDriver() {
        return driver;
    }
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractDBConfig dbConfig = (AbstractDBConfig) o;

        if (password != null ? !password.equals(dbConfig.password) : dbConfig.password != null) {
            return false;
        }

        if (!url.equals(dbConfig.url)) {
            return false;
        }

        if (!driver.equals(dbConfig.driver)) {
            return false;
        }

        if (username != null ? !username.equals(dbConfig.username) : dbConfig.username != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + (driver != null ? driver.hashCode() : 0);
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DBConfig{");
        sb.append("url='").append(url).append('\'');
        sb.append("driver='").append(driver).append('\'');
        sb.append(", username='").append(username).append('\'');
        sb.append(", password='").append(password).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
