package org.example.j2.common;

public final class Sleep {

    private Sleep() {
    }

    // Засыпает на millis (0 — без сна). Возвращает true, если во время сна пришёл
    // interrupt — тогда вызывающий код должен сразу прекратить работу, а не "проглатывать"
    // прерывание и продолжать как ни в чём не бывало.
    public static boolean quietly(long millis) {
        if (millis <= 0) {
            return Thread.currentThread().isInterrupted();
        }
        try {
            Thread.sleep(millis);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
