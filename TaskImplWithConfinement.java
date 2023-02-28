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
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class TaskImplWithConfinement {

    private static final Integer INITIAL_MATRIX_DIMENSION1 = 5;

    public static void main(String[] args) throws InterruptedException {
        int matrixDimension = INITIAL_MATRIX_DIMENSION1 == null ? DataGenerator.generateInteger() : INITIAL_MATRIX_DIMENSION1;
//        List<List<Double>> MO = DataGenerator.generateMatrix(matrixDimension, matrixDimension);
//        List<Double> D = DataGenerator.generateVector(matrixDimension);
//        List<Double> C = DataGenerator.generateVector(matrixDimension);

        List<Double> D = List.of(1., 2., 3.);
        List<Double> C = List.of(3., 2., 1.);
        List<List<Double>> MO = List.of(
                List.of(1., 15., 12.),
                List.of(3., 4., 9.),
                List.of(4., 2., 3.)
        );

        saveGeneratedDataToFile(MO, D, C);


        LocalDateTime startTime = LocalDateTime.now();
        System.out.println("Початок: " + startTime);

        Thread thread1 = new Thread(() -> calculateB(MO, D, C));
        Thread thread2 = new Thread(() -> calculateS(MO, D, C));

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        LocalDateTime endTime = LocalDateTime.now();
        System.out.println("Кінець: " + endTime);

        long timeOfExecution = ChronoUnit.MILLIS.between(startTime, endTime);
        System.out.println("Всього зайняло: " + timeOfExecution);
    }

    private static void calculateS(List<List<Double>> MO, List<Double> D, List<Double> C) {
        System.out.println("Тред S: початок обчислення S");

        List<Double> localD = new ArrayList<>(D);
        List<Double> localC = new ArrayList<>(C);
        List<List<Double>> localMO = MO.stream()
                .map(ArrayList::new)
                .collect(Collectors.toList());

        Supplier<List<Double>> CMOSupplier = () -> {
            System.out.println("Тред S: початок обчислення C*MO");
            List<List<Double>> CAsMatrix = List.of(localC);
            List<List<Double>> SMO = multiplyMatrices(CAsMatrix, localMO);
            System.out.println("Тред S: кінець обчислення C*MO");
            return SMO.get(0);
        };

        Supplier<List<Double>> sumOfDCSupplier = () -> {
            System.out.println("Тред S: початок обчислення D+C");
            List<Double> result = new ArrayList<>();
            for (int i = 0; i < localC.size(); i++) {
                result.add(kahanSum(localD.get(i), localC.get(i)));
            }
            System.out.println("Тред S: кінець обчислення D+C");
            return result;
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

    private static void calculateB(List<List<Double>> mo, List<Double> d, List<Double> c) {
        System.out.println("Тред B: початок обчислення B");

        List<List<Double>> localMO = mo.stream()
                .map(ArrayList::new)
                .collect(Collectors.toList());
        List<Double> localD = new ArrayList<>(d);
        List<Double> localC = new ArrayList<>(c);

        Supplier<List<Double>> DMOSupplier = () -> {
            System.out.println("Тред B: початок обчислення D*MO");
            List<List<Double>> dAsMatrix = List.of(localD);
            List<List<Double>> DMO = multiplyMatrices(dAsMatrix, localMO);
            System.out.println("Тред B: кінець обчислення D*MO");
            return DMO.get(0);
        };

        Supplier<List<Double>> minDCSupplier = () -> {
            System.out.println("Тред B: початок обчислення min(D)*C");
            double minD = localD.stream()
                    .min(Comparator.naturalOrder())
                    .get();

            List<Double> minDC = localC.stream()
                    .map(num -> num * minD)
                    .collect(Collectors.toList());

            System.out.println("Тред B: кінець обчислення min(D)*C");
            return minDC;
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
                .map(TaskImplWithConfinement::getListAsString)
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
