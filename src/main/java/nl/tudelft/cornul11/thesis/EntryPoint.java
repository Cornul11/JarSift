package nl.tudelft.cornul11.thesis;

import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeClass;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeSignatureExtractor;

import java.io.IOException;
import java.util.List;

public class EntryPoint {
    public static void main(String[] args) throws IOException {
        // extracts signature from a .class file
        BytecodeClass bytecodeClass = BytecodeSignatureExtractor.run("GuineaClass");

        DatabaseManager dbManager = DatabaseManager.getInstance();

        // Insert a user
        dbManager.insertUser("John Doe", "johndoe@example.com");

        // Retrieve all users
        List<DatabaseManager.User> userList = dbManager.getAllUsers();
        for (DatabaseManager.User user : userList) {
            System.out.println(user.name() + ", " + user.email());
        }

        // Close the database connection
        dbManager.closeConnection();

        // we want to run through all jars in a folder, and check them if they match the signature as per the signature corpus


    }
}
