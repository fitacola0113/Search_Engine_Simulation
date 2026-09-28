package search.Properties;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigProperties {
    
    private static Properties props = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream("java/src/main/java/search/Properties/config.properties")) {
            props.load(fis);
        } catch (IOException e) {
            System.err.println("Erro ao carregar config.properties: " + e.getMessage());
        }
    }

    public String get(String key) {
        return props.getProperty(key);
    }

    public int getInt(String key) {
        return Integer.parseInt(props.getProperty(key));
    }
}