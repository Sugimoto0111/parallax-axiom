package dev.srryo.parallaxaxiom.kill.hook;

public interface ExecutionMethodHook {
    float getHealth(float original, Object entity);

    boolean isAlive(boolean original, Object entity);

    boolean isDeadOrDying(boolean original, Object entity);
}
