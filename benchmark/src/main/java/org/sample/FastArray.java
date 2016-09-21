package org.sample;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

public class FastArray<E> extends AbstractList<E> {//NOPMD

    private static final Logger LOG = Logger.getLogger(FastArray.class.getName());

    private static final int CAPACITY = Integer.SIZE;
    private static final Object[][] EMPTY_ELEMENTDATA = {};
    private static final IndexData[] EMPTY_DATA = {};
    private static final int START_COLUMN = 0;
    private static final int END_COLUMN = CAPACITY - 1;
    private int size;//NOPMD
    private Object[][] elementData;
    private IndexData[] data;

    public FastArray() {
        super();
        elementData = EMPTY_ELEMENTDATA;
        data = EMPTY_DATA;
    }

    private static int firstLeftFreeBit(final int data, final int index) {
        int result = -1;
        for (int i = index + 1; i < CAPACITY; i++) {
            if ((data & 1 << i) == 0) {
                result = i;
                break;
            }
        }

        return result;
    }

    public E get(final int index) {
        rangeCheck(index);

        return getElementData(index);
    }

    @SuppressWarnings("unchecked")
    private E getElementData(final int index) {
        final ElementOfData element = getElementOfDataByIndex(index);
        return (E) elementData[element.row][element.col];
    }

    public int size() {
        return size;
    }

    @Override
    public E set(final int index, final E element) {
        final ElementOfData elementOfData = getElementOfDataByIndex(index);
        elementData[elementOfData.row][elementOfData.col] = element;
        return element;
    }

    private void addData(final int row, final int col, final Object element) {
        elementData[row][col] = element;
        size++;

        data[row].take(col);
    }

    private int lastRowOfElementData() {
        return elementData.length - 1;
    }

    public int memorySize() {
        int total = 0;
        for (final Object[] anElementData : elementData) {
            if (anElementData != null) {
                total += anElementData.length;
            }
        }

        return total;
    }

    public int memorySizeOfElementData() {
        int total = 0;
        for (final Object[] anElementData : elementData) {
            if (anElementData != null) {
                for (final Object anAnElementData : anElementData) {
                    if (anAnElementData != null) {
                        total++;
                    }
                }
            }
        }

        return total;
    }

    private Object[] newObjectArray() {
        return new Object[CAPACITY];
    }

    private IndexData newIndexData() {
        return new IndexData();
    }

    public void trimToSize() {
        if (size < memorySize()) {
            final int rowCount = size / CAPACITY + 1;

            Object[][] newElementData = new Object[rowCount][];
            IndexData[] newData = new IndexData[rowCount];

            int currentRow = 0;
            int currentColumn = 0;
            for (int row = 0; row < data.length; row++) {
                for (int column = 0; column < CAPACITY; column++) {

                    if (data[row].isTaked(column)) {
                        if (newElementData[currentRow] == null) {
                            newElementData[currentRow] = newObjectArray();
                            newData[currentRow] = newIndexData();
                        }

                        newElementData[currentRow][currentColumn] = elementData[row][column];
                        elementData[row][column] = null;

                        newData[currentRow].take(currentColumn);

                        currentColumn++;

                        if (currentColumn >= CAPACITY) {
                            currentColumn = 0;
                            currentRow++;
                        }
                    }
                }
            }

            elementData = newElementData;
            data = newData;
        }
    }

    private void addUndefinedElement(final E element) {
        grow();
        addData(lastRowOfElementData(), START_COLUMN, element);
    }

    private void addTakedElement(final int row, final int column, final E element) {
        final int rightIndex = column - 1;
        if (rightIndex >= 0 && data[row].isFree(rightIndex)) {
            addData(row, rightIndex, element);
        } else {

            final Object[] elements = elementData[row];

            final int leftIndex = firstLeftFreeBit(data[row].data, column);
            if (leftIndex > column) {

                System.arraycopy(elements, column, elements, column + 1, leftIndex - column);
                data[row].take(leftIndex);
                addData(row, column, element);

            } else {

                final Object tempObject = elements[END_COLUMN];
                System.arraycopy(elements, column, elements, column + 1, END_COLUMN - column);
                elementData[row][column] = element;

                final int newRowIndex = row + 1;
                int newColumnIndex = END_COLUMN / 2;

                if (newRowIndex == elementData.length) {
                    grow();
                } else {
                    final int freeBit = data[newRowIndex].getIndexTrailingFreeBit();

                    if (freeBit >= 0) {
                        newColumnIndex = freeBit;

                    } else {
                        grow();

                        System.arraycopy(elementData, newRowIndex,
                                elementData, newRowIndex + 1,
                                lastRowOfElementData() - newRowIndex);

                        elementData[newRowIndex] = new Object[CAPACITY];

                        System.arraycopy(data, newRowIndex,
                                data, newRowIndex + 1,
                                data.length - newRowIndex - 1);

                        data[newRowIndex] = new IndexData();
                    }
                }

                addData(newRowIndex, newColumnIndex, tempObject);
            }
        }
    }

    @Override
    public void add(final int index, final E element) {
        rangeCheckForAdd(index);

        final ElementOfData elementOfData = getElementOfDataByIndex(index);

        Objects.requireNonNull(elementOfData);

        final int row = elementOfData.row;
        final int column = elementOfData.col;

        switch (elementOfData.state) {
            case UNDEFINED:
                addUndefinedElement(element);
                break;
            case FREE:
                addData(row, column, element);
                break;
            case TAKED:
                addTakedElement(row, column, element);
                break;
            default:
                LOG.severe("Undefined status of data");
        }
    }

    private ElementOfData getElementOfDataByIndex(int index) {

        ElementOfData elementOfData = ElementOfData.undefined();

        if (size > 0) {
            if (index == size) {
                final int rowIndex = data.length - 1;
                if (!data[rowIndex].isFull()) {
                    elementOfData = ElementOfData.of(rowIndex, data[rowIndex].getIndexLeadingFreeBit(), DataState.FREE);
                }
            } else {
                for (int i = 0; i < data.length && index >= 0; i++) {
                    if (data[i].size > index) {
                        elementOfData = ElementOfData.of(i, getTakedIndexAt(data[i].data, index), DataState.TAKED);
                        break;
                    } else {
                        index -= data[i].size;
                    }
                }
            }
        }

        return elementOfData;
    }

    private int getTakedIndexAt(int data, int number) {

        int count = 0;
        int index = -1;

        number++;

        do {
            count += data & 1;
            index++;
        } while ((data >>>= 1) != 0 && count != number);

        return index;
    }

    private void grow() {
        elementData = Arrays.copyOf(elementData, elementData.length + 1);
        elementData[elementData.length - 1] = new Object[CAPACITY];

        data = Arrays.copyOf(data, data.length + 1);
        data[data.length - 1] = new IndexData();
    }

    @Override
    public E remove(final int index) {
        rangeCheck(index);
        final ElementOfData element = getElementOfDataByIndex(index);
        return removeData(element);
    }

    @SuppressWarnings("unchecked")
    private E removeData(final ElementOfData element) {
        final Object object = elementData[element.row][element.col];
        elementData[element.row][element.col] = null;
        data[element.row].free(element.col);
        size--;

        return (E) object;
    }

    @Override
    public void clear() {

        for (int i = 0; i < elementData.length; i++) {
            for (int j = 0; elementData[i] != null && j < elementData[i].length; j++) {
                elementData[i][j] = null;
            }
        }

        elementData = EMPTY_ELEMENTDATA;

        for (int i = 0; i < data.length; i++) {
            data[i] = null;
        }

        data = EMPTY_DATA;

        size = 0;
    }

    private void rangeCheck(final int index) {
        if (index >= size || index < 0) {
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }
    }

    private void rangeCheckForAdd(final int index) {
        if (index > size || index < 0) {
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }
    }

    private String outOfBoundsMsg(final int index) {
        return "Index: " + index + ", Size: " + size;
    }

    public String toStringDebug() {
        final StringBuilder builder = new StringBuilder(100);

        builder.append("collection.FastArray {\ngetSize: ")
                .append(size)
                .append(", rows: \n");

        for (final Object[] arr : elementData) {
            builder.append('{');

            for (final Object o : arr) {
                builder.append(o == null ? 'n' : o)
                        .append(',');
            }

            builder.append("}\n");
        }

        builder.append("IndexData { rows: \n");

        for (final IndexData d : data) {
            builder.append("{ getSize: ")
                    .append(d.getSize())
                    .append(", data: ")
                    .append(Integer.toBinaryString(d.data))
                    .append("}\n");
        }

        builder.append("}}");

        return builder.toString();
    }

    @Override
    public String toString() {
        return "collection.FastArray{" +
                "getSize=" + size +
                '}';
    }

    private enum DataState {UNDEFINED, FREE, TAKED}

    private static class IndexData {
        private int data;
        private int size;

        private boolean isFull() {
            return size == CAPACITY;
        }

        private void take(final int index) {
            if (isFree(index)) {
                data = data | 1 << index;
                size++;
            }
        }

        private void free(final int index) {
            if (isTaked(index)) {
                data = data ^ 1 << index;
                size--;
            }
        }

        private boolean isTaked(final int index) {
            return !isFree(index);
        }

        private boolean isFree(final int index) {
            return (data & 1 << index) == 0;
        }

        private int getSize() {
            return size;
        }

        private int getIndexLeadingFreeBit() {
            return CAPACITY - Integer.numberOfLeadingZeros(data);
        }

        private int getIndexTrailingFreeBit() {
            return Integer.numberOfTrailingZeros(data) - 1;
        }
    }

    private static class ElementOfData {
        private final int row, col;
        private final DataState state;

        protected ElementOfData(final int row, final int col, final DataState state) {
            this.row = row;
            this.col = col;
            this.state = state;
        }

        private static ElementOfData undefined() {
            return new ElementOfData(-1, -1, DataState.UNDEFINED);
        }

        private static ElementOfData of(final int row, final int col, final DataState state) {//NOPMD
            return new ElementOfData(row, col, state);
        }

        @Override
        public String toString() {
            return "ElementOfData{" +
                    "row=" + row +
                    ", col=" + col +
                    ", state=" + state +
                    '}';
        }
    }
}
