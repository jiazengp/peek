package com.peek.utils;

import com.peek.PeekMod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Unified tick-based task management system
 */
public class TickTaskManager {
    
    /**
     * Generic tick task
     */
    public static class TickTask {
        private final UUID taskId;
        private final String taskType;
        private int remainingTicks;
        private final Consumer<TickTask> onComplete;
        
        public TickTask(UUID taskId, String taskType, int remainingTicks, Consumer<TickTask> onComplete) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.remainingTicks = remainingTicks;
            this.onComplete = onComplete;
        }
        
        public boolean tick() {
            return --remainingTicks <= 0;
        }
        
        public UUID getTaskId() { return taskId; }
        public String getTaskType() { return taskType; }
        public int getRemainingTicks() { return remainingTicks; }
        public void executeCompletion() { 
            try {
                onComplete.accept(this); 
            } catch (Exception e) {
                PeekMod.LOGGER.error("Error executing tick task completion for {} ({})", taskType, taskId, e);
            }
        }
    }
    
    private final List<TickTask> tasks = new ArrayList<>();
    
    /**
     * Add a new tick task
     */
    public void addTask(UUID taskId, String taskType, int ticks, Consumer<TickTask> onComplete) {
        synchronized (tasks) {
            tasks.add(new TickTask(taskId, taskType, ticks, onComplete));
        }
    }
    
    /**
     * Remove tasks with specific ID
     */
    public void removeTasksWithId(UUID taskId) {
        synchronized (tasks) {
            tasks.removeIf(task -> task.getTaskId().equals(taskId));
        }
    }
    
    /**
     * Remove tasks with specific type
     */
    public void removeTasksWithType(String taskType) {
        synchronized (tasks) {
            tasks.removeIf(task -> task.getTaskType().equals(taskType));
        }
    }
    
    /**
     * Process all tick tasks (call this every server tick)
     */
    public void processTick() {
        if (tasks.isEmpty()) {
            return;
        }
        
        List<TickTask> tasksToProcess;
        List<TickTask> completedTasks = new ArrayList<>();
        
        // Get a snapshot of current tasks to avoid concurrent modification
        synchronized (tasks) {
            tasksToProcess = new ArrayList<>(tasks);
        }
        
        // Process tasks outside the synchronized block to avoid concurrent modification
        for (TickTask task : tasksToProcess) {
            if (task.tick()) {
                task.executeCompletion();
                completedTasks.add(task);
            }
        }
        
        // Remove completed tasks in a separate synchronized block
        if (!completedTasks.isEmpty()) {
            synchronized (tasks) {
                tasks.removeAll(completedTasks);
            }
        }
    }
    
    /**
     * Get number of active tasks
     */
    public int getActiveTaskCount() {
        return tasks.size();
    }
    
    /**
     * Clear all tasks
     */
    public void clear() {
        synchronized (tasks) {
            tasks.clear();
        }
    }
    
    // Static instance and methods for convenience
    private static final TickTaskManager INSTANCE = new TickTaskManager();
    
    /**
     * Get the global instance
     */
    public static TickTaskManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Schedule a delayed task with a simple Runnable
     */
    public static void scheduleDelayedTask(Runnable task, int delayTicks) {
        UUID taskId = UUID.randomUUID();
        INSTANCE.addTask(taskId, "delayed_task", delayTicks, (tickTask) -> {
            try {
                task.run();
            } catch (Exception e) {
                PeekMod.LOGGER.error("Error executing delayed task {}", taskId, e);
            }
        });
    }
}