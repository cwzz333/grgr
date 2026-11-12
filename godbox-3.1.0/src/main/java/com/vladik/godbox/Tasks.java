package com.vladik.godbox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Простой планировщик серверных эффектов, которые длятся несколько тиков. */
public final class Tasks {
    public interface Task {
        /** @return true, когда задача закончилась */
        boolean tick();
    }

    private static final List<Task> ACTIVE = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    private Tasks() {}

    public static void add(Task t) {
        PENDING.add(t);
    }

    public static void delay(int ticks, Runnable r) {
        add(new Task() {
            int left = ticks;

            @Override
            public boolean tick() {
                if (--left <= 0) {
                    r.run();
                    return true;
                }
                return false;
            }
        });
    }

    public static void tick() {
        ACTIVE.addAll(PENDING);
        PENDING.clear();
        Iterator<Task> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            boolean done;
            try {
                done = t.tick();
            } catch (Exception e) {
                GodBox.LOG.error("GodBox task failed", e);
                done = true;
            }
            if (done) it.remove();
        }
    }

    public static void clear() {
        ACTIVE.clear();
        PENDING.clear();
    }
}
