/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.util;

import java.util.Comparator;

public class QuickSelect {

    // Select k-th smallest element from the array
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

    public static <T> T select(T[] array, int k, Comparator<T> comparator) {
        return select(array, k, comparator, 0, array.length - 1);
    }

    public static <T> T select(T[] array, int k, Comparator<T> comparator, int left, int right) {
        while (left < right) {
            int pivot = (left + right) >>> 1;
            T pivotValue = array[pivot];
            int storage = left;

            array[pivot] = array[right];
            array[right] = pivotValue;
            for (int i = left; i < right; i++) {
                if (comparator.compare(array[i], pivotValue) < 0) {
                    T tmp = array[storage];
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
