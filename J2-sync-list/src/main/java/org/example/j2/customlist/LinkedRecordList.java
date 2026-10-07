package org.example.j2.customlist;

import org.example.j2.common.RecordStore;
import org.example.j2.common.Sleep;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

// Односвязный список с захватом по узлам (hand-over-hand / lock coupling): любая операция
// движется по списку только в сторону хвоста и никогда не захватывает узел дальше от головы,
// не держа при этом узел ближе к голове. Это и даёт отсутствие дедлоков между вставкой,
// печатью и сортировкой — ровно то, что требует задание ("записи ближе к голове — захватывать первыми").
public final class LinkedRecordList implements RecordStore, Iterable<String> {

    private static final Comparator<String> ORDER = Comparator.naturalOrder();

    // сентинел без данных — нужен, чтобы у "головы" тоже был свой lock, как у любого узла
    private final Node head = new Node(null);

    @Override
    public void addFirst(String value) {
        head.lock.lock();
        try {
            Node node = new Node(value);
            node.next = head.next;
            head.next = node;
        } finally {
            head.lock.unlock();
        }
    }

    @Override
    public void print(PrintStream out) {
        for (String value : this) {
            out.println(value);
        }
    }

    @Override
    public Runnable newSorter(long withinDelayMs, long betweenDelayMs, AtomicLong totalSteps) {
        return new CustomListSorter(this, withinDelayMs, betweenDelayMs, totalSteps);
    }

    @Override
    public Iterator<String> iterator() {
        return new LockCouplingIterator();
    }

    // Один полный проход пузырька. Возвращает число сделанных шагов (попыток обмена) —
    // пригодится для подсчёта статистики в бенчмарке.
    public long runBubblePass(long withinDelayMs, long betweenDelayMs) {
        long steps = 0;

        Node a = head;
        a.lock.lock();
        Node b = a.next;
        if (b == null) {
            a.lock.unlock();
            return steps;
        }
        b.lock.lock();
        Node c = b.next;
        if (c == null) {
            a.lock.unlock();
            b.lock.unlock();
            return steps;
        }
        c.lock.lock();

        while (true) {
            steps++;
            if (Sleep.quietly(withinDelayMs)) {
                a.lock.unlock();
                b.lock.unlock();
                c.lock.unlock();
                return steps;
            }

            Node newA;
            Node newB;
            Node newC;
            if (ORDER.compare(b.value, c.value) > 0) {
                // b и c стоят не по порядку — переставляем сами узлы (ссылки), а не их значения
                Node afterC = c.next;
                a.next = c;
                c.next = b;
                b.next = afterC;
                newA = c;
                newB = b;
                newC = afterC;
            } else {
                newA = b;
                newB = c;
                newC = c.next;
            }
            a.lock.unlock(); // старый a больше не нужен — окно сдвигается на шаг вперёд
            a = newA;
            b = newB;
            c = newC;

            if (Sleep.quietly(betweenDelayMs)) {
                a.lock.unlock();
                b.lock.unlock();
                return steps;
            }
            if (c == null) {
                break; // дошли до хвоста, проход закончен
            }
            c.lock.lock();
        }

        a.lock.unlock();
        b.lock.unlock();
        return steps;
    }

    private static final class Node {
        final String value;
        Node next; // трогать можно только держа lock этого узла

        final ReentrantLock lock = new ReentrantLock();

        Node(String value) {
            this.value = value;
        }
    }

    // Печать всегда идёт по стандартному for-each. Безопасность от конкурентной сортировки —
    // та же hand-over-hand блокировка: прежде чем отпустить предыдущий узел, захватываем
    // следующий, поэтому ни один узел не может "пропасть" из-под итератора.
    //
    // Ограничение: итератор нужно дочитывать до конца (как и делает print выше) — если
    // прервать перебор раньше, lock последнего посещённого узла останется висеть.
    private final class LockCouplingIterator implements Iterator<String> {
        private Node lockedPred;
        private Node pending;

        LockCouplingIterator() {
            head.lock.lock();
            lockedPred = head;
            advance();
        }

        private void advance() {
            Node candidate = lockedPred.next;
            if (candidate == null) {
                lockedPred.lock.unlock();
                lockedPred = null;
                pending = null;
                return;
            }
            candidate.lock.lock();
            lockedPred.lock.unlock();
            lockedPred = candidate;
            pending = candidate;
        }

        @Override
        public boolean hasNext() {
            return pending != null;
        }

        @Override
        public String next() {
            if (pending == null) {
                throw new NoSuchElementException();
            }
            String value = pending.value;
            advance();
            return value;
        }
    }
}
