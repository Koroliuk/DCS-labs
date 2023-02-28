import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;


// todo: додавання з Каханом
// todo: вивід самих значень
// todo: чи треба для локалізації дані отримувати в саммому треді?
public class TaskImpSync {

    public static void main(String[] args) throws InterruptedException {
        int n = DataGenerator.generateInteger();
        int m = DataGenerator.generateInteger();
//        List<List<Double>> MO1 = DataGenerator.generateMatrix(n, m);
//        List<Double> D1 = DataGenerator.generateVector(n);
//        List<Double> C1 = DataGenerator.generateVector(n);
//        saveGeneratedDataToFile(MO1, D1, C1);
        List<Double> D1 = List.of(1., 2., 3.);
        List<Double> C1 = List.of(3., 2., 1.);
        List<List<Double>> MO1 = List.of(
                List.of(1., 15., 12.),
                List.of(3., 4., 9.),
                List.of(4., 2., 3.)
        );

        Semaphore semaphoreD = new Semaphore(1);
        Semaphore semaphoreC = new Semaphore(1);
        Semaphore semaphoreMO = new Semaphore(1);

        Thread thread1 = new Thread(() -> {
            System.out.println("Починаємо обчислювати B");
            CompletableFuture<List<Double>> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            System.out.println("Починаємо обчислювати зменшуване D*MO");
                            semaphoreD.acquire();
                            List<List<Double>> dAsMatrix = List.of(D1);
                            semaphoreD.release();
                            semaphoreMO.acquire();
                            List<List<Double>> DMO = multiplyMatrices(dAsMatrix, MO1);
                            semaphoreMO.release();
                            System.out.println("Обчислили зменшуване D*MO");
                            return DMO.get(0);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
            ).thenCombine(CompletableFuture.supplyAsync(() -> {
                try {
                    System.out.println("Починаємо обчислювати від'ємник min(D)*C");
                    semaphoreD.acquire();
                    double minD = D1.stream()
                            .min(Comparator.naturalOrder())
                            .get();
                    semaphoreD.release();

                    semaphoreC.acquire();
                    List<Double> minDC = C1.stream()
                            .map(num -> num * minD)
                            .collect(Collectors.toList());
                    semaphoreC.release();
                    System.out.println("Обчислили від'ємник min(D)*C");
                    return minDC;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }), (s1, s2) -> {
                System.out.println("Починаємо обчислювати різницю");
                List<Double> result = new ArrayList<>();
                for (int i = 0; i < s1.size(); i++) {
                    result.add(s1.get(i) - s2.get(i));
                }
                System.out.println("Обчислили різницю");
                return result;
            });

            try {
                List<Double> B = future.get();
                System.out.println(getListAsString(B));
                System.out.println("Зберігаємо та виводимо B");
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Обчислення B закінчилось");
        });

        Thread thread2 = new Thread(() -> {
            System.out.println("Починаємо обчислювати S");
            CompletableFuture<List<Double>> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            System.out.println("Починаємо обчислювати доданок MO*D");
                            semaphoreD.acquire();
                            List<List<Double>> dAsMatrix = List.of(D1);
                            semaphoreD.release();
                            semaphoreMO.acquire();
                            List<List<Double>> DMO = multiplyMatrices(MO1, dAsMatrix);
                            semaphoreMO.release();
                            System.out.println("Обчислили доданок MO*D");
                            return DMO.get(0);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
            ).thenCombine(CompletableFuture.supplyAsync(() -> {
                try {
                    semaphoreD.acquire();
                    semaphoreC.acquire();
                    System.out.println("Починаємо обчислювати доданок D+C");
                    List<Double> DC = new ArrayList<>();
                    for (int i = 0; i < C1.size(); i++) {
                        DC.add(D1.get(i) + C1.get(i));
                    }
                    semaphoreD.release();
                    semaphoreC.release();
                    System.out.println("Обчислили доданок D+C");
                    return DC;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }), (s1, s2) -> {
                System.out.println("Обчилюємо загальну суму та сортуємо");
                List<Double> unsortedResult = new ArrayList<>();
                for (int i = 0; i < s1.size(); i++) {
                    unsortedResult.add(s1.get(i) + s2.get(i));
                }
                List<Double> sorted = unsortedResult.stream()
                        .sorted()
                        .toList();
                System.out.println("Обчислили загальну суму та просортували");
                return sorted;
            });

            try {
                List<Double> S = future.get();
                System.out.println(getListAsString(S));
                System.out.println("Зберігаємо та виводимо S");
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        System.out.println("res");
    }

    private static List<List<Double>> multiplyMatrices(List<List<Double>> matrix1, List<List<Double>> matrix2) {
        List<List<Double>> result = new ArrayList<>();
        for (int i = 0; i < matrix1.size(); i++) {
            List<Double> row = new ArrayList<>();
            for (int j = 0; j < matrix2.get(0).size(); j++) {
                row.add(0.);
            }
            result.add(row);
        }

        for (int i = 0; i < matrix1.size(); i++) {
            for (int j = 0; j < matrix2.get(0).size(); j++) {
                for (int k = 0; k < matrix2.size(); k++) {
                    double curr = result.get(i).get(j);
                    curr += matrix1.get(i).get(k) * matrix2.get(k).get(j);
                    result.get(i).set(j, curr);
                    List<Double> arr = result.get(i);
                    result.set(i, arr);
                }
            }
        }
        return result;
    }

    private static void saveGeneratedDataToFile(List<List<Double>> mo, List<Double> d, List<Double> c) {
        String moAsStr = getMatrixAsString(mo);
        String dAsString = getListAsString(d);
        String cAsString = getListAsString(c);

        try {
            FileWriter fileWriter = new FileWriter("input.json");

            fileWriter.write(String.format("MO (%sX%s)" + System.lineSeparator(), mo.size(), mo.get(0).size()));
            fileWriter.write(moAsStr);

            fileWriter.write(String.format("D (1X%s)" + System.lineSeparator(), d.size()));
            fileWriter.write(dAsString + System.lineSeparator());

            fileWriter.write(String.format("C (1X%s)" + System.lineSeparator(), c.size()));
            fileWriter.write(cAsString + System.lineSeparator());

            fileWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getMatrixAsString(List<List<Double>> matrix) {
        return matrix.stream()
                .map(TaskImpSync::getListAsString)
                .map(s -> s + System.lineSeparator())
                .reduce("", (s1, s2) -> s1 + s2);
    }

    private static String getListAsString(List<Double> list) {
        StringBuilder s = new StringBuilder();
        list.forEach(d -> s.append(d)
                .append(" "));
        return s.toString().trim();
    }

    static class DataGenerator {

        private static final Random random = new Random();

        public static List<List<Double>> generateMatrix(int n, int m) {
            List<List<Double>> result = new ArrayList<>();
            while (n > 0) {
                result.add(generateVector(m));
                n--;
            }
            return result;
        }

        public static List<Double> generateVector(int length) {
            List<Double> result = new ArrayList<>();
            while (length > 0) {
                result.add(generateDouble());
                length--;
            }
            return result;
        }

        private static double generateDouble() {
            return Long.MAX_VALUE * random.nextDouble();
        }

        public static int generateInteger() {
            return random.nextInt(100, 1000);
        }

    }

}
