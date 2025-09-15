package com.gridmr.master.model;

/**
 * MasterRole - Roles que puede tener un master en el sistema
 */
public enum MasterRole {
    LEADER,      // Master líder que coordina el sistema
    FOLLOWER,    // Master seguidor que replica el estado
    STANDALONE   // Master independiente (modo single-node)
}
