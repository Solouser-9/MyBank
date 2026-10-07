package bank;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Db {

    private static HikariDataSource ds;


    public static void init() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(Config.get("DB_URL"));
        config.setUsername(Config.get("DB_USER"));
        config.setPassword(Config.get("DB_PASS"));
        config.setMaximumPoolSize(10);
        ds = new HikariDataSource(config);
    }


    public static Connection conn() throws SQLException {
        return ds.getConnection();
    }


    public static int update(String sql, Object... params) throws SQLException {
        try (Connection c = conn()) {
            return update(c, sql, params);
        }
    }


    public static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        }
    }


    public static Map<String, Object> one(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> list = list(sql, params);
        return list.isEmpty() ? null : list.get(0);
    }


    public static List<Map<String, Object>> list(String sql, Object... params) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> result = new ArrayList<>();
                ResultSetMetaData md = rs.getMetaData();
                int columns = md.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columns; i++) {
                        row.put(md.getColumnName(i), rs.getObject(i));
                    }
                    result.add(row);
                }
                return result;
            }
        }
    }


    public static long scalar(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        }
    }


    public static boolean exists(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}