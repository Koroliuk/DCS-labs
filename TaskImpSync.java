import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class TaskImpSync {

    private static final Integer INITIAL_MATRIX_DIMENSION = 100;
    private static volatile List<List<Double>> MO;
    private static volatile List<Double> D;
    private static volatile List<Double> C;

    public static void main(String[] args) throws InterruptedException {
        int matrixDimension = INITIAL_MATRIX_DIMENSION == null ? DataGenerator.generateInteger() : INITIAL_MATRIX_DIMENSION;
        MO = DataGenerator.generateMatrix(matrixDimension, matrixDimension);
        D = DataGenerator.generateVector(matrixDimension);
        C = DataGenerator.generateVector(matrixDimension);
//        D = List.of(1., 2., 3.);
//        C = List.of(3., 2., 1.);
//        MO = List.of(
//                List.of(1., 15., 12.),
//                List.of(3., 4., 9.),
//                List.of(4., 2., 3.)
//        );
        saveGeneratedDataToFile(MO, D, C);

        LocalDateTime startTime = LocalDateTime.now();
        System.out.println("Початок: " + startTime);

        Semaphore semMO = new Semaphore(1);
        Semaphore semD = new Semaphore(1);
        Semaphore semC = new Semaphore(1);

        Thread thread1 = new Thread(() -> calculateB(semMO, semD, semC));
        Thread thread2 = new Thread(() -> calculateS(semMO, semD, semC));

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        LocalDateTime endTime = LocalDateTime.now();
        System.out.println("Кінець: " + endTime);

        long timeOfExecution = ChronoUnit.MILLIS.between(startTime, endTime);
        System.out.println("Всього зайняло: " + timeOfExecution);
    }

    private static void calculateS(Semaphore semMO, Semaphore semD, Semaphore semC) {
        System.out.println("Тред S: початок обчислення S");

        Supplier<List<Double>> CMOSupplier = () -> {
            try {
                System.out.println("Тред S: початок обчислення C*MO");
                System.out.println("Тред S: очікує дозвіл на C");
                semC.acquire();
                System.out.println("Тред S: отримує дозвіл на C");
                System.out.println("Тред S: очікує дозвіл на MO");
                semMO.acquire();
                System.out.println("Тред S: отримує дозвіл на MO");
                List<List<Double>> CAsMatrix = List.of(C);
                List<List<Double>> SMO = multiplyMatrices(CAsMatrix, MO);
                semC.release();
                System.out.println("Тред S: звільняє дозвіл на С");
                semMO.release();
                System.out.println("Тред S: звільняє дозвіл на MO");
                System.out.println("Тред S: кінець обчислення C*MO");
                return SMO.get(0);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Supplier<List<Double>> sumOfDCSupplier = () -> {
            try {
                System.out.println("Тред S: початок обчислення D+C");
                List<Double> result = new ArrayList<>();
                System.out.println("Тред S: очікує дозвіл на D");
                semD.acquire();
                System.out.println("Тред S: отримує дозвіл на D");
                System.out.println("Тред S: очікує дозвіл на С");
                semC.acquire();
                System.out.println("Тред S: отримує дозвіл на С");
                for (int i = 0; i < C.size(); i++) {
                    result.add(kahanSum(D.get(i), C.get(i)));
                }
                semC.release();
                System.out.println("Тред S: звільняє дозвіл на C");
                semD.release();
                System.out.println("Тред S: звільняє дозвіл на D");
                System.out.println("Тред S: кінець обчислення D+C");
                return result;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        BiFunction<List<Double>, List<Double>, List<Double>> sumAndSortResult = (list1, list2) -> {
            System.out.println("Тред S: початок обчиисленння загальної суми та сортування");
            List<Double> unsortedResult = new ArrayList<>();
            for (int i = 0; i < list1.size(); i++) {
                unsortedResult.add(kahanSum(list1.get(i), list2.get(i)));
            }
            List<Double> sorted = unsortedResult.stream()
                    .sorted()
                    .toList();
            System.out.println("Тред S: кінець обчислення загальної суми та сортування");
            return sorted;
        };

        CompletableFuture<List<Double>> future = CompletableFuture.supplyAsync(CMOSupplier)
                .thenCombine(CompletableFuture.supplyAsync(sumOfDCSupplier), sumAndSortResult);

        try {
            List<Double> S = future.get();
            System.out.println("Тред S: збереження та вивід S");
            saveAndShowResult(S, String.format("S (1x%s)", S.size()), "result_s.txt");
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void calculateB(Semaphore semMO, Semaphore semD, Semaphore semC) {
        System.out.println("Тред B: початок обчислення B");

        Supplier<List<Double>> DMOSupplier = () -> {
            try {
                System.out.println("Тред B: початок обчислення D*MO");
                System.out.println("Тред B: очікує дозвіл на D");
                semD.acquire();
                System.out.println("Тред B: отримує дозвіл на D");
                System.out.println("Тред B: очікує дозвіл на MO");
                semMO.acquire();
                System.out.println("Тред B: отримує дозвіл на MO");
                List<List<Double>> dAsMatrix = List.of(D);
                List<List<Double>> DMO = multiplyMatrices(dAsMatrix, MO);
                semD.release();
                System.out.println("Тред B: звільняє дозвіл на D");
                semMO.release();
                System.out.println("Тред B: звільняє дозвіл на MO");
                System.out.println("Тред B: кінець обчислення D*MO");
                return DMO.get(0);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Supplier<List<Double>> minDCSupplier = () -> {
            try {
                System.out.println("Тред B: початок обчислення min(D)*C");
                System.out.println("Тред B: очікує дозвіл на D");
                semD.acquire();
                System.out.println("Тред B: отримує дозвіл на D");
                double minD = D.stream()
                        .min(Comparator.naturalOrder())
                        .get();
                semD.release();
                System.out.println("Тред B: звільняє дозвіл на D");

                System.out.println("Тред B: очікує дозвіл на C");
                semC.acquire();
                System.out.println("Тред B: отримує дозвіл на C");
                List<Double> minDC = C.stream()
                        .map(num -> num * minD)
                        .collect(Collectors.toList());
                semC.release();
                System.out.println("Тред B: звільняє дозвіл на C");
                System.out.println("Тред B: кінець обчислення min(D)*C");
                return minDC;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        BiFunction<List<Double>, List<Double>, List<Double>> sumOfVectors = (list1, list2) -> {
            System.out.println("Тред B: початок обчислення фінальної різниці");
            List<Double> result = new ArrayList<>();
            for (int i = 0; i < list1.size(); i++) {
                result.add(kahanSum(list1.get(i), -list2.get(i)));
            }
            System.out.println("Тред B: кінець обчислення фінальної різниці");
            return result;
        };

        CompletableFuture<List<Double>> future = CompletableFuture.supplyAsync(DMOSupplier)
                .thenCombine(CompletableFuture.supplyAsync(minDCSupplier), sumOfVectors);

        try {
            List<Double> B = future.get();
            System.out.println("Тред B: збереження та вивід B");
            saveAndShowResult(B, String.format("B (1x%s)", B.size()), "result_b.txt");
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<List<Double>> multiplyMatrices(List<List<Double>> matrix1, List<List<Double>> matrix2) {
        List<List<List<Double>>> preResult = new ArrayList<>();
        for (int i = 0; i < matrix1.size(); i++) {
            List<List<Double>> row = new ArrayList<>();
            for (int j = 0; j < matrix2.get(0).size(); j++) {
                row.add(new ArrayList<>());
            }
            preResult.add(row);
        }

        for (int i = 0; i < matrix1.size(); i++) {
            for (int j = 0; j < matrix2.get(0).size(); j++) {
                for (int k = 0; k < matrix2.size(); k++) {
                    List<Double> curr = preResult.get(i).get(j);
                    curr.add(matrix1.get(i).get(k) * matrix2.get(k).get(j));
                    preResult.get(i).set(j, curr);
                    List<List<Double>> arr = preResult.get(i);
                    preResult.set(i, arr);
                }
            }
        }

        List<List<Double>> finalResult = new ArrayList<>();
        for (List<List<Double>> row : preResult) {
            List<Double> resRow = new ArrayList<>();
            for (List<Double> elem : row) {
                Double[] params = elem.toArray(Double[]::new);
                resRow.add(kahanSum(params));
            }
            finalResult.add(resRow);
        }

        return finalResult;
    }

    private static double kahanSum(Double... doubles) {
        double sum = 0.0;
        double c = 0.0;
        for (double f : doubles) {

            double y = f - c;
            double t = sum + y;
            c = (t - sum) - y;
            sum = t;
        }
        return sum;
    }

    private static void saveAndShowResult(List<Double> result, String startConsoleMessage, String fileName) {
        String resultAsString = getListAsString(result);
        System.out.println(startConsoleMessage + ": " + resultAsString);
        try {
            FileWriter fileWriter = new FileWriter(fileName);
            fileWriter.write(resultAsString);
            fileWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void saveGeneratedDataToFile(List<List<Double>> mo, List<Double> d, List<Double> c) {
        String moAsStr = getMatrixAsString(mo);
        String dAsString = getListAsString(d);
        String cAsString = getListAsString(c);

        try {
            FileWriter fileWriter = new FileWriter("input.txt");

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
            return Integer.MAX_VALUE * random.nextDouble();
        }

        public static int generateInteger() {
            return random.nextInt(100, 1000);
        }

    }

}
