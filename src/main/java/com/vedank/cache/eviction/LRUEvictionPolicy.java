package com.vedank.cache.eviction;

import java.util.HashMap;
import java.util.Map;

/**
 * Least-Recently-Used eviction, implemented as the classic
 * {@code HashMap<K, Node> + doubly linked list} combo:
 *
 * <pre>
 *   MRU                         LRU
 *    v                           v
 *  head <-> [A] <-> [C] <-> [B] <-> tail
 * </pre>
 *
 * The list is kept ordered from most-recently-used (right after {@code head})
 * to least-recently-used (right before {@code tail}). Every operation below
 * touches at most a constant number of pointers, so {@code onAccess},
 * {@code onInsert}, {@code evict} and {@code onRemove} are all O(1).
 *
 * <p>Not thread-safe; the owning cache must externally synchronize access.
 */
public final class LRUEvictionPolicy<K> implements EvictionPolicy<K> {

    private static final class Node<K> {
        final K key;
        Node<K> prev;
        Node<K> next;

        Node(K key) {
            this.key = key;
        }
    }

    private final Map<K, Node<K>> nodes = new HashMap<>();
    private final Node<K> head = new Node<>(null); // sentinel, most-recently-used side
    private final Node<K> tail = new Node<>(null); // sentinel, least-recently-used side

    public LRUEvictionPolicy() {
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public void onAccess(K key) {
        Node<K> node = nodes.get(key);
        if (node != null) {
            moveToFront(node);
        }
    }

    @Override
    public void onInsert(K key) {
        Node<K> existing = nodes.get(key);
        if (existing != null) {
            moveToFront(existing);
            return;
        }
        Node<K> node = new Node<>(key);
        nodes.put(key, node);
        insertAfterHead(node);
    }

    @Override
    public K evict() {
        Node<K> victim = tail.prev;
        if (victim == head) {
            return null; // empty
        }
        unlink(victim);
        nodes.remove(victim.key);
        return victim.key;
    }

    @Override
    public void onRemove(K key) {
        Node<K> node = nodes.remove(key);
        if (node != null) {
            unlink(node);
        }
    }

    @Override
    public int size() {
        return nodes.size();
    }

    @Override
    public void clear() {
        nodes.clear();
        head.next = tail;
        tail.prev = head;
    }

    private void moveToFront(Node<K> node) {
        unlink(node);
        insertAfterHead(node);
    }

    private void insertAfterHead(Node<K> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void unlink(Node<K> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
    }
}
