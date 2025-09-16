package com.mark.message;

import lombok.Data;

import java.io.Serializable;

/**
 * @author mk
 * @data 2025/9/17 0:53
 * @description
 */
@Data
public class Request extends BaseMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private Object message;
}
