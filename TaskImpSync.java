import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

public class TaskImpSync {

    static double[] D = new double[]{1, 2, 3};
    static double[] C = new double[]{3, 2, 1};
    static double[][] MO = new double[][]{
            {1, 15, 12},
            {3, 4, 9},
            {4, 2, 3}
    };
    public static void main(String[] args) throws InterruptedException {
        Semaphore semD = new Semaphore(1);
        Semaphore semC = new Semaphore(1);
        Semaphore semMO = new Semaphore(1);
        Thread thread1 = new Thread(() -> {
            CompletableFuture<double[]> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            semD.acquire();
                            System.out.println("1 Ask for D");
                            System.out.println("1 Ask for MO");
                            semMO.acquire();
                            double[][] dd = new double[][]{D};
                            double[][] DMO = multiplyMatrices(dd, MO);
                            System.out.println("1 release D");
                            System.out.println("1 release MO");
                            semD.release();
                            semMO.release();
                            return DMO[0];
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .thenCombine(CompletableFuture.supplyAsync(() -> {
                        try {
                            semD.acquire();
                            System.out.println("1 Ask for D");
                            double minD = Arrays.stream(D)
                                    .min()
                                    .getAsDouble();
                            System.out.println("1 release D");
                            semD.release();

                            semC.acquire();
                            System.out.println("1 Ask for C");
                            double[] res =  Arrays.stream(C)
                                    .map(num -> num * minD)
                                    .toArray();
                            System.out.println("1 release C");
                            semC.release();

                            return res;
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }), (s1, s2) -> {
                        double[] result = new double[s1.length];
                        for (int i = 0; i < s1.length; i ++) {
                            result[i] = s1[i] - s2[i];
                        }
                        return result;
                    });

            try {
                double[] B = future.get();
                String s = "Multithreading1 B\n:" +
                        getArrayAsString(B) +
                        "\n";
                System.out.println(s);
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Thread thread2 = new Thread(() -> {
            CompletableFuture<double[]> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            semD.acquire();
                            System.out.println("2 Ask for D");
                            System.out.println("2 Ask for MO");
                            semMO.acquire();
                            double[][] dd = new double[][]{D};
                            double[][] DMO = multiplyMatrices(MO, dd);
                            System.out.println("2 release D");
                            System.out.println("2 release MO");
                            semD.release();
                            semMO.release();
                            return DMO[0];
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .thenCombine(CompletableFuture.supplyAsync(() -> {
                        try {
                            semD.acquire();
                            System.out.println("2 Ask for D");
                            semC.acquire();
                            System.out.println("2 Ask for C");
                            double[] DC = new double[C.length];
                            for (int i = 0; i < C.length; i++) {
                                DC[i] = D[i] + C[i];
                            }
                            System.out.println("2 release D");
                            semD.release();
                            System.out.println("2 release C");
                            semC.release();
                            return DC;
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }), (s1, s2) -> {
                        double[] unsortedResult = new double[s1.length];
                        for (int i = 0; i < s1.length; i++) {
                            unsortedResult[i] = s1[i] + s2[i];
                        }
                        return Arrays.stream(unsortedResult).sorted().toArray();
                    });

            try {
                double[] B = future.get();
                String s = "Multithreading1 S\n:" +
                        getArrayAsString(B) +
                        "\n";
                System.out.println(s);
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
    }

    private static String getArrayAsString(double[] s) {
        StringBuilder res = new StringBuilder();
        Stream.of(s).forEach(num -> res.append(Arrays.toString(num))
                .append(", "));
        return res.toString();
    }

    private static double[][] multiplyMatrices(double[][] firstMatrix, double[][] secondMatrix) {
        double[][] result = new double[firstMatrix.length][secondMatrix[0].length];

        for (int row = 0; row < result.length; row++) {
            for (int col = 0; col < result[row].length; col++) {
                result[row][col] = multiplyMatricesCell(firstMatrix, secondMatrix, row, col);
            }
        }

        return result;
    }

    private static double multiplyMatricesCell(double[][] firstMatrix, double[][] secondMatrix, int row, int col) {
        double cell = 0;
        for (int i = 0; i < secondMatrix.length; i++) {
            cell += firstMatrix[row][i] * secondMatrix[i][col];
        }
        return cell;
    }

}