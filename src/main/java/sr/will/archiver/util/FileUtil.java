package sr.will.archiver.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;

public class FileUtil {
    private static File getFolder(String name) {
        File folder = new File(name);
        if (folder.mkdirs()) Archiver.LOGGER.info("Created folder(s)");
        return folder;
    }

    public static Config getConfig() {
        File configFile = new File("config.json");
        if (!Files.exists(configFile.toPath())) {
            Config config = new Config();
            saveConfig(config);
            return config;
        }

        return FileUtil.readJson(new File("config.json"), Config.class);
    }

    public static void saveConfig(Config config) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        writeText(gson.toJson(config), ".", "config.json", "config");
    }

    public static <T> T readJson(File file, Type typeof) {
        try {
            FileReader fileReader = new FileReader(file);
            T obj = Archiver.GSON.fromJson(fileReader, typeof);
            fileReader.close();
            return obj;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void writeText(String contents, String folder, String name, String message) {
        Archiver.LOGGER.info("Writing " + message + "...");
        try {
            FileWriter writer = new FileWriter(new File(getFolder(folder), name));
            writer.write(contents);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeGson(Object object, String folder, String name, String message) {
        writeText(Archiver.GSON.toJson(object), folder, name + ".json", message);
    }
}
