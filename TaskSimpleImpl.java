import java.util.Arrays;
import java.util.stream.Stream;

public class TaskSimpleImpl {

    public static void main(String[] args) {
        double[] D = new double[]{1, 2, 3};
        double[] C = new double[]{3, 2, 1};
        double[][] MO = new double[][]{
                {1, 15, 12},
                {3, 4, 9},
                {4, 2, 3}
        };

        // No multithreading
        double[] B = calculateB(D, MO, C);
        double[] S = calculateS(D, MO, C);

        String s1 = "B:\n" +
                getArrayAsString(B) +
                "\n";
        System.out.println(s1);
        String s2 = "S:\n" +
                getArrayAsString(S) +
                "\n";
        System.out.println(s2);
    }

    private static String getArrayAsString(double[] s) {
        StringBuilder res = new StringBuilder();
        Stream.of(s).forEach(num -> res.append(Arrays.toString(num))
                .append(", "));
        return res.toString();
    }

    private static double[] calculateB(double[] d, double[][] mo, double[] c) {
        double[][] dd = new double[][]{d};

        double[][] DMO = multiplyMatrices(dd, mo);

        double minD = Arrays.stream(d)
                .min()
                .getAsDouble();
        double[] minDC = Arrays.stream(c)
                .map(num -> num * minD)
                .toArray();

        double[] B = new double[minDC.length];
        for (int i = 0; i < minDC.length; i++) {
            B[i] = DMO[0][i] - minDC[i];
        }

        return B;
    }

    private static double[] calculateS(double[] d, double[][] mo, double[] c) {
        double[][] dd = new double[][]{d};

        double[][] MOD = multiplyMatrices(mo, dd);

        double[] DC = new double[c.length];
        for (int i = 0; i < c.length; i++) {
            DC[i] = d[i] + c[i];
        }

        double[] unsortedResult = new double[c.length];
        for (int i = 0; i < c.length; i++) {
            unsortedResult[i] = MOD[0][i] + DC[i];
        }

        return Arrays.stream(unsortedResult)
                .sorted()
                .toArray();
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