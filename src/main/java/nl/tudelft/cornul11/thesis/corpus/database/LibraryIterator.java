package nl.tudelft.cornul11.thesis.corpus.database;

import com.zaxxer.hikari.HikariDataSource;
import nl.tudelft.cornul11.thesis.corpus.model.Dependency;
import nl.tudelft.cornul11.thesis.corpus.model.LibraryInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;

public class LibraryIterator implements Iterator<Dependency> {
    private final Connection connection;
    private final PreparedStatement statement;
    private final ResultSet resultSet;

    public LibraryIterator(HikariDataSource ds) {
        try {
            String selectLibrariesQuery = "SELECT group_id, artifact_id, version FROM libraries WHERE is_uber_jar = 0";

            this.connection = ds.getConnection();
            this.statement = this.connection.prepareStatement(selectLibrariesQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            this.statement.setFetchSize(1000);  // Fetch 1000 rows at a time
            this.statement.setFetchDirection(ResultSet.FETCH_FORWARD);
            this.resultSet = this.statement.executeQuery();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasNext() {
        try {
            boolean hasNext = resultSet.next();
            if (!hasNext) {
                // close resources when there's no more data
                resultSet.close();
                statement.close();
                connection.close();
            }
            return hasNext;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Dependency next() {
        try {
            return new Dependency(resultSet.getString("group_id"), resultSet.getString("artifact_id"), resultSet.getString("version"), true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
