package nl.tudelft.cornul11.thesis.app;

public class MainServer {
  public static void main(String[] args) throws Exception {
    FatJarServer server = new FatJarServer();
    server.run();
  }
}