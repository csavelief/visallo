package org.visallo.core.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.vertexium.Element;
import org.vertexium.query.Query;
import org.visallo.core.exception.VisalloException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This class contains methods for working with {@link java.util.stream.Stream}.
 */
public class StreamUtil {

    private StreamUtil() {
    }

    /**
     * Create a {@link java.util.stream.Stream} containing the results of executing the queries, in order. The results
     * are not loaded into memory first.
     */
    public static Stream<Element> stream(Query... queries) {
        return Arrays.stream(queries)
                .map(query -> StreamSupport.stream(query.elements().spliterator(), false))
                .reduce(Stream::concat)
                .orElseGet(Stream::empty);
    }

    /**
     * Create a {@link java.util.stream.Stream} over the elements of the iterables, in order.  A list of iterators
     * is first created from the iterables, and passed to {@link #stream(Iterator[])}. The iterable elements are not
     * loaded into memory first.
     */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T> Stream<T> stream(Iterable<T>... iterables) {
        List<Iterator<T>> iterators = Arrays.stream(iterables)
                .map(Iterable::iterator)
                .collect(Collectors.toList());

        return stream(iterators.toArray(new Iterator[iterables.length]));
    }

    /**
     * Create a {@link java.util.stream.Stream} over the elements of the iterators, in order.  The iterator elements
     * are not loaded into memory first.
     */
    @SafeVarargs
    public static <T> Stream<T> stream(Iterator<T>... iterators) {
        return withCloseHandler(
                Arrays.stream(iterators)
                        .map(StreamUtil::streamForIterator)
                        .reduce(Stream::concat)
                        .orElseGet(Stream::empty),
                iterators
        );
    }

    @SafeVarargs
    private static <T> Stream<T> withCloseHandler(Stream<T> stream, Iterator<T>... iterators) {
        stream.onClose(() -> {
            for (Iterator<T> iterator : iterators) {
                if (iterator instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) iterator).close();
                    } catch (Exception ex) {
                        throw new VisalloException(
                                String.format("exception occurred when closing %s", iterator.getClass().getName()),
                                ex
                        );
                    }
                }
            }
        });
        return stream;
    }

    private static <T> Stream<T> streamForIterator(Iterator<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
    }

    public static <T> Collector<T, ImmutableList.Builder<T>, ImmutableList<T>> toImmutableList() {
        return Collector.of(
                ImmutableList.Builder<T>::new,
                ImmutableList.Builder<T>::add,
                (l, r) -> l.addAll(r.build()),
                ImmutableList.Builder<T>::build
        );
    }

    public static <T> Collector<T, ImmutableSet.Builder<T>, ImmutableSet<T>> toImmutableSet() {
        return Collector.of(
                ImmutableSet.Builder::new,
                ImmutableSet.Builder::add,
                (l, r) -> l.addAll(r.build()),
                ImmutableSet.Builder<T>::build,
                Collector.Characteristics.UNORDERED
        );
    }

    public static <T, K, V> Collector<T, ImmutableMap.Builder<K, V>, ImmutableMap<K, V>> toImmutableMap(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends V> valueMapper
    ) {
        return Collector.of(
                ImmutableMap.Builder<K, V>::new,
                (r, t) -> r.put(keyMapper.apply(t), valueMapper.apply(t)),
                (l, r) -> l.putAll(r.build()),
                ImmutableMap.Builder<K, V>::build,
                Collector.Characteristics.UNORDERED
        );
    }
}
