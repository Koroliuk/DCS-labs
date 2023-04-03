package org.example;

import org.multiverse.api.StmUtils;
import org.multiverse.api.references.TxnInteger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class STMExample {
    // Необхідні дані для підключення до бази даних
    private static final String DB_URL = "jdbc:postgresql://localhost/example_db";
    private static final String DB_USER = "user1";
    private static final String DB_PASSWORD = "p@SSw0rd";

    // Кількість потоків
    private static final int amountOfThreads = 50;

    // Імена клієнтів
    private static final List<String> clientNames = List.of("Bob", "Charley", "John", "Amadeu", "Jack");

    public static void main(String[] args) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            List<User> users = initDb(connection);

            List<Callable<Long>> updateQueryCallables = new ArrayList<>();
            for (int i = 0; i < amountOfThreads; i++) {
                updateQueryCallables.add(() -> executeRandomUpdate(users, connection));
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
    private static long executeRandomUpdate(List<User> users, Connection connection) {
        long startTime = System.currentTimeMillis();
        Collections.shuffle(users);
        User user1 = users.get(0);
        User user2 = users.get(1);
        User user3 = users.get(2);
        // Критична секція, що вимагає виконання тільки одним потоком одночасно, задля забезпечення цілісності даних
        StmUtils.atomic(() -> {
            String name1 = null;
            String name2 = null;
            String name3 = null;
            if (user1 != null) {
                user1.getBalance().increment(10);
                name1 = user1.getName();
            }
            if (user2 != null) {
                user2.getBalance().increment(10);
                name2 = user2.getName();
            }
            if (user3 != null) {
                user3.getBalance().increment(10);
                name3 = user3.getName();
            }
            PreparedStatement preparedStatement = null;
            try {
                preparedStatement = connection.prepareStatement("UPDATE Users SET balance = balance + 10 WHERE name in (?, ?, ?)");
                preparedStatement.setString(1, name1);
                preparedStatement.setString(2, name2);
                preparedStatement.setString(3, name3);
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    // Метод ініціалізації структури бази, повертає список користувачів
    private static List<User> initDb(Connection connection) throws SQLException {
        connection.createStatement().execute("DROP TABLE IF EXISTS Users;");
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS Users (name  varchar(100) PRIMARY KEY, balance integer NOT NULL);");

        List<User> users = new ArrayList<>();
        PreparedStatement insert = connection.prepareStatement("INSERT INTO Users (name, balance) VALUES (?, ?)");
        for (String name : clientNames) {
            int defaultBalance = 1000;
            insert.setString(1, name);
            insert.setInt(2, defaultBalance);
            insert.addBatch();
            users.add(new User(name, defaultBalance));
        }
        insert.executeBatch();

        // Додаємо також окреме пусте значення, що дозволить генерувати випадкові
        // запити оновлення даних в базі як для 3, так і для 2 клієнтів
        users.add(null);

        return users;
    }

    // Клас користвача. Має поле балансу, яке є Txn об'єктом, до якого можна
    // забезпечити доступ тільки одного потоку в межах методу StmUtils.atomic()
    private static class User {
        private final String name;
        private final TxnInteger balance;

        User(String name, int balance) {
            this.name = name;
            this.balance = StmUtils.newTxnInteger(balance);
        }

        public TxnInteger getBalance() {
            return balance;
        }

        public String getName() {
            return name;
        }
    }
}
