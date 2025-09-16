package com.mark.message;


import java.io.Serializable;

/**
 * @author mk
 * @data 2025/9/17 0:52
 * @description
 */
public abstract class BaseMessage implements Serializable {
    private static final long seriealVersionUID = 1L;

    private int sequence;

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public int getSequence() {
        return sequence;
    }
}
