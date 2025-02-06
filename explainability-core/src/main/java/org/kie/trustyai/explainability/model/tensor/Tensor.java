package org.kie.trustyai.explainability.model.tensor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Tensor<T> implements Serializable {
    private int[] dimensions;
    private T[] data;
    private int dimension;
    private int nEntries;
    private Class datatype;

    public Tensor(T[] data, int[] dimensions) {
        this.data = data;
        this.dimensions = dimensions;
        this.dimension = dimensions.length;
        this.nEntries = vectorProduct(dimensions);
        this.datatype = data[0].getClass();
    }

    // == LINEAR INDEX CONVERSIONS =====================================================================================
    // convert a set of N-dimensional tensor coordinates to the corresponding linear index
    protected int getLinearIndex(int... indices) {
        if (indices.length != dimensions.length) {
            throw new IllegalArgumentException(String.format(
                    "To retrieve a single element of a tensor, the passed list of indices must have equal length to the" +
                            " tensor dimension. Received %d indices, but the tensor dimension is %d.",
                    indices.length, dimension));
        }

        // map indices to linear index
        int linearIndex = 0;
        int product = 1;

        for (int i = dimension - 1; i >= 0; i--) {
            linearIndex += indices[i] * product;
            product *= dimensions[i];
        }
        return linearIndex;
    }

    // convert a linear index into the corresponding N-dimensional tensor coordinates
    protected int[] getCoordinates(int linearIndex) {
        int[] coords = new int[dimension];
        for (int i = dimension - 1; i >= 0; i--) {
            coords[i] = linearIndex % dimensions[i];
            linearIndex = Math.floorDiv(linearIndex, dimensions[i]);
        }
        return coords;
    }

    // == TENSOR SLICING AND ELEMENT ACCESS ============================================================================
    /**
     * Retrieve a single value from the n-dimensional tensor
     *
     * @param indices : the coordinates of the value to retrieve
     *
     * @returns the value at the provided coordinates
     **/
    public T getElement(int... indices) {
        return this.data[getLinearIndex(indices)];
    }

    /**
     * Retrieve a single value from the n-dimensional tensor, using a linear index
     *
     * @param index : the linear index of the value to retrieve
     *
     * @returns the value at the provided linear index
     **/
    public T getLinearElement(int index) {
        return this.data[index];
    }

    /**
     * Retrieve a single slice from the first dimension of the tensor, equivalent to Numpy's array[i] notation.
     * This is *massively* faster than tensor.slice(Slice.at(index))
     *
     * @param index: the index to slice at along the first dimension
     *
     * @returns the sliced tensor
     **/
    public Tensor<T> get(int index) {
        int remainingDimensions = vectorProduct(getDimensions(), 1);
        T[] slicedData = Arrays.copyOfRange(data, index * remainingDimensions, index * remainingDimensions + remainingDimensions);
        int[] newDimensions = Arrays.copyOfRange(getDimensions(), 1, dimension);
        return new Tensor<>(slicedData, newDimensions);
    }

    /**
     * Retrieve a set of slices from the first dimension of the tensor, equivalent to Numpy's array[idxs] notation.
     *
     * @param indices: the indices of the slice
     *
     * @returns the sliced tensor
     **/
    public Tensor<T> get(List<Integer> indices) {
        int remainingDimensions = vectorProduct(getDimensions(), 1);
        T[] slicedData = (T[]) Array.newInstance(datatype, indices.size() * remainingDimensions);

        for (int i = 0; i < indices.size(); i++) {
            System.arraycopy(data, indices.get(i) * remainingDimensions, slicedData, remainingDimensions * i, remainingDimensions);
        }
        int[] newDimensions = Arrays.copyOfRange(getDimensions(), 0, dimension);
        newDimensions[0] = indices.size();
        return new Tensor<>(slicedData, newDimensions);
    }

    /**
     * Retrieve a single slice from the second dimension of the tensor, equivalent to Numpy's array[:,i] notation
     * This is *massively* faster than tensor.slice(Slice.all(), Slice.at(index))
     *
     * @param index: the index to slice at along the second dimension
     *
     * @returns the sliced tensor
     **/
    public Tensor<T> getFromSecondAxis(int index) {
        int firstDimSize = vectorProduct(getDimensions(), 1);
        int secondDimSize = firstDimSize / getDimensions(1);
        T[] slicedData = (T[]) Array.newInstance(datatype, dimensions[0] * secondDimSize);

        for (int i = 0; i < dimensions[0]; i++) {
            System.arraycopy(data, i * firstDimSize + index * secondDimSize, slicedData, secondDimSize * i, secondDimSize);
        }

        int[] newDimensions = new int[dimension - 1];
        newDimensions[0] = dimensions[0];
        System.arraycopy(dimensions, 2, newDimensions, 1, dimension - 2);
        return new Tensor<>(slicedData, newDimensions);
    }

    /**
     * Slice the n-dimensional tensor, emulating the Numpy slicing syntax
     *
     * @param slices : the per-axis slices to perform
     *
     * @returns the sliced tensor
     **/
    public Tensor<T> slice(Slice... slices) {
        if (slices.length > dimensions.length) {
            throw new IllegalArgumentException(String.format(
                    "%d slice dimensions were passed, but this tensor only has %d dimensions",
                    slices.length, dimension));
        }

        // match slice size to tensor dimension
        List<Slice> effectiveSlices = new ArrayList<>(List.of(slices));
        while (effectiveSlices.size() < dimension) {
            effectiveSlices.add(Slice.all());
        }

        // get new dimensionality and coordinate bounds of each slice
        List<Integer> slicedDimensions = new ArrayList<>();
        int[][] sliceBounds = new int[dimension][2];
        for (int i = 0; i < dimension; i++) {
            Slice s = effectiveSlices.get(i);
            switch (s.getSliceType()) {
                case ALL:
                    slicedDimensions.add(dimensions[i]);
                    sliceBounds[i] = new int[] { 0, dimensions[i] };
                    break;
                case AT:
                    sliceBounds[i] = new int[] { s.at(), s.at() + 1 };
                    break;
                case BETWEEN:
                    sliceBounds[i] = new int[] { s.from(), s.to() };
                    slicedDimensions.add(s.to() - s.from());
                    break;
                case TO:
                    sliceBounds[i] = new int[] { 0, s.to() };
                    slicedDimensions.add(s.to());
                    break;
                case FROM:
                    sliceBounds[i] = new int[] { s.from(), dimensions[i] };
                    slicedDimensions.add(dimensions[i] - s.from());
                    break;
            }
        }

        // collect values that are contained within the slice
        // this is O(n*d) where n=nEntries, d=dimensions, so might not be the most efficient implementation?
        int slicedEntries = slicedDimensions.stream().reduce(1, (a, b) -> a * b);
        T[] slicedData = (T[]) Array.newInstance(datatype, slicedEntries);
        int sliceIdx = 0;
        for (int i = 0; i < nEntries; i++) {
            int[] dimensionalIndex = getCoordinates(i);
            boolean inSlice = true;
            for (int d = 0; d < dimension; d++) {
                if (!(sliceBounds[d][0] <= dimensionalIndex[d] && dimensionalIndex[d] < sliceBounds[d][1])) {
                    inSlice = false;
                    break;
                }
            }
            if (inSlice) {
                slicedData[sliceIdx] = data[i];
                sliceIdx++;
            }
        }

        int[] slicedDimensionsArr = slicedDimensions.stream().mapToInt(i -> i).toArray();
        return new Tensor<>(slicedData, slicedDimensionsArr);
    }

    // == COMBINING ====================================================================================================
    // combine a list of tensors along a new first dimension
    public static <T> Tensor<T> stack(Tensor<T>... tensors) {
        int[] firstTensorDimensions = tensors[0].getDimensions();

        // check dimensional compatibility
        if (!Arrays.stream(tensors).allMatch(t -> Arrays.equals(t.getDimensions(), firstTensorDimensions))) {
            List<String> tensorDims = Arrays.stream(tensors).map(t -> Arrays.toString(t.getDimensions())).toList();
            throw new IllegalArgumentException("Tensor dimensions do not all match: " + tensorDims);
        }

        int[] newDimensions = new int[tensors[0].getDimension() + 1];
        newDimensions[0] = tensors.length;
        for (int i = 1; i < newDimensions.length; i++) {
            newDimensions[i] = firstTensorDimensions[i - 1];
        }
        T[] dataArr = (T[]) Array.newInstance(tensors[0].getDatatype(), vectorProduct(newDimensions));
        int stride = vectorProduct(firstTensorDimensions);
        for (int tensor = 0; tensor < tensors.length; tensor++) {
            System.arraycopy(tensors[tensor].getData(), 0, dataArr, stride * tensor, stride);
        }
        return new Tensor<>(dataArr, newDimensions);
    }

    // combine a list of tensors along their existing first dimension
    public static <T> Tensor<T> concatenate(Tensor<T>... tensors) {
        int[] firstTensorDimensions = tensors[0].getDimensions();

        // check dimensional compatibility
        if (!Arrays.stream(tensors).allMatch(t -> Arrays.equals(t.getDimensions(), firstTensorDimensions))) {
            List<String> tensorDims = Arrays.stream(tensors).map(t -> Arrays.toString(t.getDimensions())).toList();
            throw new IllegalArgumentException("Tensor dimensions do not all match: " + tensorDims);
        }

        int[] newDimensions = new int[tensors[0].getDimension()];
        for (int i = 0; i < newDimensions.length; i++) {
            if (i == 0) {
                newDimensions[i] = firstTensorDimensions[i] * tensors.length;
            } else {
                newDimensions[i] = firstTensorDimensions[i];
            }
        }
        T[] dataArr = (T[]) Array.newInstance(tensors[0].getDatatype(), vectorProduct(newDimensions));
        int stride = vectorProduct(firstTensorDimensions);
        for (int tensor = 0; tensor < tensors.length; tensor++) {
            System.arraycopy(tensors[tensor].getData(), 0, dataArr, stride * tensor, stride);
        }
        return new Tensor<>(dataArr, newDimensions);

    }

    // == UTILITY ======================================================================================================
    // get the product of all elements within a vector
    public static int vectorProduct(int[] vector) {
        return vectorProduct(vector, 0);
    }

    // get the product of all elements in the subvector from index "from" to the end
    public static int vectorProduct(int[] vector, int from) {
        int[] slicedV;
        if (from != 0) {
            slicedV = Arrays.copyOfRange(vector, from, vector.length);
        } else {
            slicedV = vector;
        }
        return Arrays.stream(slicedV).reduce(1, (a, b) -> a * b);
    }

    // flatten a nested list of arbitrary depth
    private static <T> List<T> flattenList(List<?> list) {
        List<T> values = new ArrayList<>();
        if (list.get(0) instanceof List) {
            for (List sublist : (List<List>) list) {
                values.addAll(flattenList(sublist));
            }
        } else {
            values.addAll((List<T>) list);
        }
        return values;
    }

    private static List<Integer> getNestedListDimensions(List<?> list, List<Integer> dimensions) {
        dimensions.add(list.size());
        if (list.get(0) instanceof List) {
            getNestedListDimensions((List) list.get(0), dimensions);
        }
        return dimensions;
    }

    // given a flat list and a set of dimensions, nest the list into the provided shape
    private static <T> List nestList(List<T> flattenedList, int[] dimensions) {
        int prod = vectorProduct(dimensions);
        if (prod != flattenedList.size()) {
            throw new IllegalArgumentException("Shapes not compatible in nestList, received list of size " +
                    flattenedList.size() + " but the product of the provided dimensions is " + prod);
        }

        if (dimensions.length == 1) {
            return flattenedList;
        } else {
            List output = new ArrayList();
            int stride = vectorProduct(dimensions, 1);
            for (int i = 0; i < dimensions[0]; i++) {
                int startIdx = stride * i;
                int endIdx = stride * (i + 1);
                output.add(nestList(flattenedList.subList(startIdx, endIdx), Arrays.copyOfRange(dimensions, 1, dimensions.length)));
            }
            return output;
        }
    }

    // == LIST CONVERSIONS =============================================================================================
    /**
     * Create a tensor from a nested list
     *
     * @param list a set nested lists, where the bottom-most list is of type T
     *
     * @returns the resultant tensor of the same dimensions as the nested list
     **/
    public static <T> Tensor<T> fromList(List<?> list) {
        List<T> dataList = flattenList(list);
        int[] dimensions = getNestedListDimensions(list, new ArrayList<>()).stream().mapToInt(i -> i).toArray();
        T[] dataArr = (T[]) Array.newInstance(dataList.get(0).getClass(), dataList.size());
        dataArr = dataList.toArray(dataArr);
        return new Tensor<>(dataArr, dimensions);
    }

    /**
     * Convert a tensor to a nested list
     *
     * @returns a set of nested lists of the same dimension as the tensor.
     **/
    public List toNestedList() {
        return nestList(Arrays.stream(this.data).collect(Collectors.toList()), dimensions);
    }

    // == FUNCTIONAL ===================================================================================================
    /**
     * Apply a function to every item in the tensor.
     *
     * @param f: the function to apply to each element
     *
     * @returns the resultant tensor
     **/
    public Tensor<T> map(Function<T, T> f) {
        T[] dataArr = (T[]) Array.newInstance(datatype, nEntries);
        for (int i = 0; i < nEntries; i++) {
            dataArr[i] = f.apply(this.data[i]);
        }
        return new Tensor<>(dataArr, dimensions);
    }

    /**
     * Apply a function to every item in the tensor. This modifies the tensor in-place.
     *
     * @param f: the function to apply to each element
     * @param newDataArray: the array to fill with the result of the mapping. This will be used as the output tensor's data array.
     *
     * @returns the resultant tensor
     **/
    public <U> Tensor<U> map(Function<T, U> f, U[] newDataArray) {
        for (int i = 0; i < nEntries; i++) {
            newDataArray[i] = f.apply(this.data[i]);
        }
        return new Tensor<>(newDataArray, dimensions);
    }

    // == I/O ==========================================================================================================
    // convert the tensor to string, emulating Numpy's tensor printing
    public String toString() {
        String recursiveString = recursiveString(Arrays.stream(this.data).collect(Collectors.toList()), dimensions, "       ");
        return String.format("tensor(%s, dtype=%s)", recursiveString, datatype.getName());
    }

    // emulate Numpy's tensor printing
    public static <T> String recursiveString(List<T> flattenedList, int[] dimensions, String indent) {
        if (dimensions.length == 1) {
            return flattenedList.toString();
        } else {
            StringBuilder output = new StringBuilder();
            int stride = vectorProduct(dimensions, 1);
            for (int i = 0; i < dimensions[0]; i++) {
                int startIdx = stride * i;
                int endIdx = stride * (i + 1);

                int[] nextDimension = Arrays.copyOfRange(dimensions, 1, dimensions.length);
                String result = recursiveString(flattenedList.subList(startIdx, endIdx), nextDimension, indent + " ");
                if (nextDimension.length == 1) {
                    result = result.replace("[", "").replace("]", "");
                }

                output.append((i == 0 ? "" : indent) + "[");
                output.append(result);
                output.append("]");

                if (i != dimensions[0] - 1) {
                    output.append(",");
                    output.append(System.getProperty("line.separator"));
                    if (nextDimension.length > 1) {
                        output.append(System.getProperty("line.separator"));
                    }
                    if (nextDimension.length > 2) {
                        output.append(System.getProperty("line.separator"));
                    }
                }

            }
            return output.toString();
        }
    }

    // == GETTERS ======================================================================================================
    public int[] getDimensions() {
        return dimensions;
    }

    public int getDimensions(int axis) {
        return dimensions[axis];
    }

    public T[] getData() {
        return data;
    }

    public Class getDatatype() {
        return datatype;
    }

    public int getDimension() {
        return dimension;
    }

    public int getnEntries() {
        return nEntries;

    }

    // == SERIALIZERS ==================================================================================================
    public String serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream ois = new ObjectOutputStream(baos);
        ois.writeObject(this);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    public static Tensor deserialize(String encoding) throws IOException {
        byte[] data = Base64.getDecoder().decode(encoding);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (Tensor) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // == EQUALS == =====================================================================================================
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Tensor))
            return false;
        Tensor<?> tensor = (Tensor<?>) o;
        return Objects.deepEquals(dimensions, tensor.dimensions) && Objects.deepEquals(data, tensor.data) && Objects.equals(datatype, tensor.datatype);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(dimensions), Arrays.hashCode(data), dimension, nEntries, datatype);
    }
}
