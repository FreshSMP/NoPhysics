package com.jojodmo.physics;

public enum PhysicsState{

    ENABLE,
    OBEY_WORLDGUARD,
    DISABLE;

    public static PhysicsState fromBoolean(boolean b){
        return b ? OBEY_WORLDGUARD : DISABLE;
    }

    public boolean parse(boolean worldGuard){
        return this != DISABLE && (worldGuard || this == ENABLE);
    }

}
