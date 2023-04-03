package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class HTMExample {
    // Необхідні дані для підключення до бази даних
    private static final String DB_URL = "jdbc:postgresql://localhost/example_db";
    private static final String DB_USER = "user1";
    private static final String DB_PASSWORD = "p@SSw0rd";

    // Кількість потоків
    private static final int amountOfThreads = 50;

    // Імена клієнтів
    private static final List<String> clientNames = List.of("Bob", "Charley", "John", "Amadeu", "Jack");
    // Додаємо також окремий список із пустим значенням, що дозволить генерувати випадкові
    // запити оновлення даних в базі як для 3, так і для 2 клієнтів
    private static final List<String> namesToUpdate = new ArrayList<>(List.of(""));

    public static void main(String[] args) {
        namesToUpdate.addAll(clientNames);

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            initDb(connection);

            List<Callable<Long>> updateQueryCallables = new ArrayList<>();
            for (int i = 0; i < amountOfThreads; i++) {
                updateQueryCallables.add(() -> executeRandomUpdate(connection));
            }

            ExecutorService executorService = Executors.newFixedThreadPool(4);
            long startTime = System.currentTimeMillis();
            List<Future<Long>> futures = executorService.invokeAll(updateQueryCallables);
            List<Long> queryTimes = new ArrayList<>();
            for (Future<Long> future : futures) {
                queryTimes.add(future.get());
            }
            executorService.shutdown();
            long endTime = System.currentTimeMillis();
            executorService.close();
            for (int i = 0; i < queryTimes.size(); i++) {
                System.out.printf("Вираз %s зайняв %s мс\n", i + 1, queryTimes.get(i));
            }
            System.out.printf("Зайняло %s мс\n", endTime - startTime);
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // Метод, що оновлює баланс рахунку трьох чи двох випадкових клієнтів
    private static long executeRandomUpdate(Connection connection) throws SQLException {
        long startTime = System.currentTimeMillis();
        Collections.shuffle(namesToUpdate);
        PreparedStatement preparedStatement = connection.prepareStatement("UPDATE Users SET balance = balance + 10 WHERE name in (?, ?, ?)");
        preparedStatement.setString(1, namesToUpdate.get(0));
        preparedStatement.setString(2, namesToUpdate.get(1));
        preparedStatement.setString(3, namesToUpdate.get(2));
        preparedStatement.executeUpdate();
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    // Метод ініціалізації структури бази
    private static void initDb(Connection connection) throws SQLException {
        connection.createStatement().execute("DROP TABLE IF EXISTS Users;");
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS Users (name  varchar(100) PRIMARY KEY, balance integer NOT NULL);");

        PreparedStatement insert = connection.prepareStatement("INSERT INTO Users (name, balance) VALUES (?, ?)");
        for (String name : clientNames) {
            insert.setString(1, name);
            insert.setInt(2, 1000);
            insert.addBatch();
        }
        insert.executeBatch();
    }

}
