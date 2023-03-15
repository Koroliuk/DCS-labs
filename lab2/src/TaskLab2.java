import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class TaskLab2 {
    private static final Integer INITIAL_MATRIX_DIMENSION = null;

    // Змінні вхідних даних
    private static List<List<Double>> MO;
    private static List<Double> D;
    private static List<Double> C;

    // Змінні результату та попступових обчислень
    private static Double minD = Double.MAX_VALUE;
    private static final List<Double> sumDC = new ArrayList<>();
    private static final List<Double> B = new ArrayList<>();
    private static final List<Double> S = new ArrayList<>();


    // Замки на читання та запис для змінних результату та попступових обчислень
    private static final Lock lockBWrite = new ReentrantReadWriteLock().writeLock();
    private static final Lock lockBRead = new ReentrantReadWriteLock().readLock();
    private static final Lock lockMinDWrite = new ReentrantReadWriteLock().writeLock();
    private static final Lock lockMinDRead = new ReentrantReadWriteLock().readLock();
    private static final Lock lockSumDCWrite = new ReentrantReadWriteLock().writeLock();
    private static final Lock lockSumDCRead = new ReentrantReadWriteLock().readLock();
    private static final Lock lockSWrite = new ReentrantReadWriteLock().writeLock();
    private static final Lock lockSRead = new ReentrantReadWriteLock().readLock();


    public static void main(String[] args) throws InterruptedException, ExecutionException {
        int matrixDimension = INITIAL_MATRIX_DIMENSION == null ? DataGenerator.generateInteger() : INITIAL_MATRIX_DIMENSION;
        MO = DataGenerator.generateMatrix(matrixDimension, matrixDimension);
        D = DataGenerator.generateVector(matrixDimension);
        C = DataGenerator.generateVector(matrixDimension);

        saveGeneratedDataToFile(MO, D, C);

        LocalDateTime startTime = LocalDateTime.now();
        System.out.println("Початок: " + startTime);

        ExecutorService executorService = Executors.newFixedThreadPool(4);

        Future<List<Double>> futureOfB = executorService.submit(TaskLab2::calculateB);
        Future<List<Double>> futureOfS = executorService.submit(TaskLab2::calculateS);

        List<Double> B = futureOfB.get();
        List<Double> S = futureOfS.get();
        executorService.shutdown();

        LocalDateTime endTime = LocalDateTime.now();
        System.out.println("Кінець: " + endTime);

        long timeOfExecution = ChronoUnit.MILLIS.between(startTime, endTime);
        System.out.println("Всього зайняло: " + timeOfExecution);

        saveAndShowResult(B, String.format("B (1x%s)", B.size()), "result_b.txt");
        saveAndShowResult(S, String.format("S (1x%s)", S.size()), "result_s.txt");
    }

    private static List<Double> calculateS() {
        System.out.println("Тред S: початок обчислення S");
        for (int i = 0; i < D.size(); i++) {
            S.add(0.);
        }

        Supplier<List<Double>> CMOSupplier = () -> {
            System.out.println("Тред S: початок обчислення C*MO");
            List<List<Double>> CAsMatrix = List.of(C);
            List<List<Double>> SMO = multiplyMatrices(CAsMatrix, MO);
            System.out.println("Тред S: кінець обчислення C*MO");
            return SMO.get(0);
        };

        Supplier<List<Double>> sumOfDCSupplier = () -> {
            System.out.println("Тред S: початок обчислення D+C");
            for (int i = 0; i < D.size(); i++) {
                sumDC.add(0.);
            }
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < D.size(); i++) {
                int finalI = i;
                tasks.add(CompletableFuture.runAsync(() -> {
                    lockSumDCRead.lock();
                    double currValue = sumDC.get(finalI);
                    lockSumDCRead.unlock();
                    lockSumDCWrite.lock();
                    sumDC.set(finalI, kahanSum(currValue, D.get(finalI)));
                    lockSumDCWrite.unlock();
                }));
            }

            for (int i = 0; i < C.size(); i++) {
                int finalI = i;
                tasks.add(CompletableFuture.runAsync(() -> {
                    lockSumDCRead.lock();
                    double currValue = sumDC.get(finalI);
                    lockSumDCRead.unlock();
                    lockSumDCWrite.lock();
                    sumDC.set(finalI, kahanSum(currValue, C.get(finalI)));
                    lockSumDCWrite.unlock();
                }));
            }

            CompletableFuture<Void> futureOfSumDC = CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
            try {
                futureOfSumDC.get();
                System.out.println("Тред S: кінець обчислення D+C");
                return sumDC;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        };

        BiFunction<List<Double>, List<Double>, Void> sumAndSortResult = (list1, list2) -> {
            System.out.println("Тред S: початок обчиисленння загальної суми та сортування");
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < list1.size(); i++) {
                int finalI = i;
                tasks.add(CompletableFuture.runAsync(() -> {
                    lockSRead.lock();
                    double currValue = S.get(finalI);
                    lockSRead.unlock();
                    lockSWrite.lock();
                    S.set(finalI, kahanSum(currValue, list1.get(finalI)));
                    lockSWrite.unlock();
                }));
            }

            for (int i = 0; i < list2.size(); i++) {
                int finalI = i;
                tasks.add(CompletableFuture.runAsync(() -> {
                    lockSRead.lock();
                    double currValue = S.get(finalI);
                    lockSRead.unlock();
                    lockSWrite.lock();
                    S.set(finalI, kahanSum(currValue, list2.get(finalI)));
                    lockSWrite.unlock();
                }));
            }

            CompletableFuture<Void> futureOfS = CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
            try {
                futureOfS.get();
                S.sort(Comparator.naturalOrder());
                System.out.println("Тред S: кінець обчислення загальної суми та сортування");
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            return null;
        };

        CompletableFuture<Void> future = CompletableFuture.supplyAsync(CMOSupplier)
                .thenCombine(CompletableFuture.supplyAsync(sumOfDCSupplier), sumAndSortResult);

        try {
            future.get();
            return S;
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Double> calculateB() {
        System.out.println("Тред B: початок обчислення B");
        for (int i = 0; i < D.size(); i++) {
            B.add(0.);
        }

        Supplier<List<Double>> DMOSupplier = () -> {
            System.out.println("Тред B: початок обчислення D*MO");
            List<List<Double>> dAsMatrix = List.of(D);
            List<List<Double>> DMO = multiplyMatrices(dAsMatrix, MO);
            System.out.println("Тред B: кінець обчислення D*MO");
            return DMO.get(0);
        };

        Supplier<List<Double>> minDCSupplier = () -> {
            System.out.println("Тред B: початок обчислення min(D)*C");
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            Map<Double, List<Double>> chunks = D.stream().collect(Collectors.groupingBy(s -> s % 50));
            chunks.values()
                    .forEach(chunk -> tasks.add(CompletableFuture.runAsync(() -> {
                        double localMin = Double.MAX_VALUE;
                        for (Double elem : chunk) {
                            if (elem < localMin) {
                                localMin = elem;
                            }
                        }
                        lockMinDRead.lock();
                        double currValue = minD;
                        lockMinDRead.unlock();
                        lockMinDWrite.lock();
                        if (currValue > localMin) {
                            minD = localMin;
                        }
                        lockMinDWrite.unlock();
                    })));

            CompletableFuture<Void> getMinD = CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
            try {
                getMinD.get();
                System.out.println("Тред B: кінець обчислення min(D)*C");
                return C.stream()
                        .map(num -> num * minD)
                        .collect(Collectors.toList());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        };

        BiFunction<List<Double>, List<Double>, Void> sumOfVectors = (list1, list2) -> {
            System.out.println("Тред B: початок обчислення фінальної різниці");
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < list1.size(); i++) {
                int finalI = i;
                tasks.add(CompletableFuture.runAsync(() -> {
                     lockBRead.lock();
                    double currValue = B.get(finalI);
                    lockBRead.unlock();
                    lockBWrite.lock();
                    B.set(finalI, kahanSum(currValue, list1.get(finalI)));
                    lockBWrite.unlock();
                }));
            }

            for (int i = 0; i < list2.size(); i++) {
                int finalI = i;
                tasks.add(CompletableFuture.runAsync(() -> {
                    lockBRead.lock();
                    double currValue = B.get(finalI);
                    lockBRead.unlock();
                    lockBWrite.lock();
                    B.set(finalI, kahanSum(currValue, -list2.get(finalI)));
                    lockBWrite.unlock();
                }));
            }

            CompletableFuture<Void> futureOfB = CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
            try {
                futureOfB.get();
                System.out.println("Тред B: кінець обчислення фінальної різниці");
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            return null;
        };

        CompletableFuture<Void> future = CompletableFuture.supplyAsync(DMOSupplier)
                .thenCombine(CompletableFuture.supplyAsync(minDCSupplier), sumOfVectors);

        try {
            future.get();
            return B;
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
                .map(TaskLab2::getListAsString)
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
