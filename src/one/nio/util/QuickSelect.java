package one.nio.util;

public class QuickSelect {

    // Select k-th largest element from the array
    public static long select(long[] array, int k) {
        return select(array, k, 0, array.length - 1);
    }

    public static long select(long[] array, int k, int left, int right) {
        while (left < right) {
            int pivot = (left + right) >>> 1;
            long pivotValue = array[pivot];
            int storage = left;

            array[pivot] = array[right];
            array[right] = pivotValue;
            for (int i = left; i < right; i++) {
                if (array[i] < pivotValue) {
                    long tmp = array[storage];
                    array[storage] = array[i];
                    array[i] = tmp;
                    storage++;
                }
            }
            array[right] = array[storage];
            array[storage] = pivotValue;

            if (storage < k) {
                left = storage + 1;
            } else {
                right = storage;
            }
        }

        return array[k];
    }
}
