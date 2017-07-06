package me.dinos;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class App {

    // Root Directory
    //             -> Data Folder
    //                          -> Individual Files
    //
    // Individual File (1)
    //              -> Read JSON
    //                          -> For each key, store value
    //                                                     -> Loop (1)

    public static void main(String[] args) throws IOException, SQLException {
        Scanner in = new Scanner(System.in);
        System.out.print("DB Username: ");
        String user = in.nextLine();
        System.out.print("DB Password: ");
        String pass = in.nextLine();
        in.close();

        Connection sql = DriverManager.getConnection("jdbc:mariadb://localhost:3306/localDb?useUnicode=true", user, pass);
        System.out.println("Connected!");
        sql.prepareStatement("SET NAMES utf8mb4;").execute(); // Super important, allows insertion of emojis etc !!

        Set<Long> loadedGuilds = new HashSet<>(5000);
        ResultSet set = sql.prepareStatement("SELECT guild_id FROM Bot;").executeQuery();
        while (set.next()) {
            loadedGuilds.add(set.getLong(1));
        }

        Files.walk(Paths.get(new File("data").getPath())) // Iterate through all files within the data dir
                .filter(file -> file.getFileName().toString().endsWith(".json")) // Filter out any files that are not JSON
                .forEach(file -> {
                    String fileName = file.getFileName().toString();
                    String guildId = fileName.substring(0, fileName.length() - 5); // 5 = ".json".length
                    if (loadedGuilds.contains(Long.valueOf(guildId))) return;
                    System.out.println("Reading " + fileName);
                    Map<String, String> query = new LinkedHashMap<>(7);
                    query.put("guild_id", guildId);

                    JSONObject obj = new JSONObject(fromFile(file.toAbsolutePath()));
                    Arrays.stream(DataType.values()).forEach(type -> {
                        if (!obj.has(type.oEntry) || obj.getString(type.oEntry).isEmpty()) return;
                        String value = obj.getString(type.oEntry);
                        if (type == DataType.PREFIX
                                || type == DataType.FAREWELL_MESSAGE
                                || type == DataType.WELCOME_MESSAGE) {
                            value = "\"" + value.replace("\\", "\\\\")
                                    .replace("\"", "\\\"") + "\"";
                        }

                        query.put(type.nEntry, value);
                    });

                    String columns = String.join(", ", query.keySet());
                    String values = String.join(", ", query.values());
                    String statement = "INSERT INTO Bot (" + columns + ") VALUES (" + values + ");";
                    System.out.println(statement);
                    int result = 0;
                    try {
                        result = sql.prepareStatement(statement).executeUpdate();
                    } catch (SQLException e) {
                        System.err.println(e.getCause().getMessage() + "\n");
                    }
                    System.out.println(result == 0 ? "Failure!" : "Success!");
                });
    }

    private static String fromFile(Path path) {
        try {
            return new String(Files.readAllBytes(path), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "{}";
    }

    private enum DataType {
        WELCOME_MESSAGE("welcome_message", "welcome"),
        FAREWELL_MESSAGE("farewell_message", "farewell"),
        AUTOROLE_ID("autorole_id", "role"),
        WELCOME_CHANNEL("welcome_id", "join_channel"),
        FAREWELL_CHANNEL("farewell_id", "leave_channel"),
        PREFIX("prefix", "prefix");

        private final String nEntry;
        private final String oEntry;

        DataType(String nEntry, String oEntry) {
            this.nEntry = nEntry;
            this.oEntry = oEntry;
        }
    }
}
