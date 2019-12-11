package com.mishiranu.dashchan.util;

/**
 * Асинхронный таймер времени.
 * Можно перезапускать сколько угодно раз.
 * При каждом перезапуске счетчик таймер сбрасывается до начального значения.
 * Коллбек выполнится только когда время дойдет до нуля.
 */
public class AsyncTimer extends Thread {
    /**
     * Количество милисекунд до остановки счетчика.
     */
    private int timerMillis;

    /**
     * Код, который будет выполнен после окончания отчета.
     */
    private Runnable afterCallback;

    /**
     * Тред счетчика. Пытается считать до заданного значения, затем запускает коллбек.
     */
    private Thread counterThread;

    /**
     * Constructor.
     *
     * @param timerMillis   Количество милисекунд до остановки счетчика.
     * @param afterCallback Код, который будет выполнен после окончания отчета.
     */
    public AsyncTimer(int timerMillis, Runnable afterCallback) {
        this.timerMillis = timerMillis;
        this.afterCallback = afterCallback;
    }

    @Override
    public void run() {
        if (counterThread != null && counterThread.isAlive()) {
            counterThread.interrupt();
        }

        counterThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(timerMillis);
                    (new Thread(afterCallback)).start();
                } catch (InterruptedException e) {
                    // Ok, it's normal.
                }
            }
        });
        counterThread.start();
    }

    /**
     * Перезапустить обратный отчет с начального значения и до нуля.
     */
    public synchronized void restart() {
        run();
    }
}
