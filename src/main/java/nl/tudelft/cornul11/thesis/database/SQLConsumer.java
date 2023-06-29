package nl.tudelft.cornul11.thesis.database;

import java.sql.SQLException;

@FunctionalInterface
public interface SQLConsumer<T> {
    void accept(T t) throws SQLException;
}
