package one.nio.async;

public interface ParallelTask {
    void execute(int taskNum, int taskCount) throws Exception;
}
