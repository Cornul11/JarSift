package nl.tudelft.cornul11.thesis.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

public class DatabaseWriterThread implements Runnable {
    private final BlockingQueue<Task> taskQueue;
    private final Logger logger = LoggerFactory.getLogger(DatabaseWriterThread.class);
    private final static Task POISON_TASK = () -> {};

    private int processedTasks = 0;

    public DatabaseWriterThread(BlockingQueue<Task> taskQueue) {
        this.taskQueue = taskQueue;
    }

    public static Task getPoisonTask() {
        return POISON_TASK;
    }

    @Override
    public void run() {
        try {
            while (true) {
                int remainingTasks = taskQueue.size();
                logger.info("Processed tasks / Remaining tasks: " + processedTasks + " / " + remainingTasks);
                Task task = taskQueue.take();
                if (task == POISON_TASK) {
                    break;
                }
                logger.info("Processing task: " + task);
                task.execute();
                processedTasks++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
